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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.CategoryTile
import com.foldfinance.app.ui.components.EmptyState
import com.foldfinance.app.ui.components.LabeledAmount
import com.foldfinance.app.ui.components.ListGroup
import com.foldfinance.app.ui.components.ListGroupDivider
import com.foldfinance.app.ui.components.MoneyStyle
import com.foldfinance.app.ui.components.MoneyText
import com.foldfinance.app.ui.components.PeriodSelector
import com.foldfinance.app.ui.components.TransactionRow
import com.foldfinance.app.ui.components.categoryVisual
import com.foldfinance.app.ui.components.visual
import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.app.ui.format.SignDisplay
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.components.HingeSeam
import com.foldfinance.app.ui.components.SeamOrientation
import com.foldfinance.app.ui.layout.SplitSource
import com.foldfinance.app.ui.layout.TwoPane
import com.foldfinance.app.ui.layout.TwoPaneDefaults
import com.foldfinance.app.ui.layout.foldPaneScaffoldDirective
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.core.domain.analytics.AmountChange
import com.foldfinance.core.domain.analytics.PeriodType
import com.foldfinance.core.model.DefaultCategories
import com.foldfinance.parser.Direction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
 * Stateless analytics screen in the three window layouts:
 *
 * - [AppLayout.Book] — `SupportingPaneScaffold`: the chart and the totals in the main pane, the
 *   period controls, the tapped day and the category breakdown in the supporting pane; the pane
 *   boundary runs on the hinge ([foldPaneScaffoldDirective]).
 * - [AppLayout.Tabletop] — top half (to look at): totals and the chart; bottom half (to touch):
 *   period controls, the tapped day, breakdown.
 * - [AppLayout.Compact] — one column with compact cards and one chart; the period selector sits
 *   at the bottom, in the thumb zone.
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
        is AppLayout.Book -> BookLayout(state, actions, openPicker, modifier)
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

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun BookLayout(state: AnalyticsUiState, actions: AnalyticsActions, openPicker: () -> Unit, modifier: Modifier) {
    val layout = LocalAppLayout.current
    // No partition spacer: the boundary is the hinge itself, each pane keeps 12 dp clear of it
    // (the same seam clearance as TwoPane), and the perforated seam is drawn on it.
    val directive = foldPaneScaffoldDirective().copy(horizontalPartitionSpacerSize = 0.dp)
    val navigator = rememberSupportingPaneScaffoldNavigator<Any>(scaffoldDirective = directive)
    Box(modifier.fillMaxSize()) {
        SupportingPaneScaffold(
            directive = navigator.scaffoldDirective,
            scaffoldState = navigator.scaffoldState,
            modifier = Modifier.fillMaxSize(),
            mainPane = {
                AnimatedPane {
                    PaneColumn(start = 24.dp, end = TwoPaneDefaults.SeamClearance) {
                        PeriodHeader(state, actions)
                        Spacer(Modifier.height(20.dp))
                        if (state.hasData) {
                            HeroTotals(state, actions, MoneyStyle.DisplayMedium)
                            Spacer(Modifier.height(24.dp))
                            ChartCard(state, actions, chartHeight = 260.dp)
                            Spacer(Modifier.height(16.dp))
                            StatsGroup(state)
                            Spacer(Modifier.height(16.dp))
                            RatioCard(state)
                        } else if (!state.isLoading) {
                            AnalyticsEmpty(actions)
                        }
                    }
                }
            },
            supportingPane = {
                AnimatedPane {
                    PaneColumn(start = TwoPaneDefaults.SeamClearance, end = 24.dp) {
                        PeriodControls(state, actions, openPicker)
                        if (state.hasData) {
                            if (state.showsOperations) {
                                Spacer(Modifier.height(24.dp))
                                OperationsSection(state, actions)
                            }
                            Spacer(Modifier.height(24.dp))
                            CategoriesSection(state)
                        }
                    }
                }
            },
        )
        if (layout is AppLayout.Book) BookSeam(layout)
    }
}

/**
 * The perforated seam on the hinge over a `SupportingPaneScaffold` (TwoPane draws its own). With a
 * hinge the scaffold splits exactly on it; without one it splits the container in half.
 */
@Composable
private fun BookSeam(layout: AppLayout.Book) {
    var originX by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxSize()
            .onPlaced {
                originX = it.positionInWindow().x
                width = it.size.width
            },
    ) {
        val splitPx = if (layout.splitSource == SplitSource.Hinge) {
            with(density) { layout.splitAtDp.dp.toPx() } - originX
        } else {
            width / 2f
        }
        val seamWidth = 8.dp
        HingeSeam(
            orientation = SeamOrientation.Vertical,
            modifier = Modifier.offset(x = with(density) { splitPx.toDp() } - seamWidth / 2),
            thickness = seamWidth,
        )
    }
}

@Composable
private fun TabletopLayout(state: AnalyticsUiState, actions: AnalyticsActions, openPicker: () -> Unit, modifier: Modifier) {
    TwoPane(
        modifier = modifier.fillMaxSize(),
        // Top half — to look at: the totals and the chart, which takes all the remaining height.
        first = {
            Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)) {
                PeriodHeader(state, actions)
                if (state.hasData) {
                    HeroTotals(state, actions, MoneyStyle.DisplaySmall, showCurrencySwitcher = false)
                    Spacer(Modifier.height(12.dp))
                    if (state.buckets.isNotEmpty()) {
                        ChartCard(state, actions, chartHeight = null, modifier = Modifier.weight(1f), showHint = false)
                    }
                } else if (!state.isLoading) {
                    Spacer(Modifier.height(12.dp))
                    AnalyticsEmpty(actions)
                }
            }
        },
        // Bottom half — to touch: period, currency, the tapped day, the breakdown.
        second = {
            PaneColumn(start = 24.dp, end = 24.dp, top = 4.dp) {
                PeriodControls(state, actions, openPicker)
                if (state.hasData) {
                    if (state.currencies.size > 1) {
                        Spacer(Modifier.height(12.dp))
                        CurrencySwitcher(state, actions)
                    }
                    if (state.showsOperations) {
                        Spacer(Modifier.height(20.dp))
                        OperationsSection(state, actions)
                    }
                    Spacer(Modifier.height(20.dp))
                    StatsGroup(state)
                    Spacer(Modifier.height(16.dp))
                    RatioCard(state)
                    Spacer(Modifier.height(20.dp))
                    CategoriesSection(state)
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
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
        ) {
            PeriodHeader(state, actions)
            Spacer(Modifier.height(12.dp))
            if (state.hasData) {
                HeroTotals(state, actions, MoneyStyle.DisplayMedium)
                Spacer(Modifier.height(20.dp))
                if (state.buckets.isNotEmpty()) {
                    ChartCard(state, actions, chartHeight = 200.dp)
                    Spacer(Modifier.height(16.dp))
                }
                if (state.showsOperations) {
                    OperationsSection(state, actions)
                    Spacer(Modifier.height(20.dp))
                }
                StatsGroup(state)
                Spacer(Modifier.height(16.dp))
                RatioCard(state)
                Spacer(Modifier.height(20.dp))
                CategoriesSection(state)
            } else if (!state.isLoading) {
                Spacer(Modifier.height(24.dp))
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
            PeriodControls(state, actions, openPicker)
        }
    }
}

/** Scrollable pane content with the screen paddings. */
@Composable
private fun PaneColumn(start: androidx.compose.ui.unit.Dp, end: androidx.compose.ui.unit.Dp, top: androidx.compose.ui.unit.Dp = 24.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = start, end = end, top = top, bottom = 24.dp),
        content = content,
    )
}

// ---------------------------------------------------------------- Header and controls

@Composable
private fun PeriodHeader(state: AnalyticsUiState, actions: AnalyticsActions) {
    val palette = FoldFinanceTheme.palette
    val title = AnalyticsFormat.periodTitle(
        state.period,
        state.today,
        stringResource(R.string.analytics_today),
        stringResource(R.string.analytics_yesterday),
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val titleStyle = MaterialTheme.typography.headlineLarge
        Text(
            text = title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = titleStyle,
            color = palette.text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            // Next to the arrows in a ≈ 230 dp pane «Октябрь» shrinks instead of «Октя…».
            autoSize = TextAutoSize.StepBased(minFontSize = 17.sp, maxFontSize = titleStyle.fontSize, stepSize = 0.5.sp),
        )
        val colors = IconButtonDefaults.iconButtonColors(
            containerColor = palette.surfaceRaised,
            contentColor = palette.text,
            disabledContainerColor = palette.surfaceRaised.copy(alpha = 0.5f),
            disabledContentColor = palette.textMuted.copy(alpha = 0.5f),
        )
        IconButton(onClick = actions.onPrevious, colors = colors) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = stringResource(R.string.analytics_period_previous))
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = actions.onNext, enabled = state.canGoNext, colors = colors) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = stringResource(R.string.analytics_period_next))
        }
    }
}

/** «День / Неделя / Месяц / Год» + «Свой период». */
@Composable
private fun PeriodControls(state: AnalyticsUiState, actions: AnalyticsActions, openPicker: () -> Unit) {
    val palette = FoldFinanceTheme.palette
    val type = state.period.type
    val custom = type == PeriodType.CUSTOM
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PeriodSelector(
            selected = type.toSelector(),
            onSelect = { actions.onSelectType(it.toPeriodType()) },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        // «Свой период»: a round 48 dp button; the chosen range is the screen title.
        Surface(
            modifier = Modifier
                .size(48.dp)
                .clip(FoldShapes.pill)
                .selectable(selected = custom, role = Role.Button, onClick = openPicker),
            shape = FoldShapes.pill,
            color = if (custom) palette.saffronContainer else palette.surfaceRaised,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.DateRange,
                    contentDescription = stringResource(R.string.analytics_period_custom),
                    tint = if (custom) palette.onSaffronContainer else palette.textMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
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

// ---------------------------------------------------------------- Totals

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroTotals(
    state: AnalyticsUiState,
    actions: AnalyticsActions,
    style: MoneyStyle,
    modifier: Modifier = Modifier,
    showCurrencySwitcher: Boolean = true,
) {
    val palette = FoldFinanceTheme.palette
    val summary = state.summary ?: return
    Column(modifier) {
        if (showCurrencySwitcher && state.currencies.size > 1) {
            CurrencySwitcher(state, actions)
            Spacer(Modifier.height(12.dp))
        }
        MoneyText(amountMinor = summary.netExpenseMinor, currency = summary.currency, style = style)
        // The comparison badge goes under «потрачено» when the pane is too narrow for both.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.analytics_net_expense),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textMuted,
            )
            state.comparison?.netExpense?.let { change ->
                ChangeBadge(change, periodComparisonLabel(state.period.type), positiveIsGood = false)
            }
        }
        if (summary.incomeMinor > 0) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoneyText(
                    amountMinor = summary.incomeMinor,
                    currency = summary.currency,
                    direction = Direction.INCOME,
                    style = MoneyStyle.InlineStrong,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.analytics_income).lowercase(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.textMuted,
                )
            }
        }
    }
}

@Composable
private fun CurrencySwitcher(state: AnalyticsUiState, actions: AnalyticsActions) {
    val palette = FoldFinanceTheme.palette
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.currencies.forEach { total ->
            val selected = total.currency == state.currency
            Surface(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(FoldShapes.pill)
                    .selectable(selected = selected, role = Role.Tab, onClick = { actions.onSelectCurrency(total.currency) }),
                shape = FoldShapes.pill,
                color = if (selected) palette.saffronContainer else palette.surfaceRaised,
            ) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "${MoneyFormat.symbol(total.currency)}  ${total.currency}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) palette.onSaffronContainer else palette.textMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** «↑ 12% к прошлому месяцу». Expenses are neutral; only more income is tinted mint. */
@Composable
private fun ChangeBadge(change: AmountChange, suffix: String, positiveIsGood: Boolean) {
    val palette = FoldFinanceTheme.palette
    val percent = change.percent
    val text = when {
        percent == null -> return
        change.deltaMinor == 0L -> "= $suffix"
        else -> (if (change.deltaMinor > 0) "↑ " else "↓ ") + AnalyticsFormat.percent(percent) + "% " + suffix
    }
    val good = positiveIsGood && change.deltaMinor > 0
    Surface(shape = FoldShapes.pill, color = if (good) palette.mintContainer else palette.surfaceRaised) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (good) palette.mint else palette.textMuted,
            maxLines = 1,
        )
    }
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
private fun ChartCard(
    state: AnalyticsUiState,
    actions: AnalyticsActions,
    chartHeight: androidx.compose.ui.unit.Dp?,
    showHint: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val palette = FoldFinanceTheme.palette
    val currency = state.currency ?: return
    if (state.buckets.isEmpty()) return
    val buckets = state.buckets
    val monthly = state.granularity == ChartGranularity.MONTH
    val week = state.period.type == PeriodType.WEEK
    val description = stringResource(
        R.string.analytics_chart_description,
        MoneyFormat.format(state.summary?.netExpenseMinor ?: 0, currency),
        MoneyFormat.format(buckets.maxOf { it.netExpenseMinor }, currency),
    )
    Surface(modifier = modifier.fillMaxWidth(), shape = FoldShapes.summary, color = palette.surface) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 14.dp)) {
            ChartLegend(monthly, Modifier.padding(start = 6.dp))
            Spacer(Modifier.height(12.dp))
            val chartModifier = if (chartHeight != null) Modifier.fillMaxWidth().height(chartHeight) else Modifier.fillMaxWidth().weight(1f)
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
                        week -> bucket.start.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, com.foldfinance.app.ui.format.RuLocale)
                        else -> bucket.start.dayOfMonth.toString()
                    }
                },
                markerLabel = { index ->
                    buckets.getOrNull(index)?.let { MoneyFormat.format(it.netExpenseMinor, currency) }.orEmpty()
                },
                description = description,
                modifier = chartModifier,
            )
            if (showHint) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(if (monthly) R.string.analytics_chart_hint_month else R.string.analytics_chart_hint_day),
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textMuted,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartLegend(monthly: Boolean, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendItem(
            stringResource(if (monthly) R.string.analytics_chart_monthly else R.string.analytics_chart_daily),
            swatch = { Box(Modifier.size(10.dp).background(palette.text.copy(alpha = if (palette.isDark) 0.42f else 0.34f), FoldShapes.tile)) },
        )
        LegendItem(
            stringResource(R.string.analytics_chart_cumulative_income),
            swatch = { Box(Modifier.width(14.dp).height(3.dp).background(palette.mint, FoldShapes.pill)) },
        )
        LegendItem(
            stringResource(R.string.analytics_chart_cumulative_expense),
            swatch = {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(3) { Box(Modifier.width(4.dp).height(2.dp).background(palette.textMuted)) }
                }
            },
        )
    }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        swatch()
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = FoldFinanceTheme.palette.textMuted, maxLines = 1)
    }
}

// ---------------------------------------------------------------- Stats

/** Income, refunds, average per day, change of expenses and income to the previous period. */
@Composable
private fun StatsGroup(state: AnalyticsUiState) {
    val summary = state.summary ?: return
    val palette = FoldFinanceTheme.palette
    val currency = summary.currency
    ListGroup(Modifier.fillMaxWidth()) {
        StatRow(
            label = stringResource(R.string.analytics_average_day),
            supporting = stringResource(R.string.analytics_average_day_hint, state.daysCounted),
        ) {
            MoneyText(summary.averageDailyNetExpenseMinor, currency, style = MoneyStyle.InlineStrong)
        }
        ListGroupDivider(startInset = 16.dp)
        StatRow(label = stringResource(R.string.analytics_expense)) {
            MoneyText(summary.expenseMinor, currency, style = MoneyStyle.InlineStrong)
        }
        if (summary.refundMinor > 0) {
            ListGroupDivider(startInset = 16.dp)
            StatRow(label = stringResource(R.string.analytics_refunds)) {
                MoneyText(summary.refundMinor, currency, direction = Direction.REFUND, style = MoneyStyle.InlineStrong)
            }
        }
        ListGroupDivider(startInset = 16.dp)
        StatRow(label = stringResource(R.string.analytics_income)) {
            MoneyText(summary.incomeMinor, currency, direction = Direction.INCOME, style = MoneyStyle.InlineStrong)
        }
        state.comparison?.let { comparison ->
            ListGroupDivider(startInset = 16.dp)
            StatRow(
                label = stringResource(R.string.analytics_compare_expense),
                supporting = periodComparisonLabel(state.period.type),
            ) { ChangeValue(comparison.netExpense, currency, positiveIsGood = false) }
            ListGroupDivider(startInset = 16.dp)
            StatRow(
                label = stringResource(R.string.analytics_compare_income),
                supporting = periodComparisonLabel(state.period.type),
            ) { ChangeValue(comparison.income, currency, positiveIsGood = true) }
        }
    }
    if (state.needsReviewCount > 0) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.analytics_needs_review, state.needsReviewCount),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = palette.saffron,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.analytics_footnote),
        modifier = Modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = palette.textMuted,
    )
}

/** A statistic: label on the left, value on the right; in a narrow pane the value goes under the label (still on the right). */
@Composable
private fun StatRow(label: String, supporting: String? = null, value: @Composable () -> Unit) {
    val palette = FoldFinanceTheme.palette
    LabeledAmount(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        stackedAlignment = Alignment.End,
        label = {
            Column {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = palette.text)
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodySmall, color = palette.textMuted)
                }
            }
        },
        amount = value,
    )
}

/** «+1 200 ₽ · 12%» style value of a change, as an amount with a sign and a percent under it. */
@Composable
private fun ChangeValue(change: AmountChange, currency: String, positiveIsGood: Boolean) {
    val palette = FoldFinanceTheme.palette
    Column(horizontalAlignment = Alignment.End) {
        if (change.deltaMinor == 0L) {
            Text(stringResource(R.string.analytics_compare_same), style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
            return@Column
        }
        val color = if (positiveIsGood && change.deltaMinor > 0) palette.mint else palette.text
        MoneyText(
            amountMinor = change.deltaMinor,
            currency = currency,
            style = MoneyStyle.InlineStrong,
            signDisplay = SignDisplay.Always,
            color = color,
        )
        val percent = change.percent
        Text(
            text = if (percent == null) stringResource(R.string.analytics_compare_new) else AnalyticsFormat.percent(percent) + "%",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textMuted,
        )
    }
}

/** Income vs expenses: share of income spent, as a bar. */
@Composable
private fun RatioCard(state: AnalyticsUiState) {
    val summary = state.summary ?: return
    val palette = FoldFinanceTheme.palette
    Surface(Modifier.fillMaxWidth(), shape = FoldShapes.listGroup, color = palette.surface) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.analytics_ratio_title), style = MaterialTheme.typography.titleMedium, color = palette.text)
            Spacer(Modifier.height(10.dp))
            val share = summary.expenseShareOfIncomePercent
            if (share == null) {
                Text(stringResource(R.string.analytics_ratio_no_income), style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
                return@Column
            }
            val fraction = (share / 100.0).toFloat().coerceIn(0f, 1f)
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(FoldShapes.pill)
                    .background(palette.mintContainer),
            ) {
                Box(
                    Modifier
                        .width(maxWidth * fraction)
                        .fillMaxHeight()
                        .background(if (share > 100.0) palette.alert else palette.text.copy(alpha = 0.7f), FoldShapes.pill),
                )
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.analytics_ratio_share, AnalyticsFormat.percent(share)),
                    modifier = Modifier.padding(end = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.text,
                )
                val left = summary.balanceMinor
                Text(
                    text = if (left >= 0) {
                        stringResource(R.string.analytics_ratio_left, MoneyFormat.format(left, summary.currency))
                    } else {
                        stringResource(R.string.analytics_ratio_over, MoneyFormat.format(-left, summary.currency))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (left >= 0) palette.mint else palette.alert,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Categories

@Composable
private fun CategoriesSection(state: AnalyticsUiState) {
    val palette = FoldFinanceTheme.palette
    val summary = state.summary ?: return
    Text(
        stringResource(R.string.analytics_categories_title),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        color = palette.text,
    )
    Spacer(Modifier.height(12.dp))
    if (state.topCategories.isEmpty()) {
        Text(stringResource(R.string.analytics_categories_empty), style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
        return
    }
    val maxAmount = state.topCategories.maxOf { it.netExpenseMinor }.coerceAtLeast(1)
    ListGroup(Modifier.fillMaxWidth()) {
        state.topCategories.forEachIndexed { index, share ->
            if (index > 0) ListGroupDivider()
            val visual = share.category?.visual() ?: uncategorizedVisual()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryTile(visual)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            visual.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = palette.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        MoneyText(share.netExpenseMinor, summary.currency, style = MoneyStyle.Inline)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BoxWithConstraints(
                            Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(FoldShapes.pill)
                                .background(palette.surfaceRaised),
                        ) {
                            Box(
                                Modifier
                                    .width(maxWidth * (share.netExpenseMinor.toFloat() / maxAmount))
                                    .fillMaxHeight()
                                    .background(visual.color, FoldShapes.pill),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = share.sharePercent?.let { AnalyticsFormat.percent(it) + "%" }.orEmpty(),
                            modifier = Modifier.width(44.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textMuted,
                            maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun uncategorizedVisual() = categoryVisual(
    key = DefaultCategories.OTHER_KEY,
    name = stringResource(R.string.analytics_uncategorized),
    iconKey = "category",
)

// ---------------------------------------------------------------- Operations of the tapped day

@Composable
private fun OperationsSection(state: AnalyticsUiState, actions: AnalyticsActions) {
    val palette = FoldFinanceTheme.palette
    val bucket = state.selectedBucket
    val title = if (bucket != null) {
        AnalyticsFormat.bucketTitle(bucket, state.granularity, state.today)
    } else {
        stringResource(R.string.analytics_day_operations)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, color = palette.text)
            if (state.operations.isNotEmpty()) {
                Text(
                    pluralStringResource(R.plurals.analytics_operations_count, state.operations.size, state.operations.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textMuted,
                )
            }
        }
        if (bucket != null) {
            IconButton(onClick = actions.onClearSelection) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.analytics_day_close), tint = palette.textMuted)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    if (state.operations.isEmpty()) {
        Text(stringResource(R.string.analytics_day_empty), style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
        return
    }
    val withDate = bucket != null && state.granularity == ChartGranularity.MONTH
    ListGroup(Modifier.fillMaxWidth()) {
        state.operations.forEachIndexed { index, tx ->
            if (index > 0) ListGroupDivider()
            val visual = tx.categoryId?.let(state.categories::get)?.visual() ?: uncategorizedVisual()
            TransactionRow(
                merchant = tx.merchant ?: visual.name,
                time = AnalyticsFormat.operationTime(tx.occurredAt, state.period.zone, withDate),
                amountMinor = tx.amountMinor,
                currency = tx.currency,
                direction = tx.direction,
                category = visual,
                source = tx.source,
                needsReview = tx.needsReview,
            )
        }
    }
}

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
