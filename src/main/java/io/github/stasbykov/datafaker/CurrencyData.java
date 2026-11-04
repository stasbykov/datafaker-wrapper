package io.github.stasbykov.datafaker;

import java.util.Locale;
import java.util.Objects;

/** A currency code and its matching display name.
 * @param code three-letter uppercase code
 * @param name nonblank display name */
/**
 * Creates a matching currency code and name. Example: {@code new CurrencyData("EUR", "Euro")}.
 * Strips surrounding whitespace, uppercases the code with Locale.ROOT, and rejects nulls, blank
 * names and codes other than three ASCII letters.
 */
public record CurrencyData(String code, String name) {
    /**
     * Creates a matching currency code and name. Example: {@code new CurrencyData("EUR", "Euro")}.
     * Strips surrounding whitespace, uppercases the code with Locale.ROOT, and rejects nulls, blank
     * names and codes other than three ASCII letters.
     */
    public CurrencyData {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        code = code.strip().toUpperCase(Locale.ROOT);
        name = name.strip();
        if (!code.matches("[A-Z]{3}") || name.isEmpty()) {
            throw new IllegalArgumentException("Expected a three-letter code and a nonblank name");
        }
    }
}
