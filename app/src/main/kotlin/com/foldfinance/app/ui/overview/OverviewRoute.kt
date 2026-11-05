package com.foldfinance.app.ui.overview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldfinance.app.R
import com.foldfinance.app.ai.features.common.AiAvailability
import com.foldfinance.app.ai.features.query.ChatScope
import com.foldfinance.app.ui.ai.anomalies.AnomaliesCard
import com.foldfinance.app.ui.ai.chat.AiChatLauncher
import com.foldfinance.app.ui.ai.chat.AiChatLauncherButton
import com.foldfinance.app.ui.ai.chat.AiChatPane
import com.foldfinance.app.ui.ai.chat.AiChatViewModel
import com.foldfinance.app.ui.ai.chat.AiChatSheet
import com.foldfinance.app.ui.ai.insight.MonthlyInsightCard
import com.foldfinance.app.ui.components.AccentFab
import com.foldfinance.app.ui.components.CategoryChip
import com.foldfinance.app.ui.components.ChipStyle
import com.foldfinance.app.ui.components.EmptyState
import com.foldfinance.app.ui.components.FilterPill
import com.foldfinance.app.ui.components.IconCircleButton
import com.foldfinance.app.ui.components.LabeledAmountGroup
import com.foldfinance.app.ui.components.MoneyStyle
import com.foldfinance.app.ui.components.MoneyText
import com.foldfinance.app.ui.components.Panel
import com.foldfinance.app.ui.components.Period
import com.foldfinance.app.ui.components.PeriodSelector
import com.foldfinance.app.ui.components.ScreenTitle
import com.foldfinance.app.ui.components.SectionHeader
import com.foldfinance.app.ui.components.StatTile
import com.foldfinance.app.ui.components.TileRow
import com.foldfinance.app.ui.components.moneyLabel
import com.foldfinance.app.ui.format.CardMaskFormat
import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.app.ui.format.RuLocale
import com.foldfinance.app.ui.format.SignDisplay
import com.foldfinance.app.ui.format.monthTitle
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.layout.TwoPane
import com.foldfinance.app.ui.shell.LocalShellNavigation
import com.foldfinance.app.ui.shell.Section
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.app.ui.theme.FoldTextStyles
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
import com.foldfinance.core.domain.analytics.AnalyticsCalculator
import com.foldfinance.core.model.KnownBalance
import com.foldfinance.core.model.Transaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

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

/**
 * «Обзор» / «Главная» (docs/design-v2: Main, Overview).
 *
 * - Outer screen: the month, «Потрачено за месяц» big, income as a line, the mini chart of the
 *   month's days, the «Сегодня» and «На проверку» tiles, «Последние»; the lime «+» floats in the
 *   thumb zone over the navigation bar.
 * - Book: the summary panel (period, «Потрачено», the chart with the selected category drawn in
 *   its colour, the share strip, category filters) and the operations panel next to it, split on
 *   the hinge; a tap on a category or a bar filters the list in place. The add form opens in the
 *   right panel.
 * - Tabletop: «Потрачено» and the chart on top (to look at); period, category filters and the list
 *   in the bottom panel (to touch).
 *
 * Local model: the monthly summary and «Стоит проверить» follow the «Сегодня / На проверку» tiles
 * outside and close the summary panel inside; «Спросить» sits next to search in the outer header
 * and opens the chat as a sheet. All of it is absent unless the model is ready.
 */
@Composable
fun OverviewRoute(
    modifier: Modifier = Modifier,
    viewModel: OverviewViewModel = hiltViewModel(),
    addForm: TransactionFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val addState by addForm.state.collectAsStateWithLifecycle()
    val chatShown by viewModel.chatShown.collectAsStateWithLifecycle()
    val chatVm: AiChatViewModel = hiltViewModel(key = AiChatViewModel.KEY)
    val chatState by chatVm.state.collectAsStateWithLifecycle()
    val chatReady = chatState.availability == AiAvailability.Ready
    val layout = LocalAppLayout.current
    // The conversation follows the fold: the pane inside becomes the sheet outside and back.
    LaunchedEffect(layout is AppLayout.Compact, chatShown, chatState.sheetOpen) {
        if (layout is AppLayout.Compact && chatShown) {
            viewModel.showChat(false)
            chatVm.actions.setSheetOpen(true)
        } else if (layout !is AppLayout.Compact && chatState.sheetOpen) {
            chatVm.actions.setSheetOpen(false)
            viewModel.showChat(true)
        }
    }
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
        aiCards = { cardModifier ->
            MonthlyInsightCard(cardModifier)
            AnomaliesCard(cardModifier)
        },
        chatAction = { m ->
            if (layout is AppLayout.Compact) {
                AiChatLauncher(m, viewModel = chatVm)
            } else if (chatReady) {
                AiChatLauncherButton(onClick = { viewModel.showChat(true) }, modifier = m)
            }
        },
        chatShown = chatShown && chatReady,
        onCloseChat = { viewModel.showChat(false) },
        chatPane = { m -> AiChatPane(scope = ChatScope(), modifier = m, viewModel = chatVm) },
    )
    // The chat sheet of the outer screen; its open state lives in the chat ViewModel (SavedStateHandle).
    if (layout is AppLayout.Compact) AiChatSheet(ChatScope(), viewModel = chatVm)
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
    aiCards: @Composable (cardModifier: Modifier) -> Unit = {},
    chatAction: @Composable (Modifier) -> Unit = {},
    chatShown: Boolean = false,
    onCloseChat: () -> Unit = {},
    chatPane: @Composable (Modifier) -> Unit = {},
) {
    if (state.loading) {
        Box(modifier.fillMaxSize())
        return
    }
    // In a pane the form closes with «Назад» (on the outer screen the sheet handles it itself).
    BackHandler(enabled = addState.isOpen && layout !is AppLayout.Compact) { addActions.close() }
    BackHandler(enabled = chatShown && !addState.isOpen && layout is AppLayout.Book) { onCloseChat() }
    when (layout) {
        is AppLayout.Compact -> {
            CompactOverview(state, actions, zone, modifier, aiCards, chatAction)
            TransactionFormSheet(addState, addActions, now, zone)
        }
        is AppLayout.Book -> TwoPane(
            modifier = modifier.fillMaxSize().padding(top = 16.dp, bottom = 16.dp, end = 8.dp),
            first = {
                Panel(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 22.dp, vertical = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SummaryPanelContent(state, actions, zone, aiCards)
                    }
                }
            },
            second = {
                Panel(Modifier.fillMaxSize()) {
                    if (addState.isOpen) {
                        TransactionForm(
                            addState, addActions, now, zone,
                            modifier = Modifier.fillMaxSize(),
                            fillHeight = true,
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 18.dp),
                        )
                    } else if (chatShown) {
                        Column(Modifier.fillMaxSize()) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 12.dp, end = 22.dp, top = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconCircleButton(
                                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.overview_chat_back),
                                    onClick = onCloseChat,
                                )
                                Spacer(Modifier.width(8.dp))
                                ScreenTitle(stringResource(R.string.overview_chat_title), Modifier.weight(1f))
                            }
                            chatPane(Modifier.weight(1f).fillMaxWidth())
                        }
                    } else {
                        ListPane(state, actions, zone, Modifier.fillMaxSize(), horizontal = 14.dp, headerAction = chatAction)
                    }
                }
            },
        )
        is AppLayout.Tabletop -> TwoPane(
            modifier = modifier.fillMaxSize(),
            first = {
                Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 8.dp)) {
                    if (state.hasAnyTransactions) {
                        SpentBlock(state, label = periodSpentLabel(state), amountMinor = state.periodExpenseMinor, style = MoneyStyle.Hero)
                        Spacer(Modifier.height(16.dp))
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                                SpendingBars(
                                    bars = state.bars,
                                    period = state.period,
                                    selectedKey = state.selectedBar,
                                    onSelect = actions::toggleBar,
                                    currency = state.currency,
                                    chartHeight = (maxHeight - 28.dp).coerceAtLeast(48.dp),
                                    highlightColor = selectedCategoryColor(state),
                                    today = state.today,
                                    gap = 6.dp,
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                )
                            }
                        }
                    } else {
                        PendingSummary(state)
                    }
                }
            },
            second = {
                Panel(Modifier.fillMaxSize(), shape = FoldShapes.sheet) {
                    if (addState.isOpen) {
                        TransactionForm(addState, addActions, now, zone, Modifier.fillMaxSize(), fillHeight = true, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp))
                    } else {
                        Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
                            if (state.hasAnyTransactions) {
                                PeriodSelector(
                                    selected = state.period,
                                    onSelect = actions::setPeriod,
                                    periods = OverviewPeriods,
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                )
                                Spacer(Modifier.height(12.dp))
                                CategoryFilterStrip(state, actions)
                            }
                            Box(Modifier.weight(1f)) {
                                ListPane(state, actions, zone, Modifier.fillMaxSize(), horizontal = 14.dp, showTitle = false)
                                AccentFab(
                                    icon = Icons.Outlined.Add,
                                    contentDescription = stringResource(R.string.tx_add),
                                    onClick = actions::add,
                                    ringColor = FoldFinanceTheme.palette.surface,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 12.dp),
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

// ---------------------------------------------------------------- Outer screen («Главная»)

@Composable
private fun CompactOverview(
    state: OverviewUiState,
    actions: OverviewActions,
    zone: ZoneId,
    modifier: Modifier,
    aiCards: @Composable (Modifier) -> Unit,
    chatAction: @Composable (Modifier) -> Unit,
) {
    val listState = rememberLazyListState()
    Box(modifier.fillMaxSize()) {
        if (!state.hasAnyTransactions) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 16.dp)) {
                DateHeader(state, actions)
                Spacer(Modifier.height(18.dp))
                PendingSummary(state)
                // The invitation sits in the lower part of the screen: the thumb zone.
                Spacer(Modifier.weight(1f))
                OverviewEmptyState(onAdd = actions::add)
                Spacer(Modifier.height(24.dp))
            }
            return@Box
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 104.dp),
        ) {
            item(key = "header") { DateHeader(state, actions, chatAction) }
            item(key = "spent") {
                Column(Modifier.padding(top = 18.dp)) {
                    SpentBlock(state, label = stringResource(R.string.overview_spent_month), amountMinor = state.monthMinor, style = MoneyStyle.Display)
                }
            }
            item(key = "mini") {
                SpendingBars(
                    bars = state.monthBars,
                    period = Period.Month,
                    selectedKey = null,
                    onSelect = null,
                    currency = state.currency,
                    chartHeight = 44.dp,
                    today = state.today,
                    accentToday = true,
                    showAxis = false,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
            item(key = "tiles") { TodayTiles(state, actions, Modifier.padding(top = 18.dp)) }
            // Hidden cards draw nothing, so the column collapses to zero height without the model.
            item(key = "ai") { Column { aiCards(Modifier.fillMaxWidth().padding(top = 10.dp)) } }
            if (state.balances.isNotEmpty()) {
                item(key = "balances") { Balances(state.balances, state.today, zone, Modifier.padding(top = 14.dp)) }
            }
            // The outer-screen bar has five items; «Подписки» live here (and in «Настройки»).
            item(key = "subscriptions") { SubscriptionsRow(state, actions::openSubscriptions, Modifier.padding(top = 10.dp)) }
            item(key = "recentHeader") {
                SectionHeader(
                    title = stringResource(R.string.overview_recent_title),
                    actionLabel = stringResource(R.string.overview_recent_all),
                    onAction = actions::openHistory,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (state.recent.isEmpty()) {
                item(key = "recentEmpty") {
                    Text(
                        text = stringResource(R.string.overview_feed_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FoldFinanceTheme.palette.textMuted,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(state.recent, key = { it.id }, contentType = { "row" }) { tx ->
                TransactionListItem(
                    transaction = tx,
                    category = tx.categoryId?.let(state.categories::get),
                    zone = zone,
                    index = 0,
                    groupSize = 1,
                    onClick = { actions.openTransaction(tx.id) },
                    horizontalPadding = 0.dp,
                )
            }
        }
        // Thumb zone: the lime «+» right above the navigation bar, cut out of the list by its ring.
        AccentFab(
            icon = Icons.Outlined.Add,
            contentDescription = stringResource(R.string.tx_add),
            onClick = actions::add,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 12.dp),
        )
    }
}

/** «Четверг, 5 ноября» / «Ноябрь», «Спросить» (local model) and the search button. */
@Composable
private fun DateHeader(state: OverviewUiState, actions: OverviewActions, chatAction: @Composable (Modifier) -> Unit = {}) {
    val palette = FoldFinanceTheme.palette
    val weekday = state.today.dayOfWeek.getDisplayName(TextStyle.FULL, RuLocale).replaceFirstChar { it.titlecase(RuLocale) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.overview_date_header, weekday, formatDate(state.today, state.today)),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
            )
            ScreenTitle(monthTitle(state.today))
        }
        if (state.hasAnyTransactions) {
            chatAction(Modifier.padding(end = 8.dp))
            IconCircleButton(
                icon = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.overview_search),
                onClick = actions::openHistory,
            )
        }
    }
}

/** «Потрачено за месяц» · the big amount · «+95 000 ₽ доход · по известным операциям». */
@Composable
private fun SpentBlock(state: OverviewUiState, label: String, amountMinor: Long, style: MoneyStyle, incomeMinor: Long = state.monthIncomeMinor) {
    val palette = FoldFinanceTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
        MoneyText(amountMinor = amountMinor, currency = state.currency, style = style)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (incomeMinor > 0) {
                Text(
                    moneyLabel(incomeMinor, state.currency, SignDisplay.Always),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.income,
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                stringResource(if (incomeMinor > 0) R.string.overview_income_known else R.string.overview_known_only),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** «Сегодня 1 840 ₽ · 3 расхода» and «На проверку 3 · Разобрать свайпом →». */
@Composable
private fun TodayTiles(state: OverviewUiState, actions: OverviewActions, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    TileRow(modifier) {
        StatTile(Modifier.weight(1f)) {
            Text(stringResource(R.string.overview_today_tile), style = FoldTextStyles.caption, color = palette.textSecondary)
            MoneyText(amountMinor = state.todayMinor, currency = state.currency, style = MoneyStyle.Tile)
            Text(
                if (state.todayExpenseCount > 0) {
                    pluralStringResource(R.plurals.overview_today_expenses, state.todayExpenseCount, state.todayExpenseCount)
                } else {
                    stringResource(R.string.overview_today_none)
                },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                color = palette.textMuted,
            )
        }
        val count = state.reviewCount
        StatTile(Modifier.weight(1f), onClick = actions::openReview) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (count > 0) {
                    Box(Modifier.size(7.dp).background(palette.review, FoldShapes.pill))
                    Spacer(Modifier.width(6.dp))
                }
                Text(stringResource(R.string.overview_review_tile), style = FoldTextStyles.caption, color = palette.textSecondary)
            }
            Text(
                count.toString(),
                style = FoldFinanceTheme.money.tile,
                color = if (count > 0) palette.review else palette.textDisabled,
            )
            Text(
                stringResource(if (count > 0) R.string.overview_review_tile_action else R.string.overview_review_tile_done),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                color = palette.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Entry to «Подписки» on the outer screen (its bar has no such item): what confirmed
 * subscriptions cost per month, or how many candidates wait for a decision.
 */
@Composable
private fun SubscriptionsRow(state: OverviewUiState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    val summary = state.subscriptions
    val caption = when {
        summary.confirmed > 0 && summary.monthlyMinor > 0 -> stringResource(R.string.overview_subscriptions_monthly)
        summary.confirmed > 0 -> pluralStringResource(R.plurals.overview_subscriptions_confirmed, summary.confirmed, summary.confirmed)
        summary.candidates > 0 -> pluralStringResource(R.plurals.overview_subscriptions_candidates, summary.candidates, summary.candidates)
        else -> stringResource(R.string.overview_subscriptions_empty)
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(FoldShapes.listGroup)
            .background(palette.card)
            .clickable(role = Role.Button, onClick = onOpen)
            .heightIn(min = 60.dp)
            .padding(start = 14.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val violet = palette.categories.forKey("subscriptions")
        Box(Modifier.size(42.dp).background(violet.copy(alpha = 0.15f), FoldShapes.tile), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Autorenew, contentDescription = null, tint = violet, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.overview_subscriptions_title), style = FoldTextStyles.row, color = palette.text, maxLines = 1)
            Text(caption, style = FoldTextStyles.caption, color = palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (summary.monthlyMinor > 0) {
            Spacer(Modifier.width(8.dp))
            MoneyText(amountMinor = summary.monthlyMinor, currency = state.currency, style = MoneyStyle.Inline)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = palette.textMuted, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Before the first operation: an honest statement instead of zeros. */
@Composable
private fun PendingSummary(state: OverviewUiState) {
    val palette = FoldFinanceTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.overview_spent_month), style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
        Text(
            stringResource(R.string.overview_summary_pending),
            style = MaterialTheme.typography.bodyLarge,
            color = palette.textMuted,
        )
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

// ---------------------------------------------------------------- Summary panel (book)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryPanelContent(
    state: OverviewUiState,
    actions: OverviewActions,
    zone: ZoneId,
    aiCards: @Composable (Modifier) -> Unit = {},
) {
    val palette = FoldFinanceTheme.palette
    // In a narrow panel the day range goes under the period chip instead of squeezing it.
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        PeriodMenu(state, actions)
        daysRange(state)?.let {
            Text(it, style = FoldTextStyles.caption, color = palette.textMuted, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
    }
    if (!state.hasAnyTransactions) {
        PendingSummary(state)
        return
    }
    SpentBlock(
        state,
        label = stringResource(R.string.overview_spent),
        amountMinor = state.periodExpenseMinor,
        style = MoneyStyle.Display,
        incomeMinor = if (state.period == Period.Month) state.monthIncomeMinor else 0,
    )
    SpendingBars(
        bars = state.bars,
        period = state.period,
        selectedKey = state.selectedBar,
        onSelect = actions::toggleBar,
        currency = state.currency,
        chartHeight = 120.dp,
        highlightColor = selectedCategoryColor(state),
        today = state.today,
    )
    if (state.balances.isNotEmpty()) Balances(state.balances, state.today, zone)
    CategoryBreakdown(state, actions)
    aiCards(Modifier.fillMaxWidth())
}

/** «Ноябрь 2026 ⌄»: the period of the overview, a tap opens the choice of week / month / year. */
@Composable
private fun PeriodMenu(state: OverviewUiState, actions: OverviewActions) {
    val palette = FoldFinanceTheme.palette
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .heightIn(min = 36.dp)
                .clip(FoldShapes.chip)
                .background(palette.surfaceRaised)
                .clickable(role = Role.DropdownList, onClickLabel = stringResource(R.string.overview_period_choose)) { open = true }
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(periodTitle(state.period, state.periodStart), style = MaterialTheme.typography.titleSmall, color = palette.text, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = palette.card, shape = FoldShapes.tile) {
            OverviewPeriods.forEach { period ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(period.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (period == state.period) palette.accentContent else palette.text,
                        )
                    },
                    onClick = {
                        open = false
                        actions.setPeriod(period)
                    },
                )
            }
        }
    }
}

@Composable
private fun periodTitle(period: Period, start: LocalDate): String = when (period) {
    Period.Week -> stringResource(R.string.overview_period_week_title)
    Period.Year -> stringResource(R.string.overview_period_year_title, start.year)
    else -> stringResource(R.string.overview_period_month_title, monthTitle(start), start.year)
}

/** «Потрачено» in tabletop names the period, the hero number above the chart. */
@Composable
private fun periodSpentLabel(state: OverviewUiState): String = when (state.period) {
    Period.Week -> stringResource(R.string.overview_period_week)
    Period.Year -> stringResource(R.string.overview_period_year, state.periodStart.year)
    else -> stringResource(R.string.overview_period_month, monthTitle(state.periodStart).lowercase(RuLocale))
}

/** «1–5 ноября»: the days the totals cover so far. */
@Composable
private fun daysRange(state: OverviewUiState): String? {
    if (state.period == Period.Year || state.periodStart == LocalDate.MIN) return null
    val start = state.periodStart
    val end = state.today
    if (end.isBefore(start)) return null
    return if (start.month == end.month) {
        stringResource(R.string.overview_days_range, start.dayOfMonth, formatDate(end, end))
    } else {
        stringResource(R.string.overview_days_range_long, formatDate(start, end), formatDate(end, end))
    }
}

@Composable
private fun selectedCategoryColor(state: OverviewUiState): Color? {
    val selection = state.selectedCategory ?: return null
    val category = (selection as? CategorySelection.Of)?.let { state.categories[it.categoryId] }
    return categoryVisualOrNone(category).color
}

/**
 * «Категории»: the share strip and the category rows (dot, name, %, amount). A tap filters the
 * operations panel next to it; the active one is outlined in lime.
 */
@Composable
private fun CategoryBreakdown(state: OverviewUiState, actions: OverviewActions) {
    if (state.breakdown.isEmpty()) return
    val palette = FoldFinanceTheme.palette
    androidx.compose.foundation.layout.BoxWithConstraints {
    // Below ~300 dp (inner portrait, next to the hinge) the hint goes and the amounts move under the names.
    val narrow = maxWidth < 300.dp
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.overview_categories_title),
                style = MaterialTheme.typography.titleMedium,
                color = palette.text,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.weight(1f))
            if (!narrow) {
                Text(stringResource(R.string.overview_categories_hint), style = FoldTextStyles.caption, color = palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
            }
        }
        ShareStrip(state)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            state.breakdown.forEach { share ->
                val category = share.categoryId?.let(state.categories::get)
                val visual = categoryVisualOrNone(category)
                val selection = share.categoryId?.let { CategorySelection.Of(it) } ?: CategorySelection.Uncategorized
                val selected = state.selectedCategory == selection
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clip(FoldShapes.tile)
                        .then(if (selected) Modifier.background(palette.selection).border(1.dp, palette.accentLine, FoldShapes.tile) else Modifier)
                        .clickable(role = Role.Checkbox) { actions.toggleCategory(selection) }
                        .semantics { this.selected = selected }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).background(visual.color, FoldShapes.pill))
                    Spacer(Modifier.width(12.dp))
                    val name: @Composable (Modifier) -> Unit = { m ->
                        Text(
                            visual.name,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize * 1.04f),
                            color = palette.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = m,
                        )
                    }
                    val percent: @Composable () -> Unit = {
                        Text(
                            stringResource(R.string.overview_share_percent, share.percent).replace(" ", ""),
                            style = FoldTextStyles.caption,
                            color = palette.textMuted,
                            textAlign = TextAlign.End,
                            modifier = Modifier.widthIn(min = 40.dp),
                        )
                    }
                    if (narrow) {
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            name(Modifier)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MoneyText(amountMinor = share.amountMinor, currency = state.currency, style = MoneyStyle.Inline)
                                Spacer(Modifier.weight(1f))
                                percent()
                            }
                        }
                    } else {
                        name(Modifier.weight(1f))
                        percent()
                        Spacer(Modifier.width(8.dp))
                        MoneyText(amountMinor = share.amountMinor, currency = state.currency, style = MoneyStyle.Inline)
                    }
                }
            }
        }
    }
    }
}

/** 8 dp strip of category shares, in the order of the list below. */
@Composable
private fun ShareStrip(state: OverviewUiState) {
    val total = state.breakdown.sumOf { it.amountMinor }.coerceAtLeast(1)
    val colors = state.breakdown.map { share -> categoryVisualOrNone(share.categoryId?.let(state.categories::get)).color }
    Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        state.breakdown.forEachIndexed { index, share ->
            val weight = (share.amountMinor.toFloat() / total).coerceAtLeast(0.01f)
            Box(Modifier.weight(weight).fillMaxHeight().background(colors[index], FoldShapes.pill))
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
                style = ChipStyle.Outlined,
            )
        }
    }
}

/**
 * Known bank balances, never mixed with totals: «Остаток •• 1234 на 14:05 … 48 210 ₽». Caption and
 * amount side by side when every line fits; otherwise all captions go over their amounts.
 */
@Composable
private fun Balances(balances: List<KnownBalance>, today: LocalDate, zone: ZoneId, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    LabeledAmountGroup(
        count = balances.size,
        modifier = modifier.fillMaxWidth(),
        rowMinHeight = 26.dp,
        rowSpacing = 2.dp,
        label = { index ->
            val balance = balances[index]
            val date = balance.asOf.atZone(zone).toLocalDate()
            val time = formatTime(balance.asOf, zone)
            val at = if (date == today) time else stringResource(R.string.tx_date_time, formatDate(date, today), time)
            Text(
                text = stringResource(R.string.overview_balance_inline, CardMaskFormat.format(balance.cardMask), at),
                style = FoldTextStyles.caption,
                color = palette.textMuted,
            )
        },
        amount = { index ->
            val balance = balances[index]
            MoneyText(amountMinor = balance.amountMinor, currency = balance.currency, style = MoneyStyle.Inline, color = palette.textSoft)
        },
    )
}

// ---------------------------------------------------------------- Operations panel

/** Right panel of the book layout (and the bottom of tabletop): the list the left side filters. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListPane(
    state: OverviewUiState,
    actions: OverviewActions,
    zone: ZoneId,
    modifier: Modifier,
    horizontal: Dp,
    showTitle: Boolean = true,
    headerAction: @Composable (Modifier) -> Unit = {},
) {
    val palette = FoldFinanceTheme.palette
    val chips = selectionChips(state, actions)
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = horizontal, end = horizontal, top = if (showTitle) 20.dp else 8.dp, bottom = if (showTitle) 20.dp else 88.dp),
    ) {
        if (showTitle) {
            item(key = "title") {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ScreenTitle(stringResource(R.string.overview_list_title), Modifier.weight(1f))
                    if (state.hasAnyTransactions) {
                        headerAction(Modifier.padding(end = 8.dp))
                        IconCircleButton(
                            icon = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.overview_search),
                            onClick = actions::openHistory,
                        )
                    }
                }
            }
        }
        if (state.hasAnyTransactions) {
            item(key = "chips") {
                androidx.compose.foundation.layout.BoxWithConstraints {
                val wide = maxWidth >= 420.dp
                val summary = stringResource(
                    R.string.overview_list_summary,
                    pluralStringResource(R.plurals.overview_list_count, state.transactions.size, state.transactions.size),
                    moneyLabel(state.listExpenseMinor, state.currency),
                )
                Column {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    chips.forEach { chip ->
                        FilterPill(
                            text = chip.label,
                            onClick = chip.onClear,
                            active = true,
                            leading = chip.color?.let { color -> { Box(Modifier.size(8.dp).background(color, FoldShapes.pill)) } },
                            trailingIcon = Icons.Outlined.Close,
                            trailingDescription = stringResource(R.string.overview_selection_clear),
                        )
                    }
                    if (showTitle) {
                        Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                            Text(
                                periodTitle(state.period, state.periodStart),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                                color = palette.textSoft,
                                modifier = Modifier
                                    .background(palette.surfaceRaised, FoldShapes.chip)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                    if (wide) {
                        Box(Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.CenterEnd) {
                            Text(summary, style = MaterialTheme.typography.bodyMedium, color = palette.textMuted, maxLines = 1)
                        }
                    }
                }
                if (!wide) {
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = palette.textMuted, modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 6.dp))
                }
                }
                }
            }
        }
        when {
            !state.hasAnyTransactions -> item(key = "empty") {
                Box(Modifier.padding(horizontal = 8.dp, vertical = 24.dp)) { OverviewEmptyState(onAdd = actions::add) }
            }
            state.transactions.isEmpty() -> item(key = "none") {
                Text(
                    text = stringResource(if (chips.isEmpty()) R.string.overview_feed_empty else R.string.overview_list_nothing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.textMuted,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                )
            }
            else -> feed(state, state.transactions, zone, actions::openTransaction, palette.surface)
        }
    }
}

/** Grouped rows of [items]: overline day headers with the day's expenses, flat rows. */
private fun LazyListScope.feed(
    state: OverviewUiState,
    items: List<Transaction>,
    zone: ZoneId,
    onOpen: (Long) -> Unit,
    background: Color,
) {
    groupByDay(items, zone).forEach { (date, dayItems) ->
        item(key = "d-${date.toEpochDay()}", contentType = "header") {
            val total = dayItems.filter { it.currency == state.currency }.sumOf(AnalyticsCalculator::netExpenseContribution)
            DayHeader(title = dayTitle(date, state.today), totalMinor = total, currency = state.currency, background = background, modifier = Modifier.padding(top = 6.dp))
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

/** An active selection from the summary panel; a tap clears it. */
private data class SelectionChip(val label: String, val color: Color?, val onClear: () -> Unit)

@Composable
private fun selectionChips(state: OverviewUiState, actions: OverviewActions): List<SelectionChip> {
    val result = ArrayList<SelectionChip>()
    state.selectedCategory?.let { selection ->
        val category = (selection as? CategorySelection.Of)?.let { state.categories[it.categoryId] }
        val visual = categoryVisualOrNone(category)
        result += SelectionChip(visual.name, visual.color) { actions.toggleCategory(selection) }
    }
    state.selectedBar?.let { key ->
        val date = LocalDate.ofEpochDay(key)
        val label = if (state.period == Period.Year) monthTitle(date) else formatDate(date, state.today)
        result += SelectionChip(label, null) { actions.toggleBar(key) }
    }
    return result
}
