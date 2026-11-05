package com.foldfinance.parser.ai

import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.foldfinance.parser.MessageChannel
import com.foldfinance.parser.MessageMeta
import com.foldfinance.parser.ParseResult
import com.foldfinance.parser.ParserRegistry
import com.foldfinance.parser.UncertainReasons
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId

class AiExtractionValidatorTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val validator = AiExtractionValidator(zone)
    private val tbank = MessageMeta(MessageChannel.SMS, "Tinkoff", 1_790_000_000_000L)

    /** Синтетический текст: незнакомое слово операции → regex даёт Uncertain(UNKNOWN_OPERATION). */
    private val text = "Операция 1 500 ₽ по карте *1234 KOFEMANIYA. Баланс: 10 234,56 ₽"
    private val regex = ParserRegistry.default(zone).parse(text, tbank)

    private val good = AiExtraction(
        isOperation = true,
        amount = "1500",
        currency = "RUB",
        type = "debit",
        merchant = "KOFEMANIYA",
        cardLast4 = "1234",
        balance = "10234.56",
    )

    private fun accepted(extraction: AiExtraction, result: ParseResult = regex): AiValidation.Accepted {
        val v = validator.validate(text, tbank, extraction, result)
        assertThat(v).isInstanceOf(AiValidation.Accepted::class.java)
        return v as AiValidation.Accepted
    }

    private fun rejectedReason(extraction: AiExtraction): String {
        val v = validator.validate(text, tbank, extraction, regex)
        assertThat(v).isInstanceOf(AiValidation.Rejected::class.java)
        return (v as AiValidation.Rejected).reason
    }

    @Test
    fun regexReallyGivesUpOnSample() {
        assertThat(regex).isInstanceOf(ParseResult.Uncertain::class.java)
        assertThat((regex as ParseResult.Uncertain).reason).isEqualTo(UncertainReasons.UNKNOWN_OPERATION)
        assertThat(AiFallbackPolicy.isCandidate(text, tbank, regex)).isTrue()
    }

    @Test
    fun fullyVerified_becomesConfidentAiOperation() {
        val v = accepted(good)
        assertThat(v.fullyVerified).isTrue()
        assertThat(v.result.parserId).isEqualTo(AI_PARSER_ID)
        val op = v.result.operations.single()
        assertThat(op.amountMinor).isEqualTo(150_000L)
        assertThat(op.currency).isEqualTo("RUB")
        assertThat(op.direction).isEqualTo(Direction.EXPENSE)
        assertThat(op.kind).isEqualTo(Kind.PURCHASE)
        assertThat(op.cardMask).isEqualTo("*1234")
        assertThat(op.merchant).isEqualTo("KOFEMANIYA")
        assertThat(op.confidence).isEqualTo(AI_VERIFIED_CONFIDENCE)
        assertThat(op.confidence).isLessThan(0.9f)
        assertThat(v.result.balance?.amountMinor).isEqualTo(1_023_456L)
    }

    @Test
    fun amountFormats_areComparedByValueNotText() {
        assertThat(accepted(good.copy(amount = "1 500,00 ₽")).fullyVerified).isTrue()
        assertThat(accepted(good.copy(amount = "1500.00", currency = "₽")).fullyVerified).isTrue()
    }

    @Test
    fun hallucinatedAmount_isRejected() {
        assertThat(rejectedReason(good.copy(amount = "2500"))).isEqualTo("amount_not_in_text")
        assertThat(rejectedReason(good.copy(amount = "15000"))).isEqualTo("amount_not_in_text")
    }

    @Test
    fun balanceGivenAsAmount_isRejected() {
        assertThat(rejectedReason(good.copy(amount = "10234.56"))).isEqualTo("amount_is_balance")
    }

    @Test
    fun currencyConflict_isRejected() {
        assertThat(rejectedReason(good.copy(currency = "USD"))).isEqualTo("currency_conflict")
        assertThat(rejectedReason(good.copy(amount = "\$1500"))).isEqualTo("currency_conflict")
        assertThat(rejectedReason(good.copy(currency = "тугрики"))).isEqualTo("currency_invalid")
    }

    @Test
    fun hallucinatedCard_isRejected() {
        assertThat(rejectedReason(good.copy(cardLast4 = "5678"))).isEqualTo("card_not_in_text")
        assertThat(rejectedReason(good.copy(cardLast4 = "12345"))).isEqualTo("card_invalid")
        // «1500» есть в тексте, но только внутри суммы — это не карта.
        assertThat(rejectedReason(good.copy(cardLast4 = "0234"))).isEqualTo("card_not_in_text")
    }

    @Test
    fun notOperation_orNoAmount_isRejected() {
        assertThat(rejectedReason(good.copy(isOperation = false))).isEqualTo("not_operation")
        assertThat(rejectedReason(good.copy(amount = null))).isEqualTo("no_amount")
        assertThat(rejectedReason(good.copy(amount = "полторы тысячи"))).isEqualTo("amount_unparseable")
    }

    @Test
    fun merchantNotInText_isDropped_andGoesToReview() {
        val v = accepted(good.copy(merchant = "Starbucks"))
        assertThat(v.fullyVerified).isFalse()
        assertThat(v.result.operations.single().merchant).isNull()
        assertThat(v.result.operations.single().confidence).isEqualTo(AI_UNVERIFIED_CONFIDENCE)
    }

    @Test
    fun missingOrUnknownType_goesToReview() {
        assertThat(accepted(good.copy(type = null)).fullyVerified).isFalse()
        assertThat(accepted(good.copy(type = "payment")).fullyVerified).isFalse()
        assertThat(accepted(good.copy(type = "cashback")).fullyVerified).isFalse()
    }

    @Test
    fun wrongBalance_isDropped_notTrusted() {
        val v = accepted(good.copy(balance = "99999"))
        assertThat(v.result.balance).isNull()
    }

    @Test
    fun signContradictingType_goesToReview() {
        val plus = "Операция +1 500 ₽ по карте *1234 KOFEMANIYA"
        val r = ParserRegistry.default(zone).parse(plus, tbank)
        val v = validator.validate(plus, tbank, good.copy(balance = null), r) as AiValidation.Accepted
        assertThat(v.fullyVerified).isFalse()
    }

    @Test
    fun keywordContradictingType_goesToReview() {
        val refundText = "Отмена покупки 1 500 ₽ по карте *1234 KOFEMANIYA"
        val v = validator.validate(refundText, tbank, good.copy(balance = null), regex) as AiValidation.Accepted
        assertThat(v.fullyVerified).isFalse()
    }

    @Test
    fun transfer_needsDirectionConfirmedByText() {
        val incoming = "Перевод 5 000 ₽ от Иван И. на карту *1234"
        val r = ParserRegistry.default(zone).parse(incoming, tbank)
        val ok = validator.validate(incoming, tbank, AiExtraction(amount = "5000", type = "transfer", direction = "in", cardLast4 = "1234"), r)
            as AiValidation.Accepted
        assertThat(ok.result.operations.single().direction).isEqualTo(Direction.INCOME)
        assertThat(ok.result.operations.single().kind).isEqualTo(Kind.TRANSFER)
        val noDirection = validator.validate(incoming, tbank, AiExtraction(amount = "5000", type = "transfer"), r)
            as AiValidation.Accepted
        assertThat(noDirection.fullyVerified).isFalse()
    }

    @Test
    fun notFinancialOrigin_isNeverSilent() {
        val v = accepted(good, ParseResult.NotFinancial)
        assertThat(v.fullyVerified).isFalse()
        assertThat(v.result.operations.single().confidence).isEqualTo(AI_UNVERIFIED_CONFIDENCE)
    }

    @Test
    fun policy_selectsOnlyUsefulFallbacks() {
        val sber = MessageMeta(MessageChannel.SMS, "900", 0L)
        val unknown = MessageMeta(MessageChannel.SMS, "SHOP", 0L)
        val parsed = ParserRegistry.default(zone).parse("Покупка 500р Карта*1234 PYATEROCHKA", sber)
        assertThat(parsed).isInstanceOf(ParseResult.Parsed::class.java)
        assertThat(AiFallbackPolicy.isCandidate("Покупка 500р Карта*1234 PYATEROCHKA", sber, parsed)).isFalse()
        assertThat(AiFallbackPolicy.isCandidate("Сумма 500р", sber, ParseResult.NotFinancial)).isTrue()
        assertThat(AiFallbackPolicy.isCandidate("Сумма 500р", unknown, ParseResult.NotFinancial)).isFalse()
        assertThat(AiFallbackPolicy.isCandidate("Код 4821 для оплаты 500р. Никому не сообщайте", sber, ParseResult.NotFinancial))
            .isFalse()
        assertThat(AiFallbackPolicy.isCandidate("Отказ: оплата 500р", sber, ParseResult.NotFinancial)).isFalse()
        assertThat(AiFallbackPolicy.isCandidate("Сервисное сообщение", sber, ParseResult.NotFinancial)).isFalse()
        val ambiguous = ParseResult.Uncertain(UncertainReasons.AMBIGUOUS_AMOUNT, parserId = "sber")
        assertThat(AiFallbackPolicy.isCandidate("Покупка 1,500р", sber, ambiguous)).isFalse()
        assertThat(AiFallbackPolicy.isCandidate("x".repeat(AiFallbackPolicy.MAX_TEXT_LENGTH + 1), sber, regex)).isFalse()
    }
}
