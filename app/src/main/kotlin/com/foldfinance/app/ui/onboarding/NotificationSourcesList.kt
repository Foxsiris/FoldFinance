package com.foldfinance.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ingest.push.InstalledApp
import com.foldfinance.app.ui.components.EmptyState
import com.foldfinance.app.ui.components.ListGroupDivider
import com.foldfinance.app.ui.components.SectionHeader
import com.foldfinance.app.ui.theme.FoldFinanceTheme

/**
 * Checkbox list of installed apps for the notification whitelist: known banks on top
 * ([InstalledApp.isKnownBank]), then all other apps. Rows look like a list group (20 dp corners on
 * the group, square rows inside), 64 dp touch targets.
 */
@Composable
fun NotificationSourcesList(
    sources: SourcesState,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val apps = sources.apps
    when {
        apps == null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = FoldFinanceTheme.palette.saffron)
                Text(
                    stringResource(R.string.onboarding_sources_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FoldFinanceTheme.palette.textMuted,
                )
            }
        }
        apps.isEmpty() -> EmptyState(
            title = stringResource(R.string.onboarding_sources_empty_title),
            body = stringResource(R.string.onboarding_sources_empty_body),
            icon = Icons.Outlined.NotificationsNone,
            modifier = modifier,
        )
        else -> {
            val banks = apps.filter { it.isKnownBank }
            val others = apps.filterNot { it.isKnownBank }
            val banksTitle = stringResource(R.string.onboarding_sources_banks)
            val othersTitle = stringResource(R.string.onboarding_sources_other)
            LazyColumn(modifier = modifier, contentPadding = contentPadding) {
                group(banksTitle, banks, sources.selected, onToggle)
                group(othersTitle, others, sources.selected, onToggle)
            }
        }
    }
}

private fun LazyListScope.group(
    title: String,
    apps: List<InstalledApp>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    if (apps.isEmpty()) return
    item(key = "header:$title") {
        SectionHeader(title = title, modifier = Modifier.padding(top = 8.dp))
    }
    items(apps, key = { it.packageName }) { app ->
        val first = app == apps.first()
        val last = app == apps.last()
        Column {
            if (!first) {
                Box(Modifier.background(FoldFinanceTheme.palette.surface)) { ListGroupDivider() }
            }
            SourceRow(app, app.packageName in selected, onToggle, shapeFor(first, last))
        }
    }
}

private fun shapeFor(first: Boolean, last: Boolean): Shape {
    val r = 20.dp
    return when {
        first && last -> RoundedCornerShape(r)
        first -> RoundedCornerShape(topStart = r, topEnd = r)
        last -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
        else -> RectangleShape
    }
}

@Composable
private fun SourceRow(app: InstalledApp, checked: Boolean, onToggle: (String) -> Unit, shape: Shape) {
    val palette = FoldFinanceTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.surface, shape)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(app.packageName) })
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, color = palette.text, maxLines = 1)
            if (app.isKnownBank) {
                Text(stringResource(R.string.onboarding_sources_known_bank), style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
            }
        }
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = palette.saffron,
                checkmarkColor = palette.onSaffron,
                uncheckedColor = palette.textMuted,
            ),
        )
    }
}
