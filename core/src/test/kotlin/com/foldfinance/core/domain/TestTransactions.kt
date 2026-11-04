package com.foldfinance.core.domain

import com.foldfinance.core.model.Source
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Фабрика синтетических операций для доменных тестов. */
object TestTransactions {
    private var seq = 0L

    fun tx(
        at: Instant,
        amountMinor: Long,
        direction: Direction = Direction.EXPENSE,
        currency: String = "RUB",
        categoryId: Long? = null,
        merchant: String? = "SHOP",
        kind: Kind = Kind.PURCHASE,
        needsReview: Boolean = false,
        id: Long = ++seq,
    ): Transaction = Transaction(
        id = id,
        occurredAt = at,
        amountMinor = amountMinor,
        currency = currency,
        direction = direction,
        kind = kind,
        merchant = merchant,
        cardMask = "1234",
        categoryId = categoryId,
        confidence = 1f,
        needsReview = needsReview,
        source = Source.SMS,
        dedupKey = "test-$id",
        createdAt = at,
        updatedAt = at,
    )

    /** Момент локального времени `yyyy-MM-ddTHH:mm` в [zone]. */
    fun at(local: String, zone: ZoneId): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()
}
