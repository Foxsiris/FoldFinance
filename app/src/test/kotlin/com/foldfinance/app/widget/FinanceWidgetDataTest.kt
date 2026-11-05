package com.foldfinance.app.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.core.model.Source
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class FinanceWidgetDataTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val now = at(2026, 11, 5, 14, 5)

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Instant =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant()

    private var seq = 0L

    /** MoneyFormat разделяет разряды и валюту узким неразрывным пробелом. */
    private fun nb(s: String) = s.replace(' ', MoneyFormat.NARROW_NBSP)

    private fun tx(at: Instant, amountMinor: Long, direction: Direction = Direction.EXPENSE, currency: String = "RUB") =
        Transaction(
            id = ++seq,
            occurredAt = at,
            amountMinor = amountMinor,
            currency = currency,
            direction = direction,
            kind = Kind.PURCHASE,
            merchant = "SHOP",
            cardMask = "1234",
            categoryId = null,
            confidence = 1f,
            needsReview = false,
            source = Source.SMS,
            dedupKey = "t-$seq",
            createdAt = at,
            updatedAt = at,
        )

    private fun summarize(vararg txs: Transaction) = FinanceWidgetData.summarize(
        transactions = txs.toList(),
        currency = "RUB",
        now = now,
        zone = zone,
        needsReviewCount = 2,
        hideAmounts = false,
        hasTransactions = true,
        smsAccess = true,
    )

    @Test fun dailyBucketsFollowPhoneZoneAndNetRefunds() {
        val data = summarize(
            tx(at(2026, 11, 1, 0, 30), 100_00), // 31 октября 21:30 UTC — уже 1 ноября по Москве
            tx(at(2026, 11, 3), 500_00),
            tx(at(2026, 11, 3), 120_00, Direction.REFUND),
            tx(at(2026, 11, 5, 9), 1_840_00),
        )

        assertThat(data.month).isEqualTo(YearMonth.of(2026, 11))
        assertThat(data.todayOfMonth).isEqualTo(5)
        assertThat(data.dailyNetExpenseMinor).hasSize(30)
        assertThat(data.dailyNetExpenseMinor.take(5)).containsExactly(100_00L, 0L, 380_00L, 0L, 1_840_00L).inOrder()
        assertThat(data.todayNetExpenseMinor).isEqualTo(1_840_00)
        assertThat(data.monthNetExpenseMinor).isEqualTo(2_320_00)
        assertThat(data.needsReviewCount).isEqualTo(2)
    }

    @Test fun incomeCountsOnlyPrimaryCurrencyIncomeOfThisMonth() {
        val data = summarize(
            tx(at(2026, 11, 2), 95_000_00, Direction.INCOME),
            tx(at(2026, 11, 2), 10_000_00, Direction.INTERNAL_TRANSFER),
            tx(at(2026, 11, 2), 300_00, Direction.INCOME, currency = "USD"),
            tx(at(2026, 10, 31), 1_000_00, Direction.INCOME),
            tx(at(2026, 11, 4), 700_00, Direction.EXPENSE, currency = "USD"),
        )

        assertThat(data.monthIncomeMinor).isEqualTo(95_000_00)
        assertThat(data.monthNetExpenseMinor).isEqualTo(0)
        assertThat(data.dailyNetExpenseMinor.sum()).isEqualTo(0)
    }

    @Test fun barGridKeepsContainersWithinTenChildren() {
        for (days in 28..31) {
            val (groups, perGroup) = DayBarGrid.of(days)
            assertThat(groups).isAtMost(10)
            assertThat(perGroup).isAtMost(10)
            assertThat(groups * perGroup).isAtLeast(days)
            assertThat(groups * perGroup - days).isAtMost(1)
        }
        assertThat(DayBarGrid.of(30)).isEqualTo(10 to 3)
    }

    @Test fun layoutFollowsMockupGrid() {
        assertThat(FinanceWidgetLayout.forSize(DpSize(352.dp, 260.dp))).isEqualTo(FinanceWidgetLayout.Large)
        assertThat(FinanceWidgetLayout.forSize(DpSize(352.dp, 170.dp))).isEqualTo(FinanceWidgetLayout.Medium)
        assertThat(FinanceWidgetLayout.forSize(DpSize(352.dp, 80.dp))).isEqualTo(FinanceWidgetLayout.Strip)
        assertThat(FinanceWidgetLayout.forSize(DpSize(170.dp, 170.dp))).isEqualTo(FinanceWidgetLayout.Small)
        assertThat(FinanceWidgetLayout.forSize(DpSize(170.dp, 80.dp))).isEqualTo(FinanceWidgetLayout.StripCompact)
        assertThat(FinanceWidgetLayout.forSize(DpSize(80.dp, 40.dp))).isEqualTo(FinanceWidgetLayout.StripCompact)
    }

    @Test fun amountsAreRoundedToWholeUnits() {
        assertThat(WidgetMoney.format(1_840_49, "RUB")).isEqualTo(nb("1 840 ₽"))
        assertThat(WidgetMoney.format(1_840_50, "RUB")).isEqualTo(nb("1 841 ₽"))
        assertThat(WidgetMoney.format(-120_50, "RUB")).isEqualTo(nb("−121 ₽"))
        assertThat(WidgetMoney.format(1_500, "JPY")).isEqualTo(nb("1 500 JPY"))
    }
}
