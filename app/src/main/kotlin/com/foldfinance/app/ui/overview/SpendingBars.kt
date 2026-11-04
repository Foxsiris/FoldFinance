package com.foldfinance.app.ui.overview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.Period
import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.app.ui.format.RuLocale
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldMotion
import java.time.format.TextStyle

/**
 * Bars of net expenses per day (per month in the year view), drawn on a Canvas. Tapping a bar
 * selects it (the list next to the chart is filtered by that day); the selection is saffron, the
 * rest is a quiet tint of the text colour — expenses are not alarms.
 *
 * Heights are only proportions for drawing; amounts stay `Long` kopecks everywhere else.
 */
@Composable
fun SpendingBars(
    bars: List<ChartBar>,
    period: Period,
    selectedKey: Long?,
    onSelect: (Long) -> Unit,
    currency: String,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 120.dp,
) {
    val palette = FoldFinanceTheme.palette
    val max = bars.maxOfOrNull { it.amountMinor } ?: 0L
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentBars by rememberUpdatedState(bars)
    val grow by animateFloatAsState(if (bars.isEmpty()) 0f else 1f, FoldMotion.spatial(), label = "bars")
    val description = stringResource(
        R.string.overview_chart_description,
        MoneyFormat.format(max, currency),
    )
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = if (max > 0) stringResource(if (period == Period.Year) R.string.overview_chart_max_month else R.string.overview_chart_max, MoneyFormat.format(max, currency)) else stringResource(R.string.overview_chart_nothing),
                style = MaterialTheme.typography.labelMedium,
                color = palette.textMuted,
            )
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .semantics { contentDescription = description }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val list = currentBars
                        if (list.isEmpty()) return@detectTapGestures
                        val index = (offset.x / (size.width / list.size.toFloat())).toInt().coerceIn(0, list.lastIndex)
                        currentOnSelect(list[index].key)
                    }
                },
        ) {
            if (bars.isEmpty()) return@Canvas
            val slot = size.width / bars.size
            val gap = (slot * 0.28f).coerceAtMost(6.dp.toPx())
            val width = (slot - gap).coerceAtLeast(1f)
            val radius = CornerRadius((width / 2f).coerceAtMost(4.dp.toPx()))
            val minHeight = 2.dp.toPx()
            bars.forEachIndexed { i, bar ->
                val fraction = if (max > 0) bar.amountMinor.toFloat() / max.toFloat() else 0f
                val h = if (bar.amountMinor > 0) (size.height * fraction * grow).coerceAtLeast(minHeight) else minHeight
                val selected = bar.key == selectedKey
                val color = when {
                    selected -> palette.saffron
                    bar.amountMinor == 0L -> palette.seam
                    selectedKey != null -> palette.text.copy(alpha = 0.18f)
                    else -> palette.text.copy(alpha = if (palette.isDark) 0.42f else 0.32f)
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * slot + gap / 2f, size.height - h),
                    size = Size(width, h),
                    cornerRadius = radius,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        AxisLabels(bars, period)
    }
}

@Composable
private fun AxisLabels(bars: List<ChartBar>, period: Period) {
    if (bars.isEmpty()) return
    val palette = FoldFinanceTheme.palette
    val labels: List<String> = when (period) {
        Period.Year -> listOf(bars.first().date, bars[bars.size / 2].date, bars.last().date)
            .map { it.month.getDisplayName(TextStyle.SHORT_STANDALONE, RuLocale) }
        Period.Week -> listOf(bars.first().date, bars.last().date)
            .map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, RuLocale) }
        else -> listOf(bars.first().date, bars[bars.size / 2].date, bars.last().date).map { it.dayOfMonth.toString() }
    }
    Row(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = palette.textMuted,
                textAlign = when (index) {
                    0 -> TextAlign.Start
                    labels.lastIndex -> TextAlign.End
                    else -> TextAlign.Center
                },
            )
        }
    }
}
