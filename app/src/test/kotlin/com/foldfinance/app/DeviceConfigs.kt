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
 * Sizes are **measured on Galaxy Z Fold 8 (SM-F971B, Android 17) on 2026-11-05** with `adb shell dumpsys`:
 * inner 2448×1848 px, outer 1248×1972 px, both 420 dpi (2.625). The hinge is taken at the centre of the
 * inner screen; confirm the exact FoldingFeature bounds with «Диагностика окна» and update here and in CLAUDE.md.
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
    /** Outer screen, 16:10 — Compact, one hand. Measured: 475 × 751 dp. */
    val Outer = DeviceConfig(id = "outer", widthDp = 475, heightDp = 751, folds = emptyList())

    /** Inner screen unfolded flat, natural landscape, vertical hinge in the middle. Measured: 933 × 704 dp. */
    val Inner = DeviceConfig(
        id = "inner",
        widthDp = 933,
        heightDp = 704,
        folds = listOf(
            Fold(DpBounds(466.5f, 0f, 466.5f, 704f), FoldState.FLAT, FoldOrientation.VERTICAL, isSeparating = false),
        ),
    )

    /** Inner screen rotated, half-opened: horizontal hinge, top to look, bottom to touch. Measured size. */
    val Tabletop = DeviceConfig(
        id = "tabletop",
        widthDp = 704,
        heightDp = 933,
        folds = listOf(
            Fold(DpBounds(0f, 466.5f, 704f, 466.5f), FoldState.HALF_OPENED, FoldOrientation.HORIZONTAL, isSeparating = true),
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
