package com.foldfinance.app.ai.features.receipt

import com.foldfinance.core.data.repository.TransactionRepository
import com.foldfinance.core.model.Transaction
import javax.inject.Inject

/**
 * Part of a split operation: [amountMinor] of the parent goes to [categoryId].
 * @property labels receipt lines that make up the part (for the note / detail).
 */
data class SplitPart(val categoryId: Long?, val amountMinor: Long, val labels: List<String>)

/**
 * A split of one operation by receipt lines, computed by [ReceiptSplitPlanner].
 *
 * @property itemsTotalMinor sum of the edited receipt lines.
 * @property remainderMinor parent amount − [itemsTotalMinor]; ≥ 0 stays in the parent's own
 *   category (discounts, bags, unread lines), < 0 means the lines exceed the operation.
 */
data class SplitPlan(
    val parent: Transaction,
    val parts: List<SplitPart>,
    val itemsTotalMinor: Long,
    val remainderMinor: Long,
) {
    /** Can be saved: there is something to split and the lines do not exceed the operation. */
    val isValid: Boolean get() = parts.isNotEmpty() && remainderMinor >= 0
}

/** Pure arithmetic of the split; all sums in Kotlin, exact. */
object ReceiptSplitPlanner {

    /** A line of the review screen: amount `null` = not entered yet (ignored until it is). */
    data class Line(val name: String, val amountMinor: Long?, val categoryId: Long?)

    fun plan(parent: Transaction, lines: List<Line>): SplitPlan {
        val counted = lines.filter { (it.amountMinor ?: 0L) > 0L }
        val total = counted.fold(0L) { acc, l -> Math.addExact(acc, l.amountMinor!!) }
        val parts = counted.groupBy { it.categoryId }
            .map { (category, group) -> SplitPart(category, group.fold(0L) { a, l -> Math.addExact(a, l.amountMinor!!) }, group.map { it.name }) }
            .sortedByDescending { it.amountMinor }
        return SplitPlan(parent, parts, total, parent.amountMinor - total)
    }
}

/** How a [ReceiptSplitStore] keeps a split. */
enum class SplitMode {
    /** Real split: analytics counts each part in its category (needs the core API, see [ReceiptSplitStore]). */
    CATEGORY_SPLIT,

    /** Interim: the receipt breakdown is written into the operation's note; amounts and analytics untouched. */
    NOTE,
}

/**
 * Where a confirmed receipt split goes.
 *
 * Proper model (needs `:core`, not changed here): table
 * `transaction_split(id, parent_id → transactions.id ON DELETE CASCADE, category_id, amount_minor, label, created_at)`
 * with `TransactionSplitRepository.replace(parentId, parts)` (validates `Σ parts ≤ parent.amount`)
 * and `AnalyticsCalculator` distributing a split parent over its parts' categories (the remainder
 * stays in the parent's category). The parent operation itself is not changed, so SMS/push
 * cross-source dedup (same amount ± 3 min) and reparse keep working. Wiring it is one line in
 * `AiFeaturesModule`: bind a `CoreReceiptSplitStore` instead of [NoteReceiptSplitStore].
 *
 * Child manual operations via `addManual` were rejected: either the parent amount must shrink
 * (breaks dedup with a late push of the bank amount) or the money is counted twice.
 */
interface ReceiptSplitStore {
    val mode: SplitMode

    /** Saves [plan]; [noteText] is the breakdown already formatted by the UI. */
    suspend fun save(plan: SplitPlan, noteText: String)
}

/** Interim store: appends the receipt breakdown to the operation's note (a manual edit). */
class NoteReceiptSplitStore @Inject constructor(
    private val transactions: TransactionRepository,
) : ReceiptSplitStore {
    override val mode: SplitMode = SplitMode.NOTE

    override suspend fun save(plan: SplitPlan, noteText: String) {
        require(plan.isValid) { "invalid split" }
        val existing = plan.parent.note?.substringBefore(NOTE_MARKER)?.trimEnd()
        val note = listOfNotNull(existing?.takeIf { it.isNotEmpty() }, NOTE_MARKER + noteText).joinToString("\n")
        transactions.update(plan.parent.copy(note = note.take(MAX_NOTE)))
    }

    companion object {
        /** Start of the receipt block in a note; a new receipt replaces the old block. */
        const val NOTE_MARKER: String = "Чек: "
        private const val MAX_NOTE = 1_000
    }
}
