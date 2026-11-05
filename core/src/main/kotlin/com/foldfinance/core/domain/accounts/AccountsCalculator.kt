package com.foldfinance.core.domain.accounts

import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import java.time.Duration
import java.time.Instant

/**
 * Итоги одной карты в одной валюте за период — **по известным операциям**, а не банковский остаток.
 * Все суммы в минорных единицах, неотрицательные.
 *
 * @property expenseMinor расходы (EXPENSE).
 * @property refundMinor возвраты (REFUND): уменьшают расходы, доходом не считаются.
 * @property netExpenseMinor [expenseMinor] − [refundMinor]; может быть < 0, если вернули больше, чем потратили.
 * @property incomeMinor поступления (INCOME).
 * @property internalOutMinor ушло на свои карты (INTERNAL_TRANSFER) — не расход.
 * @property internalInMinor пришло со своих карт (INTERNAL_TRANSFER) — не доход.
 * @property operationCount учтённые операции, включая переводы между своими картами.
 */
data class CardCurrencyTotals(
    val currency: String,
    val expenseMinor: Long = 0,
    val refundMinor: Long = 0,
    val incomeMinor: Long = 0,
    val internalOutMinor: Long = 0,
    val internalInMinor: Long = 0,
    val operationCount: Int = 0,
) {
    val netExpenseMinor: Long get() = expenseMinor - refundMinor
}

/** Итоги карты [cardMask] за период по валютам; порядок — по убыванию нетто-расходов, затем по коду. */
data class CardTotals(val cardMask: String, val byCurrency: List<CardCurrencyTotals>) {
    val isEmpty: Boolean get() = byCurrency.isEmpty()
}

/** Сторона перевода между своими картами, известная из сообщения. */
enum class TransferFlow {
    /** Деньги ушли с карты операции на [InternalMovement.toMask]. */
    OUT,

    /** Деньги пришли на карту операции с [InternalMovement.fromMask]. */
    IN,

    /** Направление неизвестно (ручная операция, нет данных парсера): откуда и куда — не угадываем. */
    UNKNOWN,
}

/**
 * Один перевод между своими картами (INTERNAL_TRANSFER). Если о переводе пришли сообщения обеих
 * карт (списание с одной и зачисление на другую), это одно движение: [transactionIds] — обе операции.
 *
 * @property fromMask карта списания; `null` — неизвестна.
 * @property toMask карта зачисления; `null` — неизвестна.
 * @property flow как направление определено; при [TransferFlow.UNKNOWN] обе стороны — `null`, а
 *   [cardMask] — единственная известная карта.
 */
data class InternalMovement(
    val transactionIds: List<Long>,
    val occurredAt: Instant,
    val amountMinor: Long,
    val currency: String,
    val fromMask: String?,
    val toMask: String?,
    val flow: TransferFlow,
    val cardMask: String?,
) {
    /** Карта участвует в движении (как источник, получатель или единственная известная карта). */
    fun involves(mask: String): Boolean = fromMask == mask || toMask == mask || cardMask == mask
}

/**
 * Сумма переводов по паре «откуда → куда» за период.
 *
 * @property fromMask / [toMask] `null` — карта в сообщении не указана.
 */
data class TransferPair(
    val fromMask: String?,
    val toMask: String?,
    val currency: String,
    val totalMinor: Long,
    val count: Int,
)

/**
 * Расчёты экрана «Счета и карты». Чистые функции над операциями из БД.
 *
 * Правила (CLAUDE.md, инварианты):
 * - всё — «по известным операциям», банковский остаток считается отдельно ([com.foldfinance.core.model.KnownBalance]);
 * - валюты не смешиваются, курсов нет;
 * - возврат уменьшает расходы и не доход;
 * - INTERNAL_TRANSFER не входит ни в расходы, ни в доходы — считается отдельно.
 */
object AccountsCalculator {

    /** Окно, в котором списание с одной своей карты и зачисление на другую считаются одним переводом. */
    val MirrorWindow: Duration = Duration.ofMinutes(3)

    /**
     * Итоги по каждой карте за [period]. Операции без карты не учитываются.
     *
     * Перевод между своими картами попадает в [CardCurrencyTotals.internalOutMinor] /
     * [CardCurrencyTotals.internalInMinor] карты операции по направлению парсера; если направление
     * неизвестно — только в счётчик операций.
     *
     * @return карта → итоги; карт без операций в периоде нет.
     */
    fun cardTotals(transactions: List<Transaction>, period: Period): Map<String, CardTotals> =
        transactions
            .filter { it.cardMask != null && it.occurredAt in period }
            .groupBy { it.cardMask!! }
            .mapValues { (mask, txs) ->
                val byCurrency = txs.groupBy { it.currency }
                    .map { (currency, list) -> list.fold(CardCurrencyTotals(currency)) { acc, tx -> acc.add(tx) } }
                    .sortedWith(compareByDescending<CardCurrencyTotals> { it.netExpenseMinor }.thenBy { it.currency })
                CardTotals(mask, byCurrency)
            }

    private fun CardCurrencyTotals.add(tx: Transaction): CardCurrencyTotals {
        val a = tx.amountMinor
        val counted = copy(operationCount = operationCount + 1)
        return when (tx.direction) {
            Direction.EXPENSE -> counted.copy(expenseMinor = Math.addExact(expenseMinor, a))
            Direction.REFUND -> counted.copy(refundMinor = Math.addExact(refundMinor, a))
            Direction.INCOME -> counted.copy(incomeMinor = Math.addExact(incomeMinor, a))
            Direction.INTERNAL_TRANSFER -> when (flowOf(tx)) {
                TransferFlow.OUT -> counted.copy(internalOutMinor = Math.addExact(internalOutMinor, a))
                TransferFlow.IN -> counted.copy(internalInMinor = Math.addExact(internalInMinor, a))
                TransferFlow.UNKNOWN -> counted
            }
        }
    }

    /**
     * Переводы между своими картами за [period], новые сверху. Пара операций «списание с A на B» и
     * «зачисление на B с A» на ту же сумму и валюту в окне ±[MirrorWindow] — одно движение.
     */
    fun internalMovements(transactions: List<Transaction>, period: Period): List<InternalMovement> {
        val single = transactions
            .filter { it.direction == Direction.INTERNAL_TRANSFER && it.occurredAt in period }
            .sortedWith(compareBy<Transaction> { it.occurredAt }.thenBy { it.id })
            .map(::movementOf)
        val merged = ArrayList<InternalMovement>(single.size)
        val used = BooleanArray(single.size)
        for (i in single.indices) {
            if (used[i]) continue
            val a = single[i]
            val j = (i + 1 until single.size).firstOrNull { k -> !used[k] && isMirror(a, single[k]) }
            if (j == null) {
                merged += a
            } else {
                used[j] = true
                val b = single[j]
                // Списание точнее по времени отправки: берём его момент и стороны, где они известны.
                val out = if (a.flow == TransferFlow.OUT) a else b
                merged += out.copy(
                    transactionIds = (a.transactionIds + b.transactionIds).sorted(),
                    fromMask = a.fromMask ?: b.fromMask,
                    toMask = a.toMask ?: b.toMask,
                )
            }
        }
        return merged.sortedWith(compareByDescending<InternalMovement> { it.occurredAt }.thenByDescending { it.transactionIds.first() })
    }

    /**
     * Суммы по парам «откуда → куда». Переводы с неизвестным направлением в пары не входят (их
     * показывают списком). Порядок — по убыванию суммы, затем по картам.
     */
    fun transferPairs(movements: List<InternalMovement>): List<TransferPair> =
        movements
            .filter { it.flow != TransferFlow.UNKNOWN }
            .groupBy { Triple(it.fromMask, it.toMask, it.currency) }
            .map { (key, list) ->
                TransferPair(
                    fromMask = key.first,
                    toMask = key.second,
                    currency = key.third,
                    totalMinor = list.fold(0L) { acc, m -> Math.addExact(acc, m.amountMinor) },
                    count = list.size,
                )
            }
            .sortedWith(
                compareByDescending<TransferPair> { it.totalMinor }
                    .thenBy { it.currency }
                    .thenBy(nullsLast()) { it.fromMask }
                    .thenBy(nullsLast()) { it.toMask },
            )

    /** Направление перевода относительно карты операции — по направлению парсера. */
    fun flowOf(tx: Transaction): TransferFlow = when (tx.parsedDirection) {
        Direction.EXPENSE -> TransferFlow.OUT
        Direction.INCOME -> TransferFlow.IN
        else -> TransferFlow.UNKNOWN
    }

    private fun movementOf(tx: Transaction): InternalMovement {
        val flow = flowOf(tx)
        return InternalMovement(
            transactionIds = listOf(tx.id),
            occurredAt = tx.occurredAt,
            amountMinor = tx.amountMinor,
            currency = tx.currency,
            fromMask = when (flow) {
                TransferFlow.OUT -> tx.cardMask
                TransferFlow.IN -> tx.counterpartyCardMask
                TransferFlow.UNKNOWN -> null
            },
            toMask = when (flow) {
                TransferFlow.OUT -> tx.counterpartyCardMask
                TransferFlow.IN -> tx.cardMask
                TransferFlow.UNKNOWN -> null
            },
            flow = flow,
            cardMask = tx.cardMask,
        )
    }

    /** [a] и [b] — две стороны одного перевода: списание и зачисление между одними и теми же картами. */
    private fun isMirror(a: InternalMovement, b: InternalMovement): Boolean {
        if (a.amountMinor != b.amountMinor || a.currency != b.currency) return false
        if (setOf(a.flow, b.flow) != setOf(TransferFlow.OUT, TransferFlow.IN)) return false
        if (Duration.between(a.occurredAt, b.occurredAt).abs() > MirrorWindow) return false
        // Стороны не должны противоречить друг другу; неизвестная сторона совпадает с любой.
        fun agree(x: String?, y: String?) = x == null || y == null || x == y
        if (!agree(a.fromMask, b.fromMask) || !agree(a.toMask, b.toMask)) return false
        // Обе операции — по разным картам, иначе это два разных перевода одной карты.
        return a.cardMask != b.cardMask
    }
}
