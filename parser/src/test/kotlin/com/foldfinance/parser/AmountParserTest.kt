package com.foldfinance.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AmountParserTest {

    private fun parse(s: String) = requireNotNull(AmountParser.parse(s)) { "not parsed: '$s'" }

    private fun assertAmount(s: String, minor: Long, currency: String?) {
        val a = parse(s)
        assertThat(a.amountMinor).isEqualTo(minor)
        assertThat(a.currency).isEqualTo(currency)
        assertThat(a.isAmbiguous).isFalse()
    }

    // --- Форматы из CLAUDE.md ---

    @Test fun spaceGroupsCommaDecimalRubleSign() = assertAmount("1 500,00 ₽", 150_000, "RUB")

    @Test fun plainDotDecimalIsoCode() = assertAmount("1500.00 RUB", 150_000, "RUB")

    @Test fun spaceGroupsShortRuble() = assertAmount("1 500р", 150_000, "RUB")

    @Test fun commaGroupsDotDecimalWithoutCurrency() = assertAmount("1,500.00", 150_000, null)

    @Test fun dollarPrefix() = assertAmount("$12.50", 1_250, "USD")

    @Test fun commaDecimalUsdSuffix() = assertAmount("12,50 USD", 1_250, "USD")

    @Test fun euroPrefixAndSuffix() {
        assertAmount("€9,99", 999, "EUR")
        assertAmount("9,99 €", 999, "EUR")
        assertAmount("9.99 EUR", 999, "EUR")
    }

    @Test fun dotGroupsCommaDecimal() = assertAmount("1.500,00 €", 150_000, "EUR")

    @Test fun multipleCommaGroupsAreNotAmbiguous() = assertAmount("1,500,000 RUB", 150_000_000, "RUB")

    @Test fun rurIsRub() = assertAmount("10 234 RUR", 1_023_400, "RUB")

    @Test fun rubleWords() {
        assertAmount("500 руб.", 50_000, "RUB")
        assertAmount("500 рублей", 50_000, "RUB")
        assertAmount("500 р.", 50_000, "RUB")
        assertAmount("500 Р", 50_000, "RUB")
    }

    @Test fun integerOnly() = assertAmount("1500", 150_000, null)

    @Test fun oneFractionDigit() = assertAmount("1,5 ₽", 150, "RUB")

    @Test fun twoFractionDigitsWithComma() = assertAmount("1,50 ₽", 150, "RUB")

    @Test fun zero() = assertAmount("0,00 ₽", 0, "RUB")

    // --- Пробелы ---

    @Test fun nbsp() = assertAmount("1 500,00 ₽", 150_000, "RUB")

    @Test fun thinSpace() = assertAmount("1 500 ₽", 150_000, "RUB")

    @Test fun narrowNbsp() = assertAmount("1 500 ₽", 150_000, "RUB")

    @Test fun surroundingWhitespaceIgnored() = assertAmount("  1 500 ₽ ", 150_000, "RUB")

    // --- Знаки ---

    @Test fun plusSign() {
        val a = parse("+1 500 ₽")
        assertThat(a.sign).isEqualTo(AmountSign.PLUS)
        assertThat(a.amountMinor).isEqualTo(150_000)
    }

    @Test fun hyphenMinus() {
        val a = parse("-500 ₽")
        assertThat(a.sign).isEqualTo(AmountSign.MINUS)
        assertThat(a.amountMinor).isEqualTo(50_000)
    }

    @Test fun unicodeMinus() {
        val a = parse("−500,00 RUB")
        assertThat(a.sign).isEqualTo(AmountSign.MINUS)
        assertThat(a.amountMinor).isEqualTo(50_000)
    }

    @Test fun signBeforeCurrencyPrefix() {
        val a = parse("-$12.50")
        assertThat(a.sign).isEqualTo(AmountSign.MINUS)
        assertThat(a.currency).isEqualTo("USD")
        assertThat(a.amountMinor).isEqualTo(1_250)
    }

    @Test fun noSign() = assertThat(parse("500 ₽").sign).isEqualTo(AmountSign.NONE)

    // --- Неоднозначные случаи ---

    @Test fun commaThreeDigitsIsAmbiguous() {
        val a = parse("1,500")
        assertThat(a.isAmbiguous).isTrue()
        assertThat(a.amountMinor).isEqualTo(150_000) // прочтение «разделитель тысяч», но помечено
    }

    @Test fun commaThreeDigitsWithCurrencyIsStillAmbiguous() = assertThat(parse("1,500 USD").isAmbiguous).isTrue()

    @Test fun dotThreeDigitsIsAmbiguous() = assertThat(parse("1.500").isAmbiguous).isTrue()

    @Test fun conflictingCurrenciesAreAmbiguous() = assertThat(parse("$12.50 EUR").isAmbiguous).isTrue()

    @Test fun fractionForZeroDecimalCurrencyIsAmbiguous() = assertThat(parse("100.50 JPY").isAmbiguous).isTrue()

    @Test fun zeroDecimalCurrency() = assertAmount("1 000 JPY", 1_000, "JPY")

    // --- Мусор ---

    @Test fun garbageReturnsNull() {
        assertThat(AmountParser.parse("")).isNull()
        assertThat(AmountParser.parse("   ")).isNull()
        assertThat(AmountParser.parse("abc")).isNull()
        assertThat(AmountParser.parse("₽")).isNull()
        assertThat(AmountParser.parse("1 500 ₽ и ещё")).isNull()
        assertThat(AmountParser.parse("12:34")).isNull()
    }

    @Test fun overflowReturnsNull() {
        assertThat(AmountParser.parse("99999999999999999999 ₽")).isNull()
        assertThat(AmountParser.findAll("Покупка 99999999999999999999 ₽")).isEmpty()
    }

    // --- Поиск в тексте ---

    @Test fun findAllInSberStyleText() {
        val found = AmountParser.findAll("Покупка 1500р Карта*1234 PYATEROCHKA Баланс: 10 234,56р")
        assertThat(found.map { it.amountMinor to it.currency })
            .containsExactly(150_000L to "RUB", 1_023_456L to "RUB").inOrder()
    }

    @Test fun findAllIgnoresNumbersWithoutCurrency() {
        val found = AmountParser.findAll("ECMC1234 05.11.2026 12:34 код 4821, *5678 500 ₽")
        assertThat(found.map { it.amountMinor }).containsExactly(50_000L)
    }

    @Test fun findAllDoesNotGlueTimeToAmount() {
        val found = AmountParser.findAll("12:34 500р")
        assertThat(found.map { it.amountMinor }).containsExactly(50_000L)
    }

    @Test fun findAllDoesNotGlueCardMaskToAmount() {
        val found = AmountParser.findAll("Карта *1234 1 500 ₽")
        assertThat(found.map { it.amountMinor }).containsExactly(150_000L)
    }

    @Test fun findAllReportsRangeInOriginalText() {
        val text = "Итого: 1 500 ₽."
        val a = AmountParser.findAll(text).single()
        assertThat(text.substring(a.range)).isEqualTo("1 500 ₽")
    }

    @Test fun findAllMixedCurrencies() {
        val found = AmountParser.findAll("Покупка 12,50 USD (1 150,00 RUB)")
        assertThat(found.map { it.amountMinor to it.currency })
            .containsExactly(1_250L to "USD", 115_000L to "RUB").inOrder()
    }

    @Test fun rubleLetterInsideWordIsNotCurrency() {
        assertThat(AmountParser.findAll("500 раз")).isEmpty()
    }

    @Test fun currencyOf() {
        assertThat(AmountParser.currencyOf("₽")).isEqualTo("RUB")
        assertThat(AmountParser.currencyOf("руб.")).isEqualTo("RUB")
        assertThat(AmountParser.currencyOf("RUR")).isEqualTo("RUB")
        assertThat(AmountParser.currencyOf("долл.")).isEqualTo("USD")
        assertThat(AmountParser.currencyOf("евро")).isEqualTo("EUR")
        assertThat(AmountParser.currencyOf("cny")).isEqualTo("CNY")
        assertThat(AmountParser.currencyOf("abc")).isNull()
    }
}
