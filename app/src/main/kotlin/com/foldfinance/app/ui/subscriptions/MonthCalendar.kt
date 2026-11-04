package com.foldfinance.app.ui.subscriptions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.analytics.AnalyticsFormat
import com.foldfinance.app.ui.format.RuLocale
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

/**
 * Month grid of charges, Monday first. Days with charges carry up to three dots in the colours of
 * the subscriptions' categories (past charges muted); today is outlined, the tapped day filled
 * saffron. Every day is a ≥ 44 dp touch target.
 *
 * @param dotColor colour of a charge's dot.
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    today: LocalDate,
    charges: Map<LocalDate, List<CalendarCharge>>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    dotColor: @Composable (CalendarCharge) -> Color,
    modifier: Modifier = Modifier,
    cellMinHeight: Dp = 48.dp,
) {
    val palette = FoldFinanceTheme.palette
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = AnalyticsFormat.monthName(month.atDay(1)) + if (month.year != today.year) " ${month.year}" else "",
                modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                color = palette.text,
            )
            IconButton(onClick = onPrevious, enabled = canGoPrevious) {
                Icon(
                    Icons.Outlined.ChevronLeft,
                    contentDescription = stringResource(R.string.subscriptions_calendar_previous),
                    tint = if (canGoPrevious) palette.text else palette.textMuted.copy(alpha = 0.4f),
                )
            }
            IconButton(onClick = onNext, enabled = canGoNext) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = stringResource(R.string.subscriptions_calendar_next),
                    tint = if (canGoNext) palette.text else palette.textMuted.copy(alpha = 0.4f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.SHORT_STANDALONE, RuLocale),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (day.value >= 6) palette.textMuted.copy(alpha = 0.7f) else palette.textMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value - 1
        val cells = leading + month.lengthOfMonth()
        val rows = (cells + 6) / 7
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayIndex = row * 7 + col - leading
                    if (dayIndex < 0 || dayIndex >= month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val date = first.plusDays(dayIndex.toLong())
                        DayCell(
                            date = date,
                            charges = charges[date].orEmpty(),
                            isToday = date == today,
                            isPast = date.isBefore(today),
                            isSelected = date == selected,
                            onClick = { onSelect(date) },
                            dotColor = dotColor,
                            minHeight = cellMinHeight,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    charges: List<CalendarCharge>,
    isToday: Boolean,
    isPast: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    dotColor: @Composable (CalendarCharge) -> Color,
    minHeight: Dp,
    modifier: Modifier,
) {
    val palette = FoldFinanceTheme.palette
    val description = AnalyticsFormat.date(date, date) + if (charges.isNotEmpty()) {
        ": " + charges.joinToString { it.item.subscription.merchant }
    } else {
        ""
    }
    Box(
        modifier
            .height(minHeight)
            .padding(2.dp)
            .clip(FoldShapes.tile)
            .then(if (isSelected) Modifier.background(palette.saffronContainer) else Modifier)
            .then(if (isToday && !isSelected) Modifier.border(BorderStroke(1.5.dp, palette.saffron), FoldShapes.tile) else Modifier)
            .selectable(selected = isSelected, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (charges.isNotEmpty() || isToday) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    isSelected -> palette.onSaffronContainer
                    isPast -> palette.textMuted.copy(alpha = 0.7f)
                    else -> palette.text
                },
            )
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(6.dp)) {
                charges.take(MAX_DOTS).forEach { charge ->
                    val color = if (charge.isPast) palette.textMuted.copy(alpha = 0.6f) else dotColor(charge)
                    Box(Modifier.size(6.dp).background(color, FoldShapes.pill))
                }
            }
        }
    }
}

private const val MAX_DOTS = 3
