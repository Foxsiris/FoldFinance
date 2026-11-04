package com.foldfinance.core.data.db

import java.util.Locale

/**
 * Нормализация строк для сравнения и поиска на стороне Kotlin: SQLite `lower()`/`LIKE`
 * не учитывают регистр кириллицы, поэтому в БД хранятся уже нормализованные копии.
 */
internal object TextNorm {
    private val spaces = Regex("\\s+")

    /** Нижний регистр, ё→е, схлопнутые пробелы (включая NBSP); `null` для пустой строки. */
    fun normalize(value: String?): String? {
        if (value == null) return null
        val result = value
            .replace(' ', ' ')
            .replace(' ', ' ')
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .replace(spaces, " ")
            .trim()
        return result.ifEmpty { null }
    }

    fun searchText(merchant: String?, note: String?): String =
        listOfNotNull(normalize(merchant), normalize(note)).joinToString(" ")

    /** Шаблон `LIKE '%…%'` с экранированием `%`, `_` и `\` (используется с `ESCAPE '\'`). */
    fun containsLike(normalizedNeedle: String): String {
        val escaped = buildString {
            for (c in normalizedNeedle) {
                if (c == '%' || c == '_' || c == '\\') append('\\')
                append(c)
            }
        }
        return "%$escaped%"
    }
}
