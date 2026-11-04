package com.foldfinance.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.layout.LocalWindowReadings
import com.foldfinance.app.ui.layout.rememberWindowReadings
import com.foldfinance.app.ui.security.AppSecurityGate
import com.foldfinance.app.ui.shell.FoldFinanceAppUi
import com.foldfinance.app.ui.shell.ShellViewModel
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.transaction.TransactionFormViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity. Edge-to-edge: the UI draws behind the status bar, camera cutout and the
 * Samsung taskbar and pads itself with `WindowInsets.safeDrawing`; the keyboard resizes the window
 * (`adjustResize` in the manifest) and arrives as IME insets.
 *
 * Window size and folding features are read here once and provided as [LocalAppLayout] (and raw
 * [LocalWindowReadings] for the debug diagnostics screen). Orientation is not locked; all state
 * lives in ViewModels with SavedStateHandle and survives recreation.
 *
 * Intents ([AppIntents], from the widget and shortcuts) are handled in [onCreate] — only for a
 * fresh start, not when the activity is recreated with the intent it was started with — and in
 * [onNewIntent]. They go to the activity-scoped ViewModels the composition also uses.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val shell: ShellViewModel by viewModels()
    private val addForm: TransactionFormViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            AppSecurityGate { dark, dynamicColor -> AppContent(dark, dynamicColor) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (shell.handleIntentAction(intent?.action)) addForm.openAdd()
    }

    @Composable
    private fun AppContent(dark: Boolean, dynamicColor: Boolean) {
            DisposableEffect(dark) {
                // Transparent bars with icons matching the theme (the window background is ink/paper).
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            val readings = rememberWindowReadings(this)
            val layout = remember(readings) { readings.toAppLayout() }
            CompositionLocalProvider(
                LocalAppLayout provides layout,
                LocalWindowReadings provides readings,
            ) {
                FoldFinanceTheme(darkTheme = dark, dynamicColor = dynamicColor) {
                    FoldFinanceAppUi(viewModel = shell)
                }
            }
    }
}
