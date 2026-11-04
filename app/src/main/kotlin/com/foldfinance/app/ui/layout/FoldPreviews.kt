package com.foldfinance.app.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import com.foldfinance.app.ui.theme.FoldFinanceTheme

/**
 * Preview in the three Fold configurations (estimates before measuring on the device, see
 * docs/testing-strategy.md): outer 411×658, inner book 882×662, tabletop 662×882.
 *
 * Previews cannot emulate a hinge: wrap the content in [FoldPreviewSurface], which derives the
 * [AppLayout] from the preview size.
 */
@Preview(name = "Внешний 411×658", device = "spec:width=411dp,height=658dp,dpi=420", showSystemUi = false)
@Preview(name = "Внутренний 882×662", device = "spec:width=882dp,height=662dp,dpi=420", showSystemUi = false)
@Preview(name = "Tabletop 662×882", device = "spec:width=662dp,height=882dp,dpi=420", showSystemUi = false)
annotation class FoldPreviews

/**
 * Theme + [LocalAppLayout] for previews. The fold is synthesised from the preview size: a wide
 * landscape window gets a vertical FLAT hinge in the middle, a ≥ 600 dp portrait window gets a
 * horizontal HALF_OPENED hinge (tabletop), anything narrower is compact.
 */
@Composable
fun FoldPreviewSurface(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp.toFloat()
    val height = configuration.screenHeightDp.toFloat()
    val layout = computeAppLayout(width, height, previewFolds(width, height))
    CompositionLocalProvider(LocalAppLayout provides layout) {
        FoldFinanceTheme(darkTheme = darkTheme, reducedMotion = true) {
            Box(Modifier.fillMaxSize().background(FoldFinanceTheme.palette.ink)) { content() }
        }
    }
}

/** Synthetic folds for a preview/screenshot of the given size; see [FoldPreviewSurface]. */
fun previewFolds(widthDp: Float, heightDp: Float): List<Fold> = when {
    widthDp < AppLayout.TWO_PANE_MIN_WIDTH_DP -> emptyList()
    widthDp > heightDp -> listOf(
        Fold(DpBounds(widthDp / 2, 0f, widthDp / 2, heightDp), FoldState.FLAT, FoldOrientation.VERTICAL, isSeparating = false),
    )
    else -> listOf(
        Fold(DpBounds(0f, heightDp / 2, widthDp, heightDp / 2), FoldState.HALF_OPENED, FoldOrientation.HORIZONTAL, isSeparating = true),
    )
}
