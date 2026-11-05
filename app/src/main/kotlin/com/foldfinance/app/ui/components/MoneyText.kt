package com.foldfinance.app.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.app.ui.format.SignDisplay
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.parser.Direction

/**
 * Size/role of a [MoneyText].
 *
 * Display variants use Unbounded (big numbers are the headlines of a screen); inline variants
 * use Onest with tabular digits so that amounts in a column line up.
 *
 * @property minorScale size of kopecks and currency sign relative to the integer part:
 *   55% for display sizes (docs/design.md), 80% inline where 55% would be unreadable.
 * @property groupSpacing extra tracking after the narrow no-break space between digit groups:
 *   tabular digits are wide, so inline amounts need a slightly wider group gap to stay legible.
 * @property shrinkToFit the amount scales down (whole, keeping the proportions of the integer
 *   part, kopecks and the sign) when it does not fit the width — display sizes in narrow panes.
 *   Inline amounts are never squeezed: rows give them their intrinsic width.
 */
enum class MoneyStyle(val minorScale: Float, val groupSpacing: TextUnit, val shrinkToFit: Boolean) {
    /** Unbounded 60 — one hero number per screen at most. */
    Hero(0.55f, 0.08.em, shrinkToFit = true),

    /** Unbounded 48 — «Сегодня» on the summary card. */
    Display(0.55f, 0.08.em, shrinkToFit = true),

    /** Unbounded 38. */
    DisplayMedium(0.55f, 0.08.em, shrinkToFit = true),

    /** Unbounded 30 — month total, secondary big numbers. */
    DisplaySmall(0.55f, 0.08.em, shrinkToFit = true),

    /** Onest 15 tnum — amounts in rows. */
    Inline(0.8f, 0.16.em, shrinkToFit = false),

    /** Onest 15 tnum SemiBold — totals inside lists. */
    InlineStrong(0.8f, 0.16.em, shrinkToFit = false),
}

/** Smallest size a [MoneyStyle.shrinkToFit] amount goes down to, relative to its style size. */
private const val MinShrinkFraction = 0.4f

/** Display amounts never get smaller than this, even in the narrowest window. */
private val MinShrinkSize = 15.sp

/**
 * An amount of money, typeset the FoldFinance way: integer part large, kopecks and the currency
 * sign smaller and muted, `,00` hidden, ru-RU grouping with a narrow no-break space
 * (`1 240,50 ₽`).
 *
 * The amount is never cut and never wraps: it is one line, and display styles
 * ([MoneyStyle.shrinkToFit]) scale down as a whole to the available width (Compose
 * `TextAutoSize.StepBased`); the kopecks and the currency sign are sized in `em` of the integer
 * part, so they keep the 55 % proportion at any size.
 *
 * Colour follows [direction]: expense — neutral text (spending is normal, not an alarm), income —
 * mint, refund — lavender, internal transfer — muted. `null` direction means a neutral total.
 *
 * @param amountMinor amount in minor units (kopecks). Usually positive; [direction] carries the meaning.
 * @param currency ISO 4217 code.
 * @param direction money direction relative to the user; drives colour and the default sign.
 * @param style size/role, see [MoneyStyle].
 * @param signDisplay sign policy; by default `+` for income and refunds, nothing for expenses.
 * @param color overrides the direction colour (the minor part derives its muted colour from it).
 * @param hideZeroFraction drop `,00`.
 */
@Composable
fun MoneyText(
    amountMinor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    direction: Direction? = null,
    style: MoneyStyle = MoneyStyle.Inline,
    signDisplay: SignDisplay = defaultSignDisplay(direction),
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    hideZeroFraction: Boolean = true,
) {
    val textStyle = moneyTextStyle(style)
    val mainColor = if (color.isSpecified) color else moneyColor(direction)
    val minorColor = moneyMinorColor(mainColor)
    val parts = remember(amountMinor, currency, signDisplay, hideZeroFraction) {
        MoneyFormat.parts(amountMinor, currency, signDisplay, hideZeroFraction)
    }
    val text = remember(parts, mainColor, minorColor, style) {
        // Relative size: under auto-size the integer part changes size, the minor part follows it.
        val minor = SpanStyle(fontSize = style.minorScale.em, color = minorColor)
        buildAnnotatedString {
            withStyle(SpanStyle(color = mainColor)) {
                append(parts.sign)
                parts.integer.forEach { ch ->
                    if (ch == MoneyFormat.NARROW_NBSP && style.groupSpacing.value > 0f) {
                        withStyle(SpanStyle(letterSpacing = style.groupSpacing)) { append(ch) }
                    } else {
                        append(ch)
                    }
                }
            }
            withStyle(minor) {
                parts.fraction?.let { append(it) }
                append(parts.separator)
                append(parts.currency)
            }
        }
    }
    val described = modifier.semantics { contentDescription = parts.toString() }
    if (style.shrinkToFit) {
        val maxSize = textStyle.fontSize
        val minSize = (maxSize.value * MinShrinkFraction).coerceAtLeast(MinShrinkSize.value).coerceAtMost(maxSize.value).sp
        // Line height in em, so a smaller amount does not keep the full-size line box.
        val lineHeight = if (textStyle.lineHeight.isSpecified && maxSize.isSpecified) {
            (textStyle.lineHeight.value / maxSize.value).em
        } else {
            textStyle.lineHeight
        }
        BasicText(
            text = text,
            modifier = described,
            style = textStyle.copy(color = mainColor, lineHeight = lineHeight, textAlign = textAlign ?: TextAlign.Unspecified),
            overflow = TextOverflow.Clip,
            softWrap = false,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = maxSize, stepSize = 0.5.sp),
        )
    } else {
        Text(
            text = text,
            modifier = described,
            style = textStyle,
            textAlign = textAlign,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Default sign: `+` for money coming in (income, refund), none otherwise. */
fun defaultSignDisplay(direction: Direction?): SignDisplay = when (direction) {
    Direction.INCOME, Direction.REFUND -> SignDisplay.Always
    else -> SignDisplay.NegativeOnly
}

/** Text style of a [MoneyStyle]. */
@Composable
@ReadOnlyComposable
fun moneyTextStyle(style: MoneyStyle): TextStyle {
    val money = FoldFinanceTheme.money
    return when (style) {
        MoneyStyle.Hero -> money.hero
        MoneyStyle.Display -> money.display
        MoneyStyle.DisplayMedium -> money.displayMedium
        MoneyStyle.DisplaySmall -> money.displaySmall
        MoneyStyle.Inline -> money.inline
        MoneyStyle.InlineStrong -> money.inlineStrong
    }
}

/** Colour of an amount by direction (see [MoneyText]). */
@Composable
@ReadOnlyComposable
fun moneyColor(direction: Direction?): Color {
    val palette = FoldFinanceTheme.palette
    return when (direction) {
        null, Direction.EXPENSE -> palette.text
        Direction.INCOME -> palette.mint
        Direction.REFUND -> palette.lavender
        Direction.INTERNAL_TRANSFER -> palette.textMuted
    }
}

@Composable
@ReadOnlyComposable
private fun moneyMinorColor(main: Color): Color {
    val palette = FoldFinanceTheme.palette
    return if (main == palette.text) palette.textMuted else main.copy(alpha = 0.72f)
}
