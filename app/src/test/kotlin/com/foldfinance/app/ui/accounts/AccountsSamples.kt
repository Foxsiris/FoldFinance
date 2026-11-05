package com.foldfinance.app.ui.accounts

import androidx.lifecycle.SavedStateHandle
import com.foldfinance.app.ScreenshotClock
import com.foldfinance.app.ui.fakes.FakeBalanceRepository
import com.foldfinance.app.ui.fakes.FakeCardRepository
import com.foldfinance.app.ui.fakes.FakeCategoryRepository
import com.foldfinance.app.ui.fakes.FakeTransactionRepository
import com.foldfinance.app.ui.fakes.SampleData
import com.foldfinance.core.model.Card
import com.foldfinance.core.model.KnownBalance
import com.foldfinance.core.model.Source
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import java.time.Instant
import java.time.LocalDateTime

/**
 * Deterministic, anonymised cards for «Счета и карты» around «now» of [ScreenshotClock]
 * (5 November 2026, 14:05 Moscow). Test-only.
 *
 * - `*4821` «Зарплатная», own: the operations of [SampleData], two transfers to `*1177`.
 * - `*1177` «Накопительный», own: receives the transfers (one also seen from its side), interest.
 * - `*9034`, not marked: one purchase, no balance from the bank.
 */
object AccountsSamples {
    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(SampleData.zone).toInstant()

    private fun tx(
        id: Long,
        local: String,
        amountMinor: Long,
        card: String?,
        direction: Direction,
        kind: Kind,
        merchant: String? = null,
        category: String? = null,
        counterparty: String? = null,
        parsed: Direction? = direction,
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
            categoryId = category?.let(SampleData::categoryId),
            confidence = 0.95f,
            needsReview = false,
            source = Source.SMS,
            dedupKey = "acc-$id",
            createdAt = time,
            updatedAt = time,
            counterpartyCardMask = counterparty,
            parsedDirection = parsed,
        )
    }

    val cards: List<Card> = listOf(
        Card("*4821", label = "Зарплатная", isOwn = true),
        Card("*1177", label = "Накопительный", isOwn = true),
        Card("*9034", label = null, isOwn = false),
    )

    val transactions: List<Transaction> = SampleData.transactions.map { t ->
        // The transfer of the sample data, with the sides the parser saw: from *4821 to *1177.
        if (t.id == 13L) t.copy(counterpartyCardMask = "*1177", parsedDirection = Direction.EXPENSE) else t
    } + listOf(
        // The same transfer from the savings account's side, a minute later: one movement.
        tx(101, "2026-11-01T15:31", 1_000_000, "*1177", Direction.INTERNAL_TRANSFER, Kind.TRANSFER, counterparty = "*4821", parsed = Direction.INCOME),
        tx(102, "2026-11-04T12:00", 500_000, "*4821", Direction.INTERNAL_TRANSFER, Kind.TRANSFER, counterparty = "*1177", parsed = Direction.EXPENSE),
        tx(103, "2026-11-03T09:00", 4_210, "*1177", Direction.INCOME, Kind.UNKNOWN, merchant = "Проценты на остаток"),
        tx(104, "2026-11-02T17:20", 89_900, "*9034", Direction.EXPENSE, Kind.PURCHASE, merchant = "Wildberries", category = "shopping"),
        tx(105, "2026-10-20T13:00", 45_000, "*9034", Direction.EXPENSE, Kind.PURCHASE, merchant = "Лента", category = "groceries"),
    )

    val balances: List<KnownBalance> = listOf(
        KnownBalance("*4821", 4_821_035, "RUB", at("2026-11-05T13:42"), 10),
        KnownBalance("*4821", 4_945_085, "RUB", at("2026-11-05T10:00"), 30),
        KnownBalance("*4821", 5_233_085, "RUB", at("2026-11-04T18:30"), 60),
        KnownBalance("*4821", 6_120_415, "RUB", at("2026-11-03T19:05"), 80),
        KnownBalance("*4821", 6_390_000, "RUB", at("2026-11-01T15:30"), 130),
        KnownBalance("*4821", 7_502_000, "RUB", at("2026-10-28T19:30"), 150),
        KnownBalance("*1177", 1_504_210, "RUB", at("2026-11-04T12:01"), 1020),
        KnownBalance("*1177", 1_004_210, "RUB", at("2026-11-03T09:00"), 1030),
        KnownBalance("*1177", 1_000_000, "RUB", at("2026-11-01T15:31"), 1010),
        KnownBalance("*1177", 0, "RUB", at("2026-10-30T08:00"), 1000),
    )

    fun viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        cards: FakeCardRepository = FakeCardRepository(this.cards),
        balances: FakeBalanceRepository = FakeBalanceRepository(this.balances),
        transactions: FakeTransactionRepository = FakeTransactionRepository(this.transactions),
    ) = AccountsViewModel(handle, cards, balances, transactions, FakeCategoryRepository(), ScreenshotClock)
}
