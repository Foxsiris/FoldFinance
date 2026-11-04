package com.foldfinance.app.ingest.sms

import com.foldfinance.core.data.db.dao.RawMessageDao
import com.foldfinance.core.data.db.dao.TransactionDao
import com.foldfinance.core.data.ingest.IngestOutcome
import com.foldfinance.parser.Direction
import javax.inject.Inject

/** Amount and direction of one operation, for scan counters only. */
data class OperationBrief(val direction: Direction, val amountMinor: Long, val currency: String)

/** Which operations an ingested SMS stands for — feeds the scan counters. */
fun interface OperationLookup {
    suspend fun operationsOf(outcome: IngestOutcome): List<OperationBrief>
}

/**
 * Looks the operations up in the database, so the counters reflect the final direction
 * (internal transfers, merges with push) and a rescan reports the same totals as the first pass.
 */
class DatabaseOperationLookup @Inject constructor(
    private val transactions: TransactionDao,
    private val rawMessages: RawMessageDao,
) : OperationLookup {

    override suspend fun operationsOf(outcome: IngestOutcome): List<OperationBrief> {
        val entities = when (outcome) {
            is IngestOutcome.Recorded -> transactions.getByIds(outcome.transactionIds)
            is IngestOutcome.AlreadyProcessed -> {
                val own = transactions.getByRawMessage(outcome.rawMessageId)
                own.ifEmpty {
                    // Merged into an operation created from another message (cross-source dedup).
                    val linked = rawMessages.getById(outcome.rawMessageId)?.transactionId
                    listOfNotNull(linked?.let { transactions.getById(it) })
                }
            }
            is IngestOutcome.NotFinancial, is IngestOutcome.Unrecognized -> emptyList()
        }
        return entities.map { OperationBrief(it.direction, it.amountMinor, it.currency) }
    }
}
