package com.foldfinance.app.ui.subscriptions

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.foldfinance.app.ScreenshotClock
import com.foldfinance.app.ui.analytics.FakeCategoryRepository
import com.foldfinance.app.ui.analytics.FakeSubscriptionRepository
import com.foldfinance.app.ui.analytics.FakeTransactionRepository
import com.foldfinance.app.ui.analytics.InsightsSamples
import com.foldfinance.app.ui.analytics.awaitUntil
import com.foldfinance.core.data.subscriptions.RefreshSubscriptionsUseCase
import com.foldfinance.core.model.Subscription
import com.foldfinance.core.model.SubscriptionPeriod
import com.foldfinance.core.model.SubscriptionStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionsViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val transactions = FakeTransactionRepository(InsightsSamples.history)
    private val subscriptions = FakeSubscriptionRepository()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = SubscriptionsViewModel(
        savedStateHandle = handle,
        subscriptions = subscriptions,
        refresh = RefreshSubscriptionsUseCase(transactions, subscriptions, ScreenshotClock),
        categories = FakeCategoryRepository(),
        aliases = com.foldfinance.app.ui.fakes.FakeMerchantAliasRepository(),
        clock = ScreenshotClock,
    )

    @Test fun `history becomes candidates on start`() = runTest {
        val vm = viewModel()
        vm.state.test {
            val state = awaitUntil { it.candidates.isNotEmpty() }
            // «Яндекс Плюс» on the 3rd and the electricity bill on the 11th of every month.
            // Random daily shopping also forms a few weekly-looking series; the detector proposes them too.
            assertThat(state.candidates.map { it.subscription.merchant }).containsAtLeast("Яндекс Плюс", "Мосэнергосбыт")
            assertThat(state.confirmed).isEmpty()
            assertThat(state.totals).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `confirming a candidate adds it to the list, the total and the calendar`() = runTest {
        val vm = viewModel()
        vm.state.test {
            val candidates = awaitUntil { s -> s.candidates.any { it.subscription.merchant == "Яндекс Плюс" } }
            val plus = candidates.candidates.first { it.subscription.merchant == "Яндекс Плюс" }

            vm.confirm(plus.id)
            val state = awaitUntil { it.confirmed.isNotEmpty() }
            assertThat(state.candidates.map { it.id }).doesNotContain(plus.id)
            assertThat(state.confirmed.single().subscription.status).isEqualTo(SubscriptionStatus.CONFIRMED)
            assertThat(state.totals.single().amountMinor).isEqualTo(39_900)
            // Last charge 3 November (past, «списано»); the next one is 3 December.
            assertThat(state.confirmed.single().nextCharge).isEqualTo(LocalDate.of(2026, 12, 3))
            assertThat(state.charges[LocalDate.of(2026, 11, 3)]!!.single().isPast).isTrue()
            assertThat(state.upcoming.map { it.date }).containsExactly(LocalDate.of(2026, 12, 3))
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(subscriptions.rows.value.first { it.merchant == "Яндекс Плюс" }.status).isEqualTo(SubscriptionStatus.CONFIRMED)
    }

    @Test fun `dismissed candidate disappears and is not proposed again`() = runTest {
        val vm = viewModel()
        vm.state.test {
            val first = awaitUntil { s -> s.candidates.any { it.subscription.merchant == "Мосэнергосбыт" } }
            val bill = first.candidates.first { it.subscription.merchant == "Мосэнергосбыт" }
            vm.dismiss(bill.id)
            val state = awaitUntil { s -> s.candidates.none { it.subscription.merchant == "Мосэнергосбыт" } }
            assertThat(state.candidates.map { it.subscription.merchant }).contains("Яндекс Плюс")
            cancelAndIgnoreRemainingEvents()
        }
        // Next start: the refresh keeps the user's decision.
        viewModel().state.test {
            val state = awaitUntil { !it.isLoading && it.candidates.isNotEmpty() }
            assertThat(state.candidates.map { it.subscription.merchant }).doesNotContain("Мосэнергосбыт")
            assertThat(state.candidates.map { it.subscription.merchant }).contains("Яндекс Плюс")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `calendar month, day and expansion are saved and bounded`() = runTest {
        val handle = SavedStateHandle()
        subscriptions.rows.value = listOf(confirmedWeekly())
        val vm = viewModel(handle)
        vm.state.test {
            awaitUntil { !it.isLoading }
            vm.previousMonth() // the current month is the first one
            assertThat(vm.state.value.month).isEqualTo(YearMonth.of(2026, 11))

            vm.nextMonth()
            val december = awaitUntil { it.month == YearMonth.of(2026, 12) }
            // Weekly on Fridays: 4, 11, 18, 25 December.
            assertThat(december.charges.keys.map { it.dayOfMonth }).containsExactly(4, 11, 18, 25)

            vm.selectDate(LocalDate.of(2026, 12, 11))
            val day = awaitUntil { it.selectedDate != null }
            assertThat(day.selectedCharges.single().item.subscription.merchant).isEqualTo("Бассейн")

            vm.toggleCalendar()
            awaitUntil { it.calendarExpanded }
            cancelAndIgnoreRemainingEvents()
        }
        val restored = viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        restored.state.test {
            val state = awaitUntil { !it.isLoading }
            assertThat(state.month).isEqualTo(YearMonth.of(2026, 12))
            assertThat(state.selectedDate).isEqualTo(LocalDate.of(2026, 12, 11))
            assertThat(state.calendarExpanded).isTrue()
            // Weekly 1 500 ₽ is 6 500 ₽ a month (× 52 / 12).
            assertThat(state.totals.single().amountMinor).isEqualTo(650_000)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun confirmedWeekly() = Subscription(
        id = 50,
        merchant = "Бассейн",
        amountMinor = 150_000,
        currency = "RUB",
        period = SubscriptionPeriod.WEEKLY,
        lastChargeAt = LocalDate.of(2026, 10, 30).atTime(9, 0).atZone(InsightsSamples.zone).toInstant(),
        nextChargeAt = LocalDate.of(2026, 11, 6).atTime(9, 0).atZone(InsightsSamples.zone).toInstant(),
        status = SubscriptionStatus.CONFIRMED,
    )
}
