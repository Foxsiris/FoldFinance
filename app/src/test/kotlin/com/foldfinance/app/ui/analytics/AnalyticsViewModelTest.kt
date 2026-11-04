package com.foldfinance.app.ui.analytics

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.foldfinance.app.ScreenshotClock
import com.foldfinance.core.domain.analytics.PeriodType
import com.foldfinance.parser.Direction
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

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val transactions = FakeTransactionRepository(InsightsSamples.history)
    private val categories = FakeCategoryRepository()

    /** «Today» is 5 November 2026, Moscow. */
    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        AnalyticsViewModel(handle, transactions, categories, ScreenshotClock)

    @Test fun `defaults to the current month and counts only its operations`() = runTest {
        val vm = viewModel()
        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            assertThat(state.period.type).isEqualTo(PeriodType.MONTH)
            assertThat(state.period.startDate).isEqualTo(LocalDate.of(2026, 11, 1))
            assertThat(state.canGoNext).isFalse()
            val november = InsightsSamples.history.filter {
                it.currency == "RUB" && it.occurredAt >= state.period.start && it.occurredAt < state.period.endExclusive
            }
            val expected = november.filter { it.direction == Direction.EXPENSE }.sumOf { it.amountMinor } -
                november.filter { it.direction == Direction.REFUND }.sumOf { it.amountMinor }
            assertThat(state.summary!!.netExpenseMinor).isEqualTo(expected)
            assertThat(state.buckets).hasSize(30)
        }
    }

    @Test fun `period type and paging are saved and survive recreation`() = runTest {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        vm.state.test {
            awaitUntil { !it.isLoading }
            vm.selectType(PeriodType.WEEK)
            val week = awaitUntil { !it.isLoading && it.period.type == PeriodType.WEEK }
            assertThat(week.period.startDate).isEqualTo(LocalDate.of(2026, 11, 2))
            assertThat(week.buckets).hasSize(7)

            vm.previous()
            val previous = awaitUntil { !it.isLoading && it.period.startDate == LocalDate.of(2026, 10, 26) }
            assertThat(previous.canGoNext).isTrue()
            cancelAndIgnoreRemainingEvents()
        }

        // Fold/unfold or «Не сохранять действия»: a new ViewModel over the restored handle.
        val restored = viewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        restored.state.test {
            val state = awaitUntil { !it.isLoading }
            assertThat(state.period.type).isEqualTo(PeriodType.WEEK)
            assertThat(state.period.startDate).isEqualTo(LocalDate.of(2026, 10, 26))
        }
    }

    @Test fun `next stops at the current period`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitUntil { !it.isLoading }
            vm.next()
            expectNoEvents()
            assertThat(vm.state.value.period.startDate).isEqualTo(LocalDate.of(2026, 11, 1))
        }
    }

    @Test fun `tap on a column shows that day's operations and a second tap hides them`() = runTest {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        val day = LocalDate.of(2026, 10, 17)
        vm.state.test {
            awaitUntil { !it.isLoading }
            vm.previous()
            awaitUntil { !it.isLoading && it.period.startDate == LocalDate.of(2026, 10, 1) }

            vm.selectBucket(day)
            val selected = awaitUntil { it.selectedBucket != null }
            assertThat(selected.selectedBucket!!.start).isEqualTo(day)
            assertThat(selected.selectedIndex).isEqualTo(16)
            assertThat(selected.operations).isNotEmpty()
            assertThat(selected.operations.map { it.occurredAt.atZone(InsightsSamples.zone).toLocalDate() }.toSet()).containsExactly(day)
            // All currencies and directions of the day are listed, newest first.
            assertThat(selected.operations.map { it.merchant }).containsAtLeast("Steam", "Ozon", "Перевод себе *4821")
            assertThat(selected.operations).isInOrder(compareByDescending<com.foldfinance.core.model.Transaction> { it.occurredAt })
            assertThat(handle.get<String>(AnalyticsViewModel.KEY_DAY)).isEqualTo("2026-10-17")

            vm.selectBucket(day)
            val cleared = awaitUntil { it.selectedBucket == null }
            assertThat(cleared.operations).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `selected day survives recreation and is cleared by paging`() = runTest {
        val handle = SavedStateHandle(
            mapOf(
                AnalyticsViewModel.KEY_PERIOD to "MONTH|2026-10-01|",
                AnalyticsViewModel.KEY_DAY to "2026-10-05",
            ),
        )
        val vm = viewModel(handle)
        vm.state.test {
            val restored = awaitUntil { !it.isLoading }
            assertThat(restored.selectedBucket?.start).isEqualTo(LocalDate.of(2026, 10, 5))
            assertThat(restored.operations.map { it.merchant }).contains("Зарплата")

            vm.next()
            val november = awaitUntil { !it.isLoading && it.period.startDate == LocalDate.of(2026, 11, 1) }
            assertThat(november.selectedBucket).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `custom range and currency selection`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitUntil { !it.isLoading }
            vm.selectCustom(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 10))
            val custom = awaitUntil { !it.isLoading && it.period.type == PeriodType.CUSTOM }
            assertThat(custom.period.startDate).isEqualTo(LocalDate.of(2026, 10, 10))
            assertThat(custom.period.endDateInclusive).isEqualTo(LocalDate.of(2026, 10, 20))
            assertThat(custom.currencies.map { it.currency }).containsExactly("RUB", "USD").inOrder()
            assertThat(custom.currency).isEqualTo("RUB")

            vm.selectCurrency("USD")
            val usd = awaitUntil { it.currency == "USD" }
            assertThat(usd.summary!!.netExpenseMinor).isEqualTo(1_299)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `long periods are charted by month`() = runTest {
        val vm = viewModel()
        vm.state.test {
            awaitUntil { !it.isLoading }
            vm.selectType(PeriodType.YEAR)
            val year = awaitUntil { !it.isLoading && it.period.type == PeriodType.YEAR }
            assertThat(year.granularity).isEqualTo(ChartGranularity.MONTH)
            assertThat(year.buckets).hasSize(12)
            assertThat(year.buckets.first { it.start == LocalDate.of(2026, 10, 1) }.incomeMinor).isEqualTo(12_000_000)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `period without operations is empty, not zero`() = runTest {
        val vm = viewModel(SavedStateHandle(mapOf(AnalyticsViewModel.KEY_PERIOD to "MONTH|2026-05-01|")))
        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            assertThat(state.hasData).isFalse()
            assertThat(state.summary).isNull()
            assertThat(state.buckets).isEmpty()
        }
    }
}
