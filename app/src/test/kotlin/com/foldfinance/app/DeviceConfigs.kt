package com.foldfinance.app

import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.DpBounds
import com.foldfinance.app.ui.layout.Fold
import com.foldfinance.app.ui.layout.FoldOrientation
import com.foldfinance.app.ui.layout.FoldState
import com.foldfinance.app.ui.layout.WindowReadings
import com.foldfinance.app.ui.layout.computeAppLayout

/**
 * A window configuration for screenshot tests.
 *
 * **Оценка до замера на устройстве** (docs/testing-strategy.md): sizes and hinge positions are
 * estimates for Galaxy Z Fold 8. After measuring with «Диагностика окна» on the phone, replace
 * the numbers here (and in CLAUDE.md) and re-record the screenshots.
 *
 * @property id file-name part of screenshots.
 * @property folds folding features in window dp, as WindowManager would report them.
 * @property density density of the screenshot (xhdpi, matches [qualifiers]); the real Fold 8
 *   density is measured on the device.
 */
data class DeviceConfig(
    val id: String,
    val widthDp: Int,
    val heightDp: Int,
    val folds: List<Fold>,
    val density: Float = 2f,
) {
    /** Layout the app computes for this configuration — the same pure function as on device. */
    val layout: AppLayout get() = computeAppLayout(widthDp.toFloat(), heightDp.toFloat(), folds)

    /** Raw readings for the diagnostics screen. */
    val readings: WindowReadings get() = WindowReadings.fromDp(widthDp.toFloat(), heightDp.toFloat(), density, folds)

    /** Robolectric qualifiers: size, orientation, density 2x (keeps PNGs reasonably small). */
    fun qualifiers(dark: Boolean): String {
        val orientation = if (widthDp > heightDp) "land" else "port"
        val night = if (dark) "night" else "notnight"
        return "ru-w${widthDp}dp-h${heightDp}dp-$orientation-$night-xhdpi"
    }
}

/** The three Fold 8 configurations every main screen is captured in. */
object DeviceConfigs {
    /** Outer screen, 5.5" 16:10 — Compact, one hand. Estimate. */
    val Outer = DeviceConfig(id = "outer", widthDp = 411, heightDp = 658, folds = emptyList())

    /** Inner screen unfolded flat, natural landscape, vertical hinge in the middle. Estimate. */
    val Inner = DeviceConfig(
        id = "inner",
        widthDp = 882,
        heightDp = 662,
        folds = listOf(
            Fold(DpBounds(441f, 0f, 441f, 662f), FoldState.FLAT, FoldOrientation.VERTICAL, isSeparating = false),
        ),
    )

    /** Inner screen rotated, half-opened: horizontal hinge, top to look, bottom to touch. Estimate. */
    val Tabletop = DeviceConfig(
        id = "tabletop",
        widthDp = 662,
        heightDp = 882,
        folds = listOf(
            Fold(DpBounds(0f, 441f, 662f, 441f), FoldState.HALF_OPENED, FoldOrientation.HORIZONTAL, isSeparating = true),
        ),
    )

    /**
     * Inner screen in portrait, as the «7.6in Foldable» emulator (Android 16) shows it: 674×841 dp,
     * vertical hinge at x = 337. With the rail the left pane is only ≈ 230 dp — the narrowest
     * two-pane case; catches amounts, rows and lists that do not fit next to the hinge.
     */
    val InnerPortrait = DeviceConfig(
        id = "inner_portrait",
        widthDp = 674,
        heightDp = 841,
        folds = listOf(
            Fold(DpBounds(337f, 0f, 337f, 841f), FoldState.FLAT, FoldOrientation.VERTICAL, isSeparating = false),
        ),
    )

    /** Outer screen of the same emulator: 337×841 dp, Compact — the narrowest bottom bar. */
    val OuterNarrow = DeviceConfig(id = "outer_narrow", widthDp = 337, heightDp = 841, folds = emptyList())

    val all: List<DeviceConfig> = listOf(Outer, Inner, Tabletop)

    /** [all] plus the emulator's narrow windows: for the main screens. */
    val withNarrow: List<DeviceConfig> = all + listOf(InnerPortrait, OuterNarrow)
}
