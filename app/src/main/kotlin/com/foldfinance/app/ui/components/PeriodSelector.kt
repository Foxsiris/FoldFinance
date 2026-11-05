package com.foldfinance.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R

/** Analytics period shown by [PeriodSelector]. */
enum class Period(@StringRes val labelRes: Int) {
    Day(R.string.component_period_day),
    Week(R.string.component_period_week),
    Month(R.string.component_period_month),
    Year(R.string.component_period_year),
}

/**
 * Segmented control «День / Неделя / Месяц / Год» ([SegmentedControl]). 48 dp tall by default: a
 * comfortable thumb target on the outer screen and in the bottom (touch) half of tabletop.
 *
 * @param selected selected segment; `null` shows no thumb (a period outside these segments, such
 *   as a custom range, is active).
 * @param periods segments to show, in order.
 * @param extra labels of additional trailing segments («Свой»), reported by [onExtra] with their index.
 */
@Composable
fun PeriodSelector(
    selected: Period?,
    onSelect: (Period) -> Unit,
    modifier: Modifier = Modifier,
    periods: List<Period> = Period.entries,
    height: Dp = 48.dp,
    extra: List<String> = emptyList(),
    extraSelected: Int = -1,
    onExtra: (Int) -> Unit = {},
) {
    val labels = periods.map { stringResource(it.labelRes) } + extra
    val index = when {
        extraSelected >= 0 -> periods.size + extraSelected
        else -> periods.indexOf(selected)
    }
    SegmentedControl(
        labels = labels,
        selected = index,
        onSelect = { i -> if (i < periods.size) onSelect(periods[i]) else onExtra(i - periods.size) },
        modifier = modifier,
        height = height,
        description = stringResource(R.string.component_period_selector),
    )
}
