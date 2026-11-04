package com.foldfinance.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldfinance.app.ui.theme.FoldFinanceTheme

/** Direction of the perforation line. */
enum class SeamOrientation { Vertical, Horizontal }

/**
 * The seam — the only ornament of the design: a perforation line of 2 dp dots every 8 dp in
 * `outlineVariant`, drawn along the hinge between two panes (docs/design.md, principle 1).
 *
 * Purely decorative: not interactive, invisible to accessibility services, and nothing is ever
 * placed over it. [TwoPane][com.foldfinance.app.ui.layout.TwoPane] draws it automatically; use it
 * directly only for custom two-pane layouts.
 *
 * @param orientation [SeamOrientation.Vertical] for book posture, [SeamOrientation.Horizontal]
 *   for tabletop.
 * @param thickness cross-axis size of the seam area; dots are centred in it.
 * @param color dot colour, the palette `seam` token by default.
 */
@Composable
fun HingeSeam(
    orientation: SeamOrientation,
    modifier: Modifier = Modifier,
    thickness: Dp = 8.dp,
    color: Color = FoldFinanceTheme.palette.seam,
    dotSize: Dp = 2.dp,
    step: Dp = 8.dp,
) {
    val sized = when (orientation) {
        SeamOrientation.Vertical -> modifier.width(thickness).fillMaxHeight()
        SeamOrientation.Horizontal -> modifier.height(thickness).fillMaxWidth()
    }
    Canvas(sized.clearAndSetSemantics { }) {
        val radius = dotSize.toPx() / 2f
        val stepPx = step.toPx()
        val length = if (orientation == SeamOrientation.Vertical) size.height else size.width
        if (length <= 0f || stepPx <= 0f) return@Canvas
        val count = (length / stepPx).toInt()
        // Centre the run of dots so both ends of the seam look the same.
        val start = (length - (count - 1).coerceAtLeast(0) * stepPx) / 2f
        val cross = if (orientation == SeamOrientation.Vertical) size.width / 2f else size.height / 2f
        for (i in 0 until count) {
            val along = start + i * stepPx
            val center = if (orientation == SeamOrientation.Vertical) Offset(cross, along) else Offset(along, cross)
            drawCircle(color = color, radius = radius, center = center)
        }
    }
}
