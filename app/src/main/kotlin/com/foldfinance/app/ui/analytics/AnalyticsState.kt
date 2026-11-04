package com.foldfinance.app.ui.analytics

import androidx.compose.runtime.Immutable
import com.foldfinance.core.domain.analytics.AmountChange
import com.foldfinance.core.domain.analytics.AnalyticsCalculator
import com.foldfinance.core.domain.analytics.CurrencyComparison
import com.foldfinance.core.domain.analytics.CurrencySummary
import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.domain.analytics.PeriodType
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.Transaction
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** What one column of the chart stands for. */
enum class ChartGranularity { DAY, MONTH }

/**
 * One column of the dynamics chart: a day, or a month for long periods.
 *
 * @property start first date of the bucket; also the key of the selection.
 * @property endExclusive first date after the bucket.
 */
@Immutable
data class ChartBucket(
    val start: LocalDate,
    val endExclusive: LocalDate,
    val netExpenseMinor: Long,
    val incomeMinor: Long,
)

/** A category row of the breakdown, joined with the category and its change to the previous period. */
@Immutable
data class CategoryShare(
    val categoryId: Long?,
    val category: Category?,
    val netExpenseMinor: Long,
    val sharePercent: Double?,
    val change: AmountChange?,
)

/** Short totals of one currency (each currency is counted separately, no exchange rates). */
@Immutable
data class CurrencyTotal(val currency: String, val netExpenseMinor: Long, val incomeMinor: Long)

/**
 * Everything the analytics screen draws. Built by [buildAnalyticsState] from the database rows,
 * never from demo data.
 *
 * @property currency selected currency; `null` when the period has no operations.
 * @property summary totals of [currency].
 * @property buckets chart columns; empty for a one-day period (nothing to compare inside a day).
 * @property selectedBucket column the user tapped; its operations are [operations].
 * @property operations operations of [selectedBucket], or of the whole day for a one-day period.
 */
@Immutable
data class AnalyticsUiState(
    val isLoading: Boolean,
    val period: Period,
    val today: LocalDate,
    val canGoNext: Boolean,
    val currencies: List<CurrencyTotal> = emptyList(),
    val currency: String? = null,
    val summary: CurrencySummary? = null,
    val comparison: CurrencyComparison? = null,
    val daysCounted: Int = 0,
    val needsReviewCount: Int = 0,
    val granularity: ChartGranularity = ChartGranularity.DAY,
    val buckets: List<ChartBucket> = emptyList(),
    val selectedBucket: ChartBucket? = null,
    val operations: List<Transaction> = emptyList(),
    val topCategories: List<CategoryShare> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
) {
    /** The period has at least one counted operation. */
    val hasData: Boolean get() = currencies.isNotEmpty()

    /** Operations list is shown: a tapped column, or a one-day period. */
    val showsOperations: Boolean get() = selectedBucket != null || period.type == PeriodType.DAY

    /** Index of [selectedBucket] in [buckets], for the chart highlight. */
    val selectedIndex: Int? get() = selectedBucket?.let { sel -> buckets.indexOfFirst { it.start == sel.start }.takeIf { it >= 0 } }
}

/** Columns are months for periods longer than this, days otherwise. */
const val MAX_DAILY_BUCKETS: Int = 62

/** Number of categories in the breakdown. */
const val TOP_CATEGORIES: Int = 6

/**
 * Pure state builder: summarises [transactions] (they cover the current and the previous
 * period) for [period], picks the currency and the tapped column.
 *
 * @param selectedDay start date of the tapped column, if any.
 * @param preferredCurrency currency chosen by the user; falls back to the one with the largest
 *   net expense.
 */
fun buildAnalyticsState(
    period: Period,
    transactions: List<Transaction>,
    categories: List<Category>,
    selectedDay: LocalDate?,
    preferredCurrency: String?,
    now: Instant,
    today: LocalDate,
): AnalyticsUiState {
    val summary = AnalyticsCalculator.summarize(transactions, period, now)
    val previous = AnalyticsCalculator.summarize(transactions, period.previous(), null)
    val comparison = AnalyticsCalculator.compare(summary, previous)
    val currencies = summary.byCurrency.values.map { CurrencyTotal(it.currency, it.netExpenseMinor, it.incomeMinor) }
    val currency = preferredCurrency?.takeIf { it in summary.byCurrency } ?: currencies.firstOrNull()?.currency
    val currencySummary = currency?.let { summary.currency(it) }
    val currencyComparison = currency?.let { comparison.byCurrency[it] }
    val byId = categories.associateBy { it.id }

    val granularity = if (period.type == PeriodType.YEAR || period.days() > MAX_DAILY_BUCKETS) {
        ChartGranularity.MONTH
    } else {
        ChartGranularity.DAY
    }
    val buckets = when {
        currencySummary == null || period.type == PeriodType.DAY -> emptyList()
        granularity == ChartGranularity.DAY -> currencySummary.daily.map {
            ChartBucket(it.date, it.date.plusDays(1), it.netExpenseMinor, it.incomeMinor)
        }
        else -> currencySummary.daily.groupBy { YearMonth.from(it.date) }.map { (month, days) ->
            ChartBucket(
                start = maxOf(month.atDay(1), period.startDate),
                endExclusive = minOf(month.plusMonths(1).atDay(1), period.endDateExclusive),
                netExpenseMinor = days.sumOf { it.netExpenseMinor },
                incomeMinor = days.sumOf { it.incomeMinor },
            )
        }
    }
    val selectedBucket = selectedDay?.let { day -> buckets.firstOrNull { it.start == day } }
    val operationsRange: Pair<LocalDate, LocalDate>? = when {
        selectedBucket != null -> selectedBucket.start to selectedBucket.endExclusive
        period.type == PeriodType.DAY -> period.startDate to period.endDateExclusive
        else -> null
    }
    val operations = operationsRange?.let { (from, to) ->
        val start = from.atStartOfDay(period.zone).toInstant()
        val end = to.atStartOfDay(period.zone).toInstant()
        transactions
            .filter { !it.occurredAt.isBefore(start) && it.occurredAt.isBefore(end) }
            .sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenByDescending { it.id })
    }.orEmpty()

    val topCategories = currencySummary?.topCategories(TOP_CATEGORIES).orEmpty().map { breakdown ->
        CategoryShare(
            categoryId = breakdown.categoryId,
            category = breakdown.categoryId?.let(byId::get),
            netExpenseMinor = breakdown.netExpenseMinor,
            sharePercent = breakdown.sharePercent,
            change = currencyComparison?.categories?.get(breakdown.categoryId),
        )
    }

    return AnalyticsUiState(
        isLoading = false,
        period = period,
        today = today,
        canGoNext = canGoNext(period, today),
        currencies = currencies,
        currency = currency,
        summary = currencySummary,
        comparison = currencyComparison,
        daysCounted = summary.daysCounted,
        needsReviewCount = summary.needsReviewCount,
        granularity = granularity,
        buckets = buckets,
        selectedBucket = selectedBucket,
        operations = operations,
        topCategories = topCategories,
        categories = byId,
    )
}

/** Future periods hold no operations: «next» stops at the period containing today. */
fun canGoNext(period: Period, today: LocalDate): Boolean = !period.next().startDate.isAfter(today)
