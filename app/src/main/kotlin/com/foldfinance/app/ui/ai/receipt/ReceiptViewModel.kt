package com.foldfinance.app.ui.ai.receipt

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldfinance.app.ai.LocalAi
import com.foldfinance.app.ai.features.receipt.ReceiptImages
import com.foldfinance.app.ai.features.receipt.ReceiptRecognizer
import com.foldfinance.app.ai.features.receipt.ReceiptResult
import com.foldfinance.app.ai.features.receipt.ReceiptSplitPlanner
import com.foldfinance.app.ai.features.receipt.ReceiptSplitStore
import com.foldfinance.app.ai.features.receipt.SplitMode
import com.foldfinance.app.ai.features.receipt.SplitPlan
import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.app.ui.transaction.AmountEntry
import com.foldfinance.core.data.repository.CategoryRepository
import com.foldfinance.core.data.repository.TransactionRepository
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * An editable receipt line. [amountText] is keypad-style text (`"189,9"`), converted exactly
 * with [AmountEntry]; empty — not entered.
 */
@Serializable
data class EditableLine(
    val id: Long,
    val name: String,
    val amountText: String,
    val categoryId: Long?,
    val categoryUncertain: Boolean = false,
)

enum class ReceiptPhase { IDLE, READING, REVIEW, UNREADABLE, FAILED, SAVED }

/**
 * Everything the review screen shows. Sums are computed here, in Kotlin.
 *
 * @property printedTotalMinor «ИТОГО» as the model read it from the photo, for a cross-check only.
 */
data class ReceiptUiState(
    val phase: ReceiptPhase = ReceiptPhase.IDLE,
    val imagesSupported: Boolean = false,
    val transaction: Transaction? = null,
    val lines: List<EditableLine> = emptyList(),
    val categories: List<Category> = emptyList(),
    val printedTotalMinor: Long? = null,
    val splitMode: SplitMode = SplitMode.NOTE,
    val saving: Boolean = false,
) {
    private val digits: Int get() = MoneyFormat.fractionDigits(transaction?.currency ?: "RUB")

    fun amountOf(line: EditableLine): Long? = AmountEntry.toMinor(line.amountText, digits).takeIf { it > 0 }

    val plan: SplitPlan? get() = transaction?.let { tx ->
        ReceiptSplitPlanner.plan(tx, lines.map { ReceiptSplitPlanner.Line(it.name, amountOf(it), it.categoryId) })
    }

    val missingAmounts: Int get() = lines.count { amountOf(it) == null }

    /** Lines disagree with the printed total. */
    val printedMismatch: Boolean get() = printedTotalMinor != null && plan != null && plan!!.itemsTotalMinor != printedTotalMinor

    val canSave: Boolean get() = !saving && missingAmounts == 0 && lines.none { it.categoryUncertain } && plan?.isValid == true
}

/** What the review screen can do; no-ops by default (screenshots). */
open class ReceiptActions {
    open fun setName(id: Long, name: String) = Unit
    open fun setAmount(id: Long, text: String) = Unit
    open fun setCategory(id: Long, categoryId: Long?) = Unit
    open fun remove(id: Long) = Unit
    open fun add() = Unit
    open fun save(noteText: String) = Unit
    open fun close() = Unit
}

/**
 * Receipt flow (п.5): photo → model → editable lines → split of the selected operation.
 * The operation id, phase and edited lines live in [SavedStateHandle] (survive folding).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReceiptViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val recognizer: ReceiptRecognizer,
    private val images: ReceiptImages,
    private val store: ReceiptSplitStore,
    ai: LocalAi,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private val linesSerializer = ListSerializer(EditableLine.serializer())
    private val saving = MutableStateFlow(false)

    val actions: ReceiptActions = object : ReceiptActions() {
        override fun setName(id: Long, name: String) = editLine(id) { it.copy(name = name.take(80)) }
        override fun setAmount(id: Long, text: String) = editLine(id) { it.copy(amountText = sanitizeAmount(text, it.amountText)) }
        override fun setCategory(id: Long, categoryId: Long?) = editLine(id) { it.copy(categoryId = categoryId, categoryUncertain = false) }
        override fun remove(id: Long) = setLines(lines().filterNot { it.id == id })
        override fun add() = setLines(lines() + EditableLine((lines().maxOfOrNull { it.id } ?: 0L) + 1, "", "", null, categoryUncertain = true))
        override fun save(noteText: String) = this@ReceiptViewModel.save(noteText)
        override fun close() = this@ReceiptViewModel.close()
    }

    private val transaction = handle.getStateFlow(KEY_TX, 0L)
        .flatMapLatest { id -> if (id > 0) transactions.observeById(id) else flowOf(null) }

    private val edits = combine(
        handle.getStateFlow(KEY_PHASE, ReceiptPhase.IDLE.name),
        handle.getStateFlow(KEY_LINES, ""),
        handle.getStateFlow(KEY_PRINTED, 0L),
        saving,
    ) { phase, lines, printed, isSaving -> Edits(ReceiptPhase.valueOf(phase), decode(lines), printed.takeIf { it > 0 }, isSaving) }

    val state: StateFlow<ReceiptUiState> = combine(edits, transaction, categories.observeAll(), ai.supportsImages) { e, tx, cats, supported ->
        ReceiptUiState(
            phase = e.phase,
            imagesSupported = supported,
            transaction = tx,
            lines = e.lines,
            categories = cats,
            printedTotalMinor = e.printed,
            splitMode = store.mode,
            saving = e.saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiptUiState())

    /** Reads the photo at [uri] for operation [transactionId]. */
    fun recognize(transactionId: Long, uri: Uri) {
        handle[KEY_TX] = transactionId
        handle[KEY_PHASE] = ReceiptPhase.READING.name
        viewModelScope.launch {
            val bitmap = images.load(uri)
            val result = if (bitmap == null) ReceiptResult.Unreadable else recognizer.recognize(bitmap, categories.getAll())
            bitmap?.recycle()
            images.clearCaptures()
            when (result) {
                is ReceiptResult.Read -> {
                    val tx = transactions.observeById(transactionId).first()
                    val digits = MoneyFormat.fractionDigits(tx?.currency ?: "RUB")
                    setLines(
                        result.scan.lines.mapIndexed { i, line ->
                            EditableLine(
                                id = i + 1L,
                                name = line.name,
                                amountText = line.amountMinor?.let { AmountEntry.fromMinor(it, digits) }.orEmpty(),
                                categoryId = line.categoryId,
                                categoryUncertain = line.categoryUncertain,
                            )
                        },
                    )
                    handle[KEY_PRINTED] = result.scan.printedTotalMinor ?: 0L
                    handle[KEY_PHASE] = ReceiptPhase.REVIEW.name
                }
                ReceiptResult.Unreadable -> handle[KEY_PHASE] = ReceiptPhase.UNREADABLE.name
                is ReceiptResult.Failed -> handle[KEY_PHASE] = ReceiptPhase.FAILED.name
            }
        }
    }

    fun save(noteText: String) {
        val current = state.value
        val plan = current.plan ?: return
        if (!current.canSave) return
        saving.value = true
        viewModelScope.launch {
            try {
                store.save(plan, noteText)
                close()
                handle[KEY_PHASE] = ReceiptPhase.SAVED.name
            } finally {
                saving.value = false
            }
        }
    }

    fun close() {
        handle[KEY_PHASE] = ReceiptPhase.IDLE.name
        handle[KEY_LINES] = ""
        handle[KEY_PRINTED] = 0L
        viewModelScope.launch { images.clearCaptures() }
    }

    fun acknowledgeSaved() {
        if (handle.get<String>(KEY_PHASE) == ReceiptPhase.SAVED.name) handle[KEY_PHASE] = ReceiptPhase.IDLE.name
    }

    private data class Edits(val phase: ReceiptPhase, val lines: List<EditableLine>, val printed: Long?, val saving: Boolean)

    private fun lines(): List<EditableLine> = decode(handle[KEY_LINES] ?: "")

    private fun setLines(lines: List<EditableLine>) {
        handle[KEY_LINES] = json.encodeToString(linesSerializer, lines.take(MAX_LINES))
    }

    private inline fun editLine(id: Long, change: (EditableLine) -> EditableLine) = setLines(lines().map { if (it.id == id) change(it) else it })

    private fun decode(text: String): List<EditableLine> =
        if (text.isEmpty()) emptyList() else runCatching { json.decodeFromString(linesSerializer, text) }.getOrDefault(emptyList())

    companion object {
        const val KEY = "ai_receipt"
        private const val KEY_TX = "receipt.tx"
        private const val KEY_PHASE = "receipt.phase"
        private const val KEY_LINES = "receipt.lines"
        private const val KEY_PRINTED = "receipt.printed"
        private const val MAX_LINES = 80

        /** Keeps typed text a valid amount: digits, one comma (a dot becomes a comma), ≤ 2 kopeck digits. */
        fun sanitizeAmount(text: String, previous: String): String {
            val t = text.replace('.', ',').filter { it.isDigit() || it == ',' }
            val comma = t.indexOf(',')
            if (comma >= 0 && (t.indexOf(',', comma + 1) >= 0 || t.length - comma - 1 > 2)) return previous
            if (t.substringBefore(',').length > AmountEntry.MAX_INTEGER_DIGITS) return previous
            return t
        }
    }
}
