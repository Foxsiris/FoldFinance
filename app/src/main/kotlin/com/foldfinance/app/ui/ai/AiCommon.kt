package com.foldfinance.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes

/**
 * Card of an AI element: the Obsidian `card` colour of the tiles (it reads on the window background
 * of the outer screen and inside a `surface` panel of the inner one), list-group corners.
 */
@Composable
fun AiCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, shape = FoldShapes.listGroup, color = FoldFinanceTheme.palette.card) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                trailing?.invoke()
            }
            content()
        }
    }
}

/**
 * Progress of model work. Determinate when [progress] is known; otherwise an endless bar —
 * except with reduced motion (system setting, screenshots), where the text next to it is enough.
 */
@Composable
fun AiProgress(modifier: Modifier = Modifier, progress: Float? = null) {
    when {
        progress != null -> LinearProgressIndicator(progress = { progress }, modifier = modifier)
        FoldFinanceTheme.reducedMotion -> Unit
        else -> LinearProgressIndicator(modifier = modifier)
    }
}

/** Small print under model text: the numbers are the app's, the words are the model's. */
@Composable
fun AiFootnote(modelText: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(if (modelText) R.string.aif_footnote_model else R.string.aif_footnote_template),
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
