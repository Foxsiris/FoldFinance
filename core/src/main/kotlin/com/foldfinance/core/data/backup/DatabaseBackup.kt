package com.foldfinance.core.data.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.foldfinance.core.data.db.CategorySeeder
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.di.DataClock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Снимок и восстановление всей БД для зашифрованного экспорта/импорта.
 *
 * Работает прямым SQL по явному списку колонок схемы v[FoldFinanceDatabase.VERSION]: формат
 * снимка ([BackupSnapshot]) — контракт файла и не зависит от DAO. Новая колонка или таблица
 * в схеме требует правки здесь; `DatabaseBackupTest` проверяет, что снимок покрывает все
 * таблицы и колонки БД.
 *
 * Ничего не логирует: в таблицах тексты сообщений банка и суммы.
 */
@Singleton
class DatabaseBackup @Inject constructor(
    private val db: FoldFinanceDatabase,
    @DataClock private val clock: Clock,
) {

    /** Согласованный снимок всех таблиц (чтение в одной транзакции). */
    suspend fun snapshot(): BackupSnapshot = db.withTransaction {
        val sql = db.openHelper.writableDatabase
        BackupSnapshot(
            dbVersion = FoldFinanceDatabase.VERSION,
            exportedAtMillis = clock.millis(),
            categories = sql.rows("SELECT * FROM categories ORDER BY id") { readCategory() },
            merchantRules = sql.rows("SELECT * FROM merchant_rules ORDER BY id") { readRule() },
            cards = sql.rows("SELECT * FROM cards ORDER BY mask") { readCard() },
            transactions = sql.rows("SELECT * FROM transactions ORDER BY id") { readTransaction() },
            rawMessages = sql.rows("SELECT * FROM raw_messages ORDER BY id") { readRawMessage() },
            knownBalances = sql.rows("SELECT * FROM known_balances ORDER BY id") { readBalance() },
            subscriptions = sql.rows("SELECT * FROM subscriptions ORDER BY id") { readSubscription() },
        )
    }

    /**
     * Заменить все данные БД содержимым [snapshot] в одной транзакции.
     *
     * Сначала снимок проверяется ([BackupValidator]); при ошибке проверки или записи
     * транзакция откатывается и данные на устройстве остаются прежними. После замены
     * досоздаются недостающие базовые категории.
     *
     * @throws InvalidBackupException снимок не прошёл проверку.
     */
    suspend fun restore(snapshot: BackupSnapshot) {
        BackupValidator.validate(snapshot)
        db.withTransaction {
            val sql = db.openHelper.writableDatabase
            // Ссылки (в том числе transactions → transactions) проверяются при COMMIT.
            sql.execSQL("PRAGMA defer_foreign_keys = ON")
            TABLES_DELETE_ORDER.forEach { sql.execSQL("DELETE FROM `$it`") }

            snapshot.categories.forEach { sql.put("categories", it.values()) }
            snapshot.cards.forEach { sql.put("cards", it.values()) }
            snapshot.merchantRules.forEach { sql.put("merchant_rules", it.values()) }
            snapshot.transactions.forEach { sql.put("transactions", it.values()) }
            snapshot.rawMessages.forEach { sql.put("raw_messages", it.values()) }
            snapshot.knownBalances.forEach { sql.put("known_balances", it.values()) }
            snapshot.subscriptions.forEach { sql.put("subscriptions", it.values()) }

            CategorySeeder.seed(db.categoryDao())
        }
    }

    companion object {
        /** Таблицы данных в порядке удаления (сначала зависимые). */
        val TABLES_DELETE_ORDER: List<String> = listOf(
            "known_balances",
            "raw_messages",
            "subscriptions",
            "merchant_rules",
            "transactions",
            "cards",
            "categories",
        )
    }
}

// ---------------------------------------------------------------- Чтение

private inline fun <T> SupportSQLiteDatabase.rows(query: String, read: Cursor.() -> T): List<T> =
    query(query).use { cursor ->
        val result = ArrayList<T>(cursor.count)
        while (cursor.moveToNext()) result += cursor.read()
        result
    }

private fun Cursor.col(name: String): Int = getColumnIndexOrThrow(name)
private fun Cursor.long(name: String): Long = getLong(col(name))
private fun Cursor.int(name: String): Int = getInt(col(name))
private fun Cursor.bool(name: String): Boolean = getInt(col(name)) != 0
private fun Cursor.str(name: String): String = getString(col(name))
private fun Cursor.strOrNull(name: String): String? = col(name).let { if (isNull(it)) null else getString(it) }
private fun Cursor.longOrNull(name: String): Long? = col(name).let { if (isNull(it)) null else getLong(it) }
private fun Cursor.intOrNull(name: String): Int? = col(name).let { if (isNull(it)) null else getInt(it) }

private fun Cursor.readCategory() = CategoryRow(
    id = long("id"),
    key = str("key"),
    name = str("name"),
    iconKey = str("icon_key"),
    colorArgb = intOrNull("color_argb"),
    isSystem = bool("is_system"),
    sortOrder = int("sort_order"),
)

private fun Cursor.readRule() = MerchantRuleRow(
    id = long("id"),
    merchantPattern = str("merchant_pattern"),
    categoryId = long("category_id"),
    createdAt = long("created_at"),
)

private fun Cursor.readCard() = CardRow(
    mask = str("mask"),
    label = strOrNull("label"),
    isOwn = bool("is_own"),
)

private fun Cursor.readTransaction() = TransactionRow(
    id = long("id"),
    occurredAt = long("occurred_at"),
    amountMinor = long("amount_minor"),
    currency = str("currency"),
    direction = str("direction"),
    parsedDirection = str("parsed_direction"),
    kind = str("kind"),
    merchant = strOrNull("merchant"),
    merchantNorm = strOrNull("merchant_norm"),
    cardMask = strOrNull("card_mask"),
    counterpartyCardMask = strOrNull("counterparty_card_mask"),
    categoryId = longOrNull("category_id"),
    confidence = getFloat(col("confidence")),
    needsReview = bool("needs_review"),
    source = str("source"),
    dedupKey = str("dedup_key"),
    note = strOrNull("note"),
    searchText = str("search_text"),
    linkedTransactionId = longOrNull("linked_transaction_id"),
    userEdited = bool("user_edited"),
    rawMessageId = longOrNull("raw_message_id"),
    opIndex = int("op_index"),
    createdAt = long("created_at"),
    updatedAt = long("updated_at"),
)

private fun Cursor.readRawMessage() = RawMessageRow(
    id = long("id"),
    channel = str("channel"),
    sender = str("sender"),
    sourceKey = str("source_key"),
    receivedAt = long("received_at"),
    text = str("text"),
    title = strOrNull("title"),
    textHash = str("text_hash"),
    parseStatus = str("parse_status"),
    parserVersion = int("parser_version"),
    parserId = strOrNull("parser_id"),
    transactionId = longOrNull("transaction_id"),
    userDeleted = bool("user_deleted"),
)

private fun Cursor.readBalance() = KnownBalanceRow(
    id = long("id"),
    cardMask = str("card_mask"),
    amountMinor = long("amount_minor"),
    currency = str("currency"),
    asOf = long("as_of"),
    rawMessageId = longOrNull("raw_message_id"),
)

private fun Cursor.readSubscription() = SubscriptionRow(
    id = long("id"),
    merchant = str("merchant"),
    amountMinor = long("amount_minor"),
    currency = str("currency"),
    period = str("period"),
    lastChargeAt = long("last_charge_at"),
    nextChargeAt = long("next_charge_at"),
    status = str("status"),
    categoryId = longOrNull("category_id"),
)

// ---------------------------------------------------------------- Запись

private fun SupportSQLiteDatabase.put(table: String, values: ContentValues) {
    // ABORT: дубликат ключа — ошибка, а не тихий пропуск; транзакция откатится.
    insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
}

private fun Boolean.int(): Int = if (this) 1 else 0

private fun CategoryRow.values() = ContentValues().apply {
    put("id", id)
    put("key", key)
    put("name", name)
    put("icon_key", iconKey)
    put("color_argb", colorArgb)
    put("is_system", isSystem.int())
    put("sort_order", sortOrder)
}

private fun MerchantRuleRow.values() = ContentValues().apply {
    put("id", id)
    put("merchant_pattern", merchantPattern)
    put("category_id", categoryId)
    put("created_at", createdAt)
}

private fun CardRow.values() = ContentValues().apply {
    put("mask", mask)
    put("label", label)
    put("is_own", isOwn.int())
}

private fun TransactionRow.values() = ContentValues().apply {
    put("id", id)
    put("occurred_at", occurredAt)
    put("amount_minor", amountMinor)
    put("currency", currency)
    put("direction", direction)
    put("parsed_direction", parsedDirection)
    put("kind", kind)
    put("merchant", merchant)
    put("merchant_norm", merchantNorm)
    put("card_mask", cardMask)
    put("counterparty_card_mask", counterpartyCardMask)
    put("category_id", categoryId)
    put("confidence", confidence)
    put("needs_review", needsReview.int())
    put("source", source)
    put("dedup_key", dedupKey)
    put("note", note)
    put("search_text", searchText)
    put("linked_transaction_id", linkedTransactionId)
    put("user_edited", userEdited.int())
    put("raw_message_id", rawMessageId)
    put("op_index", opIndex)
    put("created_at", createdAt)
    put("updated_at", updatedAt)
}

private fun RawMessageRow.values() = ContentValues().apply {
    put("id", id)
    put("channel", channel)
    put("sender", sender)
    put("source_key", sourceKey)
    put("received_at", receivedAt)
    put("text", text)
    put("title", title)
    put("text_hash", textHash)
    put("parse_status", parseStatus)
    put("parser_version", parserVersion)
    put("parser_id", parserId)
    put("transaction_id", transactionId)
    put("user_deleted", userDeleted.int())
}

private fun KnownBalanceRow.values() = ContentValues().apply {
    put("id", id)
    put("card_mask", cardMask)
    put("amount_minor", amountMinor)
    put("currency", currency)
    put("as_of", asOf)
    put("raw_message_id", rawMessageId)
}

private fun SubscriptionRow.values() = ContentValues().apply {
    put("id", id)
    put("merchant", merchant)
    put("amount_minor", amountMinor)
    put("currency", currency)
    put("period", period)
    put("last_charge_at", lastChargeAt)
    put("next_charge_at", nextChargeAt)
    put("status", status)
    put("category_id", categoryId)
}
