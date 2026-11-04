package com.foldfinance.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes

/**
 * Empty state as an invitation to act (docs/design.md, principle 5): what is missing, why, and —
 * when there is a real action — one button for it. Never filled with fake data.
 *
 * Left-aligned like the rest of the app, width capped at 420 dp for a comfortable line length.
 *
 * @param title what is missing: «Операций пока нет».
 * @param body how to get there, one or two sentences.
 * @param icon optional pictogram in a soft tile.
 * @param actionLabel verb of the action («Разрешить», «Добавить»). Shown only together with
 *   [onAction]; omit both when the action does not exist yet — no placeholder buttons.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val palette = FoldFinanceTheme.palette
    Column(
        modifier = modifier
            .widthIn(max = 420.dp)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(palette.surfaceRaised, FoldShapes.listGroup),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = palette.saffron, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(20.dp))
        }
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            color = palette.text,
        )
        Spacer(Modifier.height(8.dp))
        Text(text = body, style = MaterialTheme.typography.bodyLarge, color = palette.textMuted)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 52.dp),
                shape = FoldShapes.pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.saffron,
                    contentColor = palette.onSaffron,
                ),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
