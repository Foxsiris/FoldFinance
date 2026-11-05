package com.foldfinance.app.ui.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.foldfinance.app.R
import com.foldfinance.app.ui.ai.LocalAiHooks
import com.foldfinance.app.ui.components.CategoryChip
import com.foldfinance.app.ui.components.IconCircleButton
import com.foldfinance.app.ui.components.MoneyStyle
import com.foldfinance.app.ui.components.PrimaryButton
import com.foldfinance.app.ui.components.SegmentedControl
import com.foldfinance.app.ui.components.moneyColor
import com.foldfinance.app.ui.components.moneyLabel
import com.foldfinance.app.ui.components.moneyTextStyle
import com.foldfinance.app.ui.components.visual
import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.app.ui.theme.FoldFinanceTheme
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

    /** Replace the draft with [prefill] in add mode (voice input). No-op by default (previews). */
    fun openAdd(prefill: TransactionDraft) = Unit
}

/** Text field of the form opened by its chip. */
private enum class FormField { Merchant, Note }

/**
 * Add / edit form (Add mockup): the title with «×», «Расход / Доход / Перевод», the typed amount
 * with a lime cursor, chips for date, place and note, category chips — and the 3 × 4 keypad with
 * «Сохранить · 1 250 ₽» at the bottom, in the thumb zone.
 *
 * Stateless: everything comes from [state] (owned by [TransactionFormViewModel]); only which text
 * field is open and the date/time dialogs are local (saveable).
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
    var openField by rememberSaveable { mutableStateOf<FormField?>(null) }
    val occurredAt = draft.occurredAtMillis?.let(Instant::ofEpochMilli) ?: now
    val digits = MoneyFormat.fractionDigits(draft.currency)
    val merchantLabel = stringResource(if (draft.directionValue == Direction.INCOME) R.string.tx_form_merchant_income else R.string.tx_form_merchant)
    val noteLabel = stringResource(R.string.tx_form_note)

    Column(modifier.then(if (fillHeight) Modifier.fillMaxHeight() else Modifier).padding(contentPadding)) {
        val title = stringResource(if (state.mode is FormMode.Edit) R.string.tx_form_title_edit else R.string.tx_form_title_add)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).semantics { paneTitle = title },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                color = palette.text,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            // Voice cash input (local model + on-device speech): hidden unless the model is ready.
            val voice = LocalAiHooks.current.voiceInput
            if (state.mode == FormMode.Add && voice != null) {
                voice { prefill -> actions.openAdd(prefill) }
                Spacer(Modifier.width(8.dp))
            }
            IconCircleButton(
                icon = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.tx_form_close),
                onClick = actions::close,
            )
        }
        Spacer(Modifier.height(12.dp))
        DirectionToggle(selected = draft.directionValue, onSelect = actions::setDirection)
        Column(
            modifier = Modifier
                .weight(1f, fill = fillHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AmountDisplay(text = draft.amountText, currency = draft.currency, direction = draft.directionValue)
            if (draft.amountUncertain) {
                Text(
                    stringResource(R.string.ai_voice_check_amount),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.review,
                    textAlign = TextAlign.Center,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FormChip(
                    text = dateTimeLabel(occurredAt, now, zone),
                    icon = Icons.Outlined.CalendarMonth,
                    onClick = { pickDate = true },
                )
                FormChip(
                    text = draft.merchant.ifBlank { merchantLabel },
                    icon = Icons.Outlined.Storefront,
                    active = openField == FormField.Merchant || draft.merchant.isNotBlank(),
                    onClick = { openField = if (openField == FormField.Merchant) null else FormField.Merchant },
                    modifier = Modifier.weight(1f, fill = false),
                )
                FormChip(
                    text = draft.note.ifBlank { noteLabel },
                    icon = Icons.Outlined.EditNote,
                    active = openField == FormField.Note || draft.note.isNotBlank(),
                    onClick = { openField = if (openField == FormField.Note) null else FormField.Note },
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            when (openField) {
                FormField.Merchant -> FormTextField(
                    value = draft.merchant,
                    onValueChange = actions::setMerchant,
                    label = merchantLabel,
                    onDone = { openField = null },
                )
                FormField.Note -> FormTextField(
                    value = draft.note,
                    onValueChange = actions::setNote,
                    label = noteLabel,
                    onDone = { openField = null },
                )
                null -> Unit
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
                            height = 40.dp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        NumericKeypad(onKey = actions::press, showDecimal = digits > 0, keyHeight = 48.dp)
        Spacer(Modifier.height(10.dp))
        val amount = moneyLabel(draft.amountMinor, draft.currency)
        PrimaryButton(
            text = if (draft.amountMinor > 0) stringResource(R.string.tx_form_save_amount, amount) else stringResource(R.string.tx_form_enter_amount),
            onClick = actions::save,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth(),
        )
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

/**
 * Segmented «Расход / Доход / Перевод» (a transfer between own cards). A refund being edited gets
 * its own fourth segment, so editing never silently changes what the bank said.
 */
@Composable
fun DirectionToggle(selected: Direction, onSelect: (Direction) -> Unit, modifier: Modifier = Modifier) {
    val options = buildList {
        add(Direction.EXPENSE)
        add(Direction.INCOME)
        add(Direction.INTERNAL_TRANSFER)
        if (selected == Direction.REFUND) add(Direction.REFUND)
    }
    val labels = options.map { option ->
        if (option == Direction.INTERNAL_TRANSFER) stringResource(R.string.tx_form_transfer) else option.label()
    }
    SegmentedControl(
        labels = labels,
        selected = options.indexOf(selected),
        onSelect = { onSelect(options[it]) },
        modifier = modifier,
        height = 42.dp,
        role = Role.RadioButton,
        description = stringResource(R.string.tx_form_types),
    )
}

/**
 * The typed amount (Add mockup): 56 sp digits, a lime cursor, the currency sign quiet; kopecks
 * smaller. Empty input shows a dim «0».
 */
@Composable
private fun AmountDisplay(text: String, currency: String, direction: Direction) {
    val palette = FoldFinanceTheme.palette
    val style = moneyTextStyle(MoneyStyle.Display).copy(fontSize = 56.sp, lineHeight = 60.sp)
    val (integer, fraction) = AmountEntry.displayParts(text)
    val empty = text.isEmpty()
    val main = when {
        empty -> palette.textDisabled.copy(alpha = 0.7f)
        direction == Direction.INCOME -> moneyColor(direction)
        else -> palette.text
    }
    val spoken = MoneyFormat.format(AmountEntry.toMinor(text, MoneyFormat.fractionDigits(currency)), currency)
    Row(
        Modifier
            .fillMaxWidth()
            .height(80.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = main)) {
                    integer.forEach { ch ->
                        if (ch == MoneyFormat.NARROW_NBSP) {
                            withStyle(SpanStyle(letterSpacing = 0.03.em)) { append(ch) }
                        } else {
                            append(ch)
                        }
                    }
                }
                if (fraction != null) withStyle(SpanStyle(color = palette.textMuted, fontSize = 0.6.em)) { append(fraction) }
            },
            style = style,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
            autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 56.sp, stepSize = 1.sp),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(2.dp).height(44.dp).background(palette.accent, FoldShapes.pill))
        Spacer(Modifier.width(8.dp))
        Text(
            MoneyFormat.symbol(currency),
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, fontWeight = FontWeight.Medium),
            color = palette.textDisabled,
        )
    }
}

/** Chip of the form: date, place, note (Add mockup: 36 dp, 12 dp corners, hairline border). */
@Composable
private fun FormChip(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    val palette = FoldFinanceTheme.palette
    Box(modifier.heightIn(min = 48.dp).clip(FoldShapes.chip).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .height(36.dp)
                .widthIn(max = 220.dp)
                .clip(FoldShapes.chip)
                .border(1.dp, if (active) palette.accentLine else palette.outline, FoldShapes.chip)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = if (active) palette.accentContent else palette.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
                color = palette.textSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val palette = FoldFinanceTheme.palette
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = FoldShapes.tile,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = palette.surfaceRaised,
            unfocusedContainerColor = palette.surfaceRaised,
            focusedIndicatorColor = palette.surfaceRaised,
            unfocusedIndicatorColor = palette.surfaceRaised,
            focusedLabelColor = palette.accentContent,
            unfocusedLabelColor = palette.textMuted,
            cursorColor = palette.accentContent,
            focusedTextColor = palette.text,
            unfocusedTextColor = palette.text,
        ),
        modifier = modifier,
    )
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
