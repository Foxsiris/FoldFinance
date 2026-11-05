package com.foldfinance.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldfinance.app.ui.theme.FoldFinanceTheme

/**
 * Title of a list section in sentence case (no upper-case «eyebrows», docs/design.md), with an
 * optional supporting line and an optional trailing text action («Все», «Разобрать»).
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val palette = FoldFinanceTheme.palette
    Row(
        modifier = modifier
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val hasAction = actionLabel != null && onAction != null
            val titleStyle = MaterialTheme.typography.titleLarge
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = titleStyle,
                color = palette.text,
                // Next to an action the title stays one line and shrinks a little in a narrow
                // window («Последние операции · Вся история» on a 337 dp outer screen).
                maxLines = if (hasAction) 1 else Int.MAX_VALUE,
                softWrap = !hasAction,
                autoSize = if (hasAction) TextAutoSize.StepBased(minFontSize = 15.sp, maxFontSize = titleStyle.fontSize, stepSize = 0.5.sp) else null,
            )
            if (supporting != null) {
                Text(text = supporting, style = MaterialTheme.typography.bodyMedium, color = palette.textMuted)
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelLarge, color = palette.saffron)
            }
        }
    }
}
