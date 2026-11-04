package com.foldfinance.app.ui.subscriptions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.analytics.AnalyticsFormat
import com.foldfinance.app.ui.components.CategoryTile
import com.foldfinance.app.ui.components.CategoryVisual
import com.foldfinance.app.ui.components.EmptyState
import com.foldfinance.app.ui.components.ListGroup
import com.foldfinance.app.ui.components.ListGroupDivider
import com.foldfinance.app.ui.components.MoneyStyle
import com.foldfinance.app.ui.components.MoneyText
import com.foldfinance.app.ui.components.categoryVisual
import com.foldfinance.app.ui.components.visual
import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.layout.TwoPane
import com.foldfinance.app.ui.layout.TwoPaneDefaults
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.core.model.DefaultCategories
import com.foldfinance.core.model.SubscriptionPeriod
import java.time.LocalDate

/** User actions of the subscriptions screen. */
@Immutable
data class SubscriptionsActions(
    val onConfirm: (Long) -> Unit = {},
    val onDismiss: (Long) -> Unit = {},
    val onPreviousMonth: () -> Unit = {},
    val onNextMonth: () -> Unit = {},
    val onSelectDate: (LocalDate) -> Unit = {},
    val onToggleCalendar: () -> Unit = {},
)

/**
 * Stateless «Подписки»:
 *
 * - [AppLayout.Book] — the list on the left (totals, candidates, confirmed), the charge calendar
 *   on the right, split on the hinge;
 * - [AppLayout.Tabletop] — top half to look at: totals and the calendar side by side; bottom half
 *   to touch: candidates with their buttons and the list;
 * - [AppLayout.Compact] — one column, the calendar collapsible.
 */
@Composable
fun SubscriptionsScreen(
    state: SubscriptionsUiState,
    actions: SubscriptionsActions,
    modifier: Modifier = Modifier,
    layout: AppLayout = LocalAppLayout.current,
) {
    if (!state.isLoading && state.isEmpty) {
        SubscriptionsEmpty(layout, modifier)
        return
    }
    when (layout) {
        is AppLayout.Book -> TwoPane(
            modifier = modifier.fillMaxSize(),
            first = {
                ScrollPane(start = 24.dp, end = TwoPaneDefaults.SeamClearance) {
                    TotalsHeader(state)
                    ListContent(state, actions)
                }
            },
            second = {
                ScrollPane(start = TwoPaneDefaults.SeamClearance, end = 24.dp) {
                    CalendarCard(state, actions)
                    Spacer(Modifier.height(24.dp))
                    DayOrUpcoming(state)
                }
            },
        )
        is AppLayout.Tabletop -> TwoPane(
            modifier = modifier.fillMaxSize(),
            first = {
                Row(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        TotalsHeader(state)
                        DayOrUpcoming(state, limit = 3)
                    }
                    Surface(Modifier.widthIn(max = 340.dp), shape = FoldShapes.summary, color = FoldFinanceTheme.palette.surface) {
                        CalendarContent(state, actions, Modifier.padding(horizontal = 8.dp, vertical = 8.dp), cellHeight = 44.dp)
                    }
                }
            },
            second = {
                ScrollPane(start = 24.dp, end = 24.dp, top = 4.dp) {
                    ListContent(state, actions)
                }
            },
        )
        is AppLayout.Compact -> ScrollPane(start = 20.dp, end = 20.dp, modifier = modifier) {
            TotalsHeader(state)
            // Decisions first, then the calendar, then the list.
            CandidatesSection(state, actions)
            if (state.confirmed.isNotEmpty()) {
                CollapsibleCalendar(state, actions)
                Spacer(Modifier.height(24.dp))
            }
            ConfirmedSection(state, actions)
        }
    }
}

@Composable
private fun ScrollPane(
    start: androidx.compose.ui.unit.Dp,
    end: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    top: androidx.compose.ui.unit.Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = start, end = end, top = top, bottom = 24.dp),
        content = content,
    )
}

// ---------------------------------------------------------------- Totals

/** «Подписки» and the monthly cost per currency — the biggest number of the screen. */
@Composable
private fun TotalsHeader(state: SubscriptionsUiState) {
    val palette = FoldFinanceTheme.palette
    Text(
        stringResource(R.string.subscriptions_title),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineLarge,
        color = palette.text,
    )
    Spacer(Modifier.height(12.dp))
    if (state.totals.isNotEmpty()) {
        state.totals.forEachIndexed { index, total ->
            MoneyText(
                amountMinor = total.amountMinor,
                currency = total.currency,
                style = if (index == 0) MoneyStyle.DisplayMedium else MoneyStyle.DisplaySmall,
            )
        }
        Text(
            text = stringResource(R.string.subscriptions_monthly_total) + " · " +
                pluralStringResource(R.plurals.subscriptions_count, state.confirmed.size, state.confirmed.size),
            style = MaterialTheme.typography.bodyLarge,
            color = palette.textMuted,
        )
        if (state.totals.size > 1 || state.confirmed.any { it.subscription.period != SubscriptionPeriod.MONTHLY }) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.subscriptions_totals_footnote), style = MaterialTheme.typography.bodySmall, color = palette.textMuted)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- Candidates and the list

@Composable
private fun ListContent(state: SubscriptionsUiState, actions: SubscriptionsActions) {
    CandidatesSection(state, actions)
    ConfirmedSection(state, actions)
}

@Composable
private fun CandidatesSection(state: SubscriptionsUiState, actions: SubscriptionsActions) {
    val palette = FoldFinanceTheme.palette
    if (state.candidates.isNotEmpty()) {
        Text(
            stringResource(R.string.subscriptions_candidates_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            color = palette.text,
        )
        Text(stringResource(R.string.subscriptions_candidates_supporting), style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.candidates.forEach { item ->
                CandidateCard(item, state.today, onConfirm = { actions.onConfirm(item.id) }, onDismiss = { actions.onDismiss(item.id) })
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ConfirmedSection(state: SubscriptionsUiState, actions: SubscriptionsActions) {
    val palette = FoldFinanceTheme.palette
    Text(
        stringResource(R.string.subscriptions_list_title),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        color = palette.text,
    )
    Spacer(Modifier.height(12.dp))
    if (state.confirmed.isEmpty()) {
        Text(stringResource(R.string.subscriptions_list_empty), style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
        return
    }
    ListGroup(Modifier.fillMaxWidth()) {
        state.confirmed.forEachIndexed { index, item ->
            if (index > 0) ListGroupDivider()
            SubscriptionRow(item, state.today, onDismiss = { actions.onDismiss(item.id) })
        }
    }
}

/** «Похоже на подписку, подтвердить?» — the detector's finding with two big buttons. */
@Composable
private fun CandidateCard(item: SubscriptionItem, today: LocalDate, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val palette = FoldFinanceTheme.palette
    val sub = item.subscription
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FoldShapes.listGroup,
        color = palette.surface,
        border = BorderStroke(1.dp, palette.saffron.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryTile(item.visual())
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        sub.merchant,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        periodLabel(sub.period),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textMuted,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(12.dp))
                MoneyText(sub.amountMinor, sub.currency, style = MoneyStyle.InlineStrong)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    R.string.subscriptions_candidate_dates,
                    AnalyticsFormat.date(item.lastCharge, today),
                    chargeDate(item.nextCharge, today),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = FoldShapes.pill,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.saffron, contentColor = palette.onSaffron),
                ) { Text(stringResource(R.string.subscriptions_confirm), style = MaterialTheme.typography.labelLarge) }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = FoldShapes.pill,
                    border = BorderStroke(1.dp, palette.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.text),
                ) { Text(stringResource(R.string.subscriptions_dismiss), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun SubscriptionRow(item: SubscriptionItem, today: LocalDate, onDismiss: () -> Unit) {
    val palette = FoldFinanceTheme.palette
    val sub = item.subscription
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(item.visual())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                sub.merchant,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.subscriptions_next_charge, chargeDate(item.nextCharge, today)) + " · " + periodLabel(sub.period),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(sub.amountMinor, sub.currency, style = MoneyStyle.InlineStrong)
            if (sub.period != SubscriptionPeriod.MONTHLY) {
                Text(
                    stringResource(R.string.subscriptions_monthly_equivalent, MoneyFormat.format(item.monthlyMinor, sub.currency)),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textMuted,
                    maxLines = 1,
                )
            }
        }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.subscriptions_more), tint = palette.textMuted)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.subscriptions_stop_tracking)) },
                    onClick = {
                        menu = false
                        onDismiss()
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Calendar

@Composable
private fun CalendarCard(state: SubscriptionsUiState, actions: SubscriptionsActions) {
    Surface(Modifier.fillMaxWidth(), shape = FoldShapes.summary, color = FoldFinanceTheme.palette.surface) {
        CalendarContent(state, actions, Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp))
    }
}

@Composable
private fun CalendarContent(
    state: SubscriptionsUiState,
    actions: SubscriptionsActions,
    modifier: Modifier = Modifier,
    cellHeight: androidx.compose.ui.unit.Dp = 48.dp,
) {
    MonthCalendar(
        month = state.month,
        today = state.today,
        charges = state.charges,
        selected = state.selectedDate,
        onSelect = actions.onSelectDate,
        onPrevious = actions.onPreviousMonth,
        onNext = actions.onNextMonth,
        canGoPrevious = state.canGoPreviousMonth,
        canGoNext = state.canGoNextMonth,
        dotColor = { it.item.visual().color },
        modifier = modifier,
        cellMinHeight = cellHeight,
    )
}

/** Outer screen: the calendar folds into one line with the next charge. */
@Composable
private fun CollapsibleCalendar(state: SubscriptionsUiState, actions: SubscriptionsActions) {
    val palette = FoldFinanceTheme.palette
    Surface(Modifier.fillMaxWidth(), shape = FoldShapes.summary, color = palette.surface) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClick = actions.onToggleCalendar)
                    .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.subscriptions_calendar_title), style = MaterialTheme.typography.titleMedium, color = palette.text)
                    state.upcoming.firstOrNull()?.let { next ->
                        Text(
                            text = chargeDate(next.date, state.today) + " · " + next.item.subscription.merchant,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    if (state.calendarExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(
                        if (state.calendarExpanded) R.string.subscriptions_calendar_hide else R.string.subscriptions_calendar_show,
                    ),
                    tint = palette.textMuted,
                )
            }
            if (state.calendarExpanded) {
                CalendarContent(state, actions, Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp), cellHeight = 44.dp)
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    DayOrUpcoming(state)
                }
            }
        }
    }
}

/** Charges of the tapped day, or the next 30 days when no day is tapped. */
@Composable
private fun DayOrUpcoming(state: SubscriptionsUiState, limit: Int = Int.MAX_VALUE) {
    val palette = FoldFinanceTheme.palette
    val selected = state.selectedDate
    val title = selected?.let { AnalyticsFormat.date(it, state.today) } ?: stringResource(R.string.subscriptions_upcoming_title)
    val charges = if (selected != null) state.selectedCharges else state.upcoming
    Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = palette.text)
    Spacer(Modifier.height(8.dp))
    if (charges.isEmpty()) {
        Text(
            stringResource(if (selected != null) R.string.subscriptions_calendar_day_empty else R.string.subscriptions_upcoming_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textMuted,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        charges.take(limit).forEach { charge ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A tapped day is the title already; the list of upcoming charges needs the dates.
                if (selected == null || charge.isPast) Text(
                    text = if (charge.isPast) stringResource(R.string.subscriptions_calendar_charged) else chargeDate(charge.date, state.today),
                    modifier = Modifier.width(96.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (charge.isPast) palette.textMuted else palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    charge.item.subscription.merchant,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                MoneyText(
                    charge.item.subscription.amountMinor,
                    charge.item.subscription.currency,
                    style = MoneyStyle.Inline,
                    color = if (charge.isPast) palette.textMuted else Color.Unspecified,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Empty state and helpers

@Composable
private fun SubscriptionsEmpty(layout: AppLayout, modifier: Modifier) {
    val padding = if (layout is AppLayout.Compact) 20.dp else 24.dp
    Column(modifier.fillMaxSize().padding(horizontal = padding, vertical = 24.dp)) {
        Text(
            stringResource(R.string.subscriptions_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
            color = FoldFinanceTheme.palette.text,
        )
        Spacer(Modifier.weight(1f))
        EmptyState(
            title = stringResource(R.string.subscriptions_empty_title),
            body = stringResource(R.string.subscriptions_empty_body),
            icon = Icons.Outlined.Autorenew,
        )
        Spacer(Modifier.weight(if (layout is AppLayout.Compact) 0.3f else 1f))
    }
}

@Composable
private fun periodLabel(period: SubscriptionPeriod): String = stringResource(
    when (period) {
        SubscriptionPeriod.WEEKLY -> R.string.subscriptions_period_weekly
        SubscriptionPeriod.MONTHLY -> R.string.subscriptions_period_monthly
        SubscriptionPeriod.YEARLY -> R.string.subscriptions_period_yearly
    },
)

@Composable
private fun chargeDate(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.subscriptions_today)
    today.plusDays(1) -> stringResource(R.string.subscriptions_tomorrow)
    else -> AnalyticsFormat.date(date, today)
}

@Composable
private fun SubscriptionItem.visual(): CategoryVisual = category?.visual() ?: categoryVisual(
    key = DefaultCategories.all.first { it.key == "subscriptions" }.key,
    name = subscription.merchant,
    iconKey = "autorenew",
)
