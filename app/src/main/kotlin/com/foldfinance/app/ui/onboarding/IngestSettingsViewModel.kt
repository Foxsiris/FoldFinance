package com.foldfinance.app.ui.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldfinance.app.ingest.IngestPermissions
import com.foldfinance.app.ingest.IngestScheduler
import com.foldfinance.app.ingest.PermissionSnapshot
import com.foldfinance.app.ingest.ScanState
import com.foldfinance.app.ingest.push.InstalledApp
import com.foldfinance.app.ingest.push.InstalledAppsSource
import com.foldfinance.app.ingest.push.ListenerConnection
import com.foldfinance.app.ingest.push.ListenerHealth
import com.foldfinance.app.ingest.push.PushIngestFailures
import com.foldfinance.core.data.settings.IngestSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the «Доступы и источники» block shows. */
@Immutable
data class IngestSettingsUiState(
    val permissions: PermissionSnapshot = PermissionSnapshot.NONE,
    val listener: ListenerConnection = ListenerConnection.UNKNOWN,
    val whitelistSize: Int = 0,
    val scan: ScanState = ScanState.Idle,
    val sources: SourcesState = SourcesState(),
    val pushFailures: PushIngestFailures = PushIngestFailures(),
)

/** State of [IngestSettingsSection]. The source-picker draft lives in [SavedStateHandle]. */
@HiltViewModel
class IngestSettingsViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val settings: IngestSettings,
    private val permissions: IngestPermissions,
    private val scheduler: IngestScheduler,
    private val health: ListenerHealth,
    private val appsSource: InstalledAppsSource,
) : ViewModel() {

    private val permissionState = MutableStateFlow(permissions.snapshot())
    private val apps = MutableStateFlow<List<InstalledApp>?>(null)

    val state: StateFlow<IngestSettingsUiState> = combine(
        permissionState,
        combine(health.status, health.ingestFailures) { status, failures -> status to failures },
        settings.pushPackageWhitelist,
        scheduler.observeFullScan().onStart { emit(ScanState.Idle) },
        combine(apps, handle.getStateFlow(KEY_DRAFT, emptyList<String>())) { a, d -> SourcesState(a, d.toSet()) },
    ) { perms, (listener, failures), whitelist, scan, sources ->
        IngestSettingsUiState(perms, listener.connection, whitelist.size, scan, sources, failures)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IngestSettingsUiState(permissions = permissionState.value))

    init {
        // The settings block is a natural «app is in use» moment: make sure background work exists.
        scheduler.ensureScheduled()
    }

    fun refresh() {
        permissionState.value = permissions.snapshot()
    }

    fun rebindListener() = health.requestRebind()

    fun clearPushFailures() = health.clearIngestFailures()

    fun rescan() {
        if (permissions.hasSms()) scheduler.startFullScan()
    }

    /** Opening the picker: load apps, start the draft from the saved whitelist. */
    fun openSources() {
        viewModelScope.launch {
            handle[KEY_DRAFT] = settings.pushPackageWhitelist.first().sorted()
            if (apps.value == null) apps.value = runCatching { appsSource.load() }.getOrDefault(emptyList())
        }
    }

    fun toggleSource(packageName: String) {
        val draft = handle.get<List<String>>(KEY_DRAFT).orEmpty().toMutableSet()
        if (!draft.add(packageName)) draft.remove(packageName)
        handle[KEY_DRAFT] = draft.sorted()
    }

    fun saveSources() {
        val draft = handle.get<List<String>>(KEY_DRAFT).orEmpty().toSet()
        viewModelScope.launch { settings.setPushPackageWhitelist(draft) }
    }

    private companion object {
        const val KEY_DRAFT = "ingestSettings.sourcesDraft"
    }
}
