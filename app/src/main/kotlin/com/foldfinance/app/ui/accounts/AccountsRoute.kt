package com.foldfinance.app.ui.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.Panel
import com.foldfinance.app.ui.format.CardMaskFormat
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.layout.TwoPane
import com.foldfinance.app.ui.shell.LocalShellNavigation
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes

/** Callbacks of [AccountsScreen]. The default implementation does nothing (screenshots, previews). */
interface AccountsActions {
    fun select(mask: String?) {}
    fun previousMonth() {}
    fun nextMonth() {}
    fun setOwn(mask: String, own: Boolean) {}
    fun startRename(mask: String) {}
    fun cancelRename() {}
    fun rename(mask: String, label: String) {}

    /** Open an operation of the card in «История». */
    fun openTransaction(transactionId: Long) {}

    companion object {
        val None: AccountsActions = object : AccountsActions {}
    }
}

/**
 * «Счета и карты» — a screen opened from the balances on «Обзор» and from «Мои карты» in
 * «Настройки» (not a section of its own: the rail and the bar keep their six / five items).
 *
 * [onBack] leaves the screen; on the outer screen «Назад» first closes an open card.
 */
@Composable
fun AccountsRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shell = LocalShellNavigation.current
    val actions = remember(viewModel, shell) {
        object : AccountsActions {
            override fun select(mask: String?) = viewModel.select(mask)
            override fun previousMonth() = viewModel.showPreviousMonth()
            override fun nextMonth() = viewModel.showNextMonth()
            override fun setOwn(mask: String, own: Boolean) = viewModel.setOwn(mask, own)
            override fun startRename(mask: String) = viewModel.startRename(mask)
            override fun cancelRename() = viewModel.cancelRename()
            override fun rename(mask: String, label: String) = viewModel.rename(mask, label)
            override fun openTransaction(transactionId: Long) = shell.openTransaction(transactionId)
        }
    }
    AccountsScreen(
        state = state,
        actions = actions,
        modifier = modifier,
        onBack = {
            // Next time the screen opens on the list, not on the card opened last time.
            viewModel.select(null)
            onBack()
        },
    )
}

/**
 * Stateless «Счета и карты».
 *
 * - Compact (outer screen): the list; a card opens over it, «Назад» returns to the list.
 * - Book: the list on the left, the card on the right, split on the hinge ([TwoPane]); without an
 *   explicit choice the first card is shown.
 * - Tabletop: known balances on top (to look at); the month, the list or the opened card at the
 *   bottom (to touch).
 */
@Composable
fun AccountsScreen(
    state: AccountsUiState,
    actions: AccountsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    layout: AppLayout = LocalAppLayout.current,
) {
    if (state.loading) {
        Box(modifier.fillMaxSize())
        return
    }
    val cardOpen = state.selectedMask != null && state.detail != null
    BackHandler {
        if (cardOpen && layout !is AppLayout.Book) actions.select(null) else onBack()
    }
    state.renaming?.let { mask ->
        val card = state.accounts.firstOrNull { it.mask == mask }?.card
        RenameDialog(
            mask = mask,
            initial = card?.label.orEmpty(),
            onDismiss = actions::cancelRename,
            onSave = { actions.rename(mask, it) },
        )
    }
    when (layout) {
        is AppLayout.Compact -> if (cardOpen) {
            AccountDetailColumn(state, state.detail!!, actions, onBack = { actions.select(null) }, modifier = modifier.fillMaxSize())
        } else {
            AccountsListColumn(state, actions, onBack = onBack, twoPane = false, modifier = modifier.fillMaxSize())
        }
        is AppLayout.Book -> TwoPane(
            modifier = modifier.fillMaxSize().padding(top = 16.dp, bottom = 16.dp, end = 8.dp),
            layout = layout,
            first = {
                Panel(Modifier.fillMaxSize()) {
                    AccountsListColumn(state, actions, onBack = onBack, twoPane = true, modifier = Modifier.fillMaxSize())
                }
            },
            second = {
                Panel(Modifier.fillMaxSize()) {
                    val detail = state.detail
                    if (detail != null) {
                        AccountDetailColumn(state, detail, actions, onBack = null, modifier = Modifier.fillMaxSize())
                    }
                }
            },
        )
        is AppLayout.Tabletop -> TwoPane(
            modifier = modifier.fillMaxSize(),
            layout = layout,
            first = {
                if (cardOpen) {
                    AccountTabletopTop(state, state.detail!!, onBack = { actions.select(null) }, modifier = Modifier.fillMaxSize())
                } else {
                    BalancesTabletopTop(state, actions, onBack = onBack, modifier = Modifier.fillMaxSize())
                }
            },
            second = {
                Panel(Modifier.fillMaxSize(), shape = FoldShapes.sheet) {
                    if (cardOpen) {
                        AccountDetailColumn(state, state.detail!!, actions, onBack = null, modifier = Modifier.fillMaxSize(), tabletopBottom = true)
                    } else {
                        AccountsListColumn(
                            state, actions, onBack = null, twoPane = true, modifier = Modifier.fillMaxSize(),
                            highlightSelected = false, showTitle = false, showMovements = false,
                        )
                    }
                }
            },
        )
    }
}

/** Label of the card; the text field keeps unsaved input across folding (saveable state). */
@Composable
private fun RenameDialog(mask: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val palette = FoldFinanceTheme.palette
    val field = rememberTextFieldState(initial)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accounts_rename_title, CardMaskFormat.format(mask))) },
        text = {
            OutlinedTextField(
                state = field,
                label = { Text(stringResource(R.string.accounts_rename_field)) },
                placeholder = { Text(stringResource(R.string.accounts_rename_hint)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(field.text.toString()) }) {
                Text(stringResource(R.string.accounts_rename_save), color = palette.accentContent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.accounts_rename_cancel), color = palette.text)
            }
        },
        containerColor = palette.surface,
    )
}
