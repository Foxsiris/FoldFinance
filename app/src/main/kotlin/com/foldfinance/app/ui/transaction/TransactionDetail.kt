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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.ai.LocalAiHooks
import com.foldfinance.app.ui.format.CardMaskFormat
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
import com.foldfinance.parser.MessageChannel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.foldfinance.app.ui.components.IconCircleButton
import com.foldfinance.app.ui.components.OutlineButton
import com.foldfinance.app.ui.components.Overline
import com.foldfinance.app.ui.components.PillTag
import com.foldfinance.app.ui.components.PrimaryButton
import com.foldfinance.app.ui.components.signedRowAmount
import com.foldfinance.app.ui.theme.FoldTextStyles
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
    val visual = categoryVisualOrNone(state.category)
    val hooks = LocalAiHooks.current
    val rename = hooks.renameMerchant.takeIf { tx.merchant != null }
    var renaming by rememberSaveable(tx.id) { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconCircleButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.tx_back),
                    onClick = onBack,
                )
            }
            Spacer(Modifier.weight(1f))
            // «Чек» (local model reads the receipt and splits the operation): only expenses, only
            // when the model accepts images.
            hooks.receiptButton?.let { receipt ->
                receipt(tx)
                Spacer(Modifier.width(8.dp))
            }
            OutlineButton(
                text = stringResource(R.string.tx_edit),
                icon = Icons.Outlined.Edit,
                onClick = { actions.edit(tx.id) },
                height = 40.dp,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(FoldShapes.tile)
                    .border(1.dp, palette.outline, FoldShapes.tile)
                    .clickable(role = Role.Button, onClick = { actions.delete(tx.id) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.tx_delete), tint = palette.danger, modifier = Modifier.size(20.dp))
            }
        }
        Column(headerModifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val renameLabel = stringResource(R.string.tx_merchant_rename)
            Row(
                Modifier
                    .then(
                        if (rename != null) {
                            Modifier
                                .clip(FoldShapes.chip)
                                .clickable(role = Role.Button, onClickLabel = renameLabel) { renaming = true }
                                .heightIn(min = 36.dp)
                        } else {
                            Modifier
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(visual.color, FoldShapes.pill))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = tx.title(),
                    modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (rename != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.Edit, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(16.dp))
                }
            }
            MoneyText(
                amountMinor = signedRowAmount(tx.amountMinor, tx.direction),
                currency = tx.currency,
                direction = tx.direction,
                style = MoneyStyle.DisplayMedium,
            )
            FlowRow(
                Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PillTag(tx.direction.label(), color = palette.textSoft, container = palette.surfaceRaised)
                PillTag(sourceSummary(tx, state.rawMessages), color = palette.textSoft, container = palette.surfaceRaised)
                if (tx.needsReview) ReviewBadge()
                if (tx.aiParsed) PillTag(stringResource(R.string.ai_badge_parsed), color = palette.textSoft, container = palette.surfaceRaised)
                if (tx.aiCategory) PillTag(stringResource(R.string.ai_badge_category), color = palette.textSoft, container = palette.surfaceRaised)
            }
        }

        if (tx.needsReview) {
            Spacer(Modifier.height(16.dp))
            ReviewCallout(onConfirm = { actions.confirm(tx.id) })
        }

        Spacer(Modifier.height(18.dp))
        Column {
            FieldRow(
                stringResource(R.string.tx_date_label),
                stringResource(R.string.tx_date_time, formatDate(tx.occurredAt.atZone(zone).toLocalDate(), today), formatTime(tx.occurredAt, zone)),
            )
            tx.cardMask?.let { FieldRow(stringResource(R.string.tx_card), CardMaskFormat.format(it)) }
            FieldRow(stringResource(R.string.tx_category), visual.name)
            FieldRow(stringResource(R.string.tx_source), tx.source.label())
            HorizontalDivider(thickness = 1.dp, color = palette.seam)
        }

        if (tx.splits.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Overline(stringResource(R.string.tx_split_title))
            Spacer(Modifier.height(6.dp))
            SplitParts(tx, state)
        }

        Spacer(Modifier.height(18.dp))
        Overline(stringResource(R.string.tx_category))
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            state.categories.forEach { category ->
                val chip = category.visual()
                CategoryChip(
                    name = chip.name,
                    color = chip.color,
                    selected = category.id == tx.categoryId,
                    onClick = { actions.setCategory(tx.id, if (category.id == tx.categoryId) null else category.id) },
                    height = 36.dp,
                )
            }
        }

        if (tx.source != Source.MANUAL) {
            Spacer(Modifier.height(18.dp))
            Overline(stringResource(if (state.rawMessages.size > 1) R.string.tx_raw_title_merged else R.string.tx_raw_title))
            Spacer(Modifier.height(10.dp))
            if (state.rawMessages.isEmpty()) {
                Text(stringResource(R.string.tx_raw_missing), style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
            }
            state.rawMessages.forEach { raw ->
                RawMessageCard(raw, tx.source, zone)
                Spacer(Modifier.height(10.dp))
            }
            if (tx.aiParsed) {
                Text(stringResource(R.string.ai_badge_parsed_body), style = FoldTextStyles.caption, color = palette.textMuted)
            }
        }

        Spacer(Modifier.height(14.dp))
        Overline(stringResource(R.string.tx_note))
        Spacer(Modifier.height(4.dp))
        val note = tx.note
        if (note.isNullOrBlank()) {
            TextButton(onClick = { actions.edit(tx.id) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(R.string.tx_note_add), color = palette.accentContent, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Text(note, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (renaming && rename != null) {
        val raw = tx.merchant.orEmpty()
        MerchantRenameDialog(
            raw = raw,
            current = tx.displayMerchant.orEmpty(),
            onSave = { name ->
                renaming = false
                rename(raw, name)
            },
            onDismiss = { renaming = false },
        )
    }
}

/**
 * Parts of an operation split by a receipt: category, what went into it, the amount; the rest of
 * the operation stays in its own category. Amounts come from the database, sums are Kotlin's.
 */
@Composable
private fun SplitParts(tx: Transaction, state: TransactionDetailState) {
    val palette = FoldFinanceTheme.palette
    val byId = state.categories.associateBy { it.id }
    Column {
        tx.splits.forEach { part ->
            val visual = categoryVisualOrNone(part.categoryId?.let(byId::get))
            SplitRow(visual.color, visual.name, part.label, part.amountMinor, tx.currency)
        }
        val rest = tx.unsplitAmountMinor
        if (rest > 0) {
            val visual = categoryVisualOrNone(state.category)
            SplitRow(visual.color, stringResource(R.string.tx_split_rest), visual.name, rest, tx.currency)
        }
        HorizontalDivider(thickness = 1.dp, color = palette.seam)
    }
}

@Composable
private fun SplitRow(color: androidx.compose.ui.graphics.Color, title: String, caption: String?, amountMinor: Long, currency: String) {
    val palette = FoldFinanceTheme.palette
    Column {
        HorizontalDivider(thickness = 1.dp, color = palette.seam)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, FoldShapes.pill))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!caption.isNullOrBlank()) {
                    Text(caption, style = FoldTextStyles.caption, color = palette.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(12.dp))
            MoneyText(amountMinor = amountMinor, currency = currency, style = MoneyStyle.Inline)
        }
    }
}

/** «SMS + push», «SMS», «Вручную»: where the operation came from. */
@Composable
private fun sourceSummary(tx: Transaction, raw: List<RawMessage>): String {
    val sms = stringResource(R.string.tx_source_short_sms)
    val push = stringResource(R.string.tx_source_short_push)
    val channels = raw.map { if (it.channel == MessageChannel.PUSH) push else sms }.distinct()
    return when {
        channels.size > 1 -> channels.joinToString(" + ")
        channels.size == 1 -> channels.first()
        else -> tx.source.label()
    }
}

/** «Дата и время … 5 ноября, 14:05» with the hairline above (History mockup). */
@Composable
private fun FieldRow(label: String, value: String) {
    val palette = FoldFinanceTheme.palette
    Column {
        HorizontalDivider(thickness = 1.dp, color = palette.seam)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.textMuted, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = palette.text, textAlign = TextAlign.End)
        }
    }
}

/** One original bank message: «SMS · 900 · 14:05» over the text in mono, in a sunken well. */
@Composable
private fun RawMessageCard(raw: RawMessage, source: Source, zone: ZoneId) {
    val palette = FoldFinanceTheme.palette
    val channel = stringResource(if (raw.channel == MessageChannel.PUSH) R.string.tx_source_short_push else R.string.tx_source_short_sms)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(FoldShapes.rowSelected)
            .background(palette.surfaceSunken)
            .border(1.dp, palette.seam, FoldShapes.rowSelected)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.tx_raw_header, channel, raw.sender, formatTimeSeconds(raw.receivedAt, zone)),
            style = MaterialTheme.typography.labelMedium,
            color = palette.textSecondary,
        )
        Text(raw.text, style = FoldTextStyles.mono, color = palette.textSoft)
    }
}

private val TimeWithSeconds: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")

private fun formatTimeSeconds(instant: Instant, zone: ZoneId): String = TimeWithSeconds.format(instant.atZone(zone))

@Composable
private fun ReviewCallout(onConfirm: () -> Unit) {
    val palette = FoldFinanceTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(FoldShapes.listGroup)
            .background(palette.review.copy(alpha = 0.1f))
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.tx_review_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSoft,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        PrimaryButton(text = stringResource(R.string.tx_confirm), onClick = onConfirm, height = 44.dp)
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
