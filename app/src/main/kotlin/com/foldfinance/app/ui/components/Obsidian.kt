package com.foldfinance.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldMotion
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.app.ui.theme.FoldTextStyles
import com.foldfinance.app.ui.format.RuLocale

/**
 * A panel of the inner screen: `surface` colour, 28 dp corners (Overview, History, Analytics
 * mockups). Two panels next to each other leave the 16 dp hinge gap between them; [TwoPane]
 * keeps it.
 *
 * @param shape [FoldShapes.panel], or [FoldShapes.sheet] for the bottom panel of tabletop.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    shape: Shape = FoldShapes.panel,
    color: Color = FoldFinanceTheme.palette.surface,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.clip(shape).background(color), content = content)
}

/** «НАДПИСЬ»: 12 sp caps with +4 % tracking, muted. Day headers, small section labels. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = FoldFinanceTheme.palette.textMuted) {
    Text(
        text = text.uppercase(RuLocale),
        modifier = modifier,
        style = FoldTextStyles.overline,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Pane or screen title: 22 / 600 (28 on the outer screen's «Проверка»). */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, large: Boolean = false) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        style = if (large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
        color = FoldFinanceTheme.palette.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Small tinted pill: «Неуверенный тип», «SMS · 900», «↑ 12% к сентябрю», the review counter.
 * @param color text colour; the fill is the same colour at 12 % unless [container] is given.
 */
@Composable
fun PillTag(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    container: Color = color.copy(alpha = 0.13f),
    large: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier
            .background(container, FoldShapes.pill)
            .padding(horizontal = if (large) 12.dp else 10.dp, vertical = if (large) 6.dp else 4.dp),
        style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
    )
}

/** Round 44 dp icon button on a field-coloured circle (search, close, filters). */
@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    container: Color = FoldFinanceTheme.palette.surfaceRaised,
    tint: Color = FoldFinanceTheme.palette.text,
) {
    Box(
        modifier
            .size(size)
            .clip(FoldShapes.pill)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/**
 * The lime «Добавить»: 62 dp, 22 dp corners, with a 6 dp ring of [ringColor] that cuts it out of
 * whatever scrolls under it (Main mockup). On the rail it is 56 dp without the ring.
 */
@Composable
fun AccentFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 62.dp,
    shape: Shape = FoldShapes.fab,
    ringColor: Color? = FoldFinanceTheme.palette.ink,
    elevated: Boolean = true,
) {
    val palette = FoldFinanceTheme.palette
    val ring = 6.dp
    Box(
        modifier
            .then(if (ringColor != null) Modifier.background(ringColor, roundedPlus(shape)).padding(ring) else Modifier)
            .then(if (elevated) Modifier.shadow(12.dp, shape, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
            .size(size)
            .clip(shape)
            .background(palette.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(26.dp))
    }
}

/** The ring around a FAB follows its corners, a little rounder. */
private fun roundedPlus(shape: Shape): Shape = if (shape == FoldShapes.fab) androidx.compose.foundation.shape.RoundedCornerShape(28.dp) else shape

/**
 * Main action of a screen: lime fill, dark text, 16 dp corners, 54–64 dp tall in the thumb zone
 * («Сохранить · 1 250 ₽», «Подтвердить»). Disabled: field colour with muted text («Введите сумму»).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    icon: ImageVector? = null,
) {
    val palette = FoldFinanceTheme.palette
    val container by animateColorAsState(if (enabled) palette.accent else palette.surfaceRaised, FoldMotion.effects(), label = "primary")
    val content = if (enabled) palette.onAccent else palette.textDisabled
    Row(
        modifier
            .heightIn(min = height)
            .clip(FoldShapes.key)
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * 1.07f), color = content, maxLines = 1)
    }
}

/** Secondary action: outlined with the hairline, 14 dp corners («Изменить», «Показать снова»). */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 44.dp,
    color: Color = FoldFinanceTheme.palette.text,
) {
    val palette = FoldFinanceTheme.palette
    Row(
        modifier
            .heightIn(min = height)
            .clip(FoldShapes.tile)
            .border(1.dp, palette.outline, FoldShapes.tile)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}

/**
 * Text chip of a filter bar: «Ноябрь ⌄», «Категория ⌄». Active filters get the lime tint and
 * border; idle ones the hairline.
 */
@Composable
fun FilterPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailingIcon: ImageVector? = null,
    trailingDescription: String? = null,
    height: Dp = 36.dp,
) {
    val palette = FoldFinanceTheme.palette
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = FoldShapes.chip,
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .height(height)
                    .clip(FoldShapes.chip)
                    .background(if (active) palette.accentSoft else Color.Transparent)
                    .border(1.dp, if (active) palette.accentLine else palette.outline, FoldShapes.chip)
                    .padding(start = 12.dp, end = if (trailingIcon != null) 8.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) palette.text else palette.textSoft,
                    maxLines = 1,
                )
                if (trailingIcon != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        trailingIcon,
                        contentDescription = trailingDescription,
                        tint = if (active) palette.text else palette.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Segmented control on a field-coloured track: «Расход / Доход / Перевод», «День … Свой».
 * The thumb slides with the spatial spring (instant under reduced motion).
 *
 * @param selected index of the selected segment; −1 shows no thumb.
 */
@Composable
fun SegmentedControl(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    role: Role = Role.Tab,
    description: String? = null,
) {
    val palette = FoldFinanceTheme.palette
    val thumbColor = if (palette.isDark) Color(0xFF2A2A31) else palette.card
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(palette.surfaceRaised, FoldShapes.tile)
            .padding(4.dp)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        val segment = maxWidth / labels.size.coerceAtLeast(1)
        val thumbIndex by animateFloatAsState(selected.coerceAtLeast(0).toFloat(), FoldMotion.spatial(), label = "segmentThumb")
        if (selected >= 0) {
            Box(
                Modifier
                    .offset(x = segment * thumbIndex)
                    .width(segment)
                    .fillMaxHeight()
                    .then(if (palette.isDark) Modifier else Modifier.shadow(1.dp, FoldShapes.chip))
                    .background(thumbColor, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
            )
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            labels.forEachIndexed { index, label ->
                val isSelected = index == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .selectable(selected = isSelected, role = role, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) palette.text else palette.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Tile of the outer Main screen and of Analytics: 24 dp corners on the card colour, caption,
 * a number, a supporting line.
 */
@Composable
fun StatTile(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = FoldFinanceTheme.palette.card,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(FoldShapes.listGroup)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** A row of tiles that share the width equally. */
@Composable
fun TileRow(modifier: Modifier = Modifier, spacing: Dp = 10.dp, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing), content = content)
}

/** Thin progress bar (category shares, ratio): 6 dp, rounded, on the hairline colour. */
@Composable
fun ShareBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val palette = FoldFinanceTheme.palette
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(FoldShapes.pill)
            .background(if (palette.isDark) palette.seam else palette.surfaceRaised),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(FoldShapes.pill)
                .background(color),
        )
    }
}

/** Border helper used by outlined surfaces. */
@Composable
fun hairline(): BorderStroke = BorderStroke(1.dp, FoldFinanceTheme.palette.outline)

/** An amount as an overline (day totals of grouped lists): «4 039,50 ₽», 12 sp, muted. */
@Composable
fun MoneyFormatOverline(amountMinor: Long, currency: String, modifier: Modifier = Modifier) {
    Text(
        text = moneyLabel(amountMinor, currency),
        modifier = modifier,
        style = FoldTextStyles.overline,
        color = FoldFinanceTheme.palette.textMuted,
        maxLines = 1,
    )
}

/**
 * [com.foldfinance.app.ui.format.MoneyFormat.format] for plain text (captions, overlines, button
 * labels): the narrow no-break space between digit groups becomes a regular no-break space, which
 * Onest draws wide enough to read «95 000» without [MoneyText]'s extra tracking.
 */
fun moneyLabel(
    amountMinor: Long,
    currency: String,
    signDisplay: com.foldfinance.app.ui.format.SignDisplay = com.foldfinance.app.ui.format.SignDisplay.NegativeOnly,
): String = com.foldfinance.app.ui.format.MoneyFormat.format(amountMinor, currency, signDisplay)
    .replace(com.foldfinance.app.ui.format.MoneyFormat.NARROW_NBSP, ' ')
