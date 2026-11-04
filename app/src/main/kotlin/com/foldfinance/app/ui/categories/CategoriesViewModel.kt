package com.foldfinance.app.ui.categories

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldfinance.core.data.repository.CategoryRepository
import com.foldfinance.core.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Category being added or edited. Unsaved input survives folding and recreation (it lives in
 * [SavedStateHandle]).
 *
 * @property id `null` for a new category.
 * @property colorArgb stored colour; `null` — the theme colour for the category key («Авто»).
 */
@Immutable
data class CategoryDraft(
    val id: Long?,
    val name: String,
    val iconKey: String,
    val colorArgb: Int?,
    val nameError: Boolean = false,
)

@Immutable
data class CategoriesUiState(
    val isLoading: Boolean,
    val categories: List<Category> = emptyList(),
    val draft: CategoryDraft? = null,
) {
    /** Category behind the draft, for its key (theme colour) and the «Базовая» label. */
    val editing: Category? get() = draft?.id?.let { id -> categories.firstOrNull { it.id == id } }
}

/** One-off results shown as a toast. */
enum class CategoriesEvent { Saved, SaveFailed }

/** Categories screen: list, add, rename, icon and colour (stage 6). */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: CategoryRepository,
) : ViewModel() {

    private val events = Channel<CategoriesEvent>(Channel.BUFFERED)

    /** «Сохранено» and errors, consumed once by the screen. */
    val eventFlow: Flow<CategoriesEvent> = events.receiveAsFlow()

    private val draft = combine(
        savedStateHandle.getStateFlow(KEY_OPEN, false),
        savedStateHandle.getStateFlow(KEY_ID, NEW_ID),
        savedStateHandle.getStateFlow(KEY_NAME, ""),
        savedStateHandle.getStateFlow(KEY_ICON, DEFAULT_ICON),
        combine(savedStateHandle.getStateFlow<Int?>(KEY_COLOR, null), savedStateHandle.getStateFlow(KEY_ERROR, false)) { c, e -> c to e },
    ) { open, id, name, icon, (color, error) ->
        if (!open) null else CategoryDraft(id.takeIf { it != NEW_ID }, name, icon, color, error)
    }

    val state: StateFlow<CategoriesUiState> = combine(repository.observeAll(), draft) { categories, draft ->
        // A category deleted elsewhere closes its editor.
        val valid = draft?.takeIf { d -> d.id == null || categories.any { it.id == d.id } }
        CategoriesUiState(isLoading = false, categories = categories, draft = valid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CategoriesUiState(isLoading = true))

    fun startNew() = open(NEW_ID, name = "", icon = DEFAULT_ICON, color = null)

    fun edit(category: Category) = open(category.id, category.name, category.iconKey, category.colorArgb)

    fun updateName(name: String) {
        savedStateHandle[KEY_NAME] = name.take(MAX_NAME)
        if (name.isNotBlank()) savedStateHandle[KEY_ERROR] = false
    }

    fun updateIcon(iconKey: String) {
        savedStateHandle[KEY_ICON] = iconKey
    }

    /** `null` — «Авто», the theme colour of the category key. */
    fun updateColor(colorArgb: Int?) {
        savedStateHandle[KEY_COLOR] = colorArgb
    }

    fun cancel() = close()

    /** Validates and writes the draft; on success closes the editor and emits [CategoriesEvent.Saved]. */
    fun save() {
        val draft = state.value.draft ?: return
        val name = draft.name.trim()
        if (name.isEmpty()) {
            savedStateHandle[KEY_ERROR] = true
            return
        }
        viewModelScope.launch {
            try {
                val id = draft.id
                if (id == null) {
                    repository.add(name, draft.iconKey, draft.colorArgb)
                } else {
                    val current = state.value.categories.firstOrNull { it.id == id }
                    if (current?.name != name) repository.rename(id, name)
                    if (current?.iconKey != draft.iconKey) repository.setIcon(id, draft.iconKey)
                    if (current?.colorArgb != draft.colorArgb) repository.setColor(id, draft.colorArgb)
                }
                close()
                events.send(CategoriesEvent.Saved)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                events.send(CategoriesEvent.SaveFailed)
            }
        }
    }

    private fun open(id: Long, name: String, icon: String, color: Int?) {
        savedStateHandle[KEY_ID] = id
        savedStateHandle[KEY_NAME] = name
        savedStateHandle[KEY_ICON] = icon
        savedStateHandle[KEY_COLOR] = color
        savedStateHandle[KEY_ERROR] = false
        savedStateHandle[KEY_OPEN] = true
    }

    private fun close() {
        savedStateHandle[KEY_OPEN] = false
        savedStateHandle[KEY_ERROR] = false
    }

    companion object {
        const val KEY_OPEN = "categories.editorOpen"
        const val KEY_ID = "categories.editorId"
        const val KEY_NAME = "categories.editorName"
        const val KEY_ICON = "categories.editorIcon"
        const val KEY_COLOR = "categories.editorColor"
        const val KEY_ERROR = "categories.editorError"
        private const val NEW_ID = -1L
        private const val DEFAULT_ICON = "category"
        private const val MAX_NAME = 40
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
