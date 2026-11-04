package com.foldfinance.app.ui.format

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.Clock
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Locale of all UI formatting (the app is Russian-only). */
val RuLocale: Locale = Locale.forLanguageTag("ru-RU")

/**
 * Clock for «today»/«this month» in the UI. Screenshot tests provide a fixed clock so images do
 * not change with the calendar; production uses the system clock and time zone.
 */
val LocalClock = staticCompositionLocalOf<Clock> { Clock.systemDefaultZone() }

/** Month name in the nominative case, capitalised: «Ноябрь». */
fun monthTitle(date: LocalDate): String =
    date.month.getDisplayName(TextStyle.FULL_STANDALONE, RuLocale)
        .replaceFirstChar { it.titlecase(RuLocale) }
