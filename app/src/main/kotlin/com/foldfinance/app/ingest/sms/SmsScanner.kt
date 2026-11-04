package com.foldfinance.app.ingest.sms

import com.foldfinance.core.data.ingest.IncomingMessage
import com.foldfinance.core.data.ingest.IngestRepository
import com.foldfinance.core.data.ingest.SourceKeys
import com.foldfinance.core.data.settings.IngestSettings
import com.foldfinance.parser.Direction
import com.foldfinance.parser.MessageChannel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Kind of SMS pass. */
enum class ScanMode {
    /** Reset the cursor and walk the whole inbox. Idempotent: known SMS are skipped by source key. */
    FULL,

    /** Only SMS after `lastProcessedSmsId`. */
    DELTA,
}

/**
 * Walks the SMS inbox after the cursor and hands admitted messages to [IngestRepository].
 *
 * Guarantees:
 * - **No SMS is lost.** The cursor (`IngestSettings.lastProcessedSmsId`) moves only past messages
 *   that were fully handled (stored, already known, or deliberately not admitted). If `ingest`
 *   throws, the cursor stays on the last handled message and the error propagates — the worker
 *   retries and the pass resumes there.
 * - **No duplicates.** `ingest` deduplicates by SMS `_id` and by content, so a full rescan and a
 *   delta scan racing a receiver-triggered scan never create a second operation.
 * - **One pass at a time** in the process ([mutex]); a second request waits and then finds nothing new.
 *
 * Privacy: which SMS are stored at all is decided by [SmsAdmission]. Nothing here logs texts or sums.
 */
@Singleton
class SmsScanner @Inject constructor(
    private val reader: SmsReader,
    private val ingest: IngestRepository,
    private val admission: SmsAdmission,
    private val settings: IngestSettings,
    private val lookup: OperationLookup,
) {
    private val mutex = Mutex()

    /**
     * Run one pass.
     * @param onProgress called after every page with the counters so far.
     * @return final counters.
     */
    suspend fun scan(
        mode: ScanMode,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        onProgress: suspend (ScanProgress) -> Unit = {},
    ): ScanProgress = mutex.withLock {
        require(pageSize > 0)
        if (mode == ScanMode.FULL) settings.resetLastProcessedSmsId()
        var cursor = settings.lastProcessedSmsId()
        var progress = ScanProgress(total = if (mode == ScanMode.FULL) reader.count(cursor) else 0)
        onProgress(progress)

        while (true) {
            currentCoroutineContext().ensureActive()
            val page = reader.readPage(afterId = cursor, limit = pageSize)
            if (page.isEmpty()) break
            var handled = cursor
            try {
                for (sms in page) {
                    currentCoroutineContext().ensureActive()
                    if (sms.id <= handled) continue // provider returned an out-of-order row
                    progress = handle(sms, progress)
                    handled = sms.id
                }
            } finally {
                // Persist what was really handled, even if the page broke off half way.
                if (handled > cursor) withContext(NonCancellable) { settings.advanceLastProcessedSmsId(handled) }
            }
            if (handled <= cursor) break // nothing advanced: guard against a provider ignoring `_id > ?`
            cursor = handled
            if (progress.total < progress.scanned) progress = progress.copy(total = progress.scanned)
            onProgress(progress)
            if (page.size < pageSize) break
        }
        if (BUILD_LOGS) Timber.d("sms %s scan done: scanned=%d found=%d", mode, progress.scanned, progress.found)
        progress
    }

    private suspend fun handle(sms: SmsRecord, progress: ScanProgress): ScanProgress {
        val scanned = progress.copy(scanned = progress.scanned + 1)
        if (!admission.admit(sms.address, sms.body, sms.dateMillis)) return scanned
        val outcome = ingest.ingest(
            IncomingMessage(
                channel = MessageChannel.SMS,
                sender = sms.address,
                sourceKey = SourceKeys.sms(sms.id),
                receivedAt = Instant.ofEpochMilli(sms.dateMillis),
                text = sms.body,
            ),
        )
        return lookup.operationsOf(outcome).fold(scanned) { acc, op -> acc.add(op) }
    }

    private fun ScanProgress.add(op: OperationBrief): ScanProgress {
        val rub = op.currency == RUB
        return when (op.direction) {
            Direction.INCOME -> copy(
                found = found + 1,
                incomeCount = incomeCount + 1,
                incomeRubMinor = if (rub) incomeRubMinor + op.amountMinor else incomeRubMinor,
            )
            Direction.EXPENSE -> copy(
                found = found + 1,
                expenseCount = expenseCount + 1,
                expenseRubMinor = if (rub) expenseRubMinor + op.amountMinor else expenseRubMinor,
            )
            Direction.REFUND, Direction.INTERNAL_TRANSFER -> copy(found = found + 1)
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE: Int = 200
        private const val RUB = "RUB"
        private val BUILD_LOGS = com.foldfinance.app.BuildConfig.DEBUG
    }
}
