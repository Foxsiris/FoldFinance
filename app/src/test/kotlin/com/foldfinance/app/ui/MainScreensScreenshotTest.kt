@file:OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)

package com.foldfinance.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foldfinance.app.DeviceConfigs
import com.foldfinance.app.ScreenshotClock
import com.foldfinance.app.captureInAllConfigs
import com.foldfinance.app.ui.components.Period
import com.foldfinance.app.ui.fakes.FakeBalanceRepository
import com.foldfinance.app.ui.fakes.FakeCategoryRepository
import com.foldfinance.app.ui.fakes.FakeMerchantRuleRepository
import com.foldfinance.app.ui.fakes.FakeTransactionRepository
import com.foldfinance.app.ui.fakes.NoopDetailActions
import com.foldfinance.app.ui.fakes.NoopFormActions
import com.foldfinance.app.ui.fakes.SampleData
import com.foldfinance.app.ui.fakes.fakePreferences
import com.foldfinance.app.ui.analytics.FakeSubscriptionRepository
import com.foldfinance.app.ui.fakes.ViewModelHarness
import com.foldfinance.app.ui.history.HistoryFilterActions
import com.foldfinance.app.ui.history.HistoryPeriod
import com.foldfinance.app.ui.history.HistoryScreen
import com.foldfinance.app.ui.history.HistoryUiState
import com.foldfinance.app.ui.history.HistoryViewModel
import com.foldfinance.app.ui.history.rememberHistoryNavigator
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.overview.CategorySelection
import com.foldfinance.app.ui.overview.OverviewActions
import com.foldfinance.app.ui.overview.OverviewScreen
import com.foldfinance.app.ui.overview.OverviewUiState
import com.foldfinance.app.ui.overview.OverviewViewModel
import com.foldfinance.app.ui.review.ReviewActions
import com.foldfinance.app.ui.review.ReviewScreen
import com.foldfinance.app.ui.review.ReviewUiState
import com.foldfinance.app.ui.review.ReviewViewModel
import com.foldfinance.app.ui.shell.Section
import com.foldfinance.app.ui.shell.ShellScaffold
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.app.ui.transaction.FormMode
import com.foldfinance.app.ui.transaction.TransactionDetailState
import com.foldfinance.app.ui.transaction.TransactionDraft
import com.foldfinance.app.ui.transaction.TransactionForm
import com.foldfinance.app.ui.transaction.TransactionFormState
import com.foldfinance.app.ui.transaction.observeDetail
import com.foldfinance.core.data.repository.TransactionSort
import com.foldfinance.parser.Direction
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Main screens inside the real shell (bar / rail, «на проверку» badge) in
 * 3 Fold configurations × light/dark, on [SampleData] run through the real ViewModels.
 * Record: `recordRoborazziDebug`, check: `verifyRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainScreensScreenshotTest {
    private val harness = ViewModelHarness()
    private val now = ScreenshotClock.instant()
    private val zone = ScreenshotClock.zone

    @Before fun setUp() = harness.install()

    @After fun tearDown() = harness.reset()

    @Test fun overview() {
        val state = overviewState(SampleData.transactionRepository())
        captureInAllConfigs("overview", configs = DeviceConfigs.withNarrow) {
            Shell(Section.Overview) { m -> OverviewScreen(state, closedForm, NoopFormActions, NoopOverview, now, zone, m) }
        }
    }

    /** Book: a tap on «Продукты» on the left filters the list on the right. */
    @Test fun overviewFiltered() {
        val handle = SavedStateHandle(mapOf(OverviewViewModel.KEY_CATEGORY to SampleData.categoryId("groceries")))
        val state = overviewState(SampleData.transactionRepository(), handle)
        captureInAllConfigs("overview_filtered", configs = listOf(com.foldfinance.app.DeviceConfigs.Inner)) {
            Shell(Section.Overview) { m -> OverviewScreen(state, closedForm, NoopFormActions, NoopOverview, now, zone, m) }
        }
    }

    @Test fun overviewEmpty() {
        val state = overviewState(FakeTransactionRepository())
        captureInAllConfigs("overview_empty") {
            Shell(Section.Overview, badge = 0) { m -> OverviewScreen(state, closedForm, NoopFormActions, NoopOverview, now, zone, m) }
        }
    }

    @Test fun historyList() {
        val state = historyState()
        captureInAllConfigs("history_list", configs = DeviceConfigs.withNarrow) {
            Shell(Section.History) { m ->
                HistoryScreen(
                    state = state,
                    navigator = rememberHistoryNavigator(),
                    listState = rememberLazyListState(),
                    detail = { null },
                    filterActions = NoopFilters,
                    detailActions = NoopDetailActions,
                    editorState = closedForm,
                    editorActions = NoopFormActions,
                    addState = closedForm,
                    addActions = NoopFormActions,
                    onAdd = {},
                    onDropOnCategory = { _, _ -> },
                    now = now,
                    zone = zone,
                    modifier = m,
                )
            }
        }
    }

    @Test fun historyDetail() {
        val state = historyState()
        val details = detailsOf(1L, 8L)
        captureInAllConfigs("history_detail", configs = DeviceConfigs.all + DeviceConfigs.InnerPortrait) {
            Shell(Section.History) { m ->
                HistoryScreen(
                    state = state,
                    navigator = rememberHistoryNavigator(initialSelection = 1L),
                    listState = rememberLazyListState(),
                    detail = { id -> details[id] },
                    filterActions = NoopFilters,
                    detailActions = NoopDetailActions,
                    editorState = closedForm,
                    editorActions = NoopFormActions,
                    addState = closedForm,
                    addActions = NoopFormActions,
                    onAdd = {},
                    onDropOnCategory = { _, _ -> },
                    now = now,
                    zone = zone,
                    modifier = m,
                )
            }
        }
    }

    /** A filtered, narrowed list: search + type + period pills active. */
    @Test fun historyFiltered() {
        val handle = SavedStateHandle()
        val vm = historyViewModel(handle)
        vm.setPeriod(HistoryPeriod.MONTH)
        vm.toggleDirection(Direction.EXPENSE)
        vm.setSort(TransactionSort.AMOUNT_DESC)
        val state = harness.snapshot(vm.state)
        captureInAllConfigs("history_filtered", configs = listOf(com.foldfinance.app.DeviceConfigs.Outer)) {
            Shell(Section.History) { m ->
                HistoryScreen(
                    state, rememberHistoryNavigator(), rememberLazyListState(), { null }, NoopFilters, NoopDetailActions,
                    closedForm, NoopFormActions, closedForm, NoopFormActions, {}, { _, _ -> }, now, zone, m,
                )
            }
        }
    }

    @Test fun add() {
        val overview = overviewState(SampleData.transactionRepository())
        val form = TransactionFormState(
            mode = FormMode.Add,
            draft = TransactionDraft(amountText = "1240,5", categoryId = SampleData.categoryId("groceries"), merchant = "Пятёрочка"),
            categories = SampleData.categories,
        )
        captureInAllConfigs("add") {
            Shell(Section.Overview) { m ->
                if (layout is AppLayout.Compact) {
                    // The modal sheet renders in its own window, which a view capture does not see:
                    // draw the same form on a sheet surface over the dimmed screen.
                    Box(m) {
                        OverviewScreen(overview, closedForm, NoopFormActions, NoopOverview, now, zone, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                            shape = FoldShapes.sheet,
                            color = com.foldfinance.app.ui.theme.FoldFinanceTheme.palette.surface,
                        ) {
                            TransactionForm(form, NoopFormActions, now, zone, Modifier.padding(top = 12.dp))
                        }
                    }
                } else {
                    OverviewScreen(overview, form, NoopFormActions, NoopOverview, now, zone, m)
                }
            }
        }
    }

    @Test fun review() {
        val vm = ReviewViewModel(SavedStateHandle(), SampleData.transactionRepository(), FakeCategoryRepository(), FakeMerchantRuleRepository(), ScreenshotClock)
        val state = harness.snapshot(vm.state)
        val details = detailsOf(*state.queue.map { it.id }.toLongArray())
        captureInAllConfigs("review") {
            Shell(Section.Review) { m ->
                ReviewScreen(state, NoopReview, { id -> details[id] }, NoopDetailActions, closedForm, NoopFormActions, now, zone, m)
            }
        }
    }

    /** Book with a multi-selection: the bulk category panel on the right. */
    @Test fun reviewBulk() {
        val handle = SavedStateHandle(mapOf(ReviewViewModel.KEY_SELECTED to longArrayOf(2L, 8L)))
        val vm = ReviewViewModel(handle, SampleData.transactionRepository(), FakeCategoryRepository(), FakeMerchantRuleRepository(), ScreenshotClock)
        val state: ReviewUiState = harness.snapshot(vm.state)
        captureInAllConfigs("review_bulk", configs = listOf(com.foldfinance.app.DeviceConfigs.Inner)) {
            Shell(Section.Review) { m ->
                ReviewScreen(state, NoopReview, { null }, NoopDetailActions, closedForm, NoopFormActions, now, zone, m)
            }
        }
    }

    @Test fun reviewDone() {
        val vm = ReviewViewModel(SavedStateHandle(), FakeTransactionRepository(), FakeCategoryRepository(), FakeMerchantRuleRepository(), ScreenshotClock)
        val state = harness.snapshot(vm.state)
        captureInAllConfigs("review_done", configs = listOf(com.foldfinance.app.DeviceConfigs.Outer)) {
            Shell(Section.Review, badge = 0) { m ->
                ReviewScreen(state, NoopReview, { null }, NoopDetailActions, closedForm, NoopFormActions, now, zone, m)
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private val closedForm = TransactionFormState(categories = SampleData.categories)

    private fun overviewState(repo: FakeTransactionRepository, handle: SavedStateHandle = SavedStateHandle()): OverviewUiState {
        val vm = OverviewViewModel(
            handle, repo, FakeCategoryRepository(), FakeBalanceRepository(if (repo.items.value.isEmpty()) emptyList() else SampleData.balances),
            FakeSubscriptionRepository(if (repo.items.value.isEmpty()) emptyList() else SampleData.subscriptions), fakePreferences(), ScreenshotClock,
        )
        return harness.snapshot(vm.state)
    }

    private fun historyViewModel(handle: SavedStateHandle = SavedStateHandle()) =
        HistoryViewModel(handle, SampleData.transactionRepository(), FakeCategoryRepository(), FakeMerchantRuleRepository(), fakePreferences(), ScreenshotClock)

    private fun historyState(): HistoryUiState = harness.snapshot(historyViewModel().state)

    private fun detailsOf(vararg ids: Long): Map<Long, TransactionDetailState> {
        val repo = SampleData.transactionRepository()
        val categories = FakeCategoryRepository()
        return ids.associateWith { id -> requireNotNull(harness.first(repo.observeDetail(id, categories))) }
    }
}

/** The real shell around a screen: sections, selection, the review badge. */
@Composable
internal fun Shell(section: Section, badge: Int = 3, content: @Composable (Modifier) -> Unit) {
    ShellScaffold(
        sections = Section.entries,
        selected = section,
        onSelect = {},
        badges = mapOf(Section.Review to badge),
        content = { _, m -> content(m) },
    )
}

private object NoopOverview : OverviewActions {
    override fun setPeriod(period: Period) = Unit
    override fun toggleCategory(selection: CategorySelection) = Unit
    override fun toggleBar(key: Long) = Unit
    override fun clearSelection() = Unit
    override fun openTransaction(transactionId: Long) = Unit
    override fun openReview() = Unit
    override fun openHistory() = Unit
    override fun openSubscriptions() = Unit
    override fun add() = Unit
}

private object NoopFilters : HistoryFilterActions {
    override fun setQuery(query: String) = Unit
    override fun setPeriod(period: HistoryPeriod) = Unit
    override fun setSort(sort: TransactionSort) = Unit
    override fun toggleDirection(direction: Direction) = Unit
    override fun toggleCategoryFilter(categoryId: Long) = Unit
    override fun toggleUncategorized() = Unit
    override fun clearCategoryFilter() = Unit
    override fun setAmountRange(min: Long?, max: Long?) = Unit
    override fun resetFilters() = Unit
}

private object NoopReview : ReviewActions {
    override fun confirm(id: Long) = Unit
    override fun confirmSelected() = Unit
    override fun assign(ids: Collection<Long>, categoryId: Long) = Unit
    override fun assignToSelected(categoryId: Long) = Unit
    override fun toggleSelected(id: Long) = Unit
    override fun selectAll() = Unit
    override fun clearSelection() = Unit
    override fun focus(id: Long) = Unit
    override fun openPicker(id: Long) = Unit
    override fun closePicker() = Unit
}
