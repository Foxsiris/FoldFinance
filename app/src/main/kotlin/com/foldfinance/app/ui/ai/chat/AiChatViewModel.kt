package com.foldfinance.app.ui.ai.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldfinance.app.ai.LocalAi
import com.foldfinance.app.ai.features.common.AiAvailability
import com.foldfinance.app.ai.features.common.toAvailability
import com.foldfinance.app.ai.features.query.ChatScope
import com.foldfinance.app.ai.features.query.ParsedCall
import com.foldfinance.app.ai.features.query.QueryAnswer
import com.foldfinance.app.ai.features.query.QueryAssistant
import com.foldfinance.app.ai.features.query.QueryEngine
import com.foldfinance.app.ai.features.query.QueryRejection
import com.foldfinance.app.ai.features.query.SavedQueryCall
import com.foldfinance.app.prefs.AppPreferences
import com.foldfinance.app.prefs.AppSettings
import com.foldfinance.app.ui.transaction.UiClock
import com.foldfinance.core.data.repository.CategoryRepository
import com.foldfinance.core.data.repository.TransactionRepository
import com.foldfinance.core.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * One question of the chat as it is saved: the question and the validated call (resolved dates,
 * category id) or the rejection. Answers are recomputed from the database on restore, so a
 * restored chat never shows stale sums.
 */
@Serializable
data class SavedTurn(
    val id: Long,
    val question: String,
    val call: SavedQueryCall? = null,
    val rejection: String? = null,
)

/** A turn as the UI renders it. */
sealed interface ChatTurn {
    val id: Long
    val question: String

    data class Answered(override val id: Long, override val question: String, val answer: QueryAnswer) : ChatTurn
    data class NotUnderstood(override val id: Long, override val question: String, val reason: QueryRejection) : ChatTurn
    data class Pending(override val id: Long, override val question: String) : ChatTurn
}

data class AiChatState(
    val availability: AiAvailability = AiAvailability.Hidden,
    val turns: List<ChatTurn> = emptyList(),
    val input: String = "",
    val categories: List<Category> = emptyList(),
    val currency: String = AppSettings.DEFAULT_CURRENCY,
    val today: LocalDate = LocalDate.MIN,
    val sheetOpen: Boolean = false,
) {
    val asking: Boolean get() = turns.any { it is ChatTurn.Pending }
    val canSend: Boolean get() = availability == AiAvailability.Ready && input.isNotBlank() && !asking
}

/** What the chat UI can do. Defaults are no-ops for previews and screenshots. */
open class AiChatActions {
    open fun setInput(text: String) = Unit
    open fun send(scope: ChatScope) = Unit
    open fun clear() = Unit
    open fun setSheetOpen(open: Boolean) = Unit
}

/**
 * Chat over the user's operations (п.4 + бонус «чат под Fold»). History, unsent input and the
 * open sheet live in [SavedStateHandle]; one instance (key [KEY]) is shared by the pane on the
 * inner screen and the sheet on the outer one, so folding keeps the conversation.
 */
@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    ai: LocalAi,
    private val assistant: QueryAssistant,
    transactions: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    preferences: AppPreferences,
    @UiClock private val clock: Clock,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private val turnsSerializer = ListSerializer(SavedTurn.serializer())
    private var job: Job? = null

    val actions: AiChatActions = object : AiChatActions() {
        override fun setInput(text: String) { handle[KEY_INPUT] = text.take(QueryAssistant.MAX_QUESTION) }
        override fun send(scope: ChatScope) = this@AiChatViewModel.send(scope)
        override fun clear() = this@AiChatViewModel.clear()
        override fun setSheetOpen(open: Boolean) { handle[KEY_SHEET] = open }
    }

    private val inputs = combine(
        handle.getStateFlow(KEY_TURNS, ""),
        handle.getStateFlow(KEY_PENDING, ""),
        handle.getStateFlow(KEY_INPUT, ""),
        handle.getStateFlow(KEY_SHEET, false),
    ) { turns, pending, input, sheet -> Inputs(decode(turns), pending, input, sheet) }

    val state: StateFlow<AiChatState> = combine(
        ai.status,
        inputs,
        transactions.observe(),
        categoryRepository.observeAll(),
        preferences.defaultCurrency,
    ) { status, inp, txs, cats, currency ->
        val zone = clock.zone
        val turns = inp.turns.map { saved ->
            val call = saved.call?.restore(cats, zone)
            when {
                call != null -> ChatTurn.Answered(saved.id, saved.question, QueryEngine.answer(call, txs, cats, currency))
                else -> ChatTurn.NotUnderstood(
                    saved.id,
                    saved.question,
                    saved.rejection?.let { runCatching { QueryRejection.valueOf(it) }.getOrNull() } ?: QueryRejection.UNKNOWN_CATEGORY,
                )
            }
        } + listOfNotNull(inp.pending.takeIf { it.isNotEmpty() }?.let { ChatTurn.Pending(PENDING_ID, it) })
        AiChatState(
            availability = status.toAvailability(),
            turns = turns,
            input = inp.input,
            categories = cats,
            currency = currency,
            today = LocalDate.now(clock),
            sheetOpen = inp.sheet,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiChatState())

    init {
        // A question asked just before the process died is asked again.
        handle.get<String>(KEY_PENDING)?.takeIf { it.isNotEmpty() }?.let { ask(it, ChatScope()) }
    }

    fun send(scope: ChatScope) {
        val question = handle.get<String>(KEY_INPUT).orEmpty().trim()
        if (question.isEmpty() || job?.isActive == true) return
        handle[KEY_INPUT] = ""
        ask(question, scope)
    }

    fun clear() {
        job?.cancel()
        handle[KEY_TURNS] = ""
        handle[KEY_PENDING] = ""
    }

    private fun ask(question: String, scope: ChatScope) {
        handle[KEY_PENDING] = question
        job = viewModelScope.launch {
            val categories = categoryRepository.getAll()
            val parsed = assistant.interpret(question, categories, LocalDate.now(clock), clock.zone, scope)
            val turns = decode(handle[KEY_TURNS] ?: "")
            val id = (turns.maxOfOrNull { it.id } ?: 0L) + 1
            val saved = when (parsed) {
                is ParsedCall.Ok -> SavedTurn(id, question, call = SavedQueryCall.of(parsed.call))
                is ParsedCall.Rejected -> SavedTurn(id, question, rejection = parsed.reason.name)
            }
            handle[KEY_TURNS] = encode((turns + saved).takeLast(MAX_TURNS))
            handle[KEY_PENDING] = ""
        }
    }

    private data class Inputs(val turns: List<SavedTurn>, val pending: String, val input: String, val sheet: Boolean)

    private fun encode(turns: List<SavedTurn>): String = json.encodeToString(turnsSerializer, turns)

    private fun decode(text: String): List<SavedTurn> =
        if (text.isEmpty()) emptyList() else runCatching { json.decodeFromString(turnsSerializer, text) }.getOrDefault(emptyList())

    /** Await the current question (tests). */
    internal suspend fun awaitIdle() {
        job?.join()
        state.first { !it.asking }
    }

    companion object {
        /** `hiltViewModel(key = AiChatViewModel.KEY)` — the pane and the sheet share it. */
        const val KEY = "ai_chat"
        const val KEY_TURNS = "ai_chat.turns"
        const val KEY_PENDING = "ai_chat.pending"
        const val KEY_INPUT = "ai_chat.input"
        const val KEY_SHEET = "ai_chat.sheet"
        private const val MAX_TURNS = 30
        private const val PENDING_ID = Long.MAX_VALUE
    }
}
