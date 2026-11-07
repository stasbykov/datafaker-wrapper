package io.github.stasbykov.datafaker;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Tests version 7 timestamp layout and its public generation entry points. */
class UUIDv7Test {
    /** Creates a repeatable UUID generator at the given clock instant. */
    private UUIDDataGenerator generator(long seed, Instant instant) {
        return DataGenerator.builder().seed(seed)
                .clock(Clock.fixed(instant, ZoneOffset.UTC)).build().uuids();
    }

    /** Reads the 48-bit timestamp without sign extension. */
    private long timestamp(UUID uuid) {
        return uuid.getMostSignificantBits() >>> 16;
    }

    /** Verifies the version, variant, and exact timestamp, including discarded nanoseconds. */
    @Test
    void encodesTimestampAndVersionFields() {
        Instant instant = Instant.parse("2026-11-05T10:15:30.123456789Z");
        UUID uuid = generator(42, instant).generateUUIDv7();
        assertEquals(7, uuid.version());
        assertEquals(2, uuid.variant());
        assertEquals(instant.toEpochMilli(), timestamp(uuid));
        assertEquals(uuid, generator(42, Instant.EPOCH).generateUUIDv7(instant));
        assertEquals(uuid, generator(42, instant).generateUUIDv7(Instant.ofEpochMilli(instant.toEpochMilli())));
    }

    /** Checks all random payload bits independently of the implementation's masking expressions. */
    @Test
    void payloadUsesTheSharedRandomStream() {
        Instant instant = Instant.ofEpochMilli(0x017f22e279b0L);
        var data = DataGenerator.builder().seed(93).build();
        Random expected = new Random(93);
        assertEquals(expected.nextInt(100), data.create(c -> c.nextInt(100)).intValue());
        long first = expected.nextLong();
        long second = expected.nextLong();
        UUID uuid = data.uuids().generateUUIDv7(instant);
        // Check both payload fields without including the version or variant bits.
        for (int bit = 0; bit < 12; bit++) {
            assertEquals((first >>> bit) & 1, (uuid.getMostSignificantBits() >>> bit) & 1);
        }
        for (int bit = 0; bit < 62; bit++) {
            assertEquals((second >>> bit) & 1, (uuid.getLeastSignificantBits() >>> bit) & 1);
        }
        assertEquals(instant.toEpochMilli(), timestamp(uuid));
    }

    /** Verifies new random values within a millisecond and reproducibility across instances. */
    @Test
    void sameMillisecondProducesRepeatableSequenceOfDifferentValues() {
        Instant instant = Instant.parse("2026-11-05T10:15:30Z");
        var first = generator(42, instant);
        var second = generator(42, instant);
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            UUID uuid = first.generateUUIDv7();
            assertEquals(uuid, second.generateUUIDv7());
            assertEquals(instant.toEpochMilli(), timestamp(uuid));
            assertTrue(seen.add(uuid));
        }
        assertNotEquals(generator(42, instant).generateUUIDv7(),
                generator(43, instant).generateUUIDv7());
    }

    /** Checks timestamp ordering using canonical strings on both sides of the signed-long boundary. */
    @Test
    void stringOrderFollowsMillisecondsAcrossFullRange() {
        var uuids = generator(42, Instant.EPOCH);
        long[] millis = {0, 1, (1L << 47) - 1, 1L << 47, (1L << 48) - 1};
        String previous = "";
        for (long time : millis) {
            UUID uuid = uuids.generateUUIDv7(Instant.ofEpochMilli(time));
            assertEquals(time, timestamp(uuid));
            assertTrue(previous.compareTo(uuid.toString()) < 0);
            previous = uuid.toString();
        }
    }

    /** Confirms that moving the clock back changes the timestamp instead of keeping hidden state. */
    @Test
    void clockRollbackIsReflected() {
        Instant now = Instant.parse("2026-11-05T10:15:30Z");
        java.util.concurrent.atomic.AtomicReference<Instant> time =
                new java.util.concurrent.atomic.AtomicReference<>(now);
        Clock clock = new Clock() {
            /** Returns the test zone. */
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            /** Returns a snapshot in the requested zone. */
            @Override public Clock withZone(java.time.ZoneId zone) { return Clock.fixed(time.get(), zone); }
            /** Reads the current test instant. */
            @Override public Instant instant() { return time.get(); }
        };
        var uuids = DataGenerator.builder().clock(clock).seed(42).build().uuids();
        assertEquals(now.toEpochMilli(), timestamp(uuids.generateUUIDv7()));
        time.set(now.minusSeconds(1));
        assertEquals(time.get().toEpochMilli(), timestamp(uuids.generateUUIDv7()));
    }

    /** Tests both timestamp endpoints and rejects out-of-range input without consuming randomness. */
    @Test
    void validatesRangeBeforeRandomDraws() {
        Instant limit = Instant.ofEpochMilli(1L << 48);
        var uuids = generator(42, Instant.EPOCH);
        for (Instant invalid : new Instant[]{Instant.MIN, Instant.EPOCH.minusNanos(1), limit, Instant.MAX}) {
            assertThrows(IllegalArgumentException.class, () -> uuids.generateUUIDv7(invalid));
        }
        assertThrows(NullPointerException.class, () -> uuids.generateUUIDv7(null));
        assertEquals(generator(42, Instant.EPOCH).generateUUIDv7(), uuids.generateUUIDv7());
        UUID last = uuids.generateUUIDv7(limit.minusNanos(1));
        assertEquals((1L << 48) - 1, timestamp(last));
        assertEquals(7, last.version());
        assertEquals(2, last.variant());
        assertThrows(IllegalArgumentException.class, () -> generator(42, limit).generateUUIDv7());
    }

    /** Checks factory construction through the main facade. */
    @Test
    void factoryEntryPointWorks() {
        Instant instant = Instant.parse("2026-11-05T10:15:30Z");
        UUID expected = generator(42, instant).generateUUIDv7();
        var custom = DataGenerator.builder().seed(42).clock(Clock.fixed(instant, ZoneOffset.UTC))
                .build().create(UUIDDataGenerator::new);
        assertEquals(expected, custom.generateUUIDv7());
    }
}
