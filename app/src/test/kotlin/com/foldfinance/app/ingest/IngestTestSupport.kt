package com.foldfinance.app.ingest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import com.foldfinance.app.ingest.sms.DatabaseOperationLookup
import com.foldfinance.app.ingest.sms.SmsAdmission
import com.foldfinance.app.ingest.sms.SmsReader
import com.foldfinance.app.ingest.sms.SmsRecord
import com.foldfinance.app.ingest.sms.SmsScanner
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.ingest.IncomingMessage
import com.foldfinance.core.data.ingest.IngestOutcome
import com.foldfinance.core.data.ingest.IngestRepository
import com.foldfinance.core.data.ingest.IngestRepositoryImpl
import com.foldfinance.core.data.settings.IngestSettings
import com.foldfinance.parser.ParserRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * DataStore in memory: the file-based one fails on Windows on repeated atomic renames
 * (see `IngestSettingsTest` in `:core`).
 */
class InMemoryPreferencesStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock { transform(state.value).also { state.value = it } }
}

val T0: Instant = Instant.parse("2026-10-01T09:00:00Z")
val TestZone: ZoneId = ZoneId.of("Europe/Moscow")
val TestClock: Clock = Clock.fixed(T0, ZoneOffset.UTC)

/** Real parser rules (synthetic fixture formats), Moscow time. */
val Registry: ParserRegistry = ParserRegistry.default(TestZone)

fun inMemoryDb(): FoldFinanceDatabase =
    FoldFinanceDatabase.build(ApplicationProvider.getApplicationContext(), openHelperFactory = null, name = null)

/** Synthetic texts, the same as the parser fixtures (`parser/src/test/resources/fixtures`). */
object Texts {
    /** sber/001 */
    const val PURCHASE = "ECMC1234 12:34 Покупка 1 500р PYATEROCHKA Баланс: 10 234,56р"

    /** sber/002 */
    const val REFUND = "VISA1234 12:40 Возврат покупки 990р OZON.RU Баланс: 11 224,56р"

    /** sber/006 — one-time code. */
    const val OTP = "Код: 482913. Никому не сообщайте код! Он нужен для входа в СберБанк Онлайн."

    /** sber/008 — promo. */
    const val PROMO = "СберБанк: кредит наличными до 5 000 000 ₽ по ставке от 9,9%. Оформите онлайн: sberbank.ru/kredit"

    /** generic/005 — a private message. */
    const val CHAT = "Привет! Как дела? 🙂"

    /** tbank/001 — used from an unknown sender to exercise GenericParser. */
    const val TBANK_PURCHASE = "Покупка, карта *1234. 1 500 RUB. PYATEROCHKA. Доступно 10 234,56 RUB"
}

fun sms(id: Long, body: String, address: String = "900", at: Instant = T0.plusSeconds(id * 60)) =
    SmsRecord(id = id, address = address, dateMillis = at.toEpochMilli(), body = body)

/** Inbox in memory; records every page request. */
class FakeSmsReader(messages: List<SmsRecord> = emptyList()) : SmsReader {
    val inbox: MutableList<SmsRecord> = messages.toMutableList()
    val pageRequests: MutableList<Long> = mutableListOf()

    override suspend fun readPage(afterId: Long, limit: Int): List<SmsRecord> {
        pageRequests += afterId
        return inbox.filter { it.id > afterId }.sortedBy { it.id }.take(limit)
    }

    override suspend fun count(afterId: Long): Int = inbox.count { it.id > afterId }
}

/** Delegates to [delegate], records inputs, optionally fails on given SMS source keys. */
class RecordingIngest(
    private val delegate: IngestRepository,
    var failOnSourceKeys: Set<String> = emptySet(),
) : IngestRepository {
    val received: MutableList<IncomingMessage> = mutableListOf()

    override suspend fun ingest(input: IncomingMessage): IngestOutcome {
        if (input.sourceKey in failOnSourceKeys) throw IllegalStateException("disk full")
        received += input
        return delegate.ingest(input)
    }
}

/** Everything a scan test needs, wired like production but in memory. */
class ScanHarness(messages: List<SmsRecord> = emptyList()) {
    val db: FoldFinanceDatabase = inMemoryDb()
    val settings = IngestSettings(InMemoryPreferencesStore())
    val reader = FakeSmsReader(messages)
    val ingest = RecordingIngest(IngestRepositoryImpl(db, Registry, TestClock))
    val scanner = SmsScanner(
        reader = reader,
        ingest = ingest,
        admission = SmsAdmission(Registry),
        settings = settings,
        lookup = DatabaseOperationLookup(db.transactionDao(), db.rawMessageDao()),
    )

    fun countRows(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    fun close() = db.close()
}
