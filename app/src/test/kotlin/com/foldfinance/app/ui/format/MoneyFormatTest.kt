package com.foldfinance.app.ui.format

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyFormatTest {
    private val n = MoneyFormat.NARROW_NBSP

    @Test fun `whole rubles hide zero kopecks`() {
        assertThat(MoneyFormat.format(124_000, "RUB")).isEqualTo("1${n}240${n}₽")
    }

    @Test fun `kopecks are shown with a comma`() {
        assertThat(MoneyFormat.format(124_050, "RUB")).isEqualTo("1${n}240,50${n}₽")
        assertThat(MoneyFormat.format(5, "RUB")).isEqualTo("0,05${n}₽")
        assertThat(MoneyFormat.format(999, "RUB")).isEqualTo("9,99${n}₽")
    }

    @Test fun `zero kopecks can be forced`() {
        assertThat(MoneyFormat.format(124_000, "RUB", hideZeroFraction = false)).isEqualTo("1${n}240,00${n}₽")
    }

    @Test fun `thousands are grouped with narrow no-break spaces`() {
        assertThat(MoneyFormat.format(100_000_000, "RUB")).isEqualTo("1${n}000${n}000${n}₽")
        assertThat(MoneyFormat.format(99_900, "RUB")).isEqualTo("999${n}₽")
        assertThat(MoneyFormat.groupThousands("1234567")).isEqualTo("1${n}234${n}567")
        assertThat(MoneyFormat.groupThousands("12")).isEqualTo("12")
    }

    @Test fun `zero`() {
        assertThat(MoneyFormat.format(0, "RUB")).isEqualTo("0${n}₽")
        assertThat(MoneyFormat.format(0, "RUB", SignDisplay.Always)).isEqualTo("0${n}₽")
    }

    @Test fun `sign policies`() {
        assertThat(MoneyFormat.format(-124_000, "RUB")).isEqualTo("−1${n}240${n}₽")
        assertThat(MoneyFormat.format(124_000, "RUB", SignDisplay.Always)).isEqualTo("+1${n}240${n}₽")
        assertThat(MoneyFormat.format(-124_000, "RUB", SignDisplay.Never)).isEqualTo("1${n}240${n}₽")
    }

    @Test fun `foreign currencies use symbols or ISO codes`() {
        assertThat(MoneyFormat.format(1_250, "USD")).isEqualTo("12,50${n}$")
        assertThat(MoneyFormat.format(1_250, "eur")).isEqualTo("12,50${n}€")
        assertThat(MoneyFormat.format(1_500, "JPY")).isEqualTo("1${n}500${n}JPY")
        assertThat(MoneyFormat.format(1_234, "CHF")).isEqualTo("12,34${n}CHF")
    }

    @Test fun `currencies with three minor digits`() {
        assertThat(MoneyFormat.format(12_345, "KWD")).isEqualTo("12,345${n}KWD")
    }

    @Test fun `unknown code falls back to two digits`() {
        assertThat(MoneyFormat.fractionDigits("XYZ")).isEqualTo(2)
        assertThat(MoneyFormat.format(1_001, "XYZ")).isEqualTo("10,01${n}XYZ")
    }

    @Test fun `extreme values do not overflow`() {
        assertThat(MoneyFormat.format(Long.MIN_VALUE, "RUB"))
            .isEqualTo("−92${n}233${n}720${n}368${n}547${n}758,08${n}₽")
        assertThat(MoneyFormat.format(Long.MAX_VALUE, "RUB"))
            .isEqualTo("92${n}233${n}720${n}368${n}547${n}758,07${n}₽")
    }

    @Test fun `parts split integer, fraction and currency`() {
        val parts = MoneyFormat.parts(124_050, "RUB", SignDisplay.Always)
        assertThat(parts.sign).isEqualTo("+")
        assertThat(parts.integer).isEqualTo("1${n}240")
        assertThat(parts.fraction).isEqualTo(",50")
        assertThat(parts.currency).isEqualTo("₽")
        assertThat(parts.toString()).isEqualTo("+1${n}240,50${n}₽")
    }

    @Test fun `month title is capitalised nominative`() {
        assertThat(monthTitle(java.time.LocalDate.of(2026, 11, 5))).isEqualTo("Ноябрь")
        assertThat(monthTitle(java.time.LocalDate.of(2026, 5, 1))).isEqualTo("Май")
    }
}
