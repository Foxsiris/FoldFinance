package com.foldfinance.parser

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
        val parser = parsers.firstOrNull { it.canHandle(meta) } ?: fallback
        return runCatching { parser.parse(text, meta) }
            .getOrElse { ParseResult.Uncertain(reason = "parser_error", parserId = parser.id) }
    }

    companion object {
        /** Реестр со всеми известными парсерами. Заполняется на этапе 1. */
        fun default(): ParserRegistry = ParserRegistry(parsers = emptyList(), fallback = PlaceholderParser)
    }
}

/** Временный fallback каркаса: всё считает неуверенным. Заменяется GenericParser на этапе 1. */
private object PlaceholderParser : BankParser {
    override val id: String = "placeholder"
    override fun canHandle(meta: MessageMeta): Boolean = true
    override fun parse(text: String, meta: MessageMeta): ParseResult =
        ParseResult.Uncertain(reason = "not_implemented", parserId = id)
}
