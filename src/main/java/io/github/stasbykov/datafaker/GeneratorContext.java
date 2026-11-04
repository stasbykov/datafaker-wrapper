package io.github.stasbykov.datafaker;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**

 * Settings and a shared random stream for custom generators. Obtain a context through

 * DataGenerator.create. Do not share it between concurrent tests.

 */
public final class GeneratorContext {
    private final Locale locale;
    private final Clock clock;
    private final Random random;
    private final int maxStringLength;
    private final List<CurrencyData> currencies;
    private final DatafakerAdapter adapter;

    /** Creates a shared context from validated settings. Called by the main factory. */
    GeneratorContext(Locale locale, Clock clock, Random random,
                     int maxStringLength, List<CurrencyData> currencies) {
        this.locale = locale;
        this.clock = clock;
        this.random = random;
        this.maxStringLength = maxStringLength;
        this.currencies = List.copyOf(currencies);
        adapter = new DatafakerAdapter(locale, random);
    }

    /** Returns the configured language. Use it to choose templates in a custom generator. */
    public Locale locale() { return locale; }
    /** Returns the configured clock. Use {@code LocalDateTime.now(context.clock())} for timestamps. */
    public Clock clock() { return clock; }
    /** Returns the maximum permitted string length in Unicode code points. */
    public int maxStringLength() { return maxStringLength; }
    /**
     * Returns the immutable ordered currency catalog. Use this list when creating correlated
     * currency data.
     */
    public List<CurrencyData> currencies() { return currencies; }

    /**

     * Draws a random integer from the shared stream. The one-argument form excludes its bound; the

     * two-argument form includes both boundaries. Invalid ranges are rejected.

     */
    public int nextInt(int bound) { return random.nextInt(bound); }

    /**

     * Draws a random integer from the shared stream. The one-argument form excludes its bound; the

     * two-argument form includes both boundaries. Invalid ranges are rejected.

     */
    public int nextInt(int minInclusive, int maxInclusive) {
        if (maxInclusive < minInclusive) throw new IllegalArgumentException("Reversed range");
        return (int) random.nextLong((long) minInclusive, (long) maxInclusive + 1);
    }

    /**

     * Creates a string generator using this context. Example: {@code

     * context.strings().generateDigits(8)}.

     */
    public StringDataGenerator strings() { return new StringDataGenerator(this); }
    /**
     * Creates a financial generator using this context. Example: {@code
     * context.financial().generateCurrency()}.
     */
    public FinancialDataGenerator financial() { return new FinancialDataGenerator(this); }
    /**
     * Creates an address generator using this context. Example: {@code
     * context.address().generateCity()}.
     */
    public AddressDataGenerator address() { return new AddressDataGenerator(this); }
    /**
     * Creates a text generator using this context. Example: {@code
     * context.texts().generateCountryDescription("France")}.
     */
    public TextDataGenerator texts() { return new TextDataGenerator(this); }

    /** Returns the internal data provider for localized fields and regex generation. */
    DatafakerAdapter adapter() { return adapter; }

    /**

     * Checks a required text argument and removes surrounding whitespace. Rejects null with

     * NullPointerException and blank text with IllegalArgumentException.

     */
    static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }
}
