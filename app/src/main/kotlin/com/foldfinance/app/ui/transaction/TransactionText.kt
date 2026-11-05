package com.foldfinance.app.ui.transaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.CategoryVisual
import com.foldfinance.app.ui.components.categoryVisual
import com.foldfinance.app.ui.components.visual
import com.foldfinance.app.ui.format.RuLocale
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.DefaultCategories
import com.foldfinance.core.model.Source
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", RuLocale)
private val DayMonthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", RuLocale)
private val DayMonthYearFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", RuLocale)

/** `14:05` in [zone]. */
fun formatTime(instant: Instant, zone: ZoneId): String = TimeFormatter.format(instant.atZone(zone))

/** `5 ноября` (this year) or `5 ноября 2025`. */
fun formatDate(date: LocalDate, today: LocalDate): String =
    if (date.year == today.year) DayMonthFormatter.format(date) else DayMonthYearFormatter.format(date)

/** Day header of a grouped list: «Сегодня», «Вчера», «3 ноября, вторник». */
@Composable
fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.tx_day_today)
    today.minusDays(1) -> stringResource(R.string.tx_day_yesterday)
    else -> stringResource(
        R.string.tx_day_with_weekday,
        formatDate(date, today),
        date.dayOfWeek.getDisplayName(TextStyle.FULL, RuLocale),
    )
}

/**
 * What the list shows as the title of an operation: the merchant's display name
 * ([Transaction.displayMerchant]: the user's or the model's name, else the raw one), or what kind of
 * operation it is. Search and category rules keep working on the raw name.
 */
@Composable
@ReadOnlyComposable
fun Transaction.title(): String = displayMerchant?.takeIf { it.isNotBlank() } ?: stringResource(
    when {
        direction == Direction.INTERNAL_TRANSFER -> R.string.tx_title_internal_transfer
        kind == Kind.TRANSFER && direction == Direction.INCOME -> R.string.tx_title_transfer_in
        kind == Kind.TRANSFER -> R.string.tx_title_transfer_out
        kind == Kind.TOP_UP -> R.string.tx_title_top_up
        kind == Kind.CASH_WITHDRAWAL -> R.string.tx_title_cash
        kind == Kind.FEE -> R.string.tx_title_fee
        direction == Direction.REFUND -> R.string.tx_title_refund
        direction == Direction.INCOME -> R.string.tx_title_income
        else -> R.string.tx_title_expense
    },
)

/** Visual of the category of an operation; operations without a category get a neutral tile. */
@Composable
@ReadOnlyComposable
fun categoryVisualOrNone(category: Category?): CategoryVisual =
    category?.visual() ?: categoryVisual(
        key = DefaultCategories.OTHER_KEY,
        name = stringResource(R.string.tx_no_category),
        iconKey = "category",
    )

@Composable
@ReadOnlyComposable
fun Direction.label(): String = stringResource(
    when (this) {
        Direction.EXPENSE -> R.string.tx_direction_expense
        Direction.INCOME -> R.string.tx_direction_income
        Direction.REFUND -> R.string.tx_direction_refund
        Direction.INTERNAL_TRANSFER -> R.string.tx_direction_internal
    },
)

@Composable
@ReadOnlyComposable
fun Source.label(): String = stringResource(
    when (this) {
        Source.SMS -> R.string.tx_source_sms
        Source.PUSH -> R.string.tx_source_push
        Source.MANUAL -> R.string.tx_source_manual
    },
)
