package com.foldfinance.app.ingest.debug

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldfinance.app.R
import com.foldfinance.app.ingest.IngestPermissions
import com.foldfinance.core.data.ingest.IncomingMessage
import com.foldfinance.core.data.ingest.IngestOutcome
import com.foldfinance.core.data.ingest.IngestRepository
import com.foldfinance.parser.MessageChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** Result line under the debug buttons. */
enum class DebugResult {
    NotificationPosted,
    NotificationNoPermission,
    SmsRecorded,
    SmsAlreadyProcessed,
    SmsNotFinancial,
    SmsUnrecognized,
    Failed,
}

/** Debug-only actions for [IngestDebugTools]. */
@HiltViewModel
class IngestDebugViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ingest: IngestRepository,
    private val permissions: IngestPermissions,
) : ViewModel() {

    private val clock: Clock = Clock.systemUTC()

    private val _result = MutableStateFlow<DebugResult?>(null)
    val result: StateFlow<DebugResult?> = _result.asStateFlow()

    private var smsIndex = 0

    /**
     * Posts a bank-like notification from our own package. The listener lets it through only in
     * debug builds (own package is never whitelisted in release).
     */
    @SuppressLint("MissingPermission") // checked via IngestPermissions
    fun sendTestNotification() {
        if (!permissions.hasPostNotifications()) {
            _result.value = DebugResult.NotificationNoPermission
            return
        }
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.ingest_debug_channel_name))
                .build(),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(DebugSamples.PUSH_TITLE)
            .setContentText(DebugSamples.PUSH_TEXT)
            .setStyle(NotificationCompat.BigTextStyle().bigText(DebugSamples.PUSH_TEXT))
            .setAutoCancel(true)
            .build()
        manager.notify((clock.millis() % Int.MAX_VALUE).toInt(), notification)
        _result.value = DebugResult.NotificationPosted
    }

    /** Ingests a synthetic Sber SMS from `900` directly (no SMS provider involved). */
    fun insertTestSms() {
        val text = DebugSamples.sms[smsIndex++ % DebugSamples.sms.size]
        val now = Instant.now(clock)
        viewModelScope.launch {
            _result.value = try {
                when (
                    ingest.ingest(
                        IncomingMessage(
                            channel = MessageChannel.SMS,
                            sender = DebugSamples.SMS_SENDER,
                            sourceKey = "debug:${now.toEpochMilli()}",
                            receivedAt = now,
                            text = text,
                        ),
                    )
                ) {
                    is IngestOutcome.Recorded -> DebugResult.SmsRecorded
                    is IngestOutcome.AlreadyProcessed -> DebugResult.SmsAlreadyProcessed
                    is IngestOutcome.NotFinancial -> DebugResult.SmsNotFinancial
                    is IngestOutcome.Unrecognized -> DebugResult.SmsUnrecognized
                }
            } catch (e: Exception) {
                DebugResult.Failed
            }
        }
    }

    private companion object {
        const val CHANNEL_ID = "debug_bank"
    }
}
