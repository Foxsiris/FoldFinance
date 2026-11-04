package com.foldfinance.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.foldfinance.app.ui.format.LocalClock
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.layout.LocalWindowReadings
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.robolectric.RuntimeEnvironment
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Fixed «now» for screenshots: 5 November 2026, 14:05 Moscow time. */
val ScreenshotClock: Clock = Clock.fixed(Instant.parse("2026-11-05T11:05:00Z"), ZoneId.of("Europe/Moscow"))

/** Where reference images live (`roborazzi.outputDir` in app/build.gradle.kts). */
const val SCREENSHOT_DIR = "src/test/screenshots"

/**
 * Captures [content] in every [DeviceConfigs] configuration × light/dark theme:
 * `src/test/screenshots/<name>_<outer|inner|tabletop>_<light|dark>.png`.
 *
 * Each capture sets Robolectric qualifiers for the window size, provides [LocalAppLayout] (with
 * the hinge — Robolectric cannot emulate one), [LocalWindowReadings], a fixed [LocalClock], the
 * theme with reduced motion (no in-flight animations), and an ink background.
 *
 * Adding a screen is one line inside a Robolectric test annotated like `ShellScreenshotTest`:
 * ```
 * @Test fun history() = captureInAllConfigs("history") { HistoryScreen(state = sampleHistory) }
 * ```
 * The receiver gives access to the current [DeviceConfig] when the content depends on it.
 */
fun captureInAllConfigs(
    name: String,
    configs: List<DeviceConfig> = DeviceConfigs.all,
    content: @Composable DeviceConfig.() -> Unit,
) {
    for (config in configs) {
        for (dark in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers(config.qualifiers(dark))
            val theme = if (dark) "dark" else "light"
            captureRoboImage("$SCREENSHOT_DIR/${name}_${config.id}_$theme.png") {
                ScreenshotHost(config, dark) { config.content() }
            }
        }
    }
}

/** Everything a screen expects from `MainActivity`, provided explicitly. */
@Composable
fun ScreenshotHost(config: DeviceConfig, dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppLayout provides config.layout,
        LocalWindowReadings provides config.readings,
        LocalClock provides ScreenshotClock,
    ) {
        FoldFinanceTheme(darkTheme = dark, reducedMotion = true) {
            CompositionLocalProvider(LocalContentColor provides FoldFinanceTheme.palette.text) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(FoldFinanceTheme.palette.ink),
                ) { content() }
            }
        }
    }
}
