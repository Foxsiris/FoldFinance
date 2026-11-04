package com.foldfinance.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foldfinance.app.captureInAllConfigs
import com.foldfinance.app.ui.components.CategoryChip
import com.foldfinance.app.ui.components.EmptyState
import com.foldfinance.app.ui.components.ListGroup
import com.foldfinance.app.ui.components.ListGroupDivider
import com.foldfinance.app.ui.components.MoneyStyle
import com.foldfinance.app.ui.components.MoneyText
import com.foldfinance.app.ui.components.Period
import com.foldfinance.app.ui.components.PeriodSelector
import com.foldfinance.app.ui.components.SectionHeader
import com.foldfinance.app.ui.components.SummaryCard
import com.foldfinance.app.ui.components.TransactionRow
import com.foldfinance.app.ui.components.categoryVisual
import com.foldfinance.app.ui.diagnostics.WindowDiagnosticsScreen
import com.foldfinance.app.ui.layout.TwoPane
import com.foldfinance.core.model.DefaultCategories
import com.foldfinance.core.model.Source
import com.foldfinance.parser.Direction
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the component gallery and the diagnostics screen in
 * 3 Fold configurations × light/dark. Record: `recordRoborazziDebug`, check: `verifyRoborazziDebug`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTests {


    @Test fun diagnostics() = captureInAllConfigs("diagnostics") {
        WindowDiagnosticsScreen(readings = readings, layout = layout, copied = false, onCopy = {}, onBack = {})
    }

    /** Component gallery: summary side and list side, split by [TwoPane] on the hinge. */
    @Test fun components() = captureInAllConfigs("components") {
        TwoPane(
            modifier = Modifier.fillMaxSize(),
            first = { GalleryColumn { SummarySide() } },
            second = { GalleryColumn { ListSide() } },
        )
    }

    /** Compact screens show only the first pane of [TwoPane]; the list side gets its own image. */
    @Test fun componentsList() = captureInAllConfigs("components_list", configs = listOf(com.foldfinance.app.DeviceConfigs.Outer)) {
        GalleryColumn { ListSide() }
    }
}

@Composable
private fun GalleryColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummarySide() {
    var period by remember { mutableStateOf(Period.Month) }
    SummaryCard(
        periodTitle = "Ноябрь",
        todayMinor = 124_000,
        monthMinor = 3_890_050,
        currency = "RUB",
        modifier = Modifier.fillMaxWidth(),
    )
    PeriodSelector(selected = period, onSelect = { period = it })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DefaultCategories.all.take(6).forEachIndexed { index, seed ->
            val visual = categoryVisual(seed.key, seed.name, seed.iconKey)
            CategoryChip(name = seed.name, color = visual.color, selected = index == 1, onClick = {})
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MoneyText(amountMinor = 1_240_050, currency = "RUB", style = MoneyStyle.DisplayMedium)
        MoneyText(amountMinor = 5_000_000, currency = "RUB", direction = Direction.INCOME, style = MoneyStyle.DisplaySmall)
        MoneyText(amountMinor = 1_250, currency = "USD", direction = Direction.REFUND, style = MoneyStyle.DisplaySmall)
    }
}

@Composable
private fun ListSide() {
    SectionHeader(title = "Сегодня", supporting = "5 операций", actionLabel = "Все", onAction = {})
    ListGroup(Modifier.fillMaxWidth()) {
        sampleRows.forEachIndexed { index, row ->
            if (index > 0) ListGroupDivider()
            val seed = DefaultCategories.all.first { it.key == row.categoryKey }
            TransactionRow(
                merchant = row.merchant,
                time = row.time,
                amountMinor = row.amountMinor,
                currency = row.currency,
                direction = row.direction,
                category = categoryVisual(seed.key, seed.name, seed.iconKey),
                source = row.source,
                needsReview = row.needsReview,
                selected = index == 0,
                onClick = {},
            )
        }
    }
    EmptyState(
        title = "Операций пока нет",
        body = "Разрешите чтение SMS или добавьте операцию вручную.",
        icon = Icons.Outlined.Sms,
        actionLabel = "Разрешить",
        onAction = {},
    )
}

private data class SampleRow(
    val merchant: String,
    val time: String,
    val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    val categoryKey: String,
    val source: Source,
    val needsReview: Boolean = false,
)

// Test-only sample data: never shipped (CLAUDE.md, «Только реальные данные»).
private val sampleRows = listOf(
    SampleRow("Пятёрочка", "14:05", 124_050, "RUB", Direction.EXPENSE, "groceries", Source.SMS),
    SampleRow("Яндекс Такси", "12:40", 41_900, "RUB", Direction.EXPENSE, "transport", Source.PUSH, needsReview = true),
    SampleRow("Зарплата", "10:00", 9_500_000, "RUB", Direction.INCOME, "other", Source.SMS),
    SampleRow("Возврат Ozon", "09:12", 259_900, "RUB", Direction.REFUND, "shopping", Source.PUSH),
    SampleRow("Перевод себе *4821", "08:30", 1_000_000, "RUB", Direction.INTERNAL_TRANSFER, "transfers", Source.MANUAL),
)
