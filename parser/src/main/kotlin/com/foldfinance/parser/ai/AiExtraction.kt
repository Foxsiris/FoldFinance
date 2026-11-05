package com.foldfinance.parser.ai

import com.foldfinance.parser.AmountParser
import com.foldfinance.parser.AmountSign
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.foldfinance.parser.KnownBankSources
import com.foldfinance.parser.MessageMeta
import com.foldfinance.parser.ParseResult
import com.foldfinance.parser.ParsedAmount
import com.foldfinance.parser.ParsedBalance
import com.foldfinance.parser.ParsedOperation
import com.foldfinance.parser.UncertainReasons
import com.foldfinance.parser.internal.OpType
import com.foldfinance.parser.internal.Signals
import com.foldfinance.parser.internal.TextNormalizer
import com.foldfinance.parser.internal.TimeExtractor
import java.time.ZoneId

/** `parserId` операций, разобранных локальной моделью (сохраняется в `RawMessage.parserId`). */
const val AI_PARSER_ID: String = "ai"

/** Уверенность операции, у которой всё сверено с текстом: ниже банковских парсеров (0.9), выше порога проверки. */
const val AI_VERIFIED_CONFIDENCE: Float = 0.8f

/** Уверенность операции, где что-то не сверено: ниже порога — операция уходит на проверку. */
const val AI_UNVERIFIED_CONFIDENCE: Float = 0.5f

/**
 * Поля, которые модель вернула для одного сообщения, как есть (строками из JSON).
 * Ничему здесь не доверяем: [AiExtractionValidator] сверяет всё с исходным текстом.
 *
 * @property type `debit` | `credit` | `transfer` | `refund` | `cashback` (иначе — тип неизвестен).
 * @property direction для перевода: `in` | `out`.
 * @property cardLast4 последние 4 цифры карты.
 */
data class AiExtraction(
    val isOperation: Boolean? = null,
    val amount: String? = null,
    val currency: String? = null,
    val type: String? = null,
    val direction: String? = null,
    val merchant: String? = null,
    val cardLast4: String? = null,
    val balance: String? = null,
)

/** Итог сверки ответа модели с текстом. */
sealed interface AiValidation {
    /**
     * Операция принята. [fullyVerified] — сумма, валюта, карта, мерчант сверены с текстом и тип
     * однозначен; только тогда операция не уходит на проверку.
     */
    data class Accepted(val result: ParseResult.Parsed, val fullyVerified: Boolean) : AiValidation

    /** Ответ отклонён (галлюцинация, нет суммы в тексте, конфликт валют…); [reason] — без значений из текста. */
    data class Rejected(val reason: String) : AiValidation
}

/**
 * Какие сообщения стоит отдать модели, когда regex-парсер не справился.
 *
 * - `Uncertain` с причиной [UncertainReasons.UNKNOWN_OPERATION] (есть сумма, вид не понят) или
 *   [UncertainReasons.PARSER_ERROR]. Остальные причины модель не исправит: суммы без валюты она не
 *   докажет текстом, неоднозначную сумму (`1,500`) и направление перевода — тоже.
 * - `NotFinancial` от известного банка ([KnownBankSources]), если в тексте есть сумма с валютой и это не
 *   код подтверждения и не отказ — возможно, незнакомая формулировка операции.
 */
object AiFallbackPolicy {

    /** Длиннее этого текст модели не отдаём (уведомления банков короткие). */
    const val MAX_TEXT_LENGTH: Int = 1000

    private val aiReasons = setOf(UncertainReasons.UNKNOWN_OPERATION, UncertainReasons.PARSER_ERROR)

    fun isCandidate(text: String, meta: MessageMeta, result: ParseResult): Boolean {
        val combined = combinedText(text, meta)
        if (combined.isBlank() || combined.length > MAX_TEXT_LENGTH) return false
        return when (result) {
            is ParseResult.Parsed -> false
            is ParseResult.Uncertain -> result.reason in aiReasons
            ParseResult.NotFinancial -> {
                if (KnownBankSources.forMeta(meta) == null) return false
                val norm = TextNormalizer.normalize(combined)
                val lower = TextNormalizer.matchingForm(norm)
                !Signals.isOneTimeCode(lower) && Signals.declinedAt(lower) == null && AmountParser.findAll(norm).isNotEmpty()
            }
        }
    }

    internal fun combinedText(text: String, meta: MessageMeta): String {
        val title = meta.title?.takeIf { it.isNotBlank() }
        return if (title != null) "$title\n$text" else text
    }
}

/**
 * Сверка ответа модели с исходным текстом. Модель никогда не считает деньги: сумма операции —
 * это сумма, которую [AmountParser] нашёл в тексте и которая совпала с ответом модели.
 *
 * Отклоняется (→ сообщение остаётся как было):
 * - `is_operation = false`, нет суммы, сумма не разбирается;
 * - сумма модели не встречается в тексте (с валютой) — галлюцинация;
 * - сумма модели есть в тексте только как остаток («Баланс: …»);
 * - валюта модели расходится с валютой суммы в тексте;
 * - маска карты не встречается в тексте отдельными 4 цифрами.
 *
 * Принятая операция: `parserId = "ai"`, уверенность [AI_VERIFIED_CONFIDENCE], если всё сверено и тип
 * однозначен, иначе [AI_UNVERIFIED_CONFIDENCE] (→ `needsReview`). Тип не однозначен, если модель его
 * не назвала, если он противоречит знаку суммы или ключевому слову в тексте, если это перевод без
 * подтверждённого текстом направления, а также для сообщений, которые regex-парсер счёл не операцией.
 * Мерчант, которого нет в тексте, и остаток, не найденный в тексте, отбрасываются.
 */
class AiExtractionValidator(private val zone: ZoneId = ZoneId.systemDefault()) {

    fun validate(text: String, meta: MessageMeta, extraction: AiExtraction, regexResult: ParseResult): AiValidation {
        if (extraction.isOperation == false) return reject("not_operation")
        val combined = AiFallbackPolicy.combinedText(text, meta)
        val norm = TextNormalizer.normalize(combined)
        val lower = TextNormalizer.matchingForm(norm)

        val amountToken = extraction.amount?.trim()?.takeIf { it.isNotEmpty() } ?: return reject("no_amount")
        val modelAmount = AmountParser.parse(amountToken) ?: return reject("amount_unparseable")
        if (modelAmount.amountMinor <= 0L) return reject("amount_not_positive")
        val currencyToken = extraction.currency?.trim()?.takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }
        val modelCurrency = currencyToken?.let(::isoOf)
        if (currencyToken != null && modelCurrency == null) return reject("currency_invalid")
        if (modelAmount.currency != null && modelCurrency != null && modelAmount.currency != modelCurrency) {
            return reject("currency_conflict")
        }
        val declaredCurrency = modelCurrency ?: modelAmount.currency

        val inText = AmountParser.findAll(norm)
        val roles = roles(inText, lower)
        val sameValue = inText.indices.filter { i ->
            val a = inText[i]
            a.amountMinor == modelAmount.amountMinor && (declaredCurrency == null || a.currency == declaredCurrency)
        }
        if (sameValue.isEmpty()) {
            // Сумма есть, но с другой валютой — это конфликт валют, а не просто «нет суммы».
            val otherCurrency = inText.any { it.amountMinor == modelAmount.amountMinor }
            return reject(if (otherCurrency) "currency_conflict" else "amount_not_in_text")
        }
        val opIndex = sameValue.firstOrNull { roles[it] == Role.PLAIN }
            ?: sameValue.firstOrNull { roles[it] == Role.FEE }
            ?: return reject("amount_is_balance")
        val opAmount = inText[opIndex]
        val currency = opAmount.currency ?: return reject("amount_without_currency")

        val cardMask = when (val last4 = extraction.cardLast4?.trim()?.takeIf { it.isNotEmpty() && it != "null" }) {
            null -> null
            else -> {
                val digits = last4.filter(Char::isDigit)
                if (digits.length != CARD_DIGITS || digits.length != last4.trimStart('*', '•', ' ').length) {
                    return reject("card_invalid")
                }
                if (!cardInText(digits, lower, inText)) return reject("card_not_in_text")
                "*$digits"
            }
        }

        val mapped = mapType(extraction.type, extraction.direction, merchantPresent = extraction.merchant != null)
        var unambiguous = mapped.unambiguous
        val direction = mapped.direction
        if (opAmount.sign == AmountSign.PLUS && direction == Direction.EXPENSE) unambiguous = false
        if (opAmount.sign == AmountSign.MINUS && direction != Direction.EXPENSE) unambiguous = false
        if (keywordConflicts(lower, direction)) unambiguous = false
        if (mapped.kind == Kind.TRANSFER && !transferDirectionConfirmed(lower, direction)) unambiguous = false

        val merchant = extraction.merchant?.trim()?.takeIf { it.isNotEmpty() && it != "null" && it.length <= MAX_MERCHANT }
        val merchantInText = merchant?.let { lower.contains(TextNormalizer.matchingForm(TextNormalizer.normalize(it))) } ?: true
        val keptMerchant = merchant?.takeIf { merchantInText }

        val balance = extraction.balance?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
            ?.let(AmountParser::parse)
            ?.let { b ->
                inText.indices.firstOrNull { i ->
                    i != opIndex && roles[i] == Role.BALANCE && inText[i].amountMinor == b.amountMinor &&
                        (b.currency == null || inText[i].currency == b.currency) && !inText[i].isAmbiguous
                }
            }
            ?.let { i -> ParsedBalance(inText[i].amountMinor, requireNotNull(inText[i].currency), cardMask) }

        val fullyVerified = unambiguous && !opAmount.isAmbiguous && merchantInText && roles[opIndex] == Role.PLAIN &&
            regexResult !is ParseResult.NotFinancial
        val operation = ParsedOperation(
            amountMinor = opAmount.amountMinor,
            currency = currency,
            direction = direction,
            kind = mapped.kind,
            merchant = keptMerchant,
            cardMask = cardMask,
            counterpartyCardMask = null,
            occurredAtMillis = TimeExtractor.extract(lower, meta.receivedAtMillis, zone),
            confidence = if (fullyVerified) AI_VERIFIED_CONFIDENCE else AI_UNVERIFIED_CONFIDENCE,
        )
        return AiValidation.Accepted(
            ParseResult.Parsed(operations = listOf(operation), balance = balance, parserId = AI_PARSER_ID),
            fullyVerified,
        )
    }

    private enum class Role { PLAIN, BALANCE, FEE }

    private class Mapped(val direction: Direction, val kind: Kind, val unambiguous: Boolean)

    private fun mapType(type: String?, transferDirection: String?, merchantPresent: Boolean): Mapped =
        when (type?.trim()?.lowercase()) {
            "debit" -> Mapped(Direction.EXPENSE, if (merchantPresent) Kind.PURCHASE else Kind.UNKNOWN, unambiguous = merchantPresent)
            "credit" -> Mapped(Direction.INCOME, Kind.TOP_UP, unambiguous = true)
            "refund" -> Mapped(Direction.REFUND, Kind.REFUND, unambiguous = true)
            // Отдельного вида «кэшбэк» нет: доход неизвестного вида, пользователь подтверждает.
            "cashback" -> Mapped(Direction.INCOME, Kind.UNKNOWN, unambiguous = false)
            "transfer" -> when (transferDirection?.trim()?.lowercase()) {
                "in" -> Mapped(Direction.INCOME, Kind.TRANSFER, unambiguous = true)
                "out" -> Mapped(Direction.EXPENSE, Kind.TRANSFER, unambiguous = true)
                else -> Mapped(Direction.EXPENSE, Kind.TRANSFER, unambiguous = false)
            }
            else -> Mapped(Direction.EXPENSE, Kind.UNKNOWN, unambiguous = false)
        }

    /** Ключевое слово операции в тексте говорит о другом направлении денег, чем модель. */
    private fun keywordConflicts(lower: String, direction: Direction): Boolean {
        val keyword = Signals.findOperation(lower)?.type ?: return false
        val expected = when (keyword) {
            OpType.PURCHASE, OpType.SUBSCRIPTION, OpType.CASH_WITHDRAWAL, OpType.FEE, OpType.CREDIT_OUT -> Direction.EXPENSE
            OpType.TOP_UP, OpType.CASH_DEPOSIT, OpType.CREDIT_IN -> Direction.INCOME
            OpType.REFUND -> Direction.REFUND
            OpType.TRANSFER, OpType.FX -> return false
        }
        return expected != direction
    }

    /** Направление перевода подтверждено маркерами текста («входящий», «от», «отправлен», …). */
    private fun transferDirectionConfirmed(lower: String, direction: Direction): Boolean {
        val incoming = Signals.incomingMarkers.containsMatchIn(lower)
        val outgoing = Signals.outgoingMarkers.containsMatchIn(lower)
        return when (direction) {
            Direction.INCOME -> incoming && !outgoing
            Direction.EXPENSE -> outgoing && !incoming
            else -> false
        }
    }

    /** 4 цифры карты стоят в тексте отдельно и не являются частью суммы. */
    private fun cardInText(digits: String, lower: String, amounts: List<ParsedAmount>): Boolean =
        Regex("(?<!\\d)$digits(?!\\d)").findAll(lower).any { m ->
            amounts.none { a -> m.range.first in a.range }
        }

    /** Остаток/комиссия — по ключевому слову перед суммой (как в общем движке). */
    private fun roles(amounts: List<ParsedAmount>, lower: String): List<Role> {
        var prevEnd = 0
        return amounts.map { a ->
            val from = maxOf(prevEnd, a.range.first - ROLE_WINDOW)
            val window = lower.substring(from, a.range.first)
            prevEnd = a.range.last + 1
            val balanceAt = Signals.balanceKeyword.findAll(window).lastOrNull()?.range?.first ?: -1
            val feeAt = Signals.feeKeyword.findAll(window).lastOrNull()?.range?.first ?: -1
            when {
                balanceAt < 0 && feeAt < 0 -> Role.PLAIN
                balanceAt > feeAt -> Role.BALANCE
                else -> Role.FEE
            }
        }
    }

    private fun isoOf(token: String): String? =
        AmountParser.currencyOf(token) ?: token.uppercase().takeIf { ISO.matches(it) }

    private fun reject(reason: String) = AiValidation.Rejected(reason)

    private companion object {
        const val ROLE_WINDOW = 30
        const val CARD_DIGITS = 4
        const val MAX_MERCHANT = 64
        val ISO = Regex("^[A-Z]{3}$")
    }
}
