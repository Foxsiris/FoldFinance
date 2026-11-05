package com.foldfinance.app.ui.analytics

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.Onest
import com.foldfinance.app.ui.theme.TabularNumbers
import com.patrykandpatrick.vico.compose.cartesian.CartesianChart
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
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
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerPadding
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.DashedShape
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
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
 * The analytics bar chart (Analytics / Tabletop mockups), on Vico.
 *
 * - Columns: net expenses of each day — or month for long periods — in the quiet chart grey
 *   (expenses are the norm, not an alarm); the tapped column turns lime, the colour of every
 *   active choice in the app, and carries a light tooltip with its date and amount.
 * - A dashed line marks the average per day of the period («среднее 2 303 ₽»).
 * - The value axis on the start side: compact round amounts («4 тыс»), hairline guidelines.
 *
 * The model is built synchronously from [buckets] (no `CartesianChartModelProducer`): the data is
 * already in memory, and screenshots stay deterministic. Zoom and scroll are off — the whole period
 * always fits the width. A tap on a column reports its index through [onSelect]
 * (`CartesianMarkerVisibilityListener` + toggle-on-tap marker controller); the selection itself
 * lives in the ViewModel, so the tooltip survives folding.
 *
 * @param bucketLabel axis label of a column by index («5», «пн», «янв»).
 * @param markerLabel text of the tooltip of a column («сб, 17 октября · 4 820 ₽»).
 * @param averageMinor average per bucket to draw as a dashed line; `null` — none.
 * @param averageLabel label of the dashed line.
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
    averageMinor: Long? = null,
    averageLabel: String? = null,
) {
    if (buckets.isEmpty()) return
    val palette = FoldFinanceTheme.palette
    val digits = com.foldfinance.app.ui.format.MoneyFormat.fractionDigits(currency)
    val scale = remember(digits) { 10.0.pow(digits.toDouble()) }
    val model = remember(buckets, scale) { chartModel(buckets, scale) }
    // Round axis steps (1 · 2 · 2,5 · 5 × 10ⁿ) and a range that ends on a step.
    val columnScale = remember(buckets, scale) {
        niceScale(buckets.minOf { it.netExpenseMinor } / scale, buckets.maxOf { it.netExpenseMinor } / scale, ticks = 2)
    }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentBucketLabel by rememberUpdatedState(bucketLabel)
    val currentMarkerLabel by rememberUpdatedState(markerLabel)

    BoxWithConstraints(modifier.semantics { contentDescription = description }) {
        // The value axis takes ~44 dp; the rest is shared by the columns.
        val plotWidth = (maxWidth - 44.dp).coerceAtLeast(120.dp)
        val slot = plotWidth / buckets.size
        val thickness = (slot * 0.7f).coerceIn(3.dp, 32.dp)
        val spacing = (slot - thickness).coerceAtLeast(1.dp)
        val corner = (thickness / 2).coerceAtMost(5.dp)

        val baseColumn = rememberLineComponent(
            fill = Fill(palette.chartBar),
            thickness = thickness,
            shape = RoundedCornerShape(corner),
        )
        val selectedColumn = rememberLineComponent(
            fill = Fill(palette.accent),
            thickness = thickness,
            shape = RoundedCornerShape(corner),
        )
        val columnProvider = remember(baseColumn, selectedColumn, selectedIndex) {
            HighlightColumnProvider(baseColumn, selectedColumn, selectedIndex)
        }

        val labelStyle = TextStyle(
            color = palette.textMuted,
            fontFamily = Onest,
            fontSize = 11.sp,
            fontFeatureSettings = TabularNumbers,
        )
        val axisLabel = rememberAxisLabelComponent(style = labelStyle)
        val guideline = rememberAxisGuidelineComponent(fill = Fill(palette.seam), thickness = 1.dp)
        val amountFormatter = remember(scale, currency) { AmountAxisFormatter(scale, currency) }
        val labelSpacing = remember(buckets.size, plotWidth) {
            ceil(buckets.size / (plotWidth / MIN_LABEL_WIDTH).coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        }
        val bottomFormatter = remember { BucketAxisFormatter { currentBucketLabel(it) } }

        // Tapping reports the column; the visible tooltip is the persistent one below.
        val tapMarker = rememberDefaultCartesianMarker(
            label = rememberTextComponent(style = labelStyle.copy(color = Color.Transparent)),
            valueFormatter = remember { MarkerFormatter { "" } },
            labelPosition = DefaultCartesianMarker.LabelPosition.Top,
            guideline = null,
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
        val tooltipText = rememberTextComponent(
            style = TextStyle(color = palette.ink, fontFamily = Onest, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TabularNumbers),
            padding = Insets(horizontal = 11.dp, vertical = 7.dp),
            background = rememberShapeComponent(fill = Fill(palette.text), shape = RoundedCornerShape(12.dp)),
        )
        val tooltip = remember(tooltipText, columnScale, scale, buckets) {
            TooltipMarker(tooltipText, { currentMarkerLabel(it) }) { index ->
                val value = (buckets.getOrNull(index)?.netExpenseMinor ?: 0L) / scale
                ((value - columnScale.min) / (columnScale.max - columnScale.min)).toFloat().coerceIn(0f, 1f)
            }
        }
        val persistentMarkers: CartesianChart.PersistentMarkerScope.(ExtraStore) -> Unit = remember(selectedIndex, tooltip) {
            { if (selectedIndex != null) tooltip.at(selectedIndex) }
        }

        val averageLine = rememberLineComponent(
            fill = Fill(palette.textDisabled),
            thickness = 1.dp,
            shape = DashedShape(RectangleShape, dashLength = 4.dp, gapLength = 4.dp),
        )
        val averageText = rememberTextComponent(
            style = labelStyle.copy(color = palette.textSecondary),
            padding = Insets(start = 6.dp, bottom = 2.dp),
        )
        val decorations = remember(averageMinor, averageLabel, averageLine, averageText, scale) {
            if (averageMinor == null || averageMinor <= 0) {
                emptyList()
            } else {
                listOf(
                    HorizontalLine(
                        y = { averageMinor / scale },
                        line = averageLine,
                        labelComponent = if (averageLabel != null) averageText else null,
                        label = { averageLabel.orEmpty() },
                        horizontalLabelPosition = Position.Horizontal.End,
                        verticalLabelPosition = Position.Vertical.Top,
                    ),
                )
            }
        }

        val chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = columnProvider,
                columnCollectionSpacing = spacing,
                rangeProvider = remember(columnScale) { CartesianLayerRangeProvider.fixed(minY = columnScale.min, maxY = columnScale.max) },
                verticalAxisPosition = Axis.Position.Vertical.Start,
            ),
            startAxis = VerticalAxis.rememberStart(
                line = null,
                label = axisLabel,
                valueFormatter = amountFormatter,
                tick = null,
                guideline = guideline,
                itemPlacer = remember(columnScale) { VerticalAxis.ItemPlacer.step({ columnScale.step }) },
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                line = rememberAxisLineComponent(fill = Fill(palette.outline), thickness = 1.dp),
                label = axisLabel,
                valueFormatter = bottomFormatter,
                tick = null,
                guideline = null,
                itemPlacer = remember(labelSpacing) { HorizontalAxis.ItemPlacer.aligned(spacing = { labelSpacing }) },
            ),
            marker = tapMarker,
            markerVisibilityListener = visibilityListener,
            markerController = CartesianMarkerController.rememberToggleOnTap(),
            persistentMarkers = persistentMarkers,
            decorations = decorations,
            layerPadding = remember(thickness) {
                { _: ExtraStore ->
                    CartesianLayerPadding(scalableStart = thickness / 2 + 2.dp, scalableEnd = thickness / 2 + 2.dp, unscalableStart = 0.dp, unscalableEnd = 0.dp)
                }
            },
        )
        CartesianChartHost(
            chart = chart,
            model = model,
            modifier = if (constraints.hasBoundedHeight) Modifier.fillMaxSize() else Modifier,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content, minZoom = Zoom.Content, maxZoom = Zoom.Content),
        )
    }
}

private const val VALUE_TICKS = 4
private val MIN_LABEL_WIDTH: Dp = 40.dp

/** Columns: daily (or monthly) net expenses. */
private fun chartModel(buckets: List<ChartBucket>, scale: Double): CartesianChartModel =
    CartesianChartModel(
        ColumnCartesianLayerModel.build { series(buckets.indices.toList(), buckets.map { it.netExpenseMinor / scale }) },
    )

/** Base columns, with the selected one drawn lime. */
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

/** Label of the tap marker (invisible: the tooltip is drawn by [TooltipMarker]). */
private class MarkerFormatter(private val label: (Int) -> String) : DefaultCartesianMarker.ValueFormatter {
    override fun format(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>): CharSequence =
        targets.firstOrNull()?.x?.roundToInt()?.let(label).orEmpty()
}

/**
 * Persistent tooltip of the selected column: a light pill with the date and the amount, right
 * above the top of the column (Analytics mockup).
 *
 * @param heightFraction top of the column at an index, 0…1 of the layer height.
 */
private class TooltipMarker(
    private val text: TextComponent,
    private val label: (Int) -> String,
    private val heightFraction: (Int) -> Float,
) : CartesianMarker {
    private val unused = Paint()

    override fun drawOverLayers(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>) {
        val target = targets.firstOrNull() ?: return
        val index = target.x.roundToInt()
        val bounds = context.layerBounds
        val top = bounds.bottom - bounds.height * heightFraction(index)
        val gap = with(context) { 8.dp.pixels }
        val y = (top - gap).coerceAtLeast(bounds.top + with(context) { 36.dp.pixels })
        val half = with(context) { 70.dp.pixels }
        val x = target.canvasX.coerceIn(bounds.left + half, bounds.right - half)
        text.draw(
            context = context,
            text = label(index),
            x = x,
            y = y,
            horizontalPosition = Position.Horizontal.Center,
            verticalPosition = Position.Vertical.Top,
        )
    }

    override fun equals(other: Any?) = other === this
    override fun hashCode() = unused.hashCode()
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
