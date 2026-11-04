package com.foldfinance.parser

import com.foldfinance.parser.bank.GenericParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId

class ParserRegistryTest {

    private val registry = ParserRegistry.default(ZoneId.of("Europe/Moscow"))

    private fun sms(sender: String) = MessageMeta(MessageChannel.SMS, sender, 0L)
    private fun push(pkg: String) = MessageMeta(MessageChannel.PUSH, pkg, 0L, title = "t")

    @Test fun selectsBySmsSender() {
        assertThat(registry.parserFor(sms("900")).id).isEqualTo("sber")
        assertThat(registry.parserFor(sms("Tinkoff")).id).isEqualTo("tbank")
        assertThat(registry.parserFor(sms("T-Bank")).id).isEqualTo("tbank")
        assertThat(registry.parserFor(sms("Sovcombank")).id).isEqualTo("sovcombank")
        assertThat(registry.parserFor(sms("SOVCOMBANK")).id).isEqualTo("sovcombank")
        assertThat(registry.parserFor(sms("Halva")).id).isEqualTo("sovcombank")
        assertThat(registry.parserFor(sms("WB Bank")).id).isEqualTo("wbbank")
        assertThat(registry.parserFor(sms("WBBank")).id).isEqualTo("wbbank")
        assertThat(registry.parserFor(sms("Wildberries")).id).isEqualTo("wbbank")
    }

    @Test fun senderMatchIsCaseInsensitiveAndTrimmed() {
        assertThat(registry.parserFor(sms("TINKOFF")).id).isEqualTo("tbank")
        assertThat(registry.parserFor(sms(" 900 ")).id).isEqualTo("sber")
        assertThat(registry.parserFor(sms("wb bank")).id).isEqualTo("wbbank")
    }

    @Test fun unknownSenderFallsBackToGeneric() {
        assertThat(registry.parserFor(sms("VTB")).id).isEqualTo(GenericParser.ID)
        assertThat(registry.parserFor(sms("9000")).id).isEqualTo(GenericParser.ID)
        assertThat(registry.parserFor(sms("")).id).isEqualTo(GenericParser.ID)
    }

    @Test fun selectsPushByPackage() {
        for (source in KnownBankSources.all) {
            for (pkg in source.notificationPackages) {
                assertThat(registry.parserFor(push(pkg)).id).isEqualTo(source.bankId)
            }
        }
        assertThat(registry.parserFor(push("com.example.unknown")).id).isEqualTo(GenericParser.ID)
    }

    @Test fun channelMatters() {
        // Адрес SMS, пришедший как «пакет» push, не должен выбирать банк, и наоборот.
        assertThat(registry.parserFor(push("900")).id).isEqualTo(GenericParser.ID)
        val pkg = KnownBankSources.SBER.notificationPackages.first()
        assertThat(registry.parserFor(sms(pkg)).id).isEqualTo(GenericParser.ID)
    }

    @Test fun everyKnownSourceHasParser() {
        for (source in KnownBankSources.all) {
            for (sender in source.smsSenders) {
                assertThat(registry.parserFor(sms(sender)).id).isEqualTo(source.bankId)
            }
        }
        assertThat(KnownBankSources.allNotificationPackages).isNotEmpty()
    }

    @Test fun throwingParserBecomesUncertain() {
        val broken = object : BankParser {
            override val id = "broken"
            override fun canHandle(meta: MessageMeta) = true
            override fun parse(text: String, meta: MessageMeta): ParseResult = error("boom")
        }
        val result = ParserRegistry(listOf(broken), GenericParser()).parse("x", sms("900"))
        assertThat(result).isEqualTo(ParseResult.Uncertain(reason = UncertainReasons.PARSER_ERROR, parserId = "broken"))
    }

    @Test fun defaultWithoutZoneWorks() {
        val result = ParserRegistry.default().parse("Покупка, карта *1234. 100 RUB. SHOP. Доступно 1 000 RUB", sms("Tinkoff"))
        assertThat(result).isInstanceOf(ParseResult.Parsed::class.java)
    }
}
