package com.foldfinance.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldMotion
import com.foldfinance.app.ui.theme.FoldShapes

/** Look of an unselected [CategoryChip]. */
enum class ChipStyle {
    /** Field-coloured fill, no border: chips to pick from (add form, review). */
    Filled,

    /** Transparent with a hairline border: filters over a list. */
    Outlined,
}

/**
 * Chip for a category: colour dot + name, 12 dp corners. Used for filters, pickers and as a drop
 * target for drag & drop on the inner screen.
 *
 * @param selected active choice: soft lime fill with a lime border, as every active choice.
 * @param onClick `null` makes the chip a static label.
 * @param height visual height; the touch target is at least 48 dp regardless.
 */
@Composable
fun CategoryChip(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    style: ChipStyle = ChipStyle.Filled,
    height: Dp = 38.dp,
) {
    val palette = FoldFinanceTheme.palette
    val idle = if (style == ChipStyle.Filled) palette.surfaceRaised else Color.Transparent
    val container by animateColorAsState(
        targetValue = if (selected) palette.accentSoft else idle,
        animationSpec = FoldMotion.effects(),
        label = "chipContainer",
    )
    val border = when {
        selected -> BorderStroke(1.dp, palette.accentLine)
        style == ChipStyle.Outlined -> BorderStroke(1.dp, palette.outline)
        else -> null
    }
    val interaction = if (onClick != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
    } else {
        Modifier
    }
    Box(modifier = modifier.then(interaction), contentAlignment = Alignment.Center) {
        Surface(shape = FoldShapes.chip, color = container, border = border) {
            Row(
                modifier = Modifier
                    .heightIn(min = height)
                    .padding(start = 12.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(color, FoldShapes.pill),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) palette.text else palette.textSoft,
                    maxLines = 1,
                )
            }
        }
    }
}
