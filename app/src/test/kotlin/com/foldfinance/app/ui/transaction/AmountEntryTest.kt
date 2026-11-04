package com.foldfinance.app.ui.transaction

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AmountEntryTest {
    private fun type(keys: String, digits: Int = 2): String = keys.fold("") { text, ch ->
        val key = when (ch) {
            ',' -> KeypadKey.Decimal
            '<' -> KeypadKey.Backspace
            else -> KeypadKey.Digit(ch - '0')
        }
        AmountEntry.press(text, key, digits)
    }

    @Test fun `digits and comma build the text, kopecks are exact`() {
        assertThat(type("1240,5")).isEqualTo("1240,5")
        assertThat(AmountEntry.toMinor("1240,5")).isEqualTo(124_050L)
        assertThat(AmountEntry.toMinor("1240,05")).isEqualTo(124_005L)
        assertThat(AmountEntry.toMinor("0,01")).isEqualTo(1L)
        assertThat(AmountEntry.toMinor("999999999,99")).isEqualTo(99_999_999_999L)
    }

    @Test fun `no leading zeros, comma on empty gives zero, one comma only`() {
        assertThat(type("0005")).isEqualTo("5")
        assertThat(type(",5")).isEqualTo("0,5")
        assertThat(type("1,,2")).isEqualTo("1,2")
    }

    @Test fun `at most two fraction digits and nine integer digits`() {
        assertThat(type("1,239")).isEqualTo("1,23")
        assertThat(type("1234567890")).isEqualTo("123456789")
    }

    @Test fun `backspace removes the last character`() {
        assertThat(type("12,5<<")).isEqualTo("12")
        assertThat(type("<")).isEmpty()
    }

    @Test fun `currencies without kopecks ignore the comma`() {
        assertThat(type("15,0", digits = 0)).isEqualTo("150")
        assertThat(AmountEntry.toMinor("150", 0)).isEqualTo(150L)
    }

    @Test fun `existing amounts round-trip`() {
        listOf(0L, 1L, 10L, 100L, 124_050L, 124_000L, 99_999_999_999L).forEach { minor ->
            assertThat(AmountEntry.toMinor(AmountEntry.fromMinor(minor))).isEqualTo(minor)
        }
        assertThat(AmountEntry.fromMinor(124_050L)).isEqualTo("1240,5")
        assertThat(AmountEntry.fromMinor(124_000L)).isEqualTo("1240")
    }

    @Test fun `display groups thousands and keeps the typed fraction`() {
        assertThat(AmountEntry.displayParts("1240,")).isEqualTo("1\u202F240" to ",")
        assertThat(AmountEntry.displayParts("")).isEqualTo("0" to null)
    }

    @Test fun `malformed text is zero, not a crash`() {
        assertThat(AmountEntry.toMinor("abc")).isEqualTo(0L)
        assertThat(AmountEntry.toMinor("1,234")).isEqualTo(0L)
    }
}
