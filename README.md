# Datafaker Wrapper

A Java library for generating test strings, currencies, financial references,
address fields, UUIDs, and sample text. Configure the language, random seed, clock,
and string length limit in one place.

Requires Java 17 or later and Maven 3.9 or later. Uses Datafaker internally;
its types are not part of the public API.

## Build and install

```bash
mvn clean verify
mvn install
```

After installing locally, add this dependency to your test project:

```xml
<dependency>
    <groupId>io.github.stasbykov</groupId>
    <artifactId>datafaker-wrapper</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

This version is not published to Maven Central. The build produces the library
JAR, a sources JAR, and a Javadoc JAR. Maven resolves Datafaker and its dependencies;
they are not bundled into the library JAR.

## Quick start

```java
import io.github.stasbykov.datafaker.DataGenerator;
import java.util.Locale;

DataGenerator data = DataGenerator.builder()
        .locale(Locale.forLanguageTag("ru"))
        .seed(42L)
        .build();

String rrn = data.financial().generateRRN();
String reference = data.strings().generateFromRegex("[A-Z]{3}-[0-9]{8}");
String country = data.address().generateCountry();
var currency = data.financial().generateCurrency();

String code = currency.code();
String name = currency.name();
```

Use `DataGenerator.russian()` for Russian data or `DataGenerator.defaults()` for
English data. A builder uses English, the UTC system clock, no fixed seed, and a
maximum string length of 1,000,000 Unicode code points by default.

## Configuration

| Setting | Purpose | Example |
|---|---|---|
| `locale(Locale)` | Choose the data language | `.locale(Locale.FRENCH)` |
| `seed(long)` | Repeat a random sequence | `.seed(42)` |
| `clock(Clock)` | Control timestamps | `.clock(Clock.fixed(instant, ZoneOffset.UTC))` |
| `maxStringLength(int)` | Limit string output length | `.maxStringLength(100_000)` |
| `currencies(List<CurrencyData>)` | Supply an ordered currency catalog | `.currencies(List.of(new CurrencyData("EUR", "Euro")))` |

Call `build()` to create an instance. Changing the builder later does not change
that instance. Null settings, a locale without a language, negative length limits,
and invalid currency catalogs are rejected.

## Strings

```java
var strings = data.strings();

strings.generate(20);                         // Latin letters and digits
strings.generate(5, 15);                      // Length from 5 to 15, inclusive
strings.generateDigits(20);                   // Digits; leading zeros are allowed
strings.generateLatin(20);                    // a-z and A-Z
strings.generateCyrillic(20);                 // Russian letters in both cases
strings.generateFromAlphabet(8, "ABC123");
strings.generateFromAlphabet(5, 10, "😀🚀A");
strings.generateFromMask("REQ-####-????");    // # is a digit; ? is a lowercase letter
strings.generateFromMask("??-####", true);    // ? is an uppercase letter
strings.generateFromMask("\\?\\#");           // Literal ?#
strings.generateFromRegex("[A-Z]{3}-[0-9]{8}");
```

Lengths count **Unicode code points**, not bytes or `String.length()` units.
For example, `😀` counts as one code point but takes two UTF-16 units in Java.
A visible character can contain several code points.

- Zero length is allowed. Negative lengths and reversed ranges are rejected.
- An alphabet must be nonempty, even for zero-length output. Duplicate symbols
  are ignored: `AAB` has the same meaning as `AB`.
- Unpaired UTF-16 surrogates are rejected.
- The locale does not change an explicitly selected alphabet.
- Choosing letters and digits does not guarantee that both appear in a result.
- In masks, a backslash escapes the next code point. A trailing backslash is an error.
- The configured length limit applies to strings and financial references,
  including literal characters in masks.

Regex generation supports the subset available in Datafaker, not every Java
`Pattern` feature. Use trusted expressions with bounded repetitions, such as
`[A-Z]{3,10}`. Invalid expressions are rejected. Every generated result is checked
with Java `Pattern`; a mismatch or an oversized result causes
`IllegalStateException`. For regex, the length check happens **after generation**,
so it does not prevent an expression from allocating a large value.

## Currencies

Use `generateCurrency()` when a code and name must describe the same currency:

```java
var currency = data.financial().generateCurrency();
String code = currency.code();
String name = currency.name();
```

`generateCurrencyCode()` and the no-argument `generateCurrencyName()` each select
an independent random currency.

Lookups do not draw random values:

```java
var financial = data.financial();
var euro = financial.findCurrencyByCode(" eur ");
var euroName = financial.findCurrencyName("EUR");
var euroCode = financial.findCurrencyCode("Euro");
var namedCurrency = financial.findCurrencyByName("Euro");
```

These methods return `Optional.empty()` for null, blank, or unknown input. Names
must match the configured catalog language. Lookups ignore case and surrounding
whitespace; internal whitespace is significant. `generateCurrencyName(code)` is
also available when a nullable result is useful.

The default catalog contains USD, EUR, RUB, GBP, JPY, CNY, CHF, CAD, AUD, INR,
KZT, UAH, BRL, and KRW, in that order. Russian names are defined in the library.
Other locales use JDK currency names and its fallback rules. This is a selected
catalog, not a complete list of currencies.

To supply your own names:

```java
import io.github.stasbykov.datafaker.CurrencyData;
import java.util.List;

DataGenerator custom = DataGenerator.builder()
        .currencies(List.of(
                new CurrencyData("USD", "US dollar"),
                new CurrencyData("EUR", "Euro")))
        .seed(42)
        .build();
```

The list is copied and must not be empty. Codes and names must each be unique,
ignoring case. A code must contain three ASCII letters; a name must be nonblank.
Custom codes are not checked against ISO 4217. Custom names are used as supplied,
without automatic translation. `financial.currencies()` returns the immutable catalog.

## Financial references

| Method | Output |
|---|---|
| `generateRRN()` | 12 random digits |
| `generateARN()` | 23 random digits |
| `generateTRN()` | `TRNB` + 14 digits + `MZN` + 1 digit; 22 characters total |

Leading zeros are allowed. These methods generate test values with the listed
formats. They do not guarantee uniqueness, calculate payment-network check digits,
or validate acquirer information.

## UUIDs

Use `data.uuids()` or `data.create(UUIDDataGenerator::new)` to obtain a
`UUIDDataGenerator`. The short factory
`DataGenerator.russianGenerator(UUIDDataGenerator::new)` also works.

```java
import java.time.Duration;
import java.time.Instant;

var uuids = data.uuids();
var random = uuids.generateUUIDv4();
var currentMinute = uuids.generateUUIDv3ForWindow(Duration.ofMinutes(1));
var atTime = uuids.generateUUIDv3ForWindow(
        Instant.parse("2026-11-05T10:15:30Z"), Duration.ofMinutes(1));
var named = uuids.generateUUIDv3FromName("order-42");
var combined = uuids.generateUUIDv3FromParts("order", 42, "payment");
String logId = "test-" + uuids.generateUUIDv4();
String requestId = uuids.generateUUIDv3ForWindow(Duration.ofMinutes(1)).toString();
```

The `generateUUIDv4()` method generates a **version 4** UUID from the shared random
stream. A configured seed and the same call order reproduce the sequence.
These values are intended for tests and are not suitable as security tokens.

The `generateUUIDv3FromName`, `generateUUIDv3FromParts`, and
`generateUUIDv3ForWindow` methods generate **version 3** UUIDs.
They consume no random values and are independent of the seed and locale.
All generation methods return `UUID`. Call `.toString()` for canonical text.
To add a prefix, concatenate it with any generated UUID as shown above.

### UUID version 7

`generateUUIDv7()` combines the configured clock's Unix timestamp in milliseconds
with new random bits from the shared seeded stream. Use it for time-based test
identifiers. You can also supply an explicit instant:

```java
var uuid = data.uuids().generateUUIDv7();
String requestId = data.uuids().generateUUIDv7().toString();
var atTime = data.uuids().generateUUIDv7(
        Instant.parse("2026-11-05T10:15:30Z"));
String atTimeText = data.uuids().generateUUIDv7(
        Instant.parse("2026-11-05T10:15:30Z")).toString();
```

The [RFC 9562 layout](https://www.rfc-editor.org/rfc/rfc9562.html#section-5.7)
stores the timestamp in 48 bits and uses 74 random bits, plus the version and
variant fields. Supported timestamps start at the Unix epoch, inclusive, and
end at 2^48 milliseconds, exclusive. Earlier or later instants are rejected
before consuming random values. Sub-millisecond precision is discarded.

Calls in the same millisecond draw new random bits and have random order.
Clock rollback is reflected in the timestamp. There is no monotonic counter.
For timestamp ordering across the supported range, compare canonical UUID
strings or unsigned bytes; Java's UUID.compareTo uses signed long comparisons.

For repeatable sequences, use the same seed, call order, and fixed clock
or explicit instant. Repeating a call on one generator advances its random
stream. For a stable ID within an epoch-aligned window, use the version 3
`generateUUIDv3ForWindow(Duration)` API described below. Version 7 uses the same test-data
random source as version 4 and is not intended for security tokens.

### Stable UUIDs within a time window

Windows start at the Unix epoch and include their start but exclude their end.
For a one-minute window, calls from 10:15:00 through 10:15:59.999999999 produce
the same UUID. At 10:16:00, the window index changes. A call at 10:15:59 does
not start a fresh 60-second period.

The current-time overload reads the configured `Clock` on every call.
Use `Clock.fixed(...)` for reproducible tests or supply an explicit `Instant`.
The clock's zone does not affect an instant's window. Independent instances
with the same window index return the same UUID.

The index is calculated as floor(epoch nanoseconds / window nanoseconds).
Its decimal string is encoded as UTF-8 and passed to `UUID.nameUUIDFromBytes`.
Durations must be positive. Fractional-second windows, including a one-nanosecond
window, are supported. Dates before 1970 use floor division, and large values
are calculated without overflowing a long.

Only the index is hashed. Different window sizes with the same index share
a UUID, as does a direct string call with that decimal index. This is useful
for global repeatability; it does not create a separate ID namespace per size.
UUID hashes can theoretically collide.

### Names and multiple parts

A string is hashed exactly as supplied using UTF-8. Case and whitespace matter,
and an empty string is allowed. Null strings and null argument arrays are rejected.

For multiple parts, each nonnull value is converted with `toString()` and encoded
as its UTF-16 string length, a colon, and its text. A null part uses `-1:`.
For example, parts `"a", "b"` encode as `"1:a1:b"`. This framing distinguishes
`("a|b", "c")` from `("a", "b|c")` and a null part from the text `"null"`.
The `generateUUIDv3FromName` method can reproduce a multipart UUID by hashing that
encoded name. There is no separate namespace for multipart names.

Parts must have stable string representations for repeatability. Values of
different types with identical text are equivalent. Arrays and nested objects
are not recursively serialized; convert them into stable text first.
Use `generateUUIDv3FromParts("name")` for a single encoded part.
Use `generateUUIDv3FromName("name")` to hash the exact string instead.

## Address fields

```java
data.address().generateCountry();
data.address().generateCity();
data.address().generateStreetAddress();
data.address().generateZipCode();
```

Datafaker supplies these fields using the configured locale. Missing locale data
may fall back to another language. Separate calls do not produce a guaranteed
matching country, city, street, and postal code.

## Sample text

```java
data.texts().generateCurrencyDescription("Euro");
data.texts().generateCountryDescription("France");
```

Russian templates are used for the `ru` language; other languages use English.
Country descriptions explicitly use a fictional population from 10,000 to
49,999,999 inclusive. Currency descriptions use the configured clock and ISO local
date-time format, without a time-zone suffix. Fractional second precision may vary.

The text methods reject null and blank names. Null causes `NullPointerException`;
blank text causes `IllegalArgumentException`.

Convenience methods are also available:

- `financial().generateCurrencyDescription()` selects a random currency name.
- `financial().generateCurrencyDescription(name)` returns null for null or blank input.
- `address().generateCountryDescription()` generates a country name first.
- `address().generateCountryDescription(name)` requires a nonnull, nonblank name.

## Repeatable data and parallel tests

```java
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

DataGenerator repeatable = DataGenerator.builder()
        .locale(Locale.ENGLISH)
        .seed(42)
        .clock(Clock.fixed(
                Instant.parse("2026-11-05T10:15:30Z"),
                ZoneOffset.UTC))
        .build();
```

The same seed, settings, JDK and dependency versions, and call order produce the
same sequence. Every generator in an instance shares its random stream, including
Datafaker. Adding a call can change later results. Results may change when you
upgrade the library, JDK, or Datafaker.

A seed does not freeze time. Use a fixed clock when timestamps must repeat.
The clock's time zone is independent of the locale.

Create one `DataGenerator` per test or independent task. Concurrent sharing is not
part of the supported contract and cannot provide a repeatable call order.
Random values can repeat. These generators are for test data, not secrets.

## Custom generators

A custom generator receives `GeneratorContext`, which provides the configured
generators, locale, clock, catalog, length limit, and random integer methods.

```java
import io.github.stasbykov.datafaker.GeneratorContext;
import java.util.Objects;

public final class OrderDataGenerator {
    private final GeneratorContext context;

    /** Creates an order generator using the supplied settings and random stream. */
    public OrderDataGenerator(GeneratorContext context) {
        this.context = Objects.requireNonNull(context);
    }

    /** Generates an order reference with a fixed prefix and 12 random digits. */
    public String generateOrderNumber() {
        return "ORD-" + context.strings().generateDigits(12);
    }
}
```

```java
OrderDataGenerator orders = data.create(OrderDataGenerator::new);
```

`context.nextInt(bound)` excludes its upper bound. `context.nextInt(min, max)`
includes both boundaries. Context generator factories share the same random stream.

For a single generator, use a factory directly:

```java
import io.github.stasbykov.datafaker.FinancialDataGenerator;

FinancialDataGenerator financial =
        DataGenerator.russianGenerator(FinancialDataGenerator::new);
```

`defaultGenerator(factory)` uses English. Both factory methods use an unfixed seed.
Use the builder and `create(factory)` when you need more settings.

## API documentation and checks

Every declared method and constructor has an English usage comment. Generated
API documentation is available in `target/reports/apidocs/index.html` after
`mvn verify`, and in the Javadoc JAR under `target/`.

Tests cover formats, currency lookups, locale behavior, Unicode, string boundaries,
masks, regex, repeatable sequences, clocks, custom generators, configuration
copies, and public API isolation. GitHub Actions runs the build on Java 17 and 21.

Provider documentation: [Datafaker usage](https://www.datafaker.net/documentation/usage/)
and [string expressions](https://www.datafaker.net/documentation/expressions/).
