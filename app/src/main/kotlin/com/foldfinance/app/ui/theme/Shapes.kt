package com.foldfinance.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Obsidian radii (System board, «Форма и касание»): 12 chips · 16 keys · 22 FAB · 24 tiles ·
 * 28 panels. The higher a container sits, the rounder it is.
 */
object FoldShapes {
    /** Panels of the inner screen, summary containers, dialogs: 28 dp. */
    val panel: Shape = RoundedCornerShape(28.dp)

    /** Same as [panel]; the name of the previous system. */
    val summary: Shape = panel

    /** The review card: a bit rounder than a panel, it is held in the hand. */
    val card: Shape = RoundedCornerShape(30.dp)

    /** Bottom sheets and the bottom panel of tabletop: rounded on top only. */
    val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Tiles («Сегодня», «На проверку», stats) and list groups: 24 dp. */
    val listGroup: Shape = RoundedCornerShape(24.dp)

    /** Same as [listGroup]. */
    val tileLarge: Shape = listGroup

    /** A row inside a [listGroup]. */
    val row: Shape = RectangleShape

    /** Selected list row, raw-message wells: 16 dp. */
    val rowSelected: Shape = RoundedCornerShape(16.dp)

    /** Badges, counters, the review reason tag. */
    val pill: Shape = CircleShape

    /** Filter and category chips, segmented tracks: 12 dp. */
    val chip: Shape = RoundedCornerShape(12.dp)

    /** Keys of the amount keypad, the save button: 16 dp. */
    val key: Shape = RoundedCornerShape(16.dp)

    /** Floating action button: 22 dp (18 dp for the 56 dp FAB of the rail). */
    val fab: Shape = RoundedCornerShape(22.dp)

    /** Icon tiles inside rows, text fields, search: 14 dp. */
    val tile: Shape = RoundedCornerShape(14.dp)
}

/** Material shape roles mapped onto [FoldShapes]. */
val FoldMaterialShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
