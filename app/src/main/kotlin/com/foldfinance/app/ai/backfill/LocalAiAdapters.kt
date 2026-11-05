package com.foldfinance.app.ai.backfill

import com.foldfinance.app.ai.AiFailureReasons
import com.foldfinance.app.ai.AiJson
import com.foldfinance.app.ai.AiJson.bool
import com.foldfinance.app.ai.AiJson.str
import com.foldfinance.app.ai.AiRequest
import com.foldfinance.app.ai.AiResponse
import com.foldfinance.app.ai.AiTier
import com.foldfinance.app.ai.LocalAi
import com.foldfinance.core.data.ai.AiMerchantAdvisor
import com.foldfinance.core.data.ai.AiMessageReader
import com.foldfinance.core.data.ai.AiOutcome
import com.foldfinance.core.data.ai.CategoryExample
import com.foldfinance.core.data.ai.MerchantAdvice
import com.foldfinance.core.model.Category
import com.foldfinance.parser.MessageMeta
import com.foldfinance.parser.ParseResult
import com.foldfinance.parser.ai.AiExtraction
import com.foldfinance.parser.ai.AiExtractionValidator
import com.foldfinance.parser.ai.AiValidation
import java.time.ZoneId
import javax.inject.Inject

/**
 * Разбор сообщения банка локальной моделью: промпт с JSON-схемой → строгий разбор JSON →
 * сверка с исходным текстом ([AiExtractionValidator]). Модель только извлекает поля; сумма
 * операции — та, что найдена в тексте.
 */
class LocalAiMessageReader internal constructor(
    private val ai: LocalAi,
    private val validator: AiExtractionValidator,
) : AiMessageReader {

    @Inject constructor(ai: LocalAi) : this(ai, AiExtractionValidator(ZoneId.systemDefault()))

    override suspend fun read(text: String, meta: MessageMeta, regexResult: ParseResult): AiOutcome<ParseResult.Parsed> {
        val response = ai.generate(
            AiRequest(
                prompt = SmsPrompt.build(text, meta),
                system = SmsPrompt.SYSTEM,
                tier = AiTier.FAST,
                temperature = 0.1f,
                maxOutputTokens = 200,
                jsonOnly = true,
            ),
        )
        val answer = when (response) {
            is AiResponse.Failed -> return failed(response.reason)
            is AiResponse.Text -> response.text
        }
        val obj = AiJson.parseObject(answer) ?: return AiOutcome.Rejected("bad_json")
        val extraction = AiExtraction(
            isOperation = obj.bool("is_operation"),
            amount = obj.str("amount"),
            currency = obj.str("currency"),
            type = obj.str("type"),
            direction = obj.str("direction"),
            merchant = obj.str("merchant"),
            cardLast4 = obj.str("card_last4"),
            balance = obj.str("balance"),
        )
        return when (val v = validator.validate(text, meta, extraction, regexResult)) {
            is AiValidation.Accepted -> AiOutcome.Done(v.result)
            is AiValidation.Rejected -> AiOutcome.Rejected(v.reason)
        }
    }
}

/** Имя и категория мерчанта от локальной модели. Проверку имени и категории делает `AiBackfillUseCase`. */
class LocalAiMerchantAdvisor @Inject constructor(private val ai: LocalAi) : AiMerchantAdvisor {

    override suspend fun advise(
        merchant: String,
        categories: List<Category>,
        examples: List<CategoryExample>,
    ): AiOutcome<MerchantAdvice> {
        val response = ai.generate(
            AiRequest(
                prompt = MerchantPrompt.build(merchant, categories, examples),
                system = MerchantPrompt.SYSTEM,
                tier = AiTier.FAST,
                temperature = 0.1f,
                maxOutputTokens = 96,
                jsonOnly = true,
            ),
        )
        val answer = when (response) {
            is AiResponse.Failed -> return failed(response.reason)
            is AiResponse.Text -> response.text
        }
        val obj = AiJson.parseObject(answer) ?: return AiOutcome.Rejected("bad_json")
        return AiOutcome.Done(MerchantAdvice(displayName = obj.str("name"), categoryName = obj.str("category")))
    }
}

private fun failed(reason: String): AiOutcome<Nothing> =
    if (reason in AiFailureReasons.TRANSIENT) AiOutcome.Unavailable else AiOutcome.Rejected(reason)

/** Промпт разбора банковского сообщения. Текст сообщения не покидает устройство (модель в AICore). */
internal object SmsPrompt {
    const val SYSTEM: String =
        "Ты извлекаешь поля из уведомления банка. Ничего не придумывай и не вычисляй: " +
            "суммы и цифры карты переписывай из текста как есть. Если поля нет в тексте — null."

    fun build(text: String, meta: MessageMeta): String = buildString {
        appendLine("Верни JSON по схеме:")
        appendLine(
            "{\"is_operation\": true|false, \"amount\": \"сумма операции как в тексте\", " +
                "\"currency\": \"код ISO 4217, например RUB\", " +
                "\"type\": \"debit|credit|transfer|refund|cashback\", \"direction\": \"in|out|null\", " +
                "\"merchant\": \"продавец как в тексте или null\", \"card_last4\": \"4 цифры карты или null\", " +
                "\"balance\": \"остаток/баланс как в тексте или null\"}",
        )
        appendLine("debit — списание, покупка, оплата; credit — зачисление, пополнение; transfer — перевод " +
            "(direction: in — входящий, out — исходящий); refund — возврат; cashback — кэшбэк.")
        appendLine("Коды подтверждения, реклама, отказы и уведомления без движения денег — is_operation: false.")
        appendLine("Отправитель: ${meta.sender}")
        appendLine("Сообщение:")
        appendLine("<<<")
        meta.title?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
        appendLine(text)
        append(">>>")
    }
}

/** Промпт имени и категории мерчанта; примеры — ручные решения пользователя. */
internal object MerchantPrompt {
    const val SYSTEM: String =
        "Ты помогаешь вести личный бюджет: даёшь продавцу понятное короткое название и выбираешь категорию " +
            "строго из списка. Если не уверен — null."

    fun build(merchant: String, categories: List<Category>, examples: List<CategoryExample>): String = buildString {
        appendLine("Продавец из банковского сообщения: \"${merchant.take(MAX_MERCHANT)}\"")
        appendLine(
            "name — название бренда так, как его обычно пишут по-русски (например «Яндекс Go», «Пятёрочка»), " +
                "без города, номера магазина, юр. формы и служебных символов.",
        )
        appendLine("category — одна категория из списка: ${categories.joinToString(", ") { it.name }}.")
        if (examples.isNotEmpty()) {
            appendLine("Как пользователь раньше категоризировал продавцов:")
            examples.forEach { appendLine("- \"${it.merchant.take(MAX_MERCHANT)}\" → ${it.categoryName}") }
        }
        append("Верни JSON: {\"name\": \"…\" или null, \"category\": \"…\" или null}")
    }

    private const val MAX_MERCHANT = 64
}
