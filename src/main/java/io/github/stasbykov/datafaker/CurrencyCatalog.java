package io.github.stasbykov.datafaker;

import java.util.Currency;
import java.util.List;
import java.util.Locale;

/** Default currency names in a stable selection order. */
final class CurrencyCatalog {
    private static final List<CurrencyData> RUSSIAN = List.of(
            new CurrencyData("USD", "Доллар США"),
            new CurrencyData("EUR", "Евро"),
            new CurrencyData("RUB", "Российский рубль"),
            new CurrencyData("GBP", "Фунт стерлингов Великобритании"),
            new CurrencyData("JPY", "Японская иена"),
            new CurrencyData("CNY", "Китайский юань"),
            new CurrencyData("CHF", "Швейцарский франк"),
            new CurrencyData("CAD", "Канадский доллар"),
            new CurrencyData("AUD", "Австралийский доллар"),
            new CurrencyData("INR", "Индийская рупия"),
            new CurrencyData("KZT", "Казахстанский тенге"),
            new CurrencyData("UAH", "Украинская гривна"),
            new CurrencyData("BRL", "Бразильский реал"),
            new CurrencyData("KRW", "Южнокорейская вона"));

    /** Prevents creation of this catalog utility. */
    private CurrencyCatalog() {}

    /**

     * Builds the default ordered currency catalog. Russian uses explicit names; other locales use

     * JDK currency display names.

     */
    static List<CurrencyData> forLocale(Locale locale) {
        if (locale.getLanguage().equals("ru")) return RUSSIAN;
        return RUSSIAN.stream().map(currency -> new CurrencyData(currency.code(),
                Currency.getInstance(currency.code()).getDisplayName(locale))).toList();
    }
}
