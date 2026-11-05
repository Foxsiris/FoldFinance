package com.foldfinance.app.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.layout.TwoPane
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes

/** Что показывает экран блокировки. */
sealed interface LockScreenMode {
    /** Ждём проверки. [message] — ошибка последней попытки, если была. */
    data class Locked(val message: String? = null) : LockScreenMode

    /** На телефоне нет ни биометрии, ни PIN: честно объясняем и пускаем без проверки. */
    data object NotConfigured : LockScreenMode

    /** Проверка временно недоступна. */
    data object Unavailable : LockScreenMode
}

/**
 * Экран блокировки в стиле приложения. Под ним ничего нет: содержимое приложения не
 * компонуется, пока экран показан (см. [AppSecurityGate]).
 *
 * Раскладка: на внешнем экране всё в нижней части (зона большого пальца); в книжной позе
 * текст слева от шарнира, кнопки справа; в tabletop — текст сверху, кнопки снизу.
 */
@Composable
fun LockScreen(
    mode: LockScreenMode,
    onUnlock: () -> Unit,
    onContinueUnprotected: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    modifier: Modifier = Modifier,
    layout: AppLayout = LocalAppLayout.current,
) {
    val palette = FoldFinanceTheme.palette
    val message: @Composable ColumnScope.() -> Unit = {
        when (mode) {
            is LockScreenMode.Locked -> LockMessage(
                icon = Icons.Outlined.Lock,
                tint = palette.saffron,
                title = stringResource(R.string.lock_title),
                body = stringResource(R.string.lock_body),
                error = mode.message,
            )
            LockScreenMode.NotConfigured -> LockMessage(
                icon = Icons.Outlined.LockOpen,
                tint = palette.alert,
                title = stringResource(R.string.lock_unprotected_title),
                body = stringResource(R.string.lock_unprotected_body),
            )
            LockScreenMode.Unavailable -> LockMessage(
                icon = Icons.Outlined.LockOpen,
                tint = palette.alert,
                title = stringResource(R.string.lock_unavailable_title),
                body = stringResource(R.string.lock_unavailable_body),
            )
        }
    }
    val actions: @Composable ColumnScope.() -> Unit = {
        when (mode) {
            is LockScreenMode.Locked -> PrimaryAction(stringResource(R.string.lock_unlock), onUnlock)
            LockScreenMode.NotConfigured, LockScreenMode.Unavailable -> {
                PrimaryAction(stringResource(R.string.lock_open_security_settings), onOpenSecuritySettings)
                Spacer(Modifier.height(12.dp))
                SecondaryAction(stringResource(R.string.lock_continue_unprotected), onContinueUnprotected)
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(palette.ink)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        when (layout) {
            is AppLayout.Compact -> BoxWithConstraints(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight)
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    message()
                    Spacer(Modifier.height(40.dp))
                    actions()
                }
                Box(Modifier.padding(horizontal = 24.dp, vertical = 32.dp)) { Wordmark() }
            }
            is AppLayout.Book, is AppLayout.Tabletop -> TwoPane(
                modifier = Modifier.fillMaxSize(),
                layout = layout,
                first = {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Wordmark()
                        Spacer(Modifier.weight(1f))
                        message()
                    }
                },
                second = {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = if (layout is AppLayout.Tabletop) Arrangement.Top else Arrangement.Bottom,
                    ) { actions() }
                },
            )
        }
    }
}

@Composable
private fun Wordmark() {
    Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.titleMedium,
        color = FoldFinanceTheme.palette.textMuted,
    )
}

@Composable
private fun LockMessage(icon: ImageVector, tint: Color, title: String, body: String, error: String? = null) {
    val palette = FoldFinanceTheme.palette
    Column(Modifier.widthIn(max = 420.dp)) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(palette.surfaceRaised, FoldShapes.listGroup),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
            color = palette.text,
        )
        Spacer(Modifier.height(8.dp))
        Text(text = body, style = MaterialTheme.typography.bodyLarge, color = palette.textMuted)
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = palette.alert,
            )
        }
    }
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    val palette = FoldFinanceTheme.palette
    Button(
        onClick = onClick,
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = FoldShapes.key,
        colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SecondaryAction(label: String, onClick: () -> Unit) {
    val palette = FoldFinanceTheme.palette
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = FoldShapes.pill,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.text),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
