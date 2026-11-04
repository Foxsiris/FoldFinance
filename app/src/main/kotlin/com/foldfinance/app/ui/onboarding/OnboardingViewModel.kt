package com.foldfinance.app.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldfinance.app.ingest.IngestPermissions
import com.foldfinance.app.ingest.IngestRuntimePrefs
import com.foldfinance.app.ingest.IngestScheduler
import com.foldfinance.app.ingest.ScanState
import com.foldfinance.app.ingest.push.InstalledApp
import com.foldfinance.app.ingest.push.InstalledAppsSource
import com.foldfinance.core.data.settings.IngestSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State of the access onboarding.
 *
 * - Current step, the «opened system settings from this step» marker, the source selection draft
 *   and «scan started» live in [SavedStateHandle]: folding, rotation, «Не сохранять действия» and
 *   a trip to system settings do not lose the place.
 * - «Onboarding completed» lives in DataStore ([IngestRuntimePrefs.onboardingCompleted]).
 * - Permissions are re-read by [refresh] on every ON_RESUME (coming back from system settings).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val prefs: IngestRuntimePrefs,
    private val settings: IngestSettings,
    private val permissions: IngestPermissions,
    private val scheduler: IngestScheduler,
    private val appsSource: InstalledAppsSource,
) : ViewModel() {

    private val flow: List<OnboardingStep> = OnboardingStep.flow(IngestPermissions.needsPostNotificationsRequest)

    private val permissionState = MutableStateFlow(permissions.snapshot())
    private val apps = MutableStateFlow<List<InstalledApp>?>(null)

    /** `null` until DataStore answered; `true` → the route calls `onFinished` right away. */
    val alreadyCompleted: StateFlow<Boolean?> =
        prefs.onboardingCompleted.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _finished = MutableStateFlow(false)

    /** Becomes `true` once the user finished or skipped the last step. */
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    val state: StateFlow<OnboardingUiState> = combine(
        handle.getStateFlow(KEY_STEP, OnboardingStep.SmsIntro.name),
        permissionState,
        apps,
        handle.getStateFlow(KEY_SELECTED, emptyList<String>()),
        scheduler.observeFullScan().onStart { emit(ScanState.Idle) },
    ) { stepName, perms, appList, selected, scan ->
        val step = OnboardingStep.valueOf(stepName)
        val counted = if (step == OnboardingStep.SmsRestricted) OnboardingStep.SmsIntro else step
        OnboardingUiState(
            step = step,
            stepNumber = flow.indexOf(counted) + 1,
            stepCount = flow.size,
            permissions = perms,
            sources = SourcesState(appList, selected.toSet()),
            scan = scan,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialState())

    private val step: OnboardingStep get() = OnboardingStep.valueOf(handle[KEY_STEP] ?: OnboardingStep.SmsIntro.name)

    init {
        onEnter(step)
    }

    /** Re-read permissions (ON_RESUME). Auto-advances where the user came back from settings with access granted. */
    fun refresh() {
        val before = permissionState.value
        val now = permissions.snapshot()
        permissionState.value = now
        val openedFrom = handle.get<String>(KEY_OPENED_SETTINGS_FROM)
        when {
            step == OnboardingStep.SmsRestricted && now.sms -> next()
            step == OnboardingStep.NotificationAccess && openedFrom == step.name &&
                now.notificationAccess && !before.notificationAccess -> next()
        }
    }

    /** Result of the READ_SMS / RECEIVE_SMS request. */
    fun onSmsPermissionResult(granted: Boolean) {
        permissionState.value = permissions.snapshot()
        if (granted || permissionState.value.sms) next() else goTo(OnboardingStep.SmsRestricted)
    }

    fun onPostNotificationsResult() {
        permissionState.value = permissions.snapshot()
        next()
    }

    /** The user is about to leave for a system settings screen from the current step. */
    fun onOpenedSettings() {
        handle[KEY_OPENED_SETTINGS_FROM] = step.name
    }

    fun next() {
        val current = step
        val anchor = if (current == OnboardingStep.SmsRestricted) OnboardingStep.SmsIntro else current
        val index = flow.indexOf(anchor)
        if (index < 0 || index == flow.lastIndex) finish() else goTo(flow[index + 1])
    }

    fun toggleSource(packageName: String) {
        val selected = handle.get<List<String>>(KEY_SELECTED).orEmpty().toMutableSet()
        if (!selected.add(packageName)) selected.remove(packageName)
        handle[KEY_SELECTED] = selected.sorted()
    }

    fun saveSources() {
        val selected = handle.get<List<String>>(KEY_SELECTED).orEmpty().toSet()
        viewModelScope.launch {
            settings.setPushPackageWhitelist(selected)
            next()
        }
    }

    fun startScan() {
        if (!permissionState.value.sms) return
        handle[KEY_SCAN_STARTED] = true
        scheduler.startFullScan()
    }

    fun finish() {
        viewModelScope.launch {
            prefs.setOnboardingCompleted(true)
            scheduler.ensureScheduled()
            _finished.value = true
        }
    }

    private fun goTo(target: OnboardingStep) {
        handle[KEY_STEP] = target.name
        handle.remove<String>(KEY_OPENED_SETTINGS_FROM)
        onEnter(target)
    }

    private fun onEnter(target: OnboardingStep) {
        when (target) {
            OnboardingStep.NotificationSources -> loadSources()
            OnboardingStep.Scan -> if (handle.get<Boolean>(KEY_SCAN_STARTED) != true) startScan()
            else -> Unit
        }
    }

    private fun loadSources() {
        if (apps.value != null) return
        viewModelScope.launch {
            val installed = runCatching { appsSource.load() }.getOrDefault(emptyList())
            if (handle.get<Boolean>(KEY_DRAFT_READY) != true) {
                handle[KEY_DRAFT_READY] = true
                val whitelist = settings.pushPackageWhitelist.first()
                // First visit: the current whitelist, or — when it is empty — the known banks that
                // are installed. Nothing is saved until the user taps «Сохранить».
                val draft = whitelist.ifEmpty { installed.filter { it.isKnownBank }.map { it.packageName }.toSet() }
                handle[KEY_SELECTED] = draft.sorted()
            }
            apps.value = installed
        }
    }

    private fun initialState(): OnboardingUiState {
        val current = step
        val counted = if (current == OnboardingStep.SmsRestricted) OnboardingStep.SmsIntro else current
        return OnboardingUiState(
            step = current,
            stepNumber = flow.indexOf(counted) + 1,
            stepCount = flow.size,
            permissions = permissionState.value,
            sources = SourcesState(null, handle.get<List<String>>(KEY_SELECTED).orEmpty().toSet()),
            scan = ScanState.Idle,
        )
    }

    companion object {
        const val KEY_STEP = "onboarding.step"
        const val KEY_SELECTED = "onboarding.selectedSources"
        const val KEY_OPENED_SETTINGS_FROM = "onboarding.openedSettingsFrom"
        const val KEY_SCAN_STARTED = "onboarding.scanStarted"
        const val KEY_DRAFT_READY = "onboarding.sourcesDraftReady"
    }
}
