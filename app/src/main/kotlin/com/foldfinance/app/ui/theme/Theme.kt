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
 * FoldFinance theme: brand palette («Шов»), Onest/Unbounded typography, hierarchy shapes and
 * reduced-motion awareness.
 *
 * @param darkTheme dark ink theme; a fully designed palette, not an inversion of light.
 * @param dynamicColor use wallpaper colours for Material roles (settings switch, off by default).
 *   Brand roles ([FoldFinanceTheme.palette]: income, refund, categories, seam) stay fixed so that
 *   money colours keep their meaning.
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
 * Full Material 3 colour scheme derived from the brand palette. Surface container levels go from
 * sunken to raised so that Material components (navigation bar, menus, dialogs) sit naturally
 * between `ink` and `surfaceRaised`.
 */
fun FoldPalette.toColorScheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = saffron,
        onPrimary = onSaffron,
        primaryContainer = saffronContainer,
        onPrimaryContainer = onSaffronContainer,
        inversePrimary = PaletteTokens.Light.saffron,
        secondary = Color(0xFFD9C08A),
        onSecondary = onSaffron,
        secondaryContainer = saffronContainer,
        onSecondaryContainer = onSaffronContainer,
        tertiary = mint,
        onTertiary = Color(0xFF00382A),
        tertiaryContainer = mintContainer,
        onTertiaryContainer = Color(0xFFB7F2D9),
        background = ink,
        onBackground = text,
        surface = ink,
        onSurface = text,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = textMuted,
        surfaceTint = saffron,
        inverseSurface = text,
        inverseOnSurface = ink,
        error = alert,
        onError = Color(0xFF450A08),
        errorContainer = alertContainer,
        onErrorContainer = Color(0xFFFFDAD6),
        outline = outline,
        outlineVariant = seam,
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF2A3858),
        surfaceDim = ink,
        surfaceContainerLowest = surfaceSunken,
        surfaceContainerLow = Color(0xFF121C31),
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = Color(0xFF263453),
    )
} else {
    lightColorScheme(
        primary = saffron,
        onPrimary = onSaffron,
        primaryContainer = saffronContainer,
        onPrimaryContainer = onSaffronContainer,
        inversePrimary = PaletteTokens.Dark.saffron,
        secondary = Color(0xFF6B5A33),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = saffronContainer,
        onSecondaryContainer = onSaffronContainer,
        tertiary = mint,
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = mintContainer,
        onTertiaryContainer = Color(0xFF00382A),
        background = ink,
        onBackground = text,
        surface = ink,
        onSurface = text,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = textMuted,
        surfaceTint = saffron,
        inverseSurface = Color(0xFF16213A),
        inverseOnSurface = Color(0xFFE9EEF7),
        error = alert,
        onError = Color(0xFFFFFFFF),
        errorContainer = alertContainer,
        onErrorContainer = Color(0xFF410E0B),
        outline = outline,
        outlineVariant = seam,
        scrim = Color(0xFF000000),
        surfaceBright = surface,
        surfaceDim = Color(0xFFDCE1EA),
        surfaceContainerLowest = surface,
        surfaceContainerLow = Color(0xFFF8F9FC),
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = surfaceSunken,
    )
}
