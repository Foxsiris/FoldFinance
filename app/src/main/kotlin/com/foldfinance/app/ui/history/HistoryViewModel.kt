package com.foldfinance.app.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldfinance.app.ai.features.query.ChatScope
import com.foldfinance.app.prefs.AppPreferences
import com.foldfinance.app.prefs.AppSettings
import com.foldfinance.app.ui.transaction.CategoryEditor
import com.foldfinance.app.ui.transaction.DeferredDeletion
import com.foldfinance.app.ui.transaction.RulePrompt
import com.foldfinance.app.ui.transaction.TransactionDetailActions
import com.foldfinance.app.ui.transaction.TransactionDetailState
import com.foldfinance.app.ui.transaction.UiClock
import com.foldfinance.app.ui.transaction.observeDetail
import com.foldfinance.core.data.repository.CategoryRepository
import com.foldfinance.core.data.repository.MerchantRuleRepository
import com.foldfinance.core.data.repository.TransactionFilter
import com.foldfinance.core.data.repository.TransactionRepository
import com.foldfinance.core.data.repository.TransactionSort
import com.foldfinance.core.domain.analytics.AnalyticsCalculator
import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.domain.analytics.PeriodType
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Period presets of the History filter bar. */
@Serializable
enum class HistoryPeriod { ALL, TODAY, WEEK, MONTH, YEAR }

/**
 * Filters of «История». Serialised into [SavedStateHandle] as JSON, so filters, search and sort
 * survive folding and activity recreation.
 *
 * @property directions `Direction` names; empty — every direction.
 * @property minAmountMinor / [maxAmountMinor] inclusive bounds, kopecks.
 */
@Serializable
data class HistoryFilter(
    val query: String = "",
    val categoryIds: Set<Long> = emptySet(),
    val uncategorized: Boolean = false,
    val directions: Set<String> = emptySet(),
    val period: HistoryPeriod = HistoryPeriod.ALL,
    val minAmountMinor: Long? = null,
    val maxAmountMinor: Long? = null,
    val sort: String = TransactionSort.DATE_DESC.name,
) {
    val sortValue: TransactionSort get() = TransactionSort.valueOf(sort)
    val directionValues: Set<Direction> get() = directions.map(Direction::valueOf).toSet()
    val hasCategoryFilter: Boolean get() = categoryIds.isNotEmpty() || uncategorized
    val hasAmountFilter: Boolean get() = minAmountMinor != null || maxAmountMinor != null

    /** Anything narrows the list (search, filters; sort does not count). */
    val isNarrowed: Boolean
        get() = query.isNotBlank() || hasCategoryFilter || directions.isNotEmpty() || period != HistoryPeriod.ALL || hasAmountFilter

    /** Calendar period of the preset at [now] in [zone]; `null` — all time. */
    fun periodAt(now: Instant, zone: ZoneId): Period? = when (period) {
        HistoryPeriod.ALL -> null
        HistoryPeriod.TODAY -> Period.containing(PeriodType.DAY, now, zone)
        HistoryPeriod.WEEK -> Period.containing(PeriodType.WEEK, now, zone)
        HistoryPeriod.MONTH -> Period.containing(PeriodType.MONTH, now, zone)
        HistoryPeriod.YEAR -> Period.containing(PeriodType.YEAR, now, zone)
    }

    /**
     * What the chat next to the list asks about: the period of the filter and its category when
     * exactly one is chosen. Questions that name their own period or category override it.
     */
    fun chatScope(now: Instant, zone: ZoneId): ChatScope =
        ChatScope(period = periodAt(now, zone), categoryId = categoryIds.singleOrNull()?.takeIf { !uncategorized })

    /** Repository filter for the moment [now] in [zone] (period presets are calendar periods). */
    fun toRepositoryFilter(now: Instant, zone: ZoneId): TransactionFilter {
        val period: Period? = periodAt(now, zone)
        return TransactionFilter(
            from = period?.start,
            to = period?.endExclusive,
            categoryIds = if (hasCategoryFilter) categoryIds else null,
            includeUncategorized = uncategorized,
            directions = directionValues.takeIf { it.isNotEmpty() },
            minAmountMinor = minAmountMinor,
            maxAmountMinor = maxAmountMinor,
            query = query.trim().takeIf { it.isNotEmpty() },
            sort = sortValue,
        )
    }
}

/**
 * A run of operations under one header. [date] is `null` when the list is sorted by amount
 * (no day grouping then).
 *
 * @property netExpenseMinor net expenses of the day in the primary-currency sense of
 *   [AnalyticsCalculator.netExpenseContribution] (per currency of the first item; mixed-currency
 *   days show no total).
 */
data class HistorySection(val date: LocalDate?, val items: List<Transaction>, val netExpenseMinor: Long?, val currency: String?)

data class HistoryUiState(
    val loading: Boolean = true,
    val filter: HistoryFilter = HistoryFilter(),
    val sections: List<HistorySection> = emptyList(),
    val categories: List<Category> = emptyList(),
    val count: Int = 0,
    /** Currency of [expenseMinor] / [incomeMinor] and of the amount filter: «Валюта» from the settings. */
    val currency: String = AppSettings.DEFAULT_CURRENCY,
    val expenseMinor: Long = 0,
    val incomeMinor: Long = 0,
) {
    val isEmpty: Boolean get() = !loading && count == 0
}

/**
 * «История»: filters (in [SavedStateHandle]), the grouped list, the detail of the selected
 * operation and edits from the detail (category with the rule question, confirm, deferred delete).
 *
 * The *selection* itself is not here: it is the content key of the list-detail navigator
 * (`rememberListDetailPaneScaffoldNavigator`, saveable), which is what makes the two fold
 * scenarios work. The ViewModel only loads the detail for a given id.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    rules: MerchantRuleRepository,
    preferences: AppPreferences,
    @UiClock private val clock: Clock,
) : ViewModel(), TransactionDetailActions, HistoryFilterActions {

    private val json = Json { ignoreUnknownKeys = true }
    private val categoryEditor = CategoryEditor(handle, viewModelScope, transactions, rules, KEY_RULE)
    private val deletion = DeferredDeletion(viewModelScope, transactions)

    val zone: ZoneId get() = clock.zone
    fun now(): Instant = clock.instant()

    val filter: StateFlow<HistoryFilter> = handle.getStateFlow(KEY_FILTER, "")
        .map(::decode)
        .stateIn(viewModelScope, SharingStarted.Eagerly, decode(handle[KEY_FILTER] ?: ""))

    /**
     * The right pane of the inner screen shows the chat instead of the detail («Деталь / Чат»).
     * In [SavedStateHandle]: survives folding and recreation.
     */
    val chatShown: StateFlow<Boolean> = handle.getStateFlow(KEY_CHAT, false)

    fun showChat(show: Boolean) {
        handle[KEY_CHAT] = show
    }

    /** Operations hidden while their deletion can still be undone. */
    val pendingDeletion: StateFlow<Set<Long>> = deletion.pending

    val rulePrompt: StateFlow<RulePrompt?> = categoryEditor.prompt

    val state: StateFlow<HistoryUiState> = combine(
        filter.map { it.toRepositoryFilter(clock.instant(), clock.zone) }
            .distinctUntilChanged()
            .debounce { if (it.query == null) 0L else SEARCH_DEBOUNCE_MS }
            .flatMapLatest { transactions.observe(it) },
        categoryRepository.observeAll(),
        filter,
        deletion.pending,
        preferences.defaultCurrency,
    ) { list, cats, f, hidden, currency ->
        val visible = if (hidden.isEmpty()) list else list.filterNot { it.id in hidden }
        HistoryUiState(
            loading = false,
            filter = f,
            sections = group(visible, f.sortValue),
            categories = cats,
            count = visible.size,
            currency = currency,
            expenseMinor = visible.filter { it.currency == currency }.sumOf(AnalyticsCalculator::netExpenseContribution),
            incomeMinor = visible.filter { it.currency == currency && it.direction == Direction.INCOME }.sumOf { it.amountMinor },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState(filter = filter.value))

    /** Detail of [id] for the detail pane; `null` while loading or after deletion. */
    fun detail(id: Long): Flow<TransactionDetailState?> = transactions.observeDetail(id, categoryRepository)

    fun updateFilter(change: (HistoryFilter) -> HistoryFilter) {
        handle[KEY_FILTER] = json.encodeToString(HistoryFilter.serializer(), change(filter.value))
    }

    override fun setQuery(query: String) = updateFilter { it.copy(query = query.take(100)) }
    override fun setPeriod(period: HistoryPeriod) = updateFilter { it.copy(period = period) }
    override fun setSort(sort: TransactionSort) = updateFilter { it.copy(sort = sort.name) }
    override fun toggleDirection(direction: Direction) = updateFilter {
        val name = direction.name
        it.copy(directions = if (name in it.directions) it.directions - name else it.directions + name)
    }
    override fun toggleCategoryFilter(categoryId: Long) = updateFilter {
        it.copy(categoryIds = if (categoryId in it.categoryIds) it.categoryIds - categoryId else it.categoryIds + categoryId)
    }
    override fun toggleUncategorized() = updateFilter { it.copy(uncategorized = !it.uncategorized) }
    override fun clearCategoryFilter() = updateFilter { it.copy(categoryIds = emptySet(), uncategorized = false) }
    override fun setAmountRange(min: Long?, max: Long?) = updateFilter {
        val lo = min?.takeIf { v -> v > 0 }
        val hi = max?.takeIf { v -> v > 0 }
        if (lo != null && hi != null && lo > hi) it.copy(minAmountMinor = hi, maxAmountMinor = lo) else it.copy(minAmountMinor = lo, maxAmountMinor = hi)
    }
    override fun resetFilters() = updateFilter { HistoryFilter(sort = it.sort) }

    override fun setCategory(transactionId: Long, categoryId: Long?) {
        val tx = currentTransaction(transactionId)
        val category = state.value.categories.firstOrNull { it.id == categoryId }
        categoryEditor.setCategory(listOf(transactionId), category, tx?.merchant)
    }

    /** Drag & drop of a row onto a category chip (inner screen). */
    fun dropOnCategory(transactionId: Long, categoryId: Long) = setCategory(transactionId, categoryId)

    fun applyRule() = categoryEditor.applyRule()
    fun dismissRule() = categoryEditor.dismissRule()

    override fun confirm(transactionId: Long) {
        viewModelScope.launch { transactions.confirmReview(transactionId) }
    }

    /** Editing is hosted by the screen (edit form); the ViewModel has nothing to do. */
    override fun edit(transactionId: Long) = Unit

    override fun delete(transactionId: Long) = deletion.request(transactionId)

    fun undoDelete(transactionId: Long) = deletion.undo(transactionId)

    private fun currentTransaction(id: Long): Transaction? =
        state.value.sections.asSequence().flatMap { it.items }.firstOrNull { it.id == id }

    private fun group(list: List<Transaction>, sort: TransactionSort): List<HistorySection> {
        if (list.isEmpty()) return emptyList()
        if (sort == TransactionSort.AMOUNT_ASC || sort == TransactionSort.AMOUNT_DESC) {
            return listOf(HistorySection(date = null, items = list, netExpenseMinor = null, currency = null))
        }
        val zone = clock.zone
        val result = ArrayList<HistorySection>()
        var date: LocalDate? = null
        var items = ArrayList<Transaction>()
        fun flush() {
            val d = date ?: return
            val currencies = items.map { it.currency }.distinct()
            val currency = currencies.singleOrNull()
            val total = if (currency != null) items.sumOf(AnalyticsCalculator::netExpenseContribution) else null
            result += HistorySection(d, items, total, currency)
        }
        for (tx in list) {
            val d = tx.occurredAt.atZone(zone).toLocalDate()
            if (d != date) {
                flush()
                date = d
                items = ArrayList()
            }
            items += tx
        }
        flush()
        return result
    }

    private fun decode(text: String): HistoryFilter =
        if (text.isEmpty()) HistoryFilter() else runCatching { json.decodeFromString(HistoryFilter.serializer(), text) }.getOrDefault(HistoryFilter())

    companion object {
        const val KEY_FILTER = "history.filter"
        const val KEY_RULE = "history.rulePrompt"
        const val KEY_CHAT = "history.chatShown"
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}
