package com.foldfinance.app.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foldfinance.core.data.backup.BackupSnapshot
import com.foldfinance.core.data.backup.DatabaseBackup
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.db.entity.CardEntity
import com.foldfinance.core.data.db.entity.RawMessageEntity
import com.foldfinance.core.data.repository.CardRepositoryImpl
import com.foldfinance.core.data.repository.ManualTransactionInput
import com.foldfinance.core.data.repository.TransactionRepositoryImpl
import com.foldfinance.core.model.ParseStatus
import com.foldfinance.parser.Direction
import com.foldfinance.parser.MessageChannel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import com.foldfinance.app.ingest.InMemoryPreferencesStore
import com.foldfinance.core.data.settings.IngestSettings
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.GZIPOutputStream

/**
 * «Экспорт → чистая установка → импорт» на in-memory Room (SQLCipher в Robolectric недоступен,
 * шифрование БД здесь ни при чём), а также ошибки: неверный пароль, повреждённый и чужой файл.
 */
@RunWith(AndroidJUnit4::class)
class BackupManagerTest {

    @get:Rule val tmp = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val clock = Clock.fixed(Instant.parse("2026-11-05T11:05:00Z"), ZoneOffset.UTC)
    private lateinit var phone: FoldFinanceDatabase
    private lateinit var fresh: FoldFinanceDatabase
    private var replacedCalls = 0

    @Before fun setUp() {
        phone = FoldFinanceDatabase.build(context, openHelperFactory = null, name = null)
        fresh = FoldFinanceDatabase.build(context, openHelperFactory = null, name = null)
    }

    @After fun tearDown() {
        phone.close()
        fresh.close()
    }

    private fun manager(db: FoldFinanceDatabase) =
        BackupManager(context, DatabaseBackup(db, clock), BackupCodec(), onDataReplaced = { replacedCalls++ })

    private fun file(name: String = "backup.ffbk"): Uri = Uri.fromFile(File(tmp.root, name))

    private fun password(text: String = "correct horse battery") = text.toCharArray()

    @Test
    fun exportToCleanInstallAndImport_restoresEverything() = runTest {
        populate(phone)
        val before = DatabaseBackup(phone, clock).snapshot()
        val target = file()

        val pass = password()
        assertThat(manager(phone).export(target, pass)).isEqualTo(BackupResult.Success(transactions = 2))
        assertThat(pass.all { it == '\u0000' }).isTrue()

        val bytes = File(tmp.root, "backup.ffbk").readBytes()
        assertThat(bytes.copyOfRange(0, 4)).isEqualTo("FFBK".toByteArray(Charsets.US_ASCII))
        assertThat(bytes[4]).isEqualTo(BackupCipher.VERSION)
        // Ни текста SMS, ни мерчанта в открытом виде.
        assertThat(String(bytes, Charsets.ISO_8859_1)).doesNotContain("PYATEROCHKA")

        assertThat(manager(fresh).import(target, password())).isEqualTo(BackupResult.Success(transactions = 2))
        assertThat(replacedCalls).isEqualTo(1)
        assertThat(DatabaseBackup(fresh, clock).snapshot()).isEqualTo(before)

        val restored = TransactionRepositoryImpl(fresh, clock).observe().first()
        assertThat(restored.map { it.amountMinor }).containsExactly(150_000L, 99L)
        assertThat(CardRepositoryImpl(fresh, clock).observeAll().first().single().isOwn).isTrue()
    }

    @Test
    fun wrongPassword_isReported_andDataUntouched() = runTest {
        populate(phone)
        manager(phone).export(file(), password())
        val untouched = DatabaseBackup(fresh, clock).snapshot()

        val pass = password("wrong password")
        assertThat(manager(fresh).import(file(), pass)).isEqualTo(BackupResult.Failure(BackupError.WRONG_PASSWORD))
        assertThat(pass.all { it == '\u0000' }).isTrue()
        assertThat(DatabaseBackup(fresh, clock).snapshot()).isEqualTo(untouched)
        assertThat(replacedCalls).isEqualTo(0)
    }

    @Test
    fun damagedFile_isRejected() = runTest {
        populate(phone)
        manager(phone).export(file(), password())
        val original = File(tmp.root, "backup.ffbk").readBytes()

        // Испорчен шифротекст → тег GCM не сходится.
        File(tmp.root, "flipped.ffbk").writeBytes(original.copyOf().also { it[it.size - 20] = (it[it.size - 20] + 1).toByte() })
        assertThat(manager(fresh).import(file("flipped.ffbk"), password())).isEqualTo(BackupResult.Failure(BackupError.WRONG_PASSWORD))

        // Подменена соль в заголовке (AAD) → тоже не расшифровывается.
        File(tmp.root, "salt.ffbk").writeBytes(original.copyOf().also { it[6] = (it[6] + 1).toByte() })
        assertThat(manager(fresh).import(file("salt.ffbk"), password())).isEqualTo(BackupResult.Failure(BackupError.WRONG_PASSWORD))

        // Обрезан до заголовка.
        File(tmp.root, "cut.ffbk").writeBytes(original.copyOf(BackupCipher.HEADER_SIZE + 3))
        assertThat(manager(fresh).import(file("cut.ffbk"), password())).isEqualTo(BackupResult.Failure(BackupError.CORRUPTED))

        // Неизвестная версия формата.
        File(tmp.root, "v9.ffbk").writeBytes(original.copyOf().also { it[4] = 9 })
        assertThat(manager(fresh).import(file("v9.ffbk"), password())).isEqualTo(BackupResult.Failure(BackupError.NEWER_VERSION))

        assertThat(TransactionRepositoryImpl(fresh, clock).observe().first()).isEmpty()
    }

    @Test
    fun foreignFiles_areRejected() = runTest {
        File(tmp.root, "photo.jpg").writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3))
        assertThat(manager(fresh).import(file("photo.jpg"), password())).isEqualTo(BackupResult.Failure(BackupError.NOT_A_BACKUP))

        File(tmp.root, "empty.ffbk").writeBytes(ByteArray(0))
        assertThat(manager(fresh).import(file("empty.ffbk"), password())).isEqualTo(BackupResult.Failure(BackupError.NOT_A_BACKUP))

        // Наш контейнер с верным паролем, но внутри не снимок FoldFinance.
        val notSnapshot = gzip(Json.encodeToString(buildJsonObject { put("hello", JsonPrimitive("world")) }).toByteArray())
        File(tmp.root, "alien.ffbk").writeBytes(BackupCipher().encrypt(notSnapshot, password()))
        assertThat(manager(fresh).import(file("alien.ffbk"), password())).isEqualTo(BackupResult.Failure(BackupError.INVALID_CONTENT))

        // Снимок, но с недопустимыми данными (сумма 0) — проверка до замены.
        populate(phone)
        val snapshot = DatabaseBackup(phone, clock).snapshot()
        val bad: BackupSnapshot = snapshot.copy(transactions = snapshot.transactions.map { it.copy(amountMinor = 0) })
        File(tmp.root, "bad.ffbk").writeBytes(BackupCodec().encode(bad, password()))
        assertThat(manager(fresh).import(file("bad.ffbk"), password())).isEqualTo(BackupResult.Failure(BackupError.INVALID_CONTENT))

        assertThat(TransactionRepositoryImpl(fresh, clock).observe().first()).isEmpty()
        assertThat(replacedCalls).isEqualTo(0)
    }

    @Test
    fun export_isReadBackAndVerified_badReadBackIsReported() = runTest {
        populate(phone)
        val uri = Uri.parse("content://test.backup/doc")
        val resolver = shadowOf(context.contentResolver)

        // Провайдер «записал», но читается не то (обрезанный/чужой файл) — экспорт не считается успешным.
        resolver.registerOutputStream(uri, ByteArrayOutputStream())
        resolver.registerInputStream(uri, ByteArrayInputStream(ByteArray(16)))
        val pass = password()
        assertThat(manager(phone).export(uri, pass)).isEqualTo(BackupResult.Failure(BackupError.IO))
        assertThat(pass.all { it == '\u0000' }).isTrue()

        // Файл вообще не перечитывается (провайдер бросает RuntimeException) — тоже ошибка, а не исключение.
        val unreadable = Uri.parse("content://test.backup/unreadable")
        resolver.registerOutputStream(unreadable, ByteArrayOutputStream())
        assertThat(manager(phone).export(unreadable, password())).isEqualTo(BackupResult.Failure(BackupError.IO))
    }

    @Test
    fun import_constraintViolation_isInvalidContent_otherDbErrorsAreIo() = runTest {
        populate(phone)
        manager(phone).export(file(), password())

        // Ограничение БД при записи снимка → INVALID_CONTENT, данные прежние.
        fresh.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject BEFORE INSERT ON cards BEGIN SELECT RAISE(ABORT, 'rejected'); END",
        )
        assertThat(manager(fresh).import(file(), password())).isEqualTo(BackupResult.Failure(BackupError.INVALID_CONTENT))
        assertThat(TransactionRepositoryImpl(fresh, clock).observe().first()).isEmpty()
        assertThat(replacedCalls).isEqualTo(0)

        // Любая другая ошибка (здесь — закрытая БД) → IO, а не исключение и не вечное «Выполняется».
        fresh.close()
        assertThat(manager(fresh).import(file(), password())).isEqualTo(BackupResult.Failure(BackupError.IO))
    }

    @Test
    fun afterRestore_resetsSmsCursor_andStartsQuietFullScanOnlyWithAutoTracking() = runTest {
        val settings = IngestSettings(InMemoryPreferencesStore())
        settings.advanceLastProcessedSmsId(500)
        var scans = 0
        var widgets = 0
        val after = AfterRestore(settings, startQuietFullScan = { scans++ }, refreshWidget = { widgets++ })

        after()
        assertThat(settings.lastProcessedSmsId()).isEqualTo(0)
        assertThat(scans).isEqualTo(1)
        assertThat(widgets).isEqualTo(1)

        settings.advanceLastProcessedSmsId(500)
        settings.setAutoTrackingEnabled(false)
        after()
        assertThat(settings.lastProcessedSmsId()).isEqualTo(0)
        assertThat(scans).isEqualTo(1)
    }

    @Test
    fun cipher_usesFreshSaltAndIvEveryTime() {
        val cipher = BackupCipher()
        val a = cipher.encrypt(byteArrayOf(1, 2, 3), password())
        val b = cipher.encrypt(byteArrayOf(1, 2, 3), password())
        assertThat(a.copyOfRange(5, BackupCipher.HEADER_SIZE)).isNotEqualTo(b.copyOfRange(5, BackupCipher.HEADER_SIZE))
        assertThat(cipher.decrypt(a, password())).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(a.size).isEqualTo(BackupCipher.HEADER_SIZE + 3 + BackupCipher.TAG_BITS / 8)
    }

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    private suspend fun populate(db: FoldFinanceDatabase) {
        val transactions = TransactionRepositoryImpl(db, clock)
        val groceries = db.categoryDao().getByKey("groceries")!!.id
        val purchase = transactions.addManual(
            ManualTransactionInput(
                occurredAt = clock.instant(),
                amountMinor = 150_000,
                currency = "RUB",
                direction = Direction.EXPENSE,
                merchant = "PYATEROCHKA",
                cardMask = "*1234",
                categoryId = groceries,
            ),
        )
        transactions.addManual(
            ManualTransactionInput(occurredAt = clock.instant(), amountMinor = 99, currency = "RUB", direction = Direction.EXPENSE),
        )
        db.cardDao().upsert(CardEntity(mask = "*1234", label = "Зарплатная", isOwn = true))
        db.rawMessageDao().insertIgnore(
            RawMessageEntity(
                channel = MessageChannel.SMS,
                sender = "900",
                sourceKey = "42",
                receivedAt = clock.instant(),
                text = "Покупка 1500р Карта*1234 PYATEROCHKA",
                title = null,
                textHash = "hash",
                parseStatus = ParseStatus.PARSED,
                parserVersion = 2,
                parserId = "sber",
                transactionId = purchase,
            ),
        )
    }
}
