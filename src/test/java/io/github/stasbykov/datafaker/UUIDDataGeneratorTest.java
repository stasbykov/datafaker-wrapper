package io.github.stasbykov.datafaker;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Checks UUID formats, deterministic names, window boundaries, and integration with settings. */
class UUIDDataGeneratorTest {
    /** Creates a repeatable facade for tests. */
    private DataGenerator data(long seed) {
        return DataGenerator.builder().seed(seed).build();
    }

    /** Random UUIDs have version 4 and the standard variant and follow the shared seeded stream. */
    @Test
    void randomUuidsFollowSeededSequence() {
        var first = data(42);
        var second = data(42);
        for (int i = 0; i < 100; i++) {
            assertEquals(first.strings().generate(8), second.strings().generate(8));
            UUID uuid = first.uuids().generateUUID();
            assertEquals(uuid, second.uuids().generateUUID());
            assertEquals(4, uuid.version());
            assertEquals(2, uuid.variant());
        }
        assertNotEquals(data(42).uuids().generateUUID(), data(43).uuids().generateUUID());
    }

    /** The explicit instant overload uses half-open windows aligned to the epoch. */
    @Test
    void minuteWindowIncludesStartAndExcludesEnd() {
        var uuids = data(1).uuids();
        Instant start = Instant.parse("2026-11-05T10:15:00Z");
        Duration size = Duration.ofMinutes(1);
        UUID expected = uuids.generateUUID(start, size);
        assertEquals(expected, uuids.generateUUID(start.plusSeconds(30), size));
        assertEquals(expected, uuids.generateUUID(start.plusSeconds(60).minusNanos(1), size));
        assertNotEquals(expected, uuids.generateUUID(start.minusNanos(1), size));
        assertNotEquals(expected, uuids.generateUUID(start.plusSeconds(60), size));
        assertEquals(3, expected.version());
        assertEquals(2, expected.variant());
    }

    /** Whole-second windows hash the UTF-8 decimal index with UUID.nameUUIDFromBytes. */
    @Test
    void wholeSecondWindowsMatchNumericName() {
        Instant instant = Instant.parse("2026-11-05T10:15:30Z");
        long index = instant.getEpochSecond() / 60;
        UUID expected = UUID.nameUUIDFromBytes(Long.toString(index).getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, data(1).uuids().generateUUID(instant, Duration.ofSeconds(60)));
    }

    /** Current-time calls consult the configured clock on every invocation, including rollover. */
    @Test
    void clockControlsWindowRolloverAndCanMoveBackwards() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-05T10:15:00Z"));
        Clock clock = new Clock() {
            /** Reports the test clock's zone. */
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            /** Returns a snapshot of this test clock in the requested zone. */
            @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now.get(), zone); }
            /** Reads the mutable instant to simulate time advancing or moving backwards. */
            @Override public Instant instant() { return now.get(); }
        };
        var uuids = DataGenerator.builder().clock(clock).build().uuids();
        Duration size = Duration.ofMinutes(1);
        UUID first = uuids.generateUUID(size);
        now.set(now.get().plusSeconds(59));
        assertEquals(first, uuids.generateUUID(size));
        now.set(now.get().plusSeconds(1));
        assertNotEquals(first, uuids.generateUUID(size));
        now.set(now.get().minusSeconds(60));
        assertEquals(first, uuids.generateUUID(size));
    }

    /** Equal instants and windows give equal IDs across seeds, locales, instances, and clock zones. */
    @Test
    void deterministicCallsAreIndependentOfSettingsAndRandomDraws() {
        Instant now = Instant.parse("2026-11-05T10:15:30Z");
        var first = DataGenerator.builder().seed(1).clock(Clock.fixed(now, ZoneOffset.UTC)).build();
        var second = DataGenerator.builder().seed(2).locale(java.util.Locale.FRENCH)
                .clock(Clock.fixed(now, ZoneId.of("Europe/Moscow"))).build();
        first.strings().generate(50);
        assertEquals(first.uuids().generateUUID(Duration.ofMinutes(1)),
                second.uuids().generateUUID(Duration.ofMinutes(1)));
        assertEquals(first.uuids().generateUUID("same"), second.uuids().generateUUID("same"));
        assertEquals(first.uuids().generateUUID(new Object[]{"a", 1}),
                second.uuids().generateUUID(new Object[]{"a", 1}));

        var untouched = data(5);
        var used = data(5);
        used.uuids().generateUUID("name");
        used.uuids().generateUUID(new Object[]{"a", "b"});
        used.uuids().generateUUID(now, Duration.ofNanos(1));
        assertEquals(untouched.uuids().generateUUID(), used.uuids().generateUUID());
    }

    /** Fractional seconds are included exactly, including windows shorter than a millisecond. */
    @Test
    void fractionalWindowsUseNanosecondPrecision() {
        var uuids = data(1).uuids();
        Duration halfSecond = Duration.ofMillis(500);
        assertEquals(uuids.generateUUID(Instant.EPOCH, halfSecond),
                uuids.generateUUID(Instant.EPOCH.plusNanos(499_999_999), halfSecond));
        assertNotEquals(uuids.generateUUID(Instant.EPOCH, halfSecond),
                uuids.generateUUID(Instant.EPOCH.plusMillis(500), halfSecond));
        Duration oneAndHalf = Duration.ofMillis(1500);
        assertEquals(uuids.generateUUID(Instant.EPOCH, oneAndHalf),
                uuids.generateUUID(Instant.EPOCH.plusMillis(1499), oneAndHalf));
        assertNotEquals(uuids.generateUUID(Instant.EPOCH, oneAndHalf),
                uuids.generateUUID(Instant.EPOCH.plusMillis(1500), oneAndHalf));
        assertNotEquals(uuids.generateUUID(Instant.EPOCH, Duration.ofNanos(1)),
                uuids.generateUUID(Instant.EPOCH.plusNanos(1), Duration.ofNanos(1)));
    }

    /** Negative epoch times use floor division, so the boundary at zero stays exact. */
    @Test
    void windowsBeforeEpochRoundDown() {
        var uuids = data(1).uuids();
        Duration minute = Duration.ofMinutes(1);
        UUID negative = uuids.generateUUID(Instant.EPOCH.minusNanos(1), minute);
        assertEquals(uuids.generateUUID("-1"), negative);
        assertEquals(negative, uuids.generateUUID(Instant.EPOCH.minusSeconds(60), minute));
        assertNotEquals(negative, uuids.generateUUID(Instant.EPOCH.minusSeconds(60).minusNanos(1), minute));
        assertNotEquals(negative, uuids.generateUUID(Instant.EPOCH, minute));
    }

    /** Supported Instant and Duration extremes avoid long overflow in nanosecond conversion. */
    @Test
    void extremeInstantsAndDurationsAreSupported() {
        var uuids = data(1).uuids();
        Duration maximum = Duration.ofSeconds(Long.MAX_VALUE, 999_999_999);
        assertEquals(uuids.generateUUID("0"), uuids.generateUUID(Instant.MAX, maximum));
        assertEquals(uuids.generateUUID("-1"), uuids.generateUUID(Instant.MIN, maximum));
        UUID min = uuids.generateUUID(Instant.MIN, Duration.ofNanos(1));
        UUID max = uuids.generateUUID(Instant.MAX, Duration.ofNanos(1));
        assertNotEquals(min, max);
    }

    /** Window sizes sharing a numeric index intentionally share the deterministic UUID. */
    @Test
    void equalWindowIndicesShareNames() {
        var uuids = data(1).uuids();
        assertEquals(uuids.generateUUID(Instant.ofEpochSecond(60), Duration.ofSeconds(60)),
                uuids.generateUUID(Instant.ofEpochSecond(120), Duration.ofSeconds(120)));
    }

    /** String generation uses exact UTF-8 text, including empty names and non-ASCII characters. */
    @Test
    void stringsUseExactUtf8Names() {
        var uuids = data(1).uuids();
        for (String name : new String[]{"", "order-42", "Евро😀", " A "}) {
            assertEquals(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)),
                    uuids.generateUUID(name));
        }
        assertNotEquals(uuids.generateUUID("A"), uuids.generateUUID("a"));
        assertNotEquals(uuids.generateUUID("A"), uuids.generateUUID(" A "));
    }

    /** Length framing distinguishes delimiters, missing values, and text that spells null. */
    @Test
    void multipartNamesAreUnambiguous() {
        var uuids = data(1).uuids();
        assertNotEquals(uuids.generateUUID("a|b", "c"), uuids.generateUUID("a", "b|c"));
        assertNotEquals(uuids.generateUUID(new Object[]{null}), uuids.generateUUID(new Object[]{"null"}));
        assertNotEquals(uuids.generateUUID(new Object[]{}), uuids.generateUUID(new Object[]{""}));
        assertNotEquals(uuids.generateUUID("ab", "c"), uuids.generateUUID("a", "bc"));
        assertNotEquals(uuids.generateUUID("a", "b"), uuids.generateUUID("b", "a"));
        assertEquals(uuids.generateUUID("order", 42), uuids.generateUUID("order", "42"));
        assertEquals(uuids.generateUUID("1:a1:b"), uuids.generateUUID("a", "b"));
    }

    /** Factory methods expose the UUID generator through the same context as other groups. */
    @Test
    void factoriesAndStringOverloadsWork() {
        var data = data(1);
        assertSame(data.uuids(), data.uuids());
        assertNotNull(DataGenerator.russianGenerator(UUIDDataGenerator::new).generateUUID());
        assertNotNull(data.create(context -> context.uuids()).generateUUID());
        var uuids = data.uuids();
        Instant now = Instant.parse("2026-11-05T10:15:00Z");
        Duration minute = Duration.ofMinutes(1);
        assertEquals(uuids.generateUUID(now, minute).toString(), uuids.generateUUIDAsString(now, minute));
        var fixed = DataGenerator.builder().clock(Clock.fixed(now, ZoneOffset.UTC)).build().uuids();
        assertEquals(fixed.generateUUID(minute).toString(), fixed.generateUUIDAsString(minute));
        assertEquals(uuids.generateUUID("name").toString(), uuids.generateUUIDAsString("name"));
        assertEquals(uuids.generateUUID("a", "b").toString(), uuids.generateUUIDAsString("a", "b"));
        assertEquals(data(1).uuids().generateUUID().toString(), data(1).uuids().generateUUIDAsString());
        assertEquals("test-" + data(1).uuids().generateUUID(), data(1).uuids().generateUUIDWithPrefix("test"));
    }

    /** Invalid durations, null inputs, and blank prefixes fail at the public entry points. */
    @Test
    void invalidInputsAreRejected() {
        var uuids = data(1).uuids();
        for (Duration duration : new Duration[]{Duration.ZERO, Duration.ofSeconds(-1), Duration.ofNanos(-1)}) {
            assertThrows(IllegalArgumentException.class, () -> uuids.generateUUID(duration));
            assertThrows(IllegalArgumentException.class, () -> uuids.generateUUID(Instant.EPOCH, duration));
        }
        assertThrows(NullPointerException.class, () -> new UUIDDataGenerator(null));
        assertThrows(NullPointerException.class, () -> uuids.generateUUID((Duration) null));
        assertThrows(NullPointerException.class, () -> uuids.generateUUID(null, Duration.ofSeconds(1)));
        assertThrows(NullPointerException.class, () -> uuids.generateUUID(Instant.EPOCH, null));
        assertThrows(NullPointerException.class, () -> uuids.generateUUID((String) null));
        assertThrows(NullPointerException.class, () -> uuids.generateUUID((Object[]) null));
        assertThrows(NullPointerException.class, () -> uuids.generateUUIDWithPrefix(null));
        assertThrows(IllegalArgumentException.class, () -> uuids.generateUUIDWithPrefix(" "));
    }
}
