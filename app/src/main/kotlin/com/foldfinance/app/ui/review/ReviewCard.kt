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
        Surface(shape = FoldShapes.pill, color = if (right) palette.saffron else palette.surfaceRaised) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (right) Icons.Outlined.Check else Icons.Outlined.Category,
                    contentDescription = null,
                    tint = if (right) palette.onSaffron else palette.text,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (right) R.string.review_confirm else R.string.review_pick_category),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (right) palette.onSaffron else palette.text,
                )
            }
        }
    }
}

/** Content of a review card: what the bank said and what we understood from it. */
@Composable
fun ReviewCard(
    transaction: Transaction,
    category: Category?,
    raw: List<RawMessage>,
    today: LocalDate,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val palette = FoldFinanceTheme.palette
    Surface(modifier.fillMaxWidth(), shape = FoldShapes.summary, color = palette.surface, shadowElevation = 2.dp) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryTile(categoryVisualOrNone(category))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        transaction.title(),
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.review_card_meta,
                            dayTitle(transaction.occurredAt.atZone(zone).toLocalDate(), today),
                            formatTime(transaction.occurredAt, zone),
                            transaction.direction.label(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textMuted,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            MoneyText(
                amountMinor = transaction.amountMinor,
                currency = transaction.currency,
                direction = transaction.direction,
                style = MoneyStyle.Display,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = categoryVisualOrNone(category).name,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textMuted,
            )
            val text = raw.firstOrNull()?.text
            if (text != null) {
                Spacer(Modifier.height(20.dp))
                Surface(shape = FoldShapes.tile, color = palette.surfaceRaised, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.review_card_source, raw.first().sender, transaction.source.label()),
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textMuted,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(text, style = MaterialTheme.typography.bodyLarge, color = palette.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** The card under the top one, a step back: shows there is more to review. */
@Composable
fun StackedCardBehind(modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    Surface(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = 0.94f
                scaleY = 0.94f
                translationY = 18.dp.toPx()
            },
        shape = FoldShapes.summary,
        color = palette.surfaceRaised,
    ) { Box(Modifier.fillMaxSize()) }
}
