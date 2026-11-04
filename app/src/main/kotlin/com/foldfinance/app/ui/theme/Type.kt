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
 * Type scale «большая терция» (×1.25): 12 · 13 · 15 · 19 · 24 · 30 · 38 · 48 · 60 sp.
 * Body 15/22, label 13/18, title 19/26, headline 24/30 (docs/design.md).
 */
object TypeScale {
    val Xs = 12.sp
    val S = 13.sp
    val M = 15.sp
    val L = 19.sp
    val Xl = 24.sp
    val X2l = 30.sp
    val X3l = 38.sp
    val X4l = 48.sp
    val X5l = 60.sp
}

/** OpenType feature string for tabular (monospaced) digits: sums in lists line up. */
const val TabularNumbers: String = "tnum"

private fun onest(weight: Int) = Font(
    resId = R.font.onest_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun unbounded(weight: Int) = Font(
    resId = R.font.unbounded_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Onest — every piece of text except big amounts. Variable font, instances via `wght`. */
val Onest: FontFamily = FontFamily(onest(400), onest(500), onest(600), onest(700))

/** Unbounded — only amounts ≥ 28 sp and the hero number of the outer screen. 500–600. */
val Unbounded: FontFamily = FontFamily(unbounded(500), unbounded(600))

/** Unbounded tracking from the design doc: −0.02 em. */
val UnboundedTracking: TextUnit = (-0.02).em

private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)
private val TrimBoth = LineHeightStyle(
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
        platformStyle = NoFontPadding,
        lineHeightStyle = TrimBoth,
    )

private fun unboundedStyle(size: TextUnit, line: TextUnit, weight: Int) = TextStyle(
    fontFamily = Unbounded,
    fontWeight = FontWeight(weight),
    fontSize = size,
    lineHeight = line,
    letterSpacing = UnboundedTracking,
    platformStyle = NoFontPadding,
    lineHeightStyle = TrimBoth,
)

/** Material roles mapped onto the FoldFinance scale. */
val FoldTypography: Typography = Typography(
    displayLarge = unboundedStyle(TypeScale.X5l, 64.sp, 600),
    displayMedium = unboundedStyle(TypeScale.X4l, 54.sp, 600),
    displaySmall = unboundedStyle(TypeScale.X3l, 44.sp, 500),
    headlineLarge = onestStyle(TypeScale.X2l, 36.sp, 600, (-0.01).em),
    headlineMedium = onestStyle(TypeScale.Xl, 30.sp, 600, (-0.01).em),
    headlineSmall = onestStyle(TypeScale.L, 26.sp, 600),
    titleLarge = onestStyle(TypeScale.L, 26.sp, 600),
    titleMedium = onestStyle(TypeScale.M, 22.sp, 600),
    titleSmall = onestStyle(TypeScale.S, 18.sp, 600),
    bodyLarge = onestStyle(TypeScale.M, 22.sp, 400),
    bodyMedium = onestStyle(TypeScale.S, 18.sp, 400),
    bodySmall = onestStyle(TypeScale.Xs, 16.sp, 400),
    labelLarge = onestStyle(TypeScale.S, 18.sp, 600, 0.01.em),
    labelMedium = onestStyle(TypeScale.Xs, 16.sp, 500, 0.01.em),
    labelSmall = onestStyle(TypeScale.Xs, 16.sp, 500, 0.02.em),
)

/**
 * Amount styles that Material has no role for.
 *
 * @property hero Unbounded 60 — one per screen at most (outer screen «Сегодня» on large windows).
 * @property display Unbounded 48 — «Сегодня» on the summary card.
 * @property displaySmall Unbounded 30 — month total and secondary big numbers.
 * @property inline Onest 15 with tabular digits — amounts in rows, right-aligned.
 * @property inlineStrong same as [inline] at weight 600 — totals inside lists.
 */
@Immutable
data class MoneyTypography(
    val hero: TextStyle = unboundedStyle(TypeScale.X5l, 64.sp, 600),
    val display: TextStyle = unboundedStyle(TypeScale.X4l, 54.sp, 600),
    val displayMedium: TextStyle = unboundedStyle(TypeScale.X3l, 44.sp, 600),
    val displaySmall: TextStyle = unboundedStyle(TypeScale.X2l, 36.sp, 500),
    val inline: TextStyle = onestStyle(TypeScale.M, 22.sp, 500).copy(fontFeatureSettings = TabularNumbers),
    val inlineStrong: TextStyle = onestStyle(TypeScale.M, 22.sp, 600).copy(fontFeatureSettings = TabularNumbers),
)

internal val LocalMoneyTypography = staticCompositionLocalOf { MoneyTypography() }
