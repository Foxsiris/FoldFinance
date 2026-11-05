package com.foldfinance.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.backup.BackupError
import com.foldfinance.app.prefs.RelockTimeout
import com.foldfinance.app.prefs.ThemeMode
import com.foldfinance.app.security.DeviceAuthStatus
import com.foldfinance.app.ui.format.CardMaskFormat
import com.foldfinance.app.ui.components.EmptyState
import com.foldfinance.app.ui.components.ListGroup
import com.foldfinance.app.ui.components.ListGroupDivider
import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.TabularNumbers
import com.foldfinance.core.model.Card

// ---------------------------------------------------------------- Доступы и источники

@Composable
internal fun SourcesPane(state: SettingsUiState, actions: SettingsActions, ingestSection: @Composable () -> Unit) {
    ListGroup(Modifier.fillMaxWidth()) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_tracking_title),
            body = stringResource(R.string.settings_tracking_body),
            checked = state.autoTracking,
            onCheckedChange = actions::setAutoTracking,
        )
    }
    ingestSection()
}

// ---------------------------------------------------------------- Мои карты

@Composable
internal fun CardsPane(state: SettingsUiState, actions: SettingsActions) {
    if (state.cards.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.settings_cards_empty_title),
            body = stringResource(R.string.settings_cards_empty_body),
            icon = Icons.Outlined.CreditCard,
        )
        return
    }
    SettingsIntro(stringResource(R.string.settings_cards_intro))
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    ListGroup(Modifier.fillMaxWidth()) {
        state.cards.forEachIndexed { index, card ->
            if (index > 0) ListGroupDivider()
            CardRow(card, onEditLabel = { editing = card.mask }, onOwnChange = { actions.setOwnCard(card.mask, it) })
        }
    }
    val card = state.cards.firstOrNull { it.mask == editing }
    if (card != null) {
        val context = LocalContext.current
        val savedText = stringResource(R.string.settings_saved)
        CardLabelDialog(
            card = card,
            onDismiss = { editing = null },
            onSave = { label ->
                actions.setCardLabel(card.mask, label)
                editing = null
                Toast.makeText(context, savedText, Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun CardRow(card: Card, onEditLabel: () -> Unit, onOwnChange: (Boolean) -> Unit) {
    val palette = FoldFinanceTheme.palette
    val ownLabel = stringResource(R.string.settings_card_own)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onEditLabel)
                .heightIn(min = 64.dp)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIcon(Icons.Outlined.CreditCard)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    CardMaskFormat.format(card.mask),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = TabularNumbers),
                    color = palette.text,
                )
                Text(
                    card.label ?: stringResource(R.string.settings_card_no_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textMuted,
                )
            }
        }
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Switch(
                checked = card.isOwn,
                onCheckedChange = onOwnChange,
                modifier = Modifier.semantics { contentDescription = "$ownLabel ${CardMaskFormat.format(card.mask)}" },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = palette.onSaffron,
                    checkedTrackColor = palette.saffron,
                    uncheckedThumbColor = palette.textMuted,
                    uncheckedTrackColor = palette.surfaceRaised,
                    uncheckedBorderColor = palette.outline,
                ),
            )
            Text(ownLabel, style = MaterialTheme.typography.labelMedium, color = palette.textMuted)
        }
    }
}

@Composable
private fun CardLabelDialog(card: Card, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val field = rememberTextFieldState(card.label.orEmpty())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_card_edit_label, CardMaskFormat.format(card.mask))) },
        text = {
            OutlinedTextField(
                state = field,
                label = { Text(stringResource(R.string.settings_card_label_field)) },
                placeholder = { Text(stringResource(R.string.settings_card_label_hint)) },
                lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(field.text.toString()) }) {
                Text(stringResource(R.string.settings_save), color = FoldFinanceTheme.palette.saffron)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel), color = FoldFinanceTheme.palette.text)
            }
        },
        containerColor = FoldFinanceTheme.palette.surface,
    )
}

// ---------------------------------------------------------------- Валюта

/** Валюты на выбор (ISO 4217). Текущая валюта вне списка тоже показывается. */
internal val CurrencyChoices: List<String> = listOf("RUB", "USD", "EUR", "KZT", "BYN", "CNY", "TRY", "GBP")

@Composable
internal fun currencyName(code: String): String = when (code) {
    "RUB" -> stringResource(R.string.settings_currency_rub)
    "USD" -> stringResource(R.string.settings_currency_usd)
    "EUR" -> stringResource(R.string.settings_currency_eur)
    "KZT" -> stringResource(R.string.settings_currency_kzt)
    "BYN" -> stringResource(R.string.settings_currency_byn)
    "CNY" -> stringResource(R.string.settings_currency_cny)
    "TRY" -> stringResource(R.string.settings_currency_try)
    "GBP" -> stringResource(R.string.settings_currency_gbp)
    else -> code
}

@Composable
internal fun CurrencyPane(state: SettingsUiState, actions: SettingsActions) {
    SettingsIntro(stringResource(R.string.settings_currency_intro))
    val current = state.settings.defaultCurrency
    val choices = if (current in CurrencyChoices) CurrencyChoices else listOf(current) + CurrencyChoices
    ListGroup(Modifier.fillMaxWidth()) {
        choices.forEachIndexed { index, code ->
            if (index > 0) ListGroupDivider(startInset = 56.dp)
            SettingsRadioRow(
                title = currencyName(code),
                supporting = MoneyFormat.symbol(code),
                selected = code == current,
                onClick = { actions.setDefaultCurrency(code) },
            )
        }
    }
}

// ---------------------------------------------------------------- Безопасность

internal fun RelockTimeout.titleRes(): Int = when (this) {
    RelockTimeout.IMMEDIATELY -> R.string.settings_timeout_immediately
    RelockTimeout.ONE_MINUTE -> R.string.settings_timeout_1
    RelockTimeout.FIVE_MINUTES -> R.string.settings_timeout_5
    RelockTimeout.FIFTEEN_MINUTES -> R.string.settings_timeout_15
}

internal fun RelockTimeout.shortRes(): Int = when (this) {
    RelockTimeout.IMMEDIATELY -> R.string.settings_timeout_short_immediately
    RelockTimeout.ONE_MINUTE -> R.string.settings_timeout_short_1
    RelockTimeout.FIVE_MINUTES -> R.string.settings_timeout_short_5
    RelockTimeout.FIFTEEN_MINUTES -> R.string.settings_timeout_short_15
}

@Composable
internal fun SecurityPane(state: SettingsUiState, actions: SettingsActions) {
    val settings = state.settings
    ListGroup(Modifier.fillMaxWidth()) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_lock_title),
            body = stringResource(R.string.settings_lock_body),
            checked = settings.lockEnabled,
            onCheckedChange = actions::setLockEnabled,
        )
    }
    if (settings.lockEnabled && state.deviceAuth != DeviceAuthStatus.AVAILABLE) {
        SettingsWarning(
            text = stringResource(
                if (state.deviceAuth == DeviceAuthStatus.NOT_CONFIGURED) {
                    R.string.settings_lock_unprotected
                } else {
                    R.string.settings_lock_unavailable
                },
            ),
            actionLabel = stringResource(R.string.settings_lock_open_settings),
            onAction = actions::openSecuritySettings,
        )
    }
    Column {
        SettingsGroupTitle(stringResource(R.string.settings_timeout_title))
        ListGroup(Modifier.fillMaxWidth()) {
            RelockTimeout.entries.forEachIndexed { index, timeout ->
                if (index > 0) ListGroupDivider(startInset = 56.dp)
                SettingsRadioRow(
                    title = stringResource(timeout.titleRes()),
                    selected = settings.relockTimeout == timeout,
                    enabled = settings.lockEnabled,
                    onClick = { actions.setRelockTimeout(timeout) },
                )
            }
        }
    }
    ListGroup(Modifier.fillMaxWidth()) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_secure_title),
            body = stringResource(R.string.settings_secure_body),
            checked = settings.secureWindow,
            onCheckedChange = actions::setSecureWindow,
        )
        ListGroupDivider(startInset = 16.dp)
        SettingsSwitchRow(
            title = stringResource(R.string.settings_widget_hide_title),
            body = stringResource(R.string.settings_widget_hide_body),
            checked = settings.hideWidgetAmounts,
            onCheckedChange = actions::setHideWidgetAmounts,
        )
    }
}

// ---------------------------------------------------------------- Резервная копия

@Composable
internal fun BackupPane(state: SettingsUiState, actions: SettingsActions) {
    val working = state.backup is BackupStatus.Working
    SettingsIntro(stringResource(R.string.settings_backup_intro))
    ListGroup(Modifier.fillMaxWidth()) {
        SettingsActionRow(
            icon = Icons.Outlined.FileUpload,
            title = stringResource(R.string.settings_backup_export),
            body = stringResource(R.string.settings_backup_export_body),
            enabled = !working,
            onClick = actions::startExport,
        )
        ListGroupDivider()
        SettingsActionRow(
            icon = Icons.Outlined.FileDownload,
            title = stringResource(R.string.settings_backup_import),
            body = stringResource(R.string.settings_backup_import_body),
            enabled = !working,
            onClick = actions::startImport,
        )
    }
    BackupStatusLine(state.backup)
}

@Composable
private fun BackupStatusLine(status: BackupStatus) {
    val palette = FoldFinanceTheme.palette
    val live = Modifier
        .padding(horizontal = 4.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }
    when (status) {
        BackupStatus.Idle -> Unit
        is BackupStatus.Working -> Column(live) {
            Text(
                stringResource(
                    if (status.kind == BackupKind.EXPORT) R.string.settings_backup_exporting else R.string.settings_backup_importing,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted,
            )
            Spacer(Modifier.padding(top = 8.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = palette.saffron,
                trackColor = palette.surfaceRaised,
            )
        }
        is BackupStatus.Done -> Text(
            stringResource(
                if (status.kind == BackupKind.EXPORT) R.string.settings_backup_exported else R.string.settings_backup_imported,
                status.transactions,
            ),
            modifier = live,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.mint,
        )
        is BackupStatus.Failed -> Text(
            stringResource(status.error.messageRes()),
            modifier = live,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.alert,
        )
    }
}

internal fun BackupError.messageRes(): Int = when (this) {
    BackupError.WRONG_PASSWORD -> R.string.settings_backup_error_wrong_password
    BackupError.NOT_A_BACKUP -> R.string.settings_backup_error_not_backup
    BackupError.NEWER_VERSION -> R.string.settings_backup_error_newer
    BackupError.CORRUPTED -> R.string.settings_backup_error_corrupted
    BackupError.INVALID_CONTENT -> R.string.settings_backup_error_invalid
    BackupError.TOO_LARGE -> R.string.settings_backup_error_too_large
    BackupError.IO -> R.string.settings_backup_error_io
}

// ---------------------------------------------------------------- Оформление

internal fun ThemeMode.titleRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

@Composable
internal fun AppearancePane(state: SettingsUiState, actions: SettingsActions) {
    Column {
        SettingsGroupTitle(stringResource(R.string.settings_theme_title))
        ListGroup(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                if (index > 0) ListGroupDivider(startInset = 56.dp)
                SettingsRadioRow(
                    title = stringResource(mode.titleRes()),
                    selected = state.settings.themeMode == mode,
                    onClick = { actions.setThemeMode(mode) },
                )
            }
        }
    }
    ListGroup(Modifier.fillMaxWidth()) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_dynamic_title),
            body = stringResource(R.string.settings_dynamic_body),
            checked = state.settings.dynamicColor,
            onCheckedChange = actions::setDynamicColor,
        )
    }
}
