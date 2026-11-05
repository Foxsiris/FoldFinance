package com.foldfinance.app.ingest.sms

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.foldfinance.app.BuildConfig
import com.foldfinance.app.ingest.IngestPermissions
import com.foldfinance.app.ingest.IngestScheduler
import com.foldfinance.core.data.ingest.ReparseUseCase
import com.foldfinance.core.data.settings.IngestSettings
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Delta scan: SMS after `lastProcessedSmsId`. Runs periodically (safety net when the receiver
 * did not fire — process killed, Samsung battery limits) and right after every `SMS_RECEIVED`.
 *
 * With auto-tracking off it does nothing and leaves the cursor where it is, so turning tracking
 * back on catches up with everything that arrived meanwhile. Also kicks [ReparseWorker] when
 * stored messages wait for a newer parser.
 *
 * Errors: retried at most [MAX_ATTEMPTS] times; the last attempt runs with `skipFailing`, so a
 * poison SMS is stored as FAILED and the cursor moves past it instead of retrying forever.
 */
@HiltWorker
class DeltaSmsScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scanner: SmsScanner,
    private val permissions: IngestPermissions,
    private val settings: IngestSettings,
    private val reparse: ReparseUseCase,
    private val scheduler: IngestScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settings.autoTrackingEnabled() || !permissions.hasSms()) return Result.success()
        return try {
            val result = scanner.scan(ScanMode.DELTA, skipFailing = runAttemptCount >= MAX_ATTEMPTS)
            if (reparse.pendingCount() > 0) scheduler.ensureReparse()
            Result.success(result.toData())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Timber.w("delta sms scan failed: %s", e.javaClass.simpleName)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        /** Retries before the attempt that skips failing messages; the periodic run comes again anyway. */
        const val MAX_ATTEMPTS: Int = 3
    }
}
