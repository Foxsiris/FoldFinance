package com.foldfinance.app.ai.features.query

import com.foldfinance.app.ai.features.common.AiJson
import com.foldfinance.app.ai.features.common.normKey
import com.foldfinance.app.ai.features.common.obj
import com.foldfinance.app.ai.features.common.text
import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.model.Category
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Context of a chat: the period and category the user is looking at. Used as defaults when the
 * question does not name them, and always shown in the answer's interpretation chips.
 */
data class ChatScope(val period: Period? = null, val categoryId: Long? = null)

/** Why a model answer was not turned into a [QueryCall]. */
enum class QueryRejection {
    NO_JSON, NOT_A_QUESTION, UNKNOWN_FUNCTION, UNKNOWN_CATEGORY, BAD_PERIOD, BAD_ARGUMENT, MODEL_FAILED,
}

sealed interface ParsedCall {
    data class Ok(val call: QueryCall) : ParsedCall
    data class Rejected(val reason: QueryRejection, val detail: String? = null) : ParsedCall
}

/**
 * Strict validation of the model's function call:
 * `{"fn": "spend|compare|top|list|none", "args": {…}}`.
 *
 * - unknown function → rejected;
 * - `category` must be one of the user's categories (by name or key, case and `ё` ignored) —
 *   anything else is rejected, never mapped to «something close»;
 * - `period` goes through [PeriodSpec]; a missing period falls back to the chat scope, then to
 *   the current month;
 * - `merchant` is free text (≤ 60 chars), matched as a substring in Kotlin;
 * - `n` in 1…20, `group_by`/`by` from fixed lists.
 */
object QueryCallParser {

    private const val MAX_MERCHANT = 60

    fun parse(
        modelText: String,
        categories: List<Category>,
        today: LocalDate,
        zone: ZoneId,
        scope: ChatScope = ChatScope(),
    ): ParsedCall {
        val root = AiJson.parseObject(modelText) ?: return ParsedCall.Rejected(QueryRejection.NO_JSON)
        return parse(root, categories, today, zone, scope)
    }

    fun parse(
        root: JsonObject,
        categories: List<Category>,
        today: LocalDate,
        zone: ZoneId,
        scope: ChatScope = ChatScope(),
    ): ParsedCall {
        val fn = (root.text("fn") ?: root.text("function") ?: root.text("name"))?.lowercase(Locale.ROOT)
            ?: return ParsedCall.Rejected(QueryRejection.UNKNOWN_FUNCTION)
        val args = root.obj("args") ?: root.obj("arguments") ?: JsonObject(emptyMap())
        if (fn == "none" || fn == "unknown") return ParsedCall.Rejected(QueryRejection.NOT_A_QUESTION)
        if (fn !in setOf(SavedQueryCall.FN_SPEND, SavedQueryCall.FN_COMPARE, SavedQueryCall.FN_TOP, SavedQueryCall.FN_LIST)) {
            return ParsedCall.Rejected(QueryRejection.UNKNOWN_FUNCTION, fn)
        }

        val categoryName = args.text("category")
        val category = if (categoryName != null) {
            findCategory(categoryName, categories) ?: return ParsedCall.Rejected(QueryRejection.UNKNOWN_CATEGORY, categoryName)
        } else {
            null
        }
        val merchant = args.text("merchant")?.trim()
        if (merchant != null && (merchant.length > MAX_MERCHANT || merchant.isBlank())) {
            return ParsedCall.Rejected(QueryRejection.BAD_ARGUMENT, "merchant")
        }

        val periodSpec = args.text("period")
        val period = when {
            periodSpec != null -> PeriodSpec.parse(periodSpec, today, zone)
                ?: return ParsedCall.Rejected(QueryRejection.BAD_PERIOD, periodSpec)
            scope.period != null -> scope.period
            else -> Period.month(today, zone)
        }

        val groupBy = args.text("group_by") ?: args.text("groupBy")
        val group = when (groupBy?.lowercase(Locale.ROOT)) {
            null, "none" -> GroupBy.NONE
            "category" -> GroupBy.CATEGORY
            "merchant" -> GroupBy.MERCHANT
            "day" -> GroupBy.DAY
            "month" -> GroupBy.MONTH
            else -> return ParsedCall.Rejected(QueryRejection.BAD_ARGUMENT, "group_by")
        }

        // The scope's category applies only when the question names neither a category nor a merchant.
        val scopedCategory = category ?: if (merchant == null) scope.categoryId?.let { id -> categories.firstOrNull { it.id == id } } else null

        val call: QueryCall = when (fn) {
            SavedQueryCall.FN_SPEND -> QueryCall.Spend(scopedCategory, merchant, period, group)
            SavedQueryCall.FN_LIST -> QueryCall.ListOps(scopedCategory, merchant, period)
            SavedQueryCall.FN_COMPARE -> {
                if (group == GroupBy.DAY || group == GroupBy.MONTH) return ParsedCall.Rejected(QueryRejection.BAD_ARGUMENT, "group_by")
                val previousSpec = args.text("previous") ?: args.text("previous_period")
                if (previousSpec != null) {
                    val previous = PeriodSpec.parse(previousSpec, today, zone)
                        ?: return ParsedCall.Rejected(QueryRejection.BAD_PERIOD, previousSpec)
                    QueryCall.Compare(period, previous, if (group == GroupBy.NONE) GroupBy.CATEGORY else group, category)
                } else {
                    val (cur, prev) = PeriodSpec.comparisonPair(period, today)
                    QueryCall.Compare(cur, prev, if (group == GroupBy.NONE) GroupBy.CATEGORY else group, category)
                }
            }
            SavedQueryCall.FN_TOP -> {
                val n = args.text("n")?.let { it.toIntOrNull() ?: return ParsedCall.Rejected(QueryRejection.BAD_ARGUMENT, "n") } ?: 5
                if (n !in 1..20) return ParsedCall.Rejected(QueryRejection.BAD_ARGUMENT, "n")
                val by = when (args.text("by")?.lowercase(Locale.ROOT)) {
                    null, "category" -> TopBy.CATEGORY
                    "merchant" -> TopBy.MERCHANT
                    "operation", "operations", "transaction" -> TopBy.OPERATION
                    else -> return ParsedCall.Rejected(QueryRejection.BAD_ARGUMENT, "by")
                }
                QueryCall.Top(period, n, by)
            }
            else -> return ParsedCall.Rejected(QueryRejection.UNKNOWN_FUNCTION, fn)
        }
        return ParsedCall.Ok(call)
    }

    /** Exact match by name or key (case, `ё` and quotes ignored); no fuzzy guessing. */
    fun findCategory(name: String, categories: List<Category>): Category? {
        val key = normKey(name)
        return categories.firstOrNull { normKey(it.name) == key } ?: categories.firstOrNull { normKey(it.key) == key }
    }
}
