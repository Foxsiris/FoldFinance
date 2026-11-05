package com.foldfinance.app.ui.transaction

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldfinance.app.ui.components.MoneyFormatOverline
import com.foldfinance.app.ui.components.Overline
import com.foldfinance.app.ui.components.TransactionRow
import com.foldfinance.app.ui.format.CardMaskFormat
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.Transaction
import java.time.ZoneId

/** Label of the clip data of a dragged operation (drag & drop onto a category chip). */
const val TransactionDragLabel = "foldfinance/transaction"

/** Shape of item [index] of [size] rows rendered as one rounded group (24 dp ends). */
fun groupItemShape(index: Int, size: Int): Shape {
    val r = 24.dp
    return when {
        size == 1 -> RoundedCornerShape(r)
        index == 0 -> RoundedCornerShape(topStart = r, topEnd = r)
        index == size - 1 -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
        else -> RectangleShape
    }
}

/**
 * One operation as an item of a lazy list: a flat [TransactionRow] on its panel, with the card
 * mask in the meta line («Продукты · •• 1234 · 14:05»).
 *
 * @param index position in its day group (kept for callers; rows are no longer grouped on cards).
 * @param draggable start drag & drop on long press (inner screen only).
 * @param rowModifier applied to the row itself (shared element of the «row → detail» transition).
 */
@Composable
fun TransactionListItem(
    transaction: Transaction,
    category: Category?,
    zone: ZoneId,
    index: Int,
    groupSize: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    draggable: Boolean = false,
    rowModifier: Modifier = Modifier,
    compactBadge: Boolean = false,
    showCard: Boolean = true,
    horizontalPadding: Dp = 10.dp,
    onClick: (() -> Unit)? = null,
) {
    val drag = if (draggable) {
        Modifier.dragAndDropSource { _ ->
            DragAndDropTransferData(
                clipData = ClipData.newPlainText(TransactionDragLabel, transaction.id.toString()),
                localState = transaction.id,
            )
        }
    } else {
        Modifier
    }
    TransactionRow(
        merchant = transaction.title(),
        time = formatTime(transaction.occurredAt, zone),
        amountMinor = transaction.amountMinor,
        currency = transaction.currency,
        direction = transaction.direction,
        category = categoryVisualOrNone(category),
        source = transaction.source,
        needsReview = transaction.needsReview,
        selected = selected,
        onClick = onClick,
        compactBadge = compactBadge,
        horizontalPadding = horizontalPadding,
        meta = if (showCard) listOfNotNull(transaction.cardMask?.let(CardMaskFormat::format)) else emptyList(),
        modifier = modifier.then(rowModifier).then(drag).fillMaxWidth(),
    )
}

/**
 * Day header of a grouped list: «СЕГОДНЯ» on the left, the day's net expenses on the right — both
 * as an overline (12 sp caps, muted).
 *
 * @param background colour behind a sticky header: the panel the list sits on.
 */
@Composable
fun DayHeader(
    title: String,
    totalMinor: Long?,
    currency: String?,
    modifier: Modifier = Modifier,
    background: Color = FoldFinanceTheme.palette.ink,
    horizontalPadding: Dp = 10.dp,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(background)
            .heightIn(min = 36.dp)
            .padding(horizontal = horizontalPadding)
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Overline(title, Modifier.weight(1f).semantics { heading() })
        if (totalMinor != null && totalMinor > 0 && currency != null) {
            Spacer(Modifier.width(8.dp))
            MoneyFormatOverline(totalMinor, currency)
        }
    }
}
