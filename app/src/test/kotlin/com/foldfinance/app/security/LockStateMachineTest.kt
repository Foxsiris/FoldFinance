package com.foldfinance.app.security

import com.foldfinance.app.prefs.RelockTimeout
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit

class LockStateMachineTest {

    private var now = 1_000_000L
    private val machine = LockStateMachine { now }

    private fun advance(minutes: Long = 0, seconds: Long = 0) {
        now += TimeUnit.MINUTES.toMillis(minutes) + TimeUnit.SECONDS.toMillis(seconds)
    }

    private fun backgroundFor(minutes: Long = 0, seconds: Long = 0) {
        machine.onBackground()
        advance(minutes, seconds)
        machine.onForeground()
    }

    @Test
    fun loadingUntilSettingsKnown_thenLockedOnProcessStart() {
        assertThat(machine.state).isEqualTo(LockState.LOADING)
        machine.configure(enabled = true, timeoutMillis = RelockTimeout.ONE_MINUTE.millis)
        assertThat(machine.state).isEqualTo(LockState.LOCKED)
        machine.unlock()
        assertThat(machine.state).isEqualTo(LockState.UNLOCKED)
    }

    @Test
    fun disabledLock_neverLocks() {
        machine.configure(enabled = false, timeoutMillis = 0)
        assertThat(machine.state).isEqualTo(LockState.UNLOCKED)
        backgroundFor(minutes = 60)
        assertThat(machine.state).isEqualTo(LockState.UNLOCKED)
    }

    @Test
    fun relocksOnlyAfterTimeoutInBackground() {
        machine.configure(enabled = true, timeoutMillis = RelockTimeout.FIVE_MINUTES.millis)
        machine.unlock()

        backgroundFor(minutes = 4, seconds = 59)
        assertThat(machine.state).isEqualTo(LockState.UNLOCKED)

        backgroundFor(minutes = 5)
        assertThat(machine.state).isEqualTo(LockState.LOCKED)
    }

    @Test
    fun immediately_relocksOnEveryReturn() {
        machine.configure(enabled = true, timeoutMillis = RelockTimeout.IMMEDIATELY.millis)
        machine.unlock()
        backgroundFor()
        assertThat(machine.state).isEqualTo(LockState.LOCKED)
    }

    @Test
    fun timeInForeground_doesNotCount() {
        machine.configure(enabled = true, timeoutMillis = RelockTimeout.ONE_MINUTE.millis)
        machine.unlock()
        advance(minutes = 30) // в приложении, не в фоне
        backgroundFor(seconds = 30)
        assertThat(machine.state).isEqualTo(LockState.UNLOCKED)
    }

    @Test
    fun enablingInSettings_keepsCurrentSession_disablingUnlocks() {
        machine.configure(enabled = false, timeoutMillis = 0)
        machine.configure(enabled = true, timeoutMillis = 0)
        assertThat(machine.state).isEqualTo(LockState.UNLOCKED)

        backgroundFor()
        assertThat(machine.state).isEqualTo(LockState.LOCKED)
        machine.configure(enabled = false, timeoutMillis = 0)
        assertThat(machine.state).isEqualTo(LockState.UNLOCKED)
    }

    @Test
    fun externalActivity_getsGraceOnce() {
        machine.configure(enabled = true, timeoutMillis = RelockTimeout.IMMEDIATELY.millis)
        machine.unlock()

        machine.allowExternalActivity()
        backgroundFor(minutes = 2)
        assertThat(machine.state).isEqualTo(LockState.UNLOCKED)

        // Льгота одноразовая и ограничена по времени.
        backgroundFor(seconds = 1)
        assertThat(machine.state).isEqualTo(LockState.LOCKED)

        machine.unlock()
        machine.allowExternalActivity()
        backgroundFor(minutes = 3)
        assertThat(machine.state).isEqualTo(LockState.LOCKED)
    }

    @Test
    fun longerTimeout_winsOverGrace() {
        machine.configure(enabled = true, timeoutMillis = RelockTimeout.FIFTEEN_MINUTES.millis)
        machine.unlock()
        machine.allowExternalActivity()
        backgroundFor(minutes = 10)
        assertThat(machine.state).isEqualTo(LockState.UNLOCKED)
    }

    @Test
    fun foregroundWithoutBackground_isIgnored() {
        machine.configure(enabled = true, timeoutMillis = 0)
        machine.unlock()
        machine.onForeground()
        assertThat(machine.state).isEqualTo(LockState.UNLOCKED)
    }
}
