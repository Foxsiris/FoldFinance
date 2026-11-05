package com.foldfinance.app.ui.format

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CardMaskFormatTest {

    @Test fun `every stored shape becomes two bullets and four digits`() {
        val expected = "•• 1234"
        assertThat(CardMaskFormat.format("*1234")).isEqualTo(expected)
        assertThat(CardMaskFormat.format("**1234")).isEqualTo(expected)
        assertThat(CardMaskFormat.format("••1234")).isEqualTo(expected)
        assertThat(CardMaskFormat.format("•*1234")).isEqualTo(expected)
        assertThat(CardMaskFormat.format("1234")).isEqualTo(expected)
        assertThat(CardMaskFormat.format(" MIR-1234 ")).isEqualTo(expected)
    }

    @Test fun `only the last four digits are shown`() {
        assertThat(CardMaskFormat.format("220012345678")).isEqualTo("•• 5678")
    }

    @Test fun `a mask without digits is shown as is`() {
        assertThat(CardMaskFormat.format(" Счёт ")).isEqualTo("Счёт")
    }
}
