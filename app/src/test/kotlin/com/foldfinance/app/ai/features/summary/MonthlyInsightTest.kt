package com.foldfinance.app.ai.features.summary

import com.foldfinance.app.ai.AiResponse
import com.foldfinance.app.ai.features.AiData
import com.foldfinance.app.ai.features.AiData.tx
import com.foldfinance.app.ai.features.FakeLocalAi
import com.foldfinance.app.ai.features.common.FactFormat
import com.foldfinance.core.domain.analytics.AmountChange
import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.model.Subscription
import com.foldfinance.core.model.SubscriptionPeriod
import com.foldfinance.core.model.SubscriptionStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthlyInsightTest {

    private val zone = AiData.zone

    private val txs = listOf(
        tx("2026-09-10T12:00", 200_000, "Пятёрочка", "groceries"),
        tx("2026-09-12T12:00", 50_000, "Кофемания", "restaurants"),
        tx("2026-10-10T12:00", 150_000, "Пятёрочка", "groceries"),
        tx("2026-10-12T12:00", 180_000, "Кофемания", "restaurants"),
        tx("2026-10-15T12:00", 60_000, "Метро", "transport"),
        tx("2026-10-25T09:00", 29_900, "Кинопоиск", "subscriptions"),
        tx("2026-11-02T09:00", 29_900, "Кинопоиск", "subscriptions"),
    )
    private val kinopoisk = Subscription(
        id = 1, merchant = "Кинопоиск", amountMinor = 29_900, currency = "RUB", period = SubscriptionPeriod.MONTHLY,
        lastChargeAt = AiData.now, nextChargeAt = AiData.now, status = SubscriptionStatus.CANDIDATE,
    )

    private fun facts(today: LocalDate = AiData.today) =
        MonthlyFactsBuilder.build(txs, AiData.categories, listOf(kinopoisk), today, zone, "RUB")

    @Test fun `early in the month the summary is about the last complete month`() {
        val (cur, prev) = MonthlyFactsBuilder.periods(LocalDate.of(2026, 11, 5), zone)
        assertThat(cur).isEqualTo(Period.month(YearMonth.of(2026, 10), zone))
        assertThat(prev).isEqualTo(Period.month(YearMonth.of(2026, 9), zone))
    }

    @Test fun `later it compares the same days, clamped to a short month`() {
        val (cur, prev) = MonthlyFactsBuilder.periods(LocalDate.of(2026, 11, 20), zone)
        assertThat(cur).isEqualTo(Period.custom(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20), zone))
        assertThat(prev).isEqualTo(Period.custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20), zone))
        val (_, feb) = MonthlyFactsBuilder.periods(LocalDate.of(2026, 3, 30), zone)
        assertThat(feb.endDateInclusive).isEqualTo(LocalDate.of(2026, 2, 28))
    }

    @Test fun `facts are computed by Kotlin`() {
        val f = facts()
        assertThat(f.total).isEqualTo(AmountChange(150_000 + 180_000 + 60_000 + 29_900, 250_000))
        assertThat(f.increases.map { it.category?.key to it.change.deltaMinor })
            .containsExactly("restaurants" to 130_000L, "transport" to 60_000L, "subscriptions" to 29_900L).inOrder()
        assertThat(f.decreases.map { it.category?.key to it.change.deltaMinor }).containsExactly("groceries" to -50_000L)
        assertThat(f.newSubscriptions).containsExactly(NewSubscription("Кинопоиск", 29_900, "RUB"))
        val values = f.toFacts().associate { it.id to it.value }
        assertThat(values["period"]).isEqualTo("октябрь 2026")
        assertThat(values["total_pct"]).isEqualTo("+68%")
        assertThat(values["up1_name"]).isEqualTo("Рестораны")
    }

    @Test fun `model text with placeholders is accepted and filled`() = runTest {
        val ai = FakeLocalAi.answering("За {period} вы потратили {total} — это {total_delta} к {prev_period}. Больше всего выросли «{up1_name}».")
        val text = MonthlyInsightGenerator(ai, MonthlyInsightCache()).generate(facts())
        assertThat(text).isInstanceOf(InsightText.Model::class.java)
        assertThat((text as InsightText.Model).text).startsWith("За октябрь 2026 вы потратили ${FactFormat.money(419_900, "RUB")}")
        assertThat(text.text).contains("«Рестораны»")
    }

    @Test fun `a hallucinated number gives the template`() = runTest {
        val ai = FakeLocalAi.answering("За {period} вы потратили около 5 000 ₽, в полтора раза больше.")
        assertThat(MonthlyInsightGenerator(ai, MonthlyInsightCache()).generate(facts())).isEqualTo(InsightText.Template)
    }

    @Test fun `the same facts never ask the model twice, failures are retried`() = runTest {
        val cache = MonthlyInsightCache()
        val ok = FakeLocalAi.answering("Расходы за {period}: {total}.")
        val generator = MonthlyInsightGenerator(ok, cache)
        generator.generate(facts())
        generator.generate(facts())
        assertThat(ok.requests).hasSize(1)

        val failing = FakeLocalAi(respond = { AiResponse.Failed("busy") })
        val g2 = MonthlyInsightGenerator(failing, MonthlyInsightCache())
        assertThat(g2.generate(facts())).isEqualTo(InsightText.Template)
        g2.generate(facts())
        assertThat(failing.requests).hasSize(2)
    }
}
