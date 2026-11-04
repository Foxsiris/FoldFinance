package com.foldfinance.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
 * @param merchant merchant or counterparty as shown to the user.
 * @param time already formatted time («14:05», «вчера»): formatting dates is the screen's job.
 * @param amountMinor amount in kopecks, positive.
 * @param currency ISO 4217 code.
 * @param direction drives the amount colour, see [MoneyText].
 * @param category category visual, see [categoryVisual].
 * @param source where the operation came from; shown as a small icon with a content description.
 * @param needsReview shows the saffron «На проверку» badge.
 * @param selected highlights the row (selected item of a list-detail layout).
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
) {
    val palette = FoldFinanceTheme.palette
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    val background = if (selected) Modifier.background(palette.saffronContainer.copy(alpha = 0.55f)) else Modifier
    Row(
        modifier = modifier
            .then(background)
            .then(clickable)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(category)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = merchant,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = source.icon(),
                    contentDescription = source.label(),
                    modifier = Modifier.size(14.dp),
                    tint = palette.textMuted,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textMuted,
                    maxLines = 1,
                )
                if (needsReview) {
                    Spacer(Modifier.width(8.dp))
                    ReviewBadge()
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        MoneyText(
            amountMinor = amountMinor,
            currency = currency,
            direction = direction,
            style = MoneyStyle.Inline,
        )
    }
}

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
