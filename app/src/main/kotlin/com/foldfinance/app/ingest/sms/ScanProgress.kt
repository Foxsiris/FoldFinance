package com.foldfinance.app.ingest.sms

import androidx.work.Data
import androidx.work.workDataOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live counters of an SMS scan.
 *
 * Only the counters go through WorkManager ([toData]: `setProgress` and the worker output): its
 * database is **not encrypted**, so money sums never go there. The sums of a running/finished full
 * scan live in memory only ([LiveScanProgress]) and are merged back for the UI by
 * `IngestScheduler.observeFullScan`.
 *
 * @property scanned inbox messages looked at.
 * @property total inbox messages to look at (`0` when unknown, e.g. a delta scan).
 * @property found operations found in the scanned SMS (new or already known — a rescan shows the
 *   same totals as the first pass).
 * @property incomeCount / [expenseCount] operations by direction; refunds and internal transfers
 *   are counted in [found] only.
 * @property incomeRubMinor / [expenseRubMinor] sums in kopecks of RUB operations only: totals are
 *   never mixed across currencies. Meaningful only when [sumsKnown].
 * @property sumsKnown `false` when the counters came from WorkManager data and the in-memory sums
 *   are gone (process restarted after the scan): the UI then shows counts without sums.
 */
data class ScanProgress(
    val scanned: Int = 0,
    val total: Int = 0,
    val found: Int = 0,
    val incomeCount: Int = 0,
    val expenseCount: Int = 0,
    val incomeRubMinor: Long = 0,
    val expenseRubMinor: Long = 0,
    val sumsKnown: Boolean = true,
) {
    /** 0..1, or `null` when [total] is unknown. */
    val fraction: Float? get() = if (total > 0) (scanned.toFloat() / total).coerceIn(0f, 1f) else null

    /** Counters only — never sums (see the class docs). */
    fun toData(): Data = workDataOf(
        KEY_SCANNED to scanned,
        KEY_TOTAL to total,
        KEY_FOUND to found,
        KEY_INCOME_COUNT to incomeCount,
        KEY_EXPENSE_COUNT to expenseCount,
    )

    /** These counters with the sums of [live] (the same scan, kept in memory); without it — sums unknown. */
    fun withSumsFrom(live: ScanProgress?): ScanProgress =
        if (live == null || !live.sumsKnown) {
            copy(incomeRubMinor = 0, expenseRubMinor = 0, sumsKnown = false)
        } else {
            copy(incomeRubMinor = live.incomeRubMinor, expenseRubMinor = live.expenseRubMinor, sumsKnown = true)
        }

    companion object {
        private const val KEY_SCANNED = "scanned"
        private const val KEY_TOTAL = "total"
        private const val KEY_FOUND = "found"
        private const val KEY_INCOME_COUNT = "income_count"
        private const val KEY_EXPENSE_COUNT = "expense_count"

        /** Counters from worker progress/output (sums unknown); `null` if [data] carries none. */
        fun fromData(data: Data): ScanProgress? {
            if (!data.keyValueMap.containsKey(KEY_SCANNED)) return null
            return ScanProgress(
                scanned = data.getInt(KEY_SCANNED, 0),
                total = data.getInt(KEY_TOTAL, 0),
                found = data.getInt(KEY_FOUND, 0),
                incomeCount = data.getInt(KEY_INCOME_COUNT, 0),
                expenseCount = data.getInt(KEY_EXPENSE_COUNT, 0),
                sumsKnown = false,
            )
        }
    }
}

/**
 * Progress of the current (or last) full SMS scan in this process, sums included. In memory only:
 * the sums must not be persisted outside the encrypted database. Written by [SmsScanner].
 */
@Singleton
class LiveScanProgress @Inject constructor() {
    private val state = MutableStateFlow<ScanProgress?>(null)

    /** `null` — no full scan ran in this process. */
    val progress: StateFlow<ScanProgress?> = state.asStateFlow()

    internal fun publish(progress: ScanProgress) {
        state.value = progress
    }
}
