package com.foldfinance.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.app.ui.theme.FoldTextStyles
import com.foldfinance.core.model.Source
import com.foldfinance.parser.Direction

/**
 * One operation in a list (Main, Overview and History mockups): a 42 dp category tile, the
 * merchant (15 / 500), the meta line «Продукты · •• 1234 · 14:05» (12.5, muted) and the amount on
 * the right (15 / 600, «−1 500 ₽» for expenses, green «+95 000 ₽» for income).
 *
 * Rows sit flat on their panel; the selected one gets a 16 dp rounded highlight. Minimum height
 * 58 dp, the whole row is one touch target when [onClick] is set.
 *
 * The row adapts to its width instead of cutting the important parts: the amount is never
 * clipped, the merchant and the meta line ellipsize. As the row gets narrower the amount moves to
 * the second line, then the tile goes (panes about 200 dp wide).
 *
 * @param merchant merchant or counterparty as shown to the user.
 * @param time already formatted time («14:05»): formatting dates is the screen's job.
 * @param amountMinor amount in kopecks, positive.
 * @param currency ISO 4217 code.
 * @param direction drives the amount colour and sign, see [MoneyText].
 * @param category category visual, see [categoryVisual]; its name starts the meta line.
 * @param source where the operation came from (read out by screen readers).
 * @param needsReview shows the amber «на проверку» dot.
 * @param selected highlights the row (selected item of a list-detail layout).
 * @param meta extra parts of the meta line between the category and the time («•• 1234»).
 * @param compactBadge kept for callers of the previous API; the review mark is always a dot.
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
    meta: List<String> = emptyList(),
    horizontalPadding: Dp = 10.dp,
) {
    val palette = FoldFinanceTheme.palette
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    val background = if (selected) Modifier.background(palette.selection) else Modifier
    val sourceLabel = source.label()
    val caption = (listOf(category.name) + meta + time).filter { it.isNotBlank() }.joinToString(" · ")
    TransactionRowLayout(
        modifier = modifier
            .clip(FoldShapes.rowSelected)
            .then(background)
            .then(clickable)
            .semantics { contentDescription = sourceLabel },
        horizontalPadding = horizontalPadding,
        tile = { CategoryTile(category) },
        merchant = {
            Text(
                text = merchant,
                style = FoldTextStyles.row,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        meta = {
            Text(
                text = caption,
                style = FoldTextStyles.caption,
                color = palette.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        dot = if (needsReview) ({ ReviewDot() }) else null,
        amount = {
            MoneyText(
                amountMinor = signedRowAmount(amountMinor, direction),
                currency = currency,
                direction = direction,
                style = MoneyStyle.Inline,
            )
        },
    )
}

/**
 * Measure policy of [TransactionRow]. The amount and the dot are measured at their intrinsic
 * size; the merchant and the meta line get the width that is left and ellipsize. Modes, from the
 * widest: `tile · merchant / meta · amount` (amount centred on the right); the amount on the
 * second line next to the meta; the same without the tile. The first mode where the merchant keeps
 * at least [MinMerchant] (or its full width) and the meta keeps [MinMeta] wins.
 */
@Composable
private fun TransactionRowLayout(
    tile: @Composable () -> Unit,
    merchant: @Composable () -> Unit,
    meta: @Composable () -> Unit,
    dot: (@Composable () -> Unit)?,
    amount: @Composable () -> Unit,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(tile, merchant, meta, dot ?: {}, amount),
        modifier = modifier,
    ) { slots, constraints ->
        val tileM = slots[0].single()
        val merchantM = slots[1].single()
        val metaM = slots[2].single()
        val dotM = slots[3].firstOrNull()
        val amountM = slots[4].single()

        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else WideRow.roundToPx()
        val padH = horizontalPadding.roundToPx()
        val padV = 8.dp.roundToPx()
        val gap = 12.dp.roundToPx()
        val dotGap = 6.dp.roundToPx()
        val lineGap = 3.dp.roundToPx()
        val inner = (width - 2 * padH).coerceAtLeast(0)

        val loose = Constraints()
        val amountP = amountM.measure(loose)
        val dotP = dotM?.measure(loose)
        val dotW = dotP?.let { it.width + dotGap } ?: 0
        val tileW = tileM.maxIntrinsicWidth(Constraints.Infinity)
        val merchantNeed = minOf(merchantM.maxIntrinsicWidth(Constraints.Infinity), MinMerchant.roundToPx())
        val metaNeed = minOf(metaM.maxIntrinsicWidth(Constraints.Infinity), MinMeta.roundToPx())

        fun columnWidth(tileShown: Boolean, beside: Boolean): Int =
            inner - (if (tileShown) tileW + gap else 0) - (if (beside) gap + amountP.width else 0)

        fun fits(tileShown: Boolean, beside: Boolean): Boolean {
            val column = columnWidth(tileShown, beside)
            val metaLine = metaNeed + dotW + (if (beside) 0 else gap + amountP.width)
            return column >= merchantNeed && column >= metaLine
        }

        val (tileShown, beside) = listOf(true to true, true to false, false to false).firstOrNull { (t, b) -> fits(t, b) } ?: (false to false)
        val column = columnWidth(tileShown, beside).coerceAtLeast(0)
        val tileP = if (tileShown) tileM.measure(loose) else null
        val merchantP = merchantM.measure(Constraints(maxWidth = column))
        val metaMax = (column - dotW - (if (beside) 0 else gap + amountP.width)).coerceAtLeast(0)
        val metaP = metaM.measure(Constraints(maxWidth = metaMax))

        val metaHeight = maxOf(metaP.height, dotP?.height ?: 0, if (beside) 0 else amountP.height)
        val columnHeight = merchantP.height + lineGap + metaHeight
        val contentHeight = maxOf(columnHeight, tileP?.height ?: 0, if (beside) amountP.height else 0)
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
            metaP.placeRelative(x, centred(metaP.height))
            dotP?.placeRelative(x + metaP.width + dotGap, centred(dotP.height))
            val amountX = width - padH - amountP.width
            val amountY = if (beside) (height - amountP.height) / 2 else centred(amountP.height)
            amountP.placeRelative(amountX, amountY)
        }
    }
}

/** Width assumed when a row is measured without a bound (never happens in lists). */
private val WideRow = 360.dp

/** The merchant keeps at least this much (or its full width) before the row changes mode. */
private val MinMerchant = 96.dp

/** The meta line keeps at least this much (or its full width). */
private val MinMeta = 72.dp

private val MinRowHeight = 58.dp

/**
 * 42 dp tile with the category icon on a 15 % tint of the category colour, 14 dp corners.
 * @param size 42 dp in rows, 40 in compact lists.
 */
@Composable
fun CategoryTile(category: CategoryVisual, modifier: Modifier = Modifier, size: Dp = 42.dp) {
    val tint = if (FoldFinanceTheme.palette.isDark) 0.15f else 0.13f
    Box(
        modifier = modifier
            .size(size)
            .background(category.color.copy(alpha = tint), FoldShapes.tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = category.name,
            modifier = Modifier.size(size * 0.48f),
            tint = category.color,
        )
    }
}

/** Amber pill «На проверку»: the operation is uncertain and waits in the review queue. */
@Composable
fun ReviewBadge(modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    Text(
        text = stringResource(R.string.component_review_badge),
        modifier = modifier
            .background(palette.review.copy(alpha = 0.14f), FoldShapes.pill)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        color = palette.review,
        maxLines = 1,
    )
}

/** Compact form of [ReviewBadge]: a 7 dp amber dot with the same content description. */
@Composable
fun ReviewDot(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.component_review_badge)
    Box(
        modifier
            .size(7.dp)
            .background(FoldFinanceTheme.palette.review, FoldShapes.pill)
            .semantics { contentDescription = description },
    )
}

@Composable
private fun Source.label(): String = stringResource(
    when (this) {
        Source.SMS -> R.string.component_source_sms
        Source.PUSH -> R.string.component_source_push
        Source.MANUAL -> R.string.component_source_manual
    },
)
