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
import androidx.compose.ui.unit.dp
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldMotion
import com.foldfinance.app.ui.theme.FoldShapes

/**
 * Pill chip for a category: colour dot + name. Used for filters and as a drop target for
 * drag & drop on the inner screen (stage «полировка»).
 *
 * @param selected active filter: saffron container, as every active choice in the app.
 * @param onClick `null` makes the chip a static label.
 */
@Composable
fun CategoryChip(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val palette = FoldFinanceTheme.palette
    val container by animateColorAsState(
        targetValue = if (selected) palette.saffronContainer else palette.surfaceRaised,
        animationSpec = FoldMotion.effects(),
        label = "chipContainer",
    )
    val content = if (selected) palette.onSaffronContainer else palette.text
    val interaction = if (onClick != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
    } else {
        Modifier
    }
    Box(modifier = modifier.then(interaction), contentAlignment = Alignment.Center) {
        Surface(
            shape = FoldShapes.pill,
            color = container,
            border = if (selected) BorderStroke(1.dp, palette.saffron.copy(alpha = 0.5f)) else null,
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 34.dp)
                    .padding(start = 12.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(color, FoldShapes.pill),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = name, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
            }
        }
    }
}
