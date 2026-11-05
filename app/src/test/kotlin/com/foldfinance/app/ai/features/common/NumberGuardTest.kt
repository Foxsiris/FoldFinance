package com.foldfinance.app.ai.features.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The filter of «hallucinated numbers» in model text. */
class NumberGuardTest {

    private val facts = listOf(
        Fact("total", "12 340 ₽"),
        Fact("total_pct", "+11%"),
        Fact("up1_name", "Рестораны"),
        Fact("up1_delta", "+3 200 ₽"),
    )

    private fun render(text: String, context: List<String> = emptyList()) = NumberGuard.render(text, facts, context)

    @Test fun `placeholders are filled with Kotlin values`() {
        val result = render("Вы потратили {total} ({total_pct}), больше всего выросли {up1_name}: {up1_delta}.")
        assertThat(result).isEqualTo(NumberGuard.Result.Accepted("Вы потратили 12 340 ₽ (+11%), больше всего выросли Рестораны: +3 200 ₽."))
    }

    @Test fun `a number that is not in the facts is rejected`() {
        assertThat(render("Вы потратили 15 000 ₽.")).isInstanceOf(NumberGuard.Result.Rejected::class.java)
        assertThat(render("Расходы выросли на 15%.")).isInstanceOf(NumberGuard.Result.Rejected::class.java)
    }

    @Test fun `a number repeated from the facts is accepted`() {
        assertThat(render("Итого 12 340 ₽, это на 11% больше.")).isInstanceOf(NumberGuard.Result.Accepted::class.java)
    }

    @Test fun `spelled-out numbers the model worked out are rejected`() {
        assertThat(render("Рестораны выросли в два раза.")).isInstanceOf(NumberGuard.Result.Rejected::class.java)
        assertThat(render("Три категории стали дороже.")).isInstanceOf(NumberGuard.Result.Rejected::class.java)
        // «одна» is a pronoun as often as a number: allowed.
        assertThat(render("Одна категория выросла: {up1_name}.")).isInstanceOf(NumberGuard.Result.Accepted::class.java)
    }

    @Test fun `unknown placeholder or markup is rejected`() {
        assertThat(render("Вы потратили {sum}.")).isEqualTo(NumberGuard.Result.Rejected("unknown_placeholder:sum"))
        assertThat(render("```Вы потратили {total}```")).isInstanceOf(NumberGuard.Result.Rejected::class.java)
        assertThat(render("   ")).isEqualTo(NumberGuard.Result.Rejected("empty"))
    }

    @Test fun `numbers from names shown to the model may be repeated`() {
        assertThat(render("Чаще всего — «Магазин 24».", context = listOf("Магазин 24"))).isInstanceOf(NumberGuard.Result.Accepted::class.java)
        assertThat(render("Чаще всего — «Магазин 24».")).isInstanceOf(NumberGuard.Result.Rejected::class.java)
    }
}
