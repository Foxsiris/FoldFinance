package com.foldfinance.app.ai.features.receipt

import com.foldfinance.app.ai.features.common.AiJson
import com.foldfinance.app.ai.features.common.array
import com.foldfinance.app.ai.features.common.text
import com.foldfinance.app.ai.features.query.QueryCallParser
import com.foldfinance.core.model.Category
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal

/**
 * One line of a receipt as the model read it and the app validated it.
 *
 * @property amountMinor line sum as printed (the model copies it, never multiplies); `null` when
 *   it was unreadable — the user types it on the review screen.
 * @property categoryId one of the user's categories, or `null` if the model named none or an
 *   unknown one ([categoryUncertain] then tells the review screen to ask).
 */
data class ReceiptLine(
    val name: String,
    val amountMinor: Long?,
    val categoryId: Long?,
    val categoryUncertain: Boolean,
)

/** Lines of a receipt plus the printed total («ИТОГО»), if the model could read it. */
data class ReceiptScan(val lines: List<ReceiptLine>, val printedTotalMinor: Long?)

/**
 * Validation of the model's receipt JSON:
 * `{"items":[{"name":"Молоко","amount":"89.90","category":"Продукты"}],"total":"1240.50"}`.
 *
 * Amounts are copied decimal strings, converted exactly (no floating point); a value with more
 * fraction digits than the currency allows, a non-positive or absurd value becomes `null` (the
 * user enters it), never a guess. Categories must match the user's list exactly.
 */
object ReceiptParser {

    const val MAX_LINES: Int = 80
    private const val MAX_NAME: Int = 80
    private const val MAX_MINOR: Long = 100_000_000_00L

    fun parse(modelText: String, categories: List<Category>, fractionDigits: Int = 2): ReceiptScan? {
        val root = AiJson.parseObject(modelText) ?: return null
        return parse(root, categories, fractionDigits)
    }

    fun parse(root: JsonObject, categories: List<Category>, fractionDigits: Int = 2): ReceiptScan? {
        val items = root.array("items") ?: return null
        val lines = items.asSequence()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { item ->
                val name = item.text("name")?.replace(Regex("\\s+"), " ")?.take(MAX_NAME) ?: return@mapNotNull null
                val amount = item.text("amount")?.let { amountMinor(it, fractionDigits) }
                val categoryName = item.text("category")
                val category = categoryName?.let { QueryCallParser.findCategory(it, categories) }
                ReceiptLine(name, amount, category?.id, categoryUncertain = category == null)
            }
            .take(MAX_LINES)
            .toList()
        if (lines.isEmpty()) return null
        return ReceiptScan(lines, root.text("total")?.let { amountMinor(it, fractionDigits) })
    }

    /** `"1 240,50 ₽"` / `"1240.5"` → `124050`; `null` if not an exact positive amount. */
    fun amountMinor(raw: String, fractionDigits: Int = 2): Long? {
        val cleaned = raw.replace(Regex("[\\s  ₽]|руб\\.?|р\\.?$", RegexOption.IGNORE_CASE), "").replace(',', '.')
        if (!cleaned.matches(Regex("\\d+(\\.\\d+)?"))) return null
        val value = BigDecimal(cleaned)
        if (value.stripTrailingZeros().scale() > fractionDigits) return null
        val minor = value.movePointRight(fractionDigits)
        return runCatching { minor.toBigIntegerExact().toLong() }.getOrNull()?.takeIf { it in 1..MAX_MINOR }
    }
}
