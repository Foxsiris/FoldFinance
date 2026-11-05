package com.foldfinance.app.ui.ai.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldfinance.app.R
import com.foldfinance.app.ui.ai.AiProgress
import com.foldfinance.app.ai.features.common.AiAvailability
import com.foldfinance.app.ai.features.common.FactFormat
import com.foldfinance.app.ai.features.query.AnswerRow
import com.foldfinance.app.ai.features.query.ChatScope
import com.foldfinance.app.ai.features.query.QueryAnswer
import com.foldfinance.app.ai.features.query.QueryCall
import com.foldfinance.app.ai.features.query.QueryRejection
import com.foldfinance.app.ai.features.query.RowLabel
import com.foldfinance.app.ui.ai.AiLoadingCard
import com.foldfinance.app.ui.components.MoneyStyle
import com.foldfinance.app.ui.components.MoneyText
import com.foldfinance.app.ui.components.TransactionRow
import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.app.ui.format.SignDisplay
import com.foldfinance.app.ui.format.monthTitle
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.app.ui.transaction.categoryVisualOrNone
import com.foldfinance.app.ui.transaction.formatDate
import com.foldfinance.app.ui.transaction.formatTime
import com.foldfinance.app.ui.transaction.title
import com.foldfinance.core.domain.analytics.AmountChange
import java.time.ZoneId

/**
 * Chat as a pane — the inner screen, to the right of the operations list. Questions are about
 * [scope] (the period/category selected on the screen) unless they name their own.
 * Shares history with [AiChatSheet], so folding keeps the conversation.
 *
 * @param fallback shown instead when the model is not available (e.g. the operation detail).
 */
@Composable
fun AiChatPane(
    scope: ChatScope,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit = {},
    viewModel: AiChatViewModel = hiltViewModel(key = AiChatViewModel.KEY),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val availability = state.availability) {
        AiAvailability.Hidden -> fallback()
        is AiAvailability.Loading -> Box(modifier.padding(16.dp)) {
            AiLoadingCard(availability, stringResource(R.string.aif_chat_title), Modifier.fillMaxWidth())
        }
        AiAvailability.Ready -> AiChatContent(state, viewModel.actions, scope, modifier)
    }
}

/** «Спросить» — opens [AiChatSheet] on the outer screen. Not shown unless the model is ready. */
@Composable
fun AiChatLauncher(
    modifier: Modifier = Modifier,
    viewModel: AiChatViewModel = hiltViewModel(key = AiChatViewModel.KEY),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.availability != AiAvailability.Ready) return
    FilledTonalButton(onClick = { viewModel.actions.setSheetOpen(true) }, modifier = modifier) {
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
        Spacer(Modifier.padding(start = 8.dp))
        Text(stringResource(R.string.aif_open_chat))
    }
}

/** Chat as a full-height sheet — the outer screen. Open state survives folding (SavedStateHandle). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatSheet(
    scope: ChatScope,
    viewModel: AiChatViewModel = hiltViewModel(key = AiChatViewModel.KEY),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.sheetOpen || state.availability != AiAvailability.Ready) return
    ModalBottomSheet(
        onDismissRequest = { viewModel.actions.setSheetOpen(false) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = FoldShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        AiChatContent(state, viewModel.actions, scope, Modifier.fillMaxSize().navigationBarsPadding())
    }
}

/** Stateless chat: history, hints and the input at the bottom (thumb zone). */
@Composable
fun AiChatContent(
    state: AiChatState,
    actions: AiChatActions,
    scope: ChatScope,
    modifier: Modifier = Modifier,
) {
    val examples = stringArrayResource(R.array.aif_chat_examples)
    val list = rememberLazyListState()
    LaunchedEffect(state.turns.size) {
        if (state.turns.isNotEmpty()) list.scrollToItem(state.turns.size - 1)
    }
    Column(modifier.imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.aif_chat_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                scope.period?.let {
                    Text(
                        stringResource(R.string.aif_chat_scope, FactFormat.period(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.turns.isNotEmpty()) {
                IconButton(onClick = actions::clear) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.aif_chat_clear))
                }
            }
        }
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.turns.isEmpty()) {
                item(key = "empty") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.aif_chat_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Hints(examples, actions)
                    }
                }
            }
            items(state.turns, key = { it.id }) { turn ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuestionBubble(turn.question)
                    when (turn) {
                        is ChatTurn.Pending -> Thinking()
                        is ChatTurn.Answered -> AnswerCard(turn.answer, state)
                        is ChatTurn.NotUnderstood -> NotUnderstood(turn.reason, examples, actions)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = actions::setInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.aif_chat_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                maxLines = 3,
                shape = FoldShapes.tile,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (state.canSend) actions.send(scope) }),
                enabled = !state.asking,
            )
            IconButton(onClick = { actions.send(scope) }, enabled = state.canSend) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = stringResource(R.string.aif_chat_send))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hints(examples: Array<String>, actions: AiChatActions) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        examples.forEach { example ->
            SuggestionChip(onClick = { actions.setInput(example) }, label = { Text(example) })
        }
    }
}

@Composable
private fun QuestionBubble(question: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            shape = FoldShapes.listGroup,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 360.dp),
        ) {
            Text(
                question,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun Thinking() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.aif_chat_thinking), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AiProgress(Modifier.widthIn(max = 200.dp))
    }
}

@Composable
private fun NotUnderstood(reason: QueryRejection, examples: Array<String>, actions: AiChatActions) {
    AnswerSurface {
        Text(stringResource(R.string.aif_not_understood), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            stringResource(
                when (reason) {
                    QueryRejection.UNKNOWN_CATEGORY -> R.string.aif_not_understood_category
                    QueryRejection.BAD_PERIOD -> R.string.aif_not_understood_period
                    QueryRejection.MODEL_FAILED -> R.string.aif_not_understood_failed
                    QueryRejection.NOT_A_QUESTION -> R.string.aif_not_understood_topic
                    else -> R.string.aif_not_understood_generic
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Hints(examples.take(3).toTypedArray(), actions)
    }
}

@Composable
private fun AnswerSurface(content: @Composable () -> Unit) {
    Surface(shape = FoldShapes.listGroup, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/** The interpretation of the question, as chips: what was computed, over what, for when. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Interpretation(call: QueryCall) {
    val chips = buildList {
        add(
            stringResource(
                when (call) {
                    is QueryCall.Spend -> R.string.aif_fn_spend
                    is QueryCall.Compare -> R.string.aif_fn_compare
                    is QueryCall.Top -> R.string.aif_fn_top
                    is QueryCall.ListOps -> R.string.aif_fn_list
                },
            ),
        )
        when (call) {
            is QueryCall.Spend -> { call.category?.let { add(it.name) }; call.merchant?.let { add(stringResource(R.string.aif_merchant_chip, it)) } }
            is QueryCall.ListOps -> { call.category?.let { add(it.name) }; call.merchant?.let { add(stringResource(R.string.aif_merchant_chip, it)) } }
            is QueryCall.Compare -> call.category?.let { add(it.name) }
            is QueryCall.Top -> Unit
        }
        add(FactFormat.period(call.period))
        if (call is QueryCall.Compare) add(stringResource(R.string.aif_compared_with, FactFormat.period(call.previous)))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { label ->
            Surface(shape = FoldShapes.pill, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/** An answer computed by Kotlin: totals, breakdown rows and the operations. */
@Composable
fun AnswerCard(answer: QueryAnswer, state: AiChatState) {
    AnswerSurface {
        Interpretation(answer.call)
        answer.totals.forEachIndexed { index, total ->
            MoneyText(
                amountMinor = total.amountMinor,
                currency = total.currency,
                style = if (index == 0) MoneyStyle.DisplaySmall else MoneyStyle.InlineStrong,
            )
            total.change?.let { ChangeLine(it, total.currency) }
        }
        Text(
            if (answer.operationCount == 0) stringResource(R.string.aif_no_operations)
            else pluralStringResource(R.plurals.aif_operations_count, answer.operationCount, answer.operationCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (answer.rows.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            answer.rows.forEach { row -> AnswerRowView(row, state, answer.call.period.zone) }
        }
        if (answer.operations.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            val zone = answer.call.period.zone
            answer.operations.take(VISIBLE_OPERATIONS).forEach { tx ->
                TransactionRow(
                    merchant = tx.title(),
                    time = "${formatDate(tx.occurredAt.atZone(zone).toLocalDate(), state.today)}, ${formatTime(tx.occurredAt, zone)}",
                    amountMinor = tx.amountMinor,
                    currency = tx.currency,
                    direction = tx.direction,
                    category = categoryVisualOrNone(state.categories.firstOrNull { it.id == tx.categoryId }),
                    source = tx.source,
                    needsReview = tx.needsReview,
                    compactBadge = true,
                )
            }
            val more = answer.operationCount - VISIBLE_OPERATIONS
            if (more > 0) {
                Text(
                    pluralStringResource(R.plurals.aif_more_operations, more, more),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChangeLine(change: AmountChange, currency: String) {
    val delta = MoneyFormat.format(change.deltaMinor, currency, SignDisplay.Always)
    val pct = FactFormat.percent(change)
    Text(
        text = stringResource(R.string.aif_was, MoneyFormat.format(change.previousMinor, currency)) + " · " +
            (if (pct != null) stringResource(R.string.aif_change_pct, delta, pct) else delta),
        style = MaterialTheme.typography.bodyMedium,
        color = if (change.deltaMinor > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
    )
}

@Composable
private fun AnswerRowView(row: AnswerRow, state: AiChatState, zone: ZoneId) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            rowLabel(row.label, state, zone),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.padding(start = 8.dp))
        val change = row.change
        if (change != null) {
            MoneyText(
                amountMinor = change.deltaMinor,
                currency = row.currency,
                signDisplay = SignDisplay.Always,
                color = if (change.deltaMinor > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
            )
        } else {
            MoneyText(amountMinor = row.amountMinor, currency = row.currency)
        }
    }
}

@Composable
private fun rowLabel(label: RowLabel, state: AiChatState, zone: ZoneId): String = when (label) {
    is RowLabel.OfCategory -> label.category.name
    RowLabel.Uncategorized -> stringResource(R.string.tx_no_category)
    is RowLabel.OfMerchant -> label.name
    RowLabel.NoMerchant -> stringResource(R.string.aif_no_merchant)
    is RowLabel.OfDay -> formatDate(label.date, state.today)
    is RowLabel.OfMonth -> monthTitle(label.month.atDay(1)) + " " + label.month.year
    is RowLabel.OfOperation -> label.transaction.title() + " · " + formatDate(
        label.transaction.occurredAt.atZone(zone).toLocalDate(),
        state.today,
    )
}


private const val VISIBLE_OPERATIONS = 5
