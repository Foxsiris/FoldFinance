package com.foldfinance.app.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Строгий разбор JSON-ответов модели. Модель может обернуть ответ в Markdown (```json … ```) или
 * добавить фразу до/после — берём первый сбалансированный `{…}`. Всё, что не объект, — `null`.
 * Значения не интерпретируются (суммы остаются строками): их сверяет с текстом вызывающий.
 */
object AiJson {
    private val json = Json { isLenient = false }

    /** Первый сбалансированный JSON-объект в тексте (с учётом строк и экранирования) или `null`. */
    fun extractObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /** Объект из ответа модели или `null`, если JSON некорректен. */
    fun parseObject(text: String): JsonObject? {
        val candidate = extractObject(text) ?: return null
        return runCatching { json.parseToJsonElement(candidate) }.getOrNull() as? JsonObject
    }

    /** Строковое значение поля: строка или число как написано; `null`, пусто, объекты и массивы → `null`. */
    fun JsonObject.str(key: String): String? {
        val e: JsonElement = this[key] ?: return null
        if (e is JsonNull || e !is JsonPrimitive) return null
        return e.content.trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    /** Логическое значение поля (`true`/`false` или строки "true"/"false"); иначе `null`. */
    fun JsonObject.bool(key: String): Boolean? {
        val e = this[key] as? JsonPrimitive ?: return null
        return e.booleanOrNull ?: when (e.content.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }
}
