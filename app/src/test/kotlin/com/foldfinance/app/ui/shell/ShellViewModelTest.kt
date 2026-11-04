package com.foldfinance.app.ui.shell

import androidx.lifecycle.SavedStateHandle
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.SplitSource
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {

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

    @Test fun `release shows only ready sections, debug shows all`() {
        assertThat(Sections.visible(debug = false)).containsExactly(Section.Overview)
        assertThat(Sections.visible(debug = true)).containsExactlyElementsIn(Section.entries).inOrder()
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
}
