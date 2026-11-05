package com.foldfinance.app.ai.features.query

import com.foldfinance.app.ai.features.AiData
import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.domain.analytics.PeriodType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Strict validation of the model's function call: nothing unknown gets through. */
class QueryCallParserTest {

    private val zone = AiData.zone
    private val today = AiData.today

    private fun parse(json: String, scope: ChatScope = ChatScope()) =
        QueryCallParser.parse(json, AiData.categories, today, zone, scope)

    private fun ok(json: String, scope: ChatScope = ChatScope()): QueryCall {
        val result = parse(json, scope)
        assertThat(result).isInstanceOf(ParsedCall.Ok::class.java)
        return (result as ParsedCall.Ok).call
    }

    private fun rejected(json: String): QueryRejection {
        val result = parse(json)
        assertThat(result).isInstanceOf(ParsedCall.Rejected::class.java)
        return (result as ParsedCall.Rejected).reason
    }

    @Test fun `taxi in October is a merchant filter for October 2026`() {
        val call = ok("""{"fn":"spend","args":{"merchant":"такси","period":"month:10"}}""")
        assertThat(call).isEqualTo(QueryCall.Spend(null, "такси", Period.month(YearMonth.of(2026, 10), zone)))
    }

    @Test fun `category must be one of the user's categories`() {
        val call = ok("""{"fn":"spend","args":{"category":"транспорт","period":"last_month"}}""") as QueryCall.Spend
        assertThat(call.category).isEqualTo(AiData.category("transport"))
        assertThat(rejected("""{"fn":"spend","args":{"category":"Такси","period":"this_month"}}""")).isEqualTo(QueryRejection.UNKNOWN_CATEGORY)
        assertThat(rejected("""{"fn":"list","args":{"category":"Кофейни"}}""")).isEqualTo(QueryRejection.UNKNOWN_CATEGORY)
    }

    @Test fun `unknown function, garbage and non-questions are rejected`() {
        assertThat(rejected("""{"fn":"delete_all","args":{}}""")).isEqualTo(QueryRejection.UNKNOWN_FUNCTION)
        assertThat(rejected("""{"args":{}}""")).isEqualTo(QueryRejection.UNKNOWN_FUNCTION)
        assertThat(rejected("Не знаю")).isEqualTo(QueryRejection.NO_JSON)
        assertThat(rejected("""{"fn":"none"}""")).isEqualTo(QueryRejection.NOT_A_QUESTION)
    }

    @Test fun `bad periods and arguments are rejected`() {
        assertThat(rejected("""{"fn":"spend","args":{"period":"next_month"}}""")).isEqualTo(QueryRejection.BAD_PERIOD)
        assertThat(rejected("""{"fn":"spend","args":{"period":"day:2027-01-01"}}""")).isEqualTo(QueryRejection.BAD_PERIOD)
        assertThat(rejected("""{"fn":"spend","args":{"period":"range:2026-10-31..2026-10-01"}}""")).isEqualTo(QueryRejection.BAD_PERIOD)
        assertThat(rejected("""{"fn":"top","args":{"period":"this_month","n":50}}""")).isEqualTo(QueryRejection.BAD_ARGUMENT)
        assertThat(rejected("""{"fn":"top","args":{"by":"color"}}""")).isEqualTo(QueryRejection.BAD_ARGUMENT)
        assertThat(rejected("""{"fn":"spend","args":{"group_by":"weekday"}}""")).isEqualTo(QueryRejection.BAD_ARGUMENT)
    }

    @Test fun `a month without a year is the latest one that has started`() {
        val december = ok("""{"fn":"spend","args":{"period":"month:12"}}""")
        assertThat(december.period).isEqualTo(Period.month(YearMonth.of(2025, 12), zone))
        val november = ok("""{"fn":"spend","args":{"period":"month:11"}}""")
        assertThat(november.period).isEqualTo(Period.month(YearMonth.of(2026, 11), zone))
    }

    @Test fun `compare of a month in progress uses the same days of the previous month`() {
        val call = ok("""{"fn":"compare","args":{"period":"this_month","group_by":"category"}}""") as QueryCall.Compare
        assertThat(call.period).isEqualTo(Period.custom(LocalDate.of(2026, 11, 1), today, zone))
        assertThat(call.previous).isEqualTo(Period.custom(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), zone))
        assertThat(call.groupBy).isEqualTo(GroupBy.CATEGORY)
    }

    @Test fun `compare of a finished month uses the whole previous month`() {
        val call = ok("""{"fn":"compare","args":{"period":"month:10"}}""") as QueryCall.Compare
        assertThat(call.previous).isEqualTo(Period.month(YearMonth.of(2026, 9), zone))
        assertThat(call.previous.type).isEqualTo(PeriodType.MONTH)
    }

    @Test fun `model wrapping the JSON in a fence is tolerated`() {
        val call = ok("Вот вызов:\n```json\n{\"fn\":\"top\",\"args\":{\"period\":\"last_days:30\",\"n\":3,\"by\":\"merchant\"}}\n```")
        assertThat(call).isEqualTo(QueryCall.Top(Period.custom(today.minusDays(29), today, zone), 3, TopBy.MERCHANT))
    }

    @Test fun `scope supplies the period and the category when the question names none`() {
        val september = Period.month(YearMonth.of(2026, 9), zone)
        val groceries = AiData.category("groceries")
        val scope = ChatScope(period = september, categoryId = groceries.id)
        assertThat(ok("""{"fn":"spend","args":{}}""", scope)).isEqualTo(QueryCall.Spend(groceries, null, september))
        // A named merchant overrides the scope's category.
        assertThat(ok("""{"fn":"spend","args":{"merchant":"Лента"}}""", scope)).isEqualTo(QueryCall.Spend(null, "Лента", september))
        // Without scope: the current month.
        assertThat(ok("""{"fn":"spend","args":{}}""").period).isEqualTo(Period.month(today, zone))
    }

    @Test fun `saved call restores to the same call`() {
        val calls = listOf(
            ok("""{"fn":"spend","args":{"category":"Продукты","period":"month:10","group_by":"day"}}"""),
            ok("""{"fn":"compare","args":{"period":"this_month","group_by":"merchant"}}"""),
            ok("""{"fn":"top","args":{"period":"this_year","n":7,"by":"operation"}}"""),
            ok("""{"fn":"list","args":{"merchant":"Ozon","period":"last_week"}}"""),
        )
        for (call in calls) {
            assertThat(SavedQueryCall.of(call).restore(AiData.categories, zone)).isEqualTo(call)
        }
        // A deleted category makes the saved call unusable rather than silently widening it.
        assertThat(SavedQueryCall.of(calls[0]).restore(emptyList(), zone)).isNull()
    }
}
