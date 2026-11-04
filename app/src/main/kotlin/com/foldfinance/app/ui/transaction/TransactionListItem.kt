package com.foldfinance.app.ui.transaction

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.foldfinance.app.ui.components.ListGroupDivider
import com.foldfinance.app.ui.components.MoneyStyle
import com.foldfinance.app.ui.components.MoneyText
import com.foldfinance.app.ui.components.TransactionRow
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.Transaction
import java.time.ZoneId

/** Label of the clip data of a dragged operation (drag & drop onto a category chip). */
const val TransactionDragLabel = "foldfinance/transaction"

/** Shape of item [index] of [size] rows rendered as one [com.foldfinance.app.ui.components.ListGroup]. */
fun groupItemShape(index: Int, size: Int): Shape {
    val r = 20.dp
    return when {
        size == 1 -> RoundedCornerShape(r)
        index == 0 -> RoundedCornerShape(topStart = r, topEnd = r)
        index == size - 1 -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
        else -> RectangleShape
    }
}

/**
 * One operation as an item of a lazy list that looks like a [com.foldfinance.app.ui.components.ListGroup]:
 * rounded at the ends of its group, hairline between rows.
 *
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
    onClick: (() -> Unit)? = null,
) {
    val palette = FoldFinanceTheme.palette
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
    Column(
        modifier
            .fillMaxWidth()
            .clip(groupItemShape(index, groupSize))
            .background(palette.surface),
    ) {
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
            modifier = rowModifier.then(drag).fillMaxWidth(),
        )
        if (index < groupSize - 1) ListGroupDivider()
    }
}

/** Day header of a grouped list: «Вчера» on the left, the day's net expenses on the right. */
@Composable
fun DayHeader(title: String, totalMinor: Long?, currency: String?, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .background(palette.ink)
            .heightIn(min = 44.dp)
            .padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            color = palette.textMuted,
        )
        if (totalMinor != null && totalMinor > 0 && currency != null) {
            Spacer(Modifier.width(8.dp))
            MoneyText(amountMinor = totalMinor, currency = currency, style = MoneyStyle.Inline, color = palette.textMuted)
        }
    }
}
