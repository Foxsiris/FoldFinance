package com.foldfinance.app.ui.accounts

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.foldfinance.app.ui.analytics.awaitUntil
import com.foldfinance.app.ui.fakes.FakeBalanceRepository
import com.foldfinance.app.ui.fakes.FakeCardRepository
import com.foldfinance.app.ui.fakes.FakeTransactionRepository
import com.foldfinance.core.domain.accounts.TransferPair
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
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `cards with known balance and the month by known operations, own first`() = runTest {
        val vm = AccountsSamples.viewModel()
        vm.state.test {
            val state = awaitUntil { !it.loading }
            assertThat(state.month).isEqualTo(YearMonth.of(2026, 11))
            assertThat(state.accounts.map { it.mask }).containsExactly("*4821", "*1177", "*9034").inOrder()

            val salary = state.accounts.first()
            assertThat(salary.card.label).isEqualTo("Зарплатная")
            // The bank's last value, not a sum of operations.
            assertThat(salary.latestBalance!!.amountMinor).isEqualTo(4_821_035)
            val rub = salary.totals!!.byCurrency.single()
            assertThat(rub.netExpenseMinor).isEqualTo(1_443_947)
            assertThat(rub.refundMinor).isEqualTo(129_900)
            assertThat(rub.incomeMinor).isEqualTo(9_500_000)
            // Two transfers to the savings account: not expenses.
            assertThat(rub.internalOutMinor).isEqualTo(1_500_000)

            val savings = state.accounts[1].totals!!.byCurrency.single()
            assertThat(savings.incomeMinor).isEqualTo(4_210)
            assertThat(savings.internalInMinor).isEqualTo(1_000_000)

            assertThat(state.accounts[2].latestBalance).isNull()
            // Debit and credit messages of the same transfer are one movement.
            assertThat(state.pairs).containsExactly(TransferPair("*4821", "*1177", "RUB", 1_500_000, 2))
            assertThat(state.unresolvedTransfers).isEmpty()

            // Nothing selected: the second pane shows the first card.
            assertThat(state.selectedMask).isNull()
            assertThat(state.detail!!.summary.mask).isEqualTo("*4821")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `card detail has balance history, operations without own transfers and the transfers`() = runTest {
        val handle = SavedStateHandle()
        val vm = AccountsSamples.viewModel(handle)
        vm.state.test {
            awaitUntil { !it.loading }
            vm.select("*1177")
            val detail = awaitUntil { it.detail?.summary?.mask == "*1177" && it.detail!!.balanceHistory.isNotEmpty() }.detail!!
            assertThat(detail.balanceHistory.map { it.amountMinor }).containsExactly(1_504_210L, 1_004_210L, 1_000_000L, 0L).inOrder()
            assertThat(detail.operations.map { it.id }).containsExactly(103L)
            assertThat(detail.movements.map { it.transactionIds }).containsExactly(listOf(102L), listOf(13L, 101L)).inOrder()
            assertThat(detail.movements.all { it.toMask == "*1177" && it.fromMask == "*4821" }).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(handle.get<String>(AccountsViewModel.KEY_SELECTED)).isEqualTo("*1177")
    }

    @Test fun `selection, month and the open rename survive recreation`() = runTest {
        val handle = SavedStateHandle()
        val first = AccountsSamples.viewModel(handle)
        first.select("*9034")
        first.showPreviousMonth()
        first.startRename("*9034")

        // Folding recreates the activity: a new ViewModel over the same saved state.
        val second = AccountsSamples.viewModel(handle)
        second.state.test {
            val state = awaitUntil { !it.loading && it.detail?.summary?.mask == "*9034" }
            assertThat(state.selectedMask).isEqualTo("*9034")
            assertThat(state.month).isEqualTo(YearMonth.of(2026, 10))
            assertThat(state.renaming).isEqualTo("*9034")
            // October: only the purchase of 20 October on this card.
            assertThat(state.detail!!.summary.totals!!.byCurrency.single().netExpenseMinor).isEqualTo(45_000)
            assertThat(state.canGoNext).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `next month stops at the current one`() = runTest {
        val vm = AccountsSamples.viewModel()
        vm.showNextMonth()
        vm.state.test {
            val state = awaitUntil { !it.loading }
            assertThat(state.month).isEqualTo(YearMonth.of(2026, 11))
            assertThat(state.canGoNext).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `marking own and renaming go to the card repository`() = runTest {
        val cards = FakeCardRepository(AccountsSamples.cards)
        val handle = SavedStateHandle()
        val vm = AccountsSamples.viewModel(handle, cards = cards)
        vm.state.test {
            awaitUntil { !it.loading }
            vm.setOwn("*9034", true)
            vm.startRename("*9034")
            assertThat(awaitUntil { it.renaming == "*9034" }.renaming).isEqualTo("*9034")
            vm.rename("*9034", "  Кредитка ")
            val state = awaitUntil { s -> s.renaming == null && s.accounts.any { it.card.label == "Кредитка" } }
            val card = state.accounts.single { it.mask == "*9034" }.card
            assertThat(card.isOwn).isTrue()
            // An empty label removes it.
            vm.rename("*9034", "   ")
            awaitUntil { s -> s.accounts.single { it.mask == "*9034" }.card.label == null }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(handle.get<String?>(AccountsViewModel.KEY_RENAME)).isNull()
    }

    @Test fun `no cards yet is an empty state, not demo data`() = runTest {
        val vm = AccountsSamples.viewModel(
            cards = FakeCardRepository(),
            balances = FakeBalanceRepository(),
            transactions = FakeTransactionRepository(),
        )
        vm.state.test {
            val state = awaitUntil { !it.loading }
            assertThat(state.isEmpty).isTrue()
            assertThat(state.detail).isNull()
            assertThat(state.pairs).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
