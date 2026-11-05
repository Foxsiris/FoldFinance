package com.foldfinance.app.ingest.push

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.foldfinance.app.BuildConfig
import com.foldfinance.app.ingest.IngestCoroutineScope
import com.foldfinance.core.data.ingest.IncomingMessage
import com.foldfinance.core.data.ingest.IngestRepository
import com.foldfinance.core.data.ingest.SourceKeys
import com.foldfinance.parser.MessageChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Text of a whitelisted notification.
 *
 * @property key `StatusBarNotification.key`; with [postTimeMillis] and the text hash it is the
 *   source identity ([SourceKeys.push]).
 */
data class PushContent(
    val key: String,
    val postTimeMillis: Long,
    val title: String?,
    val text: String,
) {
    override fun toString(): String = "PushContent(key=<redacted>, postTimeMillis=$postTimeMillis)"

    companion object {
        /**
         * Reads title and text (`EXTRA_BIG_TEXT` when present — it is the full text, otherwise
         * `EXTRA_TEXT`). Group summaries and ongoing notifications (players, foreground services)
         * are not operations: `null`.
         */
        fun from(sbn: StatusBarNotification): PushContent? {
            val notification = sbn.notification ?: return null
            if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
            if (sbn.isOngoing) return null
            val extras = notification.extras ?: return null
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            val plain = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            val text = big?.takeIf { it.isNotEmpty() } ?: plain?.takeIf { it.isNotEmpty() } ?: return null
            return PushContent(key = sbn.key, postTimeMillis = sbn.postTime, title = title, text = text)
        }
    }
}

/**
 * The whole of `onNotificationPosted`, testable without a bound listener.
 *
 * 1. **First and only check on the callback thread: the package name against [PushWhitelist].**
 *    Not whitelisted → return immediately: the notification content is never read
 *    ([onPosted]'s `readContent` is not invoked) and nothing is stored.
 * 2. Whitelisted → read the text, then ingest on the ingest scope: no database work in the
 *    callback. (Not WorkManager: its input data is stored unencrypted, and the text must only ever
 *    land in the encrypted database.)
 * 3. Whitelist still loading (first notification after process start) → wait for it off the
 *    callback thread, then apply the same rule; the content is read only after a positive answer.
 * 4. A push cannot be re-read later, so a failing `ingest` is retried with [retryDelaysMillis]
 *    pauses; if every attempt fails, the text is stored as FAILED ([IngestRepository.recordFailed])
 *    when possible, and the failure is counted in [ListenerHealth.ingestFailures] for settings.
 */
@Singleton
class PushNotificationHandler internal constructor(
    private val whitelist: PushWhitelist,
    private val ingest: IngestRepository,
    private val scope: CoroutineScope,
    private val health: ListenerHealth? = null,
    private val retryDelaysMillis: List<Long> = DEFAULT_RETRY_DELAYS_MILLIS,
) {
    @Inject constructor(
        provider: PushWhitelist.Provider,
        ingest: IngestRepository,
        @IngestCoroutineScope scope: CoroutineScope,
        health: ListenerHealth,
    ) : this(provider.whitelist, ingest, scope, health)

    /**
     * @param packageName `StatusBarNotification.packageName` — the only thing read before the check.
     * @param readContent reads the text; called only for whitelisted packages.
     */
    fun onPosted(packageName: String, readContent: () -> PushContent?) {
        when (whitelist.allowsNow(packageName)) {
            false -> return
            true -> {
                val content = readContent() ?: return
                scope.launch { ingest(packageName, content) }
            }
            null -> scope.launch {
                if (!whitelist.allows(packageName)) return@launch
                val content = readContent() ?: return@launch
                ingest(packageName, content)
            }
        }
    }

    private suspend fun ingest(packageName: String, content: PushContent) {
        val message = IncomingMessage(
            channel = MessageChannel.PUSH,
            sender = packageName,
            sourceKey = SourceKeys.push(content.key, content.postTimeMillis, content.text),
            receivedAt = Instant.ofEpochMilli(content.postTimeMillis),
            text = content.text,
            title = content.title,
        )
        var attempt = 0
        while (true) {
            try {
                ingest.ingest(message)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Timber.w("push ingest attempt %d failed: %s", attempt + 1, e.javaClass.simpleName)
                val pause = retryDelaysMillis.getOrNull(attempt++) ?: break
                delay(pause)
            }
        }
        val stored = try {
            ingest.recordFailed(message)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Timber.w("push lost: %s", e.javaClass.simpleName)
            false
        }
        health?.onIngestFailed(stored)
    }

    companion object {
        /** Pauses before the 2nd and 3rd attempts. */
        val DEFAULT_RETRY_DELAYS_MILLIS: List<Long> = listOf(500, 2_000)
    }
}
