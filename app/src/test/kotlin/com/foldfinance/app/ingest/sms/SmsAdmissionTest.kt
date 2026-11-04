package com.foldfinance.app.ingest.sms

import com.foldfinance.app.ingest.Registry
import com.foldfinance.app.ingest.T0
import com.foldfinance.app.ingest.Texts
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

    @Test fun `empty body or address is not admitted`() {
        assertThat(admission.admit("", Texts.PURCHASE, at)).isFalse()
        assertThat(admission.admit("900", "  ", at)).isFalse()
    }
}
