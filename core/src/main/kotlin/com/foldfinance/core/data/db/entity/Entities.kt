package com.foldfinance.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.foldfinance.core.model.ParseStatus
import com.foldfinance.core.model.Source
import com.foldfinance.core.model.SubscriptionPeriod
import com.foldfinance.core.model.SubscriptionStatus
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.foldfinance.parser.MessageChannel
import java.time.Instant

// Room-сущности схемы v2. Любое изменение колонок/индексов = новая версия БД + миграция + тест.

/**
 * Операция.
 *
 * Служебные колонки сверх доменной модели:
 * - [parsedDirection] — направление, как его вернул парсер (до превращения в INTERNAL_TRANSFER),
 *   нужно для пересчёта при изменении флага «моя карта» и для кросс-источникового дедупа;
 * - [counterpartyCardMask] — маска карты получателя/отправителя перевода;
 * - [rawMessageId] + [opIndex] — из какого сообщения и какой по счёту операции создана (для перепарсинга);
 * - [merchantNorm], [searchText] — нормализованные (нижний регистр, ё→е) строки для поиска и сравнения:
 *   встроенный `lower()`/`LIKE` SQLite не работают с кириллицей;
 * - [reviewConfirmed] (v2) — пользователь подтвердил операцию в очереди проверки: перепарсинг не
 *   возвращает её в очередь, пока разобранные значения (сумма, валюта, направление, вид) не изменились.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["linked_transaction_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["dedup_key"], unique = true),
        Index(value = ["occurred_at"]),
        Index(value = ["category_id"]),
        Index(value = ["needs_review"]),
        Index(value = ["linked_transaction_id"]),
        Index(value = ["raw_message_id"]),
        Index(value = ["amount_minor", "currency", "occurred_at"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "occurred_at") val occurredAt: Instant,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    @ColumnInfo(name = "parsed_direction") val parsedDirection: Direction,
    val kind: Kind,
    val merchant: String?,
    @ColumnInfo(name = "merchant_norm") val merchantNorm: String?,
    @ColumnInfo(name = "card_mask") val cardMask: String?,
    @ColumnInfo(name = "counterparty_card_mask") val counterpartyCardMask: String?,
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    val confidence: Float,
    @ColumnInfo(name = "needs_review") val needsReview: Boolean,
    val source: Source,
    @ColumnInfo(name = "dedup_key") val dedupKey: String,
    val note: String?,
    @ColumnInfo(name = "search_text") val searchText: String,
    @ColumnInfo(name = "linked_transaction_id") val linkedTransactionId: Long?,
    @ColumnInfo(name = "user_edited") val userEdited: Boolean,
    @ColumnInfo(name = "raw_message_id") val rawMessageId: Long?,
    @ColumnInfo(name = "op_index") val opIndex: Int,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "review_confirmed", defaultValue = "0") val reviewConfirmed: Boolean = false,
)

/**
 * Исходное сообщение банка.
 *
 * [transactionId] — основная операция сообщения (первая); при кросс-источниковом дедупе —
 * существующая операция из другого канала. [userDeleted] — пользователь удалил операцию из
 * этого сообщения: перепарсинг не должен её воскрешать; у нераспознанного сообщения — пользователь
 * скрыл его из раздела «Не распознано».
 */
@Entity(
    tableName = "raw_messages",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["channel", "source_key"], unique = true),
        Index(value = ["transaction_id"]),
        Index(value = ["parser_version"]),
        Index(value = ["text_hash"]),
    ],
)
data class RawMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channel: MessageChannel,
    val sender: String,
    @ColumnInfo(name = "source_key") val sourceKey: String,
    @ColumnInfo(name = "received_at") val receivedAt: Instant,
    val text: String,
    val title: String?,
    @ColumnInfo(name = "text_hash") val textHash: String,
    @ColumnInfo(name = "parse_status") val parseStatus: ParseStatus,
    @ColumnInfo(name = "parser_version") val parserVersion: Int,
    @ColumnInfo(name = "parser_id") val parserId: String?,
    @ColumnInfo(name = "transaction_id") val transactionId: Long?,
    @ColumnInfo(name = "user_deleted") val userDeleted: Boolean = false,
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["key"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val name: String,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Int?,
    @ColumnInfo(name = "is_system") val isSystem: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

/** [merchantPattern] хранится нормализованным (нижний регистр, ё→е), уникален. */
@Entity(
    tableName = "merchant_rules",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["merchant_pattern"], unique = true),
        Index(value = ["category_id"]),
    ],
)
data class MerchantRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "merchant_pattern") val merchantPattern: String,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val mask: String,
    val label: String?,
    @ColumnInfo(name = "is_own") val isOwn: Boolean,
)

/** История известных остатков; одна запись на сообщение. Последний по карте — max(as_of). */
@Entity(
    tableName = "known_balances",
    foreignKeys = [
        ForeignKey(
            entity = RawMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["raw_message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["raw_message_id"], unique = true),
        Index(value = ["card_mask", "as_of"]),
    ],
)
data class KnownBalanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "card_mask") val cardMask: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val currency: String,
    @ColumnInfo(name = "as_of") val asOf: Instant,
    @ColumnInfo(name = "raw_message_id") val rawMessageId: Long?,
)

@Entity(
    tableName = "subscriptions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["status"]),
        Index(value = ["next_charge_at"]),
    ],
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val currency: String,
    val period: SubscriptionPeriod,
    @ColumnInfo(name = "last_charge_at") val lastChargeAt: Instant,
    @ColumnInfo(name = "next_charge_at") val nextChargeAt: Instant,
    val status: SubscriptionStatus,
    @ColumnInfo(name = "category_id") val categoryId: Long?,
)
