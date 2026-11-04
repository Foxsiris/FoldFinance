package com.foldfinance.app.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldfinance.app.ui.components.HingeSeam
import com.foldfinance.app.ui.components.SeamOrientation
import kotlin.math.roundToInt

/** Defaults of [TwoPane]. */
object TwoPaneDefaults {
    /**
     * Empty space kept on each side of the hinge. No text or touch target of a pane can be
     * closer to the hinge than this (plus the physical hinge width from the folding feature).
     */
    val SeamClearance: Dp = 12.dp

    /** A pane narrower than this means the hinge is not inside this container: split evenly. */
    val MinPane: Dp = 160.dp
}

/**
 * Pure geometry of a [TwoPane] along its split axis, in pixels of the container.
 *
 * @property firstSize size of the first pane (left or top).
 * @property gapStart start of the keep-out zone around the hinge (= [firstSize]).
 * @property gapEnd end of the keep-out zone; the second pane starts here.
 * @property secondSize size of the second pane (right or bottom).
 */
data class PaneSplit(val firstSize: Int, val gapStart: Int, val gapEnd: Int, val secondSize: Int) {
    val gapSize: Int get() = gapEnd - gapStart

    companion object {
        /**
         * @param containerSize container size along the split axis, px.
         * @param splitInContainer split position relative to the container start, px.
         * @param hingeSize physical hinge thickness, px.
         * @param clearance [TwoPaneDefaults.SeamClearance] in px.
         * @param minPane [TwoPaneDefaults.MinPane] in px.
         */
        fun compute(
            containerSize: Int,
            splitInContainer: Float,
            hingeSize: Float,
            clearance: Float,
            minPane: Float,
        ): PaneSplit {
            val split = if (splitInContainer < minPane || containerSize - splitInContainer < minPane) {
                containerSize * AppLayout.DEFAULT_SPLIT_FRACTION
            } else {
                splitInContainer
            }
            val half = hingeSize / 2f + clearance
            val gapStart = (split - half).roundToInt().coerceIn(0, containerSize)
            val gapEnd = (split + half).roundToInt().coerceIn(gapStart, containerSize)
            return PaneSplit(gapStart, gapStart, gapEnd, containerSize - gapEnd)
        }
    }
}

/**
 * Two panes split exactly along the hinge, with the [HingeSeam] drawn on it.
 *
 * - [AppLayout.Book]: [first] on the left, [second] on the right.
 * - [AppLayout.Tabletop]: [first] on top (to look at), [second] at the bottom (to touch).
 * - [AppLayout.Compact]: only [first] is shown; single-pane screens decide themselves what goes
 *   there (usually the list, with the detail opened over it).
 *
 * The split comes from [layout] in window coordinates; [TwoPane] converts it to its own position
 * (it may sit next to a navigation rail), so the boundary stays on the physical hinge. Around the
 * hinge a keep-out zone of the hinge width plus [seamClearance] on each side stays empty: neither
 * text nor touch targets can end up on the fold. If the hinge is not inside this container (for
 * example the container is a sub-area), the panes split evenly instead.
 *
 * Must be measured with bounded constraints (e.g. `Modifier.fillMaxSize()`), not inside a scroll.
 * Panes get exact sizes; add your own content padding inside them.
 */
@Composable
fun TwoPane(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    layout: AppLayout = LocalAppLayout.current,
    seamClearance: Dp = TwoPaneDefaults.SeamClearance,
    showSeam: Boolean = true,
) {
    if (layout is AppLayout.Compact) {
        Box(modifier) { first() }
        return
    }
    val vertical = layout is AppLayout.Book
    var origin by remember { mutableStateOf(Offset.Zero) }
    val seam: @Composable () -> Unit = {
        if (showSeam) HingeSeam(if (vertical) SeamOrientation.Vertical else SeamOrientation.Horizontal)
    }
    Layout(
        contents = listOf(first, second, seam),
        modifier = modifier.onPlaced { origin = it.positionInWindow() },
    ) { (firstMeasurables, secondMeasurables, seamMeasurables), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        check(width != Constraints.Infinity && height != Constraints.Infinity) {
            "TwoPane needs bounded constraints"
        }
        val split = when (layout) {
            is AppLayout.Book -> PaneSplit.compute(
                containerSize = width,
                splitInContainer = layout.splitAtDp.dp.toPx() - origin.x,
                hingeSize = layout.hingeWidthDp.dp.toPx(),
                clearance = seamClearance.toPx(),
                minPane = TwoPaneDefaults.MinPane.toPx(),
            )
            is AppLayout.Tabletop -> PaneSplit.compute(
                containerSize = height,
                splitInContainer = layout.splitAtDp.dp.toPx() - origin.y,
                hingeSize = layout.hingeHeightDp.dp.toPx(),
                clearance = seamClearance.toPx(),
                minPane = TwoPaneDefaults.MinPane.toPx(),
            )
            is AppLayout.Compact -> error("unreachable")
        }
        fun fixed(w: Int, h: Int) = Constraints.fixed(w.coerceAtLeast(0), h.coerceAtLeast(0))
        val firstPlaceables: List<androidx.compose.ui.layout.Placeable>
        val secondPlaceables: List<androidx.compose.ui.layout.Placeable>
        val seamPlaceables: List<androidx.compose.ui.layout.Placeable>
        if (vertical) {
            firstPlaceables = firstMeasurables.map { it.measure(fixed(split.firstSize, height)) }
            secondPlaceables = secondMeasurables.map { it.measure(fixed(split.secondSize, height)) }
            seamPlaceables = seamMeasurables.map { it.measure(fixed(split.gapSize, height)) }
        } else {
            firstPlaceables = firstMeasurables.map { it.measure(fixed(width, split.firstSize)) }
            secondPlaceables = secondMeasurables.map { it.measure(fixed(width, split.secondSize)) }
            seamPlaceables = seamMeasurables.map { it.measure(fixed(width, split.gapSize)) }
        }
        this.layout(width, height) {
            firstPlaceables.forEach { it.place(0, 0) }
            if (vertical) {
                seamPlaceables.forEach { it.place(split.gapStart, 0) }
                secondPlaceables.forEach { it.place(split.gapEnd, 0) }
            } else {
                seamPlaceables.forEach { it.place(0, split.gapStart) }
                secondPlaceables.forEach { it.place(0, split.gapEnd) }
            }
        }
    }
}
