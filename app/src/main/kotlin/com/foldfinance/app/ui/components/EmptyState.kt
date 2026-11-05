package com.foldfinance.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes

/**
 * Empty state as an invitation to act (docs/design.md, «Принципы»): what is missing, why, and —
 * when there is a real action — one lime button for it. Never filled with fake data.
 *
 * Width capped at 420 dp for a comfortable line length.
 *
 * @param title what is missing: «Операций пока нет».
 * @param body how to get there, one or two sentences.
 * @param icon optional pictogram in a soft lime circle.
 * @param actionLabel verb of the action («Разрешить», «Добавить»). Shown only together with
 *   [onAction]; omit both when the action does not exist yet — no placeholder buttons.
 * @param centered centred column («Всё разобрано» on the review card); left-aligned by default.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    centered: Boolean = false,
) {
    val palette = FoldFinanceTheme.palette
    Column(
        modifier = modifier
            .widthIn(max = 420.dp)
            .padding(vertical = 8.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(palette.accentSoft, FoldShapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = palette.accentContent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            color = palette.text,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textMuted,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton(text = actionLabel, onClick = onAction, height = 52.dp)
        }
    }
}
