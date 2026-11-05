package com.foldfinance.app.ui.shell

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldfinance.app.AppIntents
import com.foldfinance.core.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether the first-run onboarding still has to be shown. */
enum class OnboardingState {
    /** Not read from storage yet: show nothing over the shell, so nothing flashes. */
    Unknown,

    /** First run: `OnboardingRoute` is shown over the shell until it calls `onFinished`. */
    Required,
    Done,
}

/**
 * State of the app shell:
 *
 * - the selected top-level section — in [SavedStateHandle], so it survives activity recreation
 *   (folding/unfolding, rotation, «Не сохранять действия») and process death;
 * - the «на проверку» badge count;
 * - the onboarding flag (DataStore key `onboarding_done`, see [OnboardingFlag]);
 * - a pending «open this operation in History» request from other sections (also saved).
 *
 * @param visibleSections sections the navigation shows; a saved selection that is not visible
 *   falls back to the first one.
 */
@HiltViewModel
class ShellViewModel(
    private val savedStateHandle: SavedStateHandle,
    val visibleSections: List<Section> = Sections.visible(),
    reviewCount: Flow<Int> = emptyFlow(),
    private val onboardingFlag: OnboardingFlag? = null,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        transactions: TransactionRepository,
        onboardingFlag: OnboardingFlag,
    ) : this(savedStateHandle, Sections.visible(), transactions.observeReviewCount(), onboardingFlag)

    init {
        require(visibleSections.isNotEmpty()) { "at least one section must be visible" }
    }

    val selected: StateFlow<Section> = savedStateHandle.getStateFlow(KEY_SELECTED, visibleSections.first().name)
        .map(::resolve)
        .stateIn(viewModelScope, SharingStarted.Eagerly, resolve(savedStateHandle[KEY_SELECTED]))

    /** Size of the review queue for the navigation badge. */
    val reviewCount: StateFlow<Int> = reviewCount
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val onboarding: StateFlow<OnboardingState> = (onboardingFlag?.done ?: flowOf(true))
        .map { done -> if (done) OnboardingState.Done else OnboardingState.Required }
        .stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingState.Unknown)

    /** Operation another section asked History to open; consumed by History. */
    val pendingTransaction: StateFlow<Long?> = savedStateHandle.getStateFlow<Long?>(KEY_PENDING_TX, null)

    fun select(section: Section) {
        if (section in visibleSections) savedStateHandle[KEY_SELECTED] = section.name
    }

    /** Switch to History and open [transactionId] there. */
    fun openTransaction(transactionId: Long) {
        if (Section.History !in visibleSections) return
        savedStateHandle[KEY_PENDING_TX] = transactionId
        select(Section.History)
    }

    fun consumePendingTransaction() {
        savedStateHandle[KEY_PENDING_TX] = null
    }

    fun finishOnboarding() {
        val flag = onboardingFlag ?: return
        viewModelScope.launch { flag.markDone() }
    }

    /**
     * Applies an intent action from `MainActivity` ([AppIntents]).
     * @return `true` when the action asks for the add form ([AppIntents.ACTION_QUICK_ADD]).
     */
    fun handleIntentAction(action: String?): Boolean = when (action) {
        AppIntents.ACTION_OPEN_REVIEW -> {
            select(Section.Review)
            false
        }
        AppIntents.ACTION_QUICK_ADD -> {
            select(Section.Overview)
            true
        }
        else -> false
    }

    private fun resolve(name: String?): Section =
        visibleSections.firstOrNull { it.name == name } ?: visibleSections.first()

    companion object {
        const val KEY_SELECTED = "shell.selectedSection"
        const val KEY_PENDING_TX = "shell.pendingTransaction"
    }
}
