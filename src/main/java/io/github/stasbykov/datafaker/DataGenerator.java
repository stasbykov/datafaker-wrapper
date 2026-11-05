package io.github.stasbykov.datafaker;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/**

 * Entry point for configured test data. Create one instance per test or independent task. Settings

 * are fixed at construction; the shared random stream advances on each draw.

 */
public final class DataGenerator {
    private final GeneratorContext context;
    private final StringDataGenerator strings;
    private final FinancialDataGenerator financial;
    private final AddressDataGenerator address;
    private final TextDataGenerator texts;
    private final UUIDDataGenerator uuids;

    /** Builds the generator groups from a validated configuration snapshot. */
    private DataGenerator(Builder builder) {
        List<CurrencyData> currencies = builder.currencies == null
                ? CurrencyCatalog.forLocale(builder.locale) : builder.currencies;
        context = new GeneratorContext(builder.locale, builder.clock,
                builder.seed == null ? new Random() : new Random(builder.seed),
                builder.maxStringLength, currencies);
        strings = context.strings();
        financial = context.financial();
        address = context.address();
        texts = context.texts();
        uuids = context.uuids();
    }

    /**

     * Starts configuration with English locale, UTC time, no fixed seed and a 1,000,000 code point

     * limit. Example: {@code DataGenerator.builder().seed(42).build()}.

     */
    public static Builder builder() { return new Builder(); }
    /**
     * Creates a generator with Russian locale. Example: {@code
     * DataGenerator.russian().financial().generateRRN()}.
     */
    public static DataGenerator russian() {
        return builder().locale(Locale.forLanguageTag("ru")).build();
    }
    /**
     * Creates a generator with English locale and UTC time. Example: {@code
     * DataGenerator.defaults().strings().generate(12)}.
     */
    public static DataGenerator defaults() { return builder().build(); }
    /**
     * Returns the string generator sharing this instance's random stream. Example: {@code
     * data.strings().generateDigits(12)}.
     */
    public StringDataGenerator strings() { return strings; }
    /** Returns the financial generator. Example: {@code data.financial().generateCurrency()}. */
    public FinancialDataGenerator financial() { return financial; }
    /** Returns the address generator. Example: {@code data.address().generateCountry()}. */
    public AddressDataGenerator address() { return address; }
    /** Returns the text generator. Example: {@code data.texts().generateCurrencyDescription("Euro")}. */
    public TextDataGenerator texts() { return texts; }

    /** Returns the UUID generator. Example: {@code data.uuids().generateUUIDv4()}. */
    public UUIDDataGenerator uuids() {
        return uuids;
    }


    /**

     * Creates a custom generator using this instance's settings and random stream. Example: {@code

     * data.create(FinancialDataGenerator::new)}. Rejects a null factory or null result.

     */
    public <T> T create(Function<GeneratorContext, T> creator) {
        return Objects.requireNonNull(Objects.requireNonNull(creator, "creator").apply(context),
                "creator returned null");
    }
    /**
     * Creates a generator using Russian locale. Example: {@code
     * DataGenerator.russianGenerator(FinancialDataGenerator::new)}.
     */
    public static <T> T russianGenerator(Function<GeneratorContext, T> creator) {
        return russian().create(creator);
    }
    /**
     * Creates a generator using English locale. Example: {@code
     * DataGenerator.defaultGenerator(StringDataGenerator::new)}.
     */
    public static <T> T defaultGenerator(Function<GeneratorContext, T> creator) {
        return defaults().create(creator);
    }

    /** Fluent configuration for independent generator instances. */
    public static final class Builder {
        private Locale locale = Locale.ENGLISH;
        private Clock clock = Clock.systemUTC();
        private Long seed;
        private int maxStringLength = 1_000_000;
        private List<CurrencyData> currencies;

        /** Creates a builder; call {@link DataGenerator#builder()} to obtain one. */
        private Builder() {}
        /**
         * Selects the data language. Example: {@code builder.locale(Locale.forLanguageTag("ru"))}.
         * Null and locales without a language are rejected.
         */
        public Builder locale(Locale locale) {
            Objects.requireNonNull(locale, "locale");
            if (locale.getLanguage().isBlank()) throw new IllegalArgumentException("Locale needs a language");
            this.locale = locale;
            return this;
        }
        /** Sets the starting random seed. Use the same seed and call order to repeat a sequence. */
        public Builder seed(long seed) { this.seed = seed; return this; }
        /**
         * Sets the clock for text timestamps. Use {@code Clock.fixed(instant, zone)} for repeatable
         * timestamps. Null is rejected.
         */
        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock"); return this;
        }
        /**
         * Sets the maximum output length in Unicode code points. Zero is allowed; negative values
         * are rejected. Applies to strings and financial references.
         */
        public Builder maxStringLength(int maximum) {
            if (maximum < 0) throw new IllegalArgumentException("Maximum length must be nonnegative");
            maxStringLength = maximum; return this;
        }
        /**
         * Sets an ordered currency catalog. The list is copied. It must be nonempty, contain no
         * null entries, and have unique codes and names ignoring case.
         */
        public Builder currencies(List<CurrencyData> currencies) {
            List<CurrencyData> copy = List.copyOf(Objects.requireNonNull(currencies, "currencies"));
            if (copy.isEmpty()) throw new IllegalArgumentException("Currency catalog must not be empty");
            Set<String> codes = new HashSet<>();
            Set<String> names = new HashSet<>();
            for (CurrencyData currency : copy) {
                if (!codes.add(currency.code()) || !names.add(currency.name().toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Duplicate currency code or name");
                }
            }
            this.currencies = copy; return this;
        }
        /**
         * Creates an independent generator from these settings. Reusing a seeded builder creates
         * the same starting sequence.
         */
        public DataGenerator build() { return new DataGenerator(this); }
    }
}
