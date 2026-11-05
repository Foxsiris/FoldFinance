package com.foldfinance.app.ui.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldfinance.app.prefs.AppPreferences
import com.foldfinance.app.prefs.AppSettings
import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.core.data.repository.CategoryRepository
import com.foldfinance.core.data.repository.ManualTransactionInput
import com.foldfinance.core.data.repository.TransactionRepository
import com.foldfinance.core.model.Category
import com.foldfinance.parser.Direction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/**
 * Unsaved input of the add/edit form. Serialised into [SavedStateHandle] as one JSON string, so
 * it survives folding, «Не сохранять действия» and process death.
 *
 * @property amountText keypad text (`"1240,5"`), see [AmountEntry]; never a `Double`.
 * @property direction `Direction.name`.
 * @property occurredAtMillis chosen date and time; `null` — «сейчас», taken at save.
 */
@Serializable
data class TransactionDraft(
    val amountText: String = "",
    val currency: String = AppSettings.DEFAULT_CURRENCY,
    val direction: String = Direction.EXPENSE.name,
    val categoryId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    val occurredAtMillis: Long? = null,
) {
    val directionValue: Direction get() = Direction.valueOf(direction)
    val amountMinor: Long get() = AmountEntry.toMinor(amountText, MoneyFormat.fractionDigits(currency))
}

/** What the form is doing. */
sealed interface FormMode {
    data object Closed : FormMode
    data object Add : FormMode
    data class Edit(val transactionId: Long) : FormMode
}

/** Everything the form UI renders. */
data class TransactionFormState(
    val mode: FormMode = FormMode.Closed,
    val draft: TransactionDraft = TransactionDraft(),
    val categories: List<Category> = emptyList(),
    val saving: Boolean = false,
) {
    val isOpen: Boolean get() = mode != FormMode.Closed
    val canSave: Boolean get() = isOpen && !saving && draft.amountMinor > 0
}

/** One-off results for the host (snackbar «Сохранено»). */
sealed interface FormEvent {
    data class Saved(val transactionId: Long, val added: Boolean) : FormEvent
}

/**
 * Manual add («Добавить») and full edit of an operation. One instance per host key:
 * the app-wide add form (default key, also opened by the widget intent) and the edit forms of
 * History and Review (`hiltViewModel(key = …)`).
 *
 * The mode, the edited id and the draft live in [SavedStateHandle]; nothing is kept in
 * composition, so an open editor with unsaved input survives fold/unfold and recreation.
 */
@HiltViewModel
class TransactionFormViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
    preferences: AppPreferences,
    @UiClock private val clock: Clock,
) : ViewModel(), TransactionFormActions {

    /** «Валюта» from the settings: the currency of a new manual operation. */
    private val defaultCurrency: StateFlow<String> =
        preferences.defaultCurrency.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings.DEFAULT_CURRENCY)

    private val json = Json { ignoreUnknownKeys = true }
    private val events = Channel<FormEvent>(Channel.BUFFERED)

    /** Results to show once (e.g. the «Сохранено» snackbar). */
    val formEvents: Flow<FormEvent> = events.receiveAsFlow()

    private val saving = MutableStateFlow(false)

    val state: StateFlow<TransactionFormState> = combine(
        handle.getStateFlow(KEY_MODE, MODE_CLOSED),
        handle.getStateFlow(KEY_EDIT_ID, 0L),
        handle.getStateFlow(KEY_DRAFT, ""),
        categories.observeAll(),
        saving,
    ) { mode, editId, draftJson, cats, isSaving ->
        TransactionFormState(
            mode = modeOf(mode, editId),
            draft = decode(draftJson),
            categories = cats,
            saving = isSaving,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialState())

    private fun initialState() = TransactionFormState(
        mode = modeOf(handle[KEY_MODE] ?: MODE_CLOSED, handle[KEY_EDIT_ID] ?: 0L),
        draft = decode(handle[KEY_DRAFT] ?: ""),
    )

    /** Open an empty add form. An add form that is already open keeps its unsaved input. */
    fun openAdd() {
        if (handle.get<String>(KEY_MODE) == MODE_ADD) return
        handle[KEY_DRAFT] = encode(TransactionDraft(currency = defaultCurrency.value))
        handle[KEY_EDIT_ID] = 0L
        handle[KEY_MODE] = MODE_ADD
    }

    /** Open the editor for [transactionId], prefilled from the database. Re-opening the same one keeps the draft. */
    fun openEdit(transactionId: Long) {
        if (handle.get<String>(KEY_MODE) == MODE_EDIT && handle.get<Long>(KEY_EDIT_ID) == transactionId) return
        viewModelScope.launch {
            val tx = transactions.observeById(transactionId).first() ?: return@launch
            val digits = MoneyFormat.fractionDigits(tx.currency)
            handle[KEY_DRAFT] = encode(
                TransactionDraft(
                    amountText = AmountEntry.fromMinor(tx.amountMinor, digits),
                    currency = tx.currency,
                    direction = tx.direction.name,
                    categoryId = tx.categoryId,
                    merchant = tx.merchant.orEmpty(),
                    note = tx.note.orEmpty(),
                    occurredAtMillis = tx.occurredAt.toEpochMilli(),
                ),
            )
            handle[KEY_EDIT_ID] = transactionId
            handle[KEY_MODE] = MODE_EDIT
        }
    }

    /** Close and drop the unsaved input. */
    override fun close() {
        handle[KEY_MODE] = MODE_CLOSED
        handle[KEY_EDIT_ID] = 0L
        handle[KEY_DRAFT] = ""
    }

    override fun press(key: KeypadKey) = edit { it.copy(amountText = AmountEntry.press(it.amountText, key, MoneyFormat.fractionDigits(it.currency))) }

    override fun setDirection(direction: Direction) = edit { it.copy(direction = direction.name) }

    /** Select [categoryId]; selecting the current one again clears it. */
    override fun toggleCategory(categoryId: Long) = edit { it.copy(categoryId = if (it.categoryId == categoryId) null else categoryId) }

    override fun setMerchant(merchant: String) = edit { it.copy(merchant = merchant.take(MAX_TEXT)) }

    override fun setNote(note: String) = edit { it.copy(note = note.take(MAX_TEXT)) }

    override fun setOccurredAt(instant: Instant) = edit { it.copy(occurredAtMillis = instant.toEpochMilli()) }

    /** Moment the operation will be saved with: the chosen one or «now». */
    fun effectiveOccurredAt(draft: TransactionDraft = state.value.draft): Instant =
        draft.occurredAtMillis?.let(Instant::ofEpochMilli) ?: clock.instant()

    override fun save() {
        val current = state.value
        if (!current.canSave) return
        val draft = current.draft
        saving.value = true
        viewModelScope.launch {
            try {
                when (val mode = current.mode) {
                    FormMode.Add -> {
                        val id = transactions.addManual(
                            ManualTransactionInput(
                                occurredAt = effectiveOccurredAt(draft),
                                amountMinor = draft.amountMinor,
                                currency = draft.currency,
                                direction = draft.directionValue,
                                merchant = draft.merchant.trim().ifEmpty { null },
                                categoryId = draft.categoryId,
                                note = draft.note.trim().ifEmpty { null },
                            ),
                        )
                        close()
                        events.send(FormEvent.Saved(id, added = true))
                    }
                    is FormMode.Edit -> {
                        val original = transactions.observeById(mode.transactionId).first()
                        if (original != null) {
                            transactions.update(
                                original.copy(
                                    occurredAt = effectiveOccurredAt(draft),
                                    amountMinor = draft.amountMinor,
                                    currency = draft.currency,
                                    direction = draft.directionValue,
                                    merchant = draft.merchant.trim().ifEmpty { null },
                                    categoryId = draft.categoryId,
                                    note = draft.note.trim().ifEmpty { null },
                                    // An operation the user corrected by hand is no longer uncertain.
                                    needsReview = false,
                                ),
                            )
                            events.send(FormEvent.Saved(original.id, added = false))
                        }
                        close()
                    }
                    FormMode.Closed -> Unit
                }
            } finally {
                saving.value = false
            }
        }
    }

    private inline fun edit(change: (TransactionDraft) -> TransactionDraft) {
        if (handle.get<String>(KEY_MODE) == null || handle.get<String>(KEY_MODE) == MODE_CLOSED) return
        handle[KEY_DRAFT] = encode(change(decode(handle[KEY_DRAFT] ?: "")))
    }

    private fun encode(draft: TransactionDraft): String = json.encodeToString(TransactionDraft.serializer(), draft)

    private fun decode(text: String): TransactionDraft =
        if (text.isEmpty()) TransactionDraft() else runCatching { json.decodeFromString(TransactionDraft.serializer(), text) }.getOrDefault(TransactionDraft())

    private fun modeOf(mode: String, editId: Long): FormMode = when (mode) {
        MODE_ADD -> FormMode.Add
        MODE_EDIT -> if (editId > 0) FormMode.Edit(editId) else FormMode.Closed
        else -> FormMode.Closed
    }

    companion object {
        const val KEY_MODE = "form.mode"
        const val KEY_EDIT_ID = "form.editId"
        const val KEY_DRAFT = "form.draft"
        private const val MODE_CLOSED = "closed"
        private const val MODE_ADD = "add"
        private const val MODE_EDIT = "edit"
        private const val MAX_TEXT = 200
    }
}
