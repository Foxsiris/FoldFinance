package com.foldfinance.app.ui.transaction

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.CategoryChip
import com.foldfinance.app.ui.components.CategoryTile
import com.foldfinance.app.ui.components.ListGroup
import com.foldfinance.app.ui.components.ListGroupDivider
import com.foldfinance.app.ui.components.MoneyStyle
import com.foldfinance.app.ui.components.MoneyText
import com.foldfinance.app.ui.components.ReviewBadge
import com.foldfinance.app.ui.components.visual
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.RawMessage
import com.foldfinance.core.model.Source
import com.foldfinance.core.model.Transaction
import java.time.Instant
import java.time.ZoneId

/** What the detail pane shows: the operation, its category and the bank messages it came from. */
data class TransactionDetailState(
    val transaction: Transaction,
    val category: Category?,
    val categories: List<Category>,
    val rawMessages: List<RawMessage>,
)

/** Callbacks of [TransactionDetail]. */
interface TransactionDetailActions {
    fun setCategory(transactionId: Long, categoryId: Long?)
    fun confirm(transactionId: Long)
    fun edit(transactionId: Long)
    fun delete(transactionId: Long)
}

/**
 * Detail of one operation: the amount large, merchant, category (changeable with one tap on a
 * chip), card, direction, the source and the original text of the bank message, the note.
 *
 * @param onBack shown as an arrow when the detail covers the list (single pane); `null` in two panes.
 * @param headerModifier applied to the header block (category tile, title, amount) — the shared
 *   element of the «row → detail» transition on the outer screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionDetail(
    state: TransactionDetailState,
    actions: TransactionDetailActions,
    now: Instant,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    headerModifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
) {
    val palette = FoldFinanceTheme.palette
    val tx = state.transaction
    val today = now.atZone(zone).toLocalDate()
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.tx_back), tint = palette.text)
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { actions.edit(tx.id) }) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.tx_edit), tint = palette.text)
            }
            IconButton(onClick = { actions.delete(tx.id) }) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.tx_delete), tint = palette.text)
            }
        }
        Column(headerModifier.fillMaxWidth().padding(top = 8.dp)) {
            CategoryTile(categoryVisualOrNone(state.category))
            Spacer(Modifier.height(16.dp))
            Text(
                text = tx.title(),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                color = palette.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            MoneyText(amountMinor = tx.amountMinor, currency = tx.currency, direction = tx.direction, style = MoneyStyle.DisplayMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.tx_date_time, formatDate(tx.occurredAt.atZone(zone).toLocalDate(), today), formatTime(tx.occurredAt, zone)),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textMuted,
            )
        }

        if (tx.needsReview) {
            Spacer(Modifier.height(16.dp))
            ReviewCallout(onConfirm = { actions.confirm(tx.id) })
        }

        Spacer(Modifier.height(24.dp))
        DetailSectionTitle(stringResource(R.string.tx_category))
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.categories.forEach { category ->
                val visual = category.visual()
                CategoryChip(
                    name = visual.name,
                    color = visual.color,
                    selected = category.id == tx.categoryId,
                    onClick = { actions.setCategory(tx.id, if (category.id == tx.categoryId) null else category.id) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        ListGroup(Modifier.fillMaxWidth()) {
            InfoRow(stringResource(R.string.tx_direction), tx.direction.label())
            tx.cardMask?.let {
                ListGroupDivider(startInset = 16.dp)
                InfoRow(stringResource(R.string.tx_card), stringResource(R.string.tx_card_mask, it))
            }
            ListGroupDivider(startInset = 16.dp)
            InfoRow(stringResource(R.string.tx_source), tx.source.label())
        }

        if (tx.source != Source.MANUAL) {
            Spacer(Modifier.height(24.dp))
            DetailSectionTitle(stringResource(R.string.tx_raw_title))
            Spacer(Modifier.height(8.dp))
            if (state.rawMessages.isEmpty()) {
                Text(stringResource(R.string.tx_raw_missing), style = MaterialTheme.typography.bodyLarge, color = palette.textMuted)
            }
            state.rawMessages.forEach { raw ->
                RawMessageCard(raw, today, zone)
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
        DetailSectionTitle(stringResource(R.string.tx_note))
        Spacer(Modifier.height(8.dp))
        val note = tx.note
        if (note.isNullOrBlank()) {
            TextButton(onClick = { actions.edit(tx.id) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(R.string.tx_note_add), color = palette.saffron, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Text(note, style = MaterialTheme.typography.bodyLarge, color = palette.text)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DetailSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = FoldFinanceTheme.palette.text,
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    val palette = FoldFinanceTheme.palette
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = palette.textMuted, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = palette.text)
    }
}

@Composable
private fun RawMessageCard(raw: RawMessage, today: java.time.LocalDate, zone: ZoneId) {
    val palette = FoldFinanceTheme.palette
    Surface(shape = FoldShapes.listGroup, color = palette.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    R.string.tx_raw_meta,
                    raw.sender,
                    formatDate(raw.receivedAt.atZone(zone).toLocalDate(), today),
                    formatTime(raw.receivedAt, zone),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = palette.textMuted,
            )
            Spacer(Modifier.height(6.dp))
            Text(raw.text, style = MaterialTheme.typography.bodyLarge, color = palette.text)
        }
    }
}

@Composable
private fun ReviewCallout(onConfirm: () -> Unit) {
    val palette = FoldFinanceTheme.palette
    Surface(shape = FoldShapes.listGroup, color = palette.saffronContainer.copy(alpha = 0.6f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ReviewBadge()
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.tx_review_hint), style = MaterialTheme.typography.bodyMedium, color = palette.onSaffronContainer)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onConfirm,
                shape = FoldShapes.pill,
                colors = ButtonDefaults.buttonColors(containerColor = palette.saffron, contentColor = palette.onSaffron),
            ) { Text(stringResource(R.string.tx_confirm)) }
        }
    }
}

/**
 * «Применять для всех операций от X?» after a category change. «Применять» creates a merchant
 * rule and re-categorises past operations (except ones edited by hand).
 */
@Composable
fun MerchantRuleDialog(prompt: RulePrompt, onApply: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tx_rule_title, prompt.merchant)) },
        text = { Text(stringResource(R.string.tx_rule_body, prompt.merchant, prompt.categoryName)) },
        confirmButton = { TextButton(onClick = onApply) { Text(stringResource(R.string.tx_rule_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tx_rule_only_this)) } },
    )
}
