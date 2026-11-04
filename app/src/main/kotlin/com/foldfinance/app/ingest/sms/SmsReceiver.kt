package com.foldfinance.app.ingest.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.foldfinance.app.ingest.IngestScheduler

/**
 * New SMS (`SMS_RECEIVED`, sent only by the system — `BROADCAST_SMS` in the manifest).
 *
 * Does no parsing and reads no PDUs on the main thread: it only enqueues delta scans
 * ([IngestScheduler.requestDeltaAfterSms]). The scan reads the message from the SMS provider by its
 * `_id`, so the source identity is the same as for the history import and nothing is stored twice.
 * Auto-tracking is checked by the worker. If this receiver never fires (process killed, battery
 * limits), the periodic delta scan still picks the SMS up.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        IngestScheduler(context).requestDeltaAfterSms()
    }
}
