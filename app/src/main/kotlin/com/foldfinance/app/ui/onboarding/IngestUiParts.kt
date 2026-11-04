package com.foldfinance.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ingest.push.InstalledApp
import com.foldfinance.app.ui.components.ListGroup
import com.foldfinance.app.ui.components.ListGroupDivider
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes

/** Saffron pill, full width, 52 dp — the one main action of a step. */
@Composable
internal fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val palette = FoldFinanceTheme.palette
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = FoldShapes.pill,
        colors = ButtonDefaults.buttonColors(containerColor = palette.saffron, contentColor = palette.onSaffron),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Text action in saffron, full width, 48 dp. */
@Composable
internal fun SecondaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = FoldShapes.pill,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = FoldFinanceTheme.palette.saffron)
    }
}

/** «Пропустить» — muted, always available. */
@Composable
internal fun SkipAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = FoldShapes.pill,
    ) {
        Text(stringResource(R.string.onboarding_skip), style = MaterialTheme.typography.labelLarge, color = FoldFinanceTheme.palette.textMuted)
    }
}

/** Granted / not granted line: mint check or saffron exclamation, never red for a missing permission. */
@Composable
internal fun StatusLine(ok: Boolean, text: String, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    Row(modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (ok) palette.mint else palette.saffron,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = palette.text)
    }
}

/** Numbered instructions in a [ListGroup]. */
@Composable
internal fun InstructionList(steps: List<String>, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    ListGroup(modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            if (index > 0) ListGroupDivider(startInset = 56.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // A single hint needs no number.
                if (steps.size > 1) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(palette.saffronContainer, FoldShapes.pill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text((index + 1).toString(), style = MaterialTheme.typography.labelLarge, color = palette.onSaffronContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Text(step, style = MaterialTheme.typography.bodyLarge, color = palette.text, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Soft icon tile like the category tiles: 40 dp, 12 dp corners. */
@Composable
internal fun IconTile(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    val alpha = if (FoldFinanceTheme.palette.isDark) 0.18f else 0.14f
    Box(
        modifier
            .size(40.dp)
            .background(tint.copy(alpha = alpha), FoldShapes.tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** App launcher icon, or a letter tile when the icon is not available. */
@Composable
internal fun AppIcon(app: InstalledApp, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    val bitmap = app.icon
    if (bitmap != null) {
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        Image(image, contentDescription = null, modifier = modifier.size(40.dp))
    } else {
        val color = palette.categories.forKey(app.packageName)
        Box(
            modifier
                .size(40.dp)
                .background(color.copy(alpha = if (palette.isDark) 0.18f else 0.14f), FoldShapes.tile),
            contentAlignment = Alignment.Center,
        ) {
            Text(app.label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = color)
        }
    }
}

/**
 * Settings-style row: icon tile, title + supporting line, trailing content (status action).
 * Clickable when [onClick] is set.
 */
@Composable
internal fun IngestRow(
    icon: ImageVector,
    title: String,
    supporting: String?,
    modifier: Modifier = Modifier,
    tint: Color = FoldFinanceTheme.palette.saffron,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val palette = FoldFinanceTheme.palette
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickable)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        IconTile(icon, tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = palette.text)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
            }
        }
        trailing()
    }
}

/** Compact trailing text action of an [IngestRow]. */
@Composable
internal fun RowAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp), shape = FoldShapes.pill) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = FoldFinanceTheme.palette.saffron)
    }
}
