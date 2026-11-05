package com.foldfinance.app.ui.accounts

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.EmptyState
import com.foldfinance.app.ui.components.IconCircleButton
import com.foldfinance.app.ui.components.LabeledAmount
import com.foldfinance.app.ui.components.MoneyStyle
import com.foldfinance.app.ui.components.MoneyText
import com.foldfinance.app.ui.components.Overline
import com.foldfinance.app.ui.components.PillTag
import com.foldfinance.app.ui.components.ScreenTitle
import com.foldfinance.app.ui.components.StatTile
import com.foldfinance.app.ui.components.moneyLabel
import com.foldfinance.app.ui.format.CardMaskFormat
import com.foldfinance.app.ui.format.SignDisplay
import com.foldfinance.app.ui.format.monthTitle
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.app.ui.theme.FoldTextStyles
import com.foldfinance.core.domain.accounts.TransferPair

// ---------------------------------------------------------------- List of cards

/**
 * The list side: title, month, the honest note, cards and «Движение между своими картами».
 *
 * @param onBack leaves the screen (arrow next to the title); `null` — no arrow.
 * @param twoPane the card opens next to the list: no chevrons.
 * @param highlightSelected the card shown next to the list is highlighted (book).
 * @param showTitle tabletop shows the title in the top half.
 * @param showMovements tabletop shows «Движение между своими картами» in the top half.
 */
@Composable
internal fun AccountsListColumn(
    state: AccountsUiState,
    actions: AccountsActions,
    onBack: (() -> Unit)?,
    twoPane: Boolean,
    modifier: Modifier = Modifier,
    highlightSelected: Boolean = twoPane,
    showTitle: Boolean = true,
    showMovements: Boolean = true,
) {
    val horizontal = if (twoPane) 14.dp else 20.dp
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier,
        contentPadding = PaddingValues(start = horizontal, end = horizontal, top = if (twoPane) 18.dp else 12.dp, bottom = 32.dp),
    ) {
        if (showTitle) {
            item(key = "title") { TitleRow(stringResource(R.string.accounts_title), onBack, Modifier.padding(start = if (twoPane) 8.dp else 0.dp)) }
        }
        if (state.isEmpty) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.accounts_empty_title),
                    body = stringResource(R.string.accounts_empty_body),
                    icon = Icons.Outlined.CreditCard,
                    modifier = Modifier.padding(top = 24.dp, start = if (twoPane) 8.dp else 0.dp),
                )
            }
            return@LazyColumn
        }
        item(key = "month") {
            MonthSwitcher(state, actions, Modifier.padding(top = if (showTitle) 8.dp else 0.dp, start = if (twoPane) 4.dp else 0.dp))
        }
        item(key = "honesty") {
            Text(
                stringResource(R.string.accounts_honesty),
                style = FoldTextStyles.caption,
                color = FoldFinanceTheme.palette.textMuted,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp, start = if (twoPane) 8.dp else 0.dp),
            )
        }
        items(state.accounts, key = { "card-${it.mask}" }, contentType = { "card" }) { account ->
            AccountRow(
                account = account,
                state = state,
                selected = highlightSelected && account.mask == state.detail?.summary?.mask,
                showChevron = !twoPane,
                onClick = { actions.select(account.mask) },
            )
        }
        if (showMovements) movementsSection(state, Modifier.padding(start = if (twoPane) 8.dp else 0.dp))
    }
}

/** Back arrow (when there is somewhere to go back to) and the title. */
@Composable
internal fun TitleRow(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconCircleButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.accounts_back),
                onClick = onBack,
            )
            Spacer(Modifier.width(12.dp))
        }
        ScreenTitle(title, Modifier.weight(1f))
    }
}

/** «‹ Ноябрь 2026 ›»: «вперёд» stops at the current month. */
@Composable
internal fun MonthSwitcher(state: AccountsUiState, actions: AccountsActions, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconCircleButton(
            icon = Icons.Outlined.ChevronLeft,
            contentDescription = stringResource(R.string.accounts_month_previous),
            onClick = actions::previousMonth,
            size = 40.dp,
        )
        Text(
            stringResource(R.string.accounts_month, monthTitle(state.month.atDay(1)), state.month.year),
            style = MaterialTheme.typography.titleMedium,
            color = palette.text,
            modifier = Modifier.padding(horizontal = 12.dp),
            maxLines = 1,
        )
        if (state.canGoNext) {
            IconCircleButton(
                icon = Icons.Outlined.ChevronRight,
                contentDescription = stringResource(R.string.accounts_month_next),
                onClick = actions::nextMonth,
                size = 40.dp,
            )
        }
    }
}

/**
 * One card: tile, label (or mask), «моя карта», the bank's last balance with its time and the
 * month by known operations — on separate lines, never added together.
 */
@Composable
private fun AccountRow(
    account: AccountSummary,
    state: AccountsUiState,
    selected: Boolean,
    showChevron: Boolean,
    onClick: () -> Unit,
) {
    val palette = FoldFinanceTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(FoldShapes.key)
            .background(if (selected) palette.selection else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CardTile(own = account.card.isOwn)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            CardTitleLine(account)
            account.latestBalance?.let { balance ->
                LabeledAmount(
                    label = {
                        Text(
                            stringResource(R.string.accounts_balance_at, balanceTime(balance.asOf, state.today, state.zone)),
                            style = FoldTextStyles.caption,
                            color = palette.textMuted,
                        )
                    },
                    amount = { MoneyText(balance.amountMinor, balance.currency, style = MoneyStyle.Inline, color = palette.textSoft) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                rowTotalsText(account),
                style = FoldTextStyles.caption,
                color = palette.textMuted,
            )
        }
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = palette.textMuted,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
        }
    }
}

/** «Зарплатная  [моя карта]» over «•• 1234»; without a label the mask is the title. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardTitleLine(account: AccountSummary, large: Boolean = false) {
    val palette = FoldFinanceTheme.palette
    val mask = CardMaskFormat.format(account.mask)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            account.card.label ?: mask,
            style = if (large) MaterialTheme.typography.headlineMedium else FoldTextStyles.row,
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (account.card.isOwn) PillTag(stringResource(R.string.accounts_own_tag), color = palette.accentContent, container = palette.accentSoft)
    }
    if (account.card.label != null) {
        Text(mask, style = FoldTextStyles.caption, color = palette.textMuted, maxLines = 1)
    }
}

/** 42 dp tile with a card pictogram: lime tint for own cards, neutral for the others. */
@Composable
internal fun CardTile(own: Boolean, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    Box(
        modifier
            .size(42.dp)
            .background(if (own) palette.accentSoft else palette.surfaceRaised, FoldShapes.tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.CreditCard,
            contentDescription = null,
            tint = if (own) palette.accentContent else palette.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** «Расходы 1 800 ₽ · поступления +95 000 ₽» of the first currency, or «Операций за месяц нет». */
@Composable
private fun rowTotalsText(account: AccountSummary): String {
    val first = account.totals?.byCurrency?.firstOrNull() ?: return stringResource(R.string.accounts_row_no_operations)
    val main = stringResource(
        R.string.accounts_row_totals,
        moneyLabel(first.netExpenseMinor, first.currency),
        moneyLabel(first.incomeMinor, first.currency, SignDisplay.Always),
    )
    val sides = listOfNotNull(
        first.internalOutMinor.takeIf { it > 0 }?.let { stringResource(R.string.accounts_row_internal_out, moneyLabel(it, first.currency)) },
        first.internalInMinor.takeIf { it > 0 }?.let { stringResource(R.string.accounts_row_internal_in, moneyLabel(it, first.currency)) },
    )
    if (sides.isEmpty()) return main
    return main + "\n" + stringResource(R.string.accounts_row_internal, sides.joinToString(", "))
}

// ---------------------------------------------------------------- Movement between own cards

/** «Движение между своими картами» of the month: pairs «откуда → куда» with sums. */
private fun LazyListScope.movementsSection(state: AccountsUiState, modifier: Modifier) {
    item(key = "movements-title") { MovementsHeader(state, modifier.padding(top = 22.dp, bottom = 6.dp)) }
    items(state.pairs, key = { "pair-${it.fromMask}-${it.toMask}-${it.currency}" }, contentType = { "pair" }) { pair ->
        PairRow(pair, modifier)
    }
    if (state.unresolvedTransfers.isNotEmpty()) {
        item(key = "unresolved") { UnresolvedNote(state, modifier.padding(top = 6.dp)) }
    }
}

/** Title and the note: what the pairs mean, or why there are none. */
@Composable
private fun MovementsHeader(state: AccountsUiState, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(stringResource(R.string.accounts_movements_title), style = MaterialTheme.typography.titleMedium, color = FoldFinanceTheme.palette.text)
        Text(
            stringResource(
                when {
                    state.pairs.isNotEmpty() || state.unresolvedTransfers.isNotEmpty() -> R.string.accounts_movements_note
                    state.ownCount < 2 -> R.string.accounts_movements_need_own
                    else -> R.string.accounts_movements_none
                },
            ),
            style = FoldTextStyles.caption,
            color = FoldFinanceTheme.palette.textMuted,
        )
    }
}

/** Own transfers without a direction are not guessed into pairs: say so (amber — needs a look). */
@Composable
private fun UnresolvedNote(state: AccountsUiState, modifier: Modifier = Modifier) {
    val count = state.unresolvedTransfers.size
    Text(
        pluralStringResource(R.plurals.accounts_unresolved, count, count),
        style = FoldTextStyles.caption,
        color = FoldFinanceTheme.palette.review,
        modifier = modifier,
    )
}

@Composable
private fun PairRow(pair: TransferPair, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    val from = pair.fromMask?.let(CardMaskFormat::format) ?: stringResource(R.string.accounts_card_unknown)
    val to = pair.toMask?.let(CardMaskFormat::format) ?: stringResource(R.string.accounts_card_unknown)
    val description = stringResource(R.string.accounts_pair_description, from, to, moneyLabel(pair.totalMinor, pair.currency))
    LabeledAmount(
        label = {
            Column {
                Text(stringResource(R.string.accounts_pair, from, to), style = FoldTextStyles.row, color = palette.text, maxLines = 1)
                Text(
                    pluralStringResource(R.plurals.accounts_transfers_count, pair.count, pair.count),
                    style = FoldTextStyles.caption,
                    color = palette.textMuted,
                )
            }
        },
        // Moving own money is neither an expense nor income: muted, without a sign.
        amount = { MoneyText(pair.totalMinor, pair.currency, style = MoneyStyle.Inline, color = palette.textSecondary) },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
    )
}

// ---------------------------------------------------------------- Tabletop: balances on top

/**
 * Top half of tabletop without an opened card — what to look at: every known balance as a tile and
 * the month's movement between own cards.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BalancesTabletopTop(state: AccountsUiState, actions: AccountsActions, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TitleRow(stringResource(R.string.accounts_title), onBack)
        val withBalance = state.accounts.filter { it.latestBalance != null }
        if (state.isEmpty) {
            EmptyState(
                title = stringResource(R.string.accounts_empty_title),
                body = stringResource(R.string.accounts_empty_body),
                icon = Icons.Outlined.CreditCard,
            )
            return@Column
        }
        Overline(stringResource(R.string.accounts_balance_title))
        if (withBalance.isEmpty()) {
            Text(stringResource(R.string.accounts_balance_none), style = FoldTextStyles.caption, color = palette.textMuted)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            withBalance.forEach { account ->
                val balance = account.latestBalance!!
                StatTile(
                    modifier = Modifier.widthIn(min = 196.dp),
                    onClick = { actions.select(account.mask) },
                ) {
                    Text(
                        account.card.label ?: CardMaskFormat.format(account.mask),
                        style = FoldTextStyles.caption,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MoneyText(balance.amountMinor, balance.currency, style = MoneyStyle.Tile)
                    Text(
                        stringResource(R.string.accounts_balance_at, balanceTime(balance.asOf, state.today, state.zone)),
                        style = FoldTextStyles.caption,
                        color = palette.textMuted,
                    )
                }
            }
        }
        MovementsHeader(state, Modifier.padding(top = 8.dp))
        if (state.pairs.isNotEmpty()) {
            Column(Modifier.widthIn(max = 560.dp)) { state.pairs.forEach { PairRow(it) } }
        }
        if (state.unresolvedTransfers.isNotEmpty()) UnresolvedNote(state)
    }
}

