package com.foldfinance.app.ui.analytics

import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.app.ui.format.RuLocale
import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.domain.analytics.PeriodType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.Instant

/** Date and number formatting of the analytics screen (ru-RU). Pure functions, unit-testable. */
object AnalyticsFormat {
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", RuLocale)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMMM yyyy", RuLocale)
    private val dayShortMonth = DateTimeFormatter.ofPattern("d MMM", RuLocale)
    private val time = DateTimeFormatter.ofPattern("HH:mm", RuLocale)

    /**
     * Title of a period: «Сегодня», «5 ноября», «2 – 8 ноября», «Ноябрь», «2026»,
     * «1 сентября – 15 октября». The year is added only when it is not the current one.
     *
     * @param todayLabel / [yesterdayLabel] localized «Сегодня» / «Вчера».
     */
    fun periodTitle(period: Period, today: LocalDate, todayLabel: String, yesterdayLabel: String): String {
        val start = period.startDate
        val end = period.endDateInclusive
        return when (period.type) {
            PeriodType.DAY -> when (start) {
                today -> todayLabel
                today.minusDays(1) -> yesterdayLabel
                else -> date(start, today)
            }
            PeriodType.MONTH -> monthName(start) + if (start.year != today.year) " ${start.year}" else ""
            PeriodType.YEAR -> start.year.toString()
            PeriodType.WEEK, PeriodType.CUSTOM -> range(start, end, today)
        }
    }

    /** «5 ноября» or «5 ноября 2025». */
    fun date(date: LocalDate, today: LocalDate): String =
        if (date.year == today.year) dayMonth.format(date) else dayMonthYear.format(date)

    /** «5 – 11 ноября», «28 октября – 3 ноября», with the year when not current. */
    fun range(start: LocalDate, end: LocalDate, today: LocalDate): String {
        if (start == end) return date(start, today)
        val yearSuffix = if (end.year != today.year || start.year != end.year) " ${end.year}" else ""
        val startPart = when {
            start.year != end.year -> dayMonthYear.format(start)
            start.month == end.month -> start.dayOfMonth.toString()
            else -> dayMonth.format(start)
        }
        return "$startPart – ${dayMonth.format(end)}$yearSuffix"
    }

    /** Month name in the nominative case, capitalised: «Ноябрь». */
    fun monthName(date: LocalDate): String =
        date.month.getDisplayName(TextStyle.FULL_STANDALONE, RuLocale).replaceFirstChar { it.titlecase(RuLocale) }

    /** Short month for the chart axis: «янв», «фев». */
    fun monthShort(date: LocalDate): String =
        date.month.getDisplayName(TextStyle.SHORT_STANDALONE, RuLocale).trimEnd('.')

    /** Title of a tapped column: «5 ноября, среда» for a day, «Ноябрь 2026» for a month. */
    fun bucketTitle(bucket: ChartBucket, granularity: ChartGranularity, today: LocalDate): String = when (granularity) {
        ChartGranularity.DAY -> date(bucket.start, today) + ", " +
            bucket.start.dayOfWeek.getDisplayName(TextStyle.FULL, RuLocale)
        ChartGranularity.MONTH -> monthName(bucket.start) + " " + bucket.start.year
    }

    /** Time of an operation in a list: «14:05», or «5 нояб., 14:05» when the list spans several days. */
    fun operationTime(at: Instant, zone: ZoneId, withDate: Boolean): String {
        val local = at.atZone(zone)
        return if (withDate) dayShortMonth.format(local) + ", " + time.format(local) else time.format(local)
    }

    /**
     * Compact amount for chart axes, from minor units: «0», «800», «1,5 тыс», «12 тыс», «1,2 млн».
     * Only for axis labels; every amount the user reads as a value goes through [MoneyFormat].
     */
    fun compactAmount(amountMinor: Long, currency: String): String {
        val digits = MoneyFormat.fractionDigits(currency)
        val major = BigDecimal.valueOf(amountMinor).movePointLeft(digits)
        val abs = major.abs()
        val sign = if (major.signum() < 0) MoneyFormat.MINUS.toString() else ""
        val text = when {
            abs >= MILLION -> scaled(abs, MILLION) + " млн"
            abs >= THOUSAND -> scaled(abs, THOUSAND) + " тыс"
            else -> abs.setScale(0, RoundingMode.HALF_UP).toPlainString()
        }
        return sign + text
    }

    /** Percent for display: «12%», «4,5%», «0,3%»; whole numbers from 10% up. */
    fun percent(value: Double): String {
        val abs = BigDecimal.valueOf(kotlin.math.abs(value))
        val scaled = if (abs >= BigDecimal.TEN) abs.setScale(0, RoundingMode.HALF_UP) else abs.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros()
        return scaled.toPlainString().replace('.', MoneyFormat.DECIMAL_SEPARATOR)
    }

    private fun scaled(value: BigDecimal, unit: BigDecimal): String {
        val v = value.divide(unit)
        val rounded = if (v >= BigDecimal.TEN) v.setScale(0, RoundingMode.HALF_UP) else v.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros()
        return rounded.toPlainString().replace('.', MoneyFormat.DECIMAL_SEPARATOR)
    }

    private val THOUSAND = BigDecimal(1_000)
    private val MILLION = BigDecimal(1_000_000)
}
