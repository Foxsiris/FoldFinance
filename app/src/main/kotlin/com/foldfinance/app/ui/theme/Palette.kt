package com.foldfinance.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Brand tokens of the "Seam" design system (docs/design.md, section «Цвет»).
 *
 * Components read these through [FoldFinanceTheme.palette] when they need a brand role that
 * Material's [androidx.compose.material3.ColorScheme] has no exact slot for (income, refund,
 * category colors, the seam). Everything else goes through `MaterialTheme.colorScheme`,
 * which [FoldFinanceTheme] fills from the same tokens.
 *
 * The dark palette is designed on its own (ink-blue paper at night), not inverted from light.
 * Every text/background pair used by components keeps a contrast ratio of at least 4.5:1.
 *
 * @property ink window background.
 * @property surface cards and panes.
 * @property surfaceRaised list groups, fields, segmented tracks.
 * @property text primary text; also the colour of expenses (expenses are the norm, not alarms).
 * @property textMuted secondary text, kopecks, currency sign (≥ 4.5:1 on [surface]).
 * @property saffron primary actions, active selection, «на проверку».
 * @property onSaffron content on [saffron].
 * @property saffronContainer soft fill for selected states.
 * @property onSaffronContainer content on [saffronContainer].
 * @property mint income.
 * @property lavender refunds.
 * @property alert errors.
 * @property seam dots of the hinge perforation and hairlines (`outlineVariant`).
 * @property categories ten category colours of equal lightness.
 */
@Immutable
data class FoldPalette(
    val isDark: Boolean,
    val ink: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val text: Color,
    val textMuted: Color,
    val saffron: Color,
    val onSaffron: Color,
    val saffronContainer: Color,
    val onSaffronContainer: Color,
    val mint: Color,
    val mintContainer: Color,
    val lavender: Color,
    val lavenderContainer: Color,
    val alert: Color,
    val alertContainer: Color,
    val outline: Color,
    val seam: Color,
    val categories: CategoryPalette,
)

/**
 * Ten category colours: medium saturation, equal perceived lightness (OKLCH L≈0.75 in dark,
 * L≈0.54 in light), so no category shouts louder than another. Indexed in the order of
 * `DefaultCategories.all`; user categories pick a slot by [forKey].
 */
@Immutable
class CategoryPalette(val colors: List<Color>) {
    init {
        require(colors.size == SIZE) { "CategoryPalette needs exactly $SIZE colours" }
    }

    operator fun get(index: Int): Color = colors[Math.floorMod(index, SIZE)]

    /**
     * Colour for a category key. Base categories get their designed slot; any other key gets
     * a stable slot derived from its hash, so a user category keeps its colour between launches.
     */
    fun forKey(key: String): Color {
        val slot = BASE_KEYS.indexOf(key)
        return if (slot >= 0) colors[slot] else get(key.hashCode())
    }

    override fun equals(other: Any?): Boolean = other is CategoryPalette && other.colors == colors
    override fun hashCode(): Int = colors.hashCode()

    companion object {
        const val SIZE = 10

        /** Same order as `com.foldfinance.core.model.DefaultCategories.all`. */
        val BASE_KEYS = listOf(
            "groceries", "transport", "entertainment", "shopping", "restaurants",
            "utilities", "subscriptions", "health", "transfers", "other",
        )
    }
}

/** Raw token values. Kept in one place so the design doc and the code can be diffed by eye. */
object PaletteTokens {
    val DarkCategories = CategoryPalette(
        listOf(
            Color(0xFF8DC282), // groceries — leaf
            Color(0xFF63BDE7), // transport — sky
            Color(0xFFDB98CC), // entertainment — orchid
            Color(0xFFEC9983), // shopping — coral
            Color(0xFFD8A761), // restaurants — amber
            Color(0xFF55C7BA), // utilities — teal
            Color(0xFFC0A0E7), // subscriptions — violet
            Color(0xFFE895AE), // health — rose
            Color(0xFF97AEF4), // transfers — periwinkle
            Color(0xFF9EACBE), // other — slate
        ),
    )

    val LightCategories = CategoryPalette(
        listOf(
            Color(0xFF457F38),
            Color(0xFF007AA6),
            Color(0xFF98518A),
            Color(0xFFA9513C),
            Color(0xFF976200),
            Color(0xFF008478),
            Color(0xFF7F5BA6),
            Color(0xFFA54D6C),
            Color(0xFF5469B4),
            Color(0xFF617083),
        ),
    )

    val Dark = FoldPalette(
        isDark = true,
        ink = Color(0xFF0E1626),
        surface = Color(0xFF16213A),
        surfaceRaised = Color(0xFF1E2B47),
        surfaceSunken = Color(0xFF0A101D),
        text = Color(0xFFE9EEF7),
        textMuted = Color(0xFFA3AEC4),
        saffron = Color(0xFFF2C14E),
        onSaffron = Color(0xFF2B1F00),
        saffronContainer = Color(0xFF3D3218),
        onSaffronContainer = Color(0xFFFFE3A3),
        mint = Color(0xFF5FD3A4),
        mintContainer = Color(0xFF173B33),
        lavender = Color(0xFFA9B4FF),
        lavenderContainer = Color(0xFF262F5C),
        alert = Color(0xFFFF7A70),
        alertContainer = Color(0xFF4A1F22),
        outline = Color(0xFF4A5878),
        seam = Color(0xFF34425F),
        categories = DarkCategories,
    )

    val Light = FoldPalette(
        isDark = false,
        ink = Color(0xFFF3F5F9),
        surface = Color(0xFFFFFFFF),
        surfaceRaised = Color(0xFFE9EEF7),
        surfaceSunken = Color(0xFFE3E8F1),
        text = Color(0xFF14213A),
        textMuted = Color(0xFF5A6478),
        saffron = Color(0xFF8A5F00),
        onSaffron = Color(0xFFFFFFFF),
        saffronContainer = Color(0xFFFBE7B8),
        onSaffronContainer = Color(0xFF3A2800),
        mint = Color(0xFF0F7B57),
        mintContainer = Color(0xFFD3F1E4),
        lavender = Color(0xFF4655C8),
        lavenderContainer = Color(0xFFE1E5FF),
        alert = Color(0xFFB3261E),
        alertContainer = Color(0xFFFADCD9),
        outline = Color(0xFF8C96AA),
        seam = Color(0xFFC3CCDA),
        categories = LightCategories,
    )
}

internal val LocalFoldPalette = staticCompositionLocalOf { PaletteTokens.Light }
