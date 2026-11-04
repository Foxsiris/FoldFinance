package com.foldfinance.app.ui.subscriptions

import androidx.compose.runtime.Immutable
import com.foldfinance.core.domain.subscriptions.SubscriptionCosts
import com.foldfinance.core.domain.subscriptions.SubscriptionDetector
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.Subscription
import com.foldfinance.core.model.SubscriptionPeriod
import com.foldfinance.core.model.SubscriptionStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * A subscription as the screen shows it.
 *
 * @property nextCharge next expected charge, rolled forward past today when a charge was missed
 *   (the bank SMS may simply not have arrived).
 * @property monthlyMinor cost per month in the subscription currency (week × 52 / 12, year / 12).
 */
@Immutable
data class SubscriptionItem(
    val subscription: Subscription,
    val nextCharge: LocalDate,
    val lastCharge: LocalDate,
    val anchorDay: Int,
    val monthlyMinor: Long,
    val category: Category?,
) {
    val id: Long get() = subscription.id
}

/** A charge on the calendar: projected (future) or already made ([isPast]). */
@Immutable
data class CalendarCharge(val item: SubscriptionItem, val date: LocalDate, val isPast: Boolean)

/** Monthly cost of confirmed subscriptions in one currency. */
@Immutable
data class MonthlyTotal(val currency: String, val amountMinor: Long)

/**
 * Screen state of «Подписки».
 *
 * @property candidates detector findings waiting for «Подтвердить / Не подписка».
 * @property confirmed subscriptions the user confirmed, by the next charge.
 * @property month month shown by the calendar.
 * @property charges charges of [month] by date (confirmed subscriptions only).
 * @property selectedDate tapped calendar day; `null` shows the upcoming charges instead.
 * @property upcoming charges of the next 30 days, by date.
 */
@Immutable
data class SubscriptionsUiState(
    val isLoading: Boolean,
    val today: LocalDate,
    val candidates: List<SubscriptionItem> = emptyList(),
    val confirmed: List<SubscriptionItem> = emptyList(),
    val totals: List<MonthlyTotal> = emptyList(),
    val month: YearMonth = YearMonth.from(today),
    val charges: Map<LocalDate, List<CalendarCharge>> = emptyMap(),
    val selectedDate: LocalDate? = null,
    val upcoming: List<CalendarCharge> = emptyList(),
    val calendarExpanded: Boolean = false,
    val canGoPreviousMonth: Boolean = false,
    val canGoNextMonth: Boolean = true,
) {
    val isEmpty: Boolean get() = candidates.isEmpty() && confirmed.isEmpty()

    /** Charges of [selectedDate]. */
    val selectedCharges: List<CalendarCharge> get() = selectedDate?.let { charges[it] }.orEmpty()
}

/** How far ahead the calendar can be paged, in months. */
const val CALENDAR_MONTHS_AHEAD: Long = 12

/** Window of «Ближайшие списания», days. */
const val UPCOMING_DAYS: Long = 30

/**
 * Pure state builder.
 *
 * @param month calendar month; clamped to `[current month, +12]`.
 */
fun buildSubscriptionsState(
    subscriptions: List<Subscription>,
    categories: List<Category>,
    today: LocalDate,
    zone: ZoneId,
    month: YearMonth,
    selectedDate: LocalDate?,
    calendarExpanded: Boolean,
): SubscriptionsUiState {
    val byId = categories.associateBy { it.id }
    fun item(s: Subscription): SubscriptionItem {
        val last = s.lastChargeAt.atZone(zone).toLocalDate()
        val anchor = s.nextChargeAt.atZone(zone).toLocalDate()
        return SubscriptionItem(
            subscription = s,
            nextCharge = rollForward(anchor, s.period, today),
            lastCharge = last,
            anchorDay = anchor.dayOfMonth,
            monthlyMinor = SubscriptionCosts.monthlyEquivalent(s),
            category = s.categoryId?.let(byId::get),
        )
    }

    val candidates = subscriptions.filter { it.status == SubscriptionStatus.CANDIDATE }.map(::item)
        .sortedWith(compareBy<SubscriptionItem> { it.nextCharge }.thenBy { it.subscription.merchant })
    val confirmed = subscriptions.filter { it.status == SubscriptionStatus.CONFIRMED }.map(::item)
        .sortedWith(compareBy<SubscriptionItem> { it.nextCharge }.thenBy { it.subscription.merchant })
    val totals = SubscriptionCosts.monthlyTotals(confirmed.map { it.subscription })
        .entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.key == "RUB" }.thenBy { it.key })
        .map { MonthlyTotal(it.key, it.value) }

    val current = YearMonth.from(today)
    val shown = month.coerceIn(current, current.plusMonths(CALENDAR_MONTHS_AHEAD))
    val charges = confirmed.flatMap { chargesIn(it, shown.atDay(1), shown.atEndOfMonth(), today) }
        .groupBy { it.date }
    val upcoming = confirmed.flatMap { chargesIn(it, today, today.plusDays(UPCOMING_DAYS - 1), today) }
        .filterNot { it.isPast }
        .sortedWith(compareBy<CalendarCharge> { it.date }.thenBy { it.item.subscription.merchant })

    return SubscriptionsUiState(
        isLoading = false,
        today = today,
        candidates = candidates,
        confirmed = confirmed,
        totals = totals,
        month = shown,
        charges = charges,
        selectedDate = selectedDate?.takeIf { YearMonth.from(it) == shown },
        upcoming = upcoming,
        calendarExpanded = calendarExpanded,
        canGoPreviousMonth = shown > current,
        canGoNextMonth = shown < current.plusMonths(CALENDAR_MONTHS_AHEAD),
    )
}

/** Charges of [item] within `[from, to]`: the last real charge (past) and projected future ones. */
internal fun chargesIn(item: SubscriptionItem, from: LocalDate, to: LocalDate, today: LocalDate): List<CalendarCharge> {
    val result = mutableListOf<CalendarCharge>()
    if (item.lastCharge in from..to && item.lastCharge <= today) {
        result += CalendarCharge(item, item.lastCharge, isPast = true)
    }
    var date = item.nextCharge
    var guard = 0
    while (date <= to && guard++ < MAX_PROJECTED) {
        if (date >= from && date != item.lastCharge) result += CalendarCharge(item, date, isPast = false)
        date = step(date, item.subscription.period, item.anchorDay)
    }
    return result
}

/** First charge date on or after [today], stepping from [anchor] by [period]. */
internal fun rollForward(anchor: LocalDate, period: SubscriptionPeriod, today: LocalDate): LocalDate {
    var date = anchor
    val anchorDay = anchor.dayOfMonth
    var guard = 0
    while (date.isBefore(today) && guard++ < MAX_PROJECTED) date = step(date, period, anchorDay)
    return date
}

/**
 * Next charge after [date]. Monthly and yearly charges keep their day of month, moved to the last
 * day of a shorter month (31 Jan → 28/29 Feb → 31 Mar), like [SubscriptionDetector.nextCharge].
 */
internal fun step(date: LocalDate, period: SubscriptionPeriod, anchorDay: Int): LocalDate = when (period) {
    SubscriptionPeriod.WEEKLY -> date.plusWeeks(1)
    SubscriptionPeriod.MONTHLY -> YearMonth.from(date).plusMonths(1).let { it.atDay(anchorDay.coerceIn(1, it.lengthOfMonth())) }
    SubscriptionPeriod.YEARLY -> YearMonth.from(date).plusYears(1).let { it.atDay(anchorDay.coerceIn(1, it.lengthOfMonth())) }
}

private const val MAX_PROJECTED = 600

private fun YearMonth.coerceIn(min: YearMonth, max: YearMonth): YearMonth = when {
    this < min -> min
    this > max -> max
    else -> this
}
