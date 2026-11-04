@file:OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)

package com.foldfinance.app.ui.history

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foldfinance.app.DeviceConfigs
import com.foldfinance.app.ScreenshotClock
import com.foldfinance.app.ui.fakes.FakeCategoryRepository
import com.foldfinance.app.ui.fakes.FakeMerchantRuleRepository
import com.foldfinance.app.ui.fakes.NoopDetailActions
import com.foldfinance.app.ui.fakes.NoopFormActions
import com.foldfinance.app.ui.fakes.SampleData
import com.foldfinance.app.ui.fakes.ViewModelHarness
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.transaction.TransactionDetailState
import com.foldfinance.app.ui.transaction.TransactionFormState
import com.foldfinance.app.ui.transaction.observeDetail
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The two fold scenarios of CLAUDE.md on the real History screen and its saveable navigator,
 * switching [LocalAppLayout] the way `MainActivity` does when the window changes:
 *
 * 1. open an operation on the outer screen → unfold → list and that operation on the right;
 * 2. fold with the detail open → detail on the outer screen, «Назад» leads to the list;
 *
 * plus activity recreation («Не сохранять действия») via [StateRestorationTester].
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w882dp-h662dp-land-xhdpi")
class HistoryContinuityTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val harness = ViewModelHarness()
    private lateinit var state: HistoryUiState
    private lateinit var details: Map<Long, TransactionDetailState>

    @Before fun setUp() {
        harness.install()
        val repo = SampleData.transactionRepository()
        val vm = HistoryViewModel(SavedStateHandle(), repo, FakeCategoryRepository(), FakeMerchantRuleRepository(), ScreenshotClock)
        state = harness.snapshot(vm.state)
        details = state.sections.flatMap { it.items }.associate { tx ->
            tx.id to requireNotNull(harness.first(repo.observeDetail(tx.id, FakeCategoryRepository())))
        }
        harness.reset()
    }

    @After fun tearDown() = harness.reset()

    @Composable
    private fun History(layout: AppLayout) {
        CompositionLocalProvider(LocalAppLayout provides layout) {
            FoldFinanceTheme(darkTheme = false, reducedMotion = true) {
                HistoryScreen(
                    state = state,
                    navigator = rememberHistoryNavigator(),
                    listState = rememberLazyListState(),
                    detail = { id -> details[id] },
                    filterActions = NoopFilterActions,
                    detailActions = NoopDetailActions,
                    editorState = TransactionFormState(),
                    editorActions = NoopFormActions,
                    addState = TransactionFormState(),
                    addActions = NoopFormActions,
                    onAdd = {},
                    onDropOnCategory = { _, _ -> },
                    now = ScreenshotClock.instant(),
                    zone = ScreenshotClock.zone,
                    layout = layout,
                )
            }
        }
    }

    private fun detailShows(merchant: String) {
        // The raw bank message is only in the detail pane.
        rule.onNodeWithText("Исходное сообщение").assertIsDisplayed()
        rule.onAllNodesWithText(merchant).fetchSemanticsNodes().let { check(it.isNotEmpty()) }
        rule.onNodeWithContentDescription("Изменить").assertIsDisplayed()
    }

    private fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test fun `scenario 1 and 2 — selection follows folding, back returns to the list`() {
        var layout by mutableStateOf<AppLayout>(DeviceConfigs.Outer.layout)
        rule.setContent { History(layout) }

        // Outer screen: open «ВкусВилл»; the detail covers the list.
        rule.onNodeWithText("ВкусВилл").performClick()
        rule.waitForIdle()
        detailShows("ВкусВилл")
        rule.onNodeWithText("История").assertDoesNotExist()

        // Unfold: the list is back on the left, the same operation stays on the right.
        layout = DeviceConfigs.Inner.layout
        rule.waitForIdle()
        rule.onNodeWithTag("history.list").assertIsDisplayed()
        rule.onNodeWithText("История").assertIsDisplayed()
        detailShows("ВкусВилл")

        // Fold again: the detail is on the outer screen…
        layout = DeviceConfigs.Outer.layout
        rule.waitForIdle()
        detailShows("ВкусВилл")
        rule.onNodeWithText("История").assertDoesNotExist()

        // …and «Назад» leads to the list.
        pressBack()
        rule.onNodeWithText("История").assertIsDisplayed()
        rule.onNodeWithText("Исходное сообщение").assertDoesNotExist()
    }

    @Test fun `opened on the inner screen, folded — detail on the outer screen, back to list`() {
        var layout by mutableStateOf<AppLayout>(DeviceConfigs.Inner.layout)
        rule.setContent { History(layout) }
        rule.onNodeWithText("Пятёрочка").performClick()
        rule.waitForIdle()
        detailShows("Пятёрочка")

        layout = DeviceConfigs.Outer.layout
        rule.waitForIdle()
        detailShows("Пятёрочка")
        rule.onNodeWithText("История").assertDoesNotExist()
        pressBack()
        rule.onNodeWithText("История").assertIsDisplayed()
    }

    @Test fun `activity recreation keeps the open detail and its back stack`() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { History(DeviceConfigs.Outer.layout) }
        rule.onNodeWithText("ВкусВилл").performClick()
        rule.waitForIdle()
        detailShows("ВкусВилл")

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        detailShows("ВкусВилл")

        pressBack()
        rule.onNodeWithText("История").assertIsDisplayed()
    }
}

private object NoopFilterActions : HistoryFilterActions {
    override fun setQuery(query: String) = Unit
    override fun setPeriod(period: HistoryPeriod) = Unit
    override fun setSort(sort: com.foldfinance.core.data.repository.TransactionSort) = Unit
    override fun toggleDirection(direction: com.foldfinance.parser.Direction) = Unit
    override fun toggleCategoryFilter(categoryId: Long) = Unit
    override fun toggleUncategorized() = Unit
    override fun clearCategoryFilter() = Unit
    override fun setAmountRange(min: Long?, max: Long?) = Unit
    override fun resetFilters() = Unit
}
