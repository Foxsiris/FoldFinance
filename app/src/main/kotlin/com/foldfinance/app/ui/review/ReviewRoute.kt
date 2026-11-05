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
                Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp)) {
                    ReviewHeader(state.queue.size)
                    UnrecognizedEntry(state, actions)
                    Spacer(Modifier.weight(1f))
                    val current = state.current
                    if (current == null) {
                        AllDone()
                    } else {
                        CardStack(state, actions, detail, now, zone)
                    }
                }
            },
            second = {
                val current = state.current
                if (current != null) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(stringResource(R.string.review_choose_category), style = MaterialTheme.typography.titleMedium, color = FoldFinanceTheme.palette.text)
                            CategoryGrid(state.categories, current.categoryId) { actions.assign(listOf(current.id), it) }
                        }
                        // «Подтвердить» at the very bottom: the thumb zone of the touch half.
                        ConfirmButton(stringResource(R.string.review_confirm), onClick = { actions.confirm(current.id) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                    }
                }
            },
        )
        is AppLayout.Book -> TwoPane(
            modifier = modifier.fillMaxSize(),
            first = { QueueList(state, actions, zone, Modifier.fillMaxSize()) },
            second = {
                Box(Modifier.fillMaxSize().testTag("review.editor")) {
                    val focused = state.focused
                    val editing = focused != null && editorState.mode == FormMode.Edit(focused.id)
                    BackHandler(enabled = editing) { editorActions.close() }
                    when {
                        state.selectedIds.size > 1 || (state.selectedIds.isNotEmpty() && focused?.id !in state.selectedIds) ->
                            BulkPanel(state, actions)
                        editing -> TransactionForm(
                            editorState, editorActions, now, zone,
                            modifier = Modifier.fillMaxSize(),
                            fillHeight = true,
                            contentPadding = PaddingValues(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
                        )
                        focused != null -> {
                            val data = detail(focused.id)
                            if (data != null) {
                                TransactionDetail(
                                    state = data,
                                    actions = detailActions,
                                    now = now,
                                    zone = zone,
                                    contentPadding = PaddingValues(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                                )
                            }
                        }
                        else -> Box(Modifier.fillMaxSize().padding(start = 12.dp, end = 24.dp, top = 88.dp)) { AllDone() }
                    }
                }
            },
        )
    }
}

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
        if (current == null) {
            Spacer(Modifier.weight(1f))
            AllDone()
            Spacer(Modifier.height(24.dp))
            return@Column
        }
        Spacer(Modifier.weight(1f))
        CardStack(state, actions, detail, now, zone)
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.review_swipe_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = FoldFinanceTheme.palette.textMuted,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
        // Duplicates of the swipes, in the thumb zone: accessible and discoverable.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(
                text = stringResource(R.string.review_pick_category),
                onClick = { actions.openPicker(current.id) },
                modifier = Modifier.weight(1f),
            )
            ConfirmButton(
                text = stringResource(R.string.review_confirm),
                onClick = { actions.confirm(current.id) },
                modifier = Modifier.weight(1f),
            )
        }
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

@Composable
private fun CardStack(
    state: ReviewUiState,
    actions: ReviewActions,
    detail: @Composable (Long) -> TransactionDetailState?,
    now: Instant,
    zone: ZoneId,
) {
    val current = state.current ?: return
    val data = detail(current.id)
    val today = now.atZone(zone).toLocalDate()
    Box(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        if (state.next != null) StackedCardBehind(Modifier.matchParentSize())
        SwipeableReviewCard(
            transaction = current,
            category = data?.category ?: state.categories.firstOrNull { it.id == current.categoryId },
            raw = data?.rawMessages.orEmpty(),
            today = today,
            zone = zone,
            onConfirm = { actions.confirm(current.id) },
            onPickCategory = { actions.openPicker(current.id) },
        )
    }
}

@Composable
private fun ReviewHeader(count: Int) {
    val palette = FoldFinanceTheme.palette
    Column {
        Text(
            stringResource(R.string.section_review),
            style = MaterialTheme.typography.headlineLarge,
            color = palette.text,
            modifier = Modifier.semantics { heading() },
        )
        if (count > 0) {
            Text(
                pluralStringResource(R.plurals.review_left, count, count),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textMuted,
            )
        }
    }
}

@Composable
private fun AllDone() {
    EmptyState(
        title = stringResource(R.string.review_done_title),
        body = stringResource(R.string.review_done_body),
        icon = Icons.Outlined.TaskAlt,
    )
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { category ->
            val visual = category.visual()
            CategoryChip(name = visual.name, color = visual.color, selected = category.id == selectedId, onClick = { onPick(category.id) })
        }
    }
}

@Composable
private fun QueueList(state: ReviewUiState, actions: ReviewActions, zone: ZoneId, modifier: Modifier) {
    val palette = FoldFinanceTheme.palette
    val listState = rememberLazyListState()
    val categoriesById = remember(state.categories) { state.categories.associateBy { it.id } }
    Column(modifier.testTag("review.list")) {
        Column(Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
            ReviewHeader(state.queue.size)
            UnrecognizedEntry(state, actions)
            if (state.queue.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.selectedIds.isEmpty()) {
                        TextButton(onClick = actions::selectAll, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text(stringResource(R.string.review_select_all), color = palette.saffron, style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        Text(
                            pluralStringResource(R.plurals.review_selected, state.selectedIds.size, state.selectedIds.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.text,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = actions::clearSelection) {
                            Text(stringResource(R.string.review_clear_selection), color = palette.saffron, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        if (state.queue.isEmpty()) return@Column
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 12.dp, bottom = 24.dp),
        ) {
            itemsIndexed(state.queue, key = { _, tx -> tx.id }) { index, tx ->
                val checked = tx.id in state.selectedIds
                val focused = tx.id == state.focusedId && state.selectedIds.isEmpty()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(0.dp)
                        .background(palette.surface, groupItemShape(index, state.queue.size))
                        .then(if (focused || checked) Modifier.background(palette.saffronContainer.copy(alpha = 0.55f), groupItemShape(index, state.queue.size)) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { actions.toggleSelected(tx.id) },
                        colors = CheckboxDefaults.colors(checkedColor = palette.saffron, checkmarkColor = palette.onSaffron, uncheckedColor = palette.outline),
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
                        modifier = Modifier.weight(1f).padding(start = 0.dp),
                    )
                }
                if (index < state.queue.lastIndex) {
                    Box(Modifier.fillMaxWidth().background(palette.surface)) { ListGroupDivider(startInset = 116.dp) }
                }
            }
        }
    }
}

/** Right pane for a multi-selection: one category for all of them, or confirm all as they are. */
@Composable
private fun BulkPanel(state: ReviewUiState, actions: ReviewActions) {
    val palette = FoldFinanceTheme.palette
    val count = state.selectedIds.size
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            pluralStringResource(R.plurals.review_selected, count, count),
            style = MaterialTheme.typography.headlineMedium,
            color = palette.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.review_bulk_body), style = MaterialTheme.typography.bodyLarge, color = palette.textMuted)
        CategoryGrid(state.categories, selectedId = null, onPick = actions::assignToSelected)
        Spacer(Modifier.height(8.dp))
        ConfirmButton(
            text = pluralStringResource(R.plurals.review_confirm_selected, count, count),
            onClick = actions::confirmSelected,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ConfirmButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = FoldShapes.pill,
        colors = ButtonDefaults.buttonColors(containerColor = palette.saffron, contentColor = palette.onSaffron),
    ) {
        Icon(Icons.Outlined.Check, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = FoldShapes.pill,
        colors = ButtonDefaults.buttonColors(containerColor = palette.surfaceRaised, contentColor = palette.text),
    ) {
        Icon(Icons.Outlined.Category, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

