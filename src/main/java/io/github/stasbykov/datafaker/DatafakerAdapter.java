package io.github.stasbykov.datafaker;

import net.datafaker.Faker;
import java.util.Locale;
import java.util.Random;

/** Internal adapter for the data provider. No provider types are exposed by the public API. */
final class DatafakerAdapter {
    private final Faker faker;

    /** Creates the internal provider with the configured locale and shared random stream. */
    DatafakerAdapter(Locale locale, Random random) {
        faker = new Faker(locale, random);
    }

    /** Returns a localized country name from the provider. */
    String country() { return faker.address().country(); }
    /** Returns a localized city name from the provider. */
    String city() { return faker.address().city(); }
    /** Returns a localized street address from the provider. */
    String streetAddress() { return faker.address().streetAddress(); }
    /** Returns a localized postal code from the provider. */
    String zipCode() { return faker.address().zipCode(); }
    /** Asks the provider to generate a value from its supported regex subset. */
    String regex(String regex) { return faker.regexify(regex); }
}
