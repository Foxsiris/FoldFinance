package com.foldfinance.app.ui.subscriptions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldfinance.app.ui.analytics.InsightsClock
import com.foldfinance.core.data.repository.CategoryRepository
import com.foldfinance.core.data.repository.SubscriptionRepository
import com.foldfinance.core.data.subscriptions.RefreshSubscriptionsUseCase
import com.foldfinance.core.model.SubscriptionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * «Подписки». On start runs [RefreshSubscriptionsUseCase] (detector over the last ~400 days of
 * operations), then shows candidates, confirmed subscriptions and the charge calendar. Calendar
 * month, tapped day and the collapsed/expanded calendar (outer screen) live in [SavedStateHandle].
 */
@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val subscriptions: SubscriptionRepository,
    private val refresh: RefreshSubscriptionsUseCase,
    categories: CategoryRepository,
    @InsightsClock private val clock: Clock,
) : ViewModel() {

    private val calendar = combine(
        savedStateHandle.getStateFlow<String?>(KEY_MONTH, null),
        savedStateHandle.getStateFlow<String?>(KEY_DAY, null),
        savedStateHandle.getStateFlow(KEY_EXPANDED, false),
    ) { month, day, expanded -> Triple(parseMonth(month), parseDate(day), expanded) }

    val state: StateFlow<SubscriptionsUiState> =
        combine(subscriptions.observeAll(), categories.observeAll(), calendar) { subs, cats, (month, day, expanded) ->
            buildSubscriptionsState(
                subscriptions = subs,
                categories = cats,
                today = today(),
                zone = clock.zone,
                month = month ?: YearMonth.from(today()),
                selectedDate = day,
                calendarExpanded = expanded,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SubscriptionsUiState(isLoading = true, today = today()))

    init {
        viewModelScope.launch {
            try {
                refresh(clock.zone)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Detection is best effort: saved subscriptions are still shown, the next start retries.
            }
        }
    }

    /** «Подтвердить»: the candidate becomes a subscription with a place in the calendar. */
    fun confirm(id: Long) {
        viewModelScope.launch { subscriptions.setStatus(id, SubscriptionStatus.CONFIRMED) }
    }

    /** «Не подписка»: hidden for good; the refresh never proposes it again. */
    fun dismiss(id: Long) {
        viewModelScope.launch { subscriptions.setStatus(id, SubscriptionStatus.DISMISSED) }
    }

    fun previousMonth() = shiftMonth(-1)

    fun nextMonth() = shiftMonth(1)

    /** Tap on a calendar day; tapping it again shows the upcoming charges again. */
    fun selectDate(date: LocalDate) {
        val current = parseDate(savedStateHandle[KEY_DAY])
        savedStateHandle[KEY_DAY] = if (current == date) null else date.toString()
    }

    fun toggleCalendar() {
        savedStateHandle[KEY_EXPANDED] = !(savedStateHandle.get<Boolean>(KEY_EXPANDED) ?: false)
    }

    private fun shiftMonth(delta: Long) {
        val current = YearMonth.from(today())
        val shown = parseMonth(savedStateHandle[KEY_MONTH]) ?: current
        val target = shown.plusMonths(delta)
        if (target < current || target > current.plusMonths(CALENDAR_MONTHS_AHEAD)) return
        savedStateHandle[KEY_MONTH] = target.toString()
        savedStateHandle[KEY_DAY] = null
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    private fun parseMonth(value: String?): YearMonth? = value?.let {
        try {
            YearMonth.parse(it)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun parseDate(value: String?): LocalDate? = value?.let {
        try {
            LocalDate.parse(it)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    companion object {
        const val KEY_MONTH = "subscriptions.month"
        const val KEY_DAY = "subscriptions.selectedDay"
        const val KEY_EXPANDED = "subscriptions.calendarExpanded"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
