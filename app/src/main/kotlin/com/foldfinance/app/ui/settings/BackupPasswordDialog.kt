package com.foldfinance.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.settings.SettingsViewModel.Companion.MIN_PASSWORD_LENGTH
import com.foldfinance.app.ui.theme.FoldFinanceTheme

/**
 * Пароль бэкапа. Поля — `SecureTextField` (символы скрыты, нет копирования) на `TextFieldState`
 * без сохранения в Bundle (`remember`, не `rememberSaveable`): пароль не переживает
 * пересоздание Activity и не попадает в saved state. В [onConfirm] уходит `CharArray`, поля
 * очищаются; получатель затирает массив.
 *
 * Экспорт: длина ≥ [MIN_PASSWORD_LENGTH] и повтор. Импорт: одно поле и предупреждение о замене.
 */
@Composable
internal fun BackupPasswordDialog(kind: BackupKind, onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    val password = remember { TextFieldState() }
    val repeat = remember { TextFieldState() }
    DisposableEffect(Unit) {
        onDispose {
            password.clearText()
            repeat.clearText()
        }
    }
    val export = kind == BackupKind.EXPORT
    val length = password.text.length
    val tooShort = export && length in 1 until MIN_PASSWORD_LENGTH
    val mismatch = export && repeat.text.isNotEmpty() && !contentEquals(password.text, repeat.text)
    val valid = if (export) length >= MIN_PASSWORD_LENGTH && contentEquals(password.text, repeat.text) else length > 0
    val palette = FoldFinanceTheme.palette

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (export) R.string.settings_backup_password_title_export else R.string.settings_backup_password_title_import,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (export) {
                        stringResource(R.string.settings_backup_password_body_export, MIN_PASSWORD_LENGTH)
                    } else {
                        stringResource(R.string.settings_backup_password_body_import)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textMuted,
                )
                OutlinedSecureTextField(
                    state = password,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_backup_password)) },
                    isError = tooShort,
                    supportingText = if (tooShort) {
                        { Text(stringResource(R.string.settings_backup_password_short, MIN_PASSWORD_LENGTH)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (export) ImeAction.Next else ImeAction.Done,
                    ),
                )
                if (export) {
                    OutlinedSecureTextField(
                        state = repeat,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_backup_password_repeat)) },
                        isError = mismatch,
                        supportingText = if (mismatch) {
                            { Text(stringResource(R.string.settings_backup_password_mismatch)) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val chars = password.text.copyChars()
                    password.clearText()
                    repeat.clearText()
                    onConfirm(chars)
                },
            ) {
                Text(
                    stringResource(if (export) R.string.settings_backup_do_export else R.string.settings_backup_do_import),
                    color = if (valid) palette.saffron else palette.textMuted,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel), color = palette.text)
            }
        },
        containerColor = palette.surface,
    )
}

/** Сравнение без создания `String` из пароля. */
private fun contentEquals(a: CharSequence, b: CharSequence): Boolean {
    if (a.length != b.length) return false
    for (i in a.indices) if (a[i] != b[i]) return false
    return true
}

/** Копия символов без промежуточной `String`. */
private fun CharSequence.copyChars(): CharArray = CharArray(length) { this[it] }
