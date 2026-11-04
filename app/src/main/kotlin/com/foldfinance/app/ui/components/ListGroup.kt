package com.foldfinance.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes

/**
 * A group of list rows on a card: 20 dp corners, rows inside are not rounded
 * (docs/design.md, «Форма по иерархии»). Separate rows with [ListGroupDivider].
 */
@Composable
fun ListGroup(
    modifier: Modifier = Modifier,
    color: Color = FoldFinanceTheme.palette.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, shape = FoldShapes.listGroup, color = color) {
        Column(content = content)
    }
}

/**
 * Hairline between rows of a [ListGroup], inset to start after the row icon so the icons form
 * an uninterrupted column.
 */
@Composable
fun ListGroupDivider(modifier: Modifier = Modifier, startInset: Dp = 68.dp) {
    HorizontalDivider(
        modifier = modifier.padding(start = startInset),
        thickness = 1.dp,
        color = FoldFinanceTheme.palette.seam.copy(alpha = 0.6f),
    )
}
