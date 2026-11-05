package com.foldfinance.parser

/**
 * Откуда приходят сообщения банка.
 *
 * @property bankId совпадает с [BankParser.id] парсера этого банка.
 * @property smsSenders адреса SMS-отправителей (сравнение без учёта регистра и пробелов по краям).
 * @property notificationPackages имена пакетов приложений банка для push.
 */
data class BankSource(
    val bankId: String,
    val displayName: String,
    val smsSenders: Set<String>,
    val notificationPackages: Set<String>,
) {
    /** Пришло ли сообщение из этого источника: SMS — по адресу, push — по имени пакета. */
    fun matches(meta: MessageMeta): Boolean {
        val sender = meta.sender.trim()
        val candidates = when (meta.channel) {
            MessageChannel.SMS -> smsSenders
            MessageChannel.PUSH -> notificationPackages
        }
        return candidates.any { it.equals(sender, ignoreCase = true) }
    }
}

/**
 * Единственное место, где перечислены отправители SMS и пакеты приложений банков.
 *
 * **Проверено на Fold 8 пользователя 2026-11-05** (`adb shell pm list packages`, отправители SMS
 * из входящих): пакеты всех четырёх банков; SMS-отправители `900`, `T-Bank`, `Sovcombank`, `WB-Bank`.
 * Остальные варианты отправителей оставлены как синонимы. Пользователь всё равно подтверждает
 * пакеты в белом списке уведомлений (этап 4); этот список только поднимает банки наверх.
 */
object KnownBankSources {

    /** Сбербанк. SMS с короткого номера `900`. */
    val SBER: BankSource = BankSource(
        bankId = "sber",
        displayName = "Сбербанк",
        smsSenders = setOf("900"),
        notificationPackages = setOf("ru.sberbankmobile"),
    )

    /** Т-Банк (бывший Тинькофф). */
    val TBANK: BankSource = BankSource(
        bankId = "tbank",
        displayName = "Т-Банк",
        smsSenders = setOf("Tinkoff", "T-Bank"),
        notificationPackages = setOf("com.idamob.tinkoff.android"),
    )

    /** Совкомбанк (включая карту «Халва», приложение «Халва-Совкомбанк»). */
    val SOVCOMBANK: BankSource = BankSource(
        bankId = "sovcombank",
        displayName = "Совкомбанк",
        smsSenders = setOf("Sovcombank", "SOVCOMBANK", "Halva"),
        notificationPackages = setOf("ru.sovcomcard.halva.v1"),
    )

    /**
     * ВБ Банк (Wildberries Bank). Отдельного приложения нет: push приходят из приложения Wildberries,
     * поэтому там же и реклама магазина — её отсекает разбор (NotFinancial).
     */
    val WBBANK: BankSource = BankSource(
        bankId = "wbbank",
        displayName = "ВБ Банк",
        smsSenders = setOf("WB-Bank", "WB Bank", "WBBank", "Wildberries"),
        notificationPackages = setOf("com.wildberries.ru"),
    )

    /** Все известные банки в порядке приоритета выбора. */
    val all: List<BankSource> = listOf(SBER, TBANK, SOVCOMBANK, WBBANK)

    /** Все известные пакеты банковских приложений — подсказка для экрана белого списка. */
    val allNotificationPackages: Set<String> = all.flatMapTo(linkedSetOf()) { it.notificationPackages }

    /** Банк, к которому относится сообщение, или `null`, если источник неизвестен. */
    fun forMeta(meta: MessageMeta): BankSource? = all.firstOrNull { it.matches(meta) }
}
