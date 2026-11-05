package com.foldfinance.app.ingest

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.foldfinance.app.BuildConfig
import com.foldfinance.core.data.ingest.ReparseUseCase
import com.foldfinance.core.data.settings.IngestSettings
import com.foldfinance.parser.PARSER_VERSION
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Background re-parse after a parser upgrade (`PARSER_VERSION`):
 * 1. stored `RawMessage`s of an older version go through [ReparseUseCase] (manual edits are kept);
 * 2. SMS that were not stored because the old parser called them not financial are re-read from
 *    the system inbox by a quiet full pass — only if a full pass happened before (onboarding done)
 *    with an older parser, SMS access is granted and auto-tracking is on.
 *
 * Enqueued with KEEP at app start / by the delta scan; returns at once when there is nothing to do.
 *
 * Errors: retried at most [MAX_ATTEMPTS] times; the last attempt marks a message whose re-parse
 * keeps failing as FAILED (current parser version) and goes on, so it cannot block the rest forever.
 */
@HiltWorker
class ReparseWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reparse: ReparseUseCase,
    private val runtimePrefs: IngestRuntimePrefs,
    private val settings: IngestSettings,
    private val permissions: IngestPermissions,
    private val scheduler: IngestScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val report = if (reparse.pendingCount() > 0) reparse(skipFailing = runAttemptCount >= MAX_ATTEMPTS) else null
        val lastFull = runtimePrefs.lastFullScanParserVersion()
        if (lastFull in 1 until PARSER_VERSION && permissions.hasSms() && settings.autoTrackingEnabled()) {
            scheduler.startFullScan(quiet = true)
        }
        Result.success(workDataOf(KEY_MESSAGES to (report?.messages ?: 0)))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Timber.w("reparse failed: %s", e.javaClass.simpleName)
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        const val KEY_MESSAGES: String = "messages"
        const val MAX_ATTEMPTS: Int = 3
    }
}
