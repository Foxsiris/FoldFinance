package com.foldfinance.app.ai.features.anomalies

import com.foldfinance.app.ai.features.common.Fact
import com.foldfinance.app.ai.features.common.FactFormat
import com.foldfinance.core.domain.category.MerchantNormalizer
import com.foldfinance.core.model.Subscription
import com.foldfinance.core.model.SubscriptionStatus
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

/**
 * Something unusual in the user's spending (п.8). Found by Kotlin ([AnomalyDetector]); the model
 * only explains it in words.
 *
 * @property key stable id (for «это нормально — скрыть»).
 */
sealed interface Anomaly {
    val key: String
    val transaction: Transaction
    val at: Instant get() = transaction.occurredAt

    /**
     * Amount at a merchant far above its usual: [transaction] ≥ [AnomalyDetector.UNUSUAL_RATIO] ×
     * the median of earlier purchases there.
     * @property ratio amount / typical, one decimal (HALF_UP).
     */
    data class UnusualAmount(
        override val transaction: Transaction,
        val typicalMinor: Long,
        val history: Int,
        val ratio: BigDecimal,
    ) : Anomaly {
        override val key: String get() = "unusual:${transaction.id}"
    }

    /** The same amount at the same merchant twice within [AnomalyDetector.DOUBLE_CHARGE_WINDOW]. */
    data class DoubleCharge(
        val first: Transaction,
        override val transaction: Transaction,
        val minutesApart: Long,
    ) : Anomaly {
        override val key: String get() = "double:${first.id}-${transaction.id}"
    }

    /** A subscription charged more than its previous charge. */
    data class PriceIncrease(
        val subscription: Subscription,
        override val transaction: Transaction,
        val previousMinor: Long,
    ) : Anomaly {
        override val key: String get() = "price:${transaction.id}"
        val deltaMinor: Long get() = transaction.amountMinor - previousMinor
    }
}

/** Facts of an anomaly for the model (placeholders) and the template text. Values formatted by Kotlin. */
fun Anomaly.facts(): List<Fact> {
    val tx = transaction
    val merchant = Fact("merchant", tx.merchant ?: (this as? Anomaly.PriceIncrease)?.subscription?.merchant.orEmpty())
    val amount = Fact("amount", FactFormat.money(tx.amountMinor, tx.currency))
    return when (this) {
        is Anomaly.UnusualAmount -> listOf(
            merchant, amount,
            Fact("typical", FactFormat.money(typicalMinor, tx.currency)),
            Fact("ratio", ratio.stripTrailingZeros().toPlainString().replace('.', ',')),
        )
        is Anomaly.DoubleCharge -> listOf(merchant, amount, Fact("minutes", minutesApart.toString()))
        is Anomaly.PriceIncrease -> listOf(
            merchant, amount,
            Fact("previous", FactFormat.money(previousMinor, tx.currency)),
            Fact("delta", FactFormat.signedMoney(deltaMinor, tx.currency)),
        )
    }
}

/**
 * Finds anomalies in recent operations. Pure: same input, same output; all arithmetic exact.
 *
 * Rules (only [Direction.EXPENSE], within [lookbackDays] before `now`):
 * - **unusual amount** — at least [MIN_HISTORY] earlier purchases at the same merchant
 *   ([MerchantNormalizer.canonical]) and currency; amount ≥ [UNUSUAL_RATIO] × their median;
 * - **double charge** — same amount, currency and merchant (or, without a merchant, the same
 *   card) within [DOUBLE_CHARGE_WINDOW];
 * - **price increase** — the latest charge of a non-dismissed subscription is higher than the
 *   charge before it (at most [PRICE_HISTORY_DAYS] earlier).
 * A transaction is reported once: double charge > price increase > unusual amount.
 */
object AnomalyDetector {
    const val UNUSUAL_RATIO: Int = 3
    const val MIN_HISTORY: Int = 3
    val DOUBLE_CHARGE_WINDOW: Duration = Duration.ofMinutes(10)
    const val PRICE_HISTORY_DAYS: Long = 400
    private const val HISTORY_LIMIT = 12

    fun detect(
        transactions: List<Transaction>,
        subscriptions: List<Subscription>,
        now: Instant,
        lookbackDays: Long = 30,
    ): List<Anomaly> {
        val since = now.minus(Duration.ofDays(lookbackDays))
        val expenses = transactions.filter { it.direction == Direction.EXPENSE && it.amountMinor > 0 && !it.occurredAt.isAfter(now) }
            .sortedWith(compareBy<Transaction> { it.occurredAt }.thenBy { it.id })
        val recent = expenses.filter { !it.occurredAt.isBefore(since) }

        val doubles = doubleCharges(recent)
        val reported = doubles.flatMap { listOf(it.first.id, it.transaction.id) }.toMutableSet()
        val prices = priceIncreases(expenses, subscriptions, since).filter { it.transaction.id !in reported }
        reported += prices.map { it.transaction.id }
        val unusual = unusualAmounts(expenses, recent).filter { it.transaction.id !in reported }

        return (doubles + prices + unusual).sortedWith(compareByDescending<Anomaly> { it.at }.thenBy { it.key })
    }

    private fun merchantKey(tx: Transaction): String = MerchantNormalizer.canonical(tx.merchant)

    private fun doubleCharges(recent: List<Transaction>): List<Anomaly.DoubleCharge> {
        val out = mutableListOf<Anomaly.DoubleCharge>()
        val used = mutableSetOf<Long>()
        for ((i, a) in recent.withIndex()) {
            if (a.id in used) continue
            for (j in i + 1 until recent.size) {
                val b = recent[j]
                val gap = Duration.between(a.occurredAt, b.occurredAt)
                if (gap > DOUBLE_CHARGE_WINDOW) break
                if (b.id in used || b.amountMinor != a.amountMinor || b.currency != a.currency) continue
                val ka = merchantKey(a)
                val kb = merchantKey(b)
                val same = if (ka.isNotEmpty() || kb.isNotEmpty()) ka == kb else a.cardMask != null && a.cardMask == b.cardMask
                if (!same) continue
                out += Anomaly.DoubleCharge(a, b, gap.toMinutes())
                used += a.id
                used += b.id
                break
            }
        }
        return out
    }

    private fun priceIncreases(expenses: List<Transaction>, subscriptions: List<Subscription>, since: Instant): List<Anomaly.PriceIncrease> =
        subscriptions.filter { it.status != SubscriptionStatus.DISMISSED }.mapNotNull { sub ->
            val key = MerchantNormalizer.canonical(sub.merchant)
            if (key.isEmpty()) return@mapNotNull null
            val charges = expenses.filter { it.currency == sub.currency && merchantKey(it) == key }
            if (charges.size < 2) return@mapNotNull null
            val last = charges.last()
            val previous = charges[charges.size - 2]
            val tooOld = Duration.between(previous.occurredAt, last.occurredAt) > Duration.ofDays(PRICE_HISTORY_DAYS)
            if (last.occurredAt.isBefore(since) || tooOld || last.amountMinor <= previous.amountMinor) null
            else Anomaly.PriceIncrease(sub, last, previous.amountMinor)
        }

    private fun unusualAmounts(expenses: List<Transaction>, recent: List<Transaction>): List<Anomaly.UnusualAmount> {
        val byMerchant = expenses.groupBy { merchantKey(it) to it.currency }
        return recent.mapNotNull { tx ->
            val key = merchantKey(tx)
            if (key.isEmpty()) return@mapNotNull null
            val earlier = byMerchant[key to tx.currency].orEmpty()
                .filter { it.occurredAt.isBefore(tx.occurredAt) }
                .takeLast(HISTORY_LIMIT)
            if (earlier.size < MIN_HISTORY) return@mapNotNull null
            val typical = median(earlier.map { it.amountMinor })
            if (typical <= 0 || tx.amountMinor < typical * UNUSUAL_RATIO) return@mapNotNull null
            val ratio = BigDecimal.valueOf(tx.amountMinor).divide(BigDecimal.valueOf(typical), 1, RoundingMode.HALF_UP)
            Anomaly.UnusualAmount(tx, typical, earlier.size, ratio)
        }
    }

    /** Median; for an even count the lower-rounded mean of the middle two. */
    private fun median(values: List<Long>): Long {
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }
}
