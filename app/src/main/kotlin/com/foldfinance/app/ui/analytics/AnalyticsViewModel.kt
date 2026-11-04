package com.foldfinance.app.ui.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldfinance.core.data.repository.CategoryRepository
import com.foldfinance.core.data.repository.TransactionFilter
import com.foldfinance.core.data.repository.TransactionRepository
import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.domain.analytics.PeriodType
import com.foldfinance.core.model.Transaction
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
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * «Аналитика»: period, tapped column and currency live in [SavedStateHandle], so they survive
 * folding/unfolding, rotation, «Не сохранять действия» and process death. Statistics come from
 * the database through [TransactionRepository] and are recomputed on every change of the table.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    transactions: TransactionRepository,
    categories: CategoryRepository,
    @InsightsClock private val clock: Clock,
) : ViewModel() {

    private data class Selection(val period: Period, val day: LocalDate?, val currency: String?)

    private data class Loaded(val period: Period, val transactions: List<Transaction>)

    // The period is one value («MONTH|2026-11-01|»), written atomically: no half-updated periods.
    private val selection = combine(
        savedStateHandle.getStateFlow<String?>(KEY_PERIOD, null),
        savedStateHandle.getStateFlow<String?>(KEY_DAY, null),
        savedStateHandle.getStateFlow<String?>(KEY_CURRENCY, null),
    ) { period, day, currency ->
        Selection(decodePeriod(period), parseDate(day), currency)
    }

    private val loaded = selection
        .map { it.period }
        .distinctUntilChanged()
        .flatMapLatest { period ->
            transactions.observe(TransactionFilter(from = period.previous().start, to = period.endExclusive))
                .map { Loaded(period, it) }
        }

    /** Screen state; `isLoading` until the first rows of the current period arrive. */
    val state: StateFlow<AnalyticsUiState> = combine(loaded, selection, categories.observeAll()) { data, sel, cats ->
        if (data.period != sel.period) {
            loadingState(sel.period)
        } else {
            buildAnalyticsState(
                period = sel.period,
                transactions = data.transactions,
                categories = cats,
                selectedDay = sel.day,
                preferredCurrency = sel.currency,
                now = clock.instant(),
                today = today(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), loadingState(currentPeriod()))

    /** Day / week / month / year containing the current reference date (see [referenceDate]). */
    fun selectType(type: PeriodType) {
        require(type != PeriodType.CUSTOM) { "use selectCustom for CUSTOM" }
        val current = currentPeriod()
        if (current.type == type) return
        val reference = referenceDate(current)
        val period = Period.containing(type, reference.atStartOfDay(clock.zone).toInstant(), clock.zone)
        val day = parseDate(savedStateHandle[KEY_DAY])
        writePeriod(period, keepDay = day != null && day >= period.startDate && day < period.endDateExclusive && type != PeriodType.DAY)
    }

    /** Custom range, both ends inclusive; swapped if given in reverse. */
    fun selectCustom(start: LocalDate, endInclusive: LocalDate) {
        val (from, to) = if (endInclusive.isBefore(start)) endInclusive to start else start to endInclusive
        writePeriod(Period.custom(from, to, clock.zone), keepDay = false)
    }

    fun previous() = writePeriod(currentPeriod().previous(), keepDay = false)

    fun next() {
        val current = currentPeriod()
        if (canGoNext(current, today())) writePeriod(current.next(), keepDay = false)
    }

    /**
     * Tap on a chart column: shows the operations of that day (or month) next to the chart.
     * Tapping the selected column again clears the selection.
     */
    fun selectBucket(start: LocalDate) {
        val current = parseDate(savedStateHandle[KEY_DAY])
        savedStateHandle[KEY_DAY] = if (current == start) null else start.toString()
    }

    fun clearSelection() {
        savedStateHandle[KEY_DAY] = null
    }

    fun selectCurrency(currency: String) {
        savedStateHandle[KEY_CURRENCY] = currency
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    /** Selected day, else today if it is inside the period, else the period start. */
    private fun referenceDate(period: Period): LocalDate {
        parseDate(savedStateHandle[KEY_DAY])?.let { return it }
        val today = today()
        return if (today >= period.startDate && today < period.endDateExclusive) today else period.startDate
    }

    private fun currentPeriod(): Period = decodePeriod(savedStateHandle[KEY_PERIOD])

    private fun writePeriod(period: Period, keepDay: Boolean) {
        if (!keepDay) savedStateHandle[KEY_DAY] = null
        savedStateHandle[KEY_PERIOD] = encodePeriod(period)
    }

    private fun decodePeriod(value: String?): Period {
        val parts = value?.split('|').orEmpty()
        val type = parts.getOrNull(0)
        val start = parts.getOrNull(1)
        val end = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
        val zone = clock.zone
        val periodType = PeriodType.entries.firstOrNull { it.name == type } ?: PeriodType.MONTH
        val startDate = parseDate(start) ?: today()
        return when (periodType) {
            PeriodType.DAY -> Period.day(startDate, zone)
            PeriodType.WEEK -> Period.week(startDate, zone)
            PeriodType.MONTH -> Period.month(startDate, zone)
            PeriodType.YEAR -> Period.year(startDate.year, zone)
            PeriodType.CUSTOM -> {
                val endDate = parseDate(end)?.takeIf { !it.isBefore(startDate) } ?: startDate
                Period.custom(startDate, endDate, zone)
            }
        }
    }

    private fun loadingState(period: Period) =
        AnalyticsUiState(isLoading = true, period = period, today = today(), canGoNext = canGoNext(period, today()))

    private fun parseDate(value: String?): LocalDate? = value?.let {
        try {
            LocalDate.parse(it)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    companion object {
        const val KEY_PERIOD = "analytics.period"
        const val KEY_DAY = "analytics.selectedDay"
        const val KEY_CURRENCY = "analytics.currency"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** «TYPE|start|endInclusive» (end only for CUSTOM) — the saved form of a period. */
internal fun encodePeriod(period: Period): String =
    period.type.name + "|" + period.startDate + "|" + if (period.type == PeriodType.CUSTOM) period.endDateInclusive.toString() else ""
