package com.foldfinance.app.ingest.sms

import com.foldfinance.core.data.ingest.IncomingMessage
import com.foldfinance.core.data.ingest.IngestOutcome
import com.foldfinance.core.data.ingest.IngestRepository
import com.foldfinance.core.data.ingest.SourceKeys
import com.foldfinance.core.data.settings.IngestSettings
import com.foldfinance.parser.Direction
import com.foldfinance.parser.MessageChannel
import kotlinx.coroutines.CancellationException
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

    /** SMS after `lastProcessedSmsId`, plus a re-check of the last [SmsScanner.RECHECK_IDS] ids below it. */
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
 * - **Reused `_id`s are caught.** The AOSP `sms` table has no AUTOINCREMENT: after the newest SMS is
 *   deleted, the next one gets the same `_id`, i.e. an id at or below the cursor. The source key is
 *   therefore `_id|date|text hash` ([SourceKeys.sms]) and a delta pass first re-reads the last
 *   [RECHECK_IDS] ids up to the cursor; ids whose key is already stored are skipped without parsing
 *   ([IngestRepository.isKnown]), anything else goes through the normal idempotent ingest.
 * - **No duplicates.** `ingest` deduplicates by source key and by content, so a full rescan and a
 *   delta scan racing a receiver-triggered scan never create a second operation.
 * - **A poison message does not block the cursor forever.** With `skipFailing` (the worker's last
 *   attempt) an SMS whose ingest throws is stored as FAILED ([IngestRepository.recordFailed]) and
 *   the pass moves on; it shows up under «Не распознано».
 * - **One pass at a time** in the process ([mutex]); a second request waits and then finds nothing new.
 *
 * Privacy: which SMS are stored at all is decided by [SmsAdmission]. Nothing here logs texts or sums.
 * The sums of a full pass are published only in memory ([LiveScanProgress]).
 */
@Singleton
class SmsScanner @Inject constructor(
    private val reader: SmsReader,
    private val ingest: IngestRepository,
    private val admission: SmsAdmission,
    private val settings: IngestSettings,
    private val lookup: OperationLookup,
    private val live: LiveScanProgress,
) {
    private val mutex = Mutex()

    /**
     * Run one pass.
     * @param skipFailing store an SMS whose ingest throws as FAILED and go on, instead of stopping.
     * @param onProgress called after every page with the counters so far.
     * @return final counters.
     */
    suspend fun scan(
        mode: ScanMode,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        skipFailing: Boolean = false,
        onProgress: suspend (ScanProgress) -> Unit = {},
    ): ScanProgress = mutex.withLock {
        require(pageSize > 0)
        if (mode == ScanMode.FULL) settings.resetLastProcessedSmsId()
        var cursor = settings.lastProcessedSmsId()
        if (mode == ScanMode.DELTA && cursor > 0) recheckUpTo(cursor, pageSize, skipFailing)

        var progress = ScanProgress(total = if (mode == ScanMode.FULL) reader.count(cursor) else 0)
        report(mode, progress, onProgress)

        while (true) {
            currentCoroutineContext().ensureActive()
            val page = reader.readPage(afterId = cursor, limit = pageSize)
            if (page.isEmpty()) break
            var handled = cursor
            try {
                for (sms in page) {
                    currentCoroutineContext().ensureActive()
                    if (sms.id <= handled) continue // provider returned an out-of-order row
                    progress = handle(sms, progress, skipFailing)
                    handled = sms.id
                }
            } finally {
                // Persist what was really handled, even if the page broke off half way.
                if (handled > cursor) withContext(NonCancellable) { settings.advanceLastProcessedSmsId(handled) }
            }
            if (handled <= cursor) break // nothing advanced: guard against a provider ignoring `_id > ?`
            cursor = handled
            if (progress.total < progress.scanned) progress = progress.copy(total = progress.scanned)
            report(mode, progress, onProgress)
            if (page.size < pageSize) break
        }
        if (BUILD_LOGS) Timber.d("sms %s scan done: scanned=%d found=%d", mode, progress.scanned, progress.found)
        progress
    }

    private suspend fun report(mode: ScanMode, progress: ScanProgress, onProgress: suspend (ScanProgress) -> Unit) {
        if (mode == ScanMode.FULL) live.publish(progress)
        onProgress(progress)
    }

    /**
     * Re-read the ids `cursor - RECHECK_IDS + 1 .. cursor`: an SMS that took over the `_id` of a
     * deleted one has a new key and is ingested; already stored ones are skipped without parsing.
     * Does not move the cursor and does not count in the progress.
     */
    private suspend fun recheckUpTo(cursor: Long, pageSize: Int, skipFailing: Boolean) {
        var after = (cursor - RECHECK_IDS).coerceAtLeast(0)
        while (after < cursor) {
            currentCoroutineContext().ensureActive()
            val page = reader.readPage(afterId = after, limit = pageSize)
            val rows = page.filter { it.id > after && it.id <= cursor }
            for (sms in rows) {
                currentCoroutineContext().ensureActive()
                if (ingest.isKnown(MessageChannel.SMS, sms.sourceKey())) continue
                admitAndIngest(sms, skipFailing)
            }
            if (page.size < pageSize || rows.size < page.size || rows.isEmpty()) break
            after = rows.maxOf { it.id }
        }
    }

    private suspend fun handle(sms: SmsRecord, progress: ScanProgress, skipFailing: Boolean): ScanProgress {
        val scanned = progress.copy(scanned = progress.scanned + 1)
        val outcome = admitAndIngest(sms, skipFailing) ?: return scanned
        return lookup.operationsOf(outcome).fold(scanned) { acc, op -> acc.add(op) }
    }

    /** @return `null` when the SMS is not admitted (never stored). */
    private suspend fun admitAndIngest(sms: SmsRecord, skipFailing: Boolean): IngestOutcome? {
        if (!admission.admit(sms.address, sms.body, sms.dateMillis)) return null
        val message = IncomingMessage(
            channel = MessageChannel.SMS,
            sender = sms.address,
            sourceKey = sms.sourceKey(),
            receivedAt = Instant.ofEpochMilli(sms.dateMillis),
            text = sms.body,
        )
        return try {
            ingest.ingest(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!skipFailing) throw e
            if (BUILD_LOGS) Timber.w("sms ingest failed, stored as FAILED: %s", e.javaClass.simpleName)
            ingest.recordFailed(message)
        }
    }

    private fun SmsRecord.sourceKey(): String = SourceKeys.sms(id, dateMillis, body)

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

        /** How many `_id`s up to the cursor a delta pass re-checks for reused ids. */
        const val RECHECK_IDS: Long = 50
        private const val RUB = "RUB"
        private val BUILD_LOGS = com.foldfinance.app.BuildConfig.DEBUG
    }
}
