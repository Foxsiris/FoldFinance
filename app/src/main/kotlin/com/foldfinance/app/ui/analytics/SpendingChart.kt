package com.foldfinance.app.ui.analytics

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.Onest
import com.foldfinance.app.ui.theme.TabularNumbers
import com.patrykandpatrick.vico.compose.cartesian.CartesianChart
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerPadding
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Dynamics chart of the period (Vico 3.3.1), the main element of the analytics screen.
 *
 * - Columns (start axis): net expenses of each day — or month for long periods — in the neutral
 *   text colour (expenses are the norm, not an alarm); the tapped column turns saffron, the colour
 *   of every active choice in the app.
 * - Lines (end axis, own scale): running totals since the period start — income in mint with a
 *   soft fill, expenses as a dashed neutral line. Where the mint line stays above the dashed one,
 *   the period is «in the plus».
 *
 * The model is built synchronously from [buckets] (no `CartesianChartModelProducer`): the data is
 * already in memory, and screenshots stay deterministic. Zoom and scroll are off — the whole period
 * always fits the width. A tap on a column reports its index through [onSelect]
 * (`CartesianMarkerVisibilityListener` + toggle-on-tap marker controller).
 *
 * @param bucketLabel axis label of a column by index («5», «пн», «янв»).
 * @param markerLabel text of the floating label over a tapped column.
 */
@Composable
fun SpendingChart(
    buckets: List<ChartBucket>,
    currency: String,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    bucketLabel: (Int) -> String,
    markerLabel: (Int) -> String,
    description: String,
    modifier: Modifier = Modifier,
) {
    if (buckets.isEmpty()) return
    val palette = FoldFinanceTheme.palette
    val digits = MoneyFormat.fractionDigits(currency)
    val scale = remember(digits) { Math.pow(10.0, digits.toDouble()) }
    val model = remember(buckets, scale) { chartModel(buckets, scale) }
    // Round axis steps (1 · 2 · 2,5 · 5 × 10ⁿ) and ranges that end on a step, for both scales.
    val columnScale = remember(buckets, scale) { niceScale(buckets.minOf { it.netExpenseMinor } / scale, buckets.maxOf { it.netExpenseMinor } / scale) }
    val lineScale = remember(buckets, scale) { lineScale(buckets, scale) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentBucketLabel by rememberUpdatedState(bucketLabel)
    val currentMarkerLabel by rememberUpdatedState(markerLabel)

    BoxWithConstraints(modifier.semantics { contentDescription = description }) {
        // Two value axes take ~2 × 44 dp; the rest is shared by the columns.
        val plotWidth = (maxWidth - 88.dp).coerceAtLeast(120.dp)
        val slot = plotWidth / buckets.size
        val thickness = (slot * 0.6f).coerceIn(3.dp, 28.dp)
        val spacing = (slot - thickness).coerceAtLeast(1.dp)
        val corner = (thickness / 3).coerceAtMost(6.dp)

        val baseColumn = rememberLineComponent(
            fill = Fill(palette.text.copy(alpha = if (palette.isDark) 0.42f else 0.34f)),
            thickness = thickness,
            shape = RoundedCornerShape(topStart = corner, topEnd = corner),
        )
        val selectedColumn = rememberLineComponent(
            fill = Fill(palette.saffron),
            thickness = thickness,
            shape = RoundedCornerShape(topStart = corner, topEnd = corner),
        )
        val columnProvider = remember(baseColumn, selectedColumn, selectedIndex) {
            HighlightColumnProvider(baseColumn, selectedColumn, selectedIndex)
        }

        val incomeLine = LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(Fill(palette.mint)),
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.5.dp),
            areaFill = LineCartesianLayer.AreaFill.single(
                Fill(Brush.verticalGradient(listOf(palette.mint.copy(alpha = 0.22f), palette.mint.copy(alpha = 0f)))),
            ),
            interpolator = LineCartesianLayer.Interpolator.Sharp,
        )
        val expenseLine = LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(Fill(palette.textMuted)),
            stroke = LineCartesianLayer.LineStroke.Dashed(thickness = 1.5.dp, dashLength = 5.dp, gapLength = 4.dp),
            areaFill = null,
            interpolator = LineCartesianLayer.Interpolator.Sharp,
        )

        val labelStyle = TextStyle(
            color = palette.textMuted,
            fontFamily = Onest,
            fontSize = 11.sp,
            fontFeatureSettings = TabularNumbers,
        )
        val axisLabel = rememberAxisLabelComponent(style = labelStyle)
        val guideline = rememberAxisGuidelineComponent(fill = Fill(palette.seam.copy(alpha = 0.8f)), thickness = 1.dp)
        val amountFormatter = remember(scale, currency) { AmountAxisFormatter(scale, currency) }
        val labelSpacing = remember(buckets.size, plotWidth) {
            ceil(buckets.size / (plotWidth / MIN_LABEL_WIDTH).coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        }
        val bottomFormatter = remember { BucketAxisFormatter { currentBucketLabel(it) } }

        val marker = rememberDefaultCartesianMarker(
            label = rememberTextComponent(
                style = TextStyle(color = palette.text, fontFamily = Onest, fontSize = 13.sp, fontFeatureSettings = TabularNumbers),
                padding = Insets(horizontal = 10.dp, vertical = 6.dp),
                background = rememberShapeComponent(fill = Fill(palette.surfaceRaised), shape = RoundedCornerShape(10.dp)),
            ),
            valueFormatter = remember { MarkerFormatter { currentMarkerLabel(it) } },
            labelPosition = DefaultCartesianMarker.LabelPosition.Top,
            guideline = rememberAxisGuidelineComponent(fill = Fill(palette.saffron.copy(alpha = 0.7f)), thickness = 1.dp),
        )
        val visibilityListener = remember {
            object : CartesianMarkerVisibilityListener {
                override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) = report(targets)
                override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) = report(targets)
                private fun report(targets: List<CartesianMarker.Target>) {
                    targets.firstOrNull()?.x?.roundToInt()?.let { currentOnSelect(it) }
                }
            }
        }
        // The tapped column keeps a soft saffron band behind it, visible even when its value is ~0.
        val selectionBand = remember(palette.saffronContainer) { SelectionBandMarker(palette.saffronContainer) }
        val persistentMarkers: CartesianChart.PersistentMarkerScope.(ExtraStore) -> Unit = remember(selectedIndex, selectionBand) {
            { if (selectedIndex != null) selectionBand.at(selectedIndex) }
        }

        val chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = columnProvider,
                columnCollectionSpacing = spacing,
                rangeProvider = remember(columnScale) { CartesianLayerRangeProvider.fixed(minY = columnScale.min, maxY = columnScale.max) },
                verticalAxisPosition = Axis.Position.Vertical.Start,
            ),
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(incomeLine, expenseLine),
                // The chart x spacing is the widest of all layers: keep the lines on the column grid.
                pointSpacing = slot,
                verticalAxisPosition = Axis.Position.Vertical.End,
                rangeProvider = remember(lineScale) { CartesianLayerRangeProvider.fixed(minY = lineScale.min, maxY = lineScale.max) },
            ),
            startAxis = VerticalAxis.rememberStart(
                line = null,
                label = axisLabel,
                valueFormatter = amountFormatter,
                tick = null,
                guideline = guideline,
                itemPlacer = remember(columnScale) { VerticalAxis.ItemPlacer.step({ columnScale.step }) },
            ),
            endAxis = VerticalAxis.rememberEnd(
                line = null,
                label = rememberAxisLabelComponent(style = labelStyle.copy(color = palette.mint)),
                valueFormatter = amountFormatter,
                tick = null,
                guideline = null,
                itemPlacer = remember(lineScale) { VerticalAxis.ItemPlacer.step({ lineScale.step }) },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                line = rememberAxisLineComponent(fill = Fill(palette.seam), thickness = 1.dp),
                label = axisLabel,
                valueFormatter = bottomFormatter,
                tick = null,
                guideline = null,
                itemPlacer = remember(labelSpacing) { HorizontalAxis.ItemPlacer.aligned(spacing = { labelSpacing }) },
            ),
            marker = marker,
            markerVisibilityListener = visibilityListener,
            markerController = CartesianMarkerController.rememberToggleOnTap(),
            persistentMarkers = persistentMarkers,
            layerPadding = remember(thickness) {
                { _: ExtraStore ->
                    CartesianLayerPadding(scalableStart = thickness / 2 + 4.dp, scalableEnd = thickness / 2 + 4.dp, unscalableStart = 0.dp, unscalableEnd = 0.dp)
                }
            },
        )
        CartesianChartHost(
            chart = chart,
            model = model,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content, minZoom = Zoom.Content, maxZoom = Zoom.Content),
        )
    }
}

private const val VALUE_TICKS = 4
private val MIN_LABEL_WIDTH: Dp = 40.dp

/** Columns: daily (or monthly) net expenses; lines: running income and running net expenses. */
private fun chartModel(buckets: List<ChartBucket>, scale: Double): CartesianChartModel {
    val xs = buckets.indices.toList()
    var runningIncome = 0L
    var runningExpense = 0L
    val cumulativeIncome = ArrayList<Double>(buckets.size)
    val cumulativeExpense = ArrayList<Double>(buckets.size)
    for (b in buckets) {
        runningIncome += b.incomeMinor
        runningExpense += b.netExpenseMinor
        cumulativeIncome += runningIncome / scale
        cumulativeExpense += runningExpense / scale
    }
    return CartesianChartModel(
        ColumnCartesianLayerModel.build { series(xs, buckets.map { it.netExpenseMinor / scale }) },
        LineCartesianLayerModel.build {
            series(xs, cumulativeIncome)
            series(xs, cumulativeExpense)
        },
    )
}

/** Base columns, with the selected one drawn in saffron. */
private class HighlightColumnProvider(
    private val base: LineComponent,
    private val selected: LineComponent,
    private val selectedIndex: Int?,
) : ColumnCartesianLayer.ColumnProvider {
    private fun pick(entry: ColumnCartesianLayerModel.Entry) =
        if (selectedIndex != null && entry.x.roundToInt() == selectedIndex) selected else base

    override fun getColumn(entry: ColumnCartesianLayerModel.Entry, extraStore: ExtraStore) = pick(entry)

    override fun getWidestSeriesColumn(seriesKey: Any, seriesIndex: Int, extraStore: ExtraStore) = base

    override fun equals(other: Any?) =
        other is HighlightColumnProvider && other.base == base && other.selected == selected && other.selectedIndex == selectedIndex

    override fun hashCode() = 31 * (31 * base.hashCode() + selected.hashCode()) + (selectedIndex ?: -1)
}

/** Value axis: compact amounts («12 тыс»). */
private class AmountAxisFormatter(private val scale: Double, private val currency: String) : CartesianValueFormatter {
    override fun format(context: CartesianMeasuringContext, value: Double, verticalAxisPosition: Axis.Position.Vertical?): CharSequence =
        AnalyticsFormat.compactAmount((value * scale).roundToLong(), currency)

    override fun equals(other: Any?) = other is AmountAxisFormatter && other.scale == scale && other.currency == currency
    override fun hashCode() = 31 * scale.hashCode() + currency.hashCode()
}

/** Bottom axis: label of the column at x. */
private class BucketAxisFormatter(private val label: (Int) -> String) : CartesianValueFormatter {
    override fun format(context: CartesianMeasuringContext, value: Double, verticalAxisPosition: Axis.Position.Vertical?): CharSequence =
        label(value.roundToInt())
}

/** Floating label over a tapped column. */
private class MarkerFormatter(private val label: (Int) -> String) : DefaultCartesianMarker.ValueFormatter {
    override fun format(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>): CharSequence =
        targets.firstOrNull()?.x?.roundToInt()?.let(label).orEmpty()
}

/**
 * Persistent marker of the tapped column: a rounded band of the column slot behind the layers,
 * so the selection stays visible for days with almost no expenses (or more refunds than expenses).
 */
private class SelectionBandMarker(private val color: Color) : CartesianMarker {
    private val paint = Paint().apply { this.color = this@SelectionBandMarker.color }

    override fun drawUnderLayers(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>) {
        val target = targets.firstOrNull() ?: return
        val bounds = context.layerBounds
        val half = context.layerDimensions.xSpacing * context.zoom / 2f
        val radius = minOf(half, with(context) { 8.dp.pixels })
        context.canvas.drawRoundRect(
            left = target.canvasX - half,
            top = bounds.top,
            right = target.canvasX + half,
            bottom = bounds.bottom,
            radiusX = radius,
            radiusY = radius,
            paint = paint,
        )
    }

    override fun equals(other: Any?) = other is SelectionBandMarker && other.color == color
    override fun hashCode() = color.hashCode()
}

/** Axis scale: range from [min] to [max] in [step]s, both ends on a step. */
internal data class AxisScale(val min: Double, val max: Double, val step: Double)

/**
 * A «nice» scale covering `[minValue, maxValue]` and zero with about [ticks] steps of
 * 1, 2, 2.5 or 5 × 10ⁿ — axis labels read «2,5 тыс · 5 тыс · 7,5 тыс», not «43 тыс · 87 тыс».
 */
internal fun niceScale(minValue: Double, maxValue: Double, ticks: Int = VALUE_TICKS): AxisScale {
    val lo = minOf(0.0, minValue)
    val hi = maxOf(0.0, maxValue)
    val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0
    val raw = span / ticks
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= raw * (1 - 1e-9) }
    val max = ceil(hi / step - 1e-9) * step
    val min = floor(lo / step + 1e-9) * step
    return AxisScale(min, if (max > min) max else min + step, step)
}

private fun lineScale(buckets: List<ChartBucket>, scale: Double): AxisScale {
    var income = 0L
    var expense = 0L
    var low = 0L
    var high = 0L
    for (b in buckets) {
        income += b.incomeMinor
        expense += b.netExpenseMinor
        low = minOf(low, income, expense)
        high = maxOf(high, income, expense)
    }
    return niceScale(low / scale, high / scale)
}
