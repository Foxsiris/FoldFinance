package com.foldfinance.app.ui.overview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.CategoryChip
import com.foldfinance.app.ui.components.CategoryTile
import com.foldfinance.app.ui.components.EmptyState
import com.foldfinance.app.ui.components.LabeledAmount
import com.foldfinance.app.ui.components.LabeledAmountGroup
import com.foldfinance.app.ui.components.ListGroup
import com.foldfinance.app.ui.components.ListGroupDivider
import com.foldfinance.app.ui.components.MoneyStyle
import com.foldfinance.app.ui.components.MoneyText
import com.foldfinance.app.ui.components.Period
import com.foldfinance.app.ui.components.PeriodSelector
import com.foldfinance.app.ui.components.SectionHeader
import com.foldfinance.app.ui.components.SummaryCard
import com.foldfinance.app.ui.format.CardMaskFormat
import com.foldfinance.app.ui.format.monthTitle
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.layout.TwoPane
import com.foldfinance.app.ui.shell.LocalShellNavigation
import com.foldfinance.app.ui.shell.Section
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.app.ui.transaction.DayHeader
import com.foldfinance.app.ui.transaction.SavedSnackbarEffect
import com.foldfinance.app.ui.transaction.TransactionForm
import com.foldfinance.app.ui.transaction.TransactionFormActions
import com.foldfinance.app.ui.transaction.TransactionFormSheet
import com.foldfinance.app.ui.transaction.TransactionFormState
import com.foldfinance.app.ui.transaction.TransactionFormViewModel
import com.foldfinance.app.ui.transaction.TransactionListItem
import com.foldfinance.app.ui.transaction.categoryVisualOrNone
import com.foldfinance.app.ui.transaction.dayTitle
import com.foldfinance.app.ui.transaction.formatDate
import com.foldfinance.app.ui.transaction.formatTime
import com.foldfinance.app.ui.transaction.groupByDay
import com.foldfinance.core.model.KnownBalance
import com.foldfinance.core.model.Transaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Callbacks of [OverviewScreen]. */
interface OverviewActions {
    fun setPeriod(period: Period)
    fun toggleCategory(selection: CategorySelection)
    fun toggleBar(key: Long)
    fun clearSelection()
    fun openTransaction(transactionId: Long)
    fun openReview()
    fun openHistory()
    fun openSubscriptions()
    fun add()
}

/** Periods of the overview chart (a single day has nothing to chart). */
val OverviewPeriods: List<Period> = listOf(Period.Week, Period.Month, Period.Year)

/** Operations shown in the outer-screen feed; the rest is one tap away in History. */
private const val FeedSize = 20

/**
 * «Обзор».
 *
 * - Outer screen: «Сегодня / Месяц», known balances, the feed of recent operations; «Разобрать N»
 *   and «Добавить» sit at the bottom, in the thumb zone.
 * - Book: summary, chart and categories on the left; the list on the right — a tap on a category or
 *   a bar filters it in place. The add form opens in the right pane.
 * - Tabletop: summary and chart on top (to look at), period, filters and list below (to touch).
 */
@Composable
fun OverviewRoute(
    modifier: Modifier = Modifier,
    viewModel: OverviewViewModel = hiltViewModel(),
    addForm: TransactionFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val addState by addForm.state.collectAsStateWithLifecycle()
    val shell = LocalShellNavigation.current
    SavedSnackbarEffect(addForm.formEvents)
    val actions = remember(viewModel, addForm, shell) {
        object : OverviewActions {
            override fun setPeriod(period: Period) = viewModel.setPeriod(period)
            override fun toggleCategory(selection: CategorySelection) = viewModel.toggleCategory(selection)
            override fun toggleBar(key: Long) = viewModel.toggleBar(key)
            override fun clearSelection() = viewModel.clearSelection()
            override fun openTransaction(transactionId: Long) = shell.openTransaction(transactionId)
            override fun openReview() = shell.openSection(Section.Review)
            override fun openHistory() = shell.openSection(Section.History)
            override fun openSubscriptions() = shell.openSection(Section.Subscriptions)
            override fun add() = addForm.openAdd()
        }
    }
    OverviewScreen(
        state = state,
        addState = addState,
        addActions = addForm,
        actions = actions,
        now = viewModel.now(),
        zone = viewModel.zone,
        modifier = modifier,
    )
}

@Composable
fun OverviewScreen(
    state: OverviewUiState,
    addState: TransactionFormState,
    addActions: TransactionFormActions,
    actions: OverviewActions,
    now: Instant,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    layout: AppLayout = LocalAppLayout.current,
) {
    if (state.loading) {
        Box(modifier.fillMaxSize())
        return
    }
    // In a pane the form closes with «Назад» (on the outer screen the sheet handles it itself).
    BackHandler(enabled = addState.isOpen && layout !is AppLayout.Compact) { addActions.close() }
    when (layout) {
        is AppLayout.Compact -> {
            CompactOverview(state, actions, zone, modifier)
            TransactionFormSheet(addState, addActions, now, zone)
        }
        is AppLayout.Book -> TwoPane(
            modifier = modifier.fillMaxSize(),
            first = {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (state.hasAnyTransactions) {
                        Summary(state, zone, todayStyle = MoneyStyle.DisplayMedium, twoLineBalances = true)
                        SubscriptionsCard(state, actions::openSubscriptions)
                        PeriodSelector(selected = state.period, onSelect = actions::setPeriod, periods = OverviewPeriods)
                        ChartCard(state, actions)
                        CategoryBreakdown(state, actions)
                    } else {
                        PendingSummary(state)
                    }
                }
            },
            second = {
                if (addState.isOpen) {
                    TransactionForm(
                        addState, addActions, now, zone,
                        modifier = Modifier.fillMaxSize(),
                        fillHeight = true,
                        contentPadding = PaddingValues(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
                    )
                } else {
                    ListPane(state, actions, zone, Modifier.fillMaxSize(), contentStart = 12.dp, contentEnd = 24.dp)
                }
            },
        )
        is AppLayout.Tabletop -> TwoPane(
            modifier = modifier.fillMaxSize(),
            first = {
                if (state.hasAnyTransactions) {
                    Box(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Summary(state, zone, todayStyle = MoneyStyle.DisplayMedium, twoLineBalances = true, modifier = Modifier.weight(1f).fillMaxHeight())
                            ChartCard(state, actions, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.BottomStart) {
                        PendingSummary(state)
                    }
                }
            },
            second = {
                if (addState.isOpen) {
                    TransactionForm(addState, addActions, now, zone, Modifier.fillMaxSize(), fillHeight = true)
                } else {
                    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
                        if (state.hasAnyTransactions) {
                            PeriodSelector(
                                selected = state.period,
                                onSelect = actions::setPeriod,
                                periods = OverviewPeriods,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            CategoryFilterStrip(state, actions)
                        }
                        ListPane(state, actions, zone, Modifier.weight(1f), contentStart = 24.dp, contentEnd = 24.dp, showTitle = false)
                        ThumbActions(state.reviewCount, actions, Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                    }
                }
            },
        )
    }
}

@Composable
private fun CompactOverview(state: OverviewUiState, actions: OverviewActions, zone: ZoneId, modifier: Modifier) {
    val listState = rememberLazyListState()
    Column(modifier.fillMaxSize()) {
        if (!state.hasAnyTransactions) {
            Column(Modifier.weight(1f).padding(horizontal = 20.dp).padding(top = 16.dp)) {
                PendingSummary(state)
                // The invitation sits in the lower part of the screen: the thumb zone (docs/design.md).
                Spacer(Modifier.weight(1f))
                OverviewEmptyState(onAdd = actions::add)
                Spacer(Modifier.height(24.dp))
            }
            return@Column
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        ) {
            item(key = "summary") { Summary(state, zone) }
            // The outer-screen bar has five items; «Подписки» live here (and in «Настройки»).
            item(key = "subscriptions") { SubscriptionsCard(state, actions::openSubscriptions, Modifier.padding(top = 12.dp)) }
            item(key = "feedHeader") {
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    title = stringResource(R.string.overview_feed_title),
                    actionLabel = stringResource(R.string.overview_feed_all),
                    onAction = actions::openHistory,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            if (state.transactions.isEmpty()) {
                item(key = "feedEmpty") {
                    Text(
                        text = stringResource(R.string.overview_feed_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = FoldFinanceTheme.palette.textMuted,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            }
            feed(state, state.transactions.take(FeedSize), zone, actions::openTransaction)
        }
        ThumbActions(state.reviewCount, actions, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp))
    }
}

/** Grouped rows of [items] (day headers + list groups). */
private fun LazyListScope.feed(
    state: OverviewUiState,
    items: List<Transaction>,
    zone: ZoneId,
    onOpen: (Long) -> Unit,
) {
    groupByDay(items, zone).forEach { (date, dayItems) ->
        item(key = "d-${date.toEpochDay()}", contentType = "header") {
            DayHeader(title = dayTitle(date, state.today), totalMinor = null, currency = null)
        }
        itemsIndexed(dayItems, key = { _, tx -> tx.id }, contentType = { _, _ -> "row" }) { index, tx ->
            TransactionListItem(
                transaction = tx,
                category = tx.categoryId?.let(state.categories::get),
                zone = zone,
                index = index,
                groupSize = dayItems.size,
                onClick = { onOpen(tx.id) },
            )
        }
    }
}

/** «Разобрать N» (when there is anything to review) and the FAB «Добавить», in the thumb zone. */
@Composable
private fun ThumbActions(reviewCount: Int, actions: OverviewActions, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (reviewCount > 0) {
            ReviewButton(reviewCount, actions::openReview, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
        } else {
            Spacer(Modifier.weight(1f))
        }
        FloatingActionButton(
            onClick = actions::add,
            shape = FoldShapes.fab,
            containerColor = if (reviewCount > 0) palette.surfaceRaised else palette.saffron,
            contentColor = if (reviewCount > 0) palette.text else palette.onSaffron,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.tx_add))
        }
    }
}

@Composable
private fun ReviewButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val palette = FoldFinanceTheme.palette
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (compact) 48.dp else 56.dp),
        shape = FoldShapes.pill,
        colors = ButtonDefaults.buttonColors(containerColor = palette.saffron, contentColor = palette.onSaffron),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        Icon(Icons.Outlined.TaskAlt, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.overview_review_button, count), style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * «Сегодня / Месяц» by known operations; the known bank balances go under the footnote, as a
 * separate statement of the bank — never added to the totals.
 */
@Composable
private fun Summary(
    state: OverviewUiState,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    todayStyle: MoneyStyle = MoneyStyle.Display,
    twoLineBalances: Boolean = false,
) {
    SummaryCard(
        periodTitle = monthTitle(state.today),
        todayMinor = state.todayMinor,
        monthMinor = state.monthMinor,
        currency = state.currency,
        modifier = modifier.fillMaxWidth(),
        todayStyle = todayStyle,
        trailing = if (state.balances.isNotEmpty()) {
            { Balances(state.balances, state.today, zone, twoLine = twoLineBalances) }
        } else {
            null
        },
    )
}

/**
 * Entry to «Подписки»: what confirmed subscriptions cost per month, or how many candidates wait
 * for a decision. A tap opens the section.
 */
@Composable
private fun SubscriptionsCard(state: OverviewUiState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    val summary = state.subscriptions
    val caption = when {
        summary.confirmed > 0 && summary.monthlyMinor > 0 -> stringResource(R.string.overview_subscriptions_monthly)
        summary.confirmed > 0 -> pluralStringResource(R.plurals.overview_subscriptions_confirmed, summary.confirmed, summary.confirmed)
        summary.candidates > 0 -> pluralStringResource(R.plurals.overview_subscriptions_candidates, summary.candidates, summary.candidates)
        else -> stringResource(R.string.overview_subscriptions_empty)
    }
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = FoldShapes.listGroup,
        color = palette.surface,
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).background(palette.saffron.copy(alpha = if (palette.isDark) 0.18f else 0.14f), FoldShapes.tile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Autorenew, contentDescription = null, tint = palette.saffron, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            LabeledAmount(
                modifier = Modifier.weight(1f),
                label = {
                    Column {
                        Text(stringResource(R.string.overview_subscriptions_title), style = MaterialTheme.typography.bodyLarge, color = palette.text, maxLines = 1)
                        Text(caption, style = MaterialTheme.typography.bodyMedium, color = palette.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                },
                amount = {
                    if (summary.monthlyMinor > 0) {
                        MoneyText(amountMinor = summary.monthlyMinor, currency = state.currency, style = MoneyStyle.InlineStrong)
                    }
                },
            )
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = palette.textMuted,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** Before the first operation: an honest card instead of zeros. */
@Composable
private fun PendingSummary(state: OverviewUiState) {
    val palette = FoldFinanceTheme.palette
    Column {
        Text(
            text = monthTitle(state.today),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
            color = palette.text,
        )
        Spacer(Modifier.height(16.dp))
        Surface(Modifier.fillMaxWidth(), shape = FoldShapes.summary, color = palette.surface) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.overview_summary_title), style = MaterialTheme.typography.titleLarge, color = palette.text)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.overview_summary_pending), style = MaterialTheme.typography.bodyLarge, color = palette.textMuted)
            }
        }
    }
}

@Composable
private fun OverviewEmptyState(onAdd: () -> Unit) {
    EmptyState(
        title = stringResource(R.string.overview_empty_title),
        body = stringResource(R.string.overview_empty_body),
        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
        actionLabel = stringResource(R.string.overview_empty_action),
        onAction = onAdd,
    )
}

@Composable
private fun ChartCard(state: OverviewUiState, actions: OverviewActions, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    Surface(modifier.fillMaxWidth(), shape = FoldShapes.listGroup, color = palette.surface) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)) {
            Text(periodCaption(state.period, state.periodStart), style = MaterialTheme.typography.titleMedium, color = palette.text)
            Spacer(Modifier.height(2.dp))
            MoneyText(amountMinor = state.periodExpenseMinor, currency = state.currency, style = MoneyStyle.DisplaySmall)
            Spacer(Modifier.height(14.dp))
            SpendingBars(
                bars = state.bars,
                period = state.period,
                selectedKey = state.selectedBar,
                onSelect = actions::toggleBar,
                currency = state.currency,
            )
        }
    }
}

@Composable
private fun periodCaption(period: Period, start: LocalDate): String = when (period) {
    Period.Week -> stringResource(R.string.overview_period_week)
    Period.Year -> stringResource(R.string.overview_period_year, start.year)
    else -> stringResource(R.string.overview_period_month, monthTitle(start).lowercase(com.foldfinance.app.ui.format.RuLocale))
}

@Composable
private fun CategoryBreakdown(state: OverviewUiState, actions: OverviewActions) {
    if (state.breakdown.isEmpty()) return
    val palette = FoldFinanceTheme.palette
    Column {
        SectionHeader(title = stringResource(R.string.overview_categories_title), modifier = Modifier.padding(start = 4.dp))
        ListGroup(Modifier.fillMaxWidth()) {
            state.breakdown.forEachIndexed { index, share ->
                if (index > 0) ListGroupDivider()
                val category = share.categoryId?.let(state.categories::get)
                val visual = categoryVisualOrNone(category)
                val selection = share.categoryId?.let { CategorySelection.Of(it) } ?: CategorySelection.Uncategorized
                val selected = state.selectedCategory == selection
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (selected) Modifier.background(palette.saffronContainer.copy(alpha = 0.55f)) else Modifier)
                        .clickable(role = Role.Checkbox) { actions.toggleCategory(selection) }
                        .semantics { this.selected = selected }
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryTile(visual)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(visual.name, style = MaterialTheme.typography.bodyLarge, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).background(palette.surfaceRaised, FoldShapes.pill)) {
                            Box(
                                Modifier
                                    .fillMaxWidth(share.percent.coerceIn(1, 100) / 100f)
                                    .fillMaxHeight()
                                    .background(visual.color, FoldShapes.pill),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        MoneyText(amountMinor = share.amountMinor, currency = state.currency, style = MoneyStyle.Inline)
                        Text(stringResource(R.string.overview_share_percent, share.percent), style = MaterialTheme.typography.labelMedium, color = palette.textMuted)
                    }
                }
            }
        }
    }
}

/** Chips of the period's categories in tabletop: a tap filters the list below. */
@Composable
private fun CategoryFilterStrip(state: OverviewUiState, actions: OverviewActions) {
    if (state.breakdown.isEmpty()) return
    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.breakdown, key = { it.categoryId ?: -1L }) { share ->
            val visual = categoryVisualOrNone(share.categoryId?.let(state.categories::get))
            val selection = share.categoryId?.let { CategorySelection.Of(it) } ?: CategorySelection.Uncategorized
            CategoryChip(
                name = visual.name,
                color = visual.color,
                selected = state.selectedCategory == selection,
                onClick = { actions.toggleCategory(selection) },
            )
        }
    }
}

/**
 * Known bank balances, never mixed with totals: «Остаток •• 1234 на 14:05». Caption and amount
 * side by side when every line fits; otherwise all captions go over their amounts, so nothing is
 * cut («Остато по дан…» on the 674 dp inner screen) and the lines look alike.
 */
@Composable
private fun Balances(balances: List<KnownBalance>, today: LocalDate, zone: ZoneId, twoLine: Boolean) {
    val palette = FoldFinanceTheme.palette
    Column {
        HorizontalDivider(thickness = 1.dp, color = palette.seam.copy(alpha = 0.7f))
        Spacer(Modifier.height(8.dp))
        LabeledAmountGroup(
            count = balances.size,
            modifier = Modifier.fillMaxWidth(),
            rowMinHeight = if (twoLine) 44.dp else 32.dp,
            rowSpacing = if (twoLine) 8.dp else 4.dp,
            label = { index ->
                val balance = balances[index]
                val date = balance.asOf.atZone(zone).toLocalDate()
                val time = formatTime(balance.asOf, zone)
                val at = if (date == today) time else stringResource(R.string.tx_date_time, formatDate(date, today), time)
                val mask = CardMaskFormat.format(balance.cardMask)
                if (twoLine) {
                    Column {
                        Text(
                            text = stringResource(R.string.overview_balance, mask),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.text,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Text(
                            text = stringResource(R.string.overview_balance_at, at),
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textMuted,
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.overview_balance_inline, mask, at),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textMuted,
                    )
                }
            },
            amount = { index ->
                val balance = balances[index]
                MoneyText(amountMinor = balance.amountMinor, currency = balance.currency, style = MoneyStyle.Inline)
            },
        )
    }
}

/** Right pane of the book layout (and the bottom of tabletop): the list the left side filters. */
@Composable
private fun ListPane(
    state: OverviewUiState,
    actions: OverviewActions,
    zone: ZoneId,
    modifier: Modifier,
    contentStart: androidx.compose.ui.unit.Dp,
    contentEnd: androidx.compose.ui.unit.Dp,
    showTitle: Boolean = true,
) {
    val palette = FoldFinanceTheme.palette
    val chips = selectionChips(state, actions)
    Box(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = contentStart, end = contentEnd, top = if (showTitle) 16.dp else 4.dp, bottom = if (showTitle) 96.dp else 8.dp),
        ) {
            if (showTitle) {
                item(key = "title") {
                    // In a narrow pane «Разобрать N» goes under the title instead of breaking it.
                    FlowRow(
                        Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.overview_list_title),
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineLarge,
                            color = palette.text,
                            maxLines = 1,
                        )
                        if (state.reviewCount > 0) ReviewButton(state.reviewCount, actions::openReview, compact = true)
                    }
                }
            }
            if (chips.isNotEmpty()) {
                item(key = "selection") {
                    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chips.forEach { (label, onClear) -> SelectionChip(label, onClear) }
                    }
                }
            }
            when {
                !state.hasAnyTransactions -> item(key = "empty") {
                    Box(Modifier.padding(top = 24.dp)) { OverviewEmptyState(onAdd = actions::add) }
                }
                state.transactions.isEmpty() -> item(key = "none") {
                    Text(
                        text = stringResource(if (chips.isEmpty()) R.string.overview_feed_empty else R.string.overview_list_nothing),
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.textMuted,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                    )
                }
                else -> feed(state, state.transactions, zone, actions::openTransaction)
            }
        }
        if (showTitle) {
            FloatingActionButton(
                onClick = actions::add,
                shape = FoldShapes.fab,
                containerColor = palette.saffron,
                contentColor = palette.onSaffron,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = contentEnd, bottom = 16.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.tx_add))
            }
        }
    }
}

@Composable
private fun selectionChips(state: OverviewUiState, actions: OverviewActions): List<Pair<String, () -> Unit>> {
    val result = ArrayList<Pair<String, () -> Unit>>()
    state.selectedCategory?.let { selection ->
        val category = (selection as? CategorySelection.Of)?.let { state.categories[it.categoryId] }
        result += categoryVisualOrNone(category).name to { actions.toggleCategory(selection) }
    }
    state.selectedBar?.let { key ->
        val date = LocalDate.ofEpochDay(key)
        val label = if (state.period == Period.Year) monthTitle(date) else formatDate(date, state.today)
        result += label to { actions.toggleBar(key) }
    }
    return result
}

/** An active selection from the left side; a tap clears it. */
@Composable
private fun SelectionChip(label: String, onClear: () -> Unit) {
    val palette = FoldFinanceTheme.palette
    val clear = stringResource(R.string.overview_selection_clear)
    Surface(onClick = onClear, shape = FoldShapes.pill, color = palette.saffronContainer, modifier = Modifier.heightIn(min = 40.dp)) {
        Row(Modifier.heightIn(min = 40.dp).padding(start = 14.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = palette.onSaffronContainer)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.Close, contentDescription = clear, modifier = Modifier.size(16.dp), tint = palette.onSaffronContainer)
        }
    }
}