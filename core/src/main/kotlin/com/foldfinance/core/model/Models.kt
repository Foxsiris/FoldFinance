package com.foldfinance.core.model

import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.foldfinance.parser.MessageChannel
import java.time.Instant

// Доменные модели. Room-сущности живут в com.foldfinance.core.data и маппятся сюда.
// Направление и вид операции — общие с :parser (Direction, Kind).

/**
 * Сумма в минорных единицах + ISO 4217. Никаких Double/Float для денег.
 * Знак не используется: направление задаёт [Direction].
 */
data class Money(val amountMinor: Long, val currency: String) {
    init { require(currency.length == 3) { "currency must be ISO 4217" } }
}

/** Откуда появилась операция. */
enum class Source { SMS, PUSH, MANUAL }

/**
 * Денежная операция.
 *
 * @property dedupKey ключ точной дедупликации по идентичности источника.
 * @property needsReview тип или сумма не уверены — операция в очереди проверки.
 * @property userEdited пользователь правил вручную; перепарсинг не перезаписывает поля.
 * @property linkedTransactionId для [Direction.REFUND] — исходная покупка, если найдена.
 */
data class Transaction(
    val id: Long = 0,
    val occurredAt: Instant,
    val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    val kind: Kind,
    val merchant: String?,
    val cardMask: String?,
    val categoryId: Long?,
    val confidence: Float,
    val needsReview: Boolean,
    val source: Source,
    val dedupKey: String,
    val note: String? = null,
    val linkedTransactionId: Long? = null,
    val userEdited: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val money: Money get() = Money(amountMinor, currency)
}

/** Статус разбора исходного сообщения. */
enum class ParseStatus { PARSED, UNCERTAIN, NOT_FINANCIAL, FAILED }

/**
 * Исходное сообщение банка. Хранится только для источников из белого списка
 * (уведомления не из белого списка не сохраняются вообще). Текст лежит в зашифрованной БД.
 *
 * @property sourceKey идентичность источника: SMS `_id|date|хеш текста` (старые записи — просто `_id`)
 *   или `key|postTime|хеш текста` уведомления.
 */
data class RawMessage(
    val id: Long = 0,
    val channel: MessageChannel,
    val sender: String,
    val sourceKey: String,
    val receivedAt: Instant,
    val text: String,
    val textHash: String,
    val parseStatus: ParseStatus,
    val parserVersion: Int,
    val transactionId: Long?,
)

/**
 * Категория расходов/доходов. [key] стабилен для базовых категорий и не переводится.
 * @property colorArgb цвет, выбранный пользователем; `null` — цвет из палитры темы по [key].
 */
data class Category(
    val id: Long = 0,
    val key: String,
    val name: String,
    val iconKey: String,
    val colorArgb: Int? = null,
    val isSystem: Boolean,
    val sortOrder: Int,
)

/** Базовые категории (этап 6). Названия на русском — это данные, не строки UI. */
object DefaultCategories {
    data class Seed(val key: String, val name: String, val iconKey: String)

    const val OTHER_KEY = "other"
    const val TRANSFERS_KEY = "transfers"

    val all: List<Seed> = listOf(
        Seed("groceries", "Продукты", "shopping_basket"),
        Seed("transport", "Транспорт", "directions_bus"),
        Seed("entertainment", "Развлечения", "theater_comedy"),
        Seed("shopping", "Покупки", "shopping_bag"),
        Seed("restaurants", "Рестораны", "restaurant"),
        Seed("utilities", "Коммуналка", "home"),
        Seed("subscriptions", "Подписки", "autorenew"),
        Seed("health", "Здоровье", "favorite"),
        Seed(TRANSFERS_KEY, "Переводы", "swap_horiz"),
        Seed(OTHER_KEY, "Другое", "category"),
    )
}

/** Правило пользователя «мерчант → категория». Приоритетнее встроенного словаря. */
data class MerchantRule(
    val id: Long = 0,
    val merchantPattern: String,
    val categoryId: Long,
    val createdAt: Instant,
)

/** Карта пользователя. [isOwn] — переводы между своими картами становятся INTERNAL_TRANSFER. */
data class Card(
    val mask: String,
    val label: String? = null,
    val isOwn: Boolean,
)

/** Известный банковский остаток по карте на момент сообщения: «Остаток *1234 на 14:05». */
data class KnownBalance(
    val cardMask: String,
    val amountMinor: Long,
    val currency: String,
    val asOf: Instant,
    val rawMessageId: Long?,
)

/** Период регулярного платежа. */
enum class SubscriptionPeriod(val approxDays: Int) { WEEKLY(7), MONTHLY(30), YEARLY(365) }

enum class SubscriptionStatus { CANDIDATE, CONFIRMED, DISMISSED }

/** Регулярный платёж (этап 8). */
data class Subscription(
    val id: Long = 0,
    val merchant: String,
    val amountMinor: Long,
    val currency: String,
    val period: SubscriptionPeriod,
    val lastChargeAt: Instant,
    val nextChargeAt: Instant,
    val status: SubscriptionStatus,
    val categoryId: Long? = null,
)
