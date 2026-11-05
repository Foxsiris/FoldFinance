package com.foldfinance.app.ui.overview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.Period
import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.app.ui.format.RuLocale
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldMotion
import com.foldfinance.app.ui.theme.FoldTextStyles
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * Bars of net expenses per day (per month in the year view), drawn on a Canvas (Overview and Main
 * mockups). Ordinary bars are quiet grey; the tapped bar — or today, on the outer screen — is
 * lime; days after [today] are 4 dp stubs. With [highlightColor] the part of each bar that
 * belongs to the selected category is drawn in its colour from the bottom.
 *
 * Tapping a bar reports its key (the list next to the chart is filtered by that day). Heights are
 * only proportions for drawing; amounts stay `Long` kopecks everywhere else.
 *
 * @param onSelect `null` — the chart is only to look at (outer screen mini chart).
 * @param today days after it are drawn as empty stubs; `null` — no future.
 * @param accentToday draw [today]'s bar lime (outer screen, nothing selected there).
 * @param showAxis day numbers under the bars.
 */
@Composable
fun SpendingBars(
    bars: List<ChartBar>,
    period: Period,
    selectedKey: Long?,
    onSelect: ((Long) -> Unit)?,
    currency: String,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 120.dp,
    highlightColor: Color? = null,
    today: LocalDate? = null,
    accentToday: Boolean = false,
    showAxis: Boolean = true,
    gap: Dp = 4.dp,
) {
    val palette = FoldFinanceTheme.palette
    val max = bars.maxOfOrNull { it.amountMinor } ?: 0L
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentBars by rememberUpdatedState(bars)
    val grow by animateFloatAsState(if (bars.isEmpty()) 0f else 1f, FoldMotion.spatial(), label = "bars")
    val description = stringResource(
        if (onSelect != null) R.string.overview_chart_description else R.string.overview_chart_description_static,
        MoneyFormat.format(max, currency),
    )
    val base = if (highlightColor != null) palette.chartBar.copy(alpha = if (palette.isDark) 0.85f else 1f) else palette.chartBar
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .semantics { contentDescription = description }
                .then(
                    if (onSelect != null) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val list = currentBars
                                val select = currentOnSelect ?: return@detectTapGestures
                                if (list.isEmpty()) return@detectTapGestures
                                val index = (offset.x / (size.width / list.size.toFloat())).toInt().coerceIn(0, list.lastIndex)
                                select(list[index].key)
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (bars.isEmpty()) return@Canvas
            val gapPx = gap.toPx()
            val slot = size.width / bars.size
            val width = (slot - gapPx).coerceAtLeast(1f).let { if (slot < gapPx * 2) slot * 0.7f else it }
            val offsetX = (slot - width) / 2f
            val radius = CornerRadius((width / 2f).coerceAtMost(if (chartHeight < 60.dp) 3.dp.toPx() else 4.dp.toPx()))
            val stub = 4.dp.toPx()
            val minHeight = if (chartHeight < 60.dp) 6.dp.toPx() else 3.dp.toPx()
            bars.forEachIndexed { i, bar ->
                val left = i * slot + offsetX
                val future = today != null && bar.date.isAfter(today)
                if (future || bar.amountMinor <= 0L) {
                    drawRoundRect(
                        color = palette.chartEmpty,
                        topLeft = Offset(left, size.height - stub),
                        size = Size(width, stub),
                        cornerRadius = radius,
                    )
                    return@forEachIndexed
                }
                val fraction = if (max > 0) bar.amountMinor.toFloat() / max.toFloat() else 0f
                val h = (size.height * fraction * grow).coerceAtLeast(minHeight)
                val selected = bar.key == selectedKey || (accentToday && selectedKey == null && bar.date == today)
                val top = size.height - h
                drawRoundRect(
                    color = if (selected) palette.accent else base,
                    topLeft = Offset(left, top),
                    size = Size(width, h),
                    cornerRadius = radius,
                )
                if (highlightColor != null && bar.highlightMinor > 0 && !selected) {
                    val part = (h * bar.highlightMinor.toFloat() / bar.amountMinor.toFloat()).coerceAtMost(h)
                    clipRect(left = left, top = size.height - part, right = left + width, bottom = size.height) {
                        drawRoundRect(
                            color = highlightColor,
                            topLeft = Offset(left, top),
                            size = Size(width, h),
                            cornerRadius = radius,
                        )
                    }
                }
            }
        }
        if (showAxis && bars.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            AxisLabels(bars, period)
        }
    }
}

/** Day numbers 1 · 5 · 10 · … · last (month), weekdays (week) or month names (year). */
@Composable
private fun AxisLabels(bars: List<ChartBar>, period: Period) {
    val palette = FoldFinanceTheme.palette
    val labels: List<String> = when (period) {
        Period.Year -> bars.map { it.date.month.getDisplayName(TextStyle.SHORT_STANDALONE, RuLocale).take(3) }
        Period.Week -> bars.map { it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, RuLocale) }
        else -> {
            val last = bars.last().date.dayOfMonth
            (listOf(1) + (5..last step 5).toList().filter { it < last - 1 } + last).map { it.toString() }
        }
    }
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
        labels.forEach { label ->
            Text(text = label, style = FoldTextStyles.navLabel, color = palette.textMuted, maxLines = 1)
        }
    }
}
