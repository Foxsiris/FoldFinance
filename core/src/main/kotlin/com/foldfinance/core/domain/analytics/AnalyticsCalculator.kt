package com.foldfinance.core.domain.analytics

import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Расчёты аналитики (этап 7). Чистые функции над списком операций из БД.
 *
 * Правила:
 * - каждая валюта считается отдельно, без курсов;
 * - нетто-расходы = EXPENSE − REFUND; возврат уменьшает расходы своей категории и не доход;
 * - INTERNAL_TRANSFER исключён из всех сумм;
 * - операции с `needsReview` учитываются (их число отдаётся отдельно);
 * - разбитая по чеку операция ([Transaction.splits]) распределяется по категориям частей, остаток —
 *   в категорию самой операции; итоги, дни и число операций от разбиения не меняются.
 */
object AnalyticsCalculator {

    /**
     * Итоги за [period].
     *
     * @param transactions операции (могут выходить за период — лишние отбрасываются).
     * @param period период с часовым поясом, в котором считаются дни.
     * @param now текущий момент: если он внутри периода, среднее в день делится на прошедшие
     *   дни (включая сегодня), а не на все дни периода. `null` — делить на все дни.
     */
    fun summarize(transactions: List<Transaction>, period: Period, now: Instant? = null): PeriodSummary {
        val inPeriod = transactions.filter { it.occurredAt in period }
        val daysCounted = period.elapsedDays(now)
        val byCurrency = inPeriod
            .filter { it.direction != Direction.INTERNAL_TRANSFER }
            .groupBy { it.currency }
            .map { (currency, txs) -> summarizeCurrency(currency, txs, period, daysCounted) }
            .sortedWith(compareByDescending<CurrencySummary> { it.netExpenseMinor }.thenBy { it.currency })
            .associateByTo(LinkedHashMap()) { it.currency }
        return PeriodSummary(
            period = period,
            byCurrency = byCurrency,
            daysCounted = daysCounted,
            needsReviewCount = inPeriod.count { it.needsReview },
        )
    }

    /**
     * Сравнивает итоги текущего и прошлого периода по каждой валюте.
     * Обычно `previous = summarize(tx, period.previous())`.
     */
    fun compare(current: PeriodSummary, previous: PeriodSummary): PeriodComparison {
        val currencies = (current.byCurrency.keys + previous.byCurrency.keys).sorted()
        val result = LinkedHashMap<String, CurrencyComparison>()
        for (code in currencies) {
            val cur = current.currency(code)
            val prev = previous.currency(code)
            val curCats = cur.categories.associateBy { it.categoryId }
            val prevCats = prev.categories.associateBy { it.categoryId }
            val catIds = (curCats.keys + prevCats.keys)
                .sortedByDescending { curCats[it]?.netExpenseMinor ?: 0L }
            val categories = LinkedHashMap<Long?, AmountChange>()
            for (id in catIds) {
                categories[id] = AmountChange(curCats[id]?.netExpenseMinor ?: 0L, prevCats[id]?.netExpenseMinor ?: 0L)
            }
            result[code] = CurrencyComparison(
                currency = code,
                netExpense = AmountChange(cur.netExpenseMinor, prev.netExpenseMinor),
                income = AmountChange(cur.incomeMinor, prev.incomeMinor),
                refund = AmountChange(cur.refundMinor, prev.refundMinor),
                categories = categories,
            )
        }
        return PeriodComparison(current.period, previous.period, result)
    }

    /**
     * Сводка «Сегодня / Месяц» для внешнего экрана и виджета.
     *
     * @param primaryCurrency основная валюта пользователя (ISO 4217), остальные валюты не суммируются.
     * @param now текущий момент; «сегодня» и «месяц» определяются в [zone].
     */
    fun todayMonth(
        transactions: List<Transaction>,
        primaryCurrency: String,
        now: Instant,
        zone: ZoneId,
    ): TodayMonthSummary {
        val today = Period.containing(PeriodType.DAY, now, zone)
        val month = Period.containing(PeriodType.MONTH, now, zone)
        val counted = transactions.filter {
            it.currency == primaryCurrency && it.direction != Direction.INTERNAL_TRANSFER
        }
        return TodayMonthSummary(
            currency = primaryCurrency,
            todayNetExpenseMinor = counted.filter { it.occurredAt in today }.sumOf(::netExpenseContribution),
            monthNetExpenseMinor = counted.filter { it.occurredAt in month }.sumOf(::netExpenseContribution),
            needsReviewCount = transactions.count { it.needsReview },
        )
    }

    /** Вклад операции в нетто-расходы: +сумма для EXPENSE, −сумма для REFUND, иначе 0. */
    fun netExpenseContribution(tx: Transaction): Long = when (tx.direction) {
        Direction.EXPENSE -> tx.amountMinor
        Direction.REFUND -> -tx.amountMinor
        Direction.INCOME, Direction.INTERNAL_TRANSFER -> 0L
    }

    private fun summarizeCurrency(
        currency: String,
        txs: List<Transaction>,
        period: Period,
        daysCounted: Int,
    ): CurrencySummary {
        var expense = 0L
        var refund = 0L
        var income = 0L
        val catExpense = HashMap<Long?, Long>()
        val catRefund = HashMap<Long?, Long>()
        val dayExpense = HashMap<LocalDate, Long>()
        val dayRefund = HashMap<LocalDate, Long>()
        val dayIncome = HashMap<LocalDate, Long>()

        for (tx in txs) {
            val date = period.dateOf(tx.occurredAt)
            val amount = tx.amountMinor
            when (tx.direction) {
                Direction.EXPENSE -> {
                    expense = Math.addExact(expense, amount)
                    catExpense.addByCategory(tx)
                    dayExpense.add(date, amount)
                }
                Direction.REFUND -> {
                    refund = Math.addExact(refund, amount)
                    catRefund.addByCategory(tx)
                    dayRefund.add(date, amount)
                }
                Direction.INCOME -> {
                    income = Math.addExact(income, amount)
                    dayIncome.add(date, amount)
                }
                Direction.INTERNAL_TRANSFER -> Unit
            }
        }

        val net = expense - refund
        val categories = (catExpense.keys + catRefund.keys).map { id ->
            val e = catExpense[id] ?: 0L
            val r = catRefund[id] ?: 0L
            val n = e - r
            CategoryBreakdown(
                categoryId = id,
                expenseMinor = e,
                refundMinor = r,
                netExpenseMinor = n,
                sharePercent = if (net > 0) percentOf(n, net) else null,
            )
        }.sortedWith(
            compareByDescending<CategoryBreakdown> { it.netExpenseMinor }
                .thenBy(nullsLast<Long>()) { it.categoryId },
        )

        val daily = period.dates().map { d ->
            val e = dayExpense[d] ?: 0L
            val r = dayRefund[d] ?: 0L
            DailyPoint(date = d, expenseMinor = e, refundMinor = r, netExpenseMinor = e - r, incomeMinor = dayIncome[d] ?: 0L)
        }

        val average = if (daysCounted <= 0) 0L else
            BigDecimal.valueOf(net).divide(BigDecimal.valueOf(daysCounted.toLong()), 0, RoundingMode.HALF_UP).toLong()

        return CurrencySummary(
            currency = currency,
            expenseMinor = expense,
            refundMinor = refund,
            netExpenseMinor = net,
            incomeMinor = income,
            balanceMinor = income - net,
            operationCount = txs.size,
            categories = categories,
            averageDailyNetExpenseMinor = average,
            daily = daily,
        )
    }

    /**
     * Доли операции по категориям: части разбиения — в свои категории, остаток — в категорию
     * операции. Сумма долей всегда равна сумме операции.
     */
    fun categoryShares(tx: Transaction): List<Pair<Long?, Long>> {
        val parts = tx.splits
        if (parts.isEmpty()) return listOf(tx.categoryId to tx.amountMinor)
        var allocated = 0L
        for (p in parts) {
            // Испорченное разбиение (часть ≤ 0 или части больше операции) не учитывается.
            if (p.amountMinor <= 0) return listOf(tx.categoryId to tx.amountMinor)
            allocated = Math.addExact(allocated, p.amountMinor)
            if (allocated > tx.amountMinor) return listOf(tx.categoryId to tx.amountMinor)
        }
        val rest = tx.amountMinor - allocated
        return parts.map { it.categoryId to it.amountMinor } + listOfNotNull(rest.takeIf { it > 0 }?.let { tx.categoryId to it })
    }

    private fun MutableMap<Long?, Long>.addByCategory(tx: Transaction) {
        for ((category, amount) in categoryShares(tx)) add(category, amount)
    }

    private fun <K> MutableMap<K, Long>.add(key: K, amount: Long) {
        this[key] = Math.addExact(this[key] ?: 0L, amount)
    }
}
