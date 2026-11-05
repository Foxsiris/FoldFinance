package com.foldfinance.app.ui.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foldfinance.app.DeviceConfigs
import com.foldfinance.app.captureInAllConfigs
import com.foldfinance.app.ui.fakes.FakeBalanceRepository
import com.foldfinance.app.ui.fakes.FakeCardRepository
import com.foldfinance.app.ui.fakes.FakeTransactionRepository
import com.foldfinance.app.ui.fakes.ViewModelHarness
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * «Счета и карты» on deterministic data ([AccountsSamples]) in the three Fold configurations ×
 * light/dark: the list (two panes show the first card next to it), an opened card, and the empty
 * state. States come from the real ViewModel over fakes.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountsScreenshotTest {

    private val harness = ViewModelHarness()

    @Before fun setUp() = harness.install()
    @After fun tearDown() = harness.reset()

    private fun state(selected: String? = null): AccountsUiState {
        val handle = SavedStateHandle()
        val vm = AccountsSamples.viewModel(handle)
        vm.select(selected)
        return harness.snapshot(vm.state)
    }

    /** Outer: the list; book: list + the first card by the hinge; tabletop: balances on top, list below. */
    @Test fun list() {
        val state = state()
        captureInAllConfigs("accounts_list") {
            AccountsScreen(state = state, actions = AccountsActions.None, onBack = {})
        }
    }

    /** A card opened: over the list outside, highlighted next to it inside, balance on top in tabletop. */
    @Test fun card() {
        val state = state(selected = "*4821")
        captureInAllConfigs("accounts_card") {
            AccountsScreen(state = state, actions = AccountsActions.None, onBack = {})
        }
    }

    /** The savings account: transfers in from the own card, interest, a short balance history. */
    @Test fun savingsCard() {
        val state = state(selected = "*1177")
        captureInAllConfigs("accounts_card_savings", configs = listOf(DeviceConfigs.Outer)) {
            AccountsScreen(state = state, actions = AccountsActions.None, onBack = {})
        }
    }

    @Test fun empty() {
        val vm = AccountsSamples.viewModel(
            cards = FakeCardRepository(),
            balances = FakeBalanceRepository(),
            transactions = FakeTransactionRepository(),
        )
        val state = harness.snapshot(vm.state)
        captureInAllConfigs("accounts_empty", configs = listOf(DeviceConfigs.Outer, DeviceConfigs.Inner)) {
            AccountsScreen(state = state, actions = AccountsActions.None, onBack = {})
        }
    }
}
