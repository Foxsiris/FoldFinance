package com.foldfinance.app.ui.shell

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Selected top-level section. Lives in [SavedStateHandle], so it survives activity recreation
 * (folding/unfolding, rotation, «Не сохранять действия») and process death.
 *
 * A plain ViewModel (no Hilt): created by the default `SavedStateViewModelFactory`, which Hilt's
 * factory delegates to for non-`@HiltViewModel` classes, and by Robolectric tests directly.
 *
 * @param visibleSections sections the navigation shows; a saved selection that is not visible
 *   (for example a debug-only section restored in a release build) falls back to the first one.
 */
class ShellViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val visibleSections: List<Section> = Sections.visible(),
) : ViewModel() {

    init {
        require(visibleSections.isNotEmpty()) { "at least one section must be visible" }
    }

    val selected: StateFlow<Section> = savedStateHandle.getStateFlow(KEY_SELECTED, visibleSections.first().name)
        .map(::resolve)
        .stateIn(viewModelScope, SharingStarted.Eagerly, resolve(savedStateHandle[KEY_SELECTED]))

    fun select(section: Section) {
        if (section in visibleSections) savedStateHandle[KEY_SELECTED] = section.name
    }

    private fun resolve(name: String?): Section =
        visibleSections.firstOrNull { it.name == name } ?: visibleSections.first()

    companion object {
        const val KEY_SELECTED = "shell.selectedSection"
    }
}
