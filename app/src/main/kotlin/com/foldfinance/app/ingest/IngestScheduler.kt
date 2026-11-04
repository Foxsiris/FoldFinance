package com.foldfinance.app.ingest

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.foldfinance.app.ingest.sms.DeltaSmsScanWorker
import com.foldfinance.app.ingest.sms.InitialSmsScanWorker
import com.foldfinance.app.ingest.sms.ScanProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** State of the full SMS scan as the UI shows it. */
sealed interface ScanState {
    /** Never started (or the record was pruned). */
    data object Idle : ScanState

    /** Enqueued or running; [progress] is the last published counter set. */
    data class Running(val progress: ScanProgress) : ScanState

    data class Finished(val progress: ScanProgress) : ScanState

    /** Gave up (no SMS access, repeated errors) or was cancelled. */
    data object Failed : ScanState
}

/**
 * All WorkManager scheduling of the ingest layer, by unique name:
 *
 * - [WORK_FULL_SCAN] — full pass ([InitialSmsScanWorker]), expedited + foreground, KEEP: a second
 *   tap on «Сканировать заново» while one runs does not restart it.
 * - [WORK_DELTA_PERIODIC] — periodic delta scan every 15 min (the WorkManager minimum), KEEP.
 * - [WORK_DELTA_NOW] / [WORK_DELTA_LATE] — delta scans right after `SMS_RECEIVED`: the broadcast
 *   comes before the default SMS app writes the message to the provider, so one scan runs after a
 *   short delay and a second one later as a safety net.
 * - [WORK_REPARSE] — [ReparseWorker], KEEP.
 *
 * Constructible without Hilt (the receiver uses `IngestScheduler(context)`).
 */
@Singleton
class IngestScheduler @Inject constructor(@ApplicationContext context: Context) {

    private val appContext = context.applicationContext
    private val workManager: WorkManager get() = WorkManager.getInstance(appContext)

    /**
     * Start a full pass.
     * @param quiet no foreground notification, not expedited (automatic re-pass after a parser upgrade).
     */
    fun startFullScan(quiet: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<InitialSmsScanWorker>()
            .setInputData(workDataOf(InitialSmsScanWorker.KEY_QUIET to quiet))
            .apply { if (!quiet) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
            .build()
        workManager.enqueueUniqueWork(WORK_FULL_SCAN, ExistingWorkPolicy.KEEP, request)
    }

    /** Periodic delta scan; safe to call often. */
    fun schedulePeriodicDelta() {
        val request = PeriodicWorkRequestBuilder<DeltaSmsScanWorker>(DELTA_PERIOD_MINUTES, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork(WORK_DELTA_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Delta scans after a new SMS arrived (see class docs). */
    fun requestDeltaAfterSms() {
        val now = OneTimeWorkRequestBuilder<DeltaSmsScanWorker>()
            .setInitialDelay(DELTA_NOW_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_DELTA_NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, now)
        val late = OneTimeWorkRequestBuilder<DeltaSmsScanWorker>()
            .setInitialDelay(DELTA_LATE_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_DELTA_LATE, ExistingWorkPolicy.REPLACE, late)
    }

    fun ensureReparse() {
        val request = OneTimeWorkRequestBuilder<ReparseWorker>().build()
        workManager.enqueueUniqueWork(WORK_REPARSE, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Everything that must exist after the app starts: the periodic delta scan and a reparse check.
     * Idempotent (KEEP policies). Called on app start, when the listener connects, after onboarding.
     */
    fun ensureScheduled() {
        schedulePeriodicDelta()
        ensureReparse()
    }

    /** Live state of the full scan. */
    fun observeFullScan(): Flow<ScanState> =
        workManager.getWorkInfosForUniqueWorkFlow(WORK_FULL_SCAN)
            .map { infos -> infos.lastOrNull()?.toScanState() ?: ScanState.Idle }
            .distinctUntilChanged()

    companion object {
        const val WORK_FULL_SCAN: String = "ingest.sms.full_scan"
        const val WORK_DELTA_PERIODIC: String = "ingest.sms.delta_periodic"
        const val WORK_DELTA_NOW: String = "ingest.sms.delta_now"
        const val WORK_DELTA_LATE: String = "ingest.sms.delta_late"
        const val WORK_REPARSE: String = "ingest.reparse"

        const val DELTA_PERIOD_MINUTES: Long = 15
        const val DELTA_NOW_DELAY_SECONDS: Long = 3
        const val DELTA_LATE_DELAY_SECONDS: Long = 60

        internal fun WorkInfo.toScanState(): ScanState = when (state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED ->
                ScanState.Running(ScanProgress.fromData(progress) ?: ScanProgress())
            WorkInfo.State.SUCCEEDED -> ScanState.Finished(ScanProgress.fromData(outputData) ?: ScanProgress())
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> ScanState.Failed
        }
    }
}
