package io.github.stasbykov.datafaker;

import java.util.Objects;

/**

 * Localized address fields for tests. Separate calls do not form a correlated real address.

 * Missing locale data may use the provider fallback.

 */
public final class AddressDataGenerator {
    private final GeneratorContext context;

    /** Creates an address generator using a shared context. Prefer {@code data.address()}. */
    public AddressDataGenerator(GeneratorContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }
    /**
     * Generates a country name using the configured locale. Example: {@code
     * data.address().generateCountry()}.
     */
    public String generateCountry() { return context.adapter().country(); }
    /**
     * Generates a city name using the configured locale. Separate country and city calls are not
     * correlated.
     */
    public String generateCity() { return context.adapter().city(); }
    /**
     * Generates a street address using the configured locale. It is not guaranteed to match a
     * separately generated city.
     */
    public String generateStreetAddress() { return context.adapter().streetAddress(); }
    /**
     * Generates a postal code using the configured locale. It is not guaranteed to match a
     * separately generated address.
     */
    public String generateZipCode() { return context.adapter().zipCode(); }
    /**
     * Creates a fictional country description. With no argument, generates a country name first.
     * The country argument must be nonnull and nonblank. Population is synthetic, not factual.
     */
    public String generateCountryDescription() { return generateCountryDescription(generateCountry()); }
    /**
     * Creates a fictional country description. With no argument, generates a country name first.
     * The country argument must be nonnull and nonblank. Population is synthetic, not factual.
     */
    public String generateCountryDescription(String country) {
        return context.texts().generateCountryDescription(country);
    }
}
