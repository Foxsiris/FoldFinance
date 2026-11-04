package com.foldfinance.app.ingest.push

import android.content.Context
import com.foldfinance.app.BuildConfig
import com.foldfinance.app.ingest.IngestCoroutineScope
import com.foldfinance.core.data.settings.IngestSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory copy of the notification whitelist, so `onNotificationPosted` can decide by package
 * name alone, synchronously, before touching anything else of the notification.
 *
 * Effective whitelist = the user's packages (`IngestSettings.pushPackageWhitelist`), plus our own
 * package **in debug builds only** (for «Отправить тестовое банковское уведомление»). With
 * auto-tracking off the effective whitelist is empty.
 *
 * @param debugOwnPackage own package to allow, or `null` (release).
 */
class PushWhitelist(
    settings: IngestSettings,
    scope: CoroutineScope,
    private val debugOwnPackage: String?,
) {
    @Singleton
    class Provider @Inject constructor(
        settings: IngestSettings,
        @IngestCoroutineScope scope: CoroutineScope,
        @ApplicationContext context: Context,
    ) {
        val whitelist: PushWhitelist =
            PushWhitelist(settings, scope, debugOwnPackage = if (BuildConfig.DEBUG) context.packageName else null)
    }

    /** `null` until DataStore has been read once. */
    private val effective: StateFlow<Set<String>?> =
        combine(settings.pushPackageWhitelist, settings.autoTrackingEnabled) { packages, tracking ->
            when {
                !tracking -> emptySet()
                debugOwnPackage != null -> packages + debugOwnPackage
                else -> packages
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** `true`/`false` if known right now, `null` while the whitelist is still loading. */
    fun allowsNow(packageName: String): Boolean? = effective.value?.contains(packageName)

    /** Waits for the whitelist to load, then answers. */
    suspend fun allows(packageName: String): Boolean = packageName in effective.filterNotNull().first()
}
