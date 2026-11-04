package com.foldfinance.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.core.model.Category

/**
 * What the UI needs to draw a category: name, colour, icon. Build it with [categoryVisual].
 */
@Immutable
data class CategoryVisual(val name: String, val color: Color, val icon: ImageVector)

/** Icons for `Category.iconKey` (Material Symbols names used by `DefaultCategories`). */
object CategoryIcons {
    private val byKey: Map<String, ImageVector> = mapOf(
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
    )

    /** Icon keys a user can pick from. */
    val keys: Set<String> get() = byKey.keys

    /** Icon for [iconKey]; unknown keys fall back to the generic «category» icon. */
    fun forKey(iconKey: String): ImageVector = byKey[iconKey] ?: Icons.Outlined.Category
}

/**
 * Visual of a category: the user's colour if set, otherwise the palette slot for its key
 * (theme-aware: lighter in dark mode, darker in light mode).
 */
@Composable
@ReadOnlyComposable
fun categoryVisual(key: String, name: String, iconKey: String, colorArgb: Int? = null): CategoryVisual =
    CategoryVisual(
        name = name,
        color = colorArgb?.let { Color(it) } ?: FoldFinanceTheme.palette.categories.forKey(key),
        icon = CategoryIcons.forKey(iconKey),
    )

/** Visual of a domain [Category]. */
@Composable
@ReadOnlyComposable
fun Category.visual(): CategoryVisual = categoryVisual(key, name, iconKey, colorArgb)
