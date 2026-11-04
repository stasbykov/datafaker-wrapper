package io.github.stasbykov.datafaker;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**

 * Synthetic text templates. Russian is used for the ru language; other languages use English.

 * These templates do not provide factual country data.

 */
public final class TextDataGenerator {
    private final GeneratorContext context;

    /** Creates a text generator using a shared context. Prefer {@code data.texts()}. */
    public TextDataGenerator(GeneratorContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }
    /**
     * Formats a currency name and the current time from the configured clock. Example: {@code
     * data.texts().generateCurrencyDescription("Euro")}. Uses ISO local date-time without an
     * offset. Null and blank names are rejected.
     */
    public String generateCurrencyDescription(String currencyName) {
        String name = GeneratorContext.requireText(currencyName, "currencyName");
        String time = LocalDateTime.now(context.clock()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return (russian() ? "Информация по валюте %s по состоянию на %s"
                : "Information about currency %s as of %s").formatted(name, time);
    }
    /**
     * Formats a country name and a fictional population from 10,000 to 49,999,999 inclusive.
     * Example: {@code data.texts().generateCountryDescription("France")}. Null and blank names are
     * rejected.
     */
    public String generateCountryDescription(String countryName) {
        String name = GeneratorContext.requireText(countryName, "countryName");
        int population = context.nextInt(10_000, 49_999_999);
        return (russian() ? "%s — тестовое описание, условное население: %s человек"
                : "%s — synthetic description, fictional population: %s people").formatted(name, population);
    }
    /**
     * Checks whether the configured language uses Russian text templates; all other languages use
     * English templates.
     */
    private boolean russian() { return context.locale().getLanguage().equals("ru"); }
}
