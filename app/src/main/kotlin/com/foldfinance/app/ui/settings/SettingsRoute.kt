package com.foldfinance.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DevicesFold
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.foldfinance.app.BuildConfig
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.CategoryTile
import com.foldfinance.app.ui.components.CategoryVisual
import com.foldfinance.app.ui.components.ListGroup
import com.foldfinance.app.ui.diagnostics.WindowDiagnosticsRoute
import com.foldfinance.app.ui.shell.DebugSectionPlaceholder
import com.foldfinance.app.ui.shell.Section
import com.foldfinance.app.ui.theme.FoldFinanceTheme

/**
 * «Настройки» — entry point of the section. Not ready yet (hidden from release navigation);
 * in debug builds it hosts debug tools, currently «Диагностика окна».
 */
@Composable
fun SettingsRoute(modifier: Modifier = Modifier) {
    if (BuildConfig.DEBUG) {
        DebugSettings(modifier)
    } else {
        DebugSectionPlaceholder(Section.Settings, modifier)
    }
}

@Composable
private fun DebugSettings(modifier: Modifier) {
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    if (showDiagnostics) {
        BackHandler { showDiagnostics = false }
        WindowDiagnosticsRoute(modifier, onBack = { showDiagnostics = false })
    } else {
        DebugSectionPlaceholder(Section.Settings, modifier) {
            ListGroup(Modifier.fillMaxWidth()) {
                DebugToolRow(
                    title = stringResource(R.string.diagnostics_entry_title),
                    body = stringResource(R.string.diagnostics_entry_body),
                    onClick = { showDiagnostics = true },
                )
            }
        }
    }
}

@Composable
private fun DebugToolRow(title: String, body: String, onClick: () -> Unit) {
    val palette = FoldFinanceTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(CategoryVisual(title, palette.saffron, Icons.Outlined.DevicesFold))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = palette.text)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = palette.textMuted)
    }
}
