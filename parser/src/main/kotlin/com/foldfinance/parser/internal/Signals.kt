package com.foldfinance.parser.internal

/** Вид операции до раскладки на Direction/Kind. */
internal enum class OpType {
    PURCHASE, SUBSCRIPTION, REFUND, CASH_WITHDRAWAL, CASH_DEPOSIT, TOP_UP, TRANSFER, FEE, CREDIT_IN, CREDIT_OUT, FX,
}

internal class KeywordHit(val type: OpType, val start: Int, val length: Int)

/**
 * Словари и признаки. Все шаблоны работают по [TextNormalizer.matchingForm]
 * (нижний регистр, `ё` → `е`) и привязаны к началу слова.
 */
internal object Signals {

    private val operationKeywords: List<Pair<OpType, String>> = listOf(
        OpType.REFUND to "возврат",
        OpType.REFUND to "отмена покупки",
        OpType.REFUND to "отмена операции",
        OpType.REFUND to "отмена оплаты",
        OpType.REFUND to "refund",
        OpType.CASH_WITHDRAWAL to "выдача наличных",
        OpType.CASH_WITHDRAWAL to "снятие наличных",
        OpType.CASH_WITHDRAWAL to "получение наличных",
        OpType.CASH_WITHDRAWAL to "выдача",
        OpType.CASH_WITHDRAWAL to "снятие",
        OpType.CASH_DEPOSIT to "внесение наличных",
        OpType.CASH_DEPOSIT to "взнос наличных",
        OpType.CASH_DEPOSIT to "внесение",
        OpType.CREDIT_IN to "выдача кредита",
        OpType.CREDIT_IN to "зачисление кредита",
        OpType.CREDIT_IN to "кредит зачислен",
        OpType.CREDIT_IN to "кредит выдан",
        OpType.CREDIT_OUT to "погашение кредита",
        OpType.CREDIT_OUT to "платеж по кредиту",
        OpType.CREDIT_OUT to "оплата кредита",
        OpType.FX to "обмен валюты",
        OpType.FX to "покупка валюты",
        OpType.FX to "продажа валюты",
        OpType.FX to "конвертация",
        OpType.SUBSCRIPTION to "оплата подписки",
        OpType.SUBSCRIPTION to "продление подписки",
        OpType.SUBSCRIPTION to "подписка",
        OpType.TOP_UP to "зачисление",
        OpType.TOP_UP to "зачислено",
        OpType.TOP_UP to "пополнение",
        OpType.TOP_UP to "поступление",
        OpType.TOP_UP to "поступило",
        OpType.TOP_UP to "зарплата",
        OpType.FEE to "комиссия",
        OpType.FEE to "комиссия за",
        OpType.FEE to "списание комиссии",
        OpType.FEE to "плата за обслуживание",
        OpType.FEE to "оплата обслуживания",
        OpType.FEE to "оплата годового обслуживания",
        OpType.FEE to "годовое обслуживание",
        OpType.FEE to "абонентская плата",
        OpType.TRANSFER to "перевод",
        OpType.TRANSFER to "входящий перевод",
        OpType.TRANSFER to "исходящий перевод",
        OpType.TRANSFER to "transfer",
        OpType.PURCHASE to "покупка",
        OpType.PURCHASE to "оплата",
        OpType.PURCHASE to "списание",
        OpType.PURCHASE to "списано",
        OpType.PURCHASE to "платеж",
        OpType.PURCHASE to "pokupka",
        OpType.PURCHASE to "oplata",
        OpType.PURCHASE to "purchase",
        OpType.PURCHASE to "payment",
    )

    private val keywordRegexes: List<Pair<OpType, Regex>> =
        operationKeywords.map { (type, kw) -> type to Regex("(?<!\\p{L})" + Regex.escape(kw)) }

    /** Самое раннее ключевое слово операции; при равной позиции — самое длинное. */
    fun findOperation(lower: String): KeywordHit? {
        var best: KeywordHit? = null
        for ((type, regex) in keywordRegexes) {
            val m = regex.find(lower) ?: continue
            val hit = KeywordHit(type, m.range.first, m.value.length)
            val current = best
            if (current == null || hit.start < current.start ||
                (hit.start == current.start && hit.length > current.length)
            ) {
                best = hit
            }
        }
        return best
    }

    private val otpPhrases = Regex(
        "(никому не (сообщайте|говорите|называйте)|не сообщайте|не говорите никому|" +
            "одноразов\\p{L}* (код|пароль)|код подтверждения|код для|пароль для|ваш код|" +
            "verification code|confirmation code|one-time (code|password)|(?<!\\p{L})otp(?!\\p{L}))",
    )
    private val codeWord = Regex("(?<!\\p{L})(код|пароль|code)(?!\\p{L})(?! авторизации)")
    private val standaloneCode = Regex("(?<![\\d*.,:])\\d{4,8}(?![\\d.,:])")

    /** Код подтверждения / одноразовый пароль. Даже если в нём есть сумма — это не операция. */
    fun isOneTimeCode(lower: String): Boolean =
        otpPhrases.containsMatchIn(lower) ||
            (codeWord.containsMatchIn(lower) && standaloneCode.containsMatchIn(lower))

    private val promoMarkers = Regex(
        "((?<!\\p{L})(оформите|оформи|акци[яийю]|скидк|подробнее|успейте|выгодн|получите|одобрен|закажите|" +
            "розыгрыш|предлагаем|предложени|рассрочк)|кешбэк до|кэшбэк до|cashback до|https?://|www\\.|" +
            "[\\p{L}\\d-]+\\.ru(/|(?!\\p{L}))|\\d ?%)",
    )

    /** Признаки рекламы/информирования. Само по себе не решает — см. вызывающий код. */
    fun looksLikePromo(lower: String): Boolean = promoMarkers.containsMatchIn(lower)

    private val declined = Regex("(?<!\\p{L})(отказ|отклонен|недостаточно средств|не выполнена|declined)")

    /** Позиция признака отказа/отклонения операции, если есть. */
    fun declinedAt(lower: String): Int? = declined.find(lower)?.range?.first

    val balanceKeyword = Regex("(?<!\\p{L})(баланс|остаток|доступно|available|balance)")
    val feeKeyword = Regex("(?<!\\p{L})(комисси|fee(?!\\p{L}))")

    /** Признаки «деньги пришли» для переводов. */
    val incomingMarkers = Regex("(?<!\\p{L})(от(?!\\p{L})|входящ|поступ|зачислен|пополнен|получен(?!\\p{L}|и))")

    /** Явные признаки «деньги ушли» для переводов (без учёта «на карту *1234»). */
    val outgoingMarkers = Regex("(?<!\\p{L})(исходящ|отправ|списан|получател)")

    /** Перед суммой (в «глагольной» части) — признак комиссии/обслуживания. */
    val feePrefix = Regex("(?<!\\p{L})(обслуживан|комисси)")

    val subscription = Regex("(?<!\\p{L})подписк")
}
