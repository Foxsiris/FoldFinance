package com.foldfinance.core.domain.category

/**
 * Проверки имени мерчанта, предложенного моделью. Модели не верим на слово: имя должно
 * узнаваться в сыром названии из сообщения (`YANDEX*GO*TAXI` → «Яндекс Go» — да,
 * `PYATEROCHKA` → «Магнит» — нет).
 */
object MerchantAliasRules {

    const val MAX_NAME_LENGTH: Int = 48
    private const val MIN_TOKEN = 3

    /**
     * Имя правдоподобно для сырого названия: хотя бы одно слово имени (от 3 букв, после
     * транслитерации [MerchantNormalizer.canonical]) входит в сырое название без учёта пробелов,
     * либо всё имя целиком входит в него (или наоборот).
     */
    fun isPlausible(rawMerchant: String, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH || trimmed.any { it == '\n' || it == '\r' }) return false
        if (trimmed.none { it.isLetter() }) return false
        val raw = MerchantNormalizer.canonical(rawMerchant).replace(" ", "")
        val candidate = MerchantNormalizer.canonical(trimmed)
        if (raw.isEmpty() || candidate.isEmpty()) return false
        val compact = candidate.replace(" ", "")
        if (raw.contains(compact) || compact.contains(raw)) return true
        return candidate.split(' ').any { it.length >= MIN_TOKEN && raw.contains(it) }
    }
}
