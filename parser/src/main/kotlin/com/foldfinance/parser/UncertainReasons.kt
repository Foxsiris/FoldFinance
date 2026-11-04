package com.foldfinance.parser

/** Значения [ParseResult.Uncertain.reason]. Стабильны: на них опираются фикстуры и `:core`. */
object UncertainReasons {
    /** Текст длиннее [MAX_TEXT_LENGTH] — на банковскую операцию не похоже, разбор не выполнялся. */
    const val TEXT_TOO_LONG: String = "text_too_long"

    /** Есть сумма, но вид операции не распознан. */
    const val UNKNOWN_OPERATION: String = "unknown_operation_type"

    /** Вид операции распознан, но суммы с валютой нет. */
    const val AMOUNT_NOT_FOUND: String = "amount_not_found"

    /** Сумма допускает два прочтения (`1,500`). */
    const val AMBIGUOUS_AMOUNT: String = "ambiguous_amount"

    /** Сумма операции равна нулю. */
    const val ZERO_AMOUNT: String = "zero_amount"

    /** Перевод, но непонятно, входящий он или исходящий. */
    const val DIRECTION_UNKNOWN: String = "direction_unknown"

    /** Обмен валюты: две суммы в разных валютах, направление решает пользователь. */
    const val FX_EXCHANGE: String = "fx_exchange"

    /** Только остаток, без операции. */
    const val BALANCE_ONLY: String = "balance_only"

    /** Внутренняя ошибка парсера (не должна случаться; ловится на всякий случай). */
    const val PARSER_ERROR: String = "parser_error"

    /** Максимальная длина текста (заголовок + текст), которую парсер разбирает. */
    const val MAX_TEXT_LENGTH: Int = 4096
}
