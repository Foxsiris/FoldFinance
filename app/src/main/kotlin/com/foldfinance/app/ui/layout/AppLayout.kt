package com.foldfinance.app.ui.layout

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** Posture of a fold as reported by Jetpack WindowManager (`FoldingFeature.State`). */
enum class FoldState { FLAT, HALF_OPENED }

/** Orientation of the fold line (`FoldingFeature.Orientation`). */
enum class FoldOrientation { VERTICAL, HORIZONTAL }

/** Whether the fold hides content (`FoldingFeature.OcclusionType`). */
enum class FoldOcclusion { NONE, FULL }

/**
 * A rectangle in window coordinates, in dp. Pure data so that [computeAppLayout] stays a plain
 * function testable on the JVM.
 */
@Immutable
data class DpBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * A `FoldingFeature` converted to dp, in window coordinates.
 *
 * On Galaxy Z Fold the inner-screen hinge is reported with zero width, `isSeparating = false`
 * while FLAT and `true` while HALF_OPENED.
 */
@Immutable
data class Fold(
    val bounds: DpBounds,
    val state: FoldState,
    val orientation: FoldOrientation,
    val isSeparating: Boolean,
    val occlusion: FoldOcclusion = FoldOcclusion.NONE,
)

/** How the split position of a two-pane layout was chosen. */
enum class SplitSource {
    /** Split runs along a physical hinge. */
    Hinge,

    /** No usable hinge: split by [AppLayout.DEFAULT_SPLIT_FRACTION] of the window. */
    Proportion,
}

/**
 * Window-level layout mode, computed once per window/posture change by [computeAppLayout] and
 * read by every screen through [LocalAppLayout].
 *
 * - [Compact] — outer screen, split-screen halves, narrow pop-up windows: one pane, navigation
 *   bar at the bottom, thumb zone matters.
 * - [Book] — two panes side by side (inner screen, any window ≥ 600 dp wide).
 * - [Tabletop] — horizontal hinge half-opened: top pane to look at, bottom pane to touch.
 *
 * All coordinates are window coordinates in dp; [TwoPane] converts them to its own position.
 */
@Immutable
sealed interface AppLayout {
    val windowWidthDp: Float
    val windowHeightDp: Float

    /** Single pane. */
    @Immutable
    data class Compact(
        override val windowWidthDp: Float,
        override val windowHeightDp: Float,
    ) : AppLayout

    /**
     * Left/right panes.
     *
     * @property splitAtDp x of the pane boundary: the hinge centre, or a proportion of the width.
     * @property hingeWidthDp physical width of the hinge to keep clear (0 for Fold's seamless hinge
     *   or when there is no hinge).
     * @property separating the hinge separates the window (`isSeparating` or HALF_OPENED): every
     *   two-pane screen puts its boundary on it. While FLAT a screen may size its panes freely
     *   (Analytics gives the chart pane more room).
     */
    @Immutable
    data class Book(
        override val windowWidthDp: Float,
        override val windowHeightDp: Float,
        val splitAtDp: Float,
        val hingeWidthDp: Float,
        val splitSource: SplitSource,
        val separating: Boolean = false,
    ) : AppLayout

    /**
     * Top/bottom panes split by a horizontal hinge.
     *
     * @property splitAtDp y of the hinge centre.
     * @property hingeHeightDp physical height of the hinge to keep clear.
     */
    @Immutable
    data class Tabletop(
        override val windowWidthDp: Float,
        override val windowHeightDp: Float,
        val splitAtDp: Float,
        val hingeHeightDp: Float,
    ) : AppLayout

    companion object {
        /** Width from which two panes are shown (Material «Medium», 600 dp). */
        const val TWO_PANE_MIN_WIDTH_DP: Float = 600f

        /** Width of Material «Expanded», 840 dp. */
        const val EXPANDED_MIN_WIDTH_DP: Float = 840f

        /** Height from which Material calls a window «Medium», 480 dp. */
        const val MEDIUM_MIN_HEIGHT_DP: Float = 480f

        /** Height of Material «Expanded», 900 dp. */
        const val EXPANDED_MIN_HEIGHT_DP: Float = 900f

        /** Split for two panes without a hinge: symmetric, like the Fold hinge itself. */
        const val DEFAULT_SPLIT_FRACTION: Float = 0.5f

        /**
         * A hinge closer than this to a window edge does not split the window (for example
         * the left half of a split-screen on the inner display, where the hinge is the edge).
         */
        const val MIN_PANE_DP: Float = 240f
    }
}

/** Two panes are shown. */
val AppLayout.isTwoPane: Boolean get() = this !is AppLayout.Compact

/**
 * Chooses the window layout from the window size and the folding features that intersect it.
 *
 * Rules, in order:
 * 1. A horizontal fold that is HALF_OPENED or separating and leaves at least
 *    [AppLayout.MIN_PANE_DP] on both sides → [AppLayout.Tabletop] split on the fold.
 * 2. A vertical fold that is separating (any width), or any vertical fold in a window at least
 *    [AppLayout.TWO_PANE_MIN_WIDTH_DP] wide, with room for both panes → [AppLayout.Book] split
 *    exactly on the fold. This covers the inner Fold screen while FLAT: the hinge is not
 *    separating, yet the pane boundary must still follow it (CLAUDE.md, «Шарнир и позы»).
 * 3. No usable fold and width ≥ 600 dp (Medium and wider, including wide pop-up windows) →
 *    [AppLayout.Book] split by [AppLayout.DEFAULT_SPLIT_FRACTION].
 * 4. Otherwise → [AppLayout.Compact].
 *
 * Folds outside the window or touching its edge are ignored. A FLAT, non-separating horizontal
 * fold is ignored: content may flow across it, and two stacked panes on a flat portrait screen
 * would only waste space.
 */
fun computeAppLayout(widthDp: Float, heightDp: Float, folds: List<Fold>): AppLayout {
    val horizontal = folds.firstOrNull { fold ->
        fold.orientation == FoldOrientation.HORIZONTAL &&
            (fold.state == FoldState.HALF_OPENED || fold.isSeparating) &&
            fold.bounds.top >= AppLayout.MIN_PANE_DP &&
            heightDp - fold.bounds.bottom >= AppLayout.MIN_PANE_DP
    }
    if (horizontal != null) {
        return AppLayout.Tabletop(
            windowWidthDp = widthDp,
            windowHeightDp = heightDp,
            splitAtDp = horizontal.bounds.centerY,
            hingeHeightDp = horizontal.bounds.height.coerceAtLeast(0f),
        )
    }

    val wide = widthDp >= AppLayout.TWO_PANE_MIN_WIDTH_DP
    val vertical = folds.firstOrNull { fold ->
        fold.orientation == FoldOrientation.VERTICAL &&
            (wide || fold.isSeparating) &&
            fold.bounds.left >= AppLayout.MIN_PANE_DP &&
            widthDp - fold.bounds.right >= AppLayout.MIN_PANE_DP
    }
    if (vertical != null) {
        return AppLayout.Book(
            windowWidthDp = widthDp,
            windowHeightDp = heightDp,
            splitAtDp = vertical.bounds.centerX,
            hingeWidthDp = vertical.bounds.width.coerceAtLeast(0f),
            splitSource = SplitSource.Hinge,
            separating = vertical.isSeparating || vertical.state == FoldState.HALF_OPENED,
        )
    }

    if (wide) {
        return AppLayout.Book(
            windowWidthDp = widthDp,
            windowHeightDp = heightDp,
            splitAtDp = widthDp * AppLayout.DEFAULT_SPLIT_FRACTION,
            hingeWidthDp = 0f,
            splitSource = SplitSource.Proportion,
        )
    }
    return AppLayout.Compact(widthDp, heightDp)
}

/**
 * Current window layout. Provided by `MainActivity` from WindowManager; tests and previews
 * provide it explicitly. The default is a phone-sized compact layout so that a forgotten
 * provider degrades to the safest mode.
 */
val LocalAppLayout = staticCompositionLocalOf<AppLayout> { AppLayout.Compact(411f, 658f) }
