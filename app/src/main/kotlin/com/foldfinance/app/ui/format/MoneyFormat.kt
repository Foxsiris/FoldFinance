package com.foldfinance.app.ui.format

import java.util.Currency

/** When to show a sign in front of an amount. */
enum class SignDisplay {
    /** Only `−` for negative values (default for neutral amounts). */
    NegativeOnly,

    /** `+` for positive values and `−` for negative ones (income, refunds). Zero gets no sign. */
    Always,

    /** Never show a sign; the colour carries the direction. */
    Never,
}

/**
 * An amount split into the parts the UI renders with different sizes and colours.
 *
 * Joining `sign + integer + fraction + separator + currency` gives [MoneyFormat.format].
 *
 * @property sign `""`, `"+"` or `"−"` (U+2212 minus sign, not a hyphen).
 * @property integer integer part grouped by thousands with a narrow no-break space: `1 240`.
 * @property fraction decimal part with its comma (`",50"`), or `null` when hidden.
 * @property currency currency symbol (`₽`, `$`, `€`) or the ISO code when there is no symbol.
 */
data class MoneyParts(
    val sign: String,
    val integer: String,
    val fraction: String?,
    val currency: String,
) {
    /** Space between the number and the currency sign. */
    val separator: String get() = MoneyFormat.NARROW_NBSP.toString()

    override fun toString(): String = sign + integer + (fraction ?: "") + separator + currency
}

/**
 * Money formatting for the ru-RU locale. Pure functions over [Long] minor units — never
 * `Double`. Format: `1 240,50 ₽` with U+202F (narrow no-break space) as thousands separator and
 * before the currency sign; zero kopecks (`,00`) are hidden by default.
 */
object MoneyFormat {
    const val NARROW_NBSP: Char = ' '
    const val MINUS: Char = '−'
    const val DECIMAL_SEPARATOR: Char = ','

    private val symbols = mapOf(
        "RUB" to "₽",
        "USD" to "$",
        "EUR" to "€",
        "GBP" to "£",
        "KZT" to "₸",
        "TRY" to "₺",
        "UAH" to "₴",
        "CNY" to "¥",
    )

    /** Number of minor-unit digits of an ISO 4217 code; unknown codes default to 2. */
    fun fractionDigits(currency: String): Int = try {
        Currency.getInstance(currency.uppercase()).defaultFractionDigits.takeIf { it >= 0 } ?: 2
    } catch (_: IllegalArgumentException) {
        2
    }

    /** Symbol for well-known currencies, otherwise the upper-cased ISO code. */
    fun symbol(currency: String): String = symbols[currency.uppercase()] ?: currency.uppercase()

    /** Groups an unsigned decimal string by thousands: `1234567` → `1 234 567`. */
    fun groupThousands(digits: String): String {
        if (digits.length <= 3) return digits
        val head = digits.length % 3
        val sb = StringBuilder(digits.length + digits.length / 3)
        if (head > 0) sb.append(digits, 0, head)
        var i = head
        while (i < digits.length) {
            if (sb.isNotEmpty()) sb.append(NARROW_NBSP)
            sb.append(digits, i, i + 3)
            i += 3
        }
        return sb.toString()
    }

    /**
     * Splits [amountMinor] into renderable parts.
     *
     * @param amountMinor amount in minor units (kopecks); may be negative.
     * @param currency ISO 4217 code.
     * @param signDisplay sign policy, see [SignDisplay].
     * @param hideZeroFraction drop `,00`; non-zero kopecks are always shown.
     */
    fun parts(
        amountMinor: Long,
        currency: String,
        signDisplay: SignDisplay = SignDisplay.NegativeOnly,
        hideZeroFraction: Boolean = true,
    ): MoneyParts {
        val negative = amountMinor < 0
        // Magnitude as unsigned so Long.MIN_VALUE does not overflow.
        val magnitude: ULong = if (negative) (-(amountMinor + 1)).toULong() + 1uL else amountMinor.toULong()
        val digits = fractionDigits(currency)
        val divisor = pow10(digits)
        val integerPart = magnitude / divisor
        val fractionPart = magnitude % divisor

        val sign = when (signDisplay) {
            SignDisplay.Never -> ""
            SignDisplay.NegativeOnly -> if (negative) MINUS.toString() else ""
            SignDisplay.Always -> when {
                negative -> MINUS.toString()
                magnitude == 0uL -> ""
                else -> "+"
            }
        }
        val fraction = when {
            digits == 0 -> null
            fractionPart == 0uL && hideZeroFraction -> null
            else -> DECIMAL_SEPARATOR + fractionPart.toString().padStart(digits, '0')
        }
        return MoneyParts(
            sign = sign,
            integer = groupThousands(integerPart.toString()),
            fraction = fraction,
            currency = symbol(currency),
        )
    }

    /** Full string, e.g. `1 240,50 ₽`. See [parts] for parameters. */
    fun format(
        amountMinor: Long,
        currency: String,
        signDisplay: SignDisplay = SignDisplay.NegativeOnly,
        hideZeroFraction: Boolean = true,
    ): String = parts(amountMinor, currency, signDisplay, hideZeroFraction).toString()

    private fun pow10(n: Int): ULong {
        var r = 1uL
        repeat(n) { r *= 10uL }
        return r
    }
}
