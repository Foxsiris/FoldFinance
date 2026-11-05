package com.foldfinance.app.ui.ai.receipt

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldfinance.app.R
import com.foldfinance.app.ui.ai.AiProgress
import com.foldfinance.app.ai.features.common.FactFormat
import com.foldfinance.app.ai.features.receipt.ReceiptImages
import com.foldfinance.app.ai.features.receipt.SplitMode
import com.foldfinance.app.ai.features.receipt.SplitPlan
import com.foldfinance.app.ui.ai.AiCard
import com.foldfinance.app.ui.components.MoneyText
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.app.ui.transaction.title
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction

/**
 * «Чек» action for an operation's detail: choose a photo (Photo Picker, no permission) or take
 * one (system camera through FileProvider, no CAMERA permission), then review and split.
 * Shown only for expenses and only when the model accepts images.
 */
@Composable
fun ReceiptScanButton(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    viewModel: ReceiptViewModel = hiltViewModel(key = ReceiptViewModel.KEY),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val images = remember(context) { ReceiptImages(context.applicationContext) }
    var menu by remember { mutableStateOf(false) }
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) viewModel.recognize(transaction.id, uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = captureUri?.let(Uri::parse)
        captureUri = null
        if (saved && uri != null) viewModel.recognize(transaction.id, uri)
    }

    if (!state.imagesSupported || transaction.direction != Direction.EXPENSE) return
    Box(modifier) {
        OutlinedButton(onClick = { menu = true }) {
            Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.aif_receipt_button))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.aif_receipt_pick)) },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                onClick = {
                    menu = false
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.aif_receipt_take)) },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                onClick = {
                    menu = false
                    val file = images.newCaptureFile()
                    val uri = FileProvider.getUriForFile(context, images.authority(), file)
                    captureUri = uri.toString()
                    camera.launch(uri)
                },
            )
        }
    }
    ReceiptReviewSheet(viewModel)
}

/** The review as a bottom sheet over the current screen, while a receipt is being read or edited. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptReviewSheet(viewModel: ReceiptViewModel = hiltViewModel(key = ReceiptViewModel.KEY)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.phase == ReceiptPhase.IDLE || state.phase == ReceiptPhase.SAVED) return
    ModalBottomSheet(
        onDismissRequest = viewModel.actions::close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = FoldShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        ReceiptReviewContent(state, viewModel.actions, Modifier.navigationBarsPadding().imePadding())
    }
}

/** Stateless review: lines, Kotlin-computed sums against the operation, the split action. */
@Composable
fun ReceiptReviewContent(state: ReceiptUiState, actions: ReceiptActions, modifier: Modifier = Modifier) {
    val tx = state.transaction
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.aif_receipt_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                if (tx != null) {
                    Text(tx.title(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = actions::close) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.aif_close)) }
        }
        when (state.phase) {
            ReceiptPhase.READING -> {
                Text(stringResource(R.string.aif_receipt_reading), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AiProgress(Modifier.fillMaxWidth())
            }
            ReceiptPhase.UNREADABLE, ReceiptPhase.FAILED -> Text(
                stringResource(if (state.phase == ReceiptPhase.UNREADABLE) R.string.aif_receipt_unreadable else R.string.aif_receipt_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ReceiptPhase.REVIEW -> if (tx != null) Review(state, tx, actions)
            ReceiptPhase.IDLE, ReceiptPhase.SAVED -> Unit
        }
    }
}

@Composable
private fun Review(state: ReceiptUiState, tx: Transaction, actions: ReceiptActions) {
    val plan = state.plan ?: return
    Totals(state, plan, tx)
    state.lines.forEach { line -> LineEditor(line, state, actions) }
    TextButton(onClick = actions::add) {
        Icon(Icons.Outlined.Add, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.aif_receipt_add_line))
    }
    if (state.splitMode == SplitMode.NOTE) {
        Text(stringResource(R.string.aif_receipt_note_mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val note = noteText(plan, state.categories, tx)
    Button(onClick = { actions.save(note) }, enabled = state.canSave, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(stringResource(if (state.splitMode == SplitMode.CATEGORY_SPLIT) R.string.aif_receipt_split else R.string.aif_receipt_save_note))
    }
}

@Composable
private fun Totals(state: ReceiptUiState, plan: SplitPlan, tx: Transaction) {
    AiCard(title = stringResource(R.string.aif_receipt_check)) {
        TotalLine(stringResource(R.string.aif_receipt_items_total), plan.itemsTotalMinor, tx.currency)
        TotalLine(stringResource(R.string.aif_receipt_operation), tx.amountMinor, tx.currency)
        state.printedTotalMinor?.let { TotalLine(stringResource(R.string.aif_receipt_printed), it, tx.currency) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val (text, isError) = when {
            state.missingAmounts > 0 -> stringResource(R.string.aif_receipt_missing, state.missingAmounts) to true
            plan.remainderMinor < 0 -> stringResource(R.string.aif_receipt_over, FactFormat.money(-plan.remainderMinor, tx.currency)) to true
            plan.remainderMinor > 0 -> stringResource(R.string.aif_receipt_rest, FactFormat.money(plan.remainderMinor, tx.currency)) to false
            else -> stringResource(R.string.aif_receipt_match) to false
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        if (state.printedMismatch) {
            Text(stringResource(R.string.aif_receipt_printed_mismatch), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun TotalLine(label: String, amountMinor: Long, currency: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(amountMinor = amountMinor, currency = currency)
    }
}

@Composable
private fun LineEditor(line: EditableLine, state: ReceiptUiState, actions: ReceiptActions) {
    var menu by remember { mutableStateOf(false) }
    Surface(shape = FoldShapes.tile, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = line.name,
                    onValueChange = { actions.setName(line.id, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.aif_receipt_line_name)) },
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = line.amountText,
                    onValueChange = { actions.setAmount(line.id, it) },
                    modifier = Modifier.width(120.dp),
                    singleLine = true,
                    isError = state.amountOf(line) == null,
                    label = { Text(stringResource(R.string.aif_receipt_line_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                IconButton(onClick = { actions.remove(line.id) }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.aif_receipt_remove_line))
                }
            }
            Box {
                val category = state.categories.firstOrNull { it.id == line.categoryId }
                Surface(
                    shape = FoldShapes.pill,
                    color = if (line.categoryUncertain) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.clickable { menu = true },
                ) {
                    Text(
                        when {
                            line.categoryUncertain -> stringResource(R.string.aif_receipt_pick_category)
                            category != null -> category.name
                            else -> stringResource(R.string.tx_no_category)
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (line.categoryUncertain) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.tx_no_category)) }, onClick = { menu = false; actions.setCategory(line.id, null) })
                    state.categories.forEach { c ->
                        DropdownMenuItem(text = { Text(c.name) }, onClick = { menu = false; actions.setCategory(line.id, c.id) })
                    }
                }
            }
        }
    }
}

/** The breakdown for the note: «Продукты — 1 240,50 ₽; Хозтовары — 300 ₽; остаток — 50 ₽». Kotlin sums. */
@Composable
private fun noteText(plan: SplitPlan, categories: List<Category>, tx: Transaction): String {
    val none = stringResource(R.string.tx_no_category)
    val rest = stringResource(R.string.aif_receipt_note_rest)
    val parts = plan.parts.map { part ->
        val name = categories.firstOrNull { it.id == part.categoryId }?.name ?: none
        "$name — ${FactFormat.money(part.amountMinor, tx.currency)}"
    } + listOfNotNull(plan.remainderMinor.takeIf { it > 0 }?.let { "$rest — ${FactFormat.money(it, tx.currency)}" })
    return parts.joinToString("; ")
}
