package com.foldfinance.app.ai.features.voice

import com.foldfinance.app.ai.AiRequest
import com.foldfinance.app.ai.AiResponse
import com.foldfinance.app.ai.AiTier
import com.foldfinance.app.ai.LocalAi
import com.foldfinance.app.ai.features.common.AiJson
import com.foldfinance.app.ai.features.common.RussianNumbers
import com.foldfinance.app.ai.features.common.normKey
import com.foldfinance.app.ai.features.common.text
import com.foldfinance.app.ai.features.query.QueryCallParser
import com.foldfinance.core.model.Category
import com.foldfinance.parser.Direction
import java.util.Locale
import javax.inject.Inject

/**
 * A manual cash operation drafted from speech. The user always confirms it in the add form.
 *
 * @property amountMinor parsed by Kotlin from the words of the phrase; `null` — no amount heard.
 * @property uncertain the model's reading and the Kotlin parser disagree, or no amount was found:
 *   the form opens with the amount highlighted for checking (never a silent guess).
 */
data class VoiceDraft(
    val transcript: String,
    val amountMinor: Long?,
    val currency: String,
    val direction: Direction,
    val merchant: String?,
    val categoryId: Long?,
    val isCash: Boolean,
    val uncertain: Boolean,
)

/**
 * «кофе двести пятьдесят наличными» → [VoiceDraft].
 *
 * The model (FAST) only points at fragments of the phrase: where the amount is said, what was
 * bought, which of the user's categories fits. Kotlin then:
 * - checks every fragment is really in the phrase (nothing invented);
 * - turns the amount fragment into a number with [RussianNumbers] and compares it with its own
 *   scan of the whole phrase;
 * - accepts the category only from the user's list.
 * Without a usable model answer the draft is built from the Kotlin scan alone.
 */
class VoiceParser @Inject constructor(private val ai: LocalAi) {

    suspend fun parse(transcript: String, categories: List<Category>, currency: String): VoiceDraft {
        val response = ai.generate(
            AiRequest(
                prompt = transcript.take(MAX_TRANSCRIPT),
                system = systemPrompt(categories),
                tier = AiTier.FAST,
                temperature = 0f,
                maxOutputTokens = 128,
                jsonOnly = true,
            ),
        )
        return build(transcript, (response as? AiResponse.Text)?.text, categories, currency)
    }

    companion object {
        private const val MAX_TRANSCRIPT = 200

        private val cashWords = listOf("налич", "налом", "кэш", "кеш", "cash")
        private val cardWords = listOf("картой", "по карте", "карта")
        private val incomeWords = listOf("получил", "получила", "заработал", "заработала", "зарплат", "мне вернули", "мне перевели", "доход")
        private val fillerWords = setOf(
            "наличными", "наличкой", "наличные", "налом", "кэшем", "кешем", "рублей", "рубля", "рубль", "руб", "р",
            "копеек", "копейки", "за", "на", "по", "потратил", "потратила", "заплатил", "заплатила", "купил", "купила",
            "картой", "карте", "карта", "кэш", "кеш", "наличка",
        )

        fun systemPrompt(categories: List<Category>): String = buildString {
            appendLine("Разбери короткую фразу о покупке. Не вычисляй и не переписывай сумму своими словами.")
            appendLine("Ответ — только JSON: {\"amount_text\": \"...\", \"what\": \"...\", \"category\": \"...\"}.")
            appendLine("amount_text — дословный кусок фразы, где названа сумма, или null.")
            appendLine("what — на что потрачено, 1–3 слова дословно из фразы, или null.")
            append("category — одно из точных названий: ${categories.joinToString(", ") { "\"${it.name}\"" }}; если не ясно — null.")
        }

        /** Pure part of [parse]: validates [modelText] (may be `null`) against [transcript]. */
        fun build(transcript: String, modelText: String?, categories: List<Category>, currency: String): VoiceDraft {
            val phrase = normKey(transcript)
            val json = modelText?.let(AiJson::parseObject)

            val kotlinAmounts = RussianNumbers.findAmounts(transcript)
            val modelFragment = json?.text("amount_text")?.takeIf { normKey(it).isNotEmpty() && phrase.contains(normKey(it)) }
            val modelAmount = modelFragment?.let { RussianNumbers.parseAmountMinor(it) }

            val amount: Long?
            val agreed: Boolean
            when {
                // Several numbers in the phrase («два кофе по сто пятьдесят»): the total is not obvious — ask.
                modelAmount != null && kotlinAmounts.any { it.minor == modelAmount } -> { amount = modelAmount; agreed = kotlinAmounts.size == 1 }
                modelAmount == null && kotlinAmounts.size == 1 -> { amount = kotlinAmounts.single().minor; agreed = json == null || json.text("amount_text") == null }
                modelAmount != null -> { amount = modelAmount; agreed = false }
                kotlinAmounts.isNotEmpty() -> { amount = kotlinAmounts.first().minor; agreed = false }
                else -> { amount = null; agreed = false }
            }

            val what = json?.text("what")?.takeIf { phrase.contains(normKey(it)) }?.trim()
                ?: fallbackMerchant(transcript, kotlinAmounts)
            val category = json?.text("category")?.let { QueryCallParser.findCategory(it, categories) }

            val lower = transcript.lowercase(Locale.ROOT)
            val isCash = cardWords.none { it in lower } || cashWords.any { it in lower }
            val direction = if (incomeWords.any { it in lower }) Direction.INCOME else Direction.EXPENSE

            return VoiceDraft(
                transcript = transcript.trim(),
                amountMinor = amount,
                currency = currency,
                direction = direction,
                merchant = what?.replaceFirstChar { it.titlecase(Locale.forLanguageTag("ru")) },
                categoryId = category?.id,
                isCash = isCash,
                uncertain = !agreed || amount == null,
            )
        }

        /** The phrase without the amount and filler words: «кофе двести пятьдесят наличными» → «кофе». */
        private fun fallbackMerchant(transcript: String, amounts: List<RussianNumbers.AmountSpan>): String? {
            var text = transcript
            for (span in amounts.sortedByDescending { it.start }) {
                text = text.removeRange(span.start, span.end)
            }
            val words = text.split(Regex("[^\\p{L}\\d-]+"))
                .filter { it.isNotBlank() && it.lowercase(Locale.ROOT) !in fillerWords }
            return words.take(3).joinToString(" ").takeIf { it.isNotBlank() }
        }
    }
}
