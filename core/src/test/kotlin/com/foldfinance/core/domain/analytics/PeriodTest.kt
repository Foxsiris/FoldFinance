package com.foldfinance.core.domain.analytics

import com.foldfinance.core.domain.TestTransactions.at
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class PeriodTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun `day in Moscow starts at local midnight`() {
        val day = Period.day(LocalDate.of(2026, 11, 5), moscow)
        assertThat(day.start).isEqualTo(Instant.parse("2026-11-04T21:00:00Z"))
        assertThat(day.endExclusive).isEqualTo(Instant.parse("2026-11-05T21:00:00Z"))
        assertThat(day.days()).isEqualTo(1)
    }

    @Test
    fun `contains includes start and excludes end`() {
        val day = Period.day(LocalDate.of(2026, 11, 5), moscow)
        assertThat(day.start in day).isTrue()
        assertThat(day.endExclusive.minusMillis(1) in day).isTrue()
        assertThat(day.endExclusive in day).isFalse()
        assertThat(day.start.minusMillis(1) in day).isFalse()
    }

    @Test
    fun `DST days in Berlin last 23 and 25 hours but count as one day`() {
        val spring = Period.day(LocalDate.of(2026, 3, 29), berlin)
        val autumn = Period.day(LocalDate.of(2026, 10, 25), berlin)
        assertThat(Duration.between(spring.start, spring.endExclusive)).isEqualTo(Duration.ofHours(23))
        assertThat(Duration.between(autumn.start, autumn.endExclusive)).isEqualTo(Duration.ofHours(25))
        assertThat(spring.days()).isEqualTo(1)
        assertThat(autumn.days()).isEqualTo(1)
        // 02:30 бывает дважды 25 октября — оба момента внутри суток.
        assertThat(Instant.parse("2026-10-25T00:30:00Z") in autumn).isTrue()
        assertThat(Instant.parse("2026-10-25T01:30:00Z") in autumn).isTrue()
        assertThat(Instant.parse("2026-10-25T23:00:00Z") in autumn).isFalse()
    }

    @Test
    fun `month in Berlin across DST has calendar days`() {
        val march = Period.month(YearMonth.of(2026, 3), berlin)
        assertThat(march.days()).isEqualTo(31)
        assertThat(Duration.between(march.start, march.endExclusive)).isEqualTo(Duration.ofHours(31L * 24 - 1))
        assertThat(march.dates().last()).isEqualTo(LocalDate.of(2026, 3, 31))
    }

    @Test
    fun `week starts on Monday`() {
        val thursday = Period.week(LocalDate.of(2026, 11, 5), moscow)
        assertThat(thursday.startDate).isEqualTo(LocalDate.of(2026, 11, 2))
        assertThat(thursday.endDateExclusive).isEqualTo(LocalDate.of(2026, 11, 9))
        assertThat(Period.week(LocalDate.of(2026, 11, 8), moscow)).isEqualTo(thursday)
        assertThat(Period.week(LocalDate.of(2026, 11, 9), moscow).startDate).isEqualTo(LocalDate.of(2026, 11, 9))
        assertThat(thursday.days()).isEqualTo(7)
    }

    @Test
    fun `week across new year and previous week`() {
        val week = Period.week(LocalDate.of(2026, 12, 31), moscow)
        assertThat(week.startDate).isEqualTo(LocalDate.of(2026, 12, 28))
        assertThat(week.endDateInclusive).isEqualTo(LocalDate.of(2027, 1, 3))
        assertThat(week.previous().startDate).isEqualTo(LocalDate.of(2026, 12, 21))
        assertThat(week.next().startDate).isEqualTo(LocalDate.of(2027, 1, 4))
    }

    @Test
    fun `week with DST change in Berlin is 169 hours`() {
        val week = Period.week(LocalDate.of(2026, 10, 25), berlin)
        assertThat(week.startDate).isEqualTo(LocalDate.of(2026, 10, 19))
        assertThat(Duration.between(week.start, week.endExclusive)).isEqualTo(Duration.ofHours(7L * 24 + 1))
    }

    @Test
    fun `leap year months and years`() {
        assertThat(Period.month(YearMonth.of(2028, 2), moscow).days()).isEqualTo(29)
        assertThat(Period.month(YearMonth.of(2026, 2), moscow).days()).isEqualTo(28)
        assertThat(Period.year(2028, moscow).days()).isEqualTo(366)
        assertThat(Period.year(2026, moscow).days()).isEqualTo(365)
        assertThat(Period.month(YearMonth.of(2028, 3), moscow).previous())
            .isEqualTo(Period.month(YearMonth.of(2028, 2), moscow))
    }

    @Test
    fun `previous month and year`() {
        assertThat(Period.month(YearMonth.of(2026, 1), moscow).previous())
            .isEqualTo(Period.month(YearMonth.of(2025, 12), moscow))
        assertThat(Period.year(2026, moscow).previous()).isEqualTo(Period.year(2025, moscow))
        assertThat(Period.day(LocalDate.of(2026, 3, 1), moscow).previous().startDate)
            .isEqualTo(LocalDate.of(2026, 2, 28))
    }

    @Test
    fun `custom period and its previous have the same length`() {
        val custom = Period.custom(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), moscow)
        assertThat(custom.days()).isEqualTo(10)
        val prev = custom.previous()
        assertThat(prev.startDate).isEqualTo(LocalDate.of(2026, 10, 22))
        assertThat(prev.endDateInclusive).isEqualTo(LocalDate.of(2026, 10, 31))
        assertThat(prev.type).isEqualTo(PeriodType.CUSTOM)
    }

    @Test
    fun `custom period must not be empty`() {
        assertThrows(IllegalArgumentException::class.java) {
            Period.custom(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1), moscow)
        }
    }

    @Test
    fun `containing depends on zone`() {
        val instant = Instant.parse("2026-10-31T22:30:00Z") // 1 ноября 01:30 по Москве
        assertThat(Period.containing(PeriodType.MONTH, instant, moscow))
            .isEqualTo(Period.month(YearMonth.of(2026, 11), moscow))
        assertThat(Period.containing(PeriodType.MONTH, instant, ZoneOffset.UTC))
            .isEqualTo(Period.month(YearMonth.of(2026, 10), ZoneOffset.UTC))
        assertThat(Period.containing(PeriodType.YEAR, Instant.parse("2026-12-31T21:30:00Z"), moscow).startDate.year)
            .isEqualTo(2027)
    }

    @Test
    fun `elapsed days for current, past and future periods`() {
        val november = Period.month(YearMonth.of(2026, 11), moscow)
        assertThat(november.elapsedDays(at("2026-11-05T10:00", moscow))).isEqualTo(5)
        assertThat(november.elapsedDays(at("2026-11-01T00:00", moscow))).isEqualTo(1)
        assertThat(november.elapsedDays(at("2026-11-30T23:59", moscow))).isEqualTo(30)
        assertThat(november.elapsedDays(at("2026-12-03T10:00", moscow))).isEqualTo(30)
        assertThat(november.elapsedDays(at("2026-10-20T10:00", moscow))).isEqualTo(0)
        assertThat(november.elapsedDays(null)).isEqualTo(30)
    }
}
