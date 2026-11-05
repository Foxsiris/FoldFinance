@file:OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)

package com.foldfinance.app.ui.history

import android.content.ClipDescription
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.MutableThreePaneScaffoldState
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldPaneScope
import androidx.compose.material3.adaptive.layout.calculateDefaultEnterTransition
import androidx.compose.material3.adaptive.layout.calculateDefaultExitTransition
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.CategoryChip
import com.foldfinance.app.ui.components.EmptyState
import com.foldfinance.app.ui.components.MoneyStyle
import com.foldfinance.app.ui.components.MoneyText
import com.foldfinance.app.ui.components.visual
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.layout.TwoPane
import com.foldfinance.app.ui.layout.foldPaneScaffoldDirective
import com.foldfinance.app.ui.shell.LocalShellNavigation
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldMotion
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.app.ui.transaction.DayHeader
import com.foldfinance.app.ui.transaction.FormMode
import com.foldfinance.app.ui.transaction.HingeSeamOverlay
import com.foldfinance.app.ui.transaction.LocalAppSnackbar
import com.foldfinance.app.ui.transaction.MerchantRuleDialog
import com.foldfinance.app.ui.transaction.SavedSnackbarEffect
import com.foldfinance.app.ui.transaction.TransactionDetail
import com.foldfinance.app.ui.transaction.TransactionDetailActions
import com.foldfinance.app.ui.transaction.TransactionDetailState
import com.foldfinance.app.ui.transaction.TransactionDragLabel
import com.foldfinance.app.ui.transaction.TransactionForm
import com.foldfinance.app.ui.transaction.TransactionFormActions
import com.foldfinance.app.ui.transaction.TransactionFormSheet
import com.foldfinance.app.ui.transaction.TransactionFormState
import com.foldfinance.app.ui.transaction.TransactionFormViewModel
import com.foldfinance.app.ui.transaction.TransactionListItem
import com.foldfinance.app.ui.transaction.dayTitle
import com.foldfinance.core.model.Category
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Key of the edit form of History (`hiltViewModel(key = …)`), separate from the app-wide add form. */
const val HistoryEditorKey = "history.editor"

/**
 * «История»: list-detail on [NavigableListDetailPaneScaffold] — two panes from Medium width with
 * the boundary on the hinge, one pane on the outer screen.
 *
 * Continuity: the selected operation is the content key of the saveable navigator, the filters
 * and the editors live in ViewModels with `SavedStateHandle`, the list scroll position is hoisted
 * here (outside the panes) and saveable. Folding with an open detail keeps the detail, «Назад»
 * then returns to the list; unfolding shows the list with the same operation on the right.
 */
@Composable
fun HistoryRoute(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
    editor: TransactionFormViewModel = hiltViewModel(key = HistoryEditorKey),
    addForm: TransactionFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rulePrompt by viewModel.rulePrompt.collectAsStateWithLifecycle()
    val editorState by editor.state.collectAsStateWithLifecycle()
    val addState by addForm.state.collectAsStateWithLifecycle()
    val navigator = rememberHistoryNavigator()
    val listState = rememberLazyListState()
    val snackbar = LocalAppSnackbar.current
    val scope = rememberCoroutineScope()
    val shell = LocalShellNavigation.current
    val deletedText = stringResource(R.string.tx_deleted)
    val undoText = stringResource(R.string.tx_undo)
    val ruleText = stringResource(R.string.tx_rule_applied)

    SavedSnackbarEffect(editor.formEvents)
    SavedSnackbarEffect(addForm.formEvents)

    // «Обзор» asked to open an operation here.
    LaunchedEffect(shell.pendingTransactionId) {
        val id = shell.pendingTransactionId ?: return@LaunchedEffect
        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id)
        shell.consumePendingTransaction()
    }

    val detailActions = remember(viewModel, editor, navigator, snackbar) {
        object : TransactionDetailActions {
            override fun setCategory(transactionId: Long, categoryId: Long?) = viewModel.setCategory(transactionId, categoryId)
            override fun confirm(transactionId: Long) = viewModel.confirm(transactionId)
            override fun edit(transactionId: Long) = editor.openEdit(transactionId)
            override fun delete(transactionId: Long) {
                viewModel.delete(transactionId)
                scope.launch { navigator.navigateBack(BackNavigationBehavior.PopLatest) }
                scope.launch {
                    val result = snackbar.showSnackbar(deletedText, actionLabel = undoText, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(transactionId)
                }
            }
        }
    }

    HistoryScreen(
        state = state,
        navigator = navigator,
        listState = listState,
        detail = { id -> remember(id) { viewModel.detail(id) }.collectAsStateWithLifecycle(null).value },
        filterActions = viewModel,
        detailActions = detailActions,
        editorState = editorState,
        editorActions = editor,
        addState = addState,
        addActions = addForm,
        onAdd = addForm::openAdd,
        onDropOnCategory = viewModel::dropOnCategory,
        now = viewModel.now(),
        zone = viewModel.zone,
        modifier = modifier,
    )

    rulePrompt?.let { prompt ->
        MerchantRuleDialog(
            prompt = prompt,
            onApply = {
                viewModel.applyRule()
                scope.launch { snackbar.showSnackbar(ruleText) }
            },
            onDismiss = viewModel::dismissRule,
        )
    }
}

/**
 * Directive of the History scaffold: [foldPaneScaffoldDirective] (two panes from Medium, boundary
 * on the hinge), but a single pane in tabletop — there the scaffold lives in the bottom half only.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun historyDirective(layout: AppLayout = LocalAppLayout.current): PaneScaffoldDirective {
    val base = foldPaneScaffoldDirective(layout)
    val tabletop = layout is AppLayout.Tabletop
    // No auto-focus of the destination pane: it would focus the search field of the list and pop
    // up the keyboard every time History opens or the window folds.
    return PaneScaffoldDirective(
        maxHorizontalPartitions = if (tabletop) 1 else base.maxHorizontalPartitions,
        horizontalPartitionSpacerSize = base.horizontalPartitionSpacerSize,
        maxVerticalPartitions = if (tabletop) 1 else base.maxVerticalPartitions,
        verticalPartitionSpacerSize = base.verticalPartitionSpacerSize,
        defaultPanePreferredWidth = base.defaultPanePreferredWidth,
        defaultPanePreferredHeight = base.defaultPanePreferredHeight,
        excludedBounds = if (tabletop) emptyList() else base.excludedBounds,
        shouldAutoFocusCurrentDestination = false,
    )
}

/**
 * The saveable list-detail navigator of History; the content key is the operation id.
 * @param initialSelection operation to start with (screenshots and tests); `null` — the list.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberHistoryNavigator(initialSelection: Long? = null): ThreePaneScaffoldNavigator<Long> =
    rememberListDetailPaneScaffoldNavigator(
        scaffoldDirective = historyDirective(),
        initialDestinationHistory = listOfNotNull(
            ThreePaneScaffoldDestinationItem<Long>(ListDetailPaneScaffoldRole.List),
            initialSelection?.let { ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.Detail, it) },
        ),
    )

/**
 * Stateless History screen around a [navigator]. [detail] resolves the operation of a content key
 * (a flow from the ViewModel in the app, fixed data in tests).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    navigator: ThreePaneScaffoldNavigator<Long>,
    listState: LazyListState,
    detail: @Composable (Long) -> TransactionDetailState?,
    filterActions: HistoryFilterActions,
    detailActions: TransactionDetailActions,
    editorState: TransactionFormState,
    editorActions: TransactionFormActions,
    addState: TransactionFormState,
    addActions: TransactionFormActions,
    onAdd: () -> Unit,
    onDropOnCategory: (transactionId: Long, categoryId: Long) -> Unit,
    now: Instant,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    layout: AppLayout = LocalAppLayout.current,
) {
    val scope = rememberCoroutineScope()
    val twoPane = layout is AppLayout.Book
    val reducedMotion = FoldFinanceTheme.reducedMotion
    val current = navigator.currentDestination
    val selectedId = current?.contentKey?.takeIf { current.pane == ListDetailPaneScaffoldRole.Detail }
    val categoriesById = remember(state.categories) { state.categories.associateBy { it.id } }

    // The navigator recomputes its value when the directive changes (fold/unfold, split-screen
    // resize), but the scaffold state it drives only moves on navigation. Bring it to the new value
    // at once, so unfolding with an open detail shows the list next to it (scenario 1) and folding
    // leaves the detail alone on the outer screen (scenario 2).
    LaunchedEffect(navigator, navigator.scaffoldDirective) {
        val scaffoldState = navigator.scaffoldState as? MutableThreePaneScaffoldState ?: return@LaunchedEffect
        if (scaffoldState.targetState != navigator.scaffoldValue) scaffoldState.snapTo(navigator.scaffoldValue)
    }

    val scaffoldBody: @Composable (Modifier) -> Unit = { scaffoldModifier ->
        SharedTransitionLayout(scaffoldModifier) {
            val shared = this
            NavigableListDetailPaneScaffold(
                navigator = navigator,
                listPane = {
                    HistoryAnimatedPane(reducedMotion) {
                        HistoryListPane(
                            state = state,
                            listState = listState,
                            categoriesById = categoriesById,
                            selectedId = if (twoPane) selectedId else null,
                            twoPane = twoPane,
                            filterActions = filterActions,
                            onOpen = { id -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) } },
                            onAdd = onAdd,
                            onDropOnCategory = onDropOnCategory,
                            now = now,
                            zone = zone,
                            sharedRow = { id -> sharedModifier(shared, this, "tx-$id", enabled = !twoPane && !reducedMotion) },
                        )
                    }
                },
                detailPane = {
                    HistoryAnimatedPane(reducedMotion) {
                        val visibility: AnimatedVisibilityScope = this
                        HistoryDetailPane(
                            contentKey = selectedId,
                            twoPane = twoPane,
                            detail = detail,
                            detailActions = detailActions,
                            editorState = editorState,
                            editorActions = editorActions,
                            addState = addState,
                            addActions = addActions,
                            now = now,
                            zone = zone,
                            onBack = { scope.launch { navigator.navigateBack(BackNavigationBehavior.PopUntilScaffoldValueChange) } },
                            sharedHeader = { id -> sharedModifier(shared, visibility, "tx-$id", enabled = !twoPane && !reducedMotion) },
                        )
                    }
                },
            )
        }
    }
    // The scaffold moves between the layouts below (alone, or the bottom pane of tabletop) as
    // *movable* content: its pane transitions and saveable pane state stay the same instance when
    // the window folds or unfolds, instead of being recreated at another call site.
    val latestBody by rememberUpdatedState(scaffoldBody)
    val scaffold = remember { movableContentOf { m: Modifier -> latestBody(m) } }

    when (layout) {
        is AppLayout.Tabletop -> TwoPane(
            modifier = modifier.fillMaxSize(),
            first = { HistorySummary(state, Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) },
            second = {
                if (addState.isOpen) {
                    TransactionForm(addState, addActions, now, zone, Modifier.fillMaxSize(), fillHeight = true)
                } else {
                    scaffold(Modifier.fillMaxSize())
                }
            },
        )
        is AppLayout.Book -> Box(modifier.fillMaxSize()) {
            scaffold(Modifier.fillMaxSize())
            HingeSeamOverlay(layout)
        }
        is AppLayout.Compact -> Box(modifier.fillMaxSize()) {
            scaffold(Modifier.fillMaxSize())
            TransactionFormSheet(addState, addActions, now, zone)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun sharedModifier(
    shared: SharedTransitionScope,
    visibility: AnimatedVisibilityScope,
    key: String,
    enabled: Boolean,
): Modifier = if (!enabled) {
    Modifier
} else {
    with(shared) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
        )
    }
}

/** Pane with the scaffold's default motion, or none under «Убрать анимацию». */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun ThreePaneScaffoldPaneScope.HistoryAnimatedPane(
    reducedMotion: Boolean,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedPane(
        enterTransition = if (reducedMotion) EnterTransition.None else motionDataProvider.calculateDefaultEnterTransition(paneRole),
        exitTransition = if (reducedMotion) ExitTransition.None else motionDataProvider.calculateDefaultExitTransition(paneRole),
        boundsAnimationSpec = if (reducedMotion) snap() else spring<IntRect>(FoldMotion.SpatialDamping, FoldMotion.SpatialStiffness),
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryListPane(
    state: HistoryUiState,
    listState: LazyListState,
    categoriesById: Map<Long, Category>,
    selectedId: Long?,
    twoPane: Boolean,
    filterActions: HistoryFilterActions,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onDropOnCategory: (Long, Long) -> Unit,
    now: Instant,
    zone: ZoneId,
    sharedRow: @Composable (Long) -> Modifier,
) {
    val palette = FoldFinanceTheme.palette
    val today = now.atZone(zone).toLocalDate()
    val sidePadding = 16.dp
    // In two panes the list ends on the hinge: keep 12 dp clear so nothing scrolls under the fold.
    val hingeClearance = if (twoPane) 12.dp else 0.dp
    Box(Modifier.fillMaxSize().padding(end = hingeClearance).clipToBounds().testTag("history.list")) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.section_history),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
                color = palette.text,
            )
            HistoryFilterBar(
                filter = state.filter,
                categories = state.categories,
                actions = filterActions,
                showCategoryPill = !twoPane,
                contentPadding = PaddingValues(horizontal = sidePadding),
                currency = state.currency,
            )
            if (twoPane && state.categories.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                CategoryDropStrip(state.categories, state.filter, filterActions, onDropOnCategory, PaddingValues(horizontal = sidePadding))
            }
            Spacer(Modifier.height(4.dp))
            when {
                state.loading -> Unit
                state.isEmpty -> Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
                    if (state.filter.isNarrowed) {
                        EmptyState(
                            title = stringResource(R.string.history_nothing_found_title),
                            body = stringResource(R.string.history_nothing_found_body),
                            icon = Icons.Outlined.SearchOff,
                            actionLabel = stringResource(R.string.history_filter_reset),
                            onAction = filterActions::resetFilters,
                        )
                    } else {
                        EmptyState(
                            title = stringResource(R.string.history_empty_title),
                            body = stringResource(R.string.history_empty_body),
                            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                            actionLabel = stringResource(R.string.tx_add),
                            onAction = onAdd,
                        )
                    }
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = sidePadding, end = sidePadding, bottom = 96.dp),
                ) {
                    state.sections.forEach { section ->
                        val date = section.date
                        if (date != null) {
                            stickyHeader(key = "h-${date.toEpochDay()}", contentType = "header") {
                                DayHeader(dayTitle(date, today), section.netExpenseMinor, section.currency)
                            }
                        } else {
                            item(key = "h-sorted", contentType = "header") { Spacer(Modifier.height(8.dp)) }
                        }
                        itemsIndexed(section.items, key = { _, tx -> tx.id }, contentType = { _, _ -> "row" }) { index, tx ->
                            TransactionListItem(
                                transaction = tx,
                                category = tx.categoryId?.let(categoriesById::get),
                                zone = zone,
                                index = index,
                                groupSize = section.items.size,
                                selected = tx.id == selectedId,
                                draggable = twoPane,
                                compactBadge = twoPane,
                                rowModifier = sharedRow(tx.id),
                                onClick = { onOpen(tx.id) },
                            )
                        }
                    }
                }
            }
        }
        if (!state.isEmpty) {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.tx_add)) },
                shape = FoldShapes.fab,
                containerColor = palette.saffron,
                contentColor = palette.onSaffron,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

/**
 * Category chips of the inner screen: a tap filters the list, dropping an operation onto a chip
 * moves it to that category (drag & drop from a row, long press).
 */
@Composable
private fun CategoryDropStrip(
    categories: List<Category>,
    filter: HistoryFilter,
    actions: HistoryFilterActions,
    onDrop: (Long, Long) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyRow(contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories, key = { it.id }) { category ->
            CategoryDropChip(category, selected = category.id in filter.categoryIds, onClick = { actions.toggleCategoryFilter(category.id) }) { txId ->
                onDrop(txId, category.id)
            }
        }
    }
}

@Composable
private fun CategoryDropChip(category: Category, selected: Boolean, onClick: () -> Unit, onDrop: (Long) -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    val currentOnDrop by rememberUpdatedState(onDrop)
    val target = remember(category.id) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                hovered = true
            }

            override fun onExited(event: DragAndDropEvent) {
                hovered = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                hovered = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovered = false
                val clip = event.toAndroidDragEvent().clipData ?: return false
                if (clip.description?.label != TransactionDragLabel || clip.itemCount == 0) return false
                val id = clip.getItemAt(0).text?.toString()?.toLongOrNull() ?: return false
                currentOnDrop(id)
                return true
            }
        }
    }
    val visual = category.visual()
    CategoryChip(
        name = visual.name,
        color = visual.color,
        selected = selected || hovered,
        onClick = onClick,
        modifier = Modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) },
            target = target,
        ),
    )
}

@Composable
private fun HistoryDetailPane(
    contentKey: Long?,
    twoPane: Boolean,
    detail: @Composable (Long) -> TransactionDetailState?,
    detailActions: TransactionDetailActions,
    editorState: TransactionFormState,
    editorActions: TransactionFormActions,
    addState: TransactionFormState,
    addActions: TransactionFormActions,
    now: Instant,
    zone: ZoneId,
    onBack: () -> Unit,
    sharedHeader: @Composable (Long) -> Modifier,
) {
    val reducedMotion = FoldFinanceTheme.reducedMotion
    val editing = contentKey != null && editorState.mode == FormMode.Edit(contentKey)
    val content = when {
        twoPane && addState.isOpen -> DetailContent.Add
        editing -> DetailContent.Edit
        contentKey != null -> DetailContent.Detail
        else -> DetailContent.Hint
    }
    BackHandler(enabled = editing) { editorActions.close() }
    BackHandler(enabled = content == DetailContent.Add) { addActions.close() }
    // Within the pane, switching between detail, editor and the add form fades (instant under reduced motion).
    AnimatedContent(
        targetState = content to contentKey,
        transitionSpec = { fadeIn(FoldMotion.effects(reducedMotion)) togetherWith fadeOut(FoldMotion.effects(reducedMotion)) },
        label = "historyDetail",
        modifier = Modifier.fillMaxSize().testTag("history.detail"),
    ) { (shown, key) ->
        val padding = if (twoPane) PaddingValues(start = 16.dp, end = 24.dp, top = 8.dp, bottom = 8.dp) else PaddingValues(horizontal = 20.dp, vertical = 8.dp)
        when (shown) {
            DetailContent.Add -> TransactionForm(addState, addActions, now, zone, Modifier.fillMaxSize(), fillHeight = true, contentPadding = padding)
            DetailContent.Edit -> TransactionForm(editorState, editorActions, now, zone, Modifier.fillMaxSize(), fillHeight = true, contentPadding = padding)
            DetailContent.Detail -> {
                val data = key?.let { detail(it) }
                if (data != null) {
                    TransactionDetail(
                        state = data,
                        actions = detailActions,
                        now = now,
                        zone = zone,
                        onBack = if (twoPane) null else onBack,
                        headerModifier = sharedHeader(data.transaction.id),
                        contentPadding = padding,
                    )
                } else {
                    Box(Modifier.fillMaxSize())
                }
            }
            DetailContent.Hint -> Box(Modifier.fillMaxSize().padding(padding).padding(top = 72.dp)) {
                EmptyState(
                    title = stringResource(R.string.tx_select_hint_title),
                    body = stringResource(R.string.tx_select_hint_body),
                )
            }
        }
    }
}

private enum class DetailContent { Hint, Detail, Edit, Add }

/** Top half of tabletop: what the current filter adds up to — to look at, nothing to touch. */
@Composable
private fun HistorySummary(state: HistoryUiState, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    Column(modifier, verticalArrangement = Arrangement.Bottom) {
        Text(
            text = stringResource(R.string.section_history),
            style = MaterialTheme.typography.headlineLarge,
            color = palette.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = periodLabel(state.filter.period),
            style = MaterialTheme.typography.bodyLarge,
            color = palette.textMuted,
        )
        Spacer(Modifier.weight(1f))
        MoneyText(amountMinor = state.expenseMinor, currency = state.currency, style = MoneyStyle.Display)
        Text(stringResource(R.string.history_summary_expense), style = MaterialTheme.typography.bodyLarge, color = palette.textMuted)
        if (state.incomeMinor > 0) {
            Spacer(Modifier.height(12.dp))
            MoneyText(
                amountMinor = state.incomeMinor,
                currency = state.currency,
                direction = com.foldfinance.parser.Direction.INCOME,
                style = MoneyStyle.DisplaySmall,
            )
            Text(stringResource(R.string.history_summary_income), style = MaterialTheme.typography.bodyLarge, color = palette.textMuted)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = pluralStringResource(R.plurals.history_count, state.count, state.count),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textMuted,
        )
    }
}
