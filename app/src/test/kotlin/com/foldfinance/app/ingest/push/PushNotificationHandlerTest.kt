package com.foldfinance.app.ingest.push

import android.app.Notification
import android.content.Context
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foldfinance.app.ingest.InMemoryPreferencesStore
import com.foldfinance.app.ingest.RecordingIngest
import com.foldfinance.app.ingest.Registry
import com.foldfinance.app.ingest.T0
import com.foldfinance.app.ingest.TestClock
import com.foldfinance.app.ingest.Texts
import com.foldfinance.app.ingest.inMemoryDb
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.ingest.IngestRepositoryImpl
import com.foldfinance.core.data.ingest.SourceKeys
import com.foldfinance.core.data.settings.IngestSettings
import com.foldfinance.parser.MessageChannel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whitelist rule of `onNotificationPosted` (CLAUDE.md, «Белый список уведомлений»): a
 * notification from a package outside the whitelist is dropped before anything else is read, and
 * nothing is stored.
 */
@RunWith(AndroidJUnit4::class)
class PushNotificationHandlerTest {

    private val bank = "ru.sberbankmobile"
    private val messenger = "org.telegram.messenger"

    private lateinit var db: FoldFinanceDatabase
    /** Ingest jobs only, so the test can wait for them. */
    private lateinit var scope: CoroutineScope

    /** Whitelist collectors (never complete). */
    private lateinit var whitelistScope: CoroutineScope
    private lateinit var ingest: RecordingIngest

    @Before fun setUp() {
        db = inMemoryDb()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        whitelistScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        ingest = RecordingIngest(IngestRepositoryImpl(db, Registry, TestClock))
    }

    @After fun tearDown() {
        scope.cancel()
        whitelistScope.cancel()
        db.close()
    }

    private fun content(text: String = Texts.TBANK_PURCHASE) =
        PushContent(key = "0|$bank|1|null|10001", postTimeMillis = T0.toEpochMilli(), title = "Банк", text = text)

    private fun handler(settings: IngestSettings, ownPackage: String? = null): Pair<PushNotificationHandler, PushWhitelist> {
        val whitelist = PushWhitelist(settings, whitelistScope, ownPackage)
        return PushNotificationHandler(whitelist, ingest, scope) to whitelist
    }

    private fun awaitIngestJobs() = runBlocking { scope.coroutineContext[Job]!!.children.toList().joinAll() }

    private fun rawCount(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM raw_messages").use { it.moveToFirst(); it.getInt(0) }

    @Test fun `notification outside the whitelist is not read, not ingested and not stored`() {
        val settings = IngestSettings(InMemoryPreferencesStore())
        runBlocking { settings.setPushPackageWhitelist(setOf(bank)) }
        val (handler, whitelist) = handler(settings)
        runBlocking { whitelist.allows(bank) } // whitelist loaded

        var contentRead = false
        handler.onPosted(messenger) { contentRead = true; content(Texts.PURCHASE) }
        awaitIngestJobs()

        assertThat(contentRead).isFalse()
        assertThat(ingest.received).isEmpty()
        assertThat(rawCount()).isEqualTo(0)
    }

    @Test fun `whitelisted notification is ingested as push with the push source key`() {
        val settings = IngestSettings(InMemoryPreferencesStore())
        runBlocking { settings.setPushPackageWhitelist(setOf(bank)) }
        val (handler, whitelist) = handler(settings)
        runBlocking { whitelist.allows(bank) }

        val pushed = content()
        handler.onPosted(bank) { pushed }
        awaitIngestJobs()

        val message = ingest.received.single()
        assertThat(message.channel).isEqualTo(MessageChannel.PUSH)
        assertThat(message.sender).isEqualTo(bank)
        assertThat(message.sourceKey).isEqualTo(SourceKeys.push(pushed.key, pushed.postTimeMillis, pushed.text))
        assertThat(rawCount()).isEqualTo(1)
    }

    @Test fun `while the whitelist is loading nothing is read until the answer is known`() {
        val gate = GatedStore()
        val settings = IngestSettings(gate)
        val (handler, whitelist) = handler(settings)
        assertThat(whitelist.allowsNow(messenger)).isNull()

        var contentRead = false
        handler.onPosted(messenger) { contentRead = true; content() }
        assertThat(contentRead).isFalse()

        gate.open() // DataStore answers: empty whitelist
        awaitIngestJobs()

        assertThat(contentRead).isFalse()
        assertThat(ingest.received).isEmpty()
        assertThat(rawCount()).isEqualTo(0)
    }

    @Test fun `auto tracking off drops even whitelisted packages`() {
        val settings = IngestSettings(InMemoryPreferencesStore())
        runBlocking {
            settings.setPushPackageWhitelist(setOf(bank))
            settings.setAutoTrackingEnabled(false)
        }
        val (handler, whitelist) = handler(settings)
        runBlocking { whitelist.allows(bank) }

        var contentRead = false
        handler.onPosted(bank) { contentRead = true; content() }
        awaitIngestJobs()

        assertThat(contentRead).isFalse()
        assertThat(rawCount()).isEqualTo(0)
    }

    private fun failingHandler(settings: IngestSettings, health: ListenerHealth): Pair<PushNotificationHandler, PushWhitelist> {
        val whitelist = PushWhitelist(settings, whitelistScope, null)
        return PushNotificationHandler(whitelist, ingest, scope, health, retryDelaysMillis = listOf(1, 1)) to whitelist
    }

    private fun failedRawCount(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM raw_messages WHERE parse_status = 'FAILED'")
            .use { it.moveToFirst(); it.getInt(0) }

    @Test fun `failing ingest is retried, then stored as failed and counted for settings`() {
        val settings = IngestSettings(InMemoryPreferencesStore())
        runBlocking { settings.setPushPackageWhitelist(setOf(bank)) }
        val health = ListenerHealth(ApplicationProvider.getApplicationContext())
        val (handler, whitelist) = failingHandler(settings, health)
        runBlocking { whitelist.allows(bank) }
        val pushed = content()
        ingest.failOnSourceKeys = setOf(SourceKeys.push(pushed.key, pushed.postTimeMillis, pushed.text))

        handler.onPosted(bank) { pushed }
        awaitIngestJobs()

        assertThat(ingest.ingestCalls).isEqualTo(3)
        assertThat(ingest.recordedFailed).hasSize(1)
        assertThat(failedRawCount()).isEqualTo(1)
        assertThat(health.ingestFailures.value.count).isEqualTo(1)
        assertThat(health.ingestFailures.value.lost).isEqualTo(0)
    }

    @Test fun `push that cannot be stored at all is counted as lost`() {
        val settings = IngestSettings(InMemoryPreferencesStore())
        runBlocking { settings.setPushPackageWhitelist(setOf(bank)) }
        val health = ListenerHealth(ApplicationProvider.getApplicationContext())
        val (handler, whitelist) = failingHandler(settings, health)
        runBlocking { whitelist.allows(bank) }
        val pushed = content()
        ingest.failOnSourceKeys = setOf(SourceKeys.push(pushed.key, pushed.postTimeMillis, pushed.text))
        ingest.failRecordFailed = true

        handler.onPosted(bank) { pushed }
        awaitIngestJobs()

        assertThat(rawCount()).isEqualTo(0)
        assertThat(health.ingestFailures.value.lost).isEqualTo(1)
    }

    @Test fun `own package is allowed only when given as the debug package`() {
        val own = "com.foldfinance.app.debug"
        val settings = IngestSettings(InMemoryPreferencesStore())
        val release = PushWhitelist(settings, whitelistScope, debugOwnPackage = null)
        val debug = PushWhitelist(settings, whitelistScope, debugOwnPackage = own)

        assertThat(runBlocking { release.allows(own) }).isFalse()
        assertThat(runBlocking { debug.allows(own) }).isTrue()
    }

    @Test fun `content reader skips group summaries and takes the big text`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val summary = Notification.Builder(context, "c").setGroup("g").setGroupSummary(true)
            .setContentTitle("Банк").setContentText(Texts.TBANK_PURCHASE).build()
        assertThat(PushContent.from(sbn(summary))).isNull()

        val big = Notification.Builder(context, "c")
            .setContentTitle("Банк")
            .setContentText("Покупка 1 500 RUB…")
            .setStyle(Notification.BigTextStyle().bigText(Texts.TBANK_PURCHASE))
            .build()
        val read = PushContent.from(sbn(big))!!
        assertThat(read.title).isEqualTo("Банк")
        assertThat(read.text).isEqualTo(Texts.TBANK_PURCHASE)
        assertThat(read.toString()).doesNotContain("PYATEROCHKA")
    }

    @Suppress("DEPRECATION")
    private fun sbn(notification: Notification) = StatusBarNotification(
        bank, bank, 1, null, 10001, 0, 0, notification, Process.myUserHandle(), T0.toEpochMilli(),
    )

    /** DataStore that emits nothing until [open] — the state right after process start. */
    private class GatedStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences?>(null)
        override val data: Flow<Preferences> = state.filterNotNull()
        fun open() { state.value = emptyPreferences() }
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value ?: emptyPreferences()).also { state.value = it }
    }
}
