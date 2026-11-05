package com.foldfinance.app.ai.features.query

import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.domain.analytics.PeriodType
import com.foldfinance.core.model.Category
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

/** How the answer to a question is broken down. */
enum class GroupBy { NONE, CATEGORY, MERCHANT, DAY, MONTH }

/** What [QueryCall.Top] ranks. */
enum class TopBy { CATEGORY, MERCHANT, OPERATION }

/**
 * A validated call of one of the app's question functions. The model only chooses the function
 * and its arguments; [QueryEngine] computes the answer from the database in Kotlin.
 *
 * Every field here has already passed [QueryCallParser]: categories exist in the user's list,
 * periods are real [Period]s, numbers are in range.
 */
sealed interface QueryCall {
    val period: Period

    /** `spend(category?, merchant?, period, groupBy?)` — net expenses (refunds subtract). */
    data class Spend(
        val category: Category?,
        val merchant: String?,
        override val period: Period,
        val groupBy: GroupBy = GroupBy.NONE,
    ) : QueryCall

    /** `compare(period, previousPeriod, groupBy)` — what grew and what fell. */
    data class Compare(
        override val period: Period,
        val previous: Period,
        val groupBy: GroupBy = GroupBy.CATEGORY,
        val category: Category? = null,
    ) : QueryCall

    /** `top(period, n, by)` — largest categories, merchants or operations. */
    data class Top(
        override val period: Period,
        val n: Int = 5,
        val by: TopBy = TopBy.CATEGORY,
    ) : QueryCall

    /** `list(category?, merchant?, period)` — the operations themselves. */
    data class ListOps(
        val category: Category?,
        val merchant: String?,
        override val period: Period,
    ) : QueryCall
}

/**
 * [QueryCall] in a form that fits into `SavedStateHandle`: resolved dates and the category id,
 * so a restored chat recomputes the same question against the current database.
 */
@Serializable
data class SavedQueryCall(
    val fn: String,
    val categoryId: Long? = null,
    val merchant: String? = null,
    val periodType: String,
    val start: String,
    val endExclusive: String,
    val prevType: String? = null,
    val prevStart: String? = null,
    val prevEndExclusive: String? = null,
    val groupBy: String = GroupBy.NONE.name,
    val n: Int = 5,
    val by: String = TopBy.CATEGORY.name,
) {
    /** Back to a call; `null` if the category was deleted meanwhile or the data is malformed. */
    fun restore(categories: List<Category>, zone: ZoneId): QueryCall? = runCatching {
        val category = categoryId?.let { id -> categories.firstOrNull { it.id == id } ?: return null }
        val period = Period(PeriodType.valueOf(periodType), LocalDate.parse(start), LocalDate.parse(endExclusive), zone)
        val group = GroupBy.valueOf(groupBy)
        when (fn) {
            FN_SPEND -> QueryCall.Spend(category, merchant, period, group)
            FN_COMPARE -> QueryCall.Compare(
                period,
                Period(PeriodType.valueOf(prevType ?: periodType), LocalDate.parse(prevStart!!), LocalDate.parse(prevEndExclusive!!), zone),
                group,
                category,
            )
            FN_TOP -> QueryCall.Top(period, n, TopBy.valueOf(by))
            FN_LIST -> QueryCall.ListOps(category, merchant, period)
            else -> null
        }
    }.getOrNull()

    companion object {
        const val FN_SPEND = "spend"
        const val FN_COMPARE = "compare"
        const val FN_TOP = "top"
        const val FN_LIST = "list"

        fun of(call: QueryCall): SavedQueryCall {
            val p = call.period
            val base = SavedQueryCall(
                fn = "",
                periodType = p.type.name,
                start = p.startDate.toString(),
                endExclusive = p.endDateExclusive.toString(),
            )
            return when (call) {
                is QueryCall.Spend -> base.copy(fn = FN_SPEND, categoryId = call.category?.id, merchant = call.merchant, groupBy = call.groupBy.name)
                is QueryCall.Compare -> base.copy(
                    fn = FN_COMPARE,
                    categoryId = call.category?.id,
                    groupBy = call.groupBy.name,
                    prevType = call.previous.type.name,
                    prevStart = call.previous.startDate.toString(),
                    prevEndExclusive = call.previous.endDateExclusive.toString(),
                )
                is QueryCall.Top -> base.copy(fn = FN_TOP, n = call.n, by = call.by.name)
                is QueryCall.ListOps -> base.copy(fn = FN_LIST, categoryId = call.category?.id, merchant = call.merchant)
            }
        }
    }
}
