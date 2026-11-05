package io.github.stasbykov.datafaker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GeneratorsTest {
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-11-05T10:15:30Z"), ZoneOffset.UTC);

    /** Creates a Russian generator with a fixed clock for repeatable assertions. */
    private DataGenerator seeded(long seed) {
        return DataGenerator.builder().locale(Locale.forLanguageTag("ru"))
                .seed(seed).clock(FIXED).build();
    }

    /** Verifies method reference creates configured generators. */
    @Test void methodReferenceCreatesConfiguredGenerators() {
        FinancialDataGenerator financial = DataGenerator.russianGenerator(FinancialDataGenerator::new);
        assertEquals("Российский рубль", financial.findCurrencyName("rub").orElseThrow());
        assertEquals(20, DataGenerator.russianGenerator(StringDataGenerator::new).generate(20).length());
    }

    /** Verifies mixed sequence is repeatable including provider and clock. */
    @Test void mixedSequenceIsRepeatableIncludingProviderAndClock() {
        DataGenerator first = seeded(42), second = seeded(42);
        for (int i = 0; i < 40; i++) {
            assertEquals(first.financial().generateCurrency(), second.financial().generateCurrency());
            assertEquals(first.financial().generateRRN(), second.financial().generateRRN());
            assertEquals(first.financial().generateARN(), second.financial().generateARN());
            assertEquals(first.financial().generateTRN(), second.financial().generateTRN());
            assertEquals(first.strings().generate(3, 50), second.strings().generate(3, 50));
            assertEquals(first.strings().generateFromRegex("[A-Z]{3}-[0-9]{8}"),
                    second.strings().generateFromRegex("[A-Z]{3}-[0-9]{8}"));
            assertEquals(first.address().generateCountry(), second.address().generateCountry());
            assertEquals(first.texts().generateCountryDescription("Россия"),
                    second.texts().generateCountryDescription("Россия"));
            assertEquals(first.financial().generateCurrencyDescription(),
                    second.financial().generateCurrencyDescription());
        }
    }

    /** Verifies references match documented formats. */
    @Test void referencesMatchDocumentedFormats() {
        var financial = seeded(5).financial();
        for (int i = 0; i < 100; i++) {
            assertTrue(financial.generateRRN().matches("[0-9]{12}"));
            assertTrue(financial.generateARN().matches("[0-9]{23}"));
            assertTrue(financial.generateTRN().matches("TRNB[0-9]{14}MZN[0-9]"));
        }
    }

    /** Verifies paired currencies match lookups. */
    @Test void pairedCurrenciesMatchLookups() {
        var financial = seeded(4).financial();
        assertEquals(14, financial.currencies().size());
        for (int i = 0; i < 100; i++) {
            var currency = financial.generateCurrency();
            assertEquals(currency, financial.findCurrencyByCode(currency.code()).orElseThrow());
            assertEquals(currency, financial.findCurrencyByName(currency.name()).orElseThrow());
        }
        assertEquals("USD", financial.findCurrencyCode("  доллар сша  ").orElseThrow());
        assertEquals("Российский рубль", financial.findCurrencyName(" rub ").orElseThrow());
    }

    /** Verifies currency lookup handles absent values. */
    @Test void currencyLookupHandlesAbsentValues() {
        var financial = seeded(1).financial();
        for (String value : new String[] {null, "", "  ", "unknown"}) {
            assertTrue(financial.findCurrencyByCode(value).isEmpty());
            assertTrue(financial.findCurrencyByName(value).isEmpty());
            assertNull(financial.generateCurrencyName(value));
        }
        assertNull(financial.generateCurrencyDescription((String) null));
        assertNull(financial.generateCurrencyDescription(" "));
    }

    /** Verifies locale affects currency names. */
    @Test void localeAffectsCurrencyNames() {
        String russian = seeded(1).financial().findCurrencyName("RUB").orElseThrow();
        String english = DataGenerator.defaults().financial().findCurrencyName("RUB").orElseThrow();
        assertNotEquals(russian, english);
        assertTrue(english.toLowerCase(Locale.ROOT).contains("ruble"));
    }

    /** Verifies configuration is copied and builder reuse does not mutate instances. */
    @Test void configurationIsCopiedAndBuilderReuseDoesNotMutateInstances() {
        List<CurrencyData> catalog = new ArrayList<>(List.of(new CurrencyData("tst", "Test money")));
        var builder = DataGenerator.builder().seed(1).currencies(catalog);
        catalog.clear();
        var first = builder.build();
        builder.currencies(List.of(new CurrencyData("NEW", "New money")));
        assertEquals("TST", first.financial().generateCurrencyCode());
        assertEquals("NEW", builder.build().financial().generateCurrencyCode());
        assertThrows(UnsupportedOperationException.class, () -> first.financial().currencies().clear());
    }

    /** Verifies invalid catalogs fail fast. */
    @Test void invalidCatalogsFailFast() {
        assertThrows(IllegalArgumentException.class, () -> new CurrencyData("US", "Dollar"));
        assertThrows(IllegalArgumentException.class, () -> new CurrencyData("USD", " "));
        assertThrows(NullPointerException.class, () -> new CurrencyData(null, "Dollar"));
        assertThrows(IllegalArgumentException.class, () -> DataGenerator.builder().currencies(List.of()));
        assertThrows(IllegalArgumentException.class, () -> DataGenerator.builder().currencies(List.of(
                new CurrencyData("USD", "One"), new CurrencyData("usd", "Two"))));
        assertThrows(IllegalArgumentException.class, () -> DataGenerator.builder().currencies(List.of(
                new CurrencyData("USD", "One"), new CurrencyData("EUR", "one"))));
    }

    /** Checks the documented contract for each supplied input. */
    @ParameterizedTest @ValueSource(ints = {0, 1, 20, 1000})
    void stringsRespectExactLengthsAndAlphabets(int length) {
        var strings = seeded(1).strings();
        assertEquals(length, strings.generate(length).length());
        assertTrue(strings.generateDigits(length).matches("[0-9]{" + length + "}"));
        assertTrue(strings.generateLatin(length).matches("[a-zA-Z]{" + length + "}"));
        assertTrue(strings.generateCyrillic(length).matches("[А-ЯЁа-яё]{" + length + "}"));
        String unicode = strings.generateFromAlphabet(length, "😀🚀я");
        assertEquals(length, unicode.codePointCount(0, unicode.length()));
        Set<Integer> allowed = Set.of(0x1F600, 0x1F680, (int) 'я');
        assertTrue(unicode.codePoints().allMatch(allowed::contains));
    }

    /** Verifies range boundaries are inclusive. */
    @Test void rangeBoundariesAreInclusive() {
        var strings = seeded(50).strings();
        Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < 100; i++) seen.add(strings.generate(0, 2).length());
        assertEquals(Set.of(0, 1, 2), seen);
        assertEquals("", strings.generate(0, 0));
    }

    /** Verifies duplicate alphabet symbols do not change seeded results. */
    @Test void duplicateAlphabetSymbolsDoNotChangeSeededResults() {
        assertEquals(seeded(3).strings().generateFromAlphabet(100, "AB😀"),
                seeded(3).strings().generateFromAlphabet(100, "AABB😀😀"));
    }

    /** Verifies invalid string arguments fail. */
    @Test void invalidStringArgumentsFail() {
        var strings = DataGenerator.builder().maxStringLength(10).build().strings();
        assertThrows(IllegalArgumentException.class, () -> strings.generate(-1));
        assertThrows(IllegalArgumentException.class, () -> strings.generate(3, 2));
        assertThrows(IllegalArgumentException.class, () -> strings.generate(11));
        assertThrows(IllegalArgumentException.class, () -> strings.generateFromAlphabet(0, ""));
        assertThrows(NullPointerException.class, () -> strings.generateFromAlphabet(1, null));
        assertThrows(IllegalArgumentException.class, () -> strings.generateFromAlphabet(1, "\uD800"));
        assertThrows(IllegalArgumentException.class, () -> strings.generateFromMask("\uDC00"));
    }

    /** Verifies masks handle escapes case and literals. */
    @Test void masksHandleEscapesCaseAndLiterals() {
        var strings = seeded(7).strings();
        assertTrue(strings.generateFromMask("REQ-####-????").matches("REQ-[0-9]{4}-[a-z]{4}"));
        assertTrue(strings.generateFromMask("??-##", true).matches("[A-Z]{2}-[0-9]{2}"));
        assertEquals("?#\\😀", strings.generateFromMask("\\?\\#\\\\😀"));
        assertEquals("", strings.generateFromMask(""));
        assertThrows(IllegalArgumentException.class, () -> strings.generateFromMask("abc\\"));
        var limited = DataGenerator.builder().maxStringLength(1).build().strings();
        assertEquals("?", limited.generateFromMask("\\?"));
        assertThrows(IllegalArgumentException.class, () -> limited.generateFromMask("??"));
    }

    /** Checks the documented contract for each supplied input. */
    @ParameterizedTest @ValueSource(strings = {"[A-Z]{3}-[0-9]{8}", "[А-ЯЁа-яё]{10,20}", "(AB|CD){2,5}", ""})
    void regexOutputMatchesJavaPattern(String pattern) {
        var strings = seeded(6).strings();
        for (int i = 0; i < 20; i++) assertTrue(strings.generateFromRegex(pattern).matches(pattern));
    }

    /** Verifies regex errors and length limit are visible. */
    @Test void regexErrorsAndLengthLimitAreVisible() {
        var strings = DataGenerator.builder().maxStringLength(5).build().strings();
        assertThrows(java.util.regex.PatternSyntaxException.class, () -> strings.generateFromRegex("["));
        assertThrows(NullPointerException.class, () -> strings.generateFromRegex(null));
        assertThrows(IllegalStateException.class, () -> strings.generateFromRegex("[0-9]{6}"));
    }

    /** Verifies description uses configured clock and zone. */
    @Test void descriptionUsesConfiguredClockAndZone() {
        var data = seeded(1);
        assertEquals("Информация по валюте Рубль по состоянию на 2026-11-05T10:15:30",
                data.texts().generateCurrencyDescription("Рубль"));
        var other = DataGenerator.builder().clock(FIXED.withZone(ZoneId.of("Europe/Moscow"))).build();
        assertTrue(other.texts().generateCurrencyDescription("Ruble").endsWith("2026-11-05T13:15:30"));
        assertTrue(data.texts().generateCountryDescription("Россия").contains("условное население"));
        assertThrows(IllegalArgumentException.class, () -> data.texts().generateCountryDescription(" "));
        assertThrows(NullPointerException.class, () -> data.texts().generateCurrencyDescription(null));
    }

    /** Verifies address fields are available in both locales. */
    @Test void addressFieldsAreAvailableInBothLocales() {
        for (var data : List.of(seeded(8), DataGenerator.defaults())) {
            assertFalse(data.address().generateCountry().isBlank());
            assertFalse(data.address().generateCity().isBlank());
            assertFalse(data.address().generateStreetAddress().isBlank());
            assertFalse(data.address().generateZipCode().isBlank());
        }
    }

    /** Verifies custom generator uses same context and seed. */
    @Test void customGeneratorUsesSameContextAndSeed() {
        record ReferenceGenerator(GeneratorContext context) {
            /** Creates a prefixed reference using the shared context. */
            String next() { return "CUSTOM-" + context.strings().generateDigits(8); }
        }
        assertEquals(seeded(9).create(ReferenceGenerator::new).next(),
                seeded(9).create(ReferenceGenerator::new).next());
    }

    /** Verifies builder and factory reject invalid configuration. */
    @Test void builderAndFactoryRejectInvalidConfiguration() {
        assertThrows(NullPointerException.class, () -> DataGenerator.builder().locale(null));
        assertThrows(IllegalArgumentException.class, () -> DataGenerator.builder().locale(Locale.ROOT));
        assertThrows(NullPointerException.class, () -> DataGenerator.builder().clock(null));
        assertThrows(IllegalArgumentException.class, () -> DataGenerator.builder().maxStringLength(-1));
        assertThrows(NullPointerException.class, () -> seeded(1).create(null));
        assertThrows(NullPointerException.class, () -> seeded(1).create(context -> null));
        var context = seeded(1).create(c -> c);
        assertEquals(Integer.MAX_VALUE, context.nextInt(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(Integer.MIN_VALUE, context.nextInt(Integer.MIN_VALUE, Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> context.nextInt(2, 1));
    }

    /** Verifies public api does not expose datafaker. */
    @Test void publicApiDoesNotExposeDatafaker() {
        List<Class<?>> api = List.of(DataGenerator.class, DataGenerator.Builder.class,
                GeneratorContext.class, FinancialDataGenerator.class, StringDataGenerator.class,
                AddressDataGenerator.class, TextDataGenerator.class, UUIDDataGenerator.class, CurrencyData.class);
        for (Class<?> type : api) {
            for (var method : type.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers())) assertFalse(method.toGenericString().contains("net.datafaker"));
            }
            for (var constructor : type.getConstructors()) assertFalse(constructor.toGenericString().contains("net.datafaker"));
            for (var field : type.getFields()) assertFalse(field.toGenericString().contains("net.datafaker"));
        }
    }
}
