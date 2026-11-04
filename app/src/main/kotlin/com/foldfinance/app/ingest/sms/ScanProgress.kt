package com.foldfinance.app.ingest.sms

import androidx.work.Data
import androidx.work.workDataOf

/**
 * Live counters of an SMS scan, published through `setProgress` and as the worker output.
 *
 * @property scanned inbox messages looked at.
 * @property total inbox messages to look at (`0` when unknown, e.g. a delta scan).
 * @property found operations found in the scanned SMS (new or already known — a rescan shows the
 *   same totals as the first pass).
 * @property incomeCount / [expenseCount] operations by direction; refunds and internal transfers
 *   are counted in [found] only.
 * @property incomeRubMinor / [expenseRubMinor] sums in kopecks of RUB operations only: totals are
 *   never mixed across currencies.
 */
data class ScanProgress(
    val scanned: Int = 0,
    val total: Int = 0,
    val found: Int = 0,
    val incomeCount: Int = 0,
    val expenseCount: Int = 0,
    val incomeRubMinor: Long = 0,
    val expenseRubMinor: Long = 0,
) {
    /** 0..1, or `null` when [total] is unknown. */
    val fraction: Float? get() = if (total > 0) (scanned.toFloat() / total).coerceIn(0f, 1f) else null

    fun toData(): Data = workDataOf(
        KEY_SCANNED to scanned,
        KEY_TOTAL to total,
        KEY_FOUND to found,
        KEY_INCOME_COUNT to incomeCount,
        KEY_EXPENSE_COUNT to expenseCount,
        KEY_INCOME_RUB to incomeRubMinor,
        KEY_EXPENSE_RUB to expenseRubMinor,
    )

    companion object {
        private const val KEY_SCANNED = "scanned"
        private const val KEY_TOTAL = "total"
        private const val KEY_FOUND = "found"
        private const val KEY_INCOME_COUNT = "income_count"
        private const val KEY_EXPENSE_COUNT = "expense_count"
        private const val KEY_INCOME_RUB = "income_rub_minor"
        private const val KEY_EXPENSE_RUB = "expense_rub_minor"

        /** Counters from worker progress/output; `null` if [data] carries none. */
        fun fromData(data: Data): ScanProgress? {
            if (!data.keyValueMap.containsKey(KEY_SCANNED)) return null
            return ScanProgress(
                scanned = data.getInt(KEY_SCANNED, 0),
                total = data.getInt(KEY_TOTAL, 0),
                found = data.getInt(KEY_FOUND, 0),
                incomeCount = data.getInt(KEY_INCOME_COUNT, 0),
                expenseCount = data.getInt(KEY_EXPENSE_COUNT, 0),
                incomeRubMinor = data.getLong(KEY_INCOME_RUB, 0),
                expenseRubMinor = data.getLong(KEY_EXPENSE_RUB, 0),
            )
        }
    }
}
