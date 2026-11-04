package com.foldfinance.parser.bank

import com.foldfinance.parser.BankParser
import com.foldfinance.parser.BankSource
import com.foldfinance.parser.KnownBankSources
import com.foldfinance.parser.MessageMeta
import com.foldfinance.parser.ParseResult
import com.foldfinance.parser.internal.BankProfile
import com.foldfinance.parser.internal.MessageAnalyzer
import java.time.ZoneId

/**
 * Базовый парсер известного банка: выбор по [KnownBankSources], разбор общим движком.
 *
 * Правила построены по синтетическим образцам (см. `fixtures/SYNTHETIC.md`), поэтому банки пока
 * отличаются только источником; банковские особенности добавляются после появления реальных
 * образцов — сначала фикстура, потом код.
 *
 * @param zone часовой пояс, в котором трактуется время из текста (`12:34`, `05.11 12:34`).
 */
abstract class KnownBankParser internal constructor(
    private val source: BankSource,
    zone: ZoneId,
) : BankParser {

    override val id: String = source.bankId

    private val analyzer = MessageAnalyzer(BankProfile(parserId = source.bankId, baseConfidence = BANK_CONFIDENCE), zone)

    override fun canHandle(meta: MessageMeta): Boolean = source.matches(meta)

    override fun parse(text: String, meta: MessageMeta): ParseResult = analyzer.analyze(text, meta)

    private companion object {
        const val BANK_CONFIDENCE = 0.9f
    }
}

/** Сбербанк: SMS от `900`, push из приложения Сбербанка. */
class SberParser(zone: ZoneId = ZoneId.systemDefault()) : KnownBankParser(KnownBankSources.SBER, zone)

/** Т-Банк: SMS от `Tinkoff` / `T-Bank`, push из приложения Т-Банка. */
class TBankParser(zone: ZoneId = ZoneId.systemDefault()) : KnownBankParser(KnownBankSources.TBANK, zone)

/** Совкомбанк и «Халва»: SMS от `Sovcombank` / `SOVCOMBANK` / `Halva`. Отправители не проверены. */
class SovcombankParser(zone: ZoneId = ZoneId.systemDefault()) : KnownBankParser(KnownBankSources.SOVCOMBANK, zone)

/** ВБ Банк (Wildberries Bank): SMS от `WB Bank` / `WBBank` / `Wildberries`. Отправители не проверены. */
class WbBankParser(zone: ZoneId = ZoneId.systemDefault()) : KnownBankParser(KnownBankSources.WBBANK, zone)

/**
 * Fallback для неизвестных отправителей. Подходит для любого сообщения, но не знает формата,
 * поэтому уверенность любой операции не выше [MAX_CONFIDENCE] — она всегда уходит на проверку.
 */
class GenericParser(zone: ZoneId = ZoneId.systemDefault()) : BankParser {

    override val id: String = ID

    private val analyzer = MessageAnalyzer(
        BankProfile(parserId = ID, baseConfidence = BASE_CONFIDENCE, maxConfidence = MAX_CONFIDENCE),
        zone,
    )

    override fun canHandle(meta: MessageMeta): Boolean = true

    override fun parse(text: String, meta: MessageMeta): ParseResult = analyzer.analyze(text, meta)

    companion object {
        /** Идентификатор парсера. */
        const val ID: String = "generic"

        /** Верхняя граница уверенности GenericParser. */
        const val MAX_CONFIDENCE: Float = 0.6f

        private const val BASE_CONFIDENCE = 0.55f
    }
}
