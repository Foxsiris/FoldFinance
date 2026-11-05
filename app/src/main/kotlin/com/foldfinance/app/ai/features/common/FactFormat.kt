package com.foldfinance.app.ai.features.common

import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.app.ui.format.RuLocale
import com.foldfinance.app.ui.format.SignDisplay
import com.foldfinance.core.domain.analytics.AmountChange
import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.domain.analytics.PeriodType
import java.math.RoundingMode
import java.time.format.DateTimeFormatter

/**
 * Formatting of computed values for facts and answers. Everything here is Kotlin: the model only
 * ever sees (and repeats through placeholders) these strings.
 */
object FactFormat {
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", RuLocale)
    private val monthYear = DateTimeFormatter.ofPattern("LLLL yyyy", RuLocale)

    fun money(amountMinor: Long, currency: String): String = MoneyFormat.format(amountMinor, currency)

    /** `+1 200 ₽` / `−300 ₽`. */
    fun signedMoney(amountMinor: Long, currency: String): String =
        MoneyFormat.format(amountMinor, currency, SignDisplay.Always)

    /** Change in whole percent with sign (`+12%`), `null` when there was nothing before. */
    fun percent(change: AmountChange): String? {
        val p = change.percentExact ?: return null
        val whole = p.setScale(0, RoundingMode.HALF_UP).toLong()
        return when {
            whole > 0 -> "+$whole%"
            whole < 0 -> "${MoneyFormat.MINUS}${-whole}%"
            else -> "0%"
        }
    }

    /** «октябрь 2026», «1–5 ноября», «28 октября – 3 ноября», «5 ноября», «2026». */
    fun period(period: Period): String {
        val start = period.startDate
        val end = period.endDateInclusive
        return when {
            period.type == PeriodType.MONTH -> monthYear.format(start)
            period.type == PeriodType.YEAR -> start.year.toString()
            start == end -> dayMonth.format(start)
            start.month == end.month && start.year == end.year -> "${start.dayOfMonth}–${dayMonth.format(end)}"
            else -> "${dayMonth.format(start)} – ${dayMonth.format(end)}"
        }
    }
}
