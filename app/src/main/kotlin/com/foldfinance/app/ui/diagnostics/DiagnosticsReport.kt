package com.foldfinance.app.ui.diagnostics

import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.FoldReading
import com.foldfinance.app.ui.layout.PxBounds
import com.foldfinance.app.ui.layout.WindowReadings
import com.foldfinance.app.ui.layout.heightLabel
import com.foldfinance.app.ui.layout.widthLabel
import java.util.Locale
import kotlin.math.roundToInt

/** Number formatting for the diagnostics screen and its plain-text report. */
internal object DiagnosticsFormat {
    /** `441`, `661.7` — dp with at most one decimal, trailing `.0` dropped. */
    fun dp(value: Float): String {
        val tenths = (value * 10f).roundToInt()
        return if (tenths % 10 == 0) (tenths / 10).toString() else String.format(Locale.US, "%.1f", tenths / 10f)
    }

    /** `2.625` — density with up to three decimals. */
    fun density(value: Float): String =
        String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')

    fun px(bounds: PxBounds): String = "[${bounds.left}, ${bounds.top}, ${bounds.right}, ${bounds.bottom}]"

    fun dp(bounds: PxBounds, density: Float): String = bounds.toDp(density).let {
        "[${dp(it.left)}, ${dp(it.top)}, ${dp(it.right)}, ${dp(it.bottom)}]"
    }

    /** Short machine-friendly description of a layout. */
    fun layout(layout: AppLayout): String = when (layout) {
        is AppLayout.Compact -> "Compact"
        is AppLayout.Book -> "Book splitX=${dp(layout.splitAtDp)}dp (${layout.splitSource}) hinge=${dp(layout.hingeWidthDp)}dp"
        is AppLayout.Tabletop -> "Tabletop splitY=${dp(layout.splitAtDp)}dp hinge=${dp(layout.hingeHeightDp)}dp"
    }

    fun fold(fold: FoldReading, density: Float): String =
        "bounds px ${px(fold.boundsPx)}, dp ${dp(fold.boundsPx, density)}, state ${fold.state}, " +
            "orientation ${fold.orientation}, isSeparating ${fold.isSeparating}, occlusionType ${fold.occlusion}"
}

/**
 * Plain-text report the user copies and sends back after measuring the device (stage 0:
 * the real dp of both Fold screens go into CLAUDE.md).
 *
 * @param device `Build.MANUFACTURER Build.MODEL`, e.g. `samsung SM-F966B`.
 * @param sdkInt `Build.VERSION.SDK_INT`.
 */
fun diagnosticsReport(readings: WindowReadings, device: String, sdkInt: Int): String = buildString {
    val f = DiagnosticsFormat
    val sizeClass = readings.sizeClass
    appendLine("FoldFinance — диагностика окна")
    appendLine("Устройство: $device, Android SDK $sdkInt")
    appendLine(
        "Окно: ${f.dp(readings.widthDp)} × ${f.dp(readings.heightDp)} dp " +
            "(${readings.widthPx} × ${readings.heightPx} px), density ${f.density(readings.density)}",
    )
    appendLine("Size class: width ${sizeClass.widthLabel()}, height ${sizeClass.heightLabel()}")
    appendLine("AppLayout: ${f.layout(readings.toAppLayout())}")
    if (readings.folds.isEmpty()) {
        append("FoldingFeature: нет")
    } else {
        readings.folds.forEachIndexed { index, fold ->
            if (index > 0) appendLine()
            append("FoldingFeature ${index + 1}: ${f.fold(fold, readings.density)}")
        }
    }
}
