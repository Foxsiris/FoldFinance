package com.foldfinance.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Brand tokens of the «Obsidian» design system (docs/design.md, docs/design-v2/System.dc.html).
 *
 * Components read these through [FoldFinanceTheme.palette] when they need a role that Material's
 * [androidx.compose.material3.ColorScheme] has no exact slot for (income, review, categories,
 * panels). Everything else goes through `MaterialTheme.colorScheme`, which [FoldFinanceTheme]
 * fills from the same tokens.
 *
 * Dark is the primary theme. Light keeps the same structure — background, panel, card, field get
 * one step more prominent each, one lime accent, quiet neutrals — it is designed, not inverted.
 *
 * Accent comes in two roles because lime cannot be text on a light background:
 * - [accent] / [onAccent] — the lime *fill* of the main actions (FAB, «Сохранить», «Подтвердить»,
 *   selected bar), the same in both themes;
 * - [accentContent] / [onAccentContent] — accent as text and icons on surfaces (links, active
 *   labels): lime in dark, deep olive in light.
 *
 * @property ink window background («Фон»).
 * @property surface panels of the inner screen, navigation, list groups («Панель»).
 * @property card raised cards on a panel or the background: review card, tiles («Карточка»).
 * @property surfaceRaised fields, chips, segmented tracks, keys («Поле»).
 * @property surfaceSunken wells inside cards: the original SMS text.
 * @property outline borders of outlined chips and buttons («Линия»).
 * @property seam hairlines between rows and around wells.
 * @property navBar background of the bottom navigation bar.
 * @property text primary text; also the colour of expenses (expenses are the norm, not alarms).
 * @property textSoft secondary emphasis: chip labels, merchant on the review card, raw text.
 * @property textSecondary labels of numbers («Потрачено за месяц»).
 * @property textMuted captions, meta lines, axis labels («Подпись»).
 * @property textDisabled disabled text, the currency sign of big amounts.
 * @property income income amounts.
 * @property review «на проверку»: badges, the counter, the review dot.
 * @property onReview content on [review].
 * @property growth expenses growing compared to the previous period.
 * @property danger delete and errors.
 * @property refund refund amounts (a refund lowers expenses and is not income).
 * @property chartBar ordinary bars of charts; [chartEmpty] — days with nothing yet / future days.
 * @property categories ten category colours.
 */
@Immutable
data class FoldPalette(
    val isDark: Boolean,
    val ink: Color,
    val surface: Color,
    val card: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val outline: Color,
    val seam: Color,
    val navBar: Color,
    val text: Color,
    val textSoft: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDisabled: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContent: Color,
    val onAccentContent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val income: Color,
    val incomeContainer: Color,
    val review: Color,
    val onReview: Color,
    val reviewContainer: Color,
    val growth: Color,
    val danger: Color,
    val dangerContainer: Color,
    val refund: Color,
    val chartBar: Color,
    val chartEmpty: Color,
    val categories: CategoryPalette,
) {
    /** Soft accent tint for selected chips and rows (`accent` at 14 %). */
    val accentSoft: Color get() = accent.copy(alpha = if (isDark) 0.14f else 0.32f)

    /** Border of a selected chip (`accent` at 40 %). */
    val accentLine: Color get() = if (isDark) accent.copy(alpha = 0.4f) else accentContent.copy(alpha = 0.45f)

    /** Background of a selected list row. */
    val selection: Color get() = if (isDark) Color(0xFF1E1E24) else Color(0xFFEDEFE3)

    // ---- Names of the previous design system («Шов»), kept so that code outside `ui/` (the
    // widget, debug tools) and older screens keep compiling. They map onto the Obsidian roles.

    /** Accent as text/icons and its pair — the «primary» of the old palette. */
    val saffron: Color get() = accentContent
    val onSaffron: Color get() = onAccentContent
    val saffronContainer: Color get() = accentContainer
    val onSaffronContainer: Color get() = onAccentContainer
    val mint: Color get() = income
    val mintContainer: Color get() = incomeContainer
    val lavender: Color get() = refund
    val lavenderContainer: Color get() = surfaceRaised
    val alert: Color get() = danger
    val alertContainer: Color get() = dangerContainer
}

/**
 * Ten category colours, indexed in the order of `DefaultCategories.all`; user categories pick a
 * slot by [forKey]. Dark: the System board's swatches; light: the same hues, deep enough for
 * icons and dots on white.
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
            Color(0xFF9BDB6A), // groceries — «Продукты»
            Color(0xFF6AA8FF), // transport — «Транспорт»
            Color(0xFFFF7EB3), // entertainment — «Развлечения»
            Color(0xFFE8B07A), // shopping — «Покупки»
            Color(0xFFFF9B73), // restaurants — «Рестораны»
            Color(0xFF4FD1C5), // utilities — «Коммуналка»
            Color(0xFFB49BFF), // subscriptions — «Подписки»
            Color(0xFFF0EDE6), // health — «Здоровье»
            Color(0xFF9AA3B2), // transfers — «Переводы»
            Color(0xFF6E6E76), // other — «Другое»
        ),
    )

    val LightCategories = CategoryPalette(
        listOf(
            Color(0xFF4F8A1F),
            Color(0xFF2E6BD1),
            Color(0xFFC2407A),
            Color(0xFFA0662A),
            Color(0xFFC9572F),
            Color(0xFF13857B),
            Color(0xFF6D4FD0),
            Color(0xFF8A8172),
            Color(0xFF66707F),
            Color(0xFF8A8A92),
        ),
    )

    /**
     * Category colours of the previous design system. Stored category colours that equal one of
     * these are still palette slots ([com.foldfinance.app.ui.components.categoryPaletteSlotOf]),
     * so categories coloured before Obsidian pick up the new swatches.
     */
    val LegacyCategories: List<List<Color>> = listOf(
        listOf(
            Color(0xFF457F38), Color(0xFF007AA6), Color(0xFF98518A), Color(0xFFA9513C), Color(0xFF976200),
            Color(0xFF008478), Color(0xFF7F5BA6), Color(0xFFA54D6C), Color(0xFF5469B4), Color(0xFF617083),
        ),
        listOf(
            Color(0xFF8DC282), Color(0xFF63BDE7), Color(0xFFDB98CC), Color(0xFFEC9983), Color(0xFFD8A761),
            Color(0xFF55C7BA), Color(0xFFC0A0E7), Color(0xFFE895AE), Color(0xFF97AEF4), Color(0xFF9EACBE),
        ),
    )

    val Dark = FoldPalette(
        isDark = true,
        ink = Color(0xFF08080A),
        surface = Color(0xFF111114),
        card = Color(0xFF17171B),
        surfaceRaised = Color(0xFF1A1A1E),
        surfaceSunken = Color(0xFF0B0B0D),
        outline = Color(0xFF2A2A30),
        seam = Color(0xFF1E1E23),
        navBar = Color(0xFF0E0E10),
        text = Color(0xFFF5F5F3),
        textSoft = Color(0xFFD6D6DA),
        textSecondary = Color(0xFFA1A1A8),
        textMuted = Color(0xFF8A8A92),
        textDisabled = Color(0xFF6E6E76),
        accent = Color(0xFFD2F65A),
        onAccent = Color(0xFF0B0B0C),
        accentContent = Color(0xFFD2F65A),
        onAccentContent = Color(0xFF0B0B0C),
        accentContainer = Color(0xFF2C3317),
        onAccentContainer = Color(0xFFD2F65A),
        income = Color(0xFF7FE0A8),
        incomeContainer = Color(0xFF16221B),
        review = Color(0xFFFFB547),
        onReview = Color(0xFF0B0B0C),
        reviewContainer = Color(0xFF261D10),
        growth = Color(0xFFFFB38A),
        danger = Color(0xFFFF8A80),
        dangerContainer = Color(0xFF2A1514),
        refund = Color(0xFFD6D6DA),
        chartBar = Color(0xFF34343C),
        chartEmpty = Color(0xFF1E1E23),
        categories = DarkCategories,
    )

    val Light = FoldPalette(
        isDark = false,
        ink = Color(0xFFEEEEEA),
        surface = Color(0xFFF9F9F7),
        card = Color(0xFFFFFFFF),
        surfaceRaised = Color(0xFFE6E6E1),
        surfaceSunken = Color(0xFFF1F1EE),
        outline = Color(0xFFD3D3CC),
        seam = Color(0xFFE3E3DE),
        navBar = Color(0xFFF6F6F3),
        text = Color(0xFF111113),
        textSoft = Color(0xFF2E2E33),
        textSecondary = Color(0xFF4E4E55),
        textMuted = Color(0xFF62626A),
        textDisabled = Color(0xFF8E8E95),
        accent = Color(0xFFC8EE45),
        onAccent = Color(0xFF0B0B0C),
        accentContent = Color(0xFF4A6400),
        onAccentContent = Color(0xFFFFFFFF),
        accentContainer = Color(0xFFE6F5B0),
        onAccentContainer = Color(0xFF2F4000),
        income = Color(0xFF17804D),
        incomeContainer = Color(0xFFDDF1E4),
        review = Color(0xFFA05F00),
        onReview = Color(0xFFFFFFFF),
        reviewContainer = Color(0xFFF8E8CF),
        growth = Color(0xFFB4501E),
        danger = Color(0xFFC0362C),
        dangerContainer = Color(0xFFFBE1DE),
        refund = Color(0xFF4E4E55),
        chartBar = Color(0xFFCDCDC6),
        chartEmpty = Color(0xFFE3E3DE),
        categories = LightCategories,
    )
}

internal val LocalFoldPalette = staticCompositionLocalOf { PaletteTokens.Dark }
