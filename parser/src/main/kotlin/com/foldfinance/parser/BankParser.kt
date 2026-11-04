package com.foldfinance.parser

/**
 * Парсер сообщений одного банка (или общий fallback).
 * Реализации не должны иметь состояния и не должны обращаться к сети или диску.
 */
interface BankParser {
    /** Стабильный идентификатор, например `sber`, `tbank`, `generic`. */
    val id: String

    /** Подходит ли парсер для этого отправителя SMS или пакета уведомления. */
    fun canHandle(meta: MessageMeta): Boolean

    /** Разобрать текст. Никогда не бросает исключения на «плохом» тексте. */
    fun parse(text: String, meta: MessageMeta): ParseResult
}
