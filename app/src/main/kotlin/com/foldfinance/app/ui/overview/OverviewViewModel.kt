package com.foldfinance.app.ui.overview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldfinance.app.ui.components.Period as PeriodChoice
import com.foldfinance.app.prefs.AppPreferences
import com.foldfinance.app.prefs.AppSettings
import com.foldfinance.app.ui.transaction.UiClock
import com.foldfinance.core.data.repository.BalanceRepository
import com.foldfinance.core.data.repository.CategoryRepository
import com.foldfinance.core.data.repository.SubscriptionRepository
import com.foldfinance.core.data.repository.TransactionFilter
import com.foldfinance.core.data.repository.TransactionRepository
import com.foldfinance.core.domain.analytics.AnalyticsCalculator
import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.domain.analytics.PeriodType
import com.foldfinance.core.domain.subscriptions.SubscriptionCosts
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.KnownBalance
import com.foldfinance.core.model.Subscription
import com.foldfinance.core.model.SubscriptionStatus
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * One bar of the overview chart: a day (week/month periods) or a month (year period).
 * @property key `epochDay` of [date]; for months, of the first day of the month.
 */
data class ChartBar(val key: Long, val date: LocalDate, val amountMinor: Long)

/** A category's share of the period's net expenses. [categoryId] `null` — without a category. */
data class CategoryShare(val categoryId: Long?, val amountMinor: Long, val percent: Int)

/** Selected category filter of the overview list: a category or «без категории». */
sealed interface CategorySelection {
    data class Of(val categoryId: Long) : CategorySelection
    data object Uncategorized : CategorySelection

    fun matches(tx: Transaction): Boolean = when (this) {
        is Of -> tx.categoryId == categoryId
        Uncategorized -> tx.categoryId == null
    }
}

data class OverviewUiState(
    val loading: Boolean = true,
    val hasAnyTransactions: Boolean = false,
    /** Currency of the totals: «Валюта» from the settings ([AppPreferences.defaultCurrency]). */
    val currency: String = AppSettings.DEFAULT_CURRENCY,
    val today: LocalDate = LocalDate.MIN,
    val todayMinor: Long = 0,
    val monthMinor: Long = 0,
    val balances: List<KnownBalance> = emptyList(),
    val reviewCount: Int = 0,
    val period: PeriodChoice = PeriodChoice.Month,
    val periodStart: LocalDate = LocalDate.MIN,
    val periodExpenseMinor: Long = 0,
    val bars: List<ChartBar> = emptyList(),
    val breakdown: List<CategoryShare> = emptyList(),
    val selectedCategory: CategorySelection? = null,
    val selectedBar: Long? = null,
    /** Operations of the period, newest first, narrowed by the selected category and bar. */
    val transactions: List<Transaction> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
    /** Subscriptions entry of the overview (the outer-screen bar has no «Подписки»). */
    val subscriptions: SubscriptionsSummary = SubscriptionsSummary(),
)

/**
 * What the «Подписки» card of the overview says.
 *
 * @property monthlyMinor monthly cost of confirmed subscriptions in [OverviewUiState.currency].
 * @property confirmed confirmed subscriptions in any currency.
 * @property candidates detector findings waiting for a decision.
 */
data class SubscriptionsSummary(val monthlyMinor: Long = 0, val confirmed: Int = 0, val candidates: Int = 0) {
    val isEmpty: Boolean get() = confirmed == 0 && candidates == 0
}

/**
 * «Обзор»: today / month totals by known operations, known bank balances (shown apart), the
 * period chart and category breakdown, and the list next to them. A tap on a category or a bar
 * filters the list without navigation; the period and both selections live in
 * [SavedStateHandle], so they survive folding and recreation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
    balances: BalanceRepository,
    subscriptions: SubscriptionRepository,
    preferences: AppPreferences,
    @UiClock private val clock: Clock,
) : ViewModel() {

    val zone: ZoneId get() = clock.zone
    fun now(): Instant = clock.instant()

    private val period = handle.getStateFlow(KEY_PERIOD, PeriodChoice.Month.name).map { PeriodChoice.valueOf(it) }
    private val selection = combine(
        handle.getStateFlow<Long?>(KEY_CATEGORY, null),
        handle.getStateFlow<Long?>(KEY_BAR, null),
    ) { c, b -> c to b }

    private val hasAny = transactions.observe(TransactionFilter()).map { it.isNotEmpty() }.distinctUntilChanged()

    private val periodData = period.flatMapLatest { choice ->
        val now = clock.instant()
        val range = periodOf(choice, now)
        val monthStart = Period.containing(PeriodType.MONTH, now, clock.zone).start
        val from = minOf(range.start, monthStart)
        transactions.observe(TransactionFilter(from = from)).map { list -> Triple(choice, range, list) }
    }

    val state: StateFlow<OverviewUiState> = combine(
        periodData,
        selection,
        categories.observeAll(),
        balances.observeLatest(),
        combine(transactions.observeReviewCount(), hasAny, preferences.defaultCurrency, subscriptions.observeAll()) { count, any, currency, subs ->
            Extras(count, any, currency, subs)
        },
    ) { (choice, range, list), (categoryKey, barKey), cats, known, extras ->
        build(choice, range, list, categoryKey, barKey, cats, known, extras)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    fun setPeriod(period: PeriodChoice) {
        handle[KEY_PERIOD] = period.name
        handle[KEY_BAR] = null
    }

    /** Tap on a category: filter the list by it, a second tap clears. */
    fun toggleCategory(selection: CategorySelection) {
        val key = selection.toKey()
        handle[KEY_CATEGORY] = if (handle.get<Long?>(KEY_CATEGORY) == key) null else key
    }

    /** Tap on a bar: filter the list by its day (month in the year view), a second tap clears. */
    fun toggleBar(key: Long) {
        handle[KEY_BAR] = if (handle.get<Long?>(KEY_BAR) == key) null else key
    }

    fun clearSelection() {
        handle[KEY_CATEGORY] = null
        handle[KEY_BAR] = null
    }

    private fun periodOf(choice: PeriodChoice, now: Instant): Period = when (choice) {
        PeriodChoice.Day -> Period.containing(PeriodType.DAY, now, clock.zone)
        PeriodChoice.Week -> Period.containing(PeriodType.WEEK, now, clock.zone)
        PeriodChoice.Month -> Period.containing(PeriodType.MONTH, now, clock.zone)
        PeriodChoice.Year -> Period.containing(PeriodType.YEAR, now, clock.zone)
    }

    private fun build(
        choice: PeriodChoice,
        range: Period,
        all: List<Transaction>,
        categoryKey: Long?,
        barKey: Long?,
        cats: List<Category>,
        known: List<KnownBalance>,
        extras: Extras,
    ): OverviewUiState {
        val now = clock.instant()
        val zone = clock.zone
        val currency = extras.currency
        val todayMonth = AnalyticsCalculator.todayMonth(all, currency, now, zone)
        val summary = AnalyticsCalculator.summarize(all, range, now).currency(currency)
        val bars = if (choice == PeriodChoice.Year) {
            summary.daily.groupBy { YearMonth.from(it.date) }.map { (month, days) ->
                val first = month.atDay(1)
                ChartBar(first.toEpochDay(), first, days.sumOf { it.netExpenseMinor }.coerceAtLeast(0))
            }
        } else {
            summary.daily.map { ChartBar(it.date.toEpochDay(), it.date, it.netExpenseMinor.coerceAtLeast(0)) }
        }
        val breakdown = summary.categories.filter { it.netExpenseMinor > 0 }.map {
            CategoryShare(it.categoryId, it.netExpenseMinor, it.sharePercent?.roundToInt() ?: 0)
        }
        val selectedCategory = categoryKey?.let(::selectionOf)
        val bar = bars.firstOrNull { it.key == barKey }
        val inPeriod = all.filter { it.occurredAt in range }
        val list = inPeriod
            .asSequence()
            .filter { selectedCategory?.matches(it) ?: true }
            .filter { tx ->
                if (bar == null) return@filter true
                val date = tx.occurredAt.atZone(zone).toLocalDate()
                if (choice == PeriodChoice.Year) YearMonth.from(date) == YearMonth.from(bar.date) else date == bar.date
            }
            // Overview is about spending: transfers between own cards stay in History.
            .filter { it.direction != Direction.INTERNAL_TRANSFER || selectedCategory != null }
            .sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenByDescending { it.id })
            .toList()
        return OverviewUiState(
            loading = false,
            hasAnyTransactions = extras.hasAny,
            currency = currency,
            today = now.atZone(zone).toLocalDate(),
            todayMinor = todayMonth.todayNetExpenseMinor,
            monthMinor = todayMonth.monthNetExpenseMinor,
            balances = known.sortedByDescending { it.asOf },
            reviewCount = extras.reviewCount,
            period = choice,
            periodStart = range.startDate,
            periodExpenseMinor = summary.netExpenseMinor,
            bars = bars,
            breakdown = breakdown,
            selectedCategory = selectedCategory,
            selectedBar = bar?.key,
            transactions = list,
            categories = cats.associateBy { it.id },
            subscriptions = subscriptionsSummary(extras.subscriptions, currency),
        )
    }

    private fun subscriptionsSummary(all: List<Subscription>, currency: String): SubscriptionsSummary {
        val confirmed = all.filter { it.status == SubscriptionStatus.CONFIRMED }
        return SubscriptionsSummary(
            monthlyMinor = SubscriptionCosts.monthlyTotals(confirmed)[currency] ?: 0,
            confirmed = confirmed.size,
            candidates = all.count { it.status == SubscriptionStatus.CANDIDATE },
        )
    }

    /** Inputs of [build] besides the period data and the selection. */
    private data class Extras(
        val reviewCount: Int,
        val hasAny: Boolean,
        val currency: String,
        val subscriptions: List<Subscription>,
    )

    private fun CategorySelection.toKey(): Long = when (this) {
        is CategorySelection.Of -> categoryId
        CategorySelection.Uncategorized -> UNCATEGORIZED
    }

    private fun selectionOf(key: Long): CategorySelection =
        if (key == UNCATEGORIZED) CategorySelection.Uncategorized else CategorySelection.Of(key)

    companion object {
        const val KEY_PERIOD = "overview.period"
        const val KEY_CATEGORY = "overview.category"
        const val KEY_BAR = "overview.bar"
        private const val UNCATEGORIZED = -1L
    }
}
