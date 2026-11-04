package com.foldfinance.app.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foldfinance.app.ui.components.ListGroup
import com.foldfinance.app.ui.components.ListGroupDivider
import com.foldfinance.app.ui.components.SummaryCard
import com.foldfinance.app.ui.components.TransactionRow
import com.foldfinance.app.ui.components.categoryVisual
import com.foldfinance.app.ui.diagnostics.WindowDiagnosticsRoute
import com.foldfinance.app.ui.layout.FoldPreviewSurface
import com.foldfinance.app.ui.layout.FoldPreviews
import com.foldfinance.app.ui.layout.TwoPane
import com.foldfinance.app.ui.shell.Section
import com.foldfinance.app.ui.shell.ShellScaffold
import com.foldfinance.core.model.Source
import com.foldfinance.parser.Direction

// Previews live in the debug source set: their sample data never reaches release
// (CLAUDE.md, «Только реальные данные»).

@FoldPreviews
@Composable
private fun ShellPreview() = FoldPreviewSurface {
    ShellScaffold(sections = Section.entries, selected = Section.Overview, onSelect = {})
}

@FoldPreviews
@Composable
private fun ShellDarkPreview() = FoldPreviewSurface(darkTheme = true) {
    ShellScaffold(sections = Section.entries, selected = Section.Overview, onSelect = {})
}

@FoldPreviews
@Composable
private fun DiagnosticsPreview() = FoldPreviewSurface(darkTheme = true) {
    WindowDiagnosticsRoute(onBack = {})
}

@FoldPreviews
@Composable
private fun TwoPaneComponentsPreview() = FoldPreviewSurface {
    TwoPane(
        modifier = Modifier.fillMaxSize(),
        first = {
            Column(Modifier.padding(20.dp)) {
                SummaryCard(
                    periodTitle = "Ноябрь",
                    todayMinor = 124_000,
                    monthMinor = 3_890_050,
                    currency = "RUB",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        second = {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ListGroup(Modifier.fillMaxWidth()) {
                    TransactionRow(
                        merchant = "Пятёрочка",
                        time = "14:05",
                        amountMinor = 124_050,
                        currency = "RUB",
                        direction = Direction.EXPENSE,
                        category = categoryVisual("groceries", "Продукты", "shopping_basket"),
                        source = Source.SMS,
                    )
                    ListGroupDivider()
                    TransactionRow(
                        merchant = "Зарплата",
                        time = "10:00",
                        amountMinor = 9_500_000,
                        currency = "RUB",
                        direction = Direction.INCOME,
                        category = categoryVisual("other", "Другое", "category"),
                        source = Source.PUSH,
                        needsReview = true,
                    )
                }
            }
        },
    )
}
