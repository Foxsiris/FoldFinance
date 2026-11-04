package com.foldfinance.app.ingest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime state of the ingest layer (DataStore `ingest_runtime`), separate from the user-facing
 * [com.foldfinance.core.data.settings.IngestSettings].
 *
 * - [onboardingCompleted] — the access onboarding was finished or skipped; the shell stops showing it.
 * - [lastFullScanParserVersion] — parser version of the last complete SMS pass. SMS the parser
 *   called not financial are not stored (see [com.foldfinance.app.ingest.sms.SmsAdmission]); after
 *   a parser upgrade a quiet full pass re-reads them from the system inbox.
 */
@Singleton
class IngestRuntimePrefs @Inject constructor(
    @IngestRuntimeStore private val store: DataStore<Preferences>,
) {
    val onboardingCompleted: Flow<Boolean> =
        store.data.map { it[KEY_ONBOARDING_DONE] ?: false }.distinctUntilChanged()

    suspend fun setOnboardingCompleted(done: Boolean) {
        store.edit { it[KEY_ONBOARDING_DONE] = done }
    }

    suspend fun lastFullScanParserVersion(): Int = store.data.first()[KEY_FULL_SCAN_VERSION] ?: 0

    suspend fun setLastFullScanParserVersion(version: Int) {
        store.edit { it[KEY_FULL_SCAN_VERSION] = version }
    }

    private companion object {
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        val KEY_FULL_SCAN_VERSION = intPreferencesKey("full_scan_parser_version")
    }
}
