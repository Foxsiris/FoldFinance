package com.foldfinance.app.ui.accounts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.IconCircleButton
import com.foldfinance.app.ui.components.LabeledAmount
import com.foldfinance.app.ui.components.MoneyStyle
import com.foldfinance.app.ui.components.MoneyText
import com.foldfinance.app.ui.components.OutlineButton
import com.foldfinance.app.ui.components.Overline
import com.foldfinance.app.ui.components.StatTile
import com.foldfinance.app.ui.components.TileRow
import com.foldfinance.app.ui.components.moneyLabel
import com.foldfinance.app.ui.format.CardMaskFormat
import com.foldfinance.app.ui.format.RuLocale
import com.foldfinance.app.ui.format.SignDisplay
import com.foldfinance.app.ui.format.monthTitle
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.app.ui.theme.FoldTextStyles
import com.foldfinance.app.ui.transaction.TransactionListItem
import com.foldfinance.app.ui.transaction.formatDate
import com.foldfinance.app.ui.transaction.formatTime
import com.foldfinance.core.domain.accounts.InternalMovement
import com.foldfinance.core.domain.accounts.TransferFlow
import com.foldfinance.core.model.KnownBalance
import com.foldfinance.parser.Direction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How many known balances the history list shows; the chart uses all of them. */
private const val HistoryRows = 12

/**
 * One card: title and actions, the bank's balance with its history, the month by known operations,
 * transfers between own cards and the card's operations.
 *
 * @param onBack arrow next to the title (outer screen); `null` — the card is a pane next to the list.
 * @param tabletopBottom the balance and its chart are in the top half: here only what is touched.
 */
@Composable
internal fun AccountDetailColumn(
    state: AccountsUiState,
    detail: AccountDetail,
    actions: AccountsActions,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tabletopBottom: Boolean = false,
) {
    val horizontal = if (onBack != null) 20.dp else 22.dp
    // A new card starts at the top; the position of the same card survives recreation.
    val listState = rememberSaveable(detail.summary.mask, saver = LazyListState.Saver) { LazyListState() }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(start = horizontal, end = horizontal, top = if (tabletopBottom) 16.dp else 18.dp, bottom = 32.dp),
    ) {
        if (!tabletopBottom) {
            item(key = "header") { DetailHeader(detail, onBack) }
        }
        item(key = "actions") { DetailActions(detail.summary, actions, Modifier.padding(top = if (tabletopBottom) 0.dp else 12.dp)) }
        if (!tabletopBottom) {
            item(key = "balance") { BalanceHero(detail, state, Modifier.padding(top = 22.dp)) }
        }
        totalsSection(state, detail)
        transfersSection(state, detail, actions)
        operationsSection(state, detail, actions)
        historySection(state, detail)
    }
}

/** Top half of tabletop with an opened card: what to look at — title, balance, its chart. */
@Composable
internal fun AccountTabletopTop(state: AccountsUiState, detail: AccountDetail, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp),
    ) {
        DetailHeader(detail, onBack)
        BalanceHero(detail, state, Modifier.padding(top = 16.dp), chartHeight = 160.dp)
    }
}

@Composable
private fun DetailHeader(detail: AccountDetail, onBack: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconCircleButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.accounts_back),
                onClick = onBack,
            )
            Spacer(Modifier.width(12.dp))
        }
        CardTile(own = detail.summary.card.isOwn)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { CardTitleLine(detail.summary, large = true) }
    }
}

/** «Моя карта» switch and «Переименовать»; the note says what «своя» changes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailActions(account: AccountSummary, actions: AccountsActions, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    val own = account.card.isOwn
    val ownLabel = stringResource(R.string.accounts_own)
    Column(modifier) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .clip(FoldShapes.tile)
                    .clickable(role = Role.Switch) { actions.setOwn(account.mask, !own) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$ownLabel ${CardMaskFormat.format(account.mask)}"
                        toggleableState = ToggleableState(own)
                    }
                    .heightIn(min = 48.dp)
                    .padding(start = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = own,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = palette.onAccentContent,
                        checkedTrackColor = palette.accentContent,
                        uncheckedThumbColor = palette.textMuted,
                        uncheckedTrackColor = palette.surfaceRaised,
                        uncheckedBorderColor = palette.outline,
                    ),
                )
                Spacer(Modifier.width(10.dp))
                Text(ownLabel, style = MaterialTheme.typography.labelLarge, color = palette.text)
            }
            OutlineButton(
                text = stringResource(R.string.accounts_rename),
                onClick = { actions.startRename(account.mask) },
                icon = Icons.Outlined.Edit,
                height = 48.dp,
            )
        }
        Text(
            stringResource(R.string.accounts_own_hint),
            style = FoldTextStyles.caption,
            color = palette.textMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** The bank's last balance for the card, its time, and a chart of the known balances. */
@Composable
private fun BalanceHero(detail: AccountDetail, state: AccountsUiState, modifier: Modifier = Modifier, chartHeight: Dp = 72.dp) {
    val palette = FoldFinanceTheme.palette
    val latest = detail.balanceHistory.firstOrNull() ?: detail.summary.latestBalance
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Overline(stringResource(R.string.accounts_balance_title))
        if (latest == null) {
            Text(stringResource(R.string.accounts_balance_none), style = FoldTextStyles.caption, color = palette.textMuted)
            return@Column
        }
        MoneyText(latest.amountMinor, latest.currency, style = MoneyStyle.DisplayMedium, modifier = Modifier.fillMaxWidth())
        Text(
            stringResource(R.string.accounts_balance_at, balanceTime(latest.asOf, state.today, state.zone)),
            style = FoldTextStyles.caption,
            color = palette.textMuted,
        )
        val points = detail.balanceHistory.filter { it.currency == latest.currency }.asReversed()
        if (points.size >= 2) {
            BalanceSparkline(points, Modifier.padding(top = 12.dp).fillMaxWidth().height(chartHeight))
        }
    }
}

/**
 * Known balances in time order as a line: each point is a value the bank reported, nothing is
 * interpolated into numbers. Neutral colour; the latest point is marked.
 */
@Composable
private fun BalanceSparkline(points: List<KnownBalance>, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    val line = palette.textSecondary
    val last = palette.text
    val grid = palette.seam
    val description = stringResource(
        R.string.accounts_balance_chart,
        moneyLabel(points.minOf { it.amountMinor }, points.first().currency),
        moneyLabel(points.maxOf { it.amountMinor }, points.first().currency),
    )
    Canvas(modifier.semantics { contentDescription = description }) {
        val min = points.minOf { it.amountMinor }
        val max = points.maxOf { it.amountMinor }
        val span = (max - min).takeIf { it > 0 } ?: 1L
        val t0 = points.first().asOf.toEpochMilli()
        val tSpan = (points.last().asOf.toEpochMilli() - t0).takeIf { it > 0 } ?: 1L
        val pad = 5.dp.toPx()
        fun at(b: KnownBalance): Offset {
            // Points by time; if all came at once, spread them evenly.
            val x = if (points.last().asOf == points.first().asOf) {
                points.indexOf(b).toFloat() / (points.size - 1)
            } else {
                (b.asOf.toEpochMilli() - t0).toFloat() / tSpan
            }
            val y = if (max == min) 0.5f else 1f - (b.amountMinor - min).toFloat() / span
            return Offset(pad + x * (size.width - 2 * pad), pad + y * (size.height - 2 * pad))
        }
        drawLine(grid, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 1.dp.toPx())
        val path = Path()
        points.forEachIndexed { i, b -> val p = at(b); if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        points.dropLast(1).forEach { drawCircle(line, radius = 2.5.dp.toPx(), center = at(it)) }
        drawCircle(last, radius = 4.dp.toPx(), center = at(points.last()))
    }
}

// ---------------------------------------------------------------- Month by known operations

private fun LazyListScope.totalsSection(state: AccountsUiState, detail: AccountDetail) {
    item(key = "totals") {
        val palette = FoldFinanceTheme.palette
        val monthName = monthTitle(state.month.atDay(1)).lowercase(RuLocale)
        Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Overline(stringResource(R.string.accounts_month_totals, monthName))
            val totals = detail.summary.totals?.byCurrency.orEmpty()
            if (totals.isEmpty()) {
                Text(stringResource(R.string.accounts_operations_none), style = FoldTextStyles.caption, color = palette.textMuted)
            }
            totals.forEach { t ->
                TileRow {
                    StatTile(Modifier.weight(1f)) {
                        Text(stringResource(R.string.accounts_expense), style = FoldTextStyles.caption, color = palette.textSecondary)
                        MoneyText(t.netExpenseMinor, t.currency, style = MoneyStyle.Tile)
                        if (t.refundMinor > 0) {
                            Text(
                                stringResource(R.string.accounts_refund_note, moneyLabel(t.refundMinor, t.currency)),
                                style = FoldTextStyles.caption,
                                color = palette.textMuted,
                            )
                        }
                    }
                    StatTile(Modifier.weight(1f)) {
                        Text(stringResource(R.string.accounts_income), style = FoldTextStyles.caption, color = palette.textSecondary)
                        MoneyText(
                            t.incomeMinor,
                            t.currency,
                            direction = if (t.incomeMinor > 0) Direction.INCOME else null,
                            style = MoneyStyle.Tile,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Transfers between own cards

private fun LazyListScope.transfersSection(state: AccountsUiState, detail: AccountDetail, actions: AccountsActions) {
    if (detail.movements.isEmpty()) return
    item(key = "transfers-title") {
        Column(Modifier.padding(top = 24.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.accounts_transfers_title), style = MaterialTheme.typography.titleMedium, color = FoldFinanceTheme.palette.text)
            Text(stringResource(R.string.accounts_transfer_note), style = FoldTextStyles.caption, color = FoldFinanceTheme.palette.textMuted)
        }
    }
    items(detail.movements, key = { "move-${it.transactionIds.joinToString("-")}" }, contentType = { "move" }) { m ->
        MovementRow(m, detail.summary.mask, state, onClick = { actions.openTransaction(m.transactionIds.first()) })
    }
}

/** «На •• 5678» / «С •• 1234», when, and the amount — muted, without a sign: it is not spending. */
@Composable
private fun MovementRow(movement: InternalMovement, mask: String, state: AccountsUiState, onClick: () -> Unit) {
    val palette = FoldFinanceTheme.palette
    val unknown = stringResource(R.string.accounts_card_unknown)
    val title = when {
        movement.flow == TransferFlow.UNKNOWN -> stringResource(R.string.accounts_transfer_unknown)
        movement.fromMask == mask -> stringResource(R.string.accounts_transfer_out, movement.toMask?.let(CardMaskFormat::format) ?: unknown)
        else -> stringResource(R.string.accounts_transfer_in, movement.fromMask?.let(CardMaskFormat::format) ?: unknown)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(FoldShapes.key)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 58.dp)
            .padding(horizontal = 0.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).background(palette.surfaceRaised, FoldShapes.tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = FoldTextStyles.row, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(balanceTime(movement.occurredAt, state.today, state.zone, alwaysDate = true), style = FoldTextStyles.caption, color = palette.textMuted)
        }
        Spacer(Modifier.width(8.dp))
        MoneyText(movement.amountMinor, movement.currency, direction = Direction.INTERNAL_TRANSFER, signDisplay = SignDisplay.Never)
    }
}

// ---------------------------------------------------------------- Operations of the card

private fun LazyListScope.operationsSection(state: AccountsUiState, detail: AccountDetail, actions: AccountsActions) {
    item(key = "ops-title") {
        Text(
            stringResource(R.string.accounts_operations_title),
            style = MaterialTheme.typography.titleMedium,
            color = FoldFinanceTheme.palette.text,
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        )
    }
    if (detail.operations.isEmpty()) {
        item(key = "ops-empty") {
            Text(stringResource(R.string.accounts_operations_none), style = FoldTextStyles.caption, color = FoldFinanceTheme.palette.textMuted)
        }
    }
    items(detail.operations, key = { "op-${it.id}" }, contentType = { "op" }) { tx ->
        TransactionListItem(
            transaction = tx,
            category = tx.categoryId?.let(state.categories::get),
            zone = state.zone,
            index = 0,
            groupSize = 1,
            showCard = false,
            horizontalPadding = 0.dp,
            onClick = { actions.openTransaction(tx.id) },
        )
    }
}

// ---------------------------------------------------------------- History of balances

private fun LazyListScope.historySection(state: AccountsUiState, detail: AccountDetail) {
    if (detail.balanceHistory.isEmpty()) return
    item(key = "history-title") {
        Column(Modifier.padding(top = 24.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.accounts_balance_history), style = MaterialTheme.typography.titleMedium, color = FoldFinanceTheme.palette.text)
            Text(stringResource(R.string.accounts_balance_history_note), style = FoldTextStyles.caption, color = FoldFinanceTheme.palette.textMuted)
        }
    }
    items(detail.balanceHistory.take(HistoryRows), key = { "bal-${it.asOf.toEpochMilli()}-${it.rawMessageId}-${it.amountMinor}" }, contentType = { "bal" }) { b ->
        LabeledAmount(
            label = {
                Text(balanceTime(b.asOf, state.today, state.zone, alwaysDate = true), style = FoldTextStyles.caption, color = FoldFinanceTheme.palette.textSecondary)
            },
            amount = { MoneyText(b.amountMinor, b.currency, style = MoneyStyle.Inline, color = FoldFinanceTheme.palette.textSoft) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(vertical = 6.dp),
        )
    }
}

// ---------------------------------------------------------------- Text helpers

/**
 * When a balance or a transfer happened: «14:05» today, otherwise «4 ноября, 18:30». With
 * [alwaysDate] today is «5 ноября, 14:05» too (rows of lists that span days).
 */
@Composable
internal fun balanceTime(instant: Instant, today: LocalDate, zone: ZoneId, alwaysDate: Boolean = false): String {
    val date = instant.atZone(zone).toLocalDate()
    val time = formatTime(instant, zone)
    return if (date == today && !alwaysDate) time else stringResource(R.string.tx_date_time, formatDate(date, today), time)
}
