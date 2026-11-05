package com.foldfinance.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.foldfinance.app.R

/**
 * Sizes of the Obsidian type scale (docs/design.md, «Типографика»):
 * caption 12.5 · row 15 · title 22 · amounts 30 / 42 / 48 / 60 sp.
 */
object TypeScale {
    /** Overline («НАДПИСЬ», caps +4 %), nav labels are 11.5. */
    val Xs = 12.sp

    /** Captions and meta lines («Подпись»). */
    val S = 12.5.sp

    /** Body text and list rows («Строка»). */
    val M = 15.sp

    /** Small titles. */
    val L = 17.sp

    /** Screen and pane titles («Заголовок»). */
    val Xl = 22.sp

    /** Secondary big numbers. */
    val X2l = 30.sp

    /** Amount on the review card. */
    val X3l = 42.sp

    /** Hero amount of the outer screen («Потрачено за месяц»). */
    val X4l = 48.sp

    /** Hero amount of tabletop. */
    val X5l = 60.sp
}

/** OpenType feature string for tabular (monospaced) digits: every number in the app uses them. */
const val TabularNumbers: String = "tnum"

/** Tracking of big amounts: −4.5 % (System board, «Сумма · 48–60 / 600 / −4.5%»). */
val AmountTracking: TextUnit = (-0.045).em

private fun onest(weight: Int) = Font(
    resId = R.font.onest_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Onest — every piece of text, amounts included. Variable font, instances via `wght`. */
val Onest: FontFamily = FontFamily(onest(400), onest(500), onest(600), onest(700))

/** JetBrains Mono — only the original text of bank messages. */
val JetBrainsMono: FontFamily = FontFamily(
    Font(
        resId = R.font.jetbrains_mono_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
)

private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)
private val TrimNone = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun onestStyle(size: TextUnit, line: TextUnit, weight: Int, tracking: TextUnit = 0.sp) =
    TextStyle(
        fontFamily = Onest,
        fontWeight = FontWeight(weight),
        fontSize = size,
        lineHeight = line,
        letterSpacing = tracking,
        fontFeatureSettings = TabularNumbers,
        platformStyle = NoFontPadding,
        lineHeightStyle = TrimNone,
    )

private fun amountStyle(size: TextUnit, weight: Int = 600) = onestStyle(size, size * 1.08f, weight, AmountTracking)

/** Material roles mapped onto the Obsidian scale. Tabular digits everywhere. */
val FoldTypography: Typography = Typography(
    displayLarge = amountStyle(TypeScale.X5l),
    displayMedium = amountStyle(TypeScale.X4l),
    displaySmall = amountStyle(36.sp),
    headlineLarge = onestStyle(28.sp, 34.sp, 600, (-0.03).em),
    headlineMedium = onestStyle(TypeScale.Xl, 28.sp, 600, (-0.02).em),
    headlineSmall = onestStyle(20.sp, 26.sp, 600, (-0.01).em),
    titleLarge = onestStyle(TypeScale.L, 23.sp, 600, (-0.01).em),
    titleMedium = onestStyle(TypeScale.M, 21.sp, 600),
    titleSmall = onestStyle(13.5.sp, 18.sp, 600),
    bodyLarge = onestStyle(TypeScale.M, 21.sp, 400),
    bodyMedium = onestStyle(13.5.sp, 19.sp, 400),
    bodySmall = onestStyle(TypeScale.S, 17.sp, 400),
    labelLarge = onestStyle(13.5.sp, 18.sp, 500),
    labelMedium = onestStyle(TypeScale.S, 16.sp, 500),
    labelSmall = onestStyle(11.5.sp, 14.sp, 500),
)

/**
 * Text roles Material has no slot for.
 *
 * @property overline «НАДПИСЬ»: 12 sp, +4 % tracking; pass upper-cased text (Compose has no text-transform).
 * @property row title of a list row: 15 / 500.
 * @property caption meta line of a row: 12.5 / 400.
 * @property mono original SMS / push text: JetBrains Mono 12.
 * @property navLabel labels of the navigation bar and rail: 11.5 / 500 (600 when selected).
 */
object FoldTextStyles {
    val overline: TextStyle = onestStyle(TypeScale.Xs, 16.sp, 500, 0.04.em)
    val row: TextStyle = onestStyle(TypeScale.M, 20.sp, 500)
    val caption: TextStyle = onestStyle(TypeScale.S, 16.sp, 400)
    val mono: TextStyle = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.5.sp,
        platformStyle = NoFontPadding,
    )
    val navLabel: TextStyle = onestStyle(11.5.sp, 14.sp, 500)
}

/**
 * Amount styles. All Onest 600 with tabular digits; big ones with −4.5 % tracking.
 *
 * @property hero 60 — tabletop hero.
 * @property display 48 — «Потрачено за месяц» of the outer screen, the typed amount.
 * @property displayMedium 42 — the review card and the detail of an operation.
 * @property displaySmall 30 — secondary big numbers (selected day).
 * @property tile 22 — numbers on tiles («Сегодня», «На проверку»).
 * @property inline 15 / 600 — amounts in rows, right-aligned.
 * @property inlineStrong 15 / 700 — totals inside lists.
 */
@Immutable
data class MoneyTypography(
    val hero: TextStyle = amountStyle(TypeScale.X5l),
    val display: TextStyle = amountStyle(TypeScale.X4l),
    val displayMedium: TextStyle = amountStyle(TypeScale.X3l),
    val displaySmall: TextStyle = onestStyle(TypeScale.X2l, 34.sp, 600, (-0.035).em),
    val tile: TextStyle = onestStyle(TypeScale.Xl, 26.sp, 600, (-0.02).em),
    val inline: TextStyle = onestStyle(TypeScale.M, 20.sp, 600),
    val inlineStrong: TextStyle = onestStyle(TypeScale.M, 20.sp, 700),
)

internal val LocalMoneyTypography = staticCompositionLocalOf { MoneyTypography() }
