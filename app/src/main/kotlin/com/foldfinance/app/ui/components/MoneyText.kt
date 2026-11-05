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
import androidx.compose.ui.text.font.FontWeight
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
 * Size/role of a [MoneyText]. Every style is Onest 600 with tabular digits.
 *
 * @property minorScale size of the kopecks relative to the integer part: 60 % for big amounts,
 *   100 % in rows.
 * @property currencyScale size of the currency sign: big amounts keep the sign at full size but
 *   quiet («23 640 ₽» on the System board), rows likewise.
 * @property quietMinor kopecks and the sign in the quiet colour (big amounts) or in the amount's
 *   own colour (rows, where «−1 500 ₽» reads as one word).
 * @property groupSpacing extra tracking after the narrow no-break space between digit groups:
 *   with −4.5 % tracking the narrow space alone is too tight.
 * @property shrinkToFit the amount scales down (whole, keeping proportions) when it does not fit
 *   the width — big sizes in narrow panes. Row amounts are never squeezed.
 */
enum class MoneyStyle(
    val minorScale: Float,
    val currencyScale: Float,
    val quietMinor: Boolean,
    val groupSpacing: TextUnit,
    val shrinkToFit: Boolean,
) {
    /** 60 — tabletop hero. */
    Hero(0.6f, 1f, quietMinor = true, groupSpacing = 0.12.em, shrinkToFit = true),

    /** 48 — «Потрачено за месяц» of the outer screen. */
    Display(0.6f, 1f, quietMinor = true, groupSpacing = 0.12.em, shrinkToFit = true),

    /** 42 — review card, detail of an operation. */
    DisplayMedium(0.6f, 1f, quietMinor = true, groupSpacing = 0.12.em, shrinkToFit = true),

    /** 30 — the selected day, secondary big numbers. */
    DisplaySmall(0.65f, 1f, quietMinor = true, groupSpacing = 0.1.em, shrinkToFit = true),

    /** 22 — numbers on tiles («Сегодня»). */
    Tile(0.7f, 1f, quietMinor = false, groupSpacing = 0.08.em, shrinkToFit = true),

    /** 15 / 600 — amounts in rows. */
    Inline(1f, 1f, quietMinor = false, groupSpacing = 0.1.em, shrinkToFit = false),

    /** 15 / 700 — totals inside lists. */
    InlineStrong(1f, 1f, quietMinor = false, groupSpacing = 0.1.em, shrinkToFit = false),
}

/** Smallest size a [MoneyStyle.shrinkToFit] amount goes down to, relative to its style size. */
private const val MinShrinkFraction = 0.4f

/** Big amounts never get smaller than this, even in the narrowest window. */
private val MinShrinkSize = 15.sp

/**
 * An amount of money, typeset the Obsidian way: integer part with tabular digits and tight
 * tracking, kopecks smaller, the currency sign quiet («23 640 ₽»), `,00` hidden, ru-RU grouping
 * with a narrow no-break space.
 *
 * The amount is never cut and never wraps: it is one line, and big styles
 * ([MoneyStyle.shrinkToFit]) scale down as a whole to the available width (Compose
 * `TextAutoSize.StepBased`); kopecks and the sign are sized in `em` of the integer part.
 *
 * Colour follows [direction]: expense — neutral text (spending is normal, not an alarm), income —
 * green, refund — soft neutral, internal transfer — muted. `null` direction means a neutral total.
 *
 * @param amountMinor amount in minor units (kopecks). Usually positive; [direction] carries the
 *   meaning. Pass a negative amount to show «−» (rows of expenses).
 * @param currency ISO 4217 code.
 * @param direction money direction relative to the user; drives colour and the default sign.
 * @param style size/role, see [MoneyStyle].
 * @param signDisplay sign policy; by default `+` for income and refunds, `−` only when negative.
 * @param color overrides the direction colour.
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
    val minorColor = if (style.quietMinor) moneyMinorColor(mainColor) else mainColor
    val parts = remember(amountMinor, currency, signDisplay, hideZeroFraction) {
        MoneyFormat.parts(amountMinor, currency, signDisplay, hideZeroFraction)
    }
    val text = remember(parts, mainColor, minorColor, style) {
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
            // Relative sizes: under auto-size the integer part changes size, the rest follows it.
            parts.fraction?.let {
                withStyle(SpanStyle(fontSize = style.minorScale.em, color = minorColor)) { append(it) }
            }
            withStyle(
                SpanStyle(
                    fontSize = style.currencyScale.em,
                    color = minorColor,
                    fontWeight = if (style.quietMinor) FontWeight.Medium else null,
                ),
            ) {
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

/** Default sign: `+` for money coming in (income, refund), `−` only for negative amounts. */
fun defaultSignDisplay(direction: Direction?): SignDisplay = when (direction) {
    Direction.INCOME, Direction.REFUND -> SignDisplay.Always
    else -> SignDisplay.NegativeOnly
}

/**
 * Amount of a list row as the mockups show it: expenses with «−», income and refunds with «+»,
 * transfers between own cards without a sign.
 */
fun signedRowAmount(amountMinor: Long, direction: Direction): Long =
    if (direction == Direction.EXPENSE) -amountMinor else amountMinor

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
        MoneyStyle.Tile -> money.tile
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
        Direction.INCOME -> palette.income
        Direction.REFUND -> palette.refund
        Direction.INTERNAL_TRANSFER -> palette.textMuted
    }
}

@Composable
@ReadOnlyComposable
private fun moneyMinorColor(main: Color): Color {
    val palette = FoldFinanceTheme.palette
    return if (main == palette.text) palette.textDisabled else main.copy(alpha = 0.6f)
}
