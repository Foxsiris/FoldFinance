package com.foldfinance.core.domain.accounts

import com.foldfinance.core.domain.TestTransactions.at
import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.model.Source
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class AccountsCalculatorTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val november = Period.month(YearMonth.of(2026, 11), zone)
    private var seq = 0L

    private fun tx(
        local: String,
        amountMinor: Long,
        direction: Direction,
        card: String? = "*1234",
        currency: String = "RUB",
        kind: Kind = Kind.PURCHASE,
        counterparty: String? = null,
        parsedDirection: Direction? = direction,
    ): Transaction {
        val time: Instant = at(local, zone)
        val id = ++seq
        return Transaction(
            id = id,
            occurredAt = time,
            amountMinor = amountMinor,
            currency = currency,
            direction = direction,
            kind = kind,
            merchant = null,
            cardMask = card,
            categoryId = null,
            confidence = 1f,
            needsReview = false,
            source = Source.SMS,
            dedupKey = "t-$id",
            createdAt = time,
            updatedAt = time,
            counterpartyCardMask = counterparty,
            parsedDirection = parsedDirection,
        )
    }

    /** Перевод между своими: направление операции INTERNAL_TRANSFER, парсер видел расход или доход. */
    private fun internal(local: String, amountMinor: Long, card: String?, counterparty: String?, parsed: Direction?) =
        tx(local, amountMinor, Direction.INTERNAL_TRANSFER, card = card, kind = Kind.TRANSFER, counterparty = counterparty, parsedDirection = parsed)

    @Test fun `card totals split expenses, refunds, income and own transfers per card and currency`() {
        val list = listOf(
            tx("2026-11-02T10:00", 150_000, Direction.EXPENSE),
            tx("2026-11-03T10:00", 50_000, Direction.EXPENSE),
            tx("2026-11-04T10:00", 20_000, Direction.REFUND, kind = Kind.REFUND),
            tx("2026-11-05T09:00", 9_500_000, Direction.INCOME, kind = Kind.TRANSFER),
            internal("2026-11-05T11:00", 1_000_000, "*1234", "*5678", Direction.EXPENSE),
            tx("2026-11-05T12:00", 1_250, Direction.EXPENSE, currency = "USD"),
            tx("2026-11-01T10:00", 70_000, Direction.EXPENSE, card = "*5678"),
            internal("2026-11-05T11:01", 1_000_000, "*5678", "*1234", Direction.INCOME),
            // Вне периода и без карты — не учитываются.
            tx("2026-10-31T23:59", 999_999, Direction.EXPENSE),
            tx("2026-11-02T10:00", 5_000, Direction.EXPENSE, card = null),
        )

        val totals = AccountsCalculator.cardTotals(list, november)

        assertThat(totals.keys).containsExactly("*1234", "*5678")
        val rub = totals.getValue("*1234").byCurrency.first()
        assertThat(rub.currency).isEqualTo("RUB")
        assertThat(rub.expenseMinor).isEqualTo(200_000)
        assertThat(rub.refundMinor).isEqualTo(20_000)
        assertThat(rub.netExpenseMinor).isEqualTo(180_000)
        // Перевод на свою карту не расход, зарплата — поступление.
        assertThat(rub.incomeMinor).isEqualTo(9_500_000)
        assertThat(rub.internalOutMinor).isEqualTo(1_000_000)
        assertThat(rub.internalInMinor).isEqualTo(0)
        assertThat(rub.operationCount).isEqualTo(5)
        assertThat(totals.getValue("*1234").byCurrency.map { it.currency }).containsExactly("RUB", "USD").inOrder()

        val second = totals.getValue("*5678").byCurrency.single()
        assertThat(second.netExpenseMinor).isEqualTo(70_000)
        assertThat(second.incomeMinor).isEqualTo(0)
        assertThat(second.internalInMinor).isEqualTo(1_000_000)
    }

    @Test fun `debit and credit messages of one own transfer are one movement`() {
        val out = internal("2026-11-05T11:00", 1_000_000, "*1234", "*5678", Direction.EXPENSE)
        val incoming = internal("2026-11-05T11:02", 1_000_000, "*5678", "*1234", Direction.INCOME)

        val movements = AccountsCalculator.internalMovements(listOf(incoming, out), november)

        val m = movements.single()
        assertThat(m.fromMask).isEqualTo("*1234")
        assertThat(m.toMask).isEqualTo("*5678")
        assertThat(m.occurredAt).isEqualTo(out.occurredAt)
        assertThat(m.transactionIds).containsExactly(out.id, incoming.id).inOrder()
        assertThat(m.involves("*5678")).isTrue()
    }

    @Test fun `transfers far apart, of other amounts or between other cards stay separate`() {
        val list = listOf(
            internal("2026-11-05T11:00", 1_000_000, "*1234", "*5678", Direction.EXPENSE),
            // 10 минут спустя — другой перевод.
            internal("2026-11-05T11:10", 1_000_000, "*5678", "*1234", Direction.INCOME),
            // Та же минута, другая сумма.
            internal("2026-11-05T11:00", 500_000, "*5678", "*1234", Direction.INCOME),
            // Та же сумма, но зачисление на третью карту.
            internal("2026-11-05T11:01", 1_000_000, "*9012", "*1234", Direction.INCOME),
        )
        val movements = AccountsCalculator.internalMovements(list, november)
        assertThat(movements).hasSize(4)
        assertThat(movements.all { it.transactionIds.size == 1 }).isTrue()
    }

    @Test fun `pairs sum movements from card to card, unknown direction is left out`() {
        val list = listOf(
            internal("2026-11-02T10:00", 300_000, "*1234", "*5678", Direction.EXPENSE),
            internal("2026-11-02T10:01", 300_000, "*5678", "*1234", Direction.INCOME),
            internal("2026-11-03T10:00", 200_000, "*1234", "*5678", Direction.EXPENSE),
            internal("2026-11-04T10:00", 50_000, "*5678", "*1234", Direction.EXPENSE),
            // Источник не указан в сообщении: пара «? → *5678».
            internal("2026-11-04T12:00", 5_000, null, "*5678", Direction.EXPENSE),
            // Ручной перевод без данных парсера — направление неизвестно.
            internal("2026-11-04T13:00", 7_000, "*1234", null, null),
            // Чужой перевод — не между своими.
            tx("2026-11-04T14:00", 1_000, Direction.EXPENSE, kind = Kind.TRANSFER, counterparty = "*0000"),
        )
        val movements = AccountsCalculator.internalMovements(list, november)
        assertThat(movements).hasSize(5)
        assertThat(movements.first().occurredAt).isEqualTo(at("2026-11-04T13:00", zone))
        assertThat(movements.single { it.flow == TransferFlow.UNKNOWN }.involves("*1234")).isTrue()

        val pairs = AccountsCalculator.transferPairs(movements)
        assertThat(pairs).containsExactly(
            TransferPair("*1234", "*5678", "RUB", 500_000, 2),
            TransferPair("*5678", "*1234", "RUB", 50_000, 1),
            TransferPair(null, "*5678", "RUB", 5_000, 1),
        ).inOrder()
    }

    @Test fun `flow follows the parsed direction`() {
        assertThat(AccountsCalculator.flowOf(internal("2026-11-02T10:00", 1, "*1", "*2", Direction.EXPENSE))).isEqualTo(TransferFlow.OUT)
        assertThat(AccountsCalculator.flowOf(internal("2026-11-02T10:00", 1, "*1", "*2", Direction.INCOME))).isEqualTo(TransferFlow.IN)
        assertThat(AccountsCalculator.flowOf(internal("2026-11-02T10:00", 1, "*1", "*2", Direction.INTERNAL_TRANSFER))).isEqualTo(TransferFlow.UNKNOWN)
        assertThat(AccountsCalculator.flowOf(internal("2026-11-02T10:00", 1, "*1", "*2", null))).isEqualTo(TransferFlow.UNKNOWN)
    }
}
