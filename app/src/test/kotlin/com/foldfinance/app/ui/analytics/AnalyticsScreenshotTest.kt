package com.foldfinance.app.ui.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foldfinance.app.DeviceConfigs
import com.foldfinance.app.ScreenshotClock
import com.foldfinance.app.captureInAllConfigs
import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.domain.analytics.PeriodType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * Analytics in 3 Fold configurations × light/dark, on deterministic test data
 * ([InsightsSamples]). Record: `recordRoborazziDebug`, check: `verifyRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AnalyticsScreenshotTest {

    private val zone = InsightsSamples.zone
    private val today = LocalDate.now(ScreenshotClock)

    private fun state(period: Period, selectedDay: LocalDate? = null) = buildAnalyticsState(
        period = period,
        transactions = InsightsSamples.history,
        categories = InsightsSamples.categories,
        selectedDay = selectedDay,
        preferredCurrency = null,
        now = ScreenshotClock.instant(),
        today = today,
    )

    /** October: a full month with a tapped day — the day's operations sit next to the chart. */
    @Test fun month() = captureInAllConfigs("analytics_month") {
        AnalyticsScreen(
            state = state(Period.month(YearMonth.of(2026, 10), zone), selectedDay = LocalDate.of(2026, 10, 17)),
            actions = AnalyticsActions(),
        )
    }

    /** A week without a selection. */
    @Test fun week() = captureInAllConfigs("analytics_week", configs = listOf(DeviceConfigs.Outer, DeviceConfigs.Inner)) {
        AnalyticsScreen(state = state(Period.week(LocalDate.of(2026, 10, 12), zone)), actions = AnalyticsActions())
    }

    /** A year: one column per month. */
    @Test fun year() = captureInAllConfigs("analytics_year", configs = listOf(DeviceConfigs.Inner)) {
        AnalyticsScreen(state = state(Period.year(2026, zone)), actions = AnalyticsActions())
    }

    /** No operations in the period: an invitation, no zeros. */
    @Test fun empty() = captureInAllConfigs("analytics_empty") {
        AnalyticsScreen(
            state = state(Period.containing(PeriodType.MONTH, java.time.Instant.parse("2026-06-15T10:00:00Z"), zone)),
            actions = AnalyticsActions(),
        )
    }
}
