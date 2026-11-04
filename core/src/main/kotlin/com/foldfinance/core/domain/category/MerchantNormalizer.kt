package com.foldfinance.core.domain.category

import java.util.Locale

/**
 * Нормализация названия мерчанта из банковского сообщения.
 *
 * Банки пишут одного и того же мерчанта по-разному: `PYATEROCHKA 1234`, `Пятёрочка`,
 * `ООО "ПЯТЕРОЧКА"`, `YANDEX*GO`. Здесь две ступени:
 *
 * - [normalize] — нижний регистр, `ё → е`, всё кроме букв и цифр заменено пробелами,
 *   цифровые коды терминалов и организационно-правовые формы (`ООО`, `ИП`, `LLC`, …) убраны.
 *   Письменность (кириллица/латиница) сохраняется.
 * - [canonical] — [normalize] + транслитерация кириллицы в латиницу и фонетическое
 *   выравнивание (`x → ks`, `c → k`, `w → v`, `y → i`, удвоенные буквы схлопываются).
 *   Это ключ сравнения: `canonical("Пятёрочка") == canonical("PYATEROCHKA")`.
 *
 * Обе функции чистые и идемпотентные для обычных названий.
 */
object MerchantNormalizer {

    private val stopTokens = setOf(
        "ооо", "оао", "зао", "пао", "ао", "ип",
        "ooo", "oao", "zao", "pao", "ao", "ip", "llc", "ltd", "inc", "rus",
    )

    private val translit: Map<Char, String> = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "i", 'к' to "k", 'л' to "l",
        'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s",
        'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e",
        'ю' to "yu", 'я' to "ya", 'і' to "i", 'ї' to "i", 'є' to "e",
    )

    /**
     * Приводит название к нижнему регистру, заменяет `ё` на `е`, убирает пунктуацию,
     * цифровые коды (терминалы, номера магазинов) и организационно-правовые формы.
     * Слова разделены одним пробелом. Для `null` и пустой строки возвращает `""`.
     */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val lowered = raw.lowercase(Locale.ROOT).replace('ё', 'е')
        val sb = StringBuilder(lowered.length + 8)
        var prev: Char? = null
        for (ch in lowered) {
            val c = if (ch.isLetterOrDigit()) ch else ' '
            // Разделяем буквы и цифры: "PYATEROCHKA5432" -> "pyaterochka 5432".
            if (prev != null && c != ' ' && prev != ' ' && prev.isDigit() != c.isDigit()) sb.append(' ')
            sb.append(c)
            prev = c
        }
        return sb.split(' ')
            .asSequence()
            .filter { it.isNotEmpty() }
            .filterNot { token -> token.all { it.isDigit() } }
            .filterNot { it in stopTokens }
            .joinToString(" ")
    }

    /**
     * Ключ сравнения, не зависящий от письменности и мелких различий в написании.
     * Используется для правил пользователя, словаря и группировки подписок.
     */
    fun canonical(raw: String?): String {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) return ""
        val latin = StringBuilder(normalized.length + 8)
        for (ch in normalized) latin.append(translit[ch] ?: ch.toString())
        val phonetic = latin.toString()
            .replace("ph", "f")
            .replace("x", "ks")
            .replace('c', 'k')
            .replace('q', 'k')
            .replace('w', 'v')
            .replace('y', 'i')
            .replace('j', 'i')
        val out = StringBuilder(phonetic.length)
        for (ch in phonetic) {
            if (out.isNotEmpty() && out.last() == ch && ch.isLetter()) continue
            out.append(ch)
        }
        return out.toString().trim()
    }
}
