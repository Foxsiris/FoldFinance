package com.foldfinance.app.ai.features.query

import com.foldfinance.app.ai.features.common.normKey
import com.foldfinance.core.domain.analytics.AnalyticsCalculator
import com.foldfinance.core.domain.analytics.AmountChange
import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.domain.category.MerchantNormalizer
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import java.time.LocalDate
import java.time.YearMonth

/** What a row of an answer is about. */
sealed interface RowLabel {
    data class OfCategory(val category: Category) : RowLabel
    data object Uncategorized : RowLabel
    data class OfMerchant(val name: String) : RowLabel
    data object NoMerchant : RowLabel
    data class OfDay(val date: LocalDate) : RowLabel
    data class OfMonth(val month: YearMonth) : RowLabel
    data class OfOperation(val transaction: Transaction) : RowLabel
}

/**
 * One line of an answer. Amounts are minor units computed by [QueryEngine].
 * @property change set for comparisons: current vs previous.
 */
data class AnswerRow(
    val label: RowLabel,
    val currency: String,
    val amountMinor: Long,
    val change: AmountChange? = null,
)

/** Total of the question in one currency. */
data class AnswerTotal(val currency: String, val amountMinor: Long, val change: AmountChange? = null)

/**
 * A complete answer, computed only by Kotlin from the database. The UI turns it into text with
 * string resources; no number in it comes from the model.
 *
 * @property totals per currency; the user's main currency first.
 * @property operations matching operations, newest first, at most [QueryEngine.MAX_OPERATIONS].
 * @property operationCount all matching operations (may be more than [operations]).
 */
data class QueryAnswer(
    val call: QueryCall,
    val totals: List<AnswerTotal>,
    val rows: List<AnswerRow>,
    val operations: List<Transaction>,
    val operationCount: Int,
) {
    val isEmpty: Boolean get() = operationCount == 0 && rows.isEmpty() && totals.all { it.amountMinor == 0L && (it.change?.previousMinor ?: 0L) == 0L }
}

/**
 * Executes a validated [QueryCall] over operations from the database. Money rules are those of
 * [AnalyticsCalculator]: net expenses = EXPENSE − REFUND, internal transfers excluded, currencies
 * never mixed.
 */
object QueryEngine {

    const val MAX_OPERATIONS: Int = 50
    private const val MAX_ROWS: Int = 12

    /**
     * @param transactions operations covering the call's periods (more is fine — filtered here).
     * @param primaryCurrency the user's main currency: listed first.
     */
    fun answer(
        call: QueryCall,
        transactions: List<Transaction>,
        categories: List<Category>,
        primaryCurrency: String,
    ): QueryAnswer = when (call) {
        is QueryCall.Spend -> spend(call, transactions, categories, primaryCurrency)
        is QueryCall.Compare -> compare(call, transactions, categories, primaryCurrency)
        is QueryCall.Top -> top(call, transactions, categories, primaryCurrency)
        is QueryCall.ListOps -> list(call, transactions, primaryCurrency)
    }

    /** Operation passes the category/merchant filter (the raw name from the bank or the display name). */
    fun matches(tx: Transaction, category: Category?, merchant: String?): Boolean {
        if (category != null && tx.categoryId != category.id) return false
        if (merchant != null) {
            val names = listOfNotNull(tx.merchant, tx.merchantDisplayName)
            if (names.isEmpty()) return false
            val plain = normKey(merchant)
            val canon = MerchantNormalizer.canonical(merchant)
            val hit = names.any { name ->
                normKey(name).contains(plain) || (canon.isNotEmpty() && MerchantNormalizer.canonical(name).contains(canon))
            }
            if (!hit) return false
        }
        return true
    }

    private fun inPeriod(txs: List<Transaction>, period: Period) = txs.filter { it.occurredAt in period }

    private fun spending(txs: List<Transaction>) = txs.filter { it.direction == Direction.EXPENSE || it.direction == Direction.REFUND }

    private fun currencies(txs: List<Transaction>, primary: String): List<String> =
        txs.map { it.currency }.distinct().sortedWith(compareBy<String> { it != primary }.thenBy { it })

    private fun spend(call: QueryCall.Spend, all: List<Transaction>, categories: List<Category>, primary: String): QueryAnswer {
        val filtered = all.filter { matches(it, call.category, call.merchant) }
        val ops = spending(inPeriod(filtered, call.period)).sortedByDescending { it.occurredAt }
        val summary = AnalyticsCalculator.summarize(filtered, call.period)
        val codes = currencies(ops, primary).ifEmpty { listOf(primary) }
        val totals = codes.map { AnswerTotal(it, summary.currency(it).netExpenseMinor) }
        val rows = when (call.groupBy) {
            GroupBy.NONE -> emptyList()
            GroupBy.CATEGORY -> codes.flatMap { code ->
                summary.currency(code).categories.filter { it.netExpenseMinor != 0L }
                    .map { AnswerRow(categoryLabel(it.categoryId, categories), code, it.netExpenseMinor) }
            }
            GroupBy.MERCHANT -> merchantRows(ops, codes)
            GroupBy.DAY -> codes.flatMap { code ->
                summary.currency(code).daily.filter { it.netExpenseMinor != 0L }
                    .map { AnswerRow(RowLabel.OfDay(it.date), code, it.netExpenseMinor) }
            }
            GroupBy.MONTH -> codes.flatMap { code ->
                ops.filter { it.currency == code }
                    .groupBy { YearMonth.from(call.period.dateOf(it.occurredAt)) }
                    .toSortedMap()
                    .map { (month, txs) -> AnswerRow(RowLabel.OfMonth(month), code, txs.sumOf(AnalyticsCalculator::netExpenseContribution)) }
            }
        }.take(MAX_ROWS * codes.size)
        return QueryAnswer(call, totals, rows, ops.take(MAX_OPERATIONS), ops.size)
    }

    private fun compare(call: QueryCall.Compare, all: List<Transaction>, categories: List<Category>, primary: String): QueryAnswer {
        val filtered = all.filter { matches(it, call.category, null) }
        val cur = AnalyticsCalculator.summarize(filtered, call.period)
        val prev = AnalyticsCalculator.summarize(filtered, call.previous)
        val cmp = AnalyticsCalculator.compare(cur, prev)
        val codes = cmp.byCurrency.keys.sortedWith(compareBy<String> { it != primary }.thenBy { it }).ifEmpty { listOf(primary) }
        val totals = codes.map { code ->
            val c = cmp.byCurrency[code]
            AnswerTotal(code, c?.netExpense?.currentMinor ?: 0L, c?.netExpense ?: AmountChange(0, 0))
        }
        val rows = when (call.groupBy) {
            GroupBy.MERCHANT -> codes.flatMap { code ->
                val now = merchantSums(spending(inPeriod(filtered, call.period)).filter { it.currency == code })
                val before = merchantSums(spending(inPeriod(filtered, call.previous)).filter { it.currency == code })
                (now.keys + before.keys).map { key ->
                    val change = AmountChange(now[key]?.second ?: 0L, before[key]?.second ?: 0L)
                    val name = now[key]?.first ?: before[key]?.first
                    AnswerRow(if (name == null) RowLabel.NoMerchant else RowLabel.OfMerchant(name), code, change.currentMinor, change)
                }.filter { it.change!!.deltaMinor != 0L }.sortedByDescending { it.change!!.deltaMinor }.take(MAX_ROWS)
            }
            else -> codes.flatMap { code ->
                cmp.byCurrency[code]?.categories.orEmpty()
                    .filter { (_, change) -> change.deltaMinor != 0L }
                    .map { (id, change) -> AnswerRow(categoryLabel(id, categories), code, change.currentMinor, change) }
                    .sortedByDescending { it.change!!.deltaMinor }
                    .take(MAX_ROWS)
            }
        }
        val ops = spending(inPeriod(filtered, call.period))
        return QueryAnswer(call, totals, rows, emptyList(), ops.size)
    }

    private fun top(call: QueryCall.Top, all: List<Transaction>, categories: List<Category>, primary: String): QueryAnswer {
        val ops = spending(inPeriod(all, call.period))
        val summary = AnalyticsCalculator.summarize(all, call.period)
        val codes = currencies(ops, primary).ifEmpty { listOf(primary) }
        val totals = codes.map { AnswerTotal(it, summary.currency(it).netExpenseMinor) }
        val rows = when (call.by) {
            TopBy.CATEGORY -> codes.flatMap { code ->
                summary.currency(code).topCategories(call.n).map { AnswerRow(categoryLabel(it.categoryId, categories), code, it.netExpenseMinor) }
            }
            TopBy.MERCHANT -> codes.flatMap { code ->
                merchantRows(ops.filter { it.currency == code }, listOf(code)).filter { it.amountMinor > 0 }.take(call.n)
            }
            TopBy.OPERATION -> ops.filter { it.direction == Direction.EXPENSE }
                .sortedWith(compareByDescending<Transaction> { it.amountMinor }.thenByDescending { it.occurredAt })
                .take(call.n)
                .map { AnswerRow(RowLabel.OfOperation(it), it.currency, it.amountMinor) }
        }
        return QueryAnswer(call, totals, rows, emptyList(), ops.size)
    }

    private fun list(call: QueryCall.ListOps, all: List<Transaction>, primary: String): QueryAnswer {
        val ops = inPeriod(all, call.period)
            .filter { it.direction != Direction.INTERNAL_TRANSFER && matches(it, call.category, call.merchant) }
            .sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenByDescending { it.id })
        val codes = currencies(ops, primary).ifEmpty { listOf(primary) }
        val totals = codes.map { code -> AnswerTotal(code, ops.filter { it.currency == code }.sumOf(AnalyticsCalculator::netExpenseContribution)) }
        return QueryAnswer(call, totals, emptyList(), ops.take(MAX_OPERATIONS), ops.size)
    }

    private fun categoryLabel(id: Long?, categories: List<Category>): RowLabel =
        id?.let { cid -> categories.firstOrNull { it.id == cid } }?.let { RowLabel.OfCategory(it) } ?: RowLabel.Uncategorized

    /** canonical merchant key → (display name of the latest operation, net sum). */
    private fun merchantSums(txs: List<Transaction>): Map<String, Pair<String?, Long>> =
        txs.groupBy { MerchantNormalizer.canonical(it.merchant) }
            .mapValues { (_, list) ->
                list.maxBy { it.occurredAt }.displayMerchant to list.sumOf(AnalyticsCalculator::netExpenseContribution)
            }

    private fun merchantRows(ops: List<Transaction>, codes: List<String>): List<AnswerRow> = codes.flatMap { code ->
        merchantSums(ops.filter { it.currency == code })
            .map { (_, v) -> AnswerRow(v.first?.let { RowLabel.OfMerchant(it) } ?: RowLabel.NoMerchant, code, v.second) }
            .filter { it.amountMinor != 0L }
            .sortedByDescending { it.amountMinor }
            .take(MAX_ROWS)
    }
}
