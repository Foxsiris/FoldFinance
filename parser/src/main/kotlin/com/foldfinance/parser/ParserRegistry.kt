package com.foldfinance.parser

import com.foldfinance.parser.bank.GenericParser
import com.foldfinance.parser.bank.SberParser
import com.foldfinance.parser.bank.SovcombankParser
import com.foldfinance.parser.bank.TBankParser
import com.foldfinance.parser.bank.WbBankParser
import java.time.ZoneId

/**
 * Выбирает парсер по отправителю SMS или пакету уведомления.
 * Если ни один банковский парсер не подходит — [fallback] с низким confidence.
 */
class ParserRegistry(
    private val parsers: List<BankParser>,
    private val fallback: BankParser,
) {
    /** Разобрать сообщение подходящим парсером. Никогда не бросает исключения. */
    fun parse(text: String, meta: MessageMeta): ParseResult {
        val parser = parserFor(meta)
        return runCatching { parser.parse(text, meta) }
            .getOrElse { ParseResult.Uncertain(reason = UncertainReasons.PARSER_ERROR, parserId = parser.id) }
    }

    /** Парсер, который будет использован для сообщения с такими метаданными. */
    fun parserFor(meta: MessageMeta): BankParser = parsers.firstOrNull { it.canHandle(meta) } ?: fallback

    companion object {
        /** Реестр со всеми известными парсерами; время из текста трактуется в часовом поясе устройства. */
        fun default(): ParserRegistry = default(ZoneId.systemDefault())

        /** Реестр со всеми известными парсерами; время из текста трактуется в поясе [zone]. */
        fun default(zone: ZoneId): ParserRegistry = ParserRegistry(
            parsers = listOf(SberParser(zone), TBankParser(zone), SovcombankParser(zone), WbBankParser(zone)),
            fallback = GenericParser(zone),
        )
    }
}
