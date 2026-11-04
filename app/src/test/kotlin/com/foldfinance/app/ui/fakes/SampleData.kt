package com.foldfinance.app.ui.fakes

import com.foldfinance.app.ScreenshotClock
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.DefaultCategories
import com.foldfinance.core.model.KnownBalance
import com.foldfinance.core.model.ParseStatus
import com.foldfinance.core.model.RawMessage
import com.foldfinance.core.model.Source
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.foldfinance.parser.MessageChannel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Deterministic, anonymised data for tests and screenshots, around «now» of [ScreenshotClock]
 * (5 November 2026, 14:05 Moscow). Test-only (CLAUDE.md, «Только реальные данные»).
 */
object SampleData {
    val zone: ZoneId = ZoneId.of("Europe/Moscow")

    val categories: List<Category> = DefaultCategories.all.mapIndexed { index, seed ->
        Category(id = index + 1L, key = seed.key, name = seed.name, iconKey = seed.iconKey, isSystem = true, sortOrder = index)
    }

    fun categoryId(key: String): Long = categories.first { it.key == key }.id

    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()

    private fun tx(
        id: Long,
        local: String,
        amountMinor: Long,
        merchant: String?,
        category: String?,
        direction: Direction = Direction.EXPENSE,
        kind: Kind = Kind.PURCHASE,
        source: Source = Source.SMS,
        needsReview: Boolean = false,
        card: String? = "4821",
        note: String? = null,
    ): Transaction {
        val time = at(local)
        return Transaction(
            id = id,
            occurredAt = time,
            amountMinor = amountMinor,
            currency = "RUB",
            direction = direction,
            kind = kind,
            merchant = merchant,
            cardMask = card,
            categoryId = category?.let(::categoryId),
            confidence = if (needsReview) 0.4f else 0.95f,
            needsReview = needsReview,
            source = source,
            dedupKey = "sample-$id",
            note = note,
            createdAt = time,
            updatedAt = time,
        )
    }

    val transactions: List<Transaction> = listOf(
        tx(1, "2026-11-05T13:42", 124_050, "Пятёрочка", "groceries"),
        tx(2, "2026-11-05T12:40", 41_900, "Яндекс Такси", "transport", source = Source.PUSH, needsReview = true),
        tx(3, "2026-11-05T10:00", 9_500_000, "Зарплата", null, direction = Direction.INCOME, kind = Kind.TRANSFER),
        tx(4, "2026-11-05T09:12", 38_000, "Кофемания", "restaurants"),
        tx(16, "2026-11-05T08:55", 200_000, null, null, kind = Kind.UNKNOWN, needsReview = true),
        tx(5, "2026-11-04T20:15", 259_900, "Ozon", "shopping", source = Source.PUSH, note = "Кроссовки"),
        tx(6, "2026-11-04T18:30", 86_420, "ВкусВилл", "groceries"),
        tx(7, "2026-11-04T08:10", 6_500, "Метро", "transport"),
        tx(8, "2026-11-03T19:05", 150_000, "ИП Сидоров", null, kind = Kind.TRANSFER, needsReview = true),
        tx(9, "2026-11-03T14:20", 129_900, "Ozon", "shopping", direction = Direction.REFUND, kind = Kind.REFUND, source = Source.PUSH),
        tx(10, "2026-11-03T13:00", 74_200, "Аптека Ригла", "health"),
        tx(11, "2026-11-02T21:40", 39_900, "Яндекс Плюс", "subscriptions"),
        tx(12, "2026-11-02T11:00", 321_477, "МосЭнергоСбыт", "utilities"),
        tx(13, "2026-11-01T15:30", 1_000_000, null, "transfers", direction = Direction.INTERNAL_TRANSFER, kind = Kind.TRANSFER),
        tx(14, "2026-11-01T12:10", 231_500, "Лента", "groceries"),
        tx(15, "2026-10-28T19:30", 112_000, "Шоколадница", "restaurants"),
        tx(17, "2026-11-02T16:45", 54_000, "Кинотеатр Октябрь", "entertainment", source = Source.MANUAL, card = null),
    )

    val rawMessages: List<RawMessage> = listOf(
        raw(1, "900", "2026-11-05T13:42", "Покупка 1240,50р Карта*4821 PYATEROCHKA 1234 Баланс: 48 210,35р"),
        raw(2, "ru.yandex.taxi", "2026-11-05T12:40", "Поездка оплачена: 419 ₽, карта •4821", MessageChannel.PUSH),
        raw(3, "900", "2026-11-05T10:00", "Зачисление 95000р Карта*4821 Баланс: 49 450,85р"),
        raw(16, "900", "2026-11-05T08:55", "Списание 2000р Карта*4821 Баланс: 50 330,85р"),
        raw(8, "900", "2026-11-03T19:05", "Перевод 1500р Карта*4821 ИП Сидоров Баланс: 61 204,15р"),
        raw(6, "900", "2026-11-04T18:30", "Покупка 864,20р Карта*4821 VKUSVILL Баланс: 52 330,85р"),
    )

    private fun raw(txId: Long, sender: String, local: String, text: String, channel: MessageChannel = MessageChannel.SMS) = RawMessage(
        id = txId * 10,
        channel = channel,
        sender = sender,
        sourceKey = "raw-$txId",
        receivedAt = at(local),
        text = text,
        textHash = "hash-$txId",
        parseStatus = ParseStatus.PARSED,
        parserVersion = 2,
        transactionId = txId,
    )

    val balances: List<KnownBalance> = listOf(
        KnownBalance("4821", 4_821_035, "RUB", at("2026-11-05T13:42"), 10),
        KnownBalance("1177", 1_250_000, "RUB", at("2026-11-04T18:30"), null),
    )

    fun transactionRepository() = FakeTransactionRepository(transactions, rawMessages) { ScreenshotClock.instant() }
}
