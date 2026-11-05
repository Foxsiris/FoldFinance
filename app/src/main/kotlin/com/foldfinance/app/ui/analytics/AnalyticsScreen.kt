package com.foldfinance.app.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.EmptyState
import com.foldfinance.app.ui.components.IconCircleButton
import com.foldfinance.app.ui.components.MoneyStyle
import com.foldfinance.app.ui.components.MoneyText
import com.foldfinance.app.ui.components.Overline
import com.foldfinance.app.ui.components.Panel
import com.foldfinance.app.ui.components.PeriodSelector
import com.foldfinance.app.ui.components.PillTag
import com.foldfinance.app.ui.components.ScreenTitle
import com.foldfinance.app.ui.components.SegmentedControl
import com.foldfinance.app.ui.components.ShareBar
import com.foldfinance.app.ui.components.StatTile
import com.foldfinance.app.ui.components.TileRow
import com.foldfinance.app.ui.components.categoryVisual
import com.foldfinance.app.ui.components.moneyLabel
import com.foldfinance.app.ui.components.visual
import com.foldfinance.app.ui.format.RuLocale
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.layout.TwoPane
import com.foldfinance.app.ui.layout.foldPaneScaffoldDirective
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.app.ui.theme.FoldTextStyles
import com.foldfinance.core.domain.analytics.AmountChange
import com.foldfinance.core.domain.analytics.PeriodType
import com.foldfinance.core.model.DefaultCategories
import com.foldfinance.core.model.Transaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import com.foldfinance.app.ui.components.Period as SelectorPeriod

/** User actions of the analytics screen. */
@Immutable
data class AnalyticsActions(
    val onSelectType: (PeriodType) -> Unit = {},
    val onSelectCustom: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onSelectBucket: (LocalDate) -> Unit = {},
    val onClearSelection: () -> Unit = {},
    val onSelectCurrency: (String) -> Unit = {},
)

/**
 * Stateless analytics screen in the three window layouts (Analytics and Tabletop mockups):
 *
 * - [AppLayout.Book] — `SupportingPaneScaffold` of two panels: the chart panel (period, the total
 *   with the change to the previous period, the chart with the average line, three tiles) and the
 *   supporting panel (the tapped day with its operations, the top categories). While the hinge is
 *   FLAT the chart panel is wider; when it separates the window (HALF_OPENED / `isSeparating`) the
 *   boundary moves onto the hinge ([foldPaneScaffoldDirective]).
 * - [AppLayout.Tabletop] — top half (to look at): the hero total and the chart; bottom panel (to
 *   touch): the period, the tapped day, the categories.
 * - [AppLayout.Compact] — one column; the period selector sits at the bottom, in the thumb zone.
 */
@Composable
fun AnalyticsScreen(
    state: AnalyticsUiState,
    actions: AnalyticsActions,
    modifier: Modifier = Modifier,
    layout: AppLayout = LocalAppLayout.current,
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val openPicker = { pickerOpen = true }
    when (layout) {
        is AppLayout.Book -> BookLayout(state, actions, openPicker, layout, modifier)
        is AppLayout.Tabletop -> TabletopLayout(state, actions, openPicker, modifier)
        is AppLayout.Compact -> CompactLayout(state, actions, openPicker, modifier)
    }
    if (pickerOpen) {
        CustomPeriodDialog(
            state = state,
            onDismiss = { pickerOpen = false },
            onConfirm = { start, end ->
                pickerOpen = false
                actions.onSelectCustom(start, end)
            },
        )
    }
}

// ---------------------------------------------------------------- Layouts

/** Width of the supporting panel while the hinge is FLAT (canvas note s2: the chart panel is wider). */
private const val SupportingShareFlat = 0.38f

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun BookLayout(state: AnalyticsUiState, actions: AnalyticsActions, openPicker: () -> Unit, layout: AppLayout.Book, modifier: Modifier) {
    val base = foldPaneScaffoldDirective()
    val directive = if (layout.separating) {
        // On the hinge: no partition spacer — each panel keeps 8 dp clear of it (16 dp gap).
        base.copy(horizontalPartitionSpacerSize = 0.dp)
    } else {
        // FLAT: the hinge is not a boundary; the chart panel takes ~62 %, panels 16 dp apart.
        base.copy(
            horizontalPartitionSpacerSize = 16.dp,
            excludedBounds = emptyList(),
            defaultPanePreferredWidth = ((layout.windowWidthDp - RailWidthDp) * SupportingShareFlat).dp,
        )
    }
    val navigator = rememberSupportingPaneScaffoldNavigator<Any>(scaffoldDirective = directive)
    val hingeGap = if (layout.separating) 8.dp else 0.dp
    SupportingPaneScaffold(
        directive = directive,
        scaffoldState = navigator.scaffoldState,
        modifier = modifier.fillMaxSize().padding(top = 16.dp, bottom = 16.dp, end = 8.dp),
        mainPane = {
            AnimatedPane {
                Panel(Modifier.fillMaxSize().padding(end = hingeGap)) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 20.dp)) {
                        ChartPanelHeader(state, actions, openPicker)
                        Spacer(Modifier.height(18.dp))
                        if (state.hasData) {
                            HeroTotals(state, actions, MoneyStyle.Display)
                            Spacer(Modifier.height(16.dp))
                            ChartArea(state, actions, Modifier.weight(1f).fillMaxWidth())
                            Spacer(Modifier.height(16.dp))
                            StatTiles(state)
                        } else if (!state.isLoading) {
                            AnalyticsEmpty(actions)
                        }
                    }
                }
            }
        },
        supportingPane = {
            AnimatedPane {
                Panel(Modifier.fillMaxSize().padding(start = hingeGap)) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        if (state.hasData) {
                            if (state.currencies.size > 1) CurrencySwitcher(state, actions)
                            SelectedDay(state, actions)
                            TopCategories(state)
                            Footnotes(state)
                        }
                    }
                }
            }
        },
    )
}

/** Width of the collapsed wide navigation rail next to the book panes. */
private const val RailWidthDp = 96f

@Composable
private fun TabletopLayout(state: AnalyticsUiState, actions: AnalyticsActions, openPicker: () -> Unit, modifier: Modifier) {
    TwoPane(
        modifier = modifier.fillMaxSize(),
        // Top half — to look at: the hero total and the chart, which takes all the remaining height.
        first = {
            Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 8.dp)) {
                if (state.hasData) {
                    HeroTotals(state, actions, MoneyStyle.Hero, showCurrencySwitcher = false, showAverage = true)
                    Spacer(Modifier.height(16.dp))
                    ChartArea(state, actions, Modifier.weight(1f).fillMaxWidth())
                } else if (!state.isLoading) {
                    PeriodTitleRow(state, actions)
                    Spacer(Modifier.height(12.dp))
                    AnalyticsEmpty(actions)
                }
            }
        },
        // Bottom half — to touch: period, currency, the tapped day, the categories.
        second = {
            Panel(Modifier.fillMaxSize(), shape = FoldShapes.sheet) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PeriodControls(state, actions, openPicker, height = 48.dp)
                    if (state.hasData) {
                        if (state.currencies.size > 1) CurrencySwitcher(state, actions)
                        if (state.selectedBucket != null || state.showsOperations) {
                            DayOperationsGrid(state, actions)
                        } else {
                            TopCategories(state, twoColumns = true)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun CompactLayout(state: AnalyticsUiState, actions: AnalyticsActions, openPicker: () -> Unit, modifier: Modifier) {
    val palette = FoldFinanceTheme.palette
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ScreenTitle(stringResource(R.string.analytics_title))
            if (state.hasData) {
                HeroTotals(state, actions, MoneyStyle.Display)
                if (state.buckets.isNotEmpty()) ChartArea(state, actions, Modifier.fillMaxWidth().height(220.dp))
                StatTiles(state, stacked = true)
                SelectedDay(state, actions)
                TopCategories(state)
                Footnotes(state)
            } else if (!state.isLoading) {
                PeriodTitleRow(state, actions)
                AnalyticsEmpty(actions)
            }
        }
        // Thumb zone: the period is switched at the bottom of the outer screen.
        Column(
            Modifier
                .fillMaxWidth()
                .background(palette.ink)
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        ) {
            PeriodControls(state, actions, openPicker, height = 48.dp)
        }
    }
}

// ---------------------------------------------------------------- Header and controls

/** «Аналитика» and the period segments on one line (in a narrow panel the segments go under it). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartPanelHeader(state: AnalyticsUiState, actions: AnalyticsActions, openPicker: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 480.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle(stringResource(R.string.analytics_title), Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                PeriodControls(state, actions, openPicker, height = 40.dp, modifier = Modifier.widthIn(max = 330.dp))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ScreenTitle(stringResource(R.string.analytics_title))
                PeriodControls(state, actions, openPicker, height = 40.dp)
            }
        }
    }
}

/** «День / Неделя / Месяц / Год / Свой». «Свой» opens the range picker. */
@Composable
private fun PeriodControls(
    state: AnalyticsUiState,
    actions: AnalyticsActions,
    openPicker: () -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val type = state.period.type
    PeriodSelector(
        selected = type.toSelector(),
        onSelect = { actions.onSelectType(it.toPeriodType()) },
        modifier = modifier,
        height = height,
        extra = listOf(stringResource(R.string.analytics_custom_short)),
        extraSelected = if (type == PeriodType.CUSTOM) 0 else -1,
        onExtra = { openPicker() },
    )
}

private fun PeriodType.toSelector(): SelectorPeriod? = when (this) {
    PeriodType.DAY -> SelectorPeriod.Day
    PeriodType.WEEK -> SelectorPeriod.Week
    PeriodType.MONTH -> SelectorPeriod.Month
    PeriodType.YEAR -> SelectorPeriod.Year
    PeriodType.CUSTOM -> null
}

private fun SelectorPeriod.toPeriodType(): PeriodType = when (this) {
    SelectorPeriod.Day -> PeriodType.DAY
    SelectorPeriod.Week -> PeriodType.WEEK
    SelectorPeriod.Month -> PeriodType.MONTH
    SelectorPeriod.Year -> PeriodType.YEAR
}

@Composable
private fun periodTitle(state: AnalyticsUiState): String = AnalyticsFormat.periodTitle(
    state.period,
    state.today,
    stringResource(R.string.analytics_today),
    stringResource(R.string.analytics_yesterday),
)

/** «Расходы · октябрь 2026» with ‹ › to step through periods. */
@Composable
private fun PeriodTitleRow(state: AnalyticsUiState, actions: AnalyticsActions) {
    val palette = FoldFinanceTheme.palette
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.analytics_expenses_of, periodTitle(state).lowercase(RuLocale)),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        IconCircleButton(
            icon = Icons.Outlined.ChevronLeft,
            contentDescription = stringResource(R.string.analytics_period_previous),
            onClick = actions.onPrevious,
            size = 32.dp,
            container = Color.Transparent,
            tint = palette.textSecondary,
        )
        if (state.canGoNext) {
            IconCircleButton(
                icon = Icons.Outlined.ChevronRight,
                contentDescription = stringResource(R.string.analytics_period_next),
                onClick = actions.onNext,
                size = 32.dp,
                container = Color.Transparent,
                tint = palette.textSecondary,
            )
        }
    }
}

// ---------------------------------------------------------------- Totals

/**
 * «Расходы · октябрь 2026», the total, the change to the previous period («↑ 12% к сентябрю») and,
 * in tabletop, «в среднем 2 303 ₽ в день».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroTotals(
    state: AnalyticsUiState,
    actions: AnalyticsActions,
    style: MoneyStyle,
    modifier: Modifier = Modifier,
    showCurrencySwitcher: Boolean = true,
    showAverage: Boolean = false,
) {
    val palette = FoldFinanceTheme.palette
    val summary = state.summary ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showCurrencySwitcher && state.currencies.size > 1 && LocalAppLayout.current is AppLayout.Compact) {
            CurrencySwitcher(state, actions)
        }
        PeriodTitleRow(state, actions)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            MoneyText(amountMinor = summary.netExpenseMinor, currency = summary.currency, style = style, modifier = Modifier.padding(end = 12.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.comparison?.netExpense?.let { change -> ChangeBadge(change, periodComparisonLabel(state.period.type)) }
                if (showAverage && summary.averageDailyNetExpenseMinor > 0) {
                    Text(
                        stringResource(R.string.analytics_average_per_day, moneyLabel(summary.averageDailyNetExpenseMinor, summary.currency)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencySwitcher(state: AnalyticsUiState, actions: AnalyticsActions) {
    val labels = state.currencies.map { "${com.foldfinance.app.ui.format.MoneyFormat.symbol(it.currency)}  ${it.currency}" }
    SegmentedControl(
        labels = labels,
        selected = state.currencies.indexOfFirst { it.currency == state.currency },
        onSelect = { actions.onSelectCurrency(state.currencies[it].currency) },
        height = 40.dp,
        description = stringResource(R.string.analytics_currency),
    )
}

/**
 * «↑ 12% к прошлому месяцу»: more expenses are warm (growth colour), less — quiet. Never red:
 * spending is not an alarm.
 */
@Composable
private fun ChangeBadge(change: AmountChange, suffix: String) {
    val palette = FoldFinanceTheme.palette
    val percent = change.percent
    val text = when {
        percent == null -> return
        change.deltaMinor == 0L -> "= $suffix"
        else -> (if (change.deltaMinor > 0) "↑ " else "↓ ") + AnalyticsFormat.percent(percent) + "% " + suffix
    }
    val color = if (change.deltaMinor > 0) palette.growth else palette.textSecondary
    PillTag(text, color = color, container = if (change.deltaMinor > 0) color.copy(alpha = 0.12f) else palette.surfaceRaised, large = true)
}

@Composable
private fun periodComparisonLabel(type: PeriodType): String = stringResource(
    when (type) {
        PeriodType.DAY -> R.string.analytics_previous_day
        PeriodType.WEEK -> R.string.analytics_previous_week
        PeriodType.MONTH -> R.string.analytics_previous_month
        PeriodType.YEAR -> R.string.analytics_previous_year
        PeriodType.CUSTOM -> R.string.analytics_previous_custom
    },
)

// ---------------------------------------------------------------- Chart

@Composable
private fun ChartArea(state: AnalyticsUiState, actions: AnalyticsActions, modifier: Modifier = Modifier) {
    val currency = state.currency ?: return
    if (state.buckets.isEmpty()) return
    val buckets = state.buckets
    val monthly = state.granularity == ChartGranularity.MONTH
    val week = state.period.type == PeriodType.WEEK
    val description = stringResource(
        R.string.analytics_chart_description,
        com.foldfinance.app.ui.format.MoneyFormat.format(state.summary?.netExpenseMinor ?: 0, currency),
        com.foldfinance.app.ui.format.MoneyFormat.format(buckets.maxOf { it.netExpenseMinor }, currency),
    )
    val average = state.summary?.averageDailyNetExpenseMinor?.takeIf { !monthly && it > 0 }
    SpendingChart(
        buckets = buckets,
        currency = currency,
        selectedIndex = state.selectedIndex,
        onSelect = { index -> buckets.getOrNull(index)?.let { actions.onSelectBucket(it.start) } },
        bucketLabel = { index ->
            val bucket = buckets.getOrNull(index)
            when {
                bucket == null -> ""
                monthly -> AnalyticsFormat.monthShort(bucket.start)
                week -> bucket.start.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, RuLocale)
                else -> bucket.start.dayOfMonth.toString()
            }
        },
        markerLabel = { index ->
            buckets.getOrNull(index)?.let { bucket ->
                val date = if (monthly) AnalyticsFormat.monthShort(bucket.start) else tooltipDate(bucket.start)
                date + " · " + moneyLabel(bucket.netExpenseMinor, currency)
            }.orEmpty()
        },
        description = description,
        averageMinor = average,
        averageLabel = average?.let { stringResource(R.string.analytics_average_line, moneyLabel(it, currency)) },
        modifier = modifier,
    )
}

/** «сб, 17 окт» for the tooltip. */
private fun tooltipDate(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, RuLocale) + ", " + date.dayOfMonth + " " + AnalyticsFormat.monthShort(date)

// ---------------------------------------------------------------- Tiles

/**
 * Three tiles under the chart: the average per day, the share of income spent (with a bar), the
 * biggest category. On the outer screen they stack two + one.
 */
@Composable
private fun StatTiles(state: AnalyticsUiState, stacked: Boolean = false) {
    val summary = state.summary ?: return
    val palette = FoldFinanceTheme.palette
    val average: @Composable (Modifier) -> Unit = { m ->
        StatTile(m, color = palette.card) {
            Text(stringResource(R.string.analytics_tile_average), style = FoldTextStyles.caption, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            MoneyText(summary.averageDailyNetExpenseMinor, summary.currency, style = MoneyStyle.Tile)
            Text(stringResource(R.string.analytics_average_day_hint, state.daysCounted), style = FoldTextStyles.caption, color = palette.textMuted, maxLines = 1)
        }
    }
    val ratio: @Composable (Modifier) -> Unit = { m ->
        StatTile(m, color = palette.card) {
            Text(stringResource(R.string.analytics_tile_ratio), style = FoldTextStyles.caption, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val share = summary.expenseShareOfIncomePercent
            if (share == null) {
                Text("—", style = FoldFinanceTheme.money.tile, color = palette.textDisabled)
                Text(stringResource(R.string.analytics_ratio_no_income), style = FoldTextStyles.caption, color = palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(AnalyticsFormat.percent(share) + "%", style = FoldFinanceTheme.money.tile, color = if (share > 100.0) palette.danger else palette.text)
                ShareBar((share / 100.0).toFloat(), if (share > 100.0) palette.danger else palette.accent, Modifier.padding(top = 4.dp))
            }
        }
    }
    val top: @Composable (Modifier) -> Unit = { m ->
        val first = state.topCategories.firstOrNull()
        StatTile(m, color = palette.card) {
            Text(stringResource(R.string.analytics_tile_top), style = FoldTextStyles.caption, color = palette.textSecondary, maxLines = 1)
            if (first == null) {
                Text("—", style = FoldFinanceTheme.money.tile, color = palette.textDisabled)
            } else {
                val visual = first.category?.visual() ?: uncategorizedVisual()
                Text(visual.name, style = FoldFinanceTheme.money.tile, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.analytics_tile_top_value, moneyLabel(first.netExpenseMinor, summary.currency), first.sharePercent?.let(AnalyticsFormat::percent) ?: "—"),
                    style = FoldTextStyles.caption,
                    color = palette.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TileRow {
                average(Modifier.weight(1f))
                ratio(Modifier.weight(1f))
            }
            top(Modifier.fillMaxWidth())
        }
    } else {
        TileRow {
            average(Modifier.weight(1f))
            ratio(Modifier.weight(1f))
            top(Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------- Selected day

/**
 * «ВЫБРАННЫЙ ДЕНЬ · 17 октября, суббота · 4 820 ₽ · 5 операций · в 2,1 раза выше среднего» and the
 * day's operations. Without a selection — the hint to tap a column.
 */
@Composable
private fun SelectedDay(state: AnalyticsUiState, actions: AnalyticsActions) {
    val palette = FoldFinanceTheme.palette
    val bucket = state.selectedBucket
    val monthly = state.granularity == ChartGranularity.MONTH
    if (bucket == null && !state.showsOperations) {
        Text(
            stringResource(if (monthly) R.string.analytics_chart_hint_month else R.string.analytics_chart_hint_day),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textMuted,
        )
        return
    }
    val currency = state.currency ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline(stringResource(if (monthly) R.string.analytics_selected_month else R.string.analytics_selected_day), Modifier.weight(1f))
            if (bucket != null) {
                IconCircleButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.analytics_day_close),
                    onClick = actions.onClearSelection,
                    size = 36.dp,
                    container = Color.Transparent,
                    tint = palette.textMuted,
                )
            }
        }
        Text(
            if (bucket != null) AnalyticsFormat.bucketTitle(bucket, state.granularity, state.today) else periodTitle(state),
            style = MaterialTheme.typography.headlineSmall,
            color = palette.text,
            modifier = Modifier.semantics { heading() },
        )
        val amount = bucket?.netExpenseMinor ?: state.summary?.netExpenseMinor ?: 0
        MoneyText(amount, currency, style = MoneyStyle.DisplaySmall, modifier = Modifier.padding(top = 4.dp))
        Text(daySummary(state, amount), style = FoldTextStyles.caption, color = palette.textMuted)
    }
    if (state.operations.isEmpty()) {
        Text(stringResource(R.string.analytics_day_empty), style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
        return
    }
    Column {
        state.operations.forEach { tx -> DayOperationRow(state, tx, withDate = monthly) }
    }
}

/** «5 операций · в 2,1 раза выше среднего». */
@Composable
private fun daySummary(state: AnalyticsUiState, amountMinor: Long): String {
    val count = pluralStringResource(R.plurals.analytics_operations_count, state.operations.size, state.operations.size)
    val average = state.summary?.averageDailyNetExpenseMinor ?: 0
    if (state.granularity == ChartGranularity.MONTH || state.selectedBucket == null || average <= 0) return count
    val ratio = amountMinor.toDouble() / average.toDouble()
    val comparison = when {
        ratio >= 1.1 -> stringResource(R.string.analytics_day_above, AnalyticsFormat.percent(ratio))
        ratio <= 0.9 -> stringResource(R.string.analytics_day_below)
        else -> stringResource(R.string.analytics_day_level)
    }
    return stringResource(R.string.analytics_day_summary, count, comparison)
}

/** A compact operation row of the selected day: colour dot, merchant, category · time, amount. */
@Composable
private fun DayOperationRow(state: AnalyticsUiState, tx: Transaction, withDate: Boolean, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    val visual = tx.categoryId?.let(state.categories::get)?.visual() ?: uncategorizedVisual()
    Column(modifier) {
        HorizontalDivider(thickness = 1.dp, color = palette.seam)
        Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(visual.color, FoldShapes.pill))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tx.displayMerchant ?: visual.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    visual.name + " · " + AnalyticsFormat.operationTime(tx.occurredAt, state.period.zone, withDate),
                    style = FoldTextStyles.caption,
                    color = palette.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            MoneyText(tx.amountMinor, tx.currency, direction = tx.direction, style = MoneyStyle.Inline)
        }
    }
}

/** Tabletop: «17 октября · 5 операций … 4 820 ₽» and the operations in two columns. */
@Composable
private fun DayOperationsGrid(state: AnalyticsUiState, actions: AnalyticsActions) {
    val palette = FoldFinanceTheme.palette
    val bucket = state.selectedBucket
    val currency = state.currency ?: return
    val monthly = state.granularity == ChartGranularity.MONTH
    val title = if (bucket != null) AnalyticsFormat.bucketTitle(bucket, state.granularity, state.today) else periodTitle(state)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(
                R.string.analytics_day_header,
                title,
                pluralStringResource(R.plurals.analytics_operations_count, state.operations.size, state.operations.size),
            ),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * 1.07f),
            color = palette.text,
            modifier = Modifier.weight(1f).semantics { heading() },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MoneyText(bucket?.netExpenseMinor ?: state.summary?.netExpenseMinor ?: 0, currency, style = MoneyStyle.InlineStrong)
        if (bucket != null) {
            IconCircleButton(
                icon = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.analytics_day_close),
                onClick = actions.onClearSelection,
                size = 40.dp,
                container = Color.Transparent,
                tint = palette.textMuted,
            )
        }
    }
    if (state.operations.isEmpty()) {
        Text(stringResource(R.string.analytics_day_empty), style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
        return
    }
    val rows = state.operations.chunked(2)
    Column {
        rows.forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                pair.forEach { tx -> DayOperationRow(state, tx, withDate = monthly, modifier = Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------- Categories

/** «ТОП КАТЕГОРИЙ · ОКТЯБРЬ»: name and amount over a bar in the category colour. */
@Composable
private fun TopCategories(state: AnalyticsUiState, twoColumns: Boolean = false) {
    val palette = FoldFinanceTheme.palette
    val summary = state.summary ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Overline(stringResource(R.string.analytics_top_categories, periodTitle(state)))
        if (state.topCategories.isEmpty()) {
            Text(stringResource(R.string.analytics_categories_empty), style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
            return@Column
        }
        val maxAmount = state.topCategories.maxOf { it.netExpenseMinor }.coerceAtLeast(1)
        val item: @Composable (CategoryShare, Modifier) -> Unit = { share, m ->
            val visual = share.category?.visual() ?: uncategorizedVisual()
            Column(m, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(visual.name, style = MaterialTheme.typography.bodyMedium, color = palette.textSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    MoneyText(share.netExpenseMinor, summary.currency, style = MoneyStyle.Inline)
                }
                ShareBar(share.netExpenseMinor.toFloat() / maxAmount, visual.color)
            }
        }
        if (twoColumns) {
            state.topCategories.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    pair.forEach { item(it, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            state.topCategories.forEach { item(it, Modifier.fillMaxWidth()) }
        }
    }
}

/** «3 на проверке — учтены как есть» and «По известным операциям, без пересчёта валют». */
@Composable
private fun ColumnScope.Footnotes(state: AnalyticsUiState) {
    val palette = FoldFinanceTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.needsReviewCount > 0) {
            Text(stringResource(R.string.analytics_needs_review, state.needsReviewCount), style = FoldTextStyles.caption, color = palette.review)
        }
        Text(stringResource(R.string.analytics_footnote), style = FoldTextStyles.caption, color = palette.textMuted)
    }
}

@Composable
private fun uncategorizedVisual() = categoryVisual(
    key = DefaultCategories.OTHER_KEY,
    name = stringResource(R.string.analytics_uncategorized),
    iconKey = "category",
)

// ---------------------------------------------------------------- Empty state and picker

@Composable
private fun AnalyticsEmpty(actions: AnalyticsActions) {
    EmptyState(
        title = stringResource(R.string.analytics_empty_title),
        body = stringResource(R.string.analytics_empty_body),
        icon = Icons.Outlined.Insights,
        actionLabel = stringResource(R.string.analytics_empty_action),
        onAction = actions.onPrevious,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomPeriodDialog(state: AnalyticsUiState, onDismiss: () -> Unit, onConfirm: (LocalDate, LocalDate) -> Unit) {
    val todayMillis = state.today.toUtcMillis()
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = state.period.startDate.toUtcMillis(),
        initialSelectedEndDateMillis = minOf(state.period.endDateInclusive, state.today).toUtcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = year <= state.today.year
        },
    )
    val start = pickerState.selectedStartDateMillis
    val end = pickerState.selectedEndDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (start != null) onConfirm(start.toUtcDate(), (end ?: start).toUtcDate()) },
                enabled = start != null,
            ) { Text(stringResource(R.string.analytics_period_pick_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.analytics_period_pick_cancel)) }
        },
    ) {
        DateRangePicker(
            state = pickerState,
            modifier = Modifier.weight(1f),
            title = {
                Text(
                    stringResource(R.string.analytics_period_pick_title),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
        )
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
