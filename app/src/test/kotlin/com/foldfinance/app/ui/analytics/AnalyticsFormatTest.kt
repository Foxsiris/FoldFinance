package com.foldfinance.app.ui.analytics

import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.model.SubscriptionPeriod
import com.foldfinance.app.ui.subscriptions.rollForward
import com.foldfinance.app.ui.subscriptions.step
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class AnalyticsFormatTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 11, 5)

    private fun title(period: Period) = AnalyticsFormat.periodTitle(period, today, "Сегодня", "Вчера")

    @Test fun `period titles`() {
        assertThat(title(Period.day(today, zone))).isEqualTo("Сегодня")
        assertThat(title(Period.day(today.minusDays(1), zone))).isEqualTo("Вчера")
        assertThat(title(Period.day(LocalDate.of(2026, 10, 17), zone))).isEqualTo("17 октября")
        assertThat(title(Period.week(today, zone))).isEqualTo("2 – 8 ноября")
        assertThat(title(Period.week(LocalDate.of(2026, 10, 28), zone))).isEqualTo("26 октября – 1 ноября")
        assertThat(title(Period.month(YearMonth.of(2026, 10), zone))).isEqualTo("Октябрь")
        assertThat(title(Period.month(YearMonth.of(2025, 12), zone))).isEqualTo("Декабрь 2025")
        assertThat(title(Period.year(2026, zone))).isEqualTo("2026")
        assertThat(title(Period.custom(LocalDate.of(2025, 12, 20), LocalDate.of(2026, 1, 10), zone)))
            .isEqualTo("20 декабря 2025 – 10 января 2026")
    }

    @Test fun `compact axis amounts`() {
        assertThat(AnalyticsFormat.compactAmount(0, "RUB")).isEqualTo("0")
        assertThat(AnalyticsFormat.compactAmount(80_000, "RUB")).isEqualTo("800")
        assertThat(AnalyticsFormat.compactAmount(150_000, "RUB")).isEqualTo("1,5 тыс")
        assertThat(AnalyticsFormat.compactAmount(1_200_000, "RUB")).isEqualTo("12 тыс")
        assertThat(AnalyticsFormat.compactAmount(123_000_000, "RUB")).isEqualTo("1,2 млн")
        assertThat(AnalyticsFormat.compactAmount(-500_000, "RUB")).isEqualTo("−5 тыс")
    }

    @Test fun `chart axes use round steps`() {
        assertThat(niceScale(0.0, 14_600.0)).isEqualTo(AxisScale(0.0, 15_000.0, 5_000.0))
        assertThat(niceScale(0.0, 9_000.0)).isEqualTo(AxisScale(0.0, 10_000.0, 2_500.0))
        assertThat(niceScale(0.0, 0.0)).isEqualTo(AxisScale(0.0, 0.25, 0.25))
        assertThat(niceScale(-1_200.0, 3_000.0).let { it.min to it.step }).isEqualTo(-2_000.0 to 2_000.0)
    }

    @Test fun `percent`() {
        assertThat(AnalyticsFormat.percent(12.345)).isEqualTo("12")
        assertThat(AnalyticsFormat.percent(4.55)).isEqualTo("4,6")
        assertThat(AnalyticsFormat.percent(3.0)).isEqualTo("3")
        assertThat(AnalyticsFormat.percent(-1.1)).isEqualTo("1,1")
    }

    @Test fun `monthly charges keep their day and roll past a missed charge`() {
        assertThat(step(LocalDate.of(2026, 1, 31), SubscriptionPeriod.MONTHLY, 31)).isEqualTo(LocalDate.of(2026, 2, 28))
        assertThat(step(LocalDate.of(2026, 2, 28), SubscriptionPeriod.MONTHLY, 31)).isEqualTo(LocalDate.of(2026, 3, 31))
        assertThat(rollForward(LocalDate.of(2026, 9, 3), SubscriptionPeriod.MONTHLY, today)).isEqualTo(LocalDate.of(2026, 12, 3))
        assertThat(rollForward(LocalDate.of(2026, 11, 5), SubscriptionPeriod.WEEKLY, today)).isEqualTo(today)
    }
}
