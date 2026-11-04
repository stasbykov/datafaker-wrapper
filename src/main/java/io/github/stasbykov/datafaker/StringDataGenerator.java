package io.github.stasbykov.datafaker;

import java.util.Objects;
import java.util.regex.Pattern;

/**

 * Generates strings measured in Unicode code points, not bytes, UTF-16 units or visible

 * characters. Zero length is allowed; invalid ranges and lengths above the configured limit are

 * rejected.

 */
public final class StringDataGenerator {
    private static final String DIGITS = "0123456789";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LATIN = LOWER + UPPER;
    private static final String CYRILLIC = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"
            + "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ";
    private final GeneratorContext context;

    /**

     * Creates a string generator using a shared context. Prefer {@code data.strings()} or {@code

     * data.create(StringDataGenerator::new)}.

     */
    public StringDataGenerator(GeneratorContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }
    /**
     * Generates Latin letters in both cases and digits. Example: {@code strings.generate(20)} or
     * {@code strings.generate(5, 15)}. Both range boundaries are included; zero length is allowed.
     * Character categories are not guaranteed to occur.
     */
    public String generate(int length) { return generateFromAlphabet(length, LATIN + DIGITS); }
    /**
     * Generates Latin letters in both cases and digits. Example: {@code strings.generate(20)} or
     * {@code strings.generate(5, 15)}. Both range boundaries are included; zero length is allowed.
     * Character categories are not guaranteed to occur.
     */
    public String generate(int minLength, int maxLength) {
        return generateFromAlphabet(minLength, maxLength, LATIN + DIGITS);
    }
    /**
     * Generates exactly the requested number of ASCII digits. Example: {@code
     * strings.generateDigits(12)}. Leading zeros are allowed.
     */
    public String generateDigits(int length) { return generateFromAlphabet(length, DIGITS); }
    /**
     * Generates exactly the requested number of Latin letters from a-z and A-Z. Locale does not
     * change this alphabet.
     */
    public String generateLatin(int length) { return generateFromAlphabet(length, LATIN); }
    /**
     * Generates exactly the requested number of Russian letters in both cases, including yo. Locale
     * does not change this alphabet.
     */
    public String generateCyrillic(int length) { return generateFromAlphabet(length, CYRILLIC); }
    /**
     * Generates a string from the supplied alphabet. Example: {@code
     * strings.generateFromAlphabet(8, "ABC123")}. Range boundaries are included. Duplicate symbols
     * are ignored. Empty alphabets and unpaired surrogates are rejected, even for zero length.
     */
    public String generateFromAlphabet(int length, String alphabet) {
        return generateFromAlphabet(length, length, alphabet);
    }

    /**

     * Generates a string from the supplied alphabet. Example: {@code

     * strings.generateFromAlphabet(8, "ABC123")}. Range boundaries are included. Duplicate symbols

     * are ignored. Empty alphabets and unpaired surrogates are rejected, even for zero length.

     */
    public String generateFromAlphabet(int minLength, int maxLength, String alphabet) {
        /**
         * Checks inclusive length boundaries against the configured code point limit before
         * allocating output.
         */
        validateRange(minLength, maxLength);
        int[] symbols = codePoints(Objects.requireNonNull(alphabet, "alphabet"));
        symbols = java.util.Arrays.stream(symbols).distinct().toArray();
        if (symbols.length == 0) throw new IllegalArgumentException("Alphabet must not be empty");
        int length = minLength == maxLength ? minLength : context.nextInt(minLength, maxLength);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) result.appendCodePoint(symbols[context.nextInt(symbols.length)]);
        return result.toString();
    }

    /**

     * Replaces each {@code #} with a digit and each {@code ?} with a Latin letter. Example: {@code

     * strings.generateFromMask("REF-####-??")}. Letters are lowercase unless uppercase is true. A

     * backslash makes the next code point literal; a final unpaired backslash is rejected.

     */
    public String generateFromMask(String mask) { return generateFromMask(mask, false); }

    /**

     * Replaces each {@code #} with a digit and each {@code ?} with a Latin letter. Example: {@code

     * strings.generateFromMask("REF-####-??")}. Letters are lowercase unless uppercase is true. A

     * backslash makes the next code point literal; a final unpaired backslash is rejected.

     */
    public String generateFromMask(String mask, boolean uppercase) {
        int[] symbols = codePoints(Objects.requireNonNull(mask, "mask"));
        int outputLength = 0;
        for (int i = 0; i < symbols.length; i++) {
            if (symbols[i] == '\\' && ++i == symbols.length) {
                throw new IllegalArgumentException("Dangling mask escape");
            }
            outputLength++;
        }
        /**
         * Checks inclusive length boundaries against the configured code point limit before
         * allocating output.
         */
        validateRange(outputLength, outputLength);
        String letters = uppercase ? UPPER : LOWER;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < symbols.length; i++) {
            int cp = symbols[i];
            if (cp == '\\') result.appendCodePoint(symbols[++i]);
            else if (cp == '?') result.append(letters.charAt(context.nextInt(letters.length())));
            else if (cp == '#') result.append(DIGITS.charAt(context.nextInt(DIGITS.length())));
            else result.appendCodePoint(cp);
        }
        return result.toString();
    }

    /**

     * Generates a value from the provider-supported regex subset and checks it with Java Pattern.

     * Example: {@code strings.generateFromRegex("[A-Z]{3}-[0-9]{8}")}. Use trusted, bounded

     * patterns. The length limit is checked after allocation. Invalid syntax is rejected; a

     * mismatched or oversized result causes IllegalStateException.

     */
    public String generateFromRegex(String regex) {
        Objects.requireNonNull(regex, "regex");
        Pattern pattern = Pattern.compile(regex);
        String result = context.adapter().regex(regex);
        int length = result.codePointCount(0, result.length());
        if (length > context.maxStringLength()) {
            throw new IllegalStateException("Regex result exceeds the configured length limit");
        }
        /** Converts text to Unicode code points and rejects unpaired UTF-16 surrogates. */
        codePoints(result);
        if (!pattern.matcher(result).matches()) {
            throw new IllegalStateException("Provider result does not match the requested regex");
        }
        return result;
    }

    /**

     * Checks inclusive length boundaries against the configured code point limit before allocating

     * output.

     */
    private void validateRange(int min, int max) {
        if (min < 0 || max < min || max > context.maxStringLength()) {
            throw new IllegalArgumentException("Expected 0 <= min <= max <= " + context.maxStringLength());
        }
    }
    /** Converts text to Unicode code points and rejects unpaired UTF-16 surrogates. */
    private static int[] codePoints(String value) {
        int[] result = value.codePoints().toArray();
        for (int cp : result) {
            if (cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE) {
                throw new IllegalArgumentException("Unpaired surrogate");
            }
        }
        return result;
    }
}
