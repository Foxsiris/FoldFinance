package com.foldfinance.parser.internal

/**
 * Приведение текста к единому виду. Все преобразования посимвольные (1:1),
 * поэтому позиции в нормализованном тексте совпадают с позициями в исходном.
 */
internal object TextNormalizer {

    /** NBSP, тонкие и прочие «типографские» пробелы, табуляция и `\r` → обычный пробел. */
    fun normalize(text: String): String {
        val out = CharArray(text.length)
        for (i in text.indices) {
            val c = text[i]
            out[i] = when (c) {
                ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
                ' ', ' ', ' ', '\t', '\r',
                -> ' '
                else -> c
            }
        }
        return String(out)
    }

    /** Форма для поиска ключевых слов: нижний регистр, `ё` → `е`. Длина не меняется. */
    fun matchingForm(normalized: String): String {
        val out = CharArray(normalized.length)
        for (i in normalized.indices) {
            val c = normalized[i].lowercaseChar()
            out[i] = if (c == 'ё') 'е' else c
        }
        return String(out)
    }
}
