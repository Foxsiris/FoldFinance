package com.foldfinance.app.ai.features.query

import com.foldfinance.app.ai.AiResponse
import com.foldfinance.app.ai.AiTier
import com.foldfinance.app.ai.features.AiData
import com.foldfinance.app.ai.features.AiData.tx
import com.foldfinance.app.ai.features.FakeLocalAi
import com.foldfinance.core.domain.analytics.AmountChange
import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.parser.Direction
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.YearMonth

/** Answers are computed by Kotlin from operations; the model's numbers never reach them. */
class QueryEngineTest {

    private val zone = AiData.zone
    private val october = Period.month(YearMonth.of(2026, 10), zone)
    private val september = Period.month(YearMonth.of(2026, 9), zone)

    private val txs = listOf(
        tx("2026-10-10T08:00", 41_900, "Яндекс Такси", "transport"),
        tx("2026-10-20T22:10", 35_050, "YANDEX TAXI", "transport"),
        tx("2026-10-21T08:10", 6_500, "Метро", "transport"),
        tx("2026-10-12T19:00", 124_050, "Пятёрочка", "groceries"),
        tx("2026-10-15T12:00", 30_000, "Ozon", "shopping"),
        tx("2026-10-18T12:00", 10_000, "Ozon", "shopping", Direction.REFUND),
        tx("2026-10-05T10:00", 1_000_000, null, "transfers", Direction.INTERNAL_TRANSFER),
        tx("2026-10-17T11:05", 1_299, "Steam", "entertainment", currency = "USD"),
        tx("2026-09-14T19:00", 200_000, "Пятерочка", "groceries"),
        tx("2026-11-01T09:00", 30_000, "Яндекс Такси", "transport"),
    )

    private fun answer(call: QueryCall) = QueryEngine.answer(call, txs, AiData.categories, "RUB")

    @Test fun `spend on a merchant sums only its operations in the period`() {
        val a = answer(QueryCall.Spend(null, "такси", october))
        // Cyrillic and Latin spellings of the merchant both match; November is outside the period.
        assertThat(a.totals.single().amountMinor).isEqualTo(41_900 + 35_050)
        assertThat(a.operationCount).isEqualTo(2)
        assertThat(a.operations.map { it.amountMinor }).containsExactly(35_050L, 41_900L).inOrder()
    }

    @Test fun `refunds reduce and internal transfers do not count`() {
        assertThat(answer(QueryCall.Spend(AiData.category("shopping"), null, october)).totals.single().amountMinor).isEqualTo(20_000)
        assertThat(answer(QueryCall.Spend(AiData.category("transfers"), null, october)).totals.single().amountMinor).isEqualTo(0)
        val all = answer(QueryCall.Spend(null, null, october))
        assertThat(all.totals.map { it.currency to it.amountMinor })
            .containsExactly("RUB" to 41_900L + 35_050 + 6_500 + 124_050 + 30_000 - 10_000, "USD" to 1_299L).inOrder()
    }

    @Test fun `grouping by category comes from the analytics calculator`() {
        val a = answer(QueryCall.Spend(null, null, october, GroupBy.CATEGORY))
        val rub = a.rows.filter { it.currency == "RUB" }.associate { (it.label as RowLabel.OfCategory).category.key to it.amountMinor }
        assertThat(rub).containsExactly("groceries", 124_050L, "transport", 83_450L, "shopping", 20_000L).inOrder()
    }

    @Test fun `where did I spend more than last month`() {
        val a = answer(QueryCall.Compare(october, september, GroupBy.CATEGORY))
        val rub = a.totals.first()
        assertThat(rub.change).isEqualTo(AmountChange(227_500, 200_000))
        val rows = a.rows.filter { it.currency == "RUB" }
        assertThat(rows.map { (it.label as RowLabel.OfCategory).category.key }).containsExactly("transport", "shopping", "groceries").inOrder()
        assertThat(rows.map { it.change!!.deltaMinor }).containsExactly(83_450L, 20_000L, -75_950L).inOrder()
    }

    @Test fun `top merchants and operations`() {
        // Top n per currency: currencies are never mixed.
        val merchants = answer(QueryCall.Top(october, 2, TopBy.MERCHANT)).rows
        assertThat(merchants.map { Triple((it.label as RowLabel.OfMerchant).name, it.currency, it.amountMinor) })
            .containsExactly(Triple("Пятёрочка", "RUB", 124_050L), Triple("YANDEX TAXI", "RUB", 76_950L), Triple("Steam", "USD", 1_299L))
            .inOrder()
        val ops = answer(QueryCall.Top(october, 1, TopBy.OPERATION)).rows
        assertThat((ops.single().label as RowLabel.OfOperation).transaction.merchant).isEqualTo("Пятёрочка")
    }

    @Test fun `list matches ё and е`() {
        val a = answer(QueryCall.ListOps(null, "пятерочка", Period.custom(september.startDate, october.endDateInclusive, zone)))
        assertThat(a.operations.map { it.amountMinor }).containsExactly(124_050L, 200_000L).inOrder()
    }

    @Test fun `the answer total never comes from the model`() = runTest {
        // The model adds its own «answer» with a made-up sum; only the function call is used.
        val ai = FakeLocalAi.answering("""{"fn":"spend","args":{"merchant":"такси","period":"month:10"},"answer":"Вы потратили 999 999 ₽"}""")
        val parsed = QueryAssistant(ai).interpret("Сколько ушло на такси в октябре?", AiData.categories, AiData.today, zone)
        val call = (parsed as ParsedCall.Ok).call
        assertThat(answer(call).totals.single().amountMinor).isEqualTo(76_950)

        val request = ai.requests.single()
        assertThat(request.tier).isEqualTo(AiTier.FULL)
        assertThat(request.jsonOnly).isTrue()
        // The model sees category names and the date, never an amount from the database.
        assertThat(request.system).contains("\"Транспорт\"")
        assertThat(request.system).doesNotContain("41")
    }

    @Test fun `model failure becomes a rejection, not an exception`() = runTest {
        val ai = FakeLocalAi(respond = { AiResponse.Failed("busy") })
        val parsed = QueryAssistant(ai).interpret("сколько?", AiData.categories, AiData.today, zone)
        assertThat(parsed).isEqualTo(ParsedCall.Rejected(QueryRejection.MODEL_FAILED, "busy"))
    }
}
