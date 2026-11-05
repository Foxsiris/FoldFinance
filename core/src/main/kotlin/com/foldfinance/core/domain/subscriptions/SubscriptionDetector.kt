package com.foldfinance.core.domain.subscriptions

import com.foldfinance.core.domain.category.MerchantNormalizer
import com.foldfinance.core.model.Subscription
import com.foldfinance.core.model.SubscriptionPeriod
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Детектор регулярных платежей (этап 8).
 *
 * Признаки подписки:
 * - только [Direction.EXPENSE];
 * - один и тот же мерчант ([MerchantNormalizer.canonical]) и валюта;
 * - сумма в пределах ±[AMOUNT_TOLERANCE_PERCENT]% от медианы серии;
 * - интервалы между соседними списаниями серии ~7 / ~30 / ~365 дней с допуском
 *   [toleranceDays] (±2 / ±4 / ±15);
 * - минимум [minOccurrences] списаний: 3, для годовых — 2.
 *
 * Серия ищется как самая длинная цепочка списаний с подходящими интервалами, поэтому
 * случайные покупки у того же мерчанта на похожую сумму её не ломают.
 */
object SubscriptionDetector {

    /** Допуск суммы относительно медианы, %. */
    const val AMOUNT_TOLERANCE_PERCENT: Int = 10

    /** Допуск интервала между списаниями в днях. */
    fun toleranceDays(period: SubscriptionPeriod): Int = when (period) {
        SubscriptionPeriod.WEEKLY -> 2
        SubscriptionPeriod.MONTHLY -> 4
        SubscriptionPeriod.YEARLY -> 15
    }

    /** Минимальное число списаний, чтобы считать серию подпиской. */
    fun minOccurrences(period: SubscriptionPeriod): Int = when (period) {
        SubscriptionPeriod.YEARLY -> 2
        SubscriptionPeriod.WEEKLY, SubscriptionPeriod.MONTHLY -> 3
    }

    /**
     * Находит кандидатов в подписки.
     *
     * @param transactions история операций (порядок не важен).
     * @param now текущий момент: операции позже него игнорируются, по нему считается [SubscriptionCandidate.isActive].
     * @param zone часовой пояс для календарной арифметики следующего списания.
     * @param includeInactive вернуть и серии, у которых следующее списание просрочено больше допуска
     *   (вероятно, подписка отменена).
     * @return кандидаты, по убыванию числа списаний, затем по мерчанту.
     */
    fun detect(
        transactions: List<Transaction>,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
        includeInactive: Boolean = false,
    ): List<SubscriptionCandidate> {
        val groups = transactions
            .asSequence()
            .filter { it.direction == Direction.EXPENSE && it.amountMinor > 0 && !it.occurredAt.isAfter(now) }
            .map { it to MerchantNormalizer.canonical(it.merchant) }
            .filter { it.second.isNotEmpty() }
            .groupBy({ it.second to it.first.currency }, { it.first })

        val result = mutableListOf<SubscriptionCandidate>()
        for ((key, txs) in groups) {
            for (cluster in amountClusters(txs)) {
                val best = bestSeries(cluster) ?: continue
                val candidate = buildCandidate(key.first, key.second, best.first, best.second, now, zone)
                if (includeInactive || candidate.isActive) result += candidate
            }
        }
        return result.sortedWith(compareByDescending<SubscriptionCandidate> { it.occurrences }.thenBy { it.merchantKey })
    }

    /**
     * Ожидаемое следующее списание после [last].
     *
     * @param anchorDay день месяца, на который «привязана» подписка (для MONTHLY/YEARLY);
     *   если в месяце меньше дней — переносится на последний день месяца.
     */
    fun nextCharge(last: Instant, period: SubscriptionPeriod, anchorDay: Int, zone: ZoneId): Instant {
        val local = last.atZone(zone)
        val next: ZonedDateTime = when (period) {
            SubscriptionPeriod.WEEKLY -> local.plusWeeks(1)
            SubscriptionPeriod.MONTHLY -> atAnchor(YearMonth.from(local).plusMonths(1), anchorDay, local, zone)
            SubscriptionPeriod.YEARLY -> atAnchor(YearMonth.from(local).plusYears(1), anchorDay, local, zone)
        }
        return next.toInstant()
    }

    private fun atAnchor(month: YearMonth, anchorDay: Int, time: ZonedDateTime, zone: ZoneId): ZonedDateTime {
        val day = anchorDay.coerceIn(1, month.lengthOfMonth())
        return ZonedDateTime.of(month.atDay(day), time.toLocalTime(), zone)
    }

    /** Группирует операции по близким суммам; в каждой группе все суммы в пределах ±10% от медианы. */
    private fun amountClusters(txs: List<Transaction>): List<List<Transaction>> {
        val sorted = txs.sortedBy { it.amountMinor }
        val clusters = mutableListOf<MutableList<Transaction>>()
        for (tx in sorted) {
            val current = clusters.lastOrNull()
            // (100 + 10) / (100 - 10) ≈ 1.222: шире этого в одной серии быть не может.
            if (current != null && tx.amountMinor * 90 <= current.first().amountMinor * 110) {
                current += tx
            } else {
                clusters += mutableListOf(tx)
            }
        }
        return clusters.map { cluster ->
            val median = median(cluster.map { it.amountMinor })
            cluster.filter { withinTolerance(it.amountMinor, median) }
        }.filter { it.isNotEmpty() }
    }

    /** Лучшая серия в группе: самая длинная по всем периодам; при равенстве — более короткий период. */
    private fun bestSeries(cluster: List<Transaction>): Pair<SubscriptionPeriod, List<Transaction>>? {
        val byTime = cluster.sortedWith(compareBy<Transaction> { it.occurredAt }.thenBy { it.id })
        return SubscriptionPeriod.entries
            .map { it to longestChain(byTime, it) }
            .filter { (period, chain) -> chain.size >= minOccurrences(period) }
            .maxWithOrNull(compareBy<Pair<SubscriptionPeriod, List<Transaction>>> { it.second.size }
                .thenByDescending { it.first.approxDays })
            ?.let { (period, chain) ->
                // Внутри серии суммы ещё раз сверяются с её собственной медианой.
                val median = median(chain.map { it.amountMinor })
                if (chain.all { withinTolerance(it.amountMinor, median) }) period to chain else null
            }
    }

    /** Самая длинная цепочка (с пропусками посторонних операций), где каждый интервал в допуске. */
    private fun longestChain(byTime: List<Transaction>, period: SubscriptionPeriod): List<Transaction> {
        if (byTime.isEmpty()) return emptyList()
        val n = byTime.size
        val length = IntArray(n) { 1 }
        val prev = IntArray(n) { -1 }
        val tol = toleranceDays(period).toDouble()
        for (i in 1 until n) {
            for (j in 0 until i) {
                val days = (byTime[i].occurredAt.epochSecond - byTime[j].occurredAt.epochSecond) / SECONDS_PER_DAY
                if (kotlin.math.abs(days - period.approxDays) <= tol && length[j] + 1 >= length[i]) {
                    length[i] = length[j] + 1
                    prev[i] = j
                }
            }
        }
        var end = 0
        for (i in 1 until n) if (length[i] >= length[end]) end = i
        val chain = ArrayList<Transaction>(length[end])
        var cursor = end
        while (cursor >= 0) {
            chain += byTime[cursor]
            cursor = prev[cursor]
        }
        chain.reverse()
        return chain
    }

    private fun buildCandidate(
        merchantKey: String,
        currency: String,
        period: SubscriptionPeriod,
        chain: List<Transaction>,
        now: Instant,
        zone: ZoneId,
    ): SubscriptionCandidate {
        val last = chain.last()
        val days = chain.map { it.occurredAt.atZone(zone).dayOfMonth }
        val anchorDay = days.groupingBy { it }.eachCount()
            .entries.maxWith(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { it.key }).key
        val next = nextCharge(last.occurredAt, period, anchorDay, zone)
        val graceEnd = next.plusSeconds(toleranceDays(period) * SECONDS_PER_DAY.toLong())
        val category = chain.mapNotNull { it.categoryId }.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key
        return SubscriptionCandidate(
            merchant = last.displayMerchant ?: merchantKey,
            merchantKey = merchantKey,
            currency = currency,
            typicalAmountMinor = median(chain.map { it.amountMinor }),
            lastAmountMinor = last.amountMinor,
            period = period,
            lastChargeAt = last.occurredAt,
            nextChargeAt = next,
            occurrences = chain.size,
            transactionIds = chain.map { it.id },
            categoryId = category,
            isActive = !now.isAfter(graceEnd),
        )
    }

    private fun withinTolerance(amount: Long, median: Long): Boolean =
        Math.abs(amount - median) * 100 <= median * AMOUNT_TOLERANCE_PERCENT

    /** Медиана; для чётного числа — среднее двух средних, округлённое вниз. */
    private fun median(values: List<Long>): Long {
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }

    private const val SECONDS_PER_DAY = 86_400.0
}

/** Пересчёт стоимости подписок «в месяц». */
object SubscriptionCosts {

    /**
     * Эквивалент суммы в месяц, в минорных единицах (HALF_UP):
     * неделя × 52 / 12, месяц как есть, год / 12.
     */
    fun monthlyEquivalent(amountMinor: Long, period: SubscriptionPeriod): Long = when (period) {
        SubscriptionPeriod.MONTHLY -> amountMinor
        SubscriptionPeriod.WEEKLY -> divideHalfUp(amountMinor * 52, 12)
        SubscriptionPeriod.YEARLY -> divideHalfUp(amountMinor, 12)
    }

    /** Эквивалент подписки в месяц в её валюте. */
    fun monthlyEquivalent(subscription: Subscription): Long =
        monthlyEquivalent(subscription.amountMinor, subscription.period)

    /** Эквивалент кандидата в месяц (по типичной сумме). */
    fun monthlyEquivalent(candidate: SubscriptionCandidate): Long =
        monthlyEquivalent(candidate.typicalAmountMinor, candidate.period)

    /** Сумма «в месяц» по каждой валюте отдельно. */
    fun monthlyTotals(subscriptions: List<Subscription>): Map<String, Long> =
        subscriptions.groupBy { it.currency }
            .mapValues { (_, subs) -> subs.sumOf { monthlyEquivalent(it) } }

    private fun divideHalfUp(value: Long, divisor: Long): Long =
        BigDecimal.valueOf(value).divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP).toLong()
}
