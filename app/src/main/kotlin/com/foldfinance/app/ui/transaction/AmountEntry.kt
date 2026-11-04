package com.foldfinance.app.ui.transaction

import com.foldfinance.app.ui.format.MoneyFormat

/** A key of the [NumericKeypad]. */
sealed interface KeypadKey {
    data class Digit(val value: Int) : KeypadKey {
        init {
            require(value in 0..9) { "digit must be 0..9" }
        }
    }

    /** The decimal comma: switches input to kopecks. */
    data object Decimal : KeypadKey

    data object Backspace : KeypadKey
}

/**
 * Amount typed on the keypad, kept as the text the user sees (`"1240,5"`), never as a `Double`.
 *
 * The text is the source of truth for the editor (it survives process death in a
 * `SavedStateHandle` as a plain string) and converts to minor units with [toMinor] exactly.
 *
 * Rules: at most [MAX_INTEGER_DIGITS] integer digits, at most `fractionDigits` digits after the
 * comma, no leading zeros, the comma on an empty input gives `"0,"`.
 */
object AmountEntry {
    const val MAX_INTEGER_DIGITS = 9
    private const val COMMA = MoneyFormat.DECIMAL_SEPARATOR

    /** Applies [key] to [text]. Invalid presses (too many digits) leave the text unchanged. */
    fun press(text: String, key: KeypadKey, fractionDigits: Int = 2): String {
        val commaAt = text.indexOf(COMMA)
        return when (key) {
            KeypadKey.Backspace -> text.dropLast(1)
            KeypadKey.Decimal -> when {
                fractionDigits == 0 || commaAt >= 0 -> text
                text.isEmpty() -> "0$COMMA"
                else -> text + COMMA
            }
            is KeypadKey.Digit -> {
                val digit = ('0' + key.value)
                when {
                    commaAt >= 0 -> if (text.length - commaAt - 1 >= fractionDigits) text else text + digit
                    text == "0" -> if (key.value == 0) text else digit.toString()
                    text.length >= MAX_INTEGER_DIGITS -> text
                    else -> text + digit
                }
            }
        }
    }

    /** Exact minor units of [text]: `"1240,5"` → `124050`. Empty or malformed text gives `0`. */
    fun toMinor(text: String, fractionDigits: Int = 2): Long {
        if (text.isEmpty()) return 0
        val integer = text.substringBefore(COMMA)
        val fraction = if (text.contains(COMMA)) text.substringAfter(COMMA) else ""
        if (!integer.all(Char::isDigit) || !fraction.all(Char::isDigit) || fraction.length > fractionDigits) return 0
        val major = integer.ifEmpty { "0" }.toLong()
        val minor = fraction.padEnd(fractionDigits, '0').ifEmpty { "0" }.toLong()
        var scale = 1L
        repeat(fractionDigits) { scale *= 10 }
        return Math.addExact(Math.multiplyExact(major, scale), minor)
    }

    /** Text for an existing amount: `124050` → `"1240,5"`, `124000` → `"1240"`. */
    fun fromMinor(amountMinor: Long, fractionDigits: Int = 2): String {
        require(amountMinor >= 0) { "amount must not be negative" }
        if (amountMinor == 0L) return ""
        var scale = 1L
        repeat(fractionDigits) { scale *= 10 }
        val major = amountMinor / scale
        val minor = amountMinor % scale
        if (minor == 0L) return major.toString()
        val fraction = minor.toString().padStart(fractionDigits, '0').trimEnd('0')
        return "$major$COMMA$fraction"
    }

    /**
     * Parts for display: integer grouped by thousands (`"1 240"`) and the typed fraction with
     * its comma (`",5"`, `","` right after the comma key) or `null`.
     */
    fun displayParts(text: String): Pair<String, String?> {
        val integer = text.substringBefore(COMMA).ifEmpty { "0" }
        val fraction = if (text.contains(COMMA)) COMMA + text.substringAfter(COMMA) else null
        return MoneyFormat.groupThousands(integer) to fraction
    }
}
