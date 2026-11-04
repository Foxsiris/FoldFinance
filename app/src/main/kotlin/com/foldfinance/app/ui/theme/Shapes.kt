package com.foldfinance.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Shape follows hierarchy (docs/design.md, principle 4): the higher a container sits,
 * the rounder it is.
 */
object FoldShapes {
    /** Summary card «Сегодня / Месяц», hero containers. */
    val summary: Shape = RoundedCornerShape(28.dp)

    /** Bottom sheets: rounded on top only. */
    val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** A group of list rows. Rows inside a group are not rounded ([row]). */
    val listGroup: Shape = RoundedCornerShape(20.dp)

    /** A row inside a [listGroup]. */
    val row: Shape = RectangleShape

    /** Chips, segmented controls, badges. */
    val pill: Shape = CircleShape

    /** Floating action button. */
    val fab: Shape = RoundedCornerShape(20.dp)

    /** Small affordances: icon tiles inside rows, text fields. */
    val tile: Shape = RoundedCornerShape(12.dp)
}

/** Material shape roles mapped onto [FoldShapes]. */
val FoldMaterialShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
