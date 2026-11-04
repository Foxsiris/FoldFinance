package com.foldfinance.app.ui.transaction

import androidx.lifecycle.SavedStateHandle
import com.foldfinance.core.data.repository.CategoryRepository
import com.foldfinance.core.data.repository.MerchantRuleRepository
import com.foldfinance.core.data.repository.TransactionRepository
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.RawMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * The question «Применять для всех операций от X?» waiting for an answer.
 * Serialisable: it lives in `SavedStateHandle` and survives folding while the dialog is open.
 */
@Serializable
data class RulePrompt(val merchant: String, val categoryId: Long, val categoryName: String)

/**
 * Category changes with the follow-up rule question (stage 6). After the user moves an
 * operation with a merchant to a category, [prompt] asks whether to learn it as a merchant rule;
 * [applyRule] calls `MerchantRuleRepository.add(…, applyToExisting = true)`.
 */
class CategoryEditor(
    private val handle: SavedStateHandle,
    private val scope: CoroutineScope,
    private val transactions: TransactionRepository,
    private val rules: MerchantRuleRepository,
    private val key: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val prompt: StateFlow<RulePrompt?> = handle.getStateFlow<String?>(key, null)
        .map { text -> text?.let { runCatching { json.decodeFromString(RulePrompt.serializer(), it) }.getOrNull() } }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Sets the category of [ids]. When [merchant] is known and a category is chosen, asks about a rule.
     * @param confirm also take the operations out of the review queue.
     */
    fun setCategory(ids: Collection<Long>, category: Category?, merchant: String?, confirm: Boolean = false) {
        if (ids.isEmpty()) return
        scope.launch {
            transactions.setCategory(ids, category?.id)
            if (confirm) transactions.confirmReview(ids)
        }
        val name = merchant?.trim().orEmpty()
        if (category != null && name.isNotEmpty()) {
            handle[key] = json.encodeToString(RulePrompt.serializer(), RulePrompt(name, category.id, category.name))
        }
    }

    fun applyRule() {
        val current = prompt.value ?: return
        handle[key] = null
        scope.launch { rules.add(current.merchant, current.categoryId, applyToExisting = true) }
    }

    fun dismissRule() {
        handle[key] = null
    }
}

/**
 * Deletion with «Отменить»: the operation disappears from lists at once ([pending]) and is
 * deleted for real after [UNDO_WINDOW_MS] unless [undo] is called. If the process dies in between,
 * nothing is deleted — the safe outcome.
 */
class DeferredDeletion(
    private val scope: CoroutineScope,
    private val transactions: TransactionRepository,
) {
    private val ids = MutableStateFlow<Set<Long>>(emptySet())
    private val jobs = HashMap<Long, Job>()

    val pending: StateFlow<Set<Long>> = ids.asStateFlow()

    fun request(id: Long) {
        ids.update { it + id }
        jobs.remove(id)?.cancel()
        jobs[id] = scope.launch {
            delay(UNDO_WINDOW_MS)
            commit(id)
        }
    }

    fun undo(id: Long) {
        jobs.remove(id)?.cancel()
        ids.update { it - id }
    }

    private suspend fun commit(id: Long) {
        jobs.remove(id)
        transactions.delete(id)
        ids.update { it - id }
    }

    companion object {
        /** Longer than the snackbar («Short», 4 s), so «Отменить» always works while it is visible. */
        const val UNDO_WINDOW_MS = 6_000L
    }
}

/** Detail of one operation as a flow; `null` once it is deleted. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TransactionRepository.observeDetail(id: Long, categories: CategoryRepository): Flow<TransactionDetailState?> =
    observeById(id).flatMapLatest { tx ->
        if (tx == null) {
            flowOf(null)
        } else {
            combine(categories.observeAll(), observeRawMessages(id)) { cats: List<Category>, raw: List<RawMessage> ->
                TransactionDetailState(
                    transaction = tx,
                    category = cats.firstOrNull { it.id == tx.categoryId },
                    categories = cats,
                    rawMessages = raw.sortedBy { it.receivedAt },
                )
            }
        }
    }
