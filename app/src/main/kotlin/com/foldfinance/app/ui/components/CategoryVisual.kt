package com.foldfinance.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Work
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import com.foldfinance.app.ui.theme.CategoryPalette
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.PaletteTokens
import com.foldfinance.core.model.Category

/**
 * What the UI needs to draw a category: name, colour, icon. Build it with [categoryVisual].
 */
@Immutable
data class CategoryVisual(val name: String, val color: Color, val icon: ImageVector)

/**
 * Icons for `Category.iconKey` (Material Symbols names). The first ten are the keys used by
 * `DefaultCategories`; together with the rest they form the set a user picks from on the
 * categories screen, in picker order.
 */
object CategoryIcons {
    private val byKey: Map<String, ImageVector> = linkedMapOf(
        "shopping_basket" to Icons.Outlined.ShoppingBasket,
        "directions_bus" to Icons.Outlined.DirectionsBus,
        "theater_comedy" to Icons.Outlined.TheaterComedy,
        "shopping_bag" to Icons.Outlined.ShoppingBag,
        "restaurant" to Icons.Outlined.Restaurant,
        "home" to Icons.Outlined.Home,
        "autorenew" to Icons.Outlined.Autorenew,
        "favorite" to Icons.Outlined.FavoriteBorder,
        "swap_horiz" to Icons.Outlined.SwapHoriz,
        "category" to Icons.Outlined.Category,
        "local_cafe" to Icons.Outlined.LocalCafe,
        "fastfood" to Icons.Outlined.Fastfood,
        "local_bar" to Icons.Outlined.LocalBar,
        "cake" to Icons.Outlined.Cake,
        "directions_car" to Icons.Outlined.DirectionsCar,
        "local_gas_station" to Icons.Outlined.LocalGasStation,
        "local_taxi" to Icons.Outlined.LocalTaxi,
        "train" to Icons.Outlined.Train,
        "flight" to Icons.Outlined.Flight,
        "luggage" to Icons.Outlined.Luggage,
        "beach_access" to Icons.Outlined.BeachAccess,
        "checkroom" to Icons.Outlined.Checkroom,
        "devices" to Icons.Outlined.Devices,
        "phone_android" to Icons.Outlined.PhoneAndroid,
        "wifi" to Icons.Outlined.Wifi,
        "bolt" to Icons.Outlined.Bolt,
        "water_drop" to Icons.Outlined.WaterDrop,
        "cleaning_services" to Icons.Outlined.CleaningServices,
        "medical_services" to Icons.Outlined.MedicalServices,
        "local_pharmacy" to Icons.Outlined.LocalPharmacy,
        "fitness_center" to Icons.Outlined.FitnessCenter,
        "spa" to Icons.Outlined.Spa,
        "brush" to Icons.Outlined.Brush,
        "pets" to Icons.Outlined.Pets,
        "child_care" to Icons.Outlined.ChildCare,
        "school" to Icons.Outlined.School,
        "menu_book" to Icons.AutoMirrored.Outlined.MenuBook,
        "movie" to Icons.Outlined.Movie,
        "music_note" to Icons.Outlined.MusicNote,
        "sports_esports" to Icons.Outlined.SportsEsports,
        "park" to Icons.Outlined.Park,
        "celebration" to Icons.Outlined.Celebration,
        "card_giftcard" to Icons.Outlined.CardGiftcard,
        "volunteer_activism" to Icons.Outlined.VolunteerActivism,
        "work" to Icons.Outlined.Work,
        "payments" to Icons.Outlined.Payments,
        "savings" to Icons.Outlined.Savings,
        "account_balance" to Icons.Outlined.AccountBalance,
    )

    /** Icon keys a user can pick from, in picker order. */
    val keys: Set<String> get() = byKey.keys

    /** Icon for [iconKey]; unknown keys fall back to the generic «category» icon. */
    fun forKey(iconKey: String): ImageVector = byKey[iconKey] ?: Icons.Outlined.Category
}

/**
 * ARGB to store for palette slot [slot]. Palette colours are stored as the light-theme slot and
 * drawn as the same slot of the current theme ([resolveCategoryColor]), so a picked colour keeps
 * its contrast in both themes.
 */
fun categoryPaletteArgb(slot: Int): Int = PaletteTokens.LightCategories[slot].toArgb()

/**
 * Palette slot of a stored colour (from either theme, or from the palette of the previous design
 * system), or −1 when it is not a palette colour.
 */
fun categoryPaletteSlotOf(colorArgb: Int): Int {
    val palettes = listOf(PaletteTokens.LightCategories.colors, PaletteTokens.DarkCategories.colors) + PaletteTokens.LegacyCategories
    for (colors in palettes) {
        val slot = colors.indexOfFirst { it.toArgb() == colorArgb }
        if (slot >= 0) return slot
    }
    return -1
}

/**
 * Colour of a stored `colorArgb` in [palette]: a palette slot is re-mapped to the same slot of
 * the current theme; any other colour is drawn as is.
 */
fun resolveCategoryColor(colorArgb: Int, palette: CategoryPalette): Color {
    val slot = categoryPaletteSlotOf(colorArgb)
    return if (slot >= 0) palette[slot] else Color(colorArgb)
}

/**
 * Visual of a category: the user's colour if set, otherwise the palette slot for its key
 * (theme-aware: lighter in dark mode, darker in light mode).
 */
@Composable
@ReadOnlyComposable
fun categoryVisual(key: String, name: String, iconKey: String, colorArgb: Int? = null): CategoryVisual {
    val palette = FoldFinanceTheme.palette.categories
    return CategoryVisual(
        name = name,
        color = colorArgb?.let { resolveCategoryColor(it, palette) } ?: palette.forKey(key),
        icon = CategoryIcons.forKey(iconKey),
    )
}

/** Visual of a domain [Category]. */
@Composable
@ReadOnlyComposable
fun Category.visual(): CategoryVisual = categoryVisual(key, name, iconKey, colorArgb)
