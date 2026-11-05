package com.foldfinance.core.data.backup

import com.foldfinance.core.data.T0
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.db.entity.CardEntity
import com.foldfinance.core.data.db.entity.KnownBalanceEntity
import com.foldfinance.core.data.db.entity.MerchantAliasEntity
import com.foldfinance.core.data.db.entity.MerchantRuleEntity
import com.foldfinance.core.data.db.entity.RawMessageEntity
import com.foldfinance.core.data.db.entity.SubscriptionEntity
import com.foldfinance.core.data.db.entity.TransactionEntity
import com.foldfinance.core.data.db.entity.TransactionSplitEntity
import com.foldfinance.core.data.fixedClock
import com.foldfinance.core.data.inMemoryDb
import com.foldfinance.core.data.repository.CategoryRepositoryImpl
import com.foldfinance.core.model.ParseStatus
import com.foldfinance.core.model.Source
import com.foldfinance.core.model.SubscriptionPeriod
import com.foldfinance.core.model.SubscriptionStatus
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.foldfinance.parser.MessageChannel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class DatabaseBackupTest {

    private lateinit var source: FoldFinanceDatabase
    private lateinit var target: FoldFinanceDatabase

    @Before fun setUp() {
        source = inMemoryDb()
        target = inMemoryDb()
    }

    @After fun tearDown() {
        source.close()
        target.close()
    }

    @Test
    fun snapshotRestore_roundTrip_reproducesEveryTable() = runTest {
        populate(source)
        val snapshot = DatabaseBackup(source, fixedClock()).snapshot()
        assertThat(snapshot.transactions).hasSize(3)
        assertThat(snapshot.rawMessages).hasSize(1)

        // Цель уже содержит другие данные — они должны быть полностью заменены.
        target.transactionDao().insertIgnore(tx(dedupKey = "other", amountMinor = 1))
        DatabaseBackup(target, fixedClock()).restore(snapshot)

        val restored = DatabaseBackup(target, fixedClock()).snapshot()
        assertThat(restored).isEqualTo(snapshot)
    }

    @Test
    fun restore_rejectsInvalidAmount_andKeepsExistingData() = runTest {
        target.transactionDao().insertIgnore(tx(dedupKey = "keep", amountMinor = 500))
        val before = DatabaseBackup(target, fixedClock()).snapshot()

        populate(source)
        val good = DatabaseBackup(source, fixedClock()).snapshot()
        val bad = good.copy(transactions = good.transactions.mapIndexed { i, t -> if (i == 1) t.copy(amountMinor = 0) else t })

        val e = runCatching { DatabaseBackup(target, fixedClock()).restore(bad) }.exceptionOrNull()
        assertThat(e).isInstanceOf(InvalidBackupException::class.java)
        e as InvalidBackupException
        assertThat(e.reason).isEqualTo(InvalidBackupException.Reason.INVALID_CONTENT)
        assertThat(e.problem).contains("transactions[1].amountMinor")
        assertThat(DatabaseBackup(target, fixedClock()).snapshot()).isEqualTo(before)
    }

    @Test
    fun validator_rejectsBrokenReferencesCurrencyAndEnums() = runTest {
        populate(source)
        val good = DatabaseBackup(source, fixedClock()).snapshot()
        BackupValidator.validate(good)

        fun assertInvalid(snapshot: BackupSnapshot, field: String) {
            val e = assertThrows(InvalidBackupException::class.java) { BackupValidator.validate(snapshot) }
            assertThat(e.problem).contains(field)
        }
        assertInvalid(good.copy(transactions = good.transactions.map { it.copy(currency = "rub") }), "currency")
        assertInvalid(good.copy(transactions = good.transactions.map { it.copy(direction = "GIFT") }), "direction")
        assertInvalid(good.copy(categories = good.categories.drop(1)), "categoryId")
        assertInvalid(good.copy(rawMessages = good.rawMessages.map { it.copy(transactionId = 999) }), "transactionId")
        assertInvalid(good.copy(subscriptions = good.subscriptions.map { it.copy(amountMinor = -5) }), "amountMinor")
        assertInvalid(good.copy(transactions = good.transactions + good.transactions.first()), "duplicated")
        assertInvalid(good.copy(transactionSplits = good.transactionSplits.map { it.copy(parentId = 999) }), "transactionSplits[0].parentId")
        assertInvalid(good.copy(transactionSplits = good.transactionSplits.map { it.copy(amountMinor = 0) }), "transactionSplits[0].amountMinor")
        // Части больше суммы операции (150 000): 100 000 + 60 000.
        val overflow = good.transactionSplits + good.transactionSplits.first().copy(id = 99, amountMinor = 60_000)
        assertInvalid(good.copy(transactionSplits = overflow), "transactionSplits[1].amountMinor")
    }

    @Test
    fun validator_rejectsForeignFormatAndNewerVersion() = runTest {
        val empty = DatabaseBackup(source, fixedClock()).snapshot()
        assertThat(assertThrows(InvalidBackupException::class.java) { BackupValidator.validate(empty.copy(format = "x")) }.reason)
            .isEqualTo(InvalidBackupException.Reason.FOREIGN_FORMAT)
        assertThat(
            assertThrows(InvalidBackupException::class.java) {
                BackupValidator.validate(empty.copy(dbVersion = FoldFinanceDatabase.VERSION + 1))
            }.reason,
        ).isEqualTo(InvalidBackupException.Reason.NEWER_VERSION)
    }

    @Test
    fun restore_ofEmptySnapshot_reseedsDefaultCategories() = runTest {
        val empty = DatabaseBackup(source, fixedClock()).snapshot().copy(categories = emptyList())
        DatabaseBackup(target, fixedClock()).restore(empty)
        assertThat(CategoryRepositoryImpl(target).getAll()).isNotEmpty()
    }

    /** Снимок покрывает все таблицы данных и все их колонки: новая колонка без правки бэкапа уронит тест. */
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun restore_ofFormat1Snapshot_withoutAiFields_works() = runTest {
        populate(source)
        val current = DatabaseBackup(source, fixedClock()).snapshot()
        assertThat(current.merchantAliases).hasSize(1)
        // Файл формата 1 (до БД v3): нет алиасов, частей и ИИ-полей.
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val legacyText = json.encodeToString(BackupSnapshot.serializer(), current)
            .replace(Regex(""","merchantAliases":\[[^\]]*\]"""), "")
            .replace(Regex(""","transactionSplits":\[[^\]]*\]"""), "")
            .replace(Regex(""","(aiParsed|aiCategory|aiAttemptedAt)":(true|false|null|\d+)"""), "")
            .replace(""""schemaVersion":3""", """"schemaVersion":1""")
            .replace(""""dbVersion":4""", """"dbVersion":2""")
        assertThat(legacyText).doesNotContain("merchantAliases")
        assertThat(legacyText).doesNotContain("transactionSplits")
        val legacy = kotlinx.serialization.json.Json.decodeFromString(BackupSnapshot.serializer(), legacyText)
        assertThat(legacy.schemaVersion).isEqualTo(1)

        DatabaseBackup(target, fixedClock()).restore(legacy)
        val restored = DatabaseBackup(target, fixedClock()).snapshot()
        assertThat(restored.transactions.map { it.amountMinor }).isEqualTo(current.transactions.map { it.amountMinor })
        assertThat(restored.merchantAliases).isEmpty()
        assertThat(restored.transactionSplits).isEmpty()
    }

    @Test
    fun restore_ofFormat2Snapshot_withoutSplits_works() = runTest {
        populate(source)
        val current = DatabaseBackup(source, fixedClock()).snapshot()
        assertThat(current.transactionSplits).hasSize(1)
        // Файл формата 2 (БД v3): алиасы и ИИ-поля есть, разбиения по чеку ещё нет.
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val legacyText = json.encodeToString(BackupSnapshot.serializer(), current)
            .replace(Regex(""","transactionSplits":\[[^\]]*\]"""), "")
            .replace(""""schemaVersion":3""", """"schemaVersion":2""")
            .replace(""""dbVersion":4""", """"dbVersion":3""")
        val legacy = kotlinx.serialization.json.Json.decodeFromString(BackupSnapshot.serializer(), legacyText)
        assertThat(legacy.schemaVersion).isEqualTo(2)
        assertThat(legacy.transactionSplits).isEmpty()

        DatabaseBackup(target, fixedClock()).restore(legacy)
        val restored = DatabaseBackup(target, fixedClock()).snapshot()
        assertThat(restored.transactions).isEqualTo(current.transactions)
        assertThat(restored.merchantAliases).isEqualTo(current.merchantAliases)
        assertThat(restored.transactionSplits).isEmpty()
    }

    @Test
    fun snapshot_coversEverySchemaTableAndColumn() {
        val sql = source.openHelper.writableDatabase
        val tables = sql.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }.filterNot { it.startsWith("sqlite_") || it.startsWith("room_") || it == "android_metadata" }
        assertThat(tables).containsExactlyElementsIn(DatabaseBackup.TABLES_DELETE_ORDER)

        val rowSerializers: Map<String, KSerializer<*>> = mapOf(
            "transactions" to TransactionRow.serializer(),
            "raw_messages" to RawMessageRow.serializer(),
            "categories" to CategoryRow.serializer(),
            "merchant_rules" to MerchantRuleRow.serializer(),
            "cards" to CardRow.serializer(),
            "known_balances" to KnownBalanceRow.serializer(),
            "subscriptions" to SubscriptionRow.serializer(),
            "merchant_alias" to MerchantAliasRow.serializer(),
            "transaction_split" to TransactionSplitRow.serializer(),
        )
        for (table in tables) {
            val columns = sql.query("PRAGMA table_info(`$table`)").use { c -> c.count }
            assertThat(rowSerializers.getValue(table).descriptor.elementsCount).isEqualTo(columns)
        }
    }

    private suspend fun populate(db: FoldFinanceDatabase) {
        val categories = CategoryRepositoryImpl(db)
        val userCategory = categories.add("Путешествия", "flight", 0xFF336699.toInt())
        val groceries = categories.getAll().first { it.key == "groceries" }.id
        db.merchantRuleDao().insert(MerchantRuleEntity(merchantPattern = "пятерочка", categoryId = groceries, createdAt = T0))
        db.cardDao().upsert(CardEntity(mask = "*1234", label = "Зарплатная", isOwn = true))
        db.cardDao().upsert(CardEntity(mask = "*9876", label = null, isOwn = false))
        db.merchantAliasDao().upsert(
            MerchantAliasEntity(
                merchantKey = "piaterochka",
                aiName = "Пятёрочка",
                userName = null,
                aiCategoryId = groceries,
                categoryConfirmed = true,
                aiCheckedAt = T0,
                updatedAt = T0,
            ),
        )

        val purchase = db.transactionDao().insertIgnore(tx(dedupKey = "sms:1:0", amountMinor = 150_000, categoryId = groceries))
        db.transactionSplitDao().insertAll(
            listOf(TransactionSplitEntity(parentId = purchase, categoryId = userCategory, amountMinor = 100_000, label = "Кофе, сироп")),
        )
        db.transactionDao().insertIgnore(
            tx(dedupKey = "sms:2:0", amountMinor = 20_000, direction = Direction.REFUND, kind = Kind.REFUND, linked = purchase),
        )
        db.transactionDao().insertIgnore(
            tx(dedupKey = "manual:1", amountMinor = 99, categoryId = userCategory, source = Source.MANUAL, note = "кофе"),
        )
        val raw = db.rawMessageDao().insertIgnore(
            RawMessageEntity(
                channel = MessageChannel.SMS,
                sender = "900",
                sourceKey = "1",
                receivedAt = T0,
                text = "Покупка 1500р Карта*1234 PYATEROCHKA Баланс: 10 234,56р",
                title = null,
                textHash = "h1",
                parseStatus = ParseStatus.PARSED,
                parserVersion = 2,
                parserId = "sber",
                transactionId = purchase,
            ),
        )
        db.knownBalanceDao().insertReplace(
            KnownBalanceEntity(cardMask = "*1234", amountMinor = -1_023_456, currency = "RUB", asOf = T0, rawMessageId = raw),
        )
        db.subscriptionDao().insert(
            SubscriptionEntity(
                merchant = "Кинопоиск",
                amountMinor = 29_900,
                currency = "RUB",
                period = SubscriptionPeriod.MONTHLY,
                lastChargeAt = T0,
                nextChargeAt = T0 + Duration.ofDays(30),
                status = SubscriptionStatus.CONFIRMED,
                categoryId = userCategory,
            ),
        )
    }

    private fun tx(
        dedupKey: String,
        amountMinor: Long,
        direction: Direction = Direction.EXPENSE,
        kind: Kind = Kind.PURCHASE,
        categoryId: Long? = null,
        linked: Long? = null,
        source: Source = Source.SMS,
        note: String? = null,
    ) = TransactionEntity(
        occurredAt = T0,
        amountMinor = amountMinor,
        currency = "RUB",
        direction = direction,
        parsedDirection = direction,
        kind = kind,
        merchant = "PYATEROCHKA",
        merchantNorm = "pyaterochka",
        cardMask = "*1234",
        counterpartyCardMask = null,
        categoryId = categoryId,
        confidence = 0.95f,
        needsReview = linked != null,
        source = source,
        dedupKey = dedupKey,
        note = note,
        searchText = "pyaterochka",
        linkedTransactionId = linked,
        userEdited = false,
        rawMessageId = null,
        opIndex = 0,
        createdAt = T0,
        updatedAt = T0,
    )
}
