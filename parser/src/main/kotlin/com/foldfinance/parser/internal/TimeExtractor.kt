package com.foldfinance.parser.internal

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Время операции из текста: `05.11.2026 12:34`, `05.11.26 12:34`, `05.11 12:34`, `12:34`.
 * Недостающие части даты берутся из времени получения в часовом поясе [zone].
 * Дата без времени игнорируется (нет точного момента).
 */
internal object TimeExtractor {

    private const val TIME = "(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?![\\d:])"
    private val fullDateTime = Regex("(?<![\\d.])(\\d{2})\\.(\\d{2})\\.(\\d{4}|\\d{2})(?![\\d.])[ ,]*(?:в )?$TIME")
    private val dayMonthTime = Regex("(?<![\\d.])(\\d{2})\\.(\\d{2})(?![\\d.])[ ,]*(?:в )?$TIME")
    private val timeOnly = Regex("(?<![\\d:.,])$TIME")

    /** Допуск «время из текста позже времени получения» (рассинхрон часов). */
    private const val FUTURE_TOLERANCE_MINUTES = 5L

    fun extract(lower: String, receivedAtMillis: Long, zone: ZoneId): Long? {
        val received = Instant.ofEpochMilli(receivedAtMillis).atZone(zone)
        fullDateTime.find(lower)?.let { m ->
            val g = m.groupValues
            val year = g[3].toInt().let { if (g[3].length == 2) 2000 + it else it }
            build(year, g[2], g[1], g[4], g[5], g[6], zone)?.let { return it.toInstant().toEpochMilli() }
        }
        dayMonthTime.find(lower)?.let { m ->
            val g = m.groupValues
            var dt = build(received.year, g[2], g[1], g[3], g[4], g[5], zone)
            if (dt != null && dt.isAfter(received.plusDays(1))) dt = dt.minusYears(1)
            dt?.let { return it.toInstant().toEpochMilli() }
        }
        timeOnly.find(lower)?.let { m ->
            val g = m.groupValues
            val time = localTime(g[1], g[2], g[3]) ?: return null
            var dt = ZonedDateTime.of(received.toLocalDate(), time, zone)
            if (dt.isAfter(received.plusMinutes(FUTURE_TOLERANCE_MINUTES))) dt = dt.minusDays(1)
            return dt.toInstant().toEpochMilli()
        }
        return null
    }

    private fun build(
        year: Int,
        month: String,
        day: String,
        hour: String,
        minute: String,
        second: String,
        zone: ZoneId,
    ): ZonedDateTime? {
        val time = localTime(hour, minute, second) ?: return null
        val date = runCatching { LocalDate.of(year, month.toInt(), day.toInt()) }.getOrNull() ?: return null
        return ZonedDateTime.of(LocalDateTime.of(date, time), zone)
    }

    private fun localTime(hour: String, minute: String, second: String): LocalTime? = runCatching {
        LocalTime.of(hour.toInt(), minute.toInt(), if (second.isEmpty()) 0 else second.toInt())
    }.getOrNull()
}
