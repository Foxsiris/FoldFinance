package com.foldfinance.app.ingest.debug

/**
 * Synthetic bank texts for the debug tools — the same impersonal samples as the parser fixtures
 * (`parser/src/test/resources/fixtures/sber/001.txt`, `tbank/001.txt`, see `SYNTHETIC.md`).
 * Used only by [IngestDebugTools], which renders nothing in release builds.
 */
internal object DebugSamples {
    /** Sber SMS from `900`. */
    val sms: List<String> = listOf(
        "ECMC1234 12:34 Покупка 1 500р PYATEROCHKA Баланс: 10 234,56р",
        "VISA1234 12:40 Возврат покупки 990р OZON.RU Баланс: 11 224,56р",
    )

    const val SMS_SENDER: String = "900"

    /** Push title and text, T-Bank style. */
    const val PUSH_TITLE: String = "Т-Банк"
    const val PUSH_TEXT: String = "Покупка, карта *1234. 1 500 RUB. PYATEROCHKA. Доступно 10 234,56 RUB"
}
