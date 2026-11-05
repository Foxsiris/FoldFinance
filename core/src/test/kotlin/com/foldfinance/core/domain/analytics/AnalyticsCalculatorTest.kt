package com.foldfinance.core.domain.analytics

import com.foldfinance.core.domain.TestTransactions.at
import com.foldfinance.core.domain.TestTransactions.tx
import com.foldfinance.core.model.TransactionSplit
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class AnalyticsCalculatorTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val berlin = ZoneId.of("Europe/Berlin")
    private val november = Period.month(YearMonth.of(2026, 11), moscow)

    private fun msk(local: String) = at(local, moscow)

    private val baseline = listOf(
        tx(msk("2026-11-01T10:00"), 1000_00, categoryId = 1),
        tx(msk("2026-11-02T12:00"), 500_00, categoryId = 2),
        tx(msk("2026-11-03T09:00"), 300_00, Direction.REFUND, categoryId = 1, kind = Kind.REFUND),
        tx(msk("2026-11-04T08:00"), 10_000_00, Direction.INCOME, kind = Kind.CREDIT),
        tx(msk("2026-11-04T09:00"), 5_000_00, Direction.INTERNAL_TRANSFER, kind = Kind.TRANSFER, categoryId = 9),
    )

    @Test
    fun `split operation is distributed over its parts, the rest stays in its category, totals unchanged`() {
        val plain = AnalyticsCalculator.summarize(baseline, november).currency("RUB")
        // 1000 ₽ в категории 1: 300 ₽ — в категорию 2, 200 ₽ — без категории, остаток 500 ₽ — в категории 1.
        val split = baseline.mapIndexed { i, t ->
            if (i == 0) {
                t.copy(splits = listOf(TransactionSplit(parentId = t.id, categoryId = 2, amountMinor = 300_00), TransactionSplit(parentId = t.id, categoryId = null, amountMinor = 200_00)))
            } else {
                t
            }
        }
        val rub = AnalyticsCalculator.summarize(split, november).currency("RUB")

        assertThat(rub.expenseMinor).isEqualTo(plain.expenseMinor)
        assertThat(rub.netExpenseMinor).isEqualTo(plain.netExpenseMinor)
        assertThat(rub.incomeMinor).isEqualTo(plain.incomeMinor)
        assertThat(rub.operationCount).isEqualTo(plain.operationCount)
        assertThat(rub.daily).isEqualTo(plain.daily)
        val byCategory = rub.categories.associate { it.categoryId to it.netExpenseMinor }
        // Категория 1: остаток 500 − возврат 300; категория 2: 500 + 300.
        assertThat(byCategory).containsExactly(1L, 200_00L, 2L, 800_00L, null, 200_00L)
        assertThat(rub.categories.sumOf { it.netExpenseMinor }).isEqualTo(rub.netExpenseMinor)
    }

    @Test
    fun `fully split operation leaves nothing in its own category, broken split is ignored`() {
        val t = tx(msk("2026-11-05T10:00"), 1000_00, categoryId = 1)
        val full = t.copy(splits = listOf(TransactionSplit(parentId = t.id, categoryId = 3, amountMinor = 1000_00)))
        assertThat(AnalyticsCalculator.categoryShares(full)).containsExactly(3L to 1000_00L)

        val broken = t.copy(splits = listOf(TransactionSplit(parentId = t.id, categoryId = 3, amountMinor = 1000_01)))
        assertThat(AnalyticsCalculator.categoryShares(broken)).containsExactly(1L to 1000_00L)
        val zero = t.copy(splits = listOf(TransactionSplit(parentId = t.id, categoryId = 3, amountMinor = 0)))
        assertThat(AnalyticsCalculator.categoryShares(zero)).containsExactly(1L to 1000_00L)
    }

    @Test
    fun `net expense is expense minus refund, refund is not income, internal transfer excluded`() {
        val rub = AnalyticsCalculator.summarize(baseline, november).currency("RUB")
        assertThat(rub.expenseMinor).isEqualTo(1500_00)
        assertThat(rub.refundMinor).isEqualTo(300_00)
        assertThat(rub.netExpenseMinor).isEqualTo(1200_00)
        assertThat(rub.incomeMinor).isEqualTo(10_000_00)
        assertThat(rub.balanceMinor).isEqualTo(8_800_00)
        assertThat(rub.operationCount).isEqualTo(4)
        assertThat(rub.categories.map { it.categoryId }).containsExactly(1L, 2L).inOrder()
        assertThat(rub.categories.none { it.categoryId == 9L }).isTrue()
    }

    @Test
    fun `refund reduces its own category`() {
        val rub = AnalyticsCalculator.summarize(baseline, november).currency("RUB")
        val c1 = rub.categories.first { it.categoryId == 1L }
        assertThat(c1.expenseMinor).isEqualTo(1000_00)
        assertThat(c1.refundMinor).isEqualTo(300_00)
        assertThat(c1.netExpenseMinor).isEqualTo(700_00)
        assertThat(c1.sharePercent).isEqualTo(58.33)
        assertThat(rub.categories.first { it.categoryId == 2L }.sharePercent).isEqualTo(41.67)
        assertThat(rub.topCategories(1).single().categoryId).isEqualTo(1L)
    }

    @Test
    fun `refund larger than expense gives negative net and is not a top category`() {
        val txs = listOf(
            tx(msk("2026-11-05T10:00"), 200_00, categoryId = 1),
            tx(msk("2026-11-06T10:00"), 900_00, Direction.REFUND, categoryId = 2),
        )
        val rub = AnalyticsCalculator.summarize(txs, november).currency("RUB")
        assertThat(rub.netExpenseMinor).isEqualTo(-700_00)
        assertThat(rub.incomeMinor).isEqualTo(0)
        assertThat(rub.categories.first { it.categoryId == 2L }.netExpenseMinor).isEqualTo(-900_00)
        assertThat(rub.categories.all { it.sharePercent == null }).isTrue()
        assertThat(rub.topCategories().map { it.categoryId }).containsExactly(1L)
        assertThat(rub.incomeToExpenseRatio).isNull()
    }

    @Test
    fun `currencies are summed separately without conversion`() {
        val txs = baseline + listOf(
            tx(msk("2026-11-05T10:00"), 20_00, currency = "USD", categoryId = 1),
            tx(msk("2026-11-06T10:00"), 5_00, Direction.REFUND, currency = "USD", categoryId = 1),
            tx(msk("2026-11-06T11:00"), 100_00, Direction.INCOME, currency = "EUR"),
        )
        val summary = AnalyticsCalculator.summarize(txs, november)
        assertThat(summary.currencies).containsExactly("RUB", "USD", "EUR")
        assertThat(summary.currency("RUB").netExpenseMinor).isEqualTo(1200_00)
        assertThat(summary.currency("USD").netExpenseMinor).isEqualTo(15_00)
        assertThat(summary.currency("USD").incomeMinor).isEqualTo(0)
        assertThat(summary.currency("EUR").incomeMinor).isEqualTo(100_00)
        assertThat(summary.currency("EUR").netExpenseMinor).isEqualTo(0)
        assertThat(summary.currency("GBP").operationCount).isEqualTo(0)
    }

    @Test
    fun `period boundaries follow the zone`() {
        val txs = listOf(
            tx(Instant.parse("2026-10-31T20:59:59Z"), 1_00), // 31.10 23:59:59 МСК — вне
            tx(Instant.parse("2026-10-31T21:00:00Z"), 2_00), // 01.11 00:00 МСК — внутри
            tx(Instant.parse("2026-11-30T20:59:59Z"), 4_00), // 30.11 23:59:59 МСК — внутри
            tx(Instant.parse("2026-11-30T21:00:00Z"), 8_00), // 01.12 00:00 МСК — вне
        )
        val msk = AnalyticsCalculator.summarize(txs, november).currency("RUB")
        assertThat(msk.expenseMinor).isEqualTo(6_00)
        assertThat(msk.daily.first().date).isEqualTo(LocalDate.of(2026, 11, 1))
        assertThat(msk.daily.first().expenseMinor).isEqualTo(2_00)
        assertThat(msk.daily.last().expenseMinor).isEqualTo(4_00)

        val utc = AnalyticsCalculator.summarize(txs, Period.month(YearMonth.of(2026, 11), ZoneOffset.UTC))
            .currency("RUB")
        assertThat(utc.expenseMinor).isEqualTo(12_00) // 4 + 8: оба 30.11 по UTC
    }

    @Test
    fun `DST day in Berlin counts both ambiguous hours`() {
        val day = Period.day(LocalDate.of(2026, 10, 25), berlin)
        val txs = listOf(
            tx(Instant.parse("2026-10-24T21:59:00Z"), 1_00), // 24.10 23:59 CEST — вне
            tx(Instant.parse("2026-10-25T00:30:00Z"), 2_00), // 02:30 CEST
            tx(Instant.parse("2026-10-25T01:30:00Z"), 4_00), // 02:30 CET
            tx(Instant.parse("2026-10-25T22:59:00Z"), 8_00), // 23:59 CET
            tx(Instant.parse("2026-10-25T23:00:00Z"), 16_00), // 26.10 00:00 CET — вне
        )
        val rub = AnalyticsCalculator.summarize(txs, day).currency("RUB")
        assertThat(rub.expenseMinor).isEqualTo(14_00)
        assertThat(rub.daily).hasSize(1)
        assertThat(rub.averageDailyNetExpenseMinor).isEqualTo(14_00)
    }

    @Test
    fun `daily series covers every day with net values`() {
        val rub = AnalyticsCalculator.summarize(baseline, november).currency("RUB")
        assertThat(rub.daily).hasSize(30)
        val byDate = rub.daily.associateBy { it.date }
        assertThat(byDate.getValue(LocalDate.of(2026, 11, 1)).netExpenseMinor).isEqualTo(1000_00)
        assertThat(byDate.getValue(LocalDate.of(2026, 11, 3)).netExpenseMinor).isEqualTo(-300_00)
        assertThat(byDate.getValue(LocalDate.of(2026, 11, 3)).refundMinor).isEqualTo(300_00)
        assertThat(byDate.getValue(LocalDate.of(2026, 11, 4)).incomeMinor).isEqualTo(10_000_00)
        assertThat(byDate.getValue(LocalDate.of(2026, 11, 4)).netExpenseMinor).isEqualTo(0)
        assertThat(rub.daily.sumOf { it.netExpenseMinor }).isEqualTo(rub.netExpenseMinor)
    }

    @Test
    fun `average per day uses all days of a past period and elapsed days of the current one`() {
        val txs = listOf(tx(msk("2026-11-02T10:00"), 3000_00))
        val full = AnalyticsCalculator.summarize(txs, november)
        assertThat(full.daysCounted).isEqualTo(30)
        assertThat(full.currency("RUB").averageDailyNetExpenseMinor).isEqualTo(100_00)

        val current = AnalyticsCalculator.summarize(txs, november, now = msk("2026-11-10T12:00"))
        assertThat(current.daysCounted).isEqualTo(10)
        assertThat(current.currency("RUB").averageDailyNetExpenseMinor).isEqualTo(300_00)

        val after = AnalyticsCalculator.summarize(txs, november, now = msk("2026-12-15T12:00"))
        assertThat(after.currency("RUB").averageDailyNetExpenseMinor).isEqualTo(100_00)
    }

    @Test
    fun `average per day rounds half up in minor units`() {
        val week = Period.week(LocalDate.of(2026, 11, 2), moscow)
        val txs = listOf(tx(msk("2026-11-02T10:00"), 1000))
        // 1000 / 3 = 333.33 → 333; 1000 / 6 = 166.67 → 167
        assertThat(AnalyticsCalculator.summarize(txs, week, now = msk("2026-11-04T10:00"))
            .currency("RUB").averageDailyNetExpenseMinor).isEqualTo(333)
        assertThat(AnalyticsCalculator.summarize(txs, week, now = msk("2026-11-07T10:00"))
            .currency("RUB").averageDailyNetExpenseMinor).isEqualTo(167)
    }

    @Test
    fun `income to expense ratio`() {
        val rub = AnalyticsCalculator.summarize(baseline, november).currency("RUB")
        assertThat(rub.incomeToExpenseRatio).isWithin(1e-9).of(10_000.0 / 1_200.0)
        assertThat(rub.expenseShareOfIncomePercent).isEqualTo(12.0)
    }

    @Test
    fun `needs review count and empty summary`() {
        val txs = baseline + tx(msk("2026-11-07T10:00"), 1_00, needsReview = true) +
            tx(msk("2026-10-07T10:00"), 1_00, needsReview = true)
        assertThat(AnalyticsCalculator.summarize(txs, november).needsReviewCount).isEqualTo(1)

        val empty = AnalyticsCalculator.summarize(emptyList(), november)
        assertThat(empty.byCurrency).isEmpty()
        assertThat(empty.currency("RUB").daily).hasSize(30)
        assertThat(empty.currency("RUB").netExpenseMinor).isEqualTo(0)
    }

    @Test
    fun `compare with previous period`() {
        val october = november.previous()
        val txs = baseline + listOf(
            tx(msk("2026-10-10T10:00"), 800_00, categoryId = 1),
            tx(msk("2026-10-11T10:00"), 200_00, categoryId = 3),
            tx(msk("2026-10-12T10:00"), 20_00, currency = "USD"),
        )
        val cmp = AnalyticsCalculator.compare(
            AnalyticsCalculator.summarize(txs, november),
            AnalyticsCalculator.summarize(txs, october),
        )
        val rub = cmp.byCurrency.getValue("RUB")
        assertThat(rub.netExpense.currentMinor).isEqualTo(1200_00)
        assertThat(rub.netExpense.previousMinor).isEqualTo(1000_00)
        assertThat(rub.netExpense.deltaMinor).isEqualTo(200_00)
        assertThat(rub.netExpense.percentExact).isEqualTo(BigDecimal("20.00"))
        assertThat(rub.netExpense.percent).isEqualTo(20.0)
        assertThat(rub.income.percent).isNull() // в октябре дохода не было
        assertThat(rub.categories.getValue(1L)).isEqualTo(AmountChange(700_00, 800_00))
        assertThat(rub.categories.getValue(1L).percent).isEqualTo(-12.5)
        assertThat(rub.categories.getValue(3L)).isEqualTo(AmountChange(0, 200_00))
        assertThat(rub.categories.getValue(2L).percent).isNull()

        val usd = cmp.byCurrency.getValue("USD")
        assertThat(usd.netExpense.deltaMinor).isEqualTo(-20_00)
        assertThat(usd.netExpense.percent).isEqualTo(-100.0)
    }

    @Test
    fun `today and month summary for the outer screen`() {
        val now = msk("2026-11-05T10:00")
        val txs = listOf(
            tx(msk("2026-11-05T00:05"), 300_00),
            tx(msk("2026-11-05T09:00"), 100_00, Direction.REFUND),
            tx(msk("2026-11-04T23:55"), 500_00),
            tx(msk("2026-10-31T23:59"), 700_00, needsReview = true),
            tx(msk("2026-11-05T08:00"), 50_00, currency = "USD", needsReview = true),
            tx(msk("2026-11-05T08:30"), 9_000_00, Direction.INTERNAL_TRANSFER, kind = Kind.TRANSFER),
            tx(msk("2026-11-05T08:40"), 9_000_00, Direction.INCOME),
        )
        val summary = AnalyticsCalculator.todayMonth(txs, "RUB", now, moscow)
        assertThat(summary.currency).isEqualTo("RUB")
        assertThat(summary.todayNetExpenseMinor).isEqualTo(200_00)
        assertThat(summary.monthNetExpenseMinor).isEqualTo(700_00)
        assertThat(summary.needsReviewCount).isEqualTo(2)
    }
}
