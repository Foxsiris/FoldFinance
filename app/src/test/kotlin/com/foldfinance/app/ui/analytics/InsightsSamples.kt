package com.foldfinance.app.ui.analytics

import com.foldfinance.core.model.Category
import com.foldfinance.core.model.DefaultCategories
import com.foldfinance.core.model.Source
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * Deterministic test-only history for the insight screens (CLAUDE.md, «Только реальные данные»:
 * fixtures live in tests only). Seeded random, so screenshots never change between runs.
 */
object InsightsSamples {
    val zone: ZoneId = ZoneId.of("Europe/Moscow")

    /** Base categories with ids 1…10 in `DefaultCategories` order. */
    val categories: List<Category> = DefaultCategories.all.mapIndexed { index, seed ->
        Category(id = index + 1L, key = seed.key, name = seed.name, iconKey = seed.iconKey, isSystem = true, sortOrder = index)
    }

    fun categoryId(key: String): Long = categories.first { it.key == key }.id

    private var nextId = 1L

    fun tx(
        date: LocalDate,
        time: String,
        amountMinor: Long,
        merchant: String,
        categoryKey: String?,
        direction: Direction = Direction.EXPENSE,
        currency: String = "RUB",
        source: Source = Source.SMS,
        needsReview: Boolean = false,
    ): Transaction {
        val at: Instant = date.atTime(LocalTime.parse(time)).atZone(zone).toInstant()
        return Transaction(
            id = nextId++,
            occurredAt = at,
            amountMinor = amountMinor,
            currency = currency,
            direction = direction,
            kind = when (direction) {
                Direction.INCOME -> Kind.TOP_UP
                Direction.REFUND -> Kind.REFUND
                Direction.INTERNAL_TRANSFER -> Kind.TRANSFER
                Direction.EXPENSE -> Kind.PURCHASE
            },
            merchant = merchant,
            cardMask = "*1234",
            categoryId = categoryKey?.let(::categoryId),
            confidence = 0.95f,
            needsReview = needsReview,
            source = source,
            dedupKey = "sample:${nextId}",
            createdAt = at,
            updatedAt = at,
        )
    }

    /** September 1 – November 5, 2026: daily groceries and transport, salaries, subscriptions, a refund, one USD purchase. */
    val history: List<Transaction> by lazy {
        nextId = 1L
        val random = Random(42)
        val result = mutableListOf<Transaction>()
        var date = LocalDate.of(2026, 9, 1)
        val end = LocalDate.of(2026, 11, 5)
        val groceries = listOf("Пятёрочка", "ВкусВилл", "Перекрёсток", "Магнит")
        val food = listOf("Кофемания", "Шоколадница", "Теремок", "Додо Пицца")
        while (!date.isAfter(end)) {
            val weekend = date.dayOfWeek.value >= 6
            result += tx(date, "19:${10 + random.nextInt(40)}", 40_000L + random.nextLong(260_000), groceries[random.nextInt(groceries.size)], "groceries")
            if (!weekend) result += tx(date, "08:${10 + random.nextInt(40)}", 6_400L + random.nextLong(35_000), "Метро и такси", "transport", source = Source.PUSH)
            if (weekend || random.nextInt(4) == 0) {
                result += tx(date, "13:${10 + random.nextInt(40)}", 60_000L + random.nextLong(320_000), food[random.nextInt(food.size)], "restaurants")
            }
            if (random.nextInt(9) == 0) result += tx(date, "16:20", 150_000L + random.nextLong(900_000), "Ozon", "shopping")
            if (random.nextInt(14) == 0) result += tx(date, "21:00", 90_000L + random.nextLong(200_000), "Кинотеатр", "entertainment")
            if (date.dayOfMonth == 3) result += tx(date, "09:00", 39_900, "Яндекс Плюс", "subscriptions")
            if (date.dayOfMonth == 11) result += tx(date, "10:30", 649_000, "Мосэнергосбыт", "utilities")
            if (date.dayOfMonth == 5) result += tx(date, "10:00", 7_500_000, "Зарплата", null, Direction.INCOME)
            if (date.dayOfMonth == 20) result += tx(date, "10:00", 4_500_000, "Аванс", null, Direction.INCOME)
            if (date.dayOfMonth == 14) result += tx(date, "12:00", 210_000, "Аптека", "health")
            date = date.plusDays(1)
        }
        result += tx(LocalDate.of(2026, 8, 11), "10:30", 649_000, "Мосэнергосбыт", "utilities")
        result += tx(LocalDate.of(2026, 10, 17), "15:40", 259_900, "Ozon", "shopping", Direction.REFUND, source = Source.PUSH)
        result += tx(LocalDate.of(2026, 10, 17), "11:05", 1_299, "Steam", "entertainment", currency = "USD", needsReview = true)
        result += tx(LocalDate.of(2026, 10, 17), "18:30", 1_000_000, "Перевод себе *4821", "transfers", Direction.INTERNAL_TRANSFER)
        result
    }
}
