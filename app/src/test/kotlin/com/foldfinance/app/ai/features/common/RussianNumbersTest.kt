package com.foldfinance.app.ai.features.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class RussianNumbersTest {

    private fun minor(text: String) = RussianNumbers.parseAmountMinor(text)

    @Test fun `words`() {
        assertThat(minor("двести пятьдесят")).isEqualTo(25_000)
        assertThat(minor("тысяча двести")).isEqualTo(120_000)
        assertThat(minor("две тысячи триста сорок пять")).isEqualTo(234_500)
        assertThat(minor("сто двенадцать")).isEqualTo(11_200)
        assertThat(minor("двадцать одна тысяча")).isEqualTo(2_100_000)
        assertThat(minor("миллион двести тысяч")).isEqualTo(120_000_000)
        assertThat(minor("тысячу")).isEqualTo(100_000)
    }

    @Test fun `half`() {
        assertThat(minor("полторы тысячи")).isEqualTo(150_000)
        assertThat(minor("полтора миллиона")).isEqualTo(150_000_000)
    }

    @Test fun `digits as recognisers write them`() {
        assertThat(minor("250")).isEqualTo(25_000)
        assertThat(minor("1 500")).isEqualTo(150_000)
        assertThat(minor("250,50")).isEqualTo(25_050)
        assertThat(minor("2,5 тысячи")).isEqualTo(250_000)
        assertThat(minor("2 тысячи 300")).isEqualTo(230_000)
        assertThat(minor("12 345 678")).isEqualTo(1_234_567_800)
    }

    @Test fun `roubles and kopecks`() {
        assertThat(minor("сто рублей пятьдесят копеек")).isEqualTo(10_050)
        assertThat(minor("250 р")).isEqualTo(25_000)
        assertThat(minor("пятьдесят копеек")).isEqualTo(50)
        assertThat(minor("триста рублей")).isEqualTo(30_000)
    }

    @Test fun `not an amount`() {
        assertThat(minor("кофе")).isNull()
        assertThat(minor("двести кофе")).isNull()
        assertThat(minor("ноль")).isNull()
        assertThat(minor("12,345")).isNull() // three kopeck digits
        assertThat(minor("")).isNull()
    }

    @Test fun `finds the amount inside a phrase`() {
        val text = "кофе двести пятьдесят наличными"
        val spans = RussianNumbers.findAmounts(text)
        assertThat(spans).hasSize(1)
        assertThat(spans.single().minor).isEqualTo(25_000)
        assertThat(text.substring(spans.single().start, spans.single().end)).isEqualTo("двести пятьдесят")
    }

    @Test fun `a word that cannot continue a number starts a new one`() {
        assertThat(RussianNumbers.findNumbers("пять три").map { it.value.toInt() }).containsExactly(5, 3).inOrder()
        assertThat(RussianNumbers.findNumbers("двести двести").map { it.value.toInt() }).containsExactly(200, 200).inOrder()
        assertThat(RussianNumbers.findSpelledNumbers("в два раза и 15 штук").map { it.value }).containsExactly(BigDecimal(2))
    }
}
