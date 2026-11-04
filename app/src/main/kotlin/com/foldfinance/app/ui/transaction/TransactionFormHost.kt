package com.foldfinance.app.ui.transaction

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.foldfinance.app.R
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId

/**
 * Snackbar host of the shell: «Сохранено», «Операция удалена · Отменить». Provided by
 * `ShellScaffold`; screens outside the shell (tests) get a detached instance.
 */
val LocalAppSnackbar = compositionLocalOf { SnackbarHostState() }

/**
 * The form as a modal bottom sheet — the outer screen. Shown while [state] is open; dismissing
 * the sheet closes the form (and drops the draft, like «×»). The sheet is fully expanded so the
 * keypad is never half-hidden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormSheet(
    state: TransactionFormState,
    actions: TransactionFormActions,
    now: Instant,
    zone: ZoneId,
) {
    if (!state.isOpen) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = actions::close,
        sheetState = sheetState,
        shape = FoldShapes.sheet,
        containerColor = FoldFinanceTheme.palette.surface,
        // The form has its own «×»; without the drag handle the keypad fits the outer screen.
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        TransactionForm(
            state = state,
            actions = actions,
            now = now,
            zone = zone,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(top = 12.dp),
        )
    }
}

/** Shows «Сохранено» for every save of the form. */
@Composable
fun SavedSnackbarEffect(events: Flow<FormEvent>, snackbar: SnackbarHostState = LocalAppSnackbar.current) {
    val saved = stringResource(R.string.tx_saved)
    LaunchedEffect(events, snackbar) {
        events.collect { event ->
            if (event is FormEvent.Saved) snackbar.showSnackbar(saved)
        }
    }
}
