package com.foldfinance.app.ui.shell

import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.foldfinance.app.AppIntents
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.SplitSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {

    private class FakeOnboardingFlag(initial: Boolean) : OnboardingFlag {
        val state = MutableStateFlow(initial)
        override val done = state
        override suspend fun markDone() {
            state.value = true
        }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `defaults to the first visible section`() {
        val vm = ShellViewModel(SavedStateHandle(), Section.entries)
        assertThat(vm.selected.value).isEqualTo(Section.Overview)
    }

    @Test fun `selection is written to SavedStateHandle and restored after recreation`() {
        val handle = SavedStateHandle()
        ShellViewModel(handle, Section.entries).select(Section.Analytics)
        assertThat(handle.get<String>(ShellViewModel.KEY_SELECTED)).isEqualTo("Analytics")

        // A new ViewModel over the restored handle = activity recreated after folding / process death.
        val restored = ShellViewModel(SavedStateHandle(mapOf(ShellViewModel.KEY_SELECTED to "Analytics")), Section.entries)
        assertThat(restored.selected.value).isEqualTo(Section.Analytics)
    }

    @Test fun `hidden sections cannot be selected`() {
        val vm = ShellViewModel(SavedStateHandle(), listOf(Section.Overview))
        vm.select(Section.Review)
        assertThat(vm.selected.value).isEqualTo(Section.Overview)
    }

    @Test fun `restored selection that is no longer visible falls back`() {
        val handle = SavedStateHandle(mapOf(ShellViewModel.KEY_SELECTED to "Review"))
        val vm = ShellViewModel(handle, listOf(Section.Overview))
        assertThat(vm.selected.value).isEqualTo(Section.Overview)
    }

    @Test fun `all six sections are ready and visible in release and debug`() {
        assertThat(Sections.visible(debug = false)).containsExactlyElementsIn(Section.entries).inOrder()
        assertThat(Sections.visible(debug = true)).containsExactlyElementsIn(Section.entries).inOrder()
    }

    @Test fun `review badge follows the queue size`() = runTest {
        val count = MutableStateFlow(3)
        val vm = ShellViewModel(SavedStateHandle(), Section.entries, count)
        vm.reviewCount.test {
            // The initial value of the StateFlow may or may not be observed before the queue size.
            var first = awaitItem()
            if (first == 0) first = awaitItem()
            assertThat(first).isEqualTo(3)
            count.value = 0
            assertThat(awaitItem()).isEqualTo(0)
        }
    }

    @Test fun `onboarding is required until finished, then stays done`() = runTest {
        val flag = FakeOnboardingFlag(initial = false)
        val vm = ShellViewModel(SavedStateHandle(), Section.entries, flowOf(0), flag)
        vm.onboarding.test {
            assertThat(awaitItem()).isEqualTo(OnboardingState.Required)
            vm.finishOnboarding()
            assertThat(awaitItem()).isEqualTo(OnboardingState.Done)
        }
        // Next launch reads the stored flag.
        val next = ShellViewModel(SavedStateHandle(), Section.entries, flowOf(0), flag)
        assertThat(next.onboarding.value).isEqualTo(OnboardingState.Done)
    }

    @Test fun `intent actions open review or ask for the add form`() {
        val vm = ShellViewModel(SavedStateHandle(), Section.entries)
        assertThat(vm.handleIntentAction(AppIntents.ACTION_OPEN_REVIEW)).isFalse()
        assertThat(vm.selected.value).isEqualTo(Section.Review)
        assertThat(vm.handleIntentAction(AppIntents.ACTION_QUICK_ADD)).isTrue()
        assertThat(vm.selected.value).isEqualTo(Section.Overview)
        assertThat(vm.handleIntentAction("android.intent.action.MAIN")).isFalse()
        assertThat(vm.selected.value).isEqualTo(Section.Overview)
    }

    @Test fun `open transaction switches to history with a saved pending request`() {
        val handle = SavedStateHandle()
        val vm = ShellViewModel(handle, Section.entries)
        vm.openTransaction(42L)
        assertThat(vm.selected.value).isEqualTo(Section.History)
        assertThat(vm.pendingTransaction.value).isEqualTo(42L)
        // Survives recreation until History consumes it.
        val restored = ShellViewModel(SavedStateHandle(mapOf(ShellViewModel.KEY_PENDING_TX to 42L)), Section.entries)
        assertThat(restored.pendingTransaction.value).isEqualTo(42L)
        restored.consumePendingTransaction()
        assertThat(restored.pendingTransaction.value).isNull()
    }

    @Test fun `navigation type follows the layout`() {
        val compact = AppLayout.Compact(411f, 658f)
        val book = AppLayout.Book(882f, 662f, 441f, 0f, SplitSource.Hinge)
        val tabletop = AppLayout.Tabletop(662f, 882f, 441f, 0f)
        assertThat(navigationTypeFor(compact, 6)).isEqualTo(NavigationSuiteType.ShortNavigationBarCompact)
        assertThat(navigationTypeFor(tabletop, 6)).isEqualTo(NavigationSuiteType.ShortNavigationBarCompact)
        assertThat(navigationTypeFor(book, 6)).isEqualTo(NavigationSuiteType.WideNavigationRailCollapsed)
        assertThat(navigationTypeFor(book, 1)).isEqualTo(NavigationSuiteType.None)
    }

    @Test fun `compact bar has five items without subscriptions, the rail has all six`() {
        val bar = barSections(Section.entries, compactBar = true)
        assertThat(bar).containsExactly(Section.Overview, Section.History, Section.Analytics, Section.Review, Section.Settings).inOrder()
        assertThat(bar.size).isAtMost(MaxCompactBarItems)
        assertThat(barSections(Section.entries, compactBar = false)).isEqualTo(Section.entries)
    }

    @Test fun `subscriptions selected on the inner screen stay selected after folding`() {
        // Selected on the rail; folding recreates nothing in the ViewModel, but the Compact bar has
        // no item for it — the selection must still resolve to Subscriptions, not fall back.
        val handle = SavedStateHandle()
        ShellViewModel(handle, Section.entries).select(Section.Subscriptions)
        val folded = ShellViewModel(SavedStateHandle(mapOf(ShellViewModel.KEY_SELECTED to handle.get<String>(ShellViewModel.KEY_SELECTED))), Section.entries)
        assertThat(folded.selected.value).isEqualTo(Section.Subscriptions)
        assertThat(Section.Subscriptions in barSections(folded.visibleSections, compactBar = true)).isFalse()
    }
}
