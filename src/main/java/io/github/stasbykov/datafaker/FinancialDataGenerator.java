package io.github.stasbykov.datafaker;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**

 * Currency data and formatted financial references for tests. Reference formats do not imply

 * payment-network validity or uniqueness.

 */
public final class FinancialDataGenerator {
    private final GeneratorContext context;
    private final StringDataGenerator strings;

    /**

     * Creates a generator using a shared context. Prefer {@code data.financial()} or {@code

     * data.create(FinancialDataGenerator::new)}.

     */
    public FinancialDataGenerator(GeneratorContext context) {
        this.context = Objects.requireNonNull(context, "context");
        strings = context.strings();
    }
    /** Returns the immutable currency catalog in selection order. */
    public List<CurrencyData> currencies() { return context.currencies(); }
    /**
     * Selects one currency and returns its matching code and name. Use this method when both fields
     * must agree.
     */
    public CurrencyData generateCurrency() {
        List<CurrencyData> currencies = currencies();
        return currencies.get(context.nextInt(currencies.size()));
    }
    /** Selects a random currency code. Separate calls to generateCurrencyName are independent. */
    public String generateCurrencyCode() { return generateCurrency().code(); }
    /**
     * With no argument, selects a random name. With a code, returns its matching name or null for a
     * null, blank or unknown code. Use findCurrencyName when Optional is preferred.
     */
    public String generateCurrencyName() { return generateCurrency().name(); }

    /**

     * Finds a currency by code, ignoring case and surrounding whitespace. Example: {@code

     * financial.findCurrencyByCode(" usd ")}. Null, blank and unknown codes return an empty

     * Optional.

     */
    public Optional<CurrencyData> findCurrencyByCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        String normalized = code.strip().toUpperCase(Locale.ROOT);
        return currencies().stream().filter(c -> c.code().equals(normalized)).findFirst();
    }
    /**
     * Finds a currency by display name, ignoring case and surrounding whitespace. Null, blank and
     * unknown names return an empty Optional.
     */
    public Optional<CurrencyData> findCurrencyByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        String normalized = name.strip();
        return currencies().stream().filter(c -> c.name().equalsIgnoreCase(normalized)).findFirst();
    }
    /**
     * Finds the name for a code. Example: {@code financial.findCurrencyName("EUR").orElseThrow()}.
     * Null, blank and unknown codes return an empty Optional.
     */
    public Optional<String> findCurrencyName(String code) {
        return findCurrencyByCode(code).map(CurrencyData::name);
    }
    /** Finds the code for a display name. Null, blank and unknown names return an empty Optional. */
    public Optional<String> findCurrencyCode(String name) {
        return findCurrencyByName(name).map(CurrencyData::code);
    }
    /**
     * With no argument, selects a random name. With a code, returns its matching name or null for a
     * null, blank or unknown code. Use findCurrencyName when Optional is preferred.
     */
    public String generateCurrencyName(String code) { return findCurrencyName(code).orElse(null); }

    /** Generates 12 random digits. Leading zeros are allowed. Values are not guaranteed to be unique. */
    public String generateRRN() { return strings.generateDigits(12); }
    /**
     * Generates 23 random digits. Leading zeros are allowed. This method does not calculate
     * payment-network check digits or guarantee uniqueness.
     */
    public String generateARN() { return strings.generateDigits(23); }
    /**
     * Generates {@code TRNB} followed by 14 digits, {@code MZN}, and one digit: 22 characters in
     * total. Values are not guaranteed to be unique.
     */
    public String generateTRN() { return strings.generateFromMask("TRNB##############MZN#"); }
    /**
     * Creates a currency description using the configured clock. With no argument, selects a random
     * currency name. The name overload returns null for null or blank input. For strict input
     * checks, use the text generator.
     */
    public String generateCurrencyDescription() {
        return context.texts().generateCurrencyDescription(generateCurrencyName());
    }
    /**
     * Creates a currency description using the configured clock. With no argument, selects a random
     * currency name. The name overload returns null for null or blank input. For strict input
     * checks, use the text generator.
     */
    public String generateCurrencyDescription(String name) {
        return name == null || name.isBlank() ? null : context.texts().generateCurrencyDescription(name);
    }
}
