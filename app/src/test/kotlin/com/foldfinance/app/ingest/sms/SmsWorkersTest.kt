package com.foldfinance.app.ingest.sms

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.foldfinance.app.ingest.InMemoryPreferencesStore
import com.foldfinance.app.ingest.IngestPermissions
import com.foldfinance.app.ingest.IngestRuntimePrefs
import com.foldfinance.app.ingest.IngestScheduler
import com.foldfinance.app.ingest.Registry
import com.foldfinance.app.ingest.ScanHarness
import com.foldfinance.app.ingest.TestClock
import com.foldfinance.app.ingest.Texts
import com.foldfinance.app.ingest.sms
import com.foldfinance.core.data.ingest.ReparseUseCase
import com.foldfinance.parser.PARSER_VERSION
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.util.UUID

/** Workers through `work-testing` with a fake SMS inbox and an in-memory database. */
@RunWith(AndroidJUnit4::class)
class SmsWorkersTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var h: ScanHarness
    private val runtimePrefs = IngestRuntimePrefs(InMemoryPreferencesStore())

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        h = ScanHarness(listOf(sms(1, Texts.PURCHASE), sms(2, Texts.OTP), sms(3, Texts.REFUND)))
    }

    @After fun tearDown() = h.close()

    private fun grantSms() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
    }

    private val factory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, params: WorkerParameters): ListenableWorker? {
            val permissions = IngestPermissions(appContext)
            return when (workerClassName) {
                InitialSmsScanWorker::class.java.name -> InitialSmsScanWorker(appContext, params, h.scanner, permissions, runtimePrefs)
                DeltaSmsScanWorker::class.java.name -> DeltaSmsScanWorker(
                    appContext, params, h.scanner, permissions, h.settings,
                    ReparseUseCase(h.db, Registry, TestClock), IngestScheduler(appContext),
                )
                else -> null
            }
        }
    }

    private class RecordingProgress : ProgressUpdater {
        val updates = mutableListOf<Data>()
        override fun updateProgress(context: Context, id: UUID, data: Data): ListenableFuture<Void?> {
            updates += data
            return Futures.immediateFuture<Void?>(null)
        }
    }

    @Test fun `initial scan publishes progress and returns counters`() = runTest {
        grantSms()
        val progress = RecordingProgress()
        val worker = TestListenableWorkerBuilder<InitialSmsScanWorker>(context)
            .setWorkerFactory(factory)
            .setProgressUpdater(progress)
            .build()

        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        val output = ScanProgress.fromData((result as ListenableWorker.Result.Success).outputData)!!
        assertThat(output.scanned).isEqualTo(3)
        assertThat(output.found).isEqualTo(2)
        assertThat(progress.updates.mapNotNull { ScanProgress.fromData(it) }.last().scanned).isEqualTo(3)
        assertThat(runtimePrefs.lastFullScanParserVersion()).isEqualTo(PARSER_VERSION)
    }

    @Test fun `worker data carries counters only, never money sums`() = runTest {
        grantSms()
        val progress = RecordingProgress()
        val worker = TestListenableWorkerBuilder<InitialSmsScanWorker>(context)
            .setWorkerFactory(factory)
            .setProgressUpdater(progress)
            .build()

        val output = (worker.doWork() as ListenableWorker.Result.Success).outputData

        val counters = setOf("scanned", "total", "found", "income_count", "expense_count")
        assertThat(output.keyValueMap.keys).isEqualTo(counters)
        progress.updates.forEach { assertThat(it.keyValueMap.keys).isEqualTo(counters) }
        // The sums of the same scan live in memory only.
        assertThat(h.live.progress.value!!.expenseRubMinor).isEqualTo(150_000)
        val shown = ScanProgress.fromData(output)!!.withSumsFrom(h.live.progress.value)
        assertThat(shown.sumsKnown).isTrue()
        assertThat(shown.expenseRubMinor).isEqualTo(150_000)
        assertThat(ScanProgress.fromData(output)!!.withSumsFrom(null).sumsKnown).isFalse()
    }

    @Test fun `initial scan without sms permission fails and reads nothing`() = runTest {
        val worker = TestListenableWorkerBuilder<InitialSmsScanWorker>(context).setWorkerFactory(factory).build()

        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
        assertThat(h.reader.pageRequests).isEmpty()
    }

    @Test fun `delta worker takes only new ids`() = runTest {
        grantSms()
        h.settings.advanceLastProcessedSmsId(2)
        val worker = TestListenableWorkerBuilder<DeltaSmsScanWorker>(context).setWorkerFactory(factory).build()

        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        // 1 is below the cursor but was never stored: the re-check window picks it up (2 is an OTP).
        assertThat(h.ingest.received.map { it.sourceKey }).containsExactly(h.keyOf(1), h.keyOf(3)).inOrder()
        assertThat(h.settings.lastProcessedSmsId()).isEqualTo(3)
    }

    @Test fun `delta worker does nothing with auto tracking off and keeps the cursor`() = runTest {
        grantSms()
        h.settings.setAutoTrackingEnabled(false)
        val worker = TestListenableWorkerBuilder<DeltaSmsScanWorker>(context).setWorkerFactory(factory).build()

        worker.doWork()

        assertThat(h.reader.pageRequests).isEmpty()
        assertThat(h.settings.lastProcessedSmsId()).isEqualTo(0)
    }

    @Test fun `delta worker on its last attempt stores a poison sms as failed and moves on`() = runTest {
        grantSms()
        h.ingest.failOnSourceKeys = setOf(h.keyOf(3))
        val worker = TestListenableWorkerBuilder<DeltaSmsScanWorker>(context)
            .setWorkerFactory(factory)
            .setRunAttemptCount(DeltaSmsScanWorker.MAX_ATTEMPTS)
            .build()

        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        assertThat(h.settings.lastProcessedSmsId()).isEqualTo(3)
        assertThat(h.ingest.recordedFailed.map { it.sourceKey }).containsExactly(h.keyOf(3))
    }

    @Test fun `delta worker gives up instead of retrying forever`() = runTest {
        grantSms()
        h.ingest.failOnSourceKeys = setOf(h.keyOf(3))
        h.ingest.failRecordFailed = true
        val worker = TestListenableWorkerBuilder<DeltaSmsScanWorker>(context)
            .setWorkerFactory(factory)
            .setRunAttemptCount(DeltaSmsScanWorker.MAX_ATTEMPTS)
            .build()

        assertThat(worker.doWork()).isInstanceOf(ListenableWorker.Result.Failure::class.java)
        assertThat(h.settings.lastProcessedSmsId()).isEqualTo(2)
    }

    @Test fun `delta worker retries when writing fails, cursor stays before the failure`() = runTest {
        grantSms()
        h.ingest.failOnSourceKeys = setOf(h.keyOf(3))
        val worker = TestListenableWorkerBuilder<DeltaSmsScanWorker>(context).setWorkerFactory(factory).build()

        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Retry::class.java)
        assertThat(h.settings.lastProcessedSmsId()).isEqualTo(2)
    }
}
