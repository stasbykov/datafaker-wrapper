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
            UUID uuid = first.uuids().generateUUIDv4();
            assertEquals(uuid, second.uuids().generateUUIDv4());
            assertEquals(4, uuid.version());
            assertEquals(2, uuid.variant());
        }
        assertNotEquals(data(42).uuids().generateUUIDv4(), data(43).uuids().generateUUIDv4());
    }

    /** The explicit instant overload uses half-open windows aligned to the epoch. */
    @Test
    void minuteWindowIncludesStartAndExcludesEnd() {
        var uuids = data(1).uuids();
        Instant start = Instant.parse("2026-11-05T10:15:00Z");
        Duration size = Duration.ofMinutes(1);
        UUID expected = uuids.generateUUIDv3ForWindow(start, size);
        assertEquals(expected, uuids.generateUUIDv3ForWindow(start.plusSeconds(30), size));
        assertEquals(expected, uuids.generateUUIDv3ForWindow(start.plusSeconds(60).minusNanos(1), size));
        assertNotEquals(expected, uuids.generateUUIDv3ForWindow(start.minusNanos(1), size));
        assertNotEquals(expected, uuids.generateUUIDv3ForWindow(start.plusSeconds(60), size));
        assertEquals(3, expected.version());
        assertEquals(2, expected.variant());
    }

    /** Whole-second windows hash the UTF-8 decimal index with UUID.nameUUIDFromBytes. */
    @Test
    void wholeSecondWindowsMatchNumericName() {
        Instant instant = Instant.parse("2026-11-05T10:15:30Z");
        long index = instant.getEpochSecond() / 60;
        UUID expected = UUID.nameUUIDFromBytes(Long.toString(index).getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, data(1).uuids().generateUUIDv3ForWindow(instant, Duration.ofSeconds(60)));
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
        UUID first = uuids.generateUUIDv3ForWindow(size);
        now.set(now.get().plusSeconds(59));
        assertEquals(first, uuids.generateUUIDv3ForWindow(size));
        now.set(now.get().plusSeconds(1));
        assertNotEquals(first, uuids.generateUUIDv3ForWindow(size));
        now.set(now.get().minusSeconds(60));
        assertEquals(first, uuids.generateUUIDv3ForWindow(size));
    }

    /** Equal instants and windows give equal IDs across seeds, locales, instances, and clock zones. */
    @Test
    void deterministicCallsAreIndependentOfSettingsAndRandomDraws() {
        Instant now = Instant.parse("2026-11-05T10:15:30Z");
        var first = DataGenerator.builder().seed(1).clock(Clock.fixed(now, ZoneOffset.UTC)).build();
        var second = DataGenerator.builder().seed(2).locale(java.util.Locale.FRENCH)
                .clock(Clock.fixed(now, ZoneId.of("Europe/Moscow"))).build();
        first.strings().generate(50);
        assertEquals(first.uuids().generateUUIDv3ForWindow(Duration.ofMinutes(1)),
                second.uuids().generateUUIDv3ForWindow(Duration.ofMinutes(1)));
        assertEquals(first.uuids().generateUUIDv3FromName("same"), second.uuids().generateUUIDv3FromName("same"));
        assertEquals(first.uuids().generateUUIDv3FromParts(new Object[]{"a", 1}),
                second.uuids().generateUUIDv3FromParts(new Object[]{"a", 1}));

        var untouched = data(5);
        var used = data(5);
        used.uuids().generateUUIDv3FromName("name");
        used.uuids().generateUUIDv3FromParts(new Object[]{"a", "b"});
        used.uuids().generateUUIDv3ForWindow(now, Duration.ofNanos(1));
        assertEquals(untouched.uuids().generateUUIDv4(), used.uuids().generateUUIDv4());
    }

    /** Fractional seconds are included exactly, including windows shorter than a millisecond. */
    @Test
    void fractionalWindowsUseNanosecondPrecision() {
        var uuids = data(1).uuids();
        Duration halfSecond = Duration.ofMillis(500);
        assertEquals(uuids.generateUUIDv3ForWindow(Instant.EPOCH, halfSecond),
                uuids.generateUUIDv3ForWindow(Instant.EPOCH.plusNanos(499_999_999), halfSecond));
        assertNotEquals(uuids.generateUUIDv3ForWindow(Instant.EPOCH, halfSecond),
                uuids.generateUUIDv3ForWindow(Instant.EPOCH.plusMillis(500), halfSecond));
        Duration oneAndHalf = Duration.ofMillis(1500);
        assertEquals(uuids.generateUUIDv3ForWindow(Instant.EPOCH, oneAndHalf),
                uuids.generateUUIDv3ForWindow(Instant.EPOCH.plusMillis(1499), oneAndHalf));
        assertNotEquals(uuids.generateUUIDv3ForWindow(Instant.EPOCH, oneAndHalf),
                uuids.generateUUIDv3ForWindow(Instant.EPOCH.plusMillis(1500), oneAndHalf));
        assertNotEquals(uuids.generateUUIDv3ForWindow(Instant.EPOCH, Duration.ofNanos(1)),
                uuids.generateUUIDv3ForWindow(Instant.EPOCH.plusNanos(1), Duration.ofNanos(1)));
    }

    /** Negative epoch times use floor division, so the boundary at zero stays exact. */
    @Test
    void windowsBeforeEpochRoundDown() {
        var uuids = data(1).uuids();
        Duration minute = Duration.ofMinutes(1);
        UUID negative = uuids.generateUUIDv3ForWindow(Instant.EPOCH.minusNanos(1), minute);
        assertEquals(uuids.generateUUIDv3FromName("-1"), negative);
        assertEquals(negative, uuids.generateUUIDv3ForWindow(Instant.EPOCH.minusSeconds(60), minute));
        assertNotEquals(negative, uuids.generateUUIDv3ForWindow(Instant.EPOCH.minusSeconds(60).minusNanos(1), minute));
        assertNotEquals(negative, uuids.generateUUIDv3ForWindow(Instant.EPOCH, minute));
    }

    /** Supported Instant and Duration extremes avoid long overflow in nanosecond conversion. */
    @Test
    void extremeInstantsAndDurationsAreSupported() {
        var uuids = data(1).uuids();
        Duration maximum = Duration.ofSeconds(Long.MAX_VALUE, 999_999_999);
        assertEquals(uuids.generateUUIDv3FromName("0"), uuids.generateUUIDv3ForWindow(Instant.MAX, maximum));
        assertEquals(uuids.generateUUIDv3FromName("-1"), uuids.generateUUIDv3ForWindow(Instant.MIN, maximum));
        UUID min = uuids.generateUUIDv3ForWindow(Instant.MIN, Duration.ofNanos(1));
        UUID max = uuids.generateUUIDv3ForWindow(Instant.MAX, Duration.ofNanos(1));
        assertNotEquals(min, max);
    }

    /** Window sizes sharing a numeric index intentionally share the deterministic UUID. */
    @Test
    void equalWindowIndicesShareNames() {
        var uuids = data(1).uuids();
        assertEquals(uuids.generateUUIDv3ForWindow(Instant.ofEpochSecond(60), Duration.ofSeconds(60)),
                uuids.generateUUIDv3ForWindow(Instant.ofEpochSecond(120), Duration.ofSeconds(120)));
    }

    /** String generation uses exact UTF-8 text, including empty names and non-ASCII characters. */
    @Test
    void stringsUseExactUtf8Names() {
        var uuids = data(1).uuids();
        for (String name : new String[]{"", "order-42", "Евро😀", " A "}) {
            assertEquals(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)),
                    uuids.generateUUIDv3FromName(name));
        }
        assertNotEquals(uuids.generateUUIDv3FromName("A"), uuids.generateUUIDv3FromName("a"));
        assertNotEquals(uuids.generateUUIDv3FromName("A"), uuids.generateUUIDv3FromName(" A "));
    }

    /** Length framing distinguishes delimiters, missing values, and text that spells null. */
    @Test
    void multipartNamesAreUnambiguous() {
        var uuids = data(1).uuids();
        assertNotEquals(uuids.generateUUIDv3FromParts("a|b", "c"), uuids.generateUUIDv3FromParts("a", "b|c"));
        assertNotEquals(uuids.generateUUIDv3FromParts(new Object[]{null}), uuids.generateUUIDv3FromParts(new Object[]{"null"}));
        assertNotEquals(uuids.generateUUIDv3FromParts(new Object[]{}), uuids.generateUUIDv3FromParts(new Object[]{""}));
        assertNotEquals(uuids.generateUUIDv3FromParts("ab", "c"), uuids.generateUUIDv3FromParts("a", "bc"));
        assertNotEquals(uuids.generateUUIDv3FromParts("a", "b"), uuids.generateUUIDv3FromParts("b", "a"));
        assertEquals(uuids.generateUUIDv3FromParts("order", 42), uuids.generateUUIDv3FromParts("order", "42"));
        assertNotEquals(uuids.generateUUIDv3FromName("a"), uuids.generateUUIDv3FromParts("a"));
        assertEquals(uuids.generateUUIDv3FromParts(new Object[]{"a"}), uuids.generateUUIDv3FromParts("a"));
        assertEquals(uuids.generateUUIDv3FromName("1:a1:b"), uuids.generateUUIDv3FromParts("a", "b"));
    }

    /** Factory methods expose the UUID generator through the same context as other groups. */
    @Test
    void factoriesWork() {
        var data = data(1);
        assertSame(data.uuids(), data.uuids());
        assertNotNull(DataGenerator.russianGenerator(UUIDDataGenerator::new).generateUUIDv4());
        assertNotNull(data.create(context -> context.uuids()).generateUUIDv4());
    }

    /** Invalid durations and null inputs fail at the public entry points. */
    @Test
    void invalidInputsAreRejected() {
        var uuids = data(1).uuids();
        for (Duration duration : new Duration[]{Duration.ZERO, Duration.ofSeconds(-1), Duration.ofNanos(-1)}) {
            assertThrows(IllegalArgumentException.class, () -> uuids.generateUUIDv3ForWindow(duration));
            assertThrows(IllegalArgumentException.class, () -> uuids.generateUUIDv3ForWindow(Instant.EPOCH, duration));
        }
        assertThrows(NullPointerException.class, () -> new UUIDDataGenerator(null));
        assertThrows(NullPointerException.class, () -> uuids.generateUUIDv3ForWindow((Duration) null));
        assertThrows(NullPointerException.class, () -> uuids.generateUUIDv3ForWindow(null, Duration.ofSeconds(1)));
        assertThrows(NullPointerException.class, () -> uuids.generateUUIDv3ForWindow(Instant.EPOCH, null));
        assertThrows(NullPointerException.class, () -> uuids.generateUUIDv3FromName((String) null));
        assertThrows(NullPointerException.class, () -> uuids.generateUUIDv3FromParts((Object[]) null));
    }
}
