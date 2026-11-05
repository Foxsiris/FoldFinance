package com.foldfinance.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.foldfinance.app.R
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes

/**
 * The summary card «Сегодня / Месяц» — the biggest thing on the overview is the number
 * (docs/design.md, «Цифры — это заголовки»).
 *
 * Totals are expenses by known operations; the footnote says so, the card never pretends to show
 * a bank balance (CLAUDE.md, «Остаток»).
 *
 * @param periodTitle month name in the nominative case: «Ноябрь».
 * @param todayMinor today's expenses, kopecks.
 * @param monthMinor month-to-date expenses, kopecks.
 * @param currency ISO 4217 code.
 * @param todayStyle [MoneyStyle.Display] by default, [MoneyStyle.Hero] for the outer screen hero.
 * @param footnote small print under the totals; `null` hides it.
 * @param trailing optional content under the numbers (e.g. the «Разобрать 3» action).
 */
@Composable
fun SummaryCard(
    periodTitle: String,
    todayMinor: Long,
    monthMinor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    todayStyle: MoneyStyle = MoneyStyle.Display,
    todayLabel: String = stringResource(R.string.component_summary_today),
    monthLabel: String = stringResource(R.string.component_summary_month),
    footnote: String? = stringResource(R.string.component_summary_footnote),
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = FoldFinanceTheme.palette
    Surface(modifier = modifier, shape = FoldShapes.summary, color = palette.surface) {
        Column(Modifier.adaptiveHorizontalPadding().padding(top = 22.dp, bottom = 24.dp)) {
            Text(text = periodTitle, style = MaterialTheme.typography.titleLarge, color = palette.text)
            Spacer(Modifier.height(14.dp))
            MoneyText(amountMinor = todayMinor, currency = currency, style = todayStyle)
            Text(text = todayLabel, style = MaterialTheme.typography.bodyLarge, color = palette.textMuted)
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(thickness = 1.dp, color = palette.seam.copy(alpha = 0.7f))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    MoneyText(amountMinor = monthMinor, currency = currency, style = MoneyStyle.DisplaySmall)
                    Text(text = monthLabel, style = MaterialTheme.typography.bodyLarge, color = palette.textMuted)
                }
            }
            if (footnote != null) {
                Spacer(Modifier.height(12.dp))
                Text(text = footnote, style = MaterialTheme.typography.bodySmall, color = palette.textMuted)
            }
            if (trailing != null) {
                Spacer(Modifier.height(16.dp))
                trailing()
            }
        }
    }
}

/**
 * Horizontal padding of a card that shrinks in narrow panes: [wide] normally, [narrow] when the
 * card is narrower than [threshold] (a ~200 dp pane next to the hinge has no room for 24 dp).
 * Works with intrinsic measurements, unlike `BoxWithConstraints`.
 */
fun Modifier.adaptiveHorizontalPadding(
    wide: Dp = 24.dp,
    narrow: Dp = 16.dp,
    threshold: Dp = 300.dp,
): Modifier = layout { measurable, constraints ->
    val pad = (if (constraints.hasBoundedWidth && constraints.maxWidth < threshold.roundToPx()) narrow else wide).roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = -2 * pad))
    layout(placeable.width + 2 * pad, placeable.height) { placeable.placeRelative(pad, 0) }
}
