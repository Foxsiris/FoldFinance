package com.foldfinance.app.ai.features.common

import java.math.BigDecimal

/**
 * A fact computed by Kotlin and handed to the model by [id]. The model writes `{id}` where the
 * value belongs; [NumberGuard.render] substitutes [value] (already formatted by the app).
 */
data class Fact(val id: String, val value: String)

/**
 * The guard between model text and the screen («модель никогда не считает деньги»).
 *
 * The model writes prose with placeholders (`{total}`, `{cat1_delta}`) instead of numbers. Its
 * text is accepted only if:
 * - every placeholder names a known fact;
 * - every number written in digits outside placeholders occurs in the facts or in [render]'s
 *   `context` (names like «Магазин 24»);
 * - no spelled-out number ≥ 2 («в два раза», «три категории») occurs that the facts do not
 *   contain — a count or ratio the model worked out itself;
 * - the text is not empty, has no markup and is not absurdly long.
 *
 * Otherwise the caller shows its own template text built from the same facts.
 */
object NumberGuard {

    private val placeholder = Regex("""\{([A-Za-z][A-Za-z0-9_]*)\}""")
    private val digits = Regex("""\d+(?:[    ]\d{3})*(?:[.,]\d+)?""")

    /** Longest text a 3–4 sentence summary may have. */
    const val MAX_LENGTH: Int = 900

    sealed interface Result {
        data class Accepted(val text: String) : Result
        data class Rejected(val reason: String) : Result
    }

    /**
     * Checks [template] from the model and substitutes [facts].
     *
     * @param context other strings the model was shown (names of categories and merchants):
     *   numbers in them may be repeated.
     */
    fun render(template: String, facts: List<Fact>, context: List<String> = emptyList()): Result {
        val text = template.trim().removeSurrounding("\"").trim()
        if (text.isEmpty()) return Result.Rejected("empty")
        if (text.length > MAX_LENGTH) return Result.Rejected("too_long")
        if (text.contains('`') || text.contains("```") || text.contains('{') && !placeholder.containsMatchIn(text)) {
            return Result.Rejected("markup")
        }
        val byId = facts.associate { it.id to it.value }
        val unknown = placeholder.findAll(text).map { it.groupValues[1] }.filterNot { it in byId }.toList()
        if (unknown.isNotEmpty()) return Result.Rejected("unknown_placeholder:${unknown.first()}")

        val bare = placeholder.replace(text, " ")
        if (bare.contains('{') || bare.contains('}')) return Result.Rejected("markup")

        val allowed = allowedNumbers(facts.map { it.value } + context)
        val strayDigits = numbersInDigits(bare).filterNot { it in allowed }
        if (strayDigits.isNotEmpty()) return Result.Rejected("number:${strayDigits.first().toPlainString()}")
        val straySpelled = RussianNumbers.findSpelledNumbers(bare)
            .map { it.value.stripTrailingZeros() }
            .filter { it >= BigDecimal(2) && it !in allowed }
        if (straySpelled.isNotEmpty()) return Result.Rejected("spelled:${straySpelled.first().toPlainString()}")

        val rendered = placeholder.replace(text) { byId.getValue(it.groupValues[1]) }
        return Result.Accepted(rendered.replace(Regex("[ \t]+"), " ").trim())
    }

    /** Numbers in digits, normalised (`1 240,50` → `1240.5`). */
    fun numbersInDigits(text: String): List<BigDecimal> =
        digits.findAll(text).map { normalize(it.value) }.toList()

    private fun allowedNumbers(sources: List<String>): Set<BigDecimal> = buildSet {
        for (s in sources) {
            numbersInDigits(s).forEach { add(it) }
            RussianNumbers.findSpelledNumbers(s).forEach { add(it.value.stripTrailingZeros()) }
        }
    }

    private fun normalize(raw: String): BigDecimal =
        BigDecimal(raw.filterNot { it == ' ' || it == ' ' || it == ' ' || it == ' ' }.replace(',', '.'))
            .stripTrailingZeros()
}
