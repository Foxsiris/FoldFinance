package com.foldfinance.app.ui.history

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Surface
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
import com.foldfinance.app.ui.transaction.PrimaryCurrency
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
 * Persistent filter bar above the list: search + sort, then pills «Период», «Тип», «Категории»,
 * «Сумма» and «Сбросить». An active pill is saffron, like every active choice in the app.
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
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(contentPadding), verticalAlignment = Alignment.CenterVertically) {
            SearchField(filter.query, actions::setQuery, Modifier.weight(1f))
            Spacer(Modifier.width(4.dp))
            SortButton(filter.sortValue, actions::setSort)
        }
        LazyRow(contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { PeriodPill(filter.period, actions::setPeriod) }
            item { DirectionPill(filter.directionValues, actions::toggleDirection) }
            if (showCategoryPill) item { CategoryPill(filter, categories, actions) }
            item { AmountPill(filter, actions::setAmountRange) }
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
    TextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier.heightIn(min = 52.dp),
        placeholder = { Text(stringResource(R.string.history_search), color = palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.textMuted) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.history_search_clear), tint = palette.textMuted)
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = FoldShapes.pill,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = palette.surfaceRaised,
            unfocusedContainerColor = palette.surfaceRaised,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = palette.saffron,
            focusedTextColor = palette.text,
            unfocusedTextColor = palette.text,
        ),
    )
}

@Composable
private fun SortButton(sort: TransactionSort, onSort: (TransactionSort) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = stringResource(R.string.history_sort), tint = FoldFinanceTheme.palette.text)
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
private fun AmountPill(filter: HistoryFilter, onRange: (Long?, Long?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val min = filter.minAmountMinor
    val max = filter.maxAmountMinor
    val label = when {
        min != null && max != null -> stringResource(R.string.history_filter_amount_range, MoneyFormat.format(min, PrimaryCurrency), MoneyFormat.format(max, PrimaryCurrency))
        min != null -> stringResource(R.string.history_filter_amount_from, MoneyFormat.format(min, PrimaryCurrency))
        max != null -> stringResource(R.string.history_filter_amount_to, MoneyFormat.format(max, PrimaryCurrency))
        else -> stringResource(R.string.history_filter_amount)
    }
    FilterPill(label = label, active = filter.hasAmountFilter, onClick = { open = true })
    if (open) {
        AmountRangeDialog(
            initialMin = min,
            initialMax = max,
            onDismiss = { open = false },
            onApply = { lo, hi ->
                open = false
                onRange(lo, hi)
            },
        )
    }
}

@Composable
private fun AmountRangeDialog(initialMin: Long?, initialMax: Long?, onDismiss: () -> Unit, onApply: (Long?, Long?) -> Unit) {
    var minText by rememberSaveable { mutableStateOf(initialMin?.let { AmountEntry.fromMinor(it) }.orEmpty()) }
    var maxText by rememberSaveable { mutableStateOf(initialMax?.let { AmountEntry.fromMinor(it) }.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_filter_amount_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AmountField(minText, { minText = sanitizeAmount(it) }, stringResource(R.string.history_filter_amount_min))
                AmountField(maxText, { maxText = sanitizeAmount(it) }, stringResource(R.string.history_filter_amount_max))
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
private fun AmountField(value: String, onValue: (String) -> Unit, label: String) {
    TextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        suffix = { Text(MoneyFormat.symbol(PrimaryCurrency)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        shape = FoldShapes.tile,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A filter pill: label + chevron; saffron when the filter is active. */
@Composable
fun FilterPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArrow: Boolean = true,
) {
    val palette = FoldFinanceTheme.palette
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        shape = FoldShapes.pill,
        color = if (active) palette.saffronContainer else palette.surfaceRaised,
        border = if (active) BorderStroke(1.dp, palette.saffron.copy(alpha = 0.5f)) else null,
    ) {
        Row(
            Modifier.heightIn(min = 40.dp).padding(start = 14.dp, end = if (showArrow) 8.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) palette.onSaffronContainer else palette.text, maxLines = 1)
            if (showArrow) {
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (active) palette.onSaffronContainer else palette.textMuted,
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
            { Icon(Icons.Outlined.Check, contentDescription = null, tint = palette.saffron) }
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
