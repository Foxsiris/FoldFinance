package com.foldfinance.app.ui.history

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.visual
import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.app.ui.transaction.AmountEntry
import com.foldfinance.app.prefs.AppSettings
import com.foldfinance.app.ui.transaction.label
import com.foldfinance.core.data.repository.TransactionSort
import com.foldfinance.core.model.Category
import com.foldfinance.parser.Direction

/** Callbacks of the History filter bar. */
interface HistoryFilterActions {
    fun setQuery(query: String)
    fun setPeriod(period: HistoryPeriod)
    fun setSort(sort: TransactionSort)
    fun toggleDirection(direction: Direction)
    fun toggleCategoryFilter(categoryId: Long)
    fun toggleUncategorized()
    fun clearCategoryFilter()
    fun setAmountRange(min: Long?, max: Long?)
    fun resetFilters()
}

/**
 * Persistent filter bar above the list (History mockup): the search field, then pills «Период»,
 * «Тип», «Категории», «Сумма», the sort button and «Сбросить». An active pill gets the lime tint
 * and border, like every active choice in the app.
 *
 * @param showCategoryPill `false` when the categories are shown as a chip strip instead (inner screen).
 */
@Composable
fun HistoryFilterBar(
    filter: HistoryFilter,
    categories: List<Category>,
    actions: HistoryFilterActions,
    modifier: Modifier = Modifier,
    showCategoryPill: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    currency: String = AppSettings.DEFAULT_CURRENCY,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(filter.query, actions::setQuery, Modifier.padding(contentPadding).fillMaxWidth())
        LazyRow(contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            item { PeriodPill(filter.period, actions::setPeriod) }
            item { DirectionPill(filter.directionValues, actions::toggleDirection) }
            if (showCategoryPill) item { CategoryPill(filter, categories, actions) }
            item { AmountPill(filter, currency, actions::setAmountRange) }
            item { SortButton(filter.sortValue, actions::setSort) }
            if (filter.isNarrowed) {
                item {
                    FilterPill(
                        label = stringResource(R.string.history_filter_reset),
                        active = false,
                        showArrow = false,
                        onClick = actions::resetFilters,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier) {
    val palette = FoldFinanceTheme.palette
    val placeholder = stringResource(R.string.history_search)
    Row(
        modifier
            .heightIn(min = 46.dp)
            .clip(FoldShapes.tile)
            .background(palette.surfaceRaised)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = palette.text),
                cursorBrush = SolidColor(palette.accentContent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQuery("") }) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.history_search_clear), tint = palette.textMuted)
            }
        } else {
            Spacer(Modifier.width(10.dp))
        }
    }
}

/** Sort: an outlined square button at the end of the pills (History mockup). */
@Composable
private fun SortButton(sort: TransactionSort, onSort: (TransactionSort) -> Unit) {
    val palette = FoldFinanceTheme.palette
    var open by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(36.dp)
                .clip(FoldShapes.chip)
                .border(1.dp, if (sort != TransactionSort.DATE_DESC) palette.accentLine else palette.outline, FoldShapes.chip)
                .background(if (sort != TransactionSort.DATE_DESC) palette.accentSoft else Color.Transparent)
                .clickable(role = Role.Button) { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = stringResource(R.string.history_sort), tint = palette.textSoft, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TransactionSort.entries.forEach { option ->
                CheckItem(text = sortLabel(option), checked = option == sort, onClick = {
                    open = false
                    onSort(option)
                })
            }
        }
    }
}

@Composable
private fun PeriodPill(period: HistoryPeriod, onPeriod: (HistoryPeriod) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        FilterPill(
            label = if (period == HistoryPeriod.ALL) stringResource(R.string.history_filter_period) else periodLabel(period),
            active = period != HistoryPeriod.ALL,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            HistoryPeriod.entries.forEach { option ->
                CheckItem(text = periodLabel(option), checked = option == period, onClick = {
                    open = false
                    onPeriod(option)
                })
            }
        }
    }
}

@Composable
private fun DirectionPill(selected: Set<Direction>, onToggle: (Direction) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val label = when (selected.size) {
        0 -> stringResource(R.string.history_filter_type)
        1 -> selected.first().label()
        else -> stringResource(R.string.history_filter_type_count, selected.size)
    }
    Box {
        FilterPill(label = label, active = selected.isNotEmpty(), onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Direction.entries.forEach { direction ->
                CheckItem(text = direction.label(), checked = direction in selected, onClick = { onToggle(direction) })
            }
        }
    }
}

@Composable
private fun CategoryPill(filter: HistoryFilter, categories: List<Category>, actions: HistoryFilterActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    val count = filter.categoryIds.size + if (filter.uncategorized) 1 else 0
    val label = when {
        count == 0 -> stringResource(R.string.history_filter_category)
        count == 1 && filter.uncategorized -> stringResource(R.string.tx_no_category)
        count == 1 -> categories.firstOrNull { it.id in filter.categoryIds }?.name ?: stringResource(R.string.history_filter_category)
        else -> stringResource(R.string.history_filter_category_count, count)
    }
    Box {
        FilterPill(label = label, active = count > 0, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            categories.forEach { category ->
                val visual = category.visual()
                CheckItem(
                    text = visual.name,
                    checked = category.id in filter.categoryIds,
                    dot = visual.color,
                    onClick = { actions.toggleCategoryFilter(category.id) },
                )
            }
            CheckItem(text = stringResource(R.string.tx_no_category), checked = filter.uncategorized, onClick = actions::toggleUncategorized)
            if (count > 0) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.history_filter_clear), color = FoldFinanceTheme.palette.saffron) },
                    onClick = {
                        open = false
                        actions.clearCategoryFilter()
                    },
                )
            }
        }
    }
}

@Composable
private fun AmountPill(filter: HistoryFilter, currency: String, onRange: (Long?, Long?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val min = filter.minAmountMinor
    val max = filter.maxAmountMinor
    val label = when {
        min != null && max != null -> stringResource(R.string.history_filter_amount_range, MoneyFormat.format(min, currency), MoneyFormat.format(max, currency))
        min != null -> stringResource(R.string.history_filter_amount_from, MoneyFormat.format(min, currency))
        max != null -> stringResource(R.string.history_filter_amount_to, MoneyFormat.format(max, currency))
        else -> stringResource(R.string.history_filter_amount)
    }
    FilterPill(label = label, active = filter.hasAmountFilter, onClick = { open = true })
    if (open) {
        AmountRangeDialog(
            initialMin = min,
            initialMax = max,
            currency = currency,
            onDismiss = { open = false },
            onApply = { lo, hi ->
                open = false
                onRange(lo, hi)
            },
        )
    }
}

@Composable
private fun AmountRangeDialog(initialMin: Long?, initialMax: Long?, currency: String, onDismiss: () -> Unit, onApply: (Long?, Long?) -> Unit) {
    var minText by rememberSaveable { mutableStateOf(initialMin?.let { AmountEntry.fromMinor(it) }.orEmpty()) }
    var maxText by rememberSaveable { mutableStateOf(initialMax?.let { AmountEntry.fromMinor(it) }.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_filter_amount_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AmountField(minText, { minText = sanitizeAmount(it) }, stringResource(R.string.history_filter_amount_min), currency)
                AmountField(maxText, { maxText = sanitizeAmount(it) }, stringResource(R.string.history_filter_amount_max), currency)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(AmountEntry.toMinor(minText).takeIf { it > 0 }, AmountEntry.toMinor(maxText).takeIf { it > 0 })
            }) { Text(stringResource(R.string.history_filter_apply)) }
        },
        dismissButton = {
            TextButton(onClick = { onApply(null, null) }) { Text(stringResource(R.string.history_filter_clear)) }
        },
    )
}

/** Keeps digits and one comma (a dot is read as a comma), at most two decimals. */
internal fun sanitizeAmount(input: String): String {
    val normalized = input.replace('.', ',').filter { it.isDigit() || it == ',' }
    val comma = normalized.indexOf(',')
    if (comma < 0) return normalized.take(AmountEntry.MAX_INTEGER_DIGITS)
    val integer = normalized.substring(0, comma).take(AmountEntry.MAX_INTEGER_DIGITS)
    val fraction = normalized.substring(comma + 1).replace(",", "").take(2)
    return "$integer,$fraction"
}

@Composable
private fun AmountField(value: String, onValue: (String) -> Unit, label: String, currency: String) {
    TextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        suffix = { Text(MoneyFormat.symbol(currency)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        shape = FoldShapes.tile,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A filter pill: label + chevron, 36 dp (48 dp touch); lime tint and border when the filter is active. */
@Composable
fun FilterPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArrow: Boolean = true,
) {
    val palette = FoldFinanceTheme.palette
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(36.dp)
                .clip(FoldShapes.chip)
                .background(if (active) palette.accentSoft else Color.Transparent)
                .border(1.dp, if (active) palette.accentLine else palette.outline, FoldShapes.chip)
                .padding(start = 12.dp, end = if (showArrow) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (active) FontWeight.Medium else FontWeight.Normal),
                color = if (active) palette.text else palette.textSoft,
                maxLines = 1,
            )
            if (showArrow) {
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (active) palette.text else palette.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun CheckItem(text: String, checked: Boolean, onClick: () -> Unit, dot: Color? = null) {
    val palette = FoldFinanceTheme.palette
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge, color = palette.text) },
        onClick = onClick,
        leadingIcon = dot?.let { color -> { Box(Modifier.size(10.dp).background(color, FoldShapes.pill)) } },
        trailingIcon = if (checked) {
            { Icon(Icons.Outlined.Check, contentDescription = null, tint = palette.accentContent) }
        } else {
            null
        },
    )
}

@Composable
fun periodLabel(period: HistoryPeriod): String = stringResource(
    when (period) {
        HistoryPeriod.ALL -> R.string.history_period_all
        HistoryPeriod.TODAY -> R.string.history_period_today
        HistoryPeriod.WEEK -> R.string.history_period_week
        HistoryPeriod.MONTH -> R.string.history_period_month
        HistoryPeriod.YEAR -> R.string.history_period_year
    },
)

@Composable
private fun sortLabel(sort: TransactionSort): String = stringResource(
    when (sort) {
        TransactionSort.DATE_DESC -> R.string.history_sort_date_desc
        TransactionSort.DATE_ASC -> R.string.history_sort_date_asc
        TransactionSort.AMOUNT_DESC -> R.string.history_sort_amount_desc
        TransactionSort.AMOUNT_ASC -> R.string.history_sort_amount_asc
    },
)
