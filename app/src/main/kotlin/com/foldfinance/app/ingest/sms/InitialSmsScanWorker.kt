package com.foldfinance.app.ingest.sms

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.foldfinance.app.BuildConfig
import com.foldfinance.app.ingest.IngestPermissions
import com.foldfinance.app.ingest.IngestRuntimePrefs
import com.foldfinance.parser.PARSER_VERSION
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Full SMS pass: the first import of the history (onboarding) and «Сканировать SMS заново»
 * (settings). Resets the cursor and walks the whole inbox; idempotent.
 *
 * Runs expedited and promotes itself to a `dataSync` foreground service with a progress
 * notification, so it keeps going when the app is minimised. If the system refuses the foreground
 * service (app already in background), it continues as ordinary work. Counters are published with
 * `setProgress` after every page and returned as output ([ScanProgress.toData]).
 *
 * Input [KEY_QUIET]: no foreground notification — the automatic re-pass after a parser upgrade.
 */
@HiltWorker
class InitialSmsScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scanner: SmsScanner,
    private val permissions: IngestPermissions,
    private val runtimePrefs: IngestRuntimePrefs,
) : CoroutineWorker(context, params) {

    private val quiet: Boolean get() = inputData.getBoolean(KEY_QUIET, false)

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ScanNotifications.foregroundInfo(applicationContext, ScanProgress())

    override suspend fun doWork(): Result {
        if (!permissions.hasSms()) return Result.failure(ScanProgress().toData())
        if (!quiet) promote(ScanProgress())
        return try {
            var lastShown = -1
            val result = scanner.scan(ScanMode.FULL) { progress ->
                setProgress(progress.toData())
                if (!quiet && progress.scanned != lastShown) {
                    lastShown = progress.scanned
                    promote(progress)
                }
            }
            runtimePrefs.setLastFullScanParserVersion(PARSER_VERSION)
            Result.success(result.toData())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Timber.w("full sms scan failed: %s", e.javaClass.simpleName)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private suspend fun promote(progress: ScanProgress) {
        try {
            setForeground(ScanNotifications.foregroundInfo(applicationContext, progress))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException & co: keep scanning as regular work.
            if (BuildConfig.DEBUG) Timber.d("foreground refused: %s", e.javaClass.simpleName)
        }
    }

    companion object {
        const val KEY_QUIET: String = "quiet"
        private const val MAX_ATTEMPTS = 5
    }
}
