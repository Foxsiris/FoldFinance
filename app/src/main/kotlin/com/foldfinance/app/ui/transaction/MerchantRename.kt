package com.foldfinance.app.ui.transaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldfinance.app.R
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.core.data.repository.MerchantAliasRepository
import com.foldfinance.core.data.repository.MerchantAliasRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Renaming a merchant for display (`merchant_alias.user_name`). The raw name in the operations is
 * untouched: search and category rules keep working on it.
 */
@HiltViewModel
class MerchantRenameViewModel @Inject constructor(
    private val aliases: MerchantAliasRepository,
) : ViewModel() {
    /** [displayName] `null` or blank — back to the model's name or the raw one. */
    fun rename(merchant: String, displayName: String?) {
        viewModelScope.launch { aliases.rename(merchant, displayName?.trim()?.ifEmpty { null }) }
    }
}

/**
 * «Имя продавца»: the name shown everywhere instead of [raw]. «Исходное имя» drops the user's name.
 * The typed text is saveable, so the dialog survives folding.
 */
@Composable
fun MerchantRenameDialog(
    raw: String,
    current: String,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = FoldFinanceTheme.palette
    var text by rememberSaveable(raw) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        title = { Text(stringResource(R.string.tx_merchant_rename_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.tx_merchant_rename_body, raw),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MerchantAliasRepositoryImpl.MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.tx_merchant_rename_field)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim().ifEmpty { null }) }) {
                Text(stringResource(R.string.tx_merchant_rename_save), color = palette.accentContent)
            }
        },
        dismissButton = {
            TextButton(onClick = { onSave(null) }) {
                Text(stringResource(R.string.tx_merchant_rename_reset), color = palette.textSecondary)
            }
        },
    )
}
