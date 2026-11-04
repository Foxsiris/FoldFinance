package com.foldfinance.core.data.ingest

import com.foldfinance.core.model.Source
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.foldfinance.parser.MessageChannel
import java.security.MessageDigest
import java.time.Instant

/**
 * Входящее банковское сообщение. Вызывающий (SMS-ресивер, дельта-скан, слушатель уведомлений)
 * уже проверил белый список: сообщения не из белого списка сюда не передаются.
 *
 * @property sender адрес SMS или имя пакета приложения.
 * @property sourceKey идентичность источника: для SMS — [SourceKeys.sms], для push — [SourceKeys.push].
 * @property title заголовок уведомления (у SMS — `null`).
 */
data class IncomingMessage(
    val channel: MessageChannel,
    val sender: String,
    val sourceKey: String,
    val receivedAt: Instant,
    val text: String,
    val title: String? = null,
) {
    /** Без текста сообщения: тексты и суммы не должны попадать в логи. */
    override fun toString(): String = "IncomingMessage(channel=$channel, sender=$sender, receivedAt=$receivedAt)"
}

/** Результат [IngestRepository.ingest]. */
sealed interface IngestOutcome {
    /** Сообщение уже было обработано (тот же источник или то же содержимое) — ничего не изменено. */
    data class AlreadyProcessed(val rawMessageId: Long) : IngestOutcome

    /** Сохранено, но это не денежная операция. */
    data class NotFinancial(val rawMessageId: Long) : IngestOutcome

    /**
     * Похоже на операцию, но извлечь ничего не удалось: операция не создана, сообщение сохранено
     * и будет перепарсено новой версией парсера.
     */
    data class Unrecognized(val rawMessageId: Long, val reason: String) : IngestOutcome

    /**
     * Операции записаны.
     * @property transactionIds id операций сообщения (новых или существующих, к которым оно привязано).
     * @property createdCount сколько операций создано.
     * @property mergedCount сколько операций слито с уже существующими из другого канала.
     * @property needsReview хотя бы одна операция ушла в очередь проверки.
     */
    data class Recorded(
        val rawMessageId: Long,
        val transactionIds: List<Long>,
        val createdCount: Int,
        val mergedCount: Int,
        val needsReview: Boolean,
    ) : IngestOutcome
}

/** Приём банковских сообщений — единственная точка записи операций из SMS и push. */
interface IngestRepository {
    /**
     * Принять сообщение: точный дедуп, сохранение [com.foldfinance.core.model.RawMessage], разбор,
     * кросс-источниковый дедуп (±3 мин, другой канал), INTERNAL_TRANSFER по своим картам,
     * категория, очередь проверки, связь возврата с покупкой, известный остаток.
     * Всё в одной транзакции БД; повторный вызов с тем же сообщением идемпотентен.
     */
    suspend fun ingest(input: IncomingMessage): IngestOutcome
}

/** Построение `sourceKey` и хешей текста. */
object SourceKeys {
    /** Ключ SMS: `_id` из `content://sms`. */
    fun sms(smsId: Long): String = smsId.toString()

    /** Ключ уведомления: `key|postTime|sha256(text)` — одно уведомление может обновляться с новым текстом. */
    fun push(notificationKey: String, postTimeMillis: Long, text: String): String =
        "$notificationKey|$postTimeMillis|${sha256Hex(text)}"

    /** SHA-256 текста в UTF-8, hex в нижнем регистре. */
    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/** Правило внутреннего перевода, общее для приёма и пометки «моя карта». */
internal object TransferClassifier {
    /**
     * Перевод на свою карту (или между своими картами — частный случай) — [Direction.INTERNAL_TRANSFER];
     * иначе — направление парсера.
     */
    fun direction(kind: Kind, parsedDirection: Direction, counterpartyCardMask: String?, ownMasks: Set<String>): Direction =
        if (kind == Kind.TRANSFER && counterpartyCardMask != null && counterpartyCardMask in ownMasks) {
            Direction.INTERNAL_TRANSFER
        } else {
            parsedDirection
        }
}

internal fun MessageChannel.toSource(): Source = when (this) {
    MessageChannel.SMS -> Source.SMS
    MessageChannel.PUSH -> Source.PUSH
}
