package com.foldfinance.app.ai.features.query

import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.domain.analytics.PeriodType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Period arguments of the question functions. The model writes one of these strings; Kotlin
 * turns it into a [Period] — the model never does date arithmetic.
 *
 * - `today`, `yesterday`, `this_week`, `last_week`, `this_month`, `last_month`, `this_year`, `last_year`
 * - `month:2026-10` or `month:10` (the latest such month that has started)
 * - `year:2025`, `day:2026-10-17`
 * - `range:2026-10-01..2026-10-31` (at most two years)
 * - `last_days:30` (1…366, ending today)
 *
 * Periods that start in the future or before 2000 are rejected.
 */
object PeriodSpec {

    /** Strings shown to the model in the function description. */
    val examples: List<String> = listOf(
        "this_month", "last_month", "this_week", "last_week", "today", "yesterday", "this_year", "last_year",
        "month:YYYY-MM", "year:YYYY", "day:YYYY-MM-DD", "range:YYYY-MM-DD..YYYY-MM-DD", "last_days:N",
    )

    private const val MIN_YEAR = 2000
    private const val MAX_RANGE_DAYS = 732L

    fun parse(spec: String, today: LocalDate, zone: ZoneId): Period? {
        val s = spec.trim().lowercase(Locale.ROOT)
        val period = runCatching {
            when {
                s == "today" -> Period.day(today, zone)
                s == "yesterday" -> Period.day(today.minusDays(1), zone)
                s == "this_week" -> Period.week(today, zone)
                s == "last_week" -> Period.week(today.minusWeeks(1), zone)
                s == "this_month" -> Period.month(today, zone)
                s == "last_month" -> Period.month(YearMonth.from(today).minusMonths(1), zone)
                s == "this_year" -> Period.year(today.year, zone)
                s == "last_year" -> Period.year(today.year - 1, zone)
                s.startsWith("month:") -> month(s.removePrefix("month:"), today, zone)
                s.startsWith("year:") -> Period.year(s.removePrefix("year:").toInt(), zone)
                s.startsWith("day:") -> Period.day(LocalDate.parse(s.removePrefix("day:")), zone)
                s.startsWith("range:") -> range(s.removePrefix("range:"), zone)
                s.startsWith("last_days:") -> {
                    val n = s.removePrefix("last_days:").toLong()
                    if (n !in 1..366) null else Period.custom(today.minusDays(n - 1), today, zone)
                }
                else -> null
            }
        }.getOrNull() ?: return null
        if (period.startDate.isAfter(today) || period.startDate.year < MIN_YEAR) return null
        return period
    }

    private fun month(value: String, today: LocalDate, zone: ZoneId): Period? {
        val ym = if (value.contains('-')) {
            YearMonth.parse(value)
        } else {
            val m = value.toInt()
            if (m !in 1..12) return null
            val thisYear = YearMonth.of(today.year, m)
            if (thisYear.atDay(1).isAfter(today)) thisYear.minusYears(1) else thisYear
        }
        return Period.month(ym, zone)
    }

    private fun range(value: String, zone: ZoneId): Period? {
        val parts = value.split("..")
        if (parts.size != 2) return null
        val from = LocalDate.parse(parts[0].trim())
        val to = LocalDate.parse(parts[1].trim())
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) return null
        return Period.custom(from, to, zone)
    }

    /**
     * Previous period to compare [current] with. A period still in progress is compared by the
     * same number of days: 1–5 November with 1–5 October, not with the whole of October.
     * Both periods are then [PeriodType.CUSTOM] so the labels show the exact days.
     */
    fun comparisonPair(current: Period, today: LocalDate): Pair<Period, Period> {
        if (!today.isBefore(current.endDateInclusive) || today.isBefore(current.startDate)) {
            return current to current.previous()
        }
        val elapsed = ChronoUnit.DAYS.between(current.startDate, today) // days before today
        val cur = Period.custom(current.startDate, today, current.zone)
        val prevFull = current.previous()
        val prevEnd = minOf(prevFull.startDate.plusDays(elapsed), prevFull.endDateInclusive)
        return cur to Period.custom(prevFull.startDate, prevEnd, current.zone)
    }
}
