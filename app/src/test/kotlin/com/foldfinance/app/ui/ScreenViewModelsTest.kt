package com.foldfinance.app.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.foldfinance.app.ScreenshotClock
import com.foldfinance.app.ui.components.Period
import com.foldfinance.app.ui.fakes.FakeBalanceRepository
import com.foldfinance.app.ui.fakes.FakeCategoryRepository
import com.foldfinance.app.ui.fakes.FakeMerchantRuleRepository
import com.foldfinance.app.ui.fakes.FakeTransactionRepository
import com.foldfinance.app.ui.fakes.SampleData
import com.foldfinance.app.ui.fakes.fakePreferences
import com.foldfinance.app.ui.analytics.FakeSubscriptionRepository
import com.foldfinance.app.prefs.AppPreferences
import com.foldfinance.app.ui.history.HistoryPeriod
import com.foldfinance.app.ui.history.HistoryViewModel
import com.foldfinance.app.ui.overview.CategorySelection
import com.foldfinance.app.ui.overview.OverviewViewModel
import com.foldfinance.app.ui.review.ReviewViewModel
import com.foldfinance.app.ui.transaction.DeferredDeletion
import com.foldfinance.core.data.repository.TransactionSort
import com.foldfinance.parser.Direction
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Continuity of the screen ViewModels: what lives in SavedStateHandle comes back after recreation. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenViewModelsTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun SavedStateHandle.copy() = SavedStateHandle(keys().associateWith { get<Any>(it) })

    // ------------------------------------------------------------------ History

    private fun history(
        handle: SavedStateHandle,
        repo: FakeTransactionRepository = SampleData.transactionRepository(),
        rules: FakeMerchantRuleRepository = FakeMerchantRuleRepository(),
        preferences: AppPreferences = fakePreferences(),
    ) =
        HistoryViewModel(handle, repo, FakeCategoryRepository(), rules, preferences, ScreenshotClock)

    @Test fun `history chat pane choice survives recreation, chat scope follows the filter`() = runTest {
        val handle = SavedStateHandle()
        val first = history(handle)
        assertThat(first.chatShown.value).isFalse()
        first.showChat(true)
        first.setPeriod(HistoryPeriod.MONTH)
        val groceries = SampleData.categoryId("groceries")
        first.toggleCategoryFilter(groceries)

        val restored = history(handle.copy())
        assertThat(restored.chatShown.value).isTrue()
        val scope = restored.filter.value.chatScope(ScreenshotClock.instant(), ScreenshotClock.zone)
        assertThat(scope.categoryId).isEqualTo(groceries)
        assertThat(scope.period!!.startDate).isEqualTo(java.time.LocalDate.of(2026, 11, 1))
        // Two categories or «без категории» — no single category for the chat.
        restored.toggleUncategorized()
        assertThat(restored.filter.value.chatScope(ScreenshotClock.instant(), ScreenshotClock.zone).categoryId).isNull()
    }

    @Test fun `history filters, search and sort survive recreation`() = runTest {
        val handle = SavedStateHandle()
        val first = history(handle)
        first.setPeriod(HistoryPeriod.MONTH)
        first.toggleDirection(Direction.EXPENSE)
        first.toggleCategoryFilter(SampleData.categoryId("groceries"))
        first.setSort(TransactionSort.AMOUNT_DESC)

        val restored = history(handle.copy())
        restored.state.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            assertThat(state.filter.period).isEqualTo(HistoryPeriod.MONTH)
            assertThat(state.filter.directionValues).containsExactly(Direction.EXPENSE)
            assertThat(state.filter.sortValue).isEqualTo(TransactionSort.AMOUNT_DESC)
            // Groceries this month, by amount: Лента 2 315, Пятёрочка 1 240,50, ВкусВилл 864,20.
            val items = state.sections.flatMap { it.items }
            assertThat(items.map { it.merchant }).containsExactly("Лента", "Пятёрочка", "ВкусВилл").inOrder()
            assertThat(state.sections.single().date).isNull()
        }
    }

    @Test fun `history groups by day, newest first`() = runTest {
        history(SavedStateHandle()).state.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            val dates = state.sections.map { it.date.toString() }
            assertThat(dates.first()).isEqualTo("2026-11-05")
            assertThat(dates).isInOrder(Comparator.reverseOrder<String>())
        }
    }

    @Test fun `category change from the detail asks about a merchant rule and the answer applies it`() = runTest {
        val handle = SavedStateHandle()
        val rules = FakeMerchantRuleRepository()
        val repo = SampleData.transactionRepository()
        val vm = history(handle, repo, rules)
        vm.state.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            vm.setCategory(5L, SampleData.categoryId("entertainment")) // Ozon → Развлечения
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repo.items.value.first { it.id == 5L }.categoryId).isEqualTo(SampleData.categoryId("entertainment"))
        // The open question survives folding.
        val restored = history(handle.copy(), repo, rules)
        assertThat(restored.rulePrompt.value?.merchant).isEqualTo("Ozon")
        restored.applyRule()
        assertThat(rules.calls.single()).isEqualTo(Triple("Ozon", SampleData.categoryId("entertainment"), true))
        assertThat(restored.rulePrompt.value).isNull()
    }

    @Test fun `delete hides at once, undo restores, otherwise deletes after the window`() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val scope = TestScope(dispatcher)
        val repo = SampleData.transactionRepository()
        val vm = history(SavedStateHandle(), repo)
        vm.delete(1L)
        assertThat(vm.pendingDeletion.value).containsExactly(1L)
        vm.undoDelete(1L)
        scope.advanceTimeBy(DeferredDeletion.UNDO_WINDOW_MS + 1)
        scope.runCurrent()
        assertThat(repo.deleted).isEmpty()

        vm.delete(1L)
        scope.advanceTimeBy(DeferredDeletion.UNDO_WINDOW_MS + 1)
        scope.runCurrent()
        assertThat(repo.deleted).containsExactly(1L)
        assertThat(vm.pendingDeletion.value).isEmpty()
    }

    // ------------------------------------------------------------------ Review

    private fun review(handle: SavedStateHandle, repo: FakeTransactionRepository = SampleData.transactionRepository()) =
        ReviewViewModel(handle, repo, FakeCategoryRepository(), FakeMerchantRuleRepository(), ScreenshotClock)

    @Test fun `review selection and focus survive recreation`() = runTest {
        val handle = SavedStateHandle()
        val vm = review(handle)
        vm.state.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            assertThat(state.queue.map { it.id }).containsExactly(2L, 16L, 8L).inOrder()
            vm.toggleSelected(2L)
            vm.toggleSelected(8L)
            vm.focus(16L)
            cancelAndIgnoreRemainingEvents()
        }
        val restored = review(handle.copy())
        restored.state.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            assertThat(state.selectedIds).containsExactly(2L, 8L)
            assertThat(state.focusedId).isEqualTo(16L)
        }
    }

    @Test fun `confirm and bulk category take operations out of the queue`() = runTest {
        val repo = SampleData.transactionRepository()
        val vm = review(SavedStateHandle(), repo)
        vm.state.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            vm.confirm(2L)
            vm.toggleSelected(16L)
            vm.toggleSelected(8L)
            vm.assignToSelected(SampleData.categoryId("transfers"))
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repo.items.value.filter { it.needsReview }).isEmpty()
        assertThat(repo.items.value.first { it.id == 8L }.categoryId).isEqualTo(SampleData.categoryId("transfers"))
        assertThat(vm.state.value.queue).isEmpty()
        assertThat(vm.state.value.selectedIds).isEmpty()
    }

    @Test fun `skip moves the operation to the end of the queue and survives recreation`() = runTest {
        val handle = SavedStateHandle()
        val vm = review(handle)
        vm.state.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            vm.skip(2L)
            state = awaitItem()
            assertThat(state.queue.map { it.id }).containsExactly(16L, 8L, 2L).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
        review(handle.copy()).state.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            assertThat(state.current?.id).isEqualTo(16L)
        }
    }

    // ------------------------------------------------------------------ Overview

    private fun overview(
        handle: SavedStateHandle,
        preferences: AppPreferences = fakePreferences(),
        subscriptions: FakeSubscriptionRepository = FakeSubscriptionRepository(),
    ) = OverviewViewModel(
        handle, SampleData.transactionRepository(), FakeCategoryRepository(), FakeBalanceRepository(SampleData.balances),
        subscriptions, preferences, ScreenshotClock,
    )

    @Test fun `overview totals are by known operations - refunds reduce, transfers and income do not count`() = runTest {
        overview(SavedStateHandle()).state.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            // Today: 1 240,50 + 419 + 380 + 2 000 = 4 039,50.
            assertThat(state.todayMinor).isEqualTo(403_950L)
            // Month: today + 2 599 + 864,20 + 65 + 1 500 − 1 299 + 742 + 399 + 3 214,77 + 540 + 2 315.
            assertThat(state.monthMinor).isEqualTo(403_950L + 259_900 + 86_420 + 6_500 + 150_000 - 129_900 + 74_200 + 39_900 + 321_477 + 54_000 + 231_500)
            assertThat(state.reviewCount).isEqualTo(3)
            assertThat(state.balances.map { it.cardMask }).containsExactly("*4821", "*1177").inOrder()
            assertThat(state.bars).hasSize(30)
            // Outer «Главная»: income line, today's count, every day of the month, the unfiltered recent feed.
            assertThat(state.monthIncomeMinor).isGreaterThan(0L)
            assertThat(state.todayExpenseCount).isEqualTo(4)
            assertThat(state.monthBars).hasSize(30)
            assertThat(state.monthBars.filter { it.date.isAfter(state.today) }.all { it.amountMinor == 0L }).isTrue()
            assertThat(state.recent.first().occurredAt).isAtLeast(state.recent.last().occurredAt)
        }
    }

    @Test fun `overview category and day selection filter the list and survive recreation`() = runTest {
        val handle = SavedStateHandle()
        val vm = overview(handle)
        vm.toggleCategory(CategorySelection.Of(SampleData.categoryId("groceries")))
        vm.toggleBar(java.time.LocalDate.parse("2026-11-04").toEpochDay())
        vm.setPeriod(Period.Month)
        vm.toggleBar(java.time.LocalDate.parse("2026-11-04").toEpochDay())

        overview(handle.copy()).state.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            assertThat(state.selectedCategory).isEqualTo(CategorySelection.Of(SampleData.categoryId("groceries")))
            assertThat(state.selectedBar).isEqualTo(java.time.LocalDate.parse("2026-11-04").toEpochDay())
            assertThat(state.transactions.map { it.merchant }).containsExactly("ВкусВилл")
        }
    }

    // ------------------------------------------------------------------ Currency from the settings

    @Test fun `overview totals follow the currency from the settings`() = runTest {
        val preferences = fakePreferences(currency = "USD")
        overview(SavedStateHandle(), preferences).state.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            // Sample operations are all in roubles: nothing in dollars today.
            assertThat(state.currency).isEqualTo("USD")
            assertThat(state.todayMinor).isEqualTo(0L)

            preferences.setDefaultCurrency("RUB")
            while (state.currency != "RUB") state = awaitItem()
            assertThat(state.todayMinor).isEqualTo(403_950L)
        }
    }

    @Test fun `overview subscriptions card sums confirmed subscriptions per month`() = runTest {
        val subscriptions = FakeSubscriptionRepository(SampleData.subscriptions)
        overview(SavedStateHandle(), subscriptions = subscriptions).state.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            assertThat(state.subscriptions.confirmed).isEqualTo(3)
            assertThat(state.subscriptions.candidates).isEqualTo(1)
            assertThat(state.subscriptions.monthlyMinor).isEqualTo(39_900L + 29_900 + 65_000)
        }
    }

    @Test fun `history totals and amount filter follow the currency from the settings`() = runTest {
        val preferences = fakePreferences(currency = "EUR")
        history(SavedStateHandle(), preferences = preferences).state.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            assertThat(state.currency).isEqualTo("EUR")
            assertThat(state.expenseMinor).isEqualTo(0L)
            assertThat(state.count).isGreaterThan(0)

            preferences.setDefaultCurrency("RUB")
            while (state.currency != "RUB") state = awaitItem()
            assertThat(state.expenseMinor).isGreaterThan(0L)
        }
    }
}
