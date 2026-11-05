package com.foldfinance.core.data.backup

import kotlinx.serialization.Serializable

/**
 * Полный снимок БД для зашифрованного экспорта (этап 9).
 *
 * Формат снимка не совпадает с Room-сущностями намеренно: это контракт файла, который должен
 * читаться и после изменения схемы БД. Колонки перенесены как есть (время — epoch millis UTC,
 * перечисления — по имени, как в `Converters`), id сохраняются, чтобы ссылки между таблицами
 * остались верными.
 *
 * @property format идентификатор формата, всегда [FORMAT_ID].
 * @property schemaVersion версия формата снимка ([SCHEMA_VERSION]).
 * @property dbVersion версия схемы БД, из которой сделан снимок.
 * @property exportedAtMillis момент экспорта, epoch millis UTC.
 */
@Serializable
data class BackupSnapshot(
    val format: String = FORMAT_ID,
    val schemaVersion: Int = SCHEMA_VERSION,
    val dbVersion: Int,
    val exportedAtMillis: Long,
    val categories: List<CategoryRow>,
    val merchantRules: List<MerchantRuleRow>,
    val cards: List<CardRow>,
    val transactions: List<TransactionRow>,
    val rawMessages: List<RawMessageRow>,
    val knownBalances: List<KnownBalanceRow>,
    val subscriptions: List<SubscriptionRow>,
) {
    /** Сколько записей в снимке — для сообщения «Восстановлено N операций». */
    val transactionCount: Int get() = transactions.size

    companion object {
        const val FORMAT_ID: String = "foldfinance-backup"
        const val SCHEMA_VERSION: Int = 1
    }
}

/** Строка таблицы `transactions`. */
@Serializable
data class TransactionRow(
    val id: Long,
    val occurredAt: Long,
    val amountMinor: Long,
    val currency: String,
    val direction: String,
    val parsedDirection: String,
    val kind: String,
    val merchant: String? = null,
    val merchantNorm: String? = null,
    val cardMask: String? = null,
    val counterpartyCardMask: String? = null,
    val categoryId: Long? = null,
    val confidence: Float,
    val needsReview: Boolean,
    val source: String,
    val dedupKey: String,
    val note: String? = null,
    val searchText: String,
    val linkedTransactionId: Long? = null,
    val userEdited: Boolean,
    val rawMessageId: Long? = null,
    val opIndex: Int,
    val createdAt: Long,
    val updatedAt: Long,
    /** С БД v2; в снимках v1 отсутствует — `false`. */
    val reviewConfirmed: Boolean = false,
)

/** Строка таблицы `raw_messages` (текст сообщения банка — только внутри зашифрованного файла). */
@Serializable
data class RawMessageRow(
    val id: Long,
    val channel: String,
    val sender: String,
    val sourceKey: String,
    val receivedAt: Long,
    val text: String,
    val title: String? = null,
    val textHash: String,
    val parseStatus: String,
    val parserVersion: Int,
    val parserId: String? = null,
    val transactionId: Long? = null,
    val userDeleted: Boolean,
)

/** Строка таблицы `categories`. */
@Serializable
data class CategoryRow(
    val id: Long,
    val key: String,
    val name: String,
    val iconKey: String,
    val colorArgb: Int? = null,
    val isSystem: Boolean,
    val sortOrder: Int,
)

/** Строка таблицы `merchant_rules`. */
@Serializable
data class MerchantRuleRow(
    val id: Long,
    val merchantPattern: String,
    val categoryId: Long,
    val createdAt: Long,
)

/** Строка таблицы `cards`. */
@Serializable
data class CardRow(
    val mask: String,
    val label: String? = null,
    val isOwn: Boolean,
)

/** Строка таблицы `known_balances`. Остаток может быть нулевым или отрицательным (кредитка). */
@Serializable
data class KnownBalanceRow(
    val id: Long,
    val cardMask: String,
    val amountMinor: Long,
    val currency: String,
    val asOf: Long,
    val rawMessageId: Long? = null,
)

/** Строка таблицы `subscriptions`. */
@Serializable
data class SubscriptionRow(
    val id: Long,
    val merchant: String,
    val amountMinor: Long,
    val currency: String,
    val period: String,
    val lastChargeAt: Long,
    val nextChargeAt: Long,
    val status: String,
    val categoryId: Long? = null,
)
