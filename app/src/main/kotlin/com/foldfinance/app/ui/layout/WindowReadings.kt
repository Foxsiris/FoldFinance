package com.foldfinance.app.ui.layout

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.map

/** A rectangle in window pixels. */
@Immutable
data class PxBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun toDp(density: Float) = DpBounds(left / density, top / density, right / density, bottom / density)
}

/** Raw `FoldingFeature` reading, kept in pixels for the diagnostics screen. */
@Immutable
data class FoldReading(
    val boundsPx: PxBounds,
    val state: FoldState,
    val orientation: FoldOrientation,
    val isSeparating: Boolean,
    val occlusion: FoldOcclusion,
) {
    fun toFold(density: Float) = Fold(boundsPx.toDp(density), state, orientation, isSeparating, occlusion)
}

/**
 * Everything the app knows about its window: size in pixels, density and folding features.
 * The single source for [AppLayout] (via [toAppLayout]) and for the diagnostics screen.
 */
@Immutable
data class WindowReadings(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val folds: List<FoldReading>,
) {
    val widthDp: Float get() = widthPx / density
    val heightDp: Float get() = heightPx / density

    /** Material window size class (breakpoints v2: Compact/Medium/Expanded/Large/Extra-large). */
    val sizeClass: WindowSizeClass
        get() = WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(widthDp, heightDp)

    fun foldsDp(): List<Fold> = folds.map { it.toFold(density) }

    fun toAppLayout(): AppLayout = computeAppLayout(widthDp, heightDp, foldsDp())

    companion object {
        /** Builds readings from dp values; used by tests, previews and screenshot configs. */
        fun fromDp(widthDp: Float, heightDp: Float, density: Float, folds: List<Fold>) = WindowReadings(
            widthPx = Math.round(widthDp * density),
            heightPx = Math.round(heightDp * density),
            density = density,
            folds = folds.map { fold ->
                FoldReading(
                    boundsPx = PxBounds(
                        Math.round(fold.bounds.left * density),
                        Math.round(fold.bounds.top * density),
                        Math.round(fold.bounds.right * density),
                        Math.round(fold.bounds.bottom * density),
                    ),
                    state = fold.state,
                    orientation = fold.orientation,
                    isSeparating = fold.isSeparating,
                    occlusion = fold.occlusion,
                )
            },
        )
    }
}

/** Human label of a width size class: «Compact», «Medium», «Expanded», «Large», «Extra-large». */
fun WindowSizeClass.widthLabel(): String = when {
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND) -> "Extra-large"
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND) -> "Large"
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> "Expanded"
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> "Medium"
    else -> "Compact"
}

/** Human label of a height size class: «Compact», «Medium», «Expanded». */
fun WindowSizeClass.heightLabel(): String = when {
    isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND) -> "Expanded"
    isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) -> "Medium"
    else -> "Compact"
}

/** Converts a WindowManager feature into our pure reading. */
fun FoldingFeature.toReading(): FoldReading = FoldReading(
    boundsPx = PxBounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
    state = if (state == FoldingFeature.State.HALF_OPENED) FoldState.HALF_OPENED else FoldState.FLAT,
    orientation = if (orientation == FoldingFeature.Orientation.HORIZONTAL) {
        FoldOrientation.HORIZONTAL
    } else {
        FoldOrientation.VERTICAL
    },
    isSeparating = isSeparating,
    occlusion = if (occlusionType == FoldingFeature.OcclusionType.FULL) FoldOcclusion.FULL else FoldOcclusion.NONE,
)

/**
 * Live window readings for [activity]: size from `LocalWindowInfo.containerSize` (updated on
 * every window resize, including fold/unfold and split-screen resizes) and folding features
 * from `WindowInfoTracker.windowLayoutInfo(activity)`, collected while the activity is started.
 */
@Composable
fun rememberWindowReadings(activity: Activity): WindowReadings {
    val folds by remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).map { info ->
            info.displayFeatures.filterIsInstance<FoldingFeature>().map { it.toReading() }
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    return remember(size, density, folds) {
        WindowReadings(widthPx = size.width, heightPx = size.height, density = density, folds = folds)
    }
}

/**
 * Raw readings for the debug diagnostics screen; `null` outside `MainActivity`
 * (screens must use [LocalAppLayout], never this).
 */
val LocalWindowReadings = staticCompositionLocalOf<WindowReadings?> { null }
