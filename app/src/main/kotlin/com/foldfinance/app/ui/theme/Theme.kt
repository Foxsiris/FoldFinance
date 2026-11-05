package com.foldfinance.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * FoldFinance theme «Obsidian»: brand palette, Onest typography with tabular digits, hierarchy
 * shapes and reduced-motion awareness.
 *
 * @param darkTheme the primary, dark Obsidian theme; light is its designed analogue, not an inversion.
 * @param dynamicColor use wallpaper colours for Material roles (settings switch, off by default).
 *   Brand roles ([FoldFinanceTheme.palette]: accent fill, income, review, categories, panels) stay
 *   fixed so that money colours keep their meaning.
 * @param reducedMotion override for tests; by default follows the system animator scale.
 */
@Composable
fun FoldFinanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    reducedMotion: Boolean = rememberReducedMotion(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) PaletteTokens.Dark else PaletteTokens.Light
    val colorScheme = if (dynamicColor) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        palette.toColorScheme()
    }
    CompositionLocalProvider(
        LocalFoldPalette provides palette,
        LocalMoneyTypography provides MoneyTypography(),
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FoldTypography,
            shapes = FoldMaterialShapes,
            content = content,
        )
    }
}

/** Accessors for FoldFinance-specific tokens, mirroring `MaterialTheme.*`. */
object FoldFinanceTheme {
    val palette: FoldPalette
        @Composable @ReadOnlyComposable get() = LocalFoldPalette.current

    val money: MoneyTypography
        @Composable @ReadOnlyComposable get() = LocalMoneyTypography.current

    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}

/**
 * Full Material 3 colour scheme derived from the Obsidian palette. Surface container levels go
 * from the background to the field colour, so that Material components (menus, dialogs, sheets,
 * date pickers) sit on the same steps as our own panels and cards.
 *
 * `primary` is the accent *as content* ([FoldPalette.accentContent]): lime in dark, olive in light,
 * so text buttons, switches and focused fields stay readable in both themes. The lime fill of the
 * main actions is drawn explicitly with [FoldPalette.accent].
 */
fun FoldPalette.toColorScheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = accentContent,
        onPrimary = onAccentContent,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        inversePrimary = PaletteTokens.Light.accentContent,
        secondary = textSoft,
        onSecondary = ink,
        secondaryContainer = surfaceRaised,
        onSecondaryContainer = text,
        tertiary = income,
        onTertiary = onAccent,
        tertiaryContainer = incomeContainer,
        onTertiaryContainer = income,
        background = ink,
        onBackground = text,
        surface = ink,
        onSurface = text,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = textSecondary,
        surfaceTint = Color.Transparent,
        inverseSurface = text,
        inverseOnSurface = ink,
        error = danger,
        onError = onAccent,
        errorContainer = dangerContainer,
        onErrorContainer = danger,
        outline = outline,
        outlineVariant = seam,
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF222227),
        surfaceDim = ink,
        surfaceContainerLowest = ink,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = card,
        surfaceContainerHighest = surfaceRaised,
    )
} else {
    lightColorScheme(
        primary = accentContent,
        onPrimary = onAccentContent,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        inversePrimary = PaletteTokens.Dark.accentContent,
        secondary = textSoft,
        onSecondary = card,
        secondaryContainer = surfaceRaised,
        onSecondaryContainer = text,
        tertiary = income,
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = incomeContainer,
        onTertiaryContainer = income,
        background = ink,
        onBackground = text,
        surface = ink,
        onSurface = text,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = textSecondary,
        surfaceTint = Color.Transparent,
        inverseSurface = PaletteTokens.Dark.card,
        inverseOnSurface = PaletteTokens.Dark.text,
        error = danger,
        onError = Color(0xFFFFFFFF),
        errorContainer = dangerContainer,
        onErrorContainer = danger,
        outline = outline,
        outlineVariant = seam,
        scrim = Color(0xFF000000),
        surfaceBright = card,
        surfaceDim = Color(0xFFE2E2DD),
        surfaceContainerLowest = card,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = card,
        surfaceContainerHighest = surfaceRaised,
    )
}
