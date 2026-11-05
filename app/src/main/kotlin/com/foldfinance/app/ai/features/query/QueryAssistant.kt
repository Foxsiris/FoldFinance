package com.foldfinance.app.ai.features.query

import com.foldfinance.app.ai.AiRequest
import com.foldfinance.app.ai.AiResponse
import com.foldfinance.app.ai.AiTier
import com.foldfinance.app.ai.LocalAi
import com.foldfinance.core.model.Category
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Plain-language questions (п.4): the model (FULL) turns a question into one JSON function call,
 * [QueryCallParser] validates it, [QueryEngine] computes the answer. The model never sees a
 * single amount: the prompt holds only the function list, category names and today's date.
 */
class QueryAssistant @Inject constructor(private val ai: LocalAi) {

    /** Model call → validated [QueryCall], or the reason it was rejected. */
    suspend fun interpret(
        question: String,
        categories: List<Category>,
        today: LocalDate,
        zone: ZoneId,
        scope: ChatScope = ChatScope(),
    ): ParsedCall {
        val request = AiRequest(
            prompt = question.trim().take(MAX_QUESTION),
            system = systemPrompt(categories, today, scope),
            tier = AiTier.FULL,
            temperature = 0f,
            maxOutputTokens = 200,
            jsonOnly = true,
        )
        return when (val response = ai.generate(request)) {
            is AiResponse.Failed -> ParsedCall.Rejected(QueryRejection.MODEL_FAILED, response.reason)
            is AiResponse.Text -> QueryCallParser.parse(response.text, categories, today, zone, scope)
        }
    }

    companion object {
        const val MAX_QUESTION: Int = 300

        /** Function description for the model. Public for tests and prompt review. */
        fun systemPrompt(categories: List<Category>, today: LocalDate, scope: ChatScope): String = buildString {
            appendLine("Ты превращаешь вопрос пользователя о его расходах в вызов ОДНОЙ функции. Ничего не считай сам.")
            appendLine("Ответ — только JSON: {\"fn\": \"...\", \"args\": {...}}.")
            appendLine("Функции:")
            appendLine("- spend(category?, merchant?, period, group_by?) — сколько потрачено. group_by: none|category|merchant|day|month.")
            appendLine("- compare(period, previous?, group_by) — что выросло/упало относительно прошлого периода. group_by: category|merchant.")
            appendLine("- top(period, n, by) — самые большие траты. by: category|merchant|operation; n от 1 до 20.")
            appendLine("- list(category?, merchant?, period) — показать сами операции.")
            appendLine("- none — если это не вопрос о деньгах пользователя.")
            appendLine("category — ТОЛЬКО одно из точных названий: ${categories.joinToString(", ") { "\"${it.name}\"" }}.")
            appendLine("Если места/магазина/услуги нет среди категорий (например, такси, кофейня, Ozon), передай это в merchant, а category не указывай.")
            appendLine("period — одна из строк: ${PeriodSpec.examples.joinToString(", ")}.")
            appendLine("Сегодня ${today}. «В октябре» без года — month:MM.")
            if (scope.period != null) {
                appendLine("Если период в вопросе не назван, не указывай period: возьмётся выбранный на экране.")
            }
            append("Пример: «сколько ушло на такси в октябре?» → {\"fn\":\"spend\",\"args\":{\"merchant\":\"такси\",\"period\":\"month:10\"}}")
        }
    }
}
