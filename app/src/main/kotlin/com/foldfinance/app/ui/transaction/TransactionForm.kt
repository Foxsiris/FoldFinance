package com.foldfinance.app.ui.transaction

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.CategoryChip
import com.foldfinance.app.ui.components.MoneyStyle
import com.foldfinance.app.ui.components.moneyColor
import com.foldfinance.app.ui.components.moneyTextStyle
import com.foldfinance.app.ui.components.visual
import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldMotion
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.parser.Direction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Callbacks of [TransactionForm]; implemented by [TransactionFormViewModel]. */
interface TransactionFormActions {
    fun press(key: KeypadKey)
    fun setDirection(direction: Direction)
    fun toggleCategory(categoryId: Long)
    fun setMerchant(merchant: String)
    fun setNote(note: String)
    fun setOccurredAt(instant: Instant)
    fun save()
    fun close()
}

/**
 * Add / edit form: direction, the amount in big Unbounded digits, merchant, category chips,
 * date and time, note — and the keypad with «Сохранить» at the bottom, in the thumb zone.
 *
 * Stateless: everything comes from [state] (owned by [TransactionFormViewModel]).
 *
 * @param now «сейчас» for the date chip when the draft has no explicit time.
 * @param fillHeight `true` in a pane (keypad pinned to the bottom), `false` in a sheet (wraps content).
 */
@Composable
fun TransactionForm(
    state: TransactionFormState,
    actions: TransactionFormActions,
    now: Instant,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
) {
    val draft = state.draft
    val palette = FoldFinanceTheme.palette
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    var pickedDateEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val occurredAt = draft.occurredAtMillis?.let(Instant::ofEpochMilli) ?: now

    Column(modifier.then(if (fillHeight) Modifier.fillMaxHeight() else Modifier).padding(contentPadding)) {
        // One header row: the direction is the first choice, «×» closes. The title is for screen readers.
        val title = stringResource(if (state.mode is FormMode.Edit) R.string.tx_form_title_edit else R.string.tx_form_title_add)
        Row(
            Modifier.fillMaxWidth().semantics { heading(); paneTitle = title },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DirectionToggle(selected = draft.directionValue, onSelect = actions::setDirection, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = actions::close) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.tx_form_close), tint = palette.textMuted)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = fillHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AmountDisplay(text = draft.amountText, currency = draft.currency, direction = draft.directionValue)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormTextField(
                    value = draft.merchant,
                    onValueChange = actions::setMerchant,
                    label = stringResource(
                        if (draft.directionValue == Direction.INCOME) R.string.tx_form_merchant_income else R.string.tx_form_merchant,
                    ),
                    modifier = Modifier.weight(1f),
                )
                DateChip(
                    text = dateTimeLabel(occurredAt, now, zone),
                    onClick = { pickDate = true },
                )
            }
            if (state.categories.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.categories, key = { it.id }) { category ->
                        val visual = category.visual()
                        CategoryChip(
                            name = visual.name,
                            color = visual.color,
                            selected = category.id == draft.categoryId,
                            onClick = { actions.toggleCategory(category.id) },
                        )
                    }
                }
            }
            FormTextField(value = draft.note, onValueChange = actions::setNote, label = stringResource(R.string.tx_form_note))
        }
        Spacer(Modifier.height(12.dp))
        NumericKeypad(onKey = actions::press, showDecimal = MoneyFormat.fractionDigits(draft.currency) > 0, keyHeight = 52.dp)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = actions::save,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = FoldShapes.pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.saffron,
                contentColor = palette.onSaffron,
                disabledContainerColor = palette.surfaceRaised,
                disabledContentColor = palette.textMuted,
            ),
        ) {
            Text(stringResource(R.string.tx_form_save), style = MaterialTheme.typography.titleMedium)
        }
    }

    if (pickDate) {
        FormDatePicker(
            initial = occurredAt.atZone(zone).toLocalDate(),
            onDismiss = { pickDate = false },
            onPicked = { date ->
                pickDate = false
                pickedDateEpochDay = date.toEpochDay()
                pickTime = true
            },
        )
    }
    if (pickTime) {
        val date = pickedDateEpochDay?.let(LocalDate::ofEpochDay) ?: occurredAt.atZone(zone).toLocalDate()
        FormTimePicker(
            initial = occurredAt.atZone(zone).toLocalTime(),
            onDismiss = { pickTime = false },
            onPicked = { time ->
                pickTime = false
                actions.setOccurredAt(date.atTime(time).atZone(zone).toInstant())
            },
        )
    }
}

/** Two-segment pill «Расход / Доход». */
@Composable
fun DirectionToggle(selected: Direction, onSelect: (Direction) -> Unit, modifier: Modifier = Modifier) {
    val palette = FoldFinanceTheme.palette
    val options = listOf(Direction.EXPENSE, Direction.INCOME)
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(palette.surfaceRaised, FoldShapes.pill)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        options.forEach { option ->
            val isSelected = option == selected || (option == Direction.EXPENSE && selected !in options)
            val container by animateColorAsState(
                if (isSelected) palette.saffronContainer else palette.surfaceRaised,
                FoldMotion.effects(),
                label = "direction",
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(FoldShapes.pill)
                    .background(container)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.label(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) palette.onSaffronContainer else palette.textMuted,
                )
            }
        }
    }
}

/** The typed amount: integer part in Unbounded, comma and kopecks smaller and muted, like [com.foldfinance.app.ui.components.MoneyText]. */
@Composable
private fun AmountDisplay(text: String, currency: String, direction: Direction) {
    val palette = FoldFinanceTheme.palette
    val style = moneyTextStyle(MoneyStyle.Display)
    val (integer, fraction) = AmountEntry.displayParts(text)
    val empty = text.isEmpty()
    val main = if (empty) palette.textMuted.copy(alpha = 0.6f) else moneyColor(direction)
    val minor = if (empty) main else palette.textMuted
    val spoken = MoneyFormat.format(AmountEntry.toMinor(text, MoneyFormat.fractionDigits(currency)), currency)
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = main)) {
                integer.forEach { ch ->
                    // Same widened group gap as MoneyText: the narrow no-break space alone is too tight.
                    if (ch == MoneyFormat.NARROW_NBSP) {
                        withStyle(SpanStyle(letterSpacing = MoneyStyle.Display.groupSpacing)) { append(ch) }
                    } else {
                        append(ch)
                    }
                }
            }
            withStyle(SpanStyle(color = minor, fontSize = style.fontSize * MoneyStyle.Display.minorScale)) {
                if (fraction != null) append(fraction)
                append(MoneyFormat.NARROW_NBSP)
                append(MoneyFormat.symbol(currency))
            }
        },
        style = style,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics { contentDescription = spoken },
    )
}

@Composable
private fun FormTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    val palette = FoldFinanceTheme.palette
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = FoldShapes.tile,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = palette.surfaceRaised,
            unfocusedContainerColor = palette.surfaceRaised,
            focusedIndicatorColor = palette.surfaceRaised,
            unfocusedIndicatorColor = palette.surfaceRaised,
            focusedLabelColor = palette.saffron,
            unfocusedLabelColor = palette.textMuted,
            cursorColor = palette.saffron,
            focusedTextColor = palette.text,
            unfocusedTextColor = palette.text,
        ),
        modifier = modifier,
    )
}

@Composable
private fun DateChip(text: String, onClick: () -> Unit) {
    val palette = FoldFinanceTheme.palette
    Surface(onClick = onClick, shape = FoldShapes.tile, color = palette.surfaceRaised) {
        Row(
            Modifier.heightIn(min = 56.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = palette.saffron, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = palette.text)
        }
    }
}

@Composable
private fun dateTimeLabel(instant: Instant, now: Instant, zone: ZoneId): String {
    val date = instant.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    val day = when (date) {
        today -> stringResource(R.string.tx_day_today)
        today.minusDays(1) -> stringResource(R.string.tx_day_yesterday)
        else -> formatDate(date, today)
    }
    return stringResource(R.string.tx_date_time, day, formatTime(instant, zone))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormDatePicker(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) else onDismiss()
            }) { Text(stringResource(R.string.tx_form_next)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tx_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormTimePicker(initial: LocalTime, onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tx_form_time)) },
        text = { TimeInput(state = state) },
        confirmButton = { TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.tx_form_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tx_cancel)) } },
    )
}
