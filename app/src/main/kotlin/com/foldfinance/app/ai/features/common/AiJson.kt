package com.foldfinance.app.ai.features.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * Tolerant reading of model output. Even with `jsonOnly` a small model sometimes wraps the object
 * in a code fence or adds a sentence around it, so the first `{` … last `}` is parsed. Everything
 * read here is still untrusted: callers validate every field against their own whitelist.
 */
object AiJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The JSON object inside [text], or `null` if there is none or it is malformed. */
    fun parseObject(text: String): JsonObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.parseToJsonElement(text.substring(start, end + 1)) as? JsonObject }.getOrNull()
    }
}

/** A non-blank string field; JSON `null`, `"null"` and blanks give `null`. Numbers are returned as text. */
fun JsonObject.text(key: String): String? {
    val value = this[key] as? JsonPrimitive ?: return null
    if (value is JsonNull) return null
    val content = value.content.trim()
    return content.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

/** An integer field written as a number or a string. */
fun JsonObject.integer(key: String): Int? = text(key)?.toIntOrNull()

/** A boolean field written as `true`/`false` (or the same as a string). */
fun JsonObject.flag(key: String): Boolean? = when (text(key)?.lowercase(Locale.ROOT)) {
    "true" -> true
    "false" -> false
    else -> null
}

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

/** Comparison key for user-facing names: lower case, `ё` = `е`, single spaces, no quotes. */
fun normKey(value: String): String =
    value.lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(Regex("[«»\"'`]"), "")
        .trim()
        .replace(Regex("\\s+"), " ")
