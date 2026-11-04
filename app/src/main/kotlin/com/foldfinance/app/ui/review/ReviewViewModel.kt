package com.foldfinance.app.ui.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldfinance.app.ui.transaction.CategoryEditor
import com.foldfinance.app.ui.transaction.DeferredDeletion
import com.foldfinance.app.ui.transaction.RulePrompt
import com.foldfinance.app.ui.transaction.TransactionDetailActions
import com.foldfinance.app.ui.transaction.TransactionDetailState
import com.foldfinance.app.ui.transaction.UiClock
import com.foldfinance.app.ui.transaction.observeDetail
import com.foldfinance.core.data.repository.CategoryRepository
import com.foldfinance.core.data.repository.MerchantRuleRepository
import com.foldfinance.core.data.repository.TransactionRepository
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class ReviewUiState(
    val loading: Boolean = true,
    val queue: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** Multi-selection of the inner screen. */
    val selectedIds: Set<Long> = emptySet(),
    /** Operation in the editor on the right (inner screen): the chosen one, or the first in the queue. */
    val focusedId: Long? = null,
    /** Operation whose category sheet is open (outer screen, swipe left). */
    val pickerForId: Long? = null,
) {
    val current: Transaction? get() = queue.firstOrNull()
    val next: Transaction? get() = queue.getOrNull(1)
    val focused: Transaction? get() = queue.firstOrNull { it.id == focusedId }
    val pickerFor: Transaction? get() = queue.firstOrNull { it.id == pickerForId }
    val isEmpty: Boolean get() = !loading && queue.isEmpty()
}

/**
 * «Проверка»: the `needsReview` queue. Outer screen — a stack of cards (swipe right confirms,
 * swipe left picks a category); inner screen — list with multi-selection on the left, editor on
 * the right, bulk category assignment.
 *
 * Selection, focus and the open category sheet live in [SavedStateHandle]; operations confirmed
 * here leave the queue at once ([dismissed]) without waiting for the database round-trip, so the
 * next card is there immediately.
 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    rules: MerchantRuleRepository,
    @UiClock private val clock: Clock,
) : ViewModel(), TransactionDetailActions {

    private val categoryEditor = CategoryEditor(handle, viewModelScope, transactions, rules, KEY_RULE)
    private val deletion = DeferredDeletion(viewModelScope, transactions)
    private val dismissed = MutableStateFlow<Set<Long>>(emptySet())

    val zone: ZoneId get() = clock.zone
    fun now(): Instant = clock.instant()

    val rulePrompt: StateFlow<RulePrompt?> = categoryEditor.prompt

    val state: StateFlow<ReviewUiState> = combine(
        combine(transactions.observeReviewQueue(), dismissed, deletion.pending) { queue, gone, deleting ->
            queue.filterNot { it.id in gone || it.id in deleting }
        },
        categoryRepository.observeAll(),
        handle.getStateFlow(KEY_SELECTED, LongArray(0)),
        handle.getStateFlow<Long?>(KEY_FOCUSED, null),
        handle.getStateFlow<Long?>(KEY_PICKER, null),
    ) { queue, cats, selected, focused, picker ->
        val ids = queue.mapTo(HashSet()) { it.id }
        ReviewUiState(
            loading = false,
            queue = queue,
            categories = cats,
            selectedIds = selected.filterTo(LinkedHashSet()) { it in ids },
            focusedId = focused?.takeIf { it in ids } ?: queue.firstOrNull()?.id,
            pickerForId = picker?.takeIf { it in ids },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    fun detail(id: Long): Flow<TransactionDetailState?> = transactions.observeDetail(id, categoryRepository)

    override fun confirm(transactionId: Long) = confirmAll(listOf(transactionId))

    fun confirmSelected() = confirmAll(state.value.selectedIds.toList())

    /** Category for [ids], taking them out of the queue; one operation with a merchant asks about a rule. */
    fun assignCategory(ids: Collection<Long>, categoryId: Long?) {
        if (ids.isEmpty()) return
        val category = state.value.categories.firstOrNull { it.id == categoryId }
        val merchant = if (ids.size == 1) state.value.queue.firstOrNull { it.id == ids.first() }?.merchant else null
        dismissed.update { it + ids }
        categoryEditor.setCategory(ids, category, merchant, confirm = true)
        clearFrom(ids)
    }

    fun assignToSelected(categoryId: Long) = assignCategory(state.value.selectedIds.toList(), categoryId)

    /** Category chips in the editor of one operation. */
    override fun setCategory(transactionId: Long, categoryId: Long?) = assignCategory(listOf(transactionId), categoryId)

    /** Editing is hosted by the screen (edit form). */
    override fun edit(transactionId: Long) = Unit

    override fun delete(transactionId: Long) = deletion.request(transactionId)

    fun undoDelete(transactionId: Long) = deletion.undo(transactionId)

    fun toggleSelected(id: Long) {
        val current = state.value.selectedIds
        handle[KEY_SELECTED] = (if (id in current) current - id else current + id).toLongArray()
    }

    fun selectAll() {
        handle[KEY_SELECTED] = state.value.queue.map { it.id }.toLongArray()
    }

    fun clearSelection() {
        handle[KEY_SELECTED] = LongArray(0)
    }

    fun focus(id: Long) {
        handle[KEY_FOCUSED] = id
    }

    fun openPicker(id: Long) {
        handle[KEY_PICKER] = id
    }

    fun closePicker() {
        handle[KEY_PICKER] = null
    }

    fun applyRule() = categoryEditor.applyRule()
    fun dismissRule() = categoryEditor.dismissRule()

    private fun confirmAll(ids: List<Long>) {
        if (ids.isEmpty()) return
        dismissed.update { it + ids }
        clearFrom(ids)
        viewModelScope.launch { transactions.confirmReview(ids) }
    }

    private fun clearFrom(ids: Collection<Long>) {
        val selected = handle.get<LongArray>(KEY_SELECTED) ?: LongArray(0)
        handle[KEY_SELECTED] = selected.filterNot { it in ids }.toLongArray()
        if (handle.get<Long?>(KEY_PICKER) in ids) handle[KEY_PICKER] = null
    }

    companion object {
        const val KEY_SELECTED = "review.selected"
        const val KEY_FOCUSED = "review.focused"
        const val KEY_PICKER = "review.picker"
        const val KEY_RULE = "review.rulePrompt"
    }
}
