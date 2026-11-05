package com.foldfinance.parser

/**
 * Версия правил распознавания. Увеличивается при любом изменении парсеров,
 * которое может дать другой результат на уже сохранённых сообщениях.
 * `:core` перепарсивает `RawMessage` с версией меньше текущей.
 */
const val PARSER_VERSION: Int = 3

/** Канал, по которому пришло сообщение. */
enum class MessageChannel { SMS, PUSH }

/**
 * Направление денег относительно пользователя.
 *
 * [REFUND] уменьшает расходы своей категории и не является доходом.
 * [INTERNAL_TRANSFER] исключается из доходов и расходов.
 */
enum class Direction { EXPENSE, INCOME, REFUND, INTERNAL_TRANSFER }

/** Вид операции, как его понял парсер. */
enum class Kind {
    PURCHASE, TRANSFER, CASH_WITHDRAWAL, TOP_UP, CREDIT, REFUND, FEE, SUBSCRIPTION, FX, UNKNOWN
}

/**
 * Метаданные сообщения, известные до разбора текста.
 *
 * @property channel SMS или push-уведомление.
 * @property sender адрес SMS (`900`, `Tinkoff`, …) или имя пакета приложения для push.
 * @property receivedAtMillis время получения, epoch millis (UTC).
 * @property title заголовок уведомления, если есть (у SMS — `null`).
 */
data class MessageMeta(
    val channel: MessageChannel,
    val sender: String,
    val receivedAtMillis: Long,
    val title: String? = null,
)

/**
 * Одна денежная операция, найденная в сообщении. Сумма всегда положительна,
 * знак задаёт [direction].
 *
 * @property amountMinor сумма в минорных единицах (копейки, центы).
 * @property currency ISO 4217, например `RUB`, `USD`.
 * @property occurredAtMillis время операции из текста, если указано; иначе `null` (берётся время получения).
 * @property confidence уверенность 0.0..1.0; ниже [NEEDS_REVIEW_THRESHOLD] операция уходит на проверку.
 */
data class ParsedOperation(
    val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    val kind: Kind,
    val merchant: String? = null,
    val cardMask: String? = null,
    val counterpartyCardMask: String? = null,
    val occurredAtMillis: Long? = null,
    val confidence: Float,
) {
    init {
        require(amountMinor > 0) { "amountMinor must be positive" }
        require(currency.length == 3) { "currency must be ISO 4217" }
        require(confidence in 0f..1f) { "confidence must be in 0..1" }
    }

    companion object {
        const val NEEDS_REVIEW_THRESHOLD: Float = 0.75f
    }
}

/**
 * Известный остаток по карте из фразы «Баланс/Остаток». Это банковское значение
 * на момент сообщения, а не сумма по операциям.
 */
data class ParsedBalance(
    val amountMinor: Long,
    val currency: String,
    val cardMask: String?,
)

/** Результат разбора одного сообщения. */
sealed interface ParseResult {
    /** Это не денежная операция: реклама, код подтверждения, сервисное сообщение. */
    data object NotFinancial : ParseResult

    /**
     * Найдены операции. Комиссия всегда отдельная операция с [Kind.FEE].
     * @property operations одна или несколько операций (например, покупка + комиссия).
     */
    data class Parsed(
        val operations: List<ParsedOperation>,
        val balance: ParsedBalance? = null,
        val parserId: String,
    ) : ParseResult {
        init { require(operations.isNotEmpty()) { "Parsed must contain at least one operation" } }
    }

    /**
     * Похоже на операцию, но тип или сумма не уверены. Такая операция сохраняется
     * с `needsReview = true`; [partial] — всё, что удалось извлечь.
     */
    data class Uncertain(
        val reason: String,
        val partial: ParsedOperation? = null,
        val balance: ParsedBalance? = null,
        val parserId: String,
    ) : ParseResult
}
