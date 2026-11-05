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

/** Результат [IngestRepository.ingest] и [IngestRepository.recordFailed]. */
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

    /**
     * Сообщение с таким ключом источника уже сохранено. Дешёвая проверка без разбора текста —
     * для повторного чтения окна SMS ниже курсора дельта-скана.
     */
    suspend fun isKnown(channel: MessageChannel, sourceKey: String): Boolean

    /**
     * Сохранить сообщение, разбор/запись которого упали ([ingest] бросил исключение), со статусом
     * FAILED и текущей версией парсера, без операций: оно не теряется, видно в «Не распознано» и
     * будет перепарсено следующей версией парсера. Идемпотентно (уже сохранённое не меняется).
     *
     * @return [IngestOutcome.AlreadyProcessed] или [IngestOutcome.Unrecognized] с причиной
     *   [com.foldfinance.parser.UncertainReasons.PARSER_ERROR].
     */
    suspend fun recordFailed(input: IncomingMessage): IngestOutcome
}

/**
 * Наблюдатель приёма: вызывается после записи каждого сообщения (вне транзакции БД). Точка
 * подключения фоновой дообработки локальной моделью — `:app` ставит задачу, если сообщение не
 * распознано или ушло на проверку. Не должен бросать исключения и не должен блокировать поток.
 */
fun interface IngestObserver {
    fun onIngested(outcome: IngestOutcome)
}

/** Построение `sourceKey` и хешей текста. */
object SourceKeys {
    /**
     * Ключ SMS: `_id|date|первые 16 hex SHA-256 текста`.
     *
     * Одного `_id` мало: в AOSP таблица `sms` без AUTOINCREMENT, и после удаления последней SMS
     * следующая получает тот же `_id` — по старому ключу она считалась бы уже обработанной.
     * Записи, сохранённые со старым ключом (только `_id`), находятся по содержимому
     * (`RawMessageDao.findSameContent`) и при встрече переводятся на новый ключ ([isLegacySms]).
     */
    fun sms(smsId: Long, dateMillis: Long, text: String): String =
        "$smsId|$dateMillis|${sha256Hex(text).take(SMS_HASH_CHARS)}"

    /** Ключ SMS старого формата (до БД v2): только `_id`. */
    fun isLegacySms(sourceKey: String): Boolean = sourceKey.isNotEmpty() && sourceKey.all { it.isDigit() }

    private const val SMS_HASH_CHARS = 16

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
