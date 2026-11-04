package com.foldfinance.app.ui.overview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.foldfinance.app.R
import com.foldfinance.app.ui.components.EmptyState
import com.foldfinance.app.ui.format.LocalClock
import com.foldfinance.app.ui.format.monthTitle
import com.foldfinance.app.ui.layout.AppLayout
import com.foldfinance.app.ui.layout.LocalAppLayout
import com.foldfinance.app.ui.layout.TwoPane
import com.foldfinance.app.ui.theme.FoldFinanceTheme
import com.foldfinance.app.ui.theme.FoldShapes
import java.time.LocalDate

/**
 * «Обзор». Until ingestion lands (stages 2–5) there are no operations, so the screen shows an
 * honest empty state — no fake numbers, no placeholder buttons (the action appears once SMS
 * permission onboarding exists).
 */
@Composable
fun OverviewRoute(modifier: Modifier = Modifier) {
    OverviewEmpty(modifier)
}

@Composable
private fun OverviewEmpty(modifier: Modifier, layout: AppLayout = LocalAppLayout.current) {
    val month = monthTitle(LocalDate.now(LocalClock.current))
    when (layout) {
        is AppLayout.Compact -> Column(modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
            MonthHeader(month)
            Spacer(Modifier.height(16.dp))
            SummaryPending()
            // The invitation sits in the lower part of the screen: the thumb zone (docs/design.md).
            Spacer(Modifier.weight(1f))
            OverviewEmptyState()
            Spacer(Modifier.height(24.dp))
        }
        is AppLayout.Book -> TwoPane(
            modifier = modifier,
            first = {
                Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 12.dp, top = 24.dp, bottom = 24.dp)) {
                    MonthHeader(month)
                    Spacer(Modifier.height(16.dp))
                    SummaryPending()
                }
            },
            second = {
                Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 24.dp, top = 24.dp, bottom = 24.dp)) {
                    PaneHeadline(stringResource(R.string.overview_list_title))
                    Spacer(Modifier.weight(1f))
                    OverviewEmptyState()
                    Spacer(Modifier.weight(1f))
                }
            },
        )
        is AppLayout.Tabletop -> TwoPane(
            modifier = modifier,
            first = {
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp)) {
                    MonthHeader(month)
                    Spacer(Modifier.height(16.dp))
                    SummaryPending()
                }
            },
            second = {
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Spacer(Modifier.weight(1f))
                    OverviewEmptyState()
                    Spacer(Modifier.weight(1f))
                }
            },
        )
    }
}

@Composable
private fun MonthHeader(month: String) = PaneHeadline(month)

/** Headline of a pane; both panes of the book spread start with one, on the same baseline. */
@Composable
private fun PaneHeadline(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineLarge,
        color = FoldFinanceTheme.palette.text,
    )
}

/** Where the «Сегодня / Месяц» card will be: an empty card with an honest note, no zeros. */
@Composable
private fun SummaryPending() {
    val palette = FoldFinanceTheme.palette
    Surface(Modifier.fillMaxWidth(), shape = FoldShapes.summary, color = palette.surface) {
        Column(Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.overview_summary_title),
                style = MaterialTheme.typography.titleLarge,
                color = palette.text,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.overview_summary_pending),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textMuted,
            )
        }
    }
}

@Composable
private fun OverviewEmptyState() {
    EmptyState(
        title = stringResource(R.string.overview_empty_title),
        body = stringResource(R.string.overview_empty_body),
        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
    )
}
