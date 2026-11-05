package com.foldfinance.parser

import com.foldfinance.parser.internal.TextNormalizer

/** Явный знак перед суммой: `+1 500 ₽`, `-500 ₽`, `−500 ₽` (U+2212). */
enum class AmountSign { NONE, PLUS, MINUS }

/**
 * Сумма, найденная в тексте.
 *
 * @property amountMinor абсолютное значение в минорных единицах (копейки, центы); никогда не отрицательно.
 * @property currency ISO 4217 (`RUB`, `USD`, …) или `null`, если валюта в строке не указана.
 * @property sign явный знак перед суммой, если был.
 * @property isAmbiguous `true`, если запись допускает два прочтения (`1,500` — это 1 500 или 1,5?).
 *   В этом случае [amountMinor] — прочтение «разделитель тысяч», и полагаться на него без проверки нельзя.
 * @property range позиция суммы (вместе со знаком и валютой) во входной строке.
 */
data class ParsedAmount(
    val amountMinor: Long,
    val currency: String?,
    val sign: AmountSign,
    val isAmbiguous: Boolean,
    val range: IntRange,
)

/**
 * Разбор денежных сумм без `Double`: результат — `Long` в минорных единицах + ISO-код.
 *
 * Поддерживаемые записи:
 * - разделители: `1 500,00 ₽`, `1500.00 RUB`, `1 500р`, `1,500.00`, `1.500,00 €`;
 * - валюта знаком или кодом, до или после числа: `$12.50`, `12,50 USD`, `€9,99`, `500 руб.`, `RUR` (→ `RUB`);
 * - пробелы-разделители: обычный, NBSP (U+00A0), тонкий (U+2009), узкий неразрывный (U+202F), цифровой (U+2007);
 * - знаки `+`, `-`, `−` (U+2212).
 *
 * Неоднозначные записи (`1,500`, `1.500` — одна группа из трёх цифр без дробной части) помечаются
 * [ParsedAmount.isAmbiguous]. На мусорном вводе возвращает `null` / пустой список. Внутренняя ошибка
 * (баг) не маскируется под «сумм нет»: исключение уходит вызывающему, и `MessageAnalyzer` превращает
 * его в `Uncertain(PARSER_ERROR)` — сообщение сохраняется и будет перепарсено.
 */
object AmountParser {

    /** Число: группы через пробел, через запятую, через точку, либо сплошные цифры. */
    private const val NUMBER =
        """(\d{1,3}(?: \d{3})+(?:[.,]\d{1,2})?|\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?|\d+(?:[.,]\d{1,2})?)(?!\d)"""

    private const val SIGN = "([+\\-−])?"
    private const val PREFIX_SYMBOL = "([\$€£₽₸₺])"
    private const val SUFFIX_TOKEN =
        "(₽|руб(?:лей|ля|ль)?\\.?|р\\.?|rub|rur|usd|eur|gbp|cny|kzt|byn|uah|try|amd|gel|aed|thb|jpy|krw|chf|uzs|kgs|azn|tjs" +
            "|долл(?:ар(?:ов|а|ы)?|\\.)?|евро|\\$|€|£|₸|₺)(?!\\p{L})"

    /** Для поиска в тексте: валюта обязательна (иначе любое число — «сумма»). */
    private val inTextRegex = Regex(
        "(?iu)(?<![\\p{L}\\d*.,/])(?<!\\d:)$SIGN(?:$PREFIX_SYMBOL ?$NUMBER(?: ?$SUFFIX_TOKEN)?|$NUMBER ?$SUFFIX_TOKEN)",
    )

    /** Для разбора отдельной строки: валюта необязательна. */
    private val wholeRegex = Regex("(?iu)$SIGN(?:$PREFIX_SYMBOL ?)?$NUMBER(?: ?$SUFFIX_TOKEN)?")

    private const val MAX_INTEGER_DIGITS = 15
    private val zeroDecimalCurrencies = setOf("JPY", "KRW")

    /**
     * Разобрать строку, которая целиком является суммой (`"1 500,00 ₽"`, `"1,500.00"`).
     * Возвращает `null`, если строка не сумма или число слишком большое.
     */
    fun parse(input: String): ParsedAmount? {
        val normalized = TextNormalizer.normalize(input)
        val start = normalized.indexOfFirst { !it.isWhitespace() }
        if (start < 0) return null
        val end = normalized.indexOfLast { !it.isWhitespace() }
        val core = normalized.substring(start, end + 1)
        val match = wholeRegex.matchEntire(core) ?: return null
        return build(
            signToken = match.groupValues[1],
            prefixToken = match.groupValues[2],
            number = match.groupValues[3],
            suffixToken = match.groupValues[4],
            range = start..end,
        )
    }

    /**
     * Найти в тексте все суммы с указанной валютой, по порядку появления.
     * Числа без валюты (время, маски карт, коды) не возвращаются.
     */
    fun findAll(text: String): List<ParsedAmount> {
        val normalized = TextNormalizer.normalize(text)
        return inTextRegex.findAll(normalized).mapNotNull { m ->
            val prefixed = m.groupValues[3].isNotEmpty()
            build(
                signToken = m.groupValues[1],
                prefixToken = m.groupValues[2],
                number = if (prefixed) m.groupValues[3] else m.groupValues[5],
                suffixToken = if (prefixed) m.groupValues[4] else m.groupValues[6],
                range = m.range,
            )
        }.toList()
    }

    /** ISO-код по знаку или слову валюты (`₽`, `р.`, `руб`, `RUR`, `$`, `евро`, …); `null`, если не валюта. */
    fun currencyOf(token: String): String? {
        val t = token.trim().lowercase().trimEnd('.')
        return when {
            t.isEmpty() -> null
            t == "₽" || t == "р" || t.startsWith("руб") || t == "rub" || t == "rur" -> "RUB"
            t == "$" || t == "usd" || t.startsWith("долл") -> "USD"
            t == "€" || t == "eur" || t == "евро" -> "EUR"
            t == "£" || t == "gbp" -> "GBP"
            t == "₸" || t == "kzt" -> "KZT"
            t == "₺" || t == "try" -> "TRY"
            t.uppercase() in isoCodes -> t.uppercase()
            else -> null
        }
    }

    private val isoCodes = setOf(
        "CNY", "BYN", "UAH", "AMD", "GEL", "AED", "THB", "JPY", "KRW", "CHF", "UZS", "KGS", "AZN", "TJS",
    )

    private fun build(
        signToken: String,
        prefixToken: String,
        number: String,
        suffixToken: String,
        range: IntRange,
    ): ParsedAmount? {
        val prefixCurrency = prefixToken.takeIf { it.isNotEmpty() }?.let(::currencyOf)
        val suffixCurrency = suffixToken.takeIf { it.isNotEmpty() }?.let(::currencyOf)
        val currencyConflict = prefixCurrency != null && suffixCurrency != null && prefixCurrency != suffixCurrency
        val currency = prefixCurrency ?: suffixCurrency
        val minorDigits = if (currency in zeroDecimalCurrencies) 0 else 2
        val value = interpret(number, minorDigits) ?: return null
        val sign = when (signToken) {
            "+" -> AmountSign.PLUS
            "-", "−" -> AmountSign.MINUS
            else -> AmountSign.NONE
        }
        return ParsedAmount(
            amountMinor = value.minor,
            currency = currency,
            sign = sign,
            isAmbiguous = value.ambiguous || currencyConflict,
            range = range,
        )
    }

    private class Interpreted(val minor: Long, val ambiguous: Boolean)

    private val commaGrouped = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?""")
    private val dotGrouped = Regex("""\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?""")

    private fun interpret(number: String, minorDigits: Int): Interpreted? {
        val integerPart: String
        val fraction: String
        var ambiguous = false
        when {
            ' ' in number -> {
                val decimalAt = number.indexOfAny(charArrayOf('.', ','))
                integerPart = (if (decimalAt >= 0) number.substring(0, decimalAt) else number).replace(" ", "")
                fraction = if (decimalAt >= 0) number.substring(decimalAt + 1) else ""
            }
            commaGrouped.matches(number) -> {
                val dotAt = number.indexOf('.')
                integerPart = (if (dotAt >= 0) number.substring(0, dotAt) else number).replace(",", "")
                fraction = if (dotAt >= 0) number.substring(dotAt + 1) else ""
                ambiguous = dotAt < 0 && number.count { it == ',' } == 1
            }
            dotGrouped.matches(number) -> {
                val commaAt = number.indexOf(',')
                integerPart = (if (commaAt >= 0) number.substring(0, commaAt) else number).replace(".", "")
                fraction = if (commaAt >= 0) number.substring(commaAt + 1) else ""
                ambiguous = commaAt < 0 && number.count { it == '.' } == 1
            }
            else -> {
                val decimalAt = number.indexOfAny(charArrayOf('.', ','))
                integerPart = if (decimalAt >= 0) number.substring(0, decimalAt) else number
                fraction = if (decimalAt >= 0) number.substring(decimalAt + 1) else ""
            }
        }
        val digits = integerPart.trimStart('0').ifEmpty { "0" }
        if (digits.length > MAX_INTEGER_DIGITS || !digits.all { it.isDigit() } || !fraction.all { it.isDigit() }) {
            return null
        }
        var minor = digits.toLong()
        repeat(minorDigits) { minor *= 10 }
        if (minorDigits == 0) {
            if (fraction.any { it != '0' }) ambiguous = true
        } else {
            val scaled = fraction.padEnd(minorDigits, '0').take(minorDigits)
            minor += scaled.toLong()
        }
        return Interpreted(minor, ambiguous)
    }
}
