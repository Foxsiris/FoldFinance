package com.foldfinance.app.ingest.push

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * Health of the notification listener: `onListenerConnected` / `onListenerDisconnected` land here
 * and settings show it. In-memory on purpose: a persisted «connected» would lie after the system
 * silently unbinds the listener (Samsung battery limits) and the process restarts.
 */
@Singleton
class ListenerHealth @Inject constructor(@ApplicationContext private val context: Context) {

    private val state = MutableStateFlow(ListenerStatus(ListenerConnection.UNKNOWN))
    val status: StateFlow<ListenerStatus> = state.asStateFlow()

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
