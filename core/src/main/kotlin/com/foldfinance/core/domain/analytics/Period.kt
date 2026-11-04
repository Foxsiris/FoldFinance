package com.foldfinance.core.domain.analytics

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Тип периода аналитики. */
enum class PeriodType { DAY, WEEK, MONTH, YEAR, CUSTOM }

/**
 * Календарный период в часовом поясе [zone]: полуинтервал дат
 * `[startDate, endDateExclusive)`.
 *
 * Границы как моменты времени — [start] и [endExclusive] — это полночь соответствующих дат
 * в [zone] (с учётом перехода на летнее время: сутки могут длиться 23 или 25 часов).
 * Неделя начинается с понедельника.
 *
 * Создавать через фабрики [day], [week], [month], [year], [custom], [containing].
 */
data class Period(
    val type: PeriodType,
    val startDate: LocalDate,
    val endDateExclusive: LocalDate,
    val zone: ZoneId,
) {
    init {
        require(startDate.isBefore(endDateExclusive)) { "period must contain at least one day" }
    }

    /** Начало периода: полночь [startDate] в [zone]. */
    val start: Instant get() = startDate.atStartOfDay(zone).toInstant()

    /** Конец периода (не включается): полночь [endDateExclusive] в [zone]. */
    val endExclusive: Instant get() = endDateExclusive.atStartOfDay(zone).toInstant()

    /** Последняя дата периода (включительно). */
    val endDateInclusive: LocalDate get() = endDateExclusive.minusDays(1)

    /** `true`, если момент попадает в `[start, endExclusive)`. */
    operator fun contains(instant: Instant): Boolean = !instant.isBefore(start) && instant.isBefore(endExclusive)

    /** Число календарных дней в периоде. */
    fun days(): Int = ChronoUnit.DAYS.between(startDate, endDateExclusive).toInt()

    /** Все даты периода по порядку. */
    fun dates(): List<LocalDate> = (0 until days()).map { startDate.plusDays(it.toLong()) }

    /** Дата момента [instant] в часовом поясе периода. */
    fun dateOf(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    /**
     * Сколько дней периода уже прошло на момент [now], включая текущий день.
     * `0` — период ещё не начался; [days] — период закончился (или [now] = `null`).
     */
    fun elapsedDays(now: Instant?): Int {
        if (now == null || !now.isBefore(endExclusive)) return days()
        if (now.isBefore(start)) return 0
        return ChronoUnit.DAYS.between(startDate, dateOf(now)).toInt() + 1
    }

    /** Предыдущий период того же типа. Для [PeriodType.CUSTOM] — та же длина сразу перед этим. */
    fun previous(): Period = shift(-1)

    /** Следующий период того же типа. Для [PeriodType.CUSTOM] — та же длина сразу после этого. */
    fun next(): Period = shift(1)

    private fun shift(steps: Long): Period = when (type) {
        PeriodType.DAY -> day(startDate.plusDays(steps), zone)
        PeriodType.WEEK -> week(startDate.plusWeeks(steps), zone)
        PeriodType.MONTH -> month(YearMonth.from(startDate).plusMonths(steps), zone)
        PeriodType.YEAR -> year(startDate.year + steps.toInt(), zone)
        PeriodType.CUSTOM -> {
            val len = days().toLong()
            copy(startDate = startDate.plusDays(len * steps), endDateExclusive = endDateExclusive.plusDays(len * steps))
        }
    }

    companion object {
        /** Сутки [date]. */
        fun day(date: LocalDate, zone: ZoneId): Period = Period(PeriodType.DAY, date, date.plusDays(1), zone)

        /** Неделя (понедельник — воскресенье), содержащая [date]. */
        fun week(date: LocalDate, zone: ZoneId): Period {
            val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return Period(PeriodType.WEEK, monday, monday.plusWeeks(1), zone)
        }

        /** Календарный месяц [month]. */
        fun month(month: YearMonth, zone: ZoneId): Period =
            Period(PeriodType.MONTH, month.atDay(1), month.plusMonths(1).atDay(1), zone)

        /** Календарный месяц, содержащий [date]. */
        fun month(date: LocalDate, zone: ZoneId): Period = month(YearMonth.from(date), zone)

        /** Календарный год. */
        fun year(year: Int, zone: ZoneId): Period =
            Period(PeriodType.YEAR, LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1), zone)

        /** Произвольный период с [startDate] по [endDateInclusive] включительно. */
        fun custom(startDate: LocalDate, endDateInclusive: LocalDate, zone: ZoneId): Period =
            Period(PeriodType.CUSTOM, startDate, endDateInclusive.plusDays(1), zone)

        /**
         * Период типа [type], содержащий момент [instant] в [zone].
         * Для [PeriodType.CUSTOM] не определён — используйте [custom].
         */
        fun containing(type: PeriodType, instant: Instant, zone: ZoneId): Period {
            val date = instant.atZone(zone).toLocalDate()
            return when (type) {
                PeriodType.DAY -> day(date, zone)
                PeriodType.WEEK -> week(date, zone)
                PeriodType.MONTH -> month(date, zone)
                PeriodType.YEAR -> year(date.year, zone)
                PeriodType.CUSTOM -> throw IllegalArgumentException("use Period.custom for CUSTOM")
            }
        }
    }
}
