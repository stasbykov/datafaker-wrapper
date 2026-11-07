package io.github.stasbykov.datafaker;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Random version 4, time-based version 7, and deterministic version 3 UUIDs for test data.
 * Time windows are aligned to the Unix epoch, with inclusive start and exclusive end.
 * All generation methods return UUID; call toString() for canonical text.
 * All deterministic methods are independent of the random seed and consume no random values.
 */
public final class UUIDDataGenerator {
    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);
    private static final Instant V7_TIMESTAMP_LIMIT = Instant.ofEpochMilli(1L << 48);
    private final GeneratorContext context;

    /** Creates a generator using shared settings. Prefer {@code data.uuids()}. */
    public UUIDDataGenerator(GeneratorContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    /**
     * Generates a version 4 UUID using the context's random stream.
     * The configured seed and call order reproduce the sequence. Intended for test data.
     */
    public UUID generateUUIDv4() {
        long most = (context.nextLong() & 0xffffffffffff0fffL) | 0x0000000000004000L;
        long least = (context.nextLong() & 0x3fffffffffffffffL) | 0x8000000000000000L;
        return new UUID(most, least);
    }

    /**
     * Generates a version 7 UUID using the configured clock and shared random stream.
     * Example: {@code data.uuids().generateUUIDv7()}.
     * Each call draws fresh random bits, including calls in the same millisecond.
     *
     * @return version 7 UUID with the current Unix millisecond timestamp
     * @throws IllegalArgumentException if the clock is outside the supported timestamp range
     */
    public UUID generateUUIDv7() {
        return generateUUIDv7(context.clock().instant());
    }

    /**
     * Generates a UUID with the RFC 9562 version 7 layout: 48 timestamp bits and 74 random bits.
     * Sub-millisecond precision is discarded. A fixed instant, seed, and call order reproduce
     * the sequence. Equal timestamps have random order; clock rollback is reflected in the UUID.
     * Compare canonical strings or unsigned bytes for timestamp ordering across the full range.
     *
     * @param instant timestamp from the Unix epoch (inclusive) to 2^48 milliseconds (exclusive)
     * @return version 7 UUID for the supplied timestamp
     * @throws NullPointerException if instant is null
     * @throws IllegalArgumentException if instant is outside the unsigned 48-bit timestamp range
     */
    public UUID generateUUIDv7(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        if (instant.isBefore(Instant.EPOCH) || !instant.isBefore(V7_TIMESTAMP_LIMIT)) {
            throw new IllegalArgumentException("UUID v7 requires Unix milliseconds in [0, 2^48)");
        }
        long most = (instant.toEpochMilli() << 16)
                | 0x0000000000007000L
                | (context.nextLong() & 0x0000000000000fffL);
        long least = (context.nextLong() & 0x3fffffffffffffffL) | 0x8000000000000000L;
        return new UUID(most, least);
    }

    /**
     * Returns the version 3 UUID for the current epoch-aligned window using the configured clock.
     * Example: {@code data.uuids().generateUUIDv3ForWindow(Duration.ofMinutes(1))}.
     * The same window index always gives the same UUID, across instances and seeds.
     *
     * @param bucketSize positive window duration, with nanosecond precision
     * @return UUID for the current window
     * @throws NullPointerException if bucketSize is null
     * @throws IllegalArgumentException if bucketSize is zero or negative
     */
    public UUID generateUUIDv3ForWindow(Duration bucketSize) {
        return generateUUIDv3ForWindow(context.clock().instant(), bucketSize);
    }

    /**
     * Returns the version 3 UUID for an explicit instant's epoch-aligned window.
     * Hashes the UTF-8 decimal representation of floor(epoch nanoseconds / window nanoseconds).
     * Window size is not part of the hashed name: equal indices for different sizes share a UUID.
     *
     * @param instant instant to place into a window
     * @param bucketSize positive window duration, with nanosecond precision
     * @return UUID for the window index
     * @throws NullPointerException if either argument is null
     * @throws IllegalArgumentException if bucketSize is zero or negative
     */
    public UUID generateUUIDv3ForWindow(Instant instant, Duration bucketSize) {
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(bucketSize, "bucketSize");
        if (bucketSize.isZero() || bucketSize.isNegative()) {
            throw new IllegalArgumentException("bucketSize must be positive");
        }
        BigInteger time = toNanos(instant.getEpochSecond(), instant.getNano());
        BigInteger size = toNanos(bucketSize.getSeconds(), bucketSize.getNano());
        BigInteger[] division = time.divideAndRemainder(size);
        BigInteger bucket = division[0];
        if (division[1].signum() < 0) {
            bucket = bucket.subtract(BigInteger.ONE);
        }
        return generateUUIDv3FromName(bucket.toString());
    }

    /**
     * Generates a version 3 UUID from the exact UTF-8 string. Empty strings are allowed.
     * Whitespace and case are significant. Example: {@code data.uuids().generateUUIDv3FromName("order-42")}.
     *
     * @param value nonnull name
     * @return deterministic UUID
     */
    public UUID generateUUIDv3FromName(String value) {
        Objects.requireNonNull(value, "value");
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a version 3 UUID from ordered parts using length-prefixed text.
     * Null parts have a separate marker. Embedded separators cannot merge adjacent parts.
     * Nonnull parts use toString(), which must be stable for repeatable results.
     * Types with equal text are equivalent; arrays are not recursively serialized.
     * Example: {@code data.uuids().generateUUIDv3FromParts("order", 42)}.
     * Even a single part uses this encoding; use generateUUIDv3FromName for an exact name.
     *
     * @param values nonnull array of parts; an empty array is allowed
     * @return deterministic UUID for the encoded parts
     */
    public UUID generateUUIDv3FromParts(Object... values) {
        Objects.requireNonNull(values, "values");
        StringBuilder name = new StringBuilder();
        for (Object value : values) {
            if (value == null) {
                name.append("-1:");
            } else {
                String text = Objects.requireNonNull(value.toString(), "part.toString()");
                name.append(text.length()).append(':').append(text);
            }
        }
        return generateUUIDv3FromName(name.toString());
    }

    /** Converts seconds and their nanosecond fraction without overflowing long arithmetic. */
    private static BigInteger toNanos(long seconds, int nanos) {
        return BigInteger.valueOf(seconds).multiply(NANOS_PER_SECOND).add(BigInteger.valueOf(nanos));
    }
}
