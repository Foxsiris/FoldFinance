package com.foldfinance.app.ingest.sms

import com.foldfinance.app.ingest.Registry
import com.foldfinance.app.ingest.T0
import com.foldfinance.app.ingest.Texts
import com.foldfinance.parser.BankParser
import com.foldfinance.parser.MessageMeta
import com.foldfinance.parser.ParseResult
import com.foldfinance.parser.ParserRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmsAdmissionTest {

    private val admission = SmsAdmission(Registry)
    private val at = T0.toEpochMilli()

    @Test fun `bank operation is admitted`() {
        assertThat(admission.admit("900", Texts.PURCHASE, at)).isTrue()
    }

    @Test fun `one-time codes and promos from a bank are not admitted`() {
        assertThat(admission.admit("900", Texts.OTP, at)).isFalse()
        assertThat(admission.admit("900", Texts.PROMO, at)).isFalse()
    }

    @Test fun `private message is not admitted`() {
        assertThat(admission.admit("+79990000000", Texts.CHAT, at)).isFalse()
    }

    @Test fun `unknown sender is admitted only when money was found`() {
        assertThat(admission.admit("ShopBank", Texts.TBANK_PURCHASE, at)).isTrue()
        assertThat(admission.admit("ShopBank", "Ваш заказ доставлен в пункт выдачи", at)).isFalse()
    }

    @Test fun `parser error is admitted from any sender`() {
        val crashing = object : BankParser {
            override val id = "crashing"
            override fun canHandle(meta: MessageMeta) = true
            override fun parse(text: String, meta: MessageMeta): ParseResult = throw IllegalStateException("bug")
        }
        val admission = SmsAdmission(ParserRegistry(listOf(crashing), crashing))
        // The parser crashed: unknown whether it is money; stored so that a fixed parser re-reads it.
        assertThat(admission.admit("ShopBank", "Списание 1 500 RUB", at)).isTrue()
    }

    @Test fun `empty body or address is not admitted`() {
        assertThat(admission.admit("", Texts.PURCHASE, at)).isFalse()
        assertThat(admission.admit("900", "  ", at)).isFalse()
    }
}
