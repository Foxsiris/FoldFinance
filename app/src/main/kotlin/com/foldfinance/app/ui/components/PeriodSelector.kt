package com.foldfinance.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldMotion
import com.foldfinance.app.ui.theme.FoldShapes

/** Analytics period shown by [PeriodSelector]. */
enum class Period(@StringRes val labelRes: Int) {
    Day(R.string.component_period_day),
    Week(R.string.component_period_week),
    Month(R.string.component_period_month),
    Year(R.string.component_period_year),
}

/**
 * Segmented control «День / Неделя / Месяц / Год». The selection thumb slides with the spatial
 * spring (instant under reduced motion). 48 dp tall: a comfortable thumb target on the outer
 * screen and in the bottom (touch) half of tabletop.
 *
 * @param selected selected segment; `null` shows no thumb (a period outside these segments, such
 *   as a custom range, is active).
 * @param periods segments to show, in order.
 */
@Composable
fun PeriodSelector(
    selected: Period?,
    onSelect: (Period) -> Unit,
    modifier: Modifier = Modifier,
    periods: List<Period> = Period.entries,
) {
    val palette = FoldFinanceTheme.palette
    val description = stringResource(R.string.component_period_selector)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(palette.surfaceRaised, FoldShapes.pill)
            .padding(4.dp)
            .semantics { contentDescription = description },
    ) {
        val segment = maxWidth / periods.size
        val index = periods.indexOf(selected)
        val thumbOffset by animateDpAsState(segment * index.coerceAtLeast(0), FoldMotion.spatial(), label = "periodThumb")
        if (index >= 0) {
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .width(segment)
                    .fillMaxHeight()
                    .background(palette.saffronContainer, FoldShapes.pill),
            )
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            periods.forEach { period ->
                val isSelected = period == selected
                val color by animateColorAsState(
                    targetValue = if (isSelected) palette.onSaffronContainer else palette.textMuted,
                    animationSpec = FoldMotion.effects(),
                    label = "periodLabel",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(FoldShapes.pill)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(period) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(period.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = color,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
