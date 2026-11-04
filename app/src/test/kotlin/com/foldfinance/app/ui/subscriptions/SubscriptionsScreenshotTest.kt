package com.foldfinance.app.ui.subscriptions

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foldfinance.app.ScreenshotClock
import com.foldfinance.app.captureInAllConfigs
import com.foldfinance.app.ui.analytics.InsightsSamples
import com.foldfinance.core.model.Subscription
import com.foldfinance.core.model.SubscriptionPeriod
import com.foldfinance.core.model.SubscriptionStatus
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/** «Подписки» in 3 Fold configurations × light/dark, on deterministic test data. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubscriptionsScreenshotTest {

    private val zone = InsightsSamples.zone
    private val today = LocalDate.now(ScreenshotClock)

    private fun at(date: String) = LocalDate.parse(date).atTime(9, 0).atZone(zone).toInstant()

    private var nextId = 1L

    private fun sub(
        merchant: String,
        amountMinor: Long,
        period: SubscriptionPeriod,
        last: String,
        next: String,
        status: SubscriptionStatus = SubscriptionStatus.CONFIRMED,
        categoryKey: String? = "subscriptions",
        currency: String = "RUB",
    ) = Subscription(
        id = nextId++,
        merchant = merchant,
        amountMinor = amountMinor,
        currency = currency,
        period = period,
        lastChargeAt = at(last),
        nextChargeAt = at(next),
        status = status,
        categoryId = categoryKey?.let(InsightsSamples::categoryId),
    )

    private val sample: List<Subscription> by lazy {
        listOf(
            sub("Яндекс Плюс", 39_900, SubscriptionPeriod.MONTHLY, "2026-11-03", "2026-12-03"),
            sub("Кинопоиск", 29_900, SubscriptionPeriod.MONTHLY, "2026-10-15", "2026-11-15", categoryKey = "entertainment"),
            sub("Бассейн", 150_000, SubscriptionPeriod.WEEKLY, "2026-10-30", "2026-11-06", categoryKey = "health"),
            sub("Мобильная связь", 65_000, SubscriptionPeriod.MONTHLY, "2026-10-22", "2026-11-22", categoryKey = "utilities"),
            sub("Spotify", 999, SubscriptionPeriod.MONTHLY, "2026-10-27", "2026-11-27", currency = "USD", categoryKey = "entertainment"),
            sub("Антивирус", 199_000, SubscriptionPeriod.YEARLY, "2026-03-01", "2027-03-01", categoryKey = "other"),
            sub("Мосэнергосбыт", 649_000, SubscriptionPeriod.MONTHLY, "2026-10-11", "2026-11-11", SubscriptionStatus.CANDIDATE, "utilities"),
            sub("VK Музыка", 19_900, SubscriptionPeriod.MONTHLY, "2026-10-28", "2026-11-28", SubscriptionStatus.CANDIDATE),
            sub("Ozon Premium", 19_900, SubscriptionPeriod.MONTHLY, "2026-09-01", "2026-10-01", SubscriptionStatus.DISMISSED, "shopping"),
        )
    }

    private fun state(selected: LocalDate? = null, expanded: Boolean = false) = buildSubscriptionsState(
        subscriptions = sample,
        categories = InsightsSamples.categories,
        today = today,
        zone = zone,
        month = YearMonth.from(today),
        selectedDate = selected,
        calendarExpanded = expanded,
    )

    @Test fun subscriptions() = captureInAllConfigs("subscriptions") {
        SubscriptionsScreen(state = state(selected = LocalDate.of(2026, 11, 15), expanded = true), actions = SubscriptionsActions())
    }

    /** Outer screen with the calendar folded: one line with the next charge. */
    @Test fun collapsed() = captureInAllConfigs("subscriptions_collapsed", configs = listOf(com.foldfinance.app.DeviceConfigs.Outer)) {
        SubscriptionsScreen(state = state(), actions = SubscriptionsActions())
    }

    @Test fun empty() = captureInAllConfigs("subscriptions_empty") {
        SubscriptionsScreen(
            state = buildSubscriptionsState(emptyList(), InsightsSamples.categories, today, zone, YearMonth.from(today), null, false),
            actions = SubscriptionsActions(),
        )
    }
}
