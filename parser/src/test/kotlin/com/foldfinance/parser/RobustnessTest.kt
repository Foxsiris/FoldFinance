package com.foldfinance.parser

import com.foldfinance.parser.bank.GenericParser
import com.foldfinance.parser.bank.SberParser
import com.foldfinance.parser.bank.SovcombankParser
import com.foldfinance.parser.bank.TBankParser
import com.foldfinance.parser.bank.WbBankParser
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.time.ZoneId

/** Парсеры не бросают исключений на мусорном вводе и не «угадывают» молча. */
class RobustnessTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val parsers: List<BankParser> =
        listOf(SberParser(zone), TBankParser(zone), SovcombankParser(zone), WbBankParser(zone), GenericParser(zone))
    private val meta = MessageMeta(MessageChannel.SMS, "900", 1_762_335_300_000L)

    private val garbage = listOf(
        "",
        " ",
        "\n\n\t",
        "\u0000\u0001\u0002",
        "🎉🎉🎉",
        "Покупка 💸 500 ₽ 🍕",
        "💳*1234 ➖1 500 ₽ 🛒",
        "₽₽₽ $$$ €€€",
        "1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 ₽",
        "9".repeat(500) + " ₽",
        "Покупка " + "9".repeat(40) + " ₽",
        "Покупка 0,00 ₽ карта *1234",
        "Покупка -0 ₽",
        "*1234*5678*9012",
        "Баланс: 1 000 ₽",
        "\uD800",
        "Перевод на карту *1234 от *5678 с карты *9012 +-−1 ₽",
        "12:99 32.13.2026 25:61 Покупка 10 ₽",
    )

    @Test(timeout = 10_000)
    fun garbageNeverThrows() {
        for (parser in parsers) {
            for (text in garbage) {
                val result = runCatching { parser.parse(text, meta) }
                assertWithMessage("${parser.id}: '$text'").that(result.exceptionOrNull()).isNull()
                assertWithMessage("${parser.id}: '$text'").that(result.getOrNull()).isNotNull()
            }
        }
    }

    @Test(timeout = 10_000)
    fun veryLongTextIsUncertainNotThrown() {
        val long = "Покупка 1 500 ₽ PYATEROCHKA ".repeat(20_000)
        for (parser in parsers) {
            val result = parser.parse(long, meta)
            assertThat(result).isInstanceOf(ParseResult.Uncertain::class.java)
            assertThat((result as ParseResult.Uncertain).reason).isEqualTo(UncertainReasons.TEXT_TOO_LONG)
        }
    }

    @Test(timeout = 10_000)
    fun longDigitRunsAreFast() {
        val text = "1 ".repeat(2_000) + "₽"
        for (parser in parsers) parser.parse(text, meta)
        AmountParser.findAll("1,".repeat(2_000) + "₽")
        AmountParser.findAll("1.".repeat(2_000) + "₽")
    }

    @Test
    fun emptyTextIsNotFinancial() {
        for (parser in parsers) assertThat(parser.parse("", meta)).isEqualTo(ParseResult.NotFinancial)
    }

    @Test
    fun emojiOnlyIsNotFinancial() {
        for (parser in parsers) assertThat(parser.parse("🎉🎉🎉", meta)).isEqualTo(ParseResult.NotFinancial)
    }

    @Test
    fun zeroAmountIsUncertain() {
        val result = SberParser(zone).parse("Покупка 0,00 ₽ SHOP карта *1234", meta)
        assertThat(result).isInstanceOf(ParseResult.Uncertain::class.java)
        assertThat((result as ParseResult.Uncertain).reason).isEqualTo(UncertainReasons.ZERO_AMOUNT)
    }

    @Test
    fun balanceOnlyIsUncertainWithBalance() {
        val result = TBankParser(zone).parse("Остаток по карте *1234: 5 000 RUB", meta)
        assertThat(result).isInstanceOf(ParseResult.Uncertain::class.java)
        result as ParseResult.Uncertain
        assertThat(result.reason).isEqualTo(UncertainReasons.BALANCE_ONLY)
        assertThat(result.balance).isEqualTo(ParsedBalance(500_000, "RUB", "*1234"))
    }

    @Test
    fun timeAfterReceiptMeansPreviousDay() {
        // Получено 05.11 00:03 МСК, в тексте 23:58 → 04.11 23:58.
        val received = java.time.ZonedDateTime.of(2026, 11, 5, 0, 3, 0, 0, zone).toInstant().toEpochMilli()
        val result = SberParser(zone).parse(
            "VISA1234 23:58 Покупка 100р SHOP Баланс: 900р",
            MessageMeta(MessageChannel.SMS, "900", received),
        ) as ParseResult.Parsed
        val expected = java.time.ZonedDateTime.of(2026, 11, 4, 23, 58, 0, 0, zone).toInstant().toEpochMilli()
        assertThat(result.operations.single().occurredAtMillis).isEqualTo(expected)
    }

    @Test
    fun parserVersionIsOne() = assertThat(PARSER_VERSION).isEqualTo(1)
}
