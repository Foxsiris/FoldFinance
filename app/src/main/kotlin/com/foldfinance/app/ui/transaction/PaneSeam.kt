package com.foldfinance.app.ui.transaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.foldfinance.app.ui.components.HingeSeam
import com.foldfinance.app.ui.components.SeamOrientation
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.layout.SplitSource

/**
 * Draws the [HingeSeam] on the hinge over a container that splits its panes itself
 * (`ListDetailPaneScaffold` with `foldPaneScaffoldDirective()`), the way `TwoPane` does it for
 * its own panes. Only in book posture with a real hinge; nothing otherwise.
 *
 * Place it last inside a `Box` that fills the same area as the scaffold.
 */
@Composable
fun BoxScope.HingeSeamOverlay(layout: AppLayout = LocalAppLayout.current) {
    if (layout !is AppLayout.Book || layout.splitSource != SplitSource.Hinge) return
    val density = LocalDensity.current
    var originX by remember { mutableFloatStateOf(Float.NaN) }
    Box(Modifier.matchParentSize().onPlaced { originX = it.positionInWindow().x }) {
        if (!originX.isNaN()) {
            val seamWidth = 8.dp
            val x = with(density) { layout.splitAtDp.dp - originX.toDp() } - seamWidth / 2
            HingeSeam(SeamOrientation.Vertical, Modifier.offset(x = x).fillMaxHeight(), thickness = seamWidth)
        }
    }
}
