package com.foldfinance.app.ingest.push

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Binding state of [BankNotificationListener] in this process. */
enum class ListenerConnection {
    /** No callback yet in this process: not bound (or access not granted). */
    UNKNOWN,
    CONNECTED,
    DISCONNECTED,
}

data class ListenerStatus(val connection: ListenerConnection, val since: Instant? = null)

/**
 * Bank notifications that could not be recorded normally in this process (all retries of `ingest`
 * failed). [stored] of them were still saved as FAILED (visible in «Проверка → Не распознано»);
 * the rest ([lost]) are gone — a push cannot be re-read later.
 */
data class PushIngestFailures(
    val count: Int = 0,
    val stored: Int = 0,
    val lastAt: Instant? = null,
) {
    val lost: Int get() = count - stored
}

/**
 * Health of the notification listener: `onListenerConnected` / `onListenerDisconnected` land here
 * and settings show it. In-memory on purpose: a persisted «connected» would lie after the system
 * silently unbinds the listener (Samsung battery limits) and the process restarts.
 */
@Singleton
class ListenerHealth @Inject constructor(@ApplicationContext private val context: Context) {

    private val state = MutableStateFlow(ListenerStatus(ListenerConnection.UNKNOWN))
    val status: StateFlow<ListenerStatus> = state.asStateFlow()

    private val failures = MutableStateFlow(PushIngestFailures())

    /** Push ingest failures since process start, for the settings screen. In memory, like [status]. */
    val ingestFailures: StateFlow<PushIngestFailures> = failures.asStateFlow()

    /** All attempts to record a whitelisted notification failed; [stored] — saved as FAILED anyway. */
    fun onIngestFailed(stored: Boolean, now: Instant = Instant.now()) {
        failures.update { it.copy(count = it.count + 1, stored = it.stored + if (stored) 1 else 0, lastAt = now) }
    }

    /** The user has seen the failures (settings): start counting again. */
    fun clearIngestFailures() {
        failures.value = PushIngestFailures()
    }

    fun onConnected(now: Instant = Instant.now()) {
        state.value = ListenerStatus(ListenerConnection.CONNECTED, now)
    }

    fun onDisconnected(now: Instant = Instant.now()) {
        state.value = ListenerStatus(ListenerConnection.DISCONNECTED, now)
    }

    /**
     * Ask the system to rebind the listener («Переподключить»). Works only while notification
     * access is granted; otherwise the system ignores it.
     */
    fun requestRebind() {
        NotificationListenerService.requestRebind(componentName(context))
    }

    companion object {
        fun componentName(context: Context): ComponentName = ComponentName(context, BankNotificationListener::class.java)
    }
}
