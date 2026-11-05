package com.foldfinance.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.core.model.Source
import com.foldfinance.parser.Direction

/**
 * One operation in a list: category tile, merchant, time with the source icon, the amount on the
 * right and, when the operation is uncertain, the «На проверку» badge.
 *
 * Rows are flat (no rounding); put them into a [ListGroup]. Minimum height 64 dp, the whole row is
 * one touch target when [onClick] is set.
 *
 * The row adapts to its width instead of cutting text: the time and the amount are never clipped,
 * the merchant takes what is left and ellipsizes. As the row gets narrower the badge becomes a
 * dot, the source icon goes, then the amount moves to the second line next to the time; in the
 * narrowest panes the category tile goes too (see [TransactionRowLayout]).
 *
 * @param merchant merchant or counterparty as shown to the user.
 * @param time already formatted time («14:05», «вчера»): formatting dates is the screen's job.
 * @param amountMinor amount in kopecks, positive.
 * @param currency ISO 4217 code.
 * @param direction drives the amount colour, see [MoneyText].
 * @param category category visual, see [categoryVisual].
 * @param source where the operation came from; shown as a small icon with a content description.
 * @param needsReview shows the saffron «На проверку» badge.
 * @param selected highlights the row (selected item of a list-detail layout).
 * @param compactBadge always show «На проверку» as a saffron dot (same meaning for screen
 *   readers); narrow rows switch to the dot by themselves.
 */
@Composable
fun TransactionRow(
    merchant: String,
    time: String,
    amountMinor: Long,
    currency: String,
    direction: Direction,
    category: CategoryVisual,
    source: Source,
    modifier: Modifier = Modifier,
    needsReview: Boolean = false,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    compactBadge: Boolean = false,
) {
    val palette = FoldFinanceTheme.palette
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    val background = if (selected) Modifier.background(palette.saffronContainer.copy(alpha = 0.55f)) else Modifier
    TransactionRowLayout(
        modifier = modifier.then(background).then(clickable),
        tile = { CategoryTile(category) },
        merchant = {
            Text(
                text = merchant,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        sourceIcon = {
            Icon(
                imageVector = source.icon(),
                contentDescription = source.label(),
                modifier = Modifier.size(14.dp),
                tint = palette.textMuted,
            )
        },
        time = {
            Text(
                text = time,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted,
                maxLines = 1,
                softWrap = false,
            )
        },
        badge = if (needsReview && !compactBadge) ({ ReviewBadge() }) else null,
        dot = if (needsReview) ({ ReviewDot() }) else null,
        amount = {
            MoneyText(
                amountMinor = amountMinor,
                currency = currency,
                direction = direction,
                style = MoneyStyle.Inline,
            )
        },
    )
}

/**
 * Measure policy of [TransactionRow]. The time, the amount, the icons and the badge are measured
 * at their intrinsic size; the merchant gets the width that is left. Modes, from the widest:
 *
 * 1. `tile · merchant / icon time badge · amount` — the amount centred on the right;
 * 2. the same with the badge pill replaced by the dot, then without the source icon;
 * 3. the amount moves to the second line: `tile · merchant / icon time dot … amount`;
 * 4. as 3 without the source icon, then without the tile (panes about 200 dp wide).
 *
 * Below [TwoLineRow] only modes 3–4 without the source icon are used, so all rows of a narrow
 * list share one shape.
 * The first mode where the merchant keeps at least [MinMerchant] (or its full width, if shorter)
 * and the second line fits completely wins.
 */
@Composable
private fun TransactionRowLayout(
    tile: @Composable () -> Unit,
    merchant: @Composable () -> Unit,
    sourceIcon: @Composable () -> Unit,
    time: @Composable () -> Unit,
    badge: (@Composable () -> Unit)?,
    dot: (@Composable () -> Unit)?,
    amount: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(tile, merchant, sourceIcon, time, badge ?: {}, dot ?: {}, amount),
        modifier = modifier,
    ) { slots, constraints ->
        val tileM = slots[0].single()
        val merchantM = slots[1].single()
        val iconM = slots[2].single()
        val timeM = slots[3].single()
        val badgeM = slots[4].firstOrNull()
        val dotM = slots[5].firstOrNull()
        val amountM = slots[6].single()

        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else WideRow.roundToPx()
        val narrow = width < NarrowRow.roundToPx()
        val padH = (if (narrow) 12.dp else 16.dp).roundToPx()
        val padV = 10.dp.roundToPx()
        val gap = (if (narrow) 10.dp else 12.dp).roundToPx()
        val iconGap = 4.dp.roundToPx()
        val badgeGap = 8.dp.roundToPx()
        val lineGap = 2.dp.roundToPx()
        val inner = (width - 2 * padH).coerceAtLeast(0)

        val loose = Constraints()
        val amountP = amountM.measure(loose)
        val timeP = timeM.measure(loose)
        val tileW = tileM.maxIntrinsicWidth(Constraints.Infinity)
        val iconW = iconM.maxIntrinsicWidth(Constraints.Infinity)
        val badgeW = badgeM?.maxIntrinsicWidth(Constraints.Infinity)
        val dotW = dotM?.maxIntrinsicWidth(Constraints.Infinity)
        val merchantNeed = minOf(merchantM.maxIntrinsicWidth(Constraints.Infinity), MinMerchant.roundToPx())

        fun columnWidth(m: RowMode): Int =
            inner - (if (m.tile) tileW + gap else 0) - (if (m.amountBeside) gap + amountP.width else 0)

        fun fits(m: RowMode): Boolean {
            val column = columnWidth(m)
            val mark = if (m.pill) badgeW else dotW
            val meta = (if (m.icon) iconW + iconGap else 0) + timeP.width + (mark?.let { badgeGap + it } ?: 0) +
                (if (m.amountBeside) 0 else gap + amountP.width)
            return column >= merchantNeed && column >= meta
        }

        // Rows of one narrow list look alike: below [TwoLineRow] the amount always goes down and
        // the source icon goes, whatever the widths of this row's texts.
        val twoLineOnly = width < TwoLineRow.roundToPx()
        val mode = RowModes.firstOrNull { (!it.pill || badgeW != null) && (!twoLineOnly || (!it.amountBeside && !it.icon)) && fits(it) }
            ?: RowModes.last()
        val column = columnWidth(mode).coerceAtLeast(0)
        val tileP = if (mode.tile) tileM.measure(loose) else null
        val iconP = if (mode.icon) iconM.measure(loose) else null
        val markP = (if (mode.pill) badgeM else dotM)?.measure(loose)
        val merchantP = merchantM.measure(Constraints(maxWidth = column))

        val metaHeight = maxOf(
            maxOf(timeP.height, iconP?.height ?: 0),
            maxOf(markP?.height ?: 0, if (mode.amountBeside) 0 else amountP.height),
        )
        val columnHeight = merchantP.height + lineGap + metaHeight
        val contentHeight = maxOf(columnHeight, tileP?.height ?: 0, if (mode.amountBeside) amountP.height else 0)
        val height = maxOf(contentHeight + 2 * padV, MinRowHeight.roundToPx(), constraints.minHeight)

        layout(width, height) {
            var x = padH
            tileP?.let {
                it.placeRelative(x, (height - it.height) / 2)
                x += it.width + gap
            }
            val top = (height - columnHeight) / 2
            merchantP.placeRelative(x, top)
            val metaTop = top + merchantP.height + lineGap
            fun centred(h: Int) = metaTop + (metaHeight - h) / 2
            var mx = x
            iconP?.let {
                it.placeRelative(mx, centred(it.height))
                mx += it.width + iconGap
            }
            timeP.placeRelative(mx, centred(timeP.height))
            mx += timeP.width + badgeGap
            markP?.placeRelative(mx, centred(markP.height))
            val amountX = width - padH - amountP.width
            val amountY = if (mode.amountBeside) (height - amountP.height) / 2 else centred(amountP.height)
            amountP.placeRelative(amountX, amountY)
        }
    }
}

/** A layout mode of [TransactionRowLayout]. */
private data class RowMode(val tile: Boolean, val icon: Boolean, val pill: Boolean, val amountBeside: Boolean)

/** Modes in order of preference (widest first). */
private val RowModes = listOf(
    RowMode(tile = true, icon = true, pill = true, amountBeside = true),
    RowMode(tile = true, icon = true, pill = false, amountBeside = true),
    RowMode(tile = true, icon = false, pill = false, amountBeside = true),
    RowMode(tile = true, icon = true, pill = false, amountBeside = false),
    RowMode(tile = true, icon = false, pill = false, amountBeside = false),
    RowMode(tile = false, icon = false, pill = false, amountBeside = false),
)

/** Below this row width the amount is always on the second line and there is no source icon. */
private val TwoLineRow = 260.dp

/** Below this row width paddings and gaps tighten. */
private val NarrowRow = 300.dp

/** Width assumed when a row is measured without a bound (never happens in lists). */
private val WideRow = 360.dp

/** The merchant keeps at least this much (or its full width) before the row changes mode. */
private val MinMerchant = 88.dp

private val MinRowHeight = 64.dp

/** 40 dp rounded tile with the category icon on a tint of the category colour. */
@Composable
fun CategoryTile(category: CategoryVisual, modifier: Modifier = Modifier) {
    val tint = if (FoldFinanceTheme.palette.isDark) 0.18f else 0.14f
    Box(
        modifier = modifier
            .size(40.dp)
            .background(category.color.copy(alpha = tint), FoldShapes.tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = category.name,
            modifier = Modifier.size(22.dp),
            tint = category.color,
        )
    }
}

/** Saffron pill «На проверку»: the operation is uncertain and waits in the review queue. */
@Composable
fun ReviewBadge(modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    Surface(modifier = modifier, shape = FoldShapes.pill, color = palette.saffronContainer) {
        Text(
            text = stringResource(R.string.component_review_badge),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelMedium,
            color = palette.onSaffronContainer,
            maxLines = 1,
        )
    }
}

/** Compact form of [ReviewBadge]: an 8 dp saffron dot with the same content description. */
@Composable
fun ReviewDot(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.component_review_badge)
    Box(
        modifier
            .size(8.dp)
            .background(FoldFinanceTheme.palette.saffron, FoldShapes.pill)
            .semantics { contentDescription = description },
    )
}

private fun Source.icon(): ImageVector = when (this) {
    Source.SMS -> Icons.Outlined.Sms
    Source.PUSH -> Icons.Outlined.NotificationsNone
    Source.MANUAL -> Icons.Outlined.EditNote
}

@Composable
private fun Source.label(): String = stringResource(
    when (this) {
        Source.SMS -> R.string.component_source_sms
        Source.PUSH -> R.string.component_source_push
        Source.MANUAL -> R.string.component_source_manual
    },
)
