package com.foldfinance.app.ingest.sms

import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * One SMS from the system inbox: only the columns ingest needs.
 *
 * @property id `_id` in `content://sms` — the source identity and the delta-scan cursor.
 * @property dateMillis receive time (`date`), epoch millis.
 */
data class SmsRecord(
    val id: Long,
    val address: String,
    val dateMillis: Long,
    val body: String,
) {
    /** No body: message texts must never reach logs. */
    override fun toString(): String = "SmsRecord(id=$id, address=$address, dateMillis=$dateMillis)"
}

/** Reads the SMS inbox page by page in ascending `_id` order. */
interface SmsReader {
    /** Inbox messages with `_id > afterId`, ascending, at most [limit]. Requires READ_SMS. */
    suspend fun readPage(afterId: Long, limit: Int): List<SmsRecord>

    /** Number of inbox messages with `_id > afterId` (for the progress bar). */
    suspend fun count(afterId: Long): Int
}

/**
 * [SmsReader] over `Telephony.Sms.Inbox`. Queries only `_id, address, date, body`.
 *
 * The SMS provider has no Bundle-based LIMIT, so the page size goes into the sort order
 * (`_id ASC LIMIT n`); if a provider rejects that, the page is read from an unlimited query and the
 * cursor is closed after [limit] rows.
 */
class ContentResolverSmsReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : SmsReader {

    override suspend fun readPage(afterId: Long, limit: Int): List<SmsRecord> = withContext(Dispatchers.IO) {
        require(limit > 0)
        val cursor = try {
            query(sortOrder = "${Telephony.Sms._ID} ASC LIMIT $limit", afterId = afterId)
        } catch (e: IllegalArgumentException) {
            query(sortOrder = "${Telephony.Sms._ID} ASC", afterId = afterId)
        }
        cursor?.use { readRows(it, limit) } ?: emptyList()
    }

    override suspend fun count(afterId: Long): Int = withContext(Dispatchers.IO) {
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms._ID} > ?",
            arrayOf(afterId.toString()),
            null,
        )?.use { it.count } ?: 0
    }

    private fun query(sortOrder: String, afterId: Long): Cursor? = context.contentResolver.query(
        Telephony.Sms.Inbox.CONTENT_URI,
        PROJECTION,
        "${Telephony.Sms._ID} > ?",
        arrayOf(afterId.toString()),
        sortOrder,
    )

    private fun readRows(cursor: Cursor, limit: Int): List<SmsRecord> {
        val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
        val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
        val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
        val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
        val rows = ArrayList<SmsRecord>(minOf(limit, cursor.count.coerceAtLeast(0)))
        while (rows.size < limit && cursor.moveToNext()) {
            rows += SmsRecord(
                id = cursor.getLong(idCol),
                address = cursor.getString(addressCol).orEmpty(),
                dateMillis = cursor.getLong(dateCol),
                body = cursor.getString(bodyCol).orEmpty(),
            )
        }
        return rows
    }

    private companion object {
        val PROJECTION = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.BODY)
    }
}
