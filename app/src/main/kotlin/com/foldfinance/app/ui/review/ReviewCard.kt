package com.foldfinance.app.ui.review

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import com.foldfinance.app.ui.components.CategoryChip
import com.foldfinance.app.ui.components.Overline
import com.foldfinance.app.ui.components.PillTag
import com.foldfinance.app.ui.components.visual
import com.foldfinance.app.ui.format.CardMaskFormat
import com.foldfinance.app.ui.format.RuLocale
import com.foldfinance.app.ui.theme.FoldTextStyles
import com.foldfinance.core.model.Source
import com.foldfinance.parser.Kind
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.CategoryTile
import com.foldfinance.app.ui.components.MoneyStyle
import com.foldfinance.app.ui.components.MoneyText
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldMotion
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.app.ui.transaction.categoryVisualOrNone
import com.foldfinance.app.ui.transaction.dayTitle
import com.foldfinance.app.ui.transaction.formatTime
import com.foldfinance.app.ui.transaction.label
import com.foldfinance.app.ui.transaction.title
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.RawMessage
import com.foldfinance.core.model.Transaction
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Swipe distance (fraction of the card width) or fling speed that commits a swipe. */
private const val CommitFraction = 0.32f
private const val CommitVelocity = 1600f

/**
 * The top card of the review stack. Drag right — confirm, drag left — pick a category. The card
 * follows the finger, tilts slightly, and on release settles with the spatial spring of
 * [FoldMotion], carrying the finger's velocity; crossing the commit threshold ticks a haptic.
 * Under «Убрать анимацию» it jumps instead of flying. Screen readers get the same two actions as
 * custom actions, and the screen shows them as buttons too.
 */
@Composable
fun SwipeableReviewCard(
    transaction: Transaction,
    category: Category?,
    raw: List<RawMessage>,
    today: LocalDate,
    zone: ZoneId,
    onConfirm: () -> Unit,
    onPickCategory: () -> Unit,
    modifier: Modifier = Modifier,
    quickCategories: List<Category> = emptyList(),
    onQuickPick: (Long) -> Unit = {},
) {
    val reducedMotion = FoldFinanceTheme.reducedMotion
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember(transaction.id) { Animatable(0f) }
    var width by remember { mutableStateOf(1f) }
    var armed by remember(transaction.id) { mutableStateOf(0) }
    val confirm by rememberUpdatedState(onConfirm)
    val pick by rememberUpdatedState(onPickCategory)
    val confirmLabel = stringResource(R.string.review_confirm)
    val pickLabel = stringResource(R.string.review_pick_category)

    Box(
        modifier
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(confirmLabel) { confirm(); true },
                    CustomAccessibilityAction(pickLabel) { pick(); true },
                )
            }
            .pointerInput(transaction.id) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val next = offset.value + amount
                        scope.launch { offset.snapTo(next) }
                        val side = when {
                            next > width * CommitFraction -> 1
                            next < -width * CommitFraction -> -1
                            else -> 0
                        }
                        if (side != armed) {
                            if (side != 0) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            armed = side
                        }
                    },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity().x
                        val value = offset.value
                        val spec = FoldMotion.spatial<Float>(reducedMotion)
                        scope.launch {
                            when {
                                value > width * CommitFraction || velocity > CommitVelocity -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    if (!reducedMotion) offset.animateTo(width * 1.4f, spec, initialVelocity = velocity)
                                    confirm()
                                }
                                value < -width * CommitFraction || velocity < -CommitVelocity -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    pick()
                                    offset.animateTo(0f, spec, initialVelocity = velocity)
                                }
                                else -> offset.animateTo(0f, spec, initialVelocity = velocity)
                            }
                            armed = 0
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, FoldMotion.spatial(reducedMotion)) } },
                )
            },
    ) {
        val progress = (offset.value / (width * CommitFraction)).coerceIn(-1f, 1f)
        SwipeHint(progress)
        ReviewCard(
            transaction = transaction,
            category = category,
            raw = raw,
            today = today,
            zone = zone,
            quickCategories = quickCategories,
            onQuickPick = onQuickPick,
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .graphicsLayer { rotationZ = (offset.value / width) * 6f },
        )
    }
}


/** What the swipe will do, revealed under the card as it moves. */
@Composable
private fun SwipeHint(progress: Float) {
    if (progress == 0f) return
    val palette = FoldFinanceTheme.palette
    val right = progress > 0
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .graphicsLayer { alpha = abs(progress) },
        horizontalArrangement = if (right) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = FoldShapes.pill, color = if (right) palette.accent else palette.surfaceRaised) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (right) Icons.Outlined.Check else Icons.Outlined.Category,
                    contentDescription = null,
                    tint = if (right) palette.onAccent else palette.text,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (right) R.string.review_confirm else R.string.review_pick_category),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (right) palette.onAccent else palette.text,
                )
            }
        }
    }
}

/**
 * Content of a review card (Review mockup): why it is here and where it came from, the merchant,
 * the amount, card and time, the original text of the bank in mono — and a quick question with up
 * to three categories (a tap assigns and takes the operation out of the queue).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewCard(
    transaction: Transaction,
    category: Category?,
    raw: List<RawMessage>,
    today: LocalDate,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    quickCategories: List<Category> = emptyList(),
    onQuickPick: (Long) -> Unit = {},
) {
    val palette = FoldFinanceTheme.palette
    Column(
        modifier
            .fillMaxWidth()
            .shadow(if (palette.isDark) 20.dp else 6.dp, FoldShapes.card, ambientColor = Color.Black, spotColor = Color.Black)
            .background(palette.card, FoldShapes.card)
            .border(1.dp, if (palette.isDark) Color(0xFF25252B) else palette.outline, FoldShapes.card)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillTag(stringResource(reasonOf(transaction)), color = palette.review, container = palette.review.copy(alpha = 0.14f))
            PillTag(sourceTag(transaction, raw), color = palette.textSecondary, container = palette.surfaceRaised)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                transaction.title(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                color = palette.textSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MoneyText(
                amountMinor = transaction.amountMinor,
                currency = transaction.currency,
                direction = transaction.direction.takeIf { it != com.foldfinance.parser.Direction.EXPENSE },
                style = MoneyStyle.DisplayMedium,
            )
            Text(
                text = cardLine(transaction, today, zone),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val text = raw.firstOrNull()?.text
        if (text != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(palette.surfaceSunken, androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Overline(stringResource(R.string.review_raw_title))
                Text(text, style = FoldTextStyles.mono, color = palette.textSoft, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        if (quickCategories.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(if (category == null) R.string.review_question_where else R.string.review_question_category),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    quickCategories.forEach { option ->
                        val visual = option.visual()
                        CategoryChip(
                            name = visual.name,
                            color = visual.color,
                            selected = option.id == category?.id,
                            onClick = { onQuickPick(option.id) },
                            height = 36.dp,
                            style = com.foldfinance.app.ui.components.ChipStyle.Outlined,
                        )
                    }
                }
            }
        }
    }
}

/** Why the operation waits for review — said plainly, never guessed. */
private fun reasonOf(transaction: Transaction): Int = when {
    transaction.categoryId == null -> R.string.review_reason_category
    transaction.kind == Kind.UNKNOWN -> R.string.review_reason_type
    else -> R.string.review_reason_check
}

/** «SMS · 900», «Push · Т-Банк»: the channel and the sender of the original message. */
@Composable
private fun sourceTag(transaction: Transaction, raw: List<RawMessage>): String {
    val channel = stringResource(
        when (transaction.source) {
            Source.SMS -> R.string.review_source_sms
            Source.PUSH -> R.string.review_source_push
            Source.MANUAL -> R.string.review_source_manual
        },
    )
    val sender = raw.firstOrNull()?.sender?.takeIf { it.isNotBlank() } ?: return channel
    return stringResource(R.string.review_source_tag, channel, sender)
}

/** «Карта •• 1234 · сегодня, 12:41». */
@Composable
private fun cardLine(transaction: Transaction, today: LocalDate, zone: ZoneId): String {
    val day = dayTitle(transaction.occurredAt.atZone(zone).toLocalDate(), today).lowercase(RuLocale)
    val time = formatTime(transaction.occurredAt, zone)
    val whenText = stringResource(R.string.review_card_when, day, time)
    val mask = transaction.cardMask?.takeIf { it.isNotBlank() } ?: return whenText
    return stringResource(R.string.review_card_line, CardMaskFormat.format(mask), whenText)
}

/**
 * The cards under the top one, a step back each (12 and 24 dp): shows there is more to review.
 * @param depth 1 or 2 cards behind.
 */
@Composable
fun StackedCardBehind(modifier: Modifier = Modifier, depth: Int = 1) {
    val palette = FoldFinanceTheme.palette
    Box(modifier) {
        if (depth >= 2) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp)
                    .offset(y = 16.dp)
                    .background(if (palette.isDark) Color(0xFF0F0F12) else palette.surfaceRaised, FoldShapes.card)
                    .border(1.dp, if (palette.isDark) Color(0xFF18181C) else palette.outline, FoldShapes.card),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp)
                .offset(y = 8.dp)
                .background(if (palette.isDark) Color(0xFF121215) else palette.surface, FoldShapes.card)
                .border(1.dp, if (palette.isDark) Color(0xFF1C1C21) else palette.outline, FoldShapes.card),
        )
    }
}
