package com.foldfinance.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A caption and an amount: side by side (caption on the left, amount on the right) when both fit
 * on one line, otherwise the caption on top and the amount under it. Neither is ever cut by the
 * other: the amount keeps its intrinsic width, the caption gets the whole width when stacked.
 *
 * Used for statements like «Остаток •• 1234 по данным банка на 14:05 … 48 210 ₽» that have to
 * survive a ~200 dp pane next to the hinge. For several such lines that must look alike, use
 * [LabeledAmountGroup].
 */
@Composable
fun LabeledAmount(
    label: @Composable () -> Unit,
    amount: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = 12.dp,
    stackedGap: Dp = 2.dp,
    stackedAlignment: Alignment.Horizontal = Alignment.Start,
) {
    LabeledAmountGroup(
        count = 1,
        label = { label() },
        amount = { amount() },
        modifier = modifier,
        gap = gap,
        stackedGap = stackedGap,
        stackedAlignment = stackedAlignment,
    )
}

/**
 * [count] caption/amount lines laid out *together*: if any line does not fit side by side, every
 * line is stacked (caption over amount), so a list of balances never mixes the two forms.
 *
 * @param rowMinHeight minimum height of one line (side by side or stacked).
 * @param rowSpacing vertical space between lines.
 */
@Composable
fun LabeledAmountGroup(
    count: Int,
    label: @Composable (index: Int) -> Unit,
    amount: @Composable (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = 12.dp,
    stackedGap: Dp = 2.dp,
    stackedAlignment: Alignment.Horizontal = Alignment.Start,
    rowMinHeight: Dp = 0.dp,
    rowSpacing: Dp = 0.dp,
) {
    // One measurable per line in each slot, whatever the caller emits.
    val labels: @Composable () -> Unit = { repeat(count) { i -> Box { label(i) } } }
    val amounts: @Composable () -> Unit = { repeat(count) { i -> Box { amount(i) } } }
    Layout(contents = listOf(labels, amounts), modifier = modifier) { (labelMeasurables, amountMeasurables), constraints ->
        val bounded = constraints.hasBoundedWidth
        val width = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val stackedGapPx = stackedGap.roundToPx()
        val minRow = rowMinHeight.roundToPx()
        val spacing = rowSpacing.roundToPx()

        val amountPlaceables = amountMeasurables.map { it.measure(Constraints(maxWidth = width)) }
        val labelWidths = labelMeasurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val sideBySide = !bounded || labelWidths.indices.all { i ->
            labelWidths[i] + gapPx + amountPlaceables[i].width <= width
        }
        val labelPlaceables = labelMeasurables.mapIndexed { i, m ->
            val max = when {
                !bounded -> Constraints.Infinity
                sideBySide -> (width - gapPx - amountPlaceables[i].width).coerceAtLeast(0)
                else -> width
            }
            m.measure(Constraints(maxWidth = max))
        }
        val layoutWidth = if (bounded) {
            width
        } else {
            labelPlaceables.indices.maxOfOrNull { labelPlaceables[it].width + gapPx + amountPlaceables[it].width } ?: 0
        }
        val rowHeights = labelPlaceables.indices.map { i ->
            val content = if (sideBySide) {
                maxOf(labelPlaceables[i].height, amountPlaceables[i].height)
            } else {
                labelPlaceables[i].height + stackedGapPx + amountPlaceables[i].height
            }
            maxOf(content, minRow)
        }
        val total = (rowHeights.sum() + spacing * (count - 1).coerceAtLeast(0)).coerceAtLeast(constraints.minHeight)
        layout(layoutWidth, total) {
            var y = 0
            for (i in labelPlaceables.indices) {
                val l = labelPlaceables[i]
                val a = amountPlaceables[i]
                val h = rowHeights[i]
                if (sideBySide) {
                    l.placeRelative(0, y + (h - l.height) / 2)
                    a.placeRelative(layoutWidth - a.width, y + (h - a.height) / 2)
                } else {
                    val top = y + (h - l.height - stackedGapPx - a.height) / 2
                    l.placeRelative(0, top)
                    a.place(stackedAlignment.align(a.width, layoutWidth, layoutDirection), top + l.height + stackedGapPx)
                }
                y += h + spacing
            }
        }
    }
}
