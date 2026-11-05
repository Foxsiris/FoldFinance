package com.foldfinance.app.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.foldfinance.app.ui.components.ChipStyle
import com.foldfinance.app.ui.components.Panel
import com.foldfinance.app.ui.components.PillTag
import com.foldfinance.app.ui.components.PrimaryButton
import com.foldfinance.app.ui.components.ScreenTitle
import com.foldfinance.app.ui.theme.FoldTextStyles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.CategoryChip
import com.foldfinance.app.ui.components.EmptyState
import com.foldfinance.app.ui.components.ListGroupDivider
import com.foldfinance.app.ui.components.TransactionRow
import com.foldfinance.app.ui.components.visual
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.layout.TwoPane
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.app.ui.transaction.FormEvent
import com.foldfinance.app.ui.transaction.FormMode
import com.foldfinance.app.ui.transaction.LocalAppSnackbar
import com.foldfinance.app.ui.transaction.MerchantRuleDialog
import com.foldfinance.app.ui.transaction.TransactionDetail
import com.foldfinance.app.ui.transaction.TransactionDetailActions
import com.foldfinance.app.ui.transaction.TransactionDetailState
import com.foldfinance.app.ui.transaction.TransactionForm
import com.foldfinance.app.ui.transaction.TransactionFormActions
import com.foldfinance.app.ui.transaction.TransactionFormSheet
import com.foldfinance.app.ui.transaction.TransactionFormState
import com.foldfinance.app.ui.transaction.TransactionFormViewModel
import com.foldfinance.app.ui.transaction.categoryVisualOrNone
import com.foldfinance.app.ui.transaction.formatTime
import com.foldfinance.app.ui.transaction.groupItemShape
import com.foldfinance.app.ui.transaction.title
import com.foldfinance.core.model.Category
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Key of the edit form of Review. */
const val ReviewEditorKey = "review.editor"

/** Callbacks of [ReviewScreen]. */
interface ReviewActions {
    fun confirm(id: Long)
    fun confirmSelected()
    fun assign(ids: Collection<Long>, categoryId: Long)
    fun assignToSelected(categoryId: Long)
    fun toggleSelected(id: Long)
    fun selectAll()
    fun clearSelection()
    fun focus(id: Long)
    fun openPicker(id: Long)
    fun closePicker()

    /** «Пропустить»: the operation goes to the end of the queue (default: no-op for previews). */
    fun skip(id: Long) {}

    /** «Не распознано» (defaults: no-ops, so previews and tests need not implement them). */
    fun openUnrecognized() {}
    fun closeUnrecognized() {}
    fun hideUnrecognized(rawMessageId: Long) {}
    fun addUnrecognizedManually(rawMessageId: Long) {}
}

/**
 * «Проверка» — the queue of operations recognised without certainty.
 *
 * - Outer screen: one card at a time; swipe right confirms, swipe left opens the category sheet;
 *   both are also buttons in the thumb zone.
 * - Book: the queue with checkboxes on the left, the editor of the focused operation (or the bulk
 *   panel for a multi-selection) on the right.
 * - Tabletop: the card on top, the categories and «Подтвердить» below.
 * - Everywhere: «Не распознано» under the header when stored bank messages gave no operation —
 *   a sheet with their texts, «Скрыть» and «Добавить вручную» (the add form opens as a sheet with
 *   the text in the note; the saved operation gets the message attached).
 */
@Composable
fun ReviewRoute(
    modifier: Modifier = Modifier,
    viewModel: ReviewViewModel = hiltViewModel(),
    editor: TransactionFormViewModel = hiltViewModel(key = ReviewEditorKey),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rulePrompt by viewModel.rulePrompt.collectAsStateWithLifecycle()
    val editorState by editor.state.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    val scope = rememberCoroutineScope()
    val deletedText = stringResource(R.string.tx_deleted)
    val undoText = stringResource(R.string.tx_undo)
    val ruleText = stringResource(R.string.tx_rule_applied)
    val savedText = stringResource(R.string.tx_saved)
    // Like SavedSnackbarEffect, plus: an operation added from «Не распознано» takes the message along.
    LaunchedEffect(editor, snackbar) {
        editor.formEvents.collect { event ->
            if (event is FormEvent.Saved) {
                if (event.added) viewModel.onManualAdded(event.transactionId)
                snackbar.showSnackbar(savedText)
            }
        }
    }

    val actions = remember(viewModel, editor) {
        object : ReviewActions {
            override fun confirm(id: Long) = viewModel.confirm(id)
            override fun confirmSelected() = viewModel.confirmSelected()
            override fun assign(ids: Collection<Long>, categoryId: Long) = viewModel.assignCategory(ids, categoryId)
            override fun assignToSelected(categoryId: Long) = viewModel.assignToSelected(categoryId)
            override fun toggleSelected(id: Long) = viewModel.toggleSelected(id)
            override fun selectAll() = viewModel.selectAll()
            override fun clearSelection() = viewModel.clearSelection()
            override fun focus(id: Long) = viewModel.focus(id)
            override fun openPicker(id: Long) = viewModel.openPicker(id)
            override fun closePicker() = viewModel.closePicker()
            override fun skip(id: Long) = viewModel.skip(id)
            override fun openUnrecognized() = viewModel.openUnrecognized()
            override fun closeUnrecognized() = viewModel.closeUnrecognized()
            override fun hideUnrecognized(rawMessageId: Long) = viewModel.hideUnrecognized(rawMessageId)
            override fun addUnrecognizedManually(rawMessageId: Long) {
                val message = viewModel.startManualAdd(rawMessageId) ?: return
                editor.close()
                editor.openAdd()
                editor.setNote(message.text)
                editor.setOccurredAt(message.receivedAt)
            }
        }
    }
    val detailActions = remember(viewModel, editor, snackbar) {
        object : TransactionDetailActions {
            override fun setCategory(transactionId: Long, categoryId: Long?) = viewModel.setCategory(transactionId, categoryId)
            override fun confirm(transactionId: Long) = viewModel.confirm(transactionId)
            override fun edit(transactionId: Long) = editor.openEdit(transactionId)
            override fun delete(transactionId: Long) {
                viewModel.delete(transactionId)
                scope.launch {
                    val result = snackbar.showSnackbar(deletedText, actionLabel = undoText, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(transactionId)
                }
            }
        }
    }

    ReviewScreen(
        state = state,
        actions = actions,
        detail = { id -> remember(id) { viewModel.detail(id) }.collectAsStateWithLifecycle(null).value },
        detailActions = detailActions,
        editorState = editorState,
        editorActions = editor,
        now = viewModel.now(),
        zone = viewModel.zone,
        modifier = modifier,
    )

    // Manual add from «Не распознано» (edit forms are hosted inside the screen).
    if (editorState.mode == FormMode.Add) TransactionFormSheet(editorState, editor, viewModel.now(), viewModel.zone)

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

@Composable
fun ReviewScreen(
    state: ReviewUiState,
    actions: ReviewActions,
    detail: @Composable (Long) -> TransactionDetailState?,
    detailActions: TransactionDetailActions,
    editorState: TransactionFormState,
    editorActions: TransactionFormActions,
    now: Instant,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    layout: AppLayout = LocalAppLayout.current,
) {
    if (state.loading) {
        Box(modifier.fillMaxSize())
        return
    }
    UnrecognizedSheet(state, actions, now, zone)
    when (layout) {
        is AppLayout.Compact -> CompactReview(state, actions, detail, now, zone, modifier)
        is AppLayout.Tabletop -> TwoPane(
            modifier = modifier.fillMaxSize(),
            first = {
                Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp)) {
                    ReviewHeader(state.queue.size)
                    UnrecognizedEntry(state, actions)
                    Spacer(Modifier.weight(1f))
                    if (state.current == null) {
                        AllDone()
                    } else {
                        CardStack(state, actions, detail, now, zone, quickPicks = false)
                    }
                    Spacer(Modifier.weight(1f))
                }
            },
            second = {
                Panel(Modifier.fillMaxSize(), shape = FoldShapes.sheet) {
                    val current = state.current
                    if (current != null) {
                        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
                            Column(
                                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text(stringResource(R.string.review_choose_category), style = MaterialTheme.typography.titleMedium, color = FoldFinanceTheme.palette.text)
                                CategoryGrid(state.categories, current.categoryId) { actions.assign(listOf(current.id), it) }
                            }
                            // The round actions at the very bottom: the thumb zone of the touch half.
                            ThumbActions(
                                onCategory = { actions.openPicker(current.id) },
                                onSkip = if (state.queue.size > 1) ({ actions.skip(current.id) }) else null,
                                onConfirm = { actions.confirm(current.id) },
                                showCategory = false,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    } else {
                        Box(Modifier.fillMaxSize().padding(24.dp))
                    }
                }
            },
        )
        is AppLayout.Book -> TwoPane(
            modifier = modifier.fillMaxSize().padding(top = 16.dp, bottom = 16.dp, end = 8.dp),
            first = {
                Panel(Modifier.fillMaxSize()) { QueueList(state, actions, zone, Modifier.fillMaxSize()) }
            },
            second = {
                Panel(Modifier.fillMaxSize().testTag("review.editor")) {
                    val focused = state.focused
                    val editing = focused != null && editorState.mode == FormMode.Edit(focused.id)
                    BackHandler(enabled = editing) { editorActions.close() }
                    val padding = PaddingValues(horizontal = 24.dp, vertical = 20.dp)
                    when {
                        state.selectedIds.size > 1 || (state.selectedIds.isNotEmpty() && focused?.id !in state.selectedIds) ->
                            BulkPanel(state, actions)
                        editing -> TransactionForm(
                            editorState, editorActions, now, zone,
                            modifier = Modifier.fillMaxSize(),
                            fillHeight = true,
                            contentPadding = padding,
                        )
                        focused != null -> {
                            val data = detail(focused.id)
                            if (data != null) {
                                TransactionDetail(
                                    state = data,
                                    actions = detailActions,
                                    now = now,
                                    zone = zone,
                                    contentPadding = padding,
                                )
                            }
                        }
                        else -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { AllDone() }
                    }
                }
            },
        )
    }
}

/**
 * Outer screen (Review mockup): «Проверка» with the counter, the hint, the card stack, and the
 * three thumb actions — «Категория» (round), «Пропустить», «Подтвердить» (round, lime).
 */
@Composable
private fun CompactReview(
    state: ReviewUiState,
    actions: ReviewActions,
    detail: @Composable (Long) -> TransactionDetailState?,
    now: Instant,
    zone: ZoneId,
    modifier: Modifier,
) {
    val current = state.current
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 12.dp)) {
        ReviewHeader(state.queue.size)
        UnrecognizedEntry(state, actions)
        Spacer(Modifier.height(16.dp))
        if (current == null) {
            AllDone(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 300.dp))
            Spacer(Modifier.weight(1f))
            return@Column
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            CardStack(state, actions, detail, now, zone, quickPicks = true)
        }
        // Duplicates of the swipes, in the thumb zone: accessible and discoverable.
        ThumbActions(
            onCategory = { actions.openPicker(current.id) },
            onSkip = if (state.queue.size > 1) ({ actions.skip(current.id) }) else null,
            onConfirm = { actions.confirm(current.id) },
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    state.pickerFor?.let { tx ->
        CategorySheet(
            title = stringResource(R.string.review_sheet_title, tx.title()),
            categories = state.categories,
            selectedId = tx.categoryId,
            onPick = { actions.assign(listOf(tx.id), it) },
            onDismiss = actions::closePicker,
        )
    }
}

/** «Категория» · «Пропустить» · «Подтвердить»: 64 dp round buttons with labels under them. */
@Composable
private fun ThumbActions(
    onCategory: () -> Unit,
    onSkip: (() -> Unit)?,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    showCategory: Boolean = true,
) {
    val palette = FoldFinanceTheme.palette
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.Top) {
        if (showCategory) {
            RoundAction(
                label = stringResource(R.string.review_pick_category),
                icon = Icons.Outlined.Sell,
                onClick = onCategory,
                container = palette.card,
                content = palette.text,
                border = true,
            )
        }
        Spacer(Modifier.weight(1f))
        if (onSkip != null) {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .heightIn(min = 44.dp)
                    .clip(FoldShapes.tile)
                    .clickable(role = Role.Button, onClick = onSkip)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.review_skip), style = MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = palette.textMuted)
            }
        }
        Spacer(Modifier.weight(1f))
        RoundAction(
            label = stringResource(R.string.review_confirm),
            icon = Icons.Outlined.Check,
            onClick = onConfirm,
            container = palette.accent,
            content = palette.onAccent,
            border = false,
        )
    }
}

@Composable
private fun RoundAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    border: Boolean,
) {
    val palette = FoldFinanceTheme.palette
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(64.dp)
                .then(if (!border) Modifier.shadow(10.dp, FoldShapes.pill, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
                .clip(FoldShapes.pill)
                .background(container)
                .then(if (border) Modifier.border(1.dp, palette.outline, FoldShapes.pill) else Modifier)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(26.dp))
        }
        Text(label, style = FoldTextStyles.overline.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), color = palette.textSecondary)
    }
}

@Composable
private fun CardStack(
    state: ReviewUiState,
    actions: ReviewActions,
    detail: @Composable (Long) -> TransactionDetailState?,
    now: Instant,
    zone: ZoneId,
    quickPicks: Boolean,
) {
    val current = state.current ?: return
    val data = detail(current.id)
    val today = now.atZone(zone).toLocalDate()
    val category = data?.category ?: state.categories.firstOrNull { it.id == current.categoryId }
    val quick = if (quickPicks) quickCategories(state.categories, category) else emptyList()
    Box(Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(bottom = 18.dp)) {
        if (state.next != null) StackedCardBehind(Modifier.matchParentSize(), depth = if (state.queue.size > 2) 2 else 1)
        SwipeableReviewCard(
            transaction = current,
            category = category,
            raw = data?.rawMessages.orEmpty(),
            today = today,
            zone = zone,
            onConfirm = { actions.confirm(current.id) },
            onPickCategory = { actions.openPicker(current.id) },
            quickCategories = quick,
            onQuickPick = { picked ->
                if (picked == current.categoryId) actions.confirm(current.id) else actions.assign(listOf(current.id), picked)
            },
        )
    }
}

/** Up to three categories for the card's quick question: the current one first. */
private fun quickCategories(all: List<Category>, current: Category?): List<Category> =
    (listOfNotNull(current) + all.filter { it.id != current?.id }).take(3)

/** «Проверка» (28 / 600), the amber counter, the hint about the swipes. */
@Composable
private fun ReviewHeader(count: Int, showHint: Boolean = true) {
    val palette = FoldFinanceTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle(stringResource(R.string.section_review), Modifier.weight(1f), large = true)
            PillTag(
                text = if (count > 0) pluralStringResource(R.plurals.review_counter, count, count) else stringResource(R.string.review_counter_done),
                color = if (count > 0) palette.review else palette.accentContent,
                large = true,
                modifier = Modifier.semantics {
                    // The visual counter is short; screen readers get the full sentence.
                },
            )
        }
        if (count > 0 && showHint) {
            Text(stringResource(R.string.review_hint), style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
        }
    }
}

/** «Всё разобрано»: the place of the card, with the lime check. */
@Composable
private fun AllDone(modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    Box(
        modifier
            .fillMaxWidth()
            .background(palette.surface, FoldShapes.card)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            title = stringResource(R.string.review_done_title),
            body = stringResource(R.string.review_done_body),
            icon = Icons.Outlined.Check,
            centered = true,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(
    title: String,
    categories: List<Category>,
    selectedId: Long?,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = FoldShapes.sheet,
        containerColor = FoldFinanceTheme.palette.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = FoldFinanceTheme.palette.text)
            Spacer(Modifier.height(16.dp))
            CategoryGrid(categories, selectedId, onPick)
        }
    }
}

/** All categories as chips; a tap assigns and takes the operation out of the queue. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryGrid(categories: List<Category>, selectedId: Long?, onPick: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        categories.forEach { category ->
            val visual = category.visual()
            CategoryChip(name = visual.name, color = visual.color, selected = category.id == selectedId, onClick = { onPick(category.id) }, height = 40.dp)
        }
    }
}

/** Left panel of the book layout: the queue with checkboxes for bulk assignment. */
@Composable
private fun QueueList(state: ReviewUiState, actions: ReviewActions, zone: ZoneId, modifier: Modifier) {
    val palette = FoldFinanceTheme.palette
    val listState = rememberLazyListState()
    val categoriesById = remember(state.categories) { state.categories.associateBy { it.id } }
    Column(modifier.testTag("review.list")) {
        Column(Modifier.padding(start = 22.dp, end = 18.dp, top = 22.dp, bottom = 4.dp)) {
            ReviewHeader(state.queue.size, showHint = false)
            UnrecognizedEntry(state, actions)
            if (state.queue.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.selectedIds.isEmpty()) {
                        TextButton(onClick = actions::selectAll, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text(stringResource(R.string.review_select_all), color = palette.accentContent, style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Text(
                            pluralStringResource(R.plurals.review_selected, state.selectedIds.size, state.selectedIds.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.text,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = actions::clearSelection) {
                            Text(stringResource(R.string.review_clear_selection), color = palette.accentContent, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        if (state.queue.isEmpty()) return@Column
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 14.dp, bottom = 20.dp),
        ) {
            itemsIndexed(state.queue, key = { _, tx -> tx.id }) { _, tx ->
                val checked = tx.id in state.selectedIds
                val focused = tx.id == state.focusedId && state.selectedIds.isEmpty()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(FoldShapes.rowSelected)
                        .then(if (focused || checked) Modifier.background(palette.selection) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { actions.toggleSelected(tx.id) },
                        colors = CheckboxDefaults.colors(checkedColor = palette.accent, checkmarkColor = palette.onAccent, uncheckedColor = palette.outline),
                    )
                    TransactionRow(
                        merchant = tx.title(),
                        time = formatTime(tx.occurredAt, zone),
                        amountMinor = tx.amountMinor,
                        currency = tx.currency,
                        direction = tx.direction,
                        category = categoryVisualOrNone(tx.categoryId?.let(categoriesById::get)),
                        source = tx.source,
                        onClick = { if (state.selectedIds.isNotEmpty()) actions.toggleSelected(tx.id) else actions.focus(tx.id) },
                        modifier = Modifier.weight(1f),
                        horizontalPadding = 4.dp,
                    )
                }
            }
        }
    }
}

/** Right panel for a multi-selection: one category for all of them, or confirm all as they are. */
@Composable
private fun BulkPanel(state: ReviewUiState, actions: ReviewActions) {
    val palette = FoldFinanceTheme.palette
    val count = state.selectedIds.size
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            pluralStringResource(R.plurals.review_selected, count, count),
            style = MaterialTheme.typography.headlineMedium,
            color = palette.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.review_bulk_body), style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
        CategoryGrid(state.categories, selectedId = null, onPick = actions::assignToSelected)
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            text = pluralStringResource(R.plurals.review_confirm_selected, count, count),
            onClick = actions::confirmSelected,
            icon = Icons.Outlined.Check,
            height = 56.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
