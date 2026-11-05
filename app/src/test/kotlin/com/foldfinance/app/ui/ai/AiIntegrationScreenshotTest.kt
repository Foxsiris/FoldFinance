@file:OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)

package com.foldfinance.app.ui.ai

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foldfinance.app.DeviceConfigs
import com.foldfinance.app.ScreenshotClock
import com.foldfinance.app.ai.features.anomalies.AnomalyDetector
import com.foldfinance.app.ai.features.common.AiAvailability
import com.foldfinance.app.ai.features.common.NumberGuard
import com.foldfinance.app.ai.features.query.ChatScope
import com.foldfinance.app.ai.features.query.QueryCall
import com.foldfinance.app.ai.features.query.QueryEngine
import com.foldfinance.app.ai.features.summary.InsightText
import com.foldfinance.app.ai.features.summary.MonthlyFactsBuilder
import com.foldfinance.app.captureInAllConfigs
import com.foldfinance.app.ui.Shell
import com.foldfinance.app.ui.ai.anomalies.AnomaliesCardContent
import com.foldfinance.app.ui.ai.anomalies.AnomaliesState
import com.foldfinance.app.ui.ai.chat.AiChatActions
import com.foldfinance.app.ui.ai.chat.AiChatContent
import com.foldfinance.app.ui.ai.chat.AiChatLauncherButton
import com.foldfinance.app.ui.ai.chat.AiChatState
import com.foldfinance.app.ui.ai.chat.ChatTurn
import com.foldfinance.app.ui.ai.insight.InsightCardState
import com.foldfinance.app.ui.ai.insight.MonthlyInsightCardContent
import com.foldfinance.app.ui.ai.receipt.ReceiptActionButton
import com.foldfinance.app.ui.analytics.FakeSubscriptionRepository
import com.foldfinance.app.ui.analytics.InsightsSamples
import com.foldfinance.app.ui.fakes.FakeBalanceRepository
import com.foldfinance.app.ui.fakes.FakeCategoryRepository
import com.foldfinance.app.ui.fakes.FakeMerchantRuleRepository
import com.foldfinance.app.ui.fakes.NoopDetailActions
import com.foldfinance.app.ui.fakes.NoopFormActions
import com.foldfinance.app.ui.fakes.SampleData
import com.foldfinance.app.ui.fakes.ViewModelHarness
import com.foldfinance.app.ui.fakes.fakePreferences
import com.foldfinance.app.ui.history.HistoryFilterActions
import com.foldfinance.app.ui.history.HistoryPeriod
import com.foldfinance.app.ui.history.HistoryScreen
import com.foldfinance.app.ui.history.HistoryViewModel
import com.foldfinance.app.ui.history.rememberHistoryNavigator
import com.foldfinance.app.ui.overview.CategorySelection
import com.foldfinance.app.ui.overview.OverviewActions
import com.foldfinance.app.ui.overview.OverviewScreen
import com.foldfinance.app.ui.overview.OverviewViewModel
import com.foldfinance.app.ui.shell.Section
import com.foldfinance.app.ui.components.Period as UiPeriod
import com.foldfinance.app.ui.transaction.TransactionFormState
import com.foldfinance.app.ui.transaction.observeDetail
import com.foldfinance.core.data.repository.TransactionSort
import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.model.TransactionSplit
import com.foldfinance.parser.Direction
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The local model built into the screens (cards, chat, detail additions) inside the real shell,
 * outer and inner screen × light/dark. AI parts get fixed states, as the real composables would
 * with a ready model; model texts go through [NumberGuard] as in the app.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiIntegrationScreenshotTest {
    private val harness = ViewModelHarness()
    private val now = ScreenshotClock.instant()
    private val zone = ScreenshotClock.zone
    private val today = LocalDate.now(ScreenshotClock)
    private val configs = listOf(DeviceConfigs.Outer, DeviceConfigs.Inner)

    @Before fun setUp() = harness.install()

    @After fun tearDown() = harness.reset()

    /** «Главная» outside: «Спросить» next to search, the summary and «Стоит проверить» after the tiles; inside — in the summary panel. */
    @Test fun overview() {
        val state = harness.snapshot(
            OverviewViewModel(
                SavedStateHandle(), SampleData.transactionRepository(), FakeCategoryRepository(), FakeBalanceRepository(SampleData.balances),
                FakeSubscriptionRepository(SampleData.subscriptions), fakePreferences(), ScreenshotClock,
            ).state,
        )
        val insight = insightState()
        val anomalies = anomaliesState()
        captureInAllConfigs("overview_ai", configs) {
            Shell(Section.Overview) { m ->
                OverviewScreen(
                    state, closedForm, NoopFormActions, NoopOverview, now, zone, m,
                    aiCards = { card ->
                        MonthlyInsightCardContent(insight, card)
                        AnomaliesCardContent(anomalies, card)
                    },
                    chatAction = { mod -> AiChatLauncherButton(onClick = {}, modifier = mod) },
                )
            }
        }
    }

    /** History on the book: the list and the chat in the right pane («Деталь / Чат»), scoped to the filter. */
    @Test fun historyChat() {
        val handle = SavedStateHandle()
        val vm = historyViewModel(handle)
        vm.setPeriod(HistoryPeriod.MONTH)
        val state = harness.snapshot(vm.state)
        val scope = state.filter.chatScope(now, zone)
        captureInAllConfigs("history_chat", listOf(DeviceConfigs.Inner)) {
            Shell(Section.History) { m ->
                HistoryScreen(
                    state, rememberHistoryNavigator(initialSelection = 1L), rememberLazyListState(), { null }, NoopFilters,
                    NoopDetailActions, closedForm, NoopFormActions, closedForm, NoopFormActions, {}, { _, _ -> }, now, zone, m,
                    chatAvailable = true,
                    chatShown = true,
                    chat = { mod -> AiChatContent(chatState(), AiChatActions(), scope, mod) },
                )
            }
        }
    }

    /**
     * Detail of an operation the model parsed and categorised, split by a receipt: «Чек», the AI
     * tags, the parts with the rest, the merchant under its display name (tap to rename).
     */
    @Test fun detail() {
        val repo = SampleData.transactionRepository()
        val base = requireNotNull(harness.first(repo.observeDetail(1L, FakeCategoryRepository())))
        val tx = base.transaction
        val detail = base.copy(
            transaction = tx.copy(
                aiParsed = true,
                aiCategory = true,
                merchantDisplayName = "Пятёрочка у дома",
                splits = listOf(
                    TransactionSplit(1, tx.id, SampleData.categoryId("shopping"), 21_900, "Средство для посуды"),
                    TransactionSplit(2, tx.id, SampleData.categoryId("health"), 35_000, "Пластырь, витамины"),
                ),
            ),
        )
        val state = harness.snapshot(historyViewModel().state)
        val hooks = AiHooks(receiptButton = { ReceiptActionButton(onClick = {}) }, renameMerchant = { _, _ -> })
        captureInAllConfigs("detail_ai", configs) {
            CompositionLocalProvider(LocalAiHooks provides hooks) {
                Shell(Section.History) { m ->
                    HistoryScreen(
                        state, rememberHistoryNavigator(initialSelection = 1L), rememberLazyListState(), { detail }, NoopFilters,
                        NoopDetailActions, closedForm, NoopFormActions, closedForm, NoopFormActions, {}, { _, _ -> }, now, zone, m,
                        chatAvailable = true,
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private val closedForm = TransactionFormState(categories = SampleData.categories)

    private fun historyViewModel(handle: SavedStateHandle = SavedStateHandle()) =
        HistoryViewModel(handle, SampleData.transactionRepository(), FakeCategoryRepository(), FakeMerchantRuleRepository(), fakePreferences(), ScreenshotClock)

    private fun insightState(): InsightCardState {
        val facts = MonthlyFactsBuilder.build(InsightsSamples.history, InsightsSamples.categories, emptyList(), today, zone, "RUB")
        val model = NumberGuard.render(
            "За {period} вы потратили {total}. По сравнению с прошлым периодом это {total_delta}. Сильнее всего выросли расходы на «{up1_name}».",
            facts.toFacts(),
        ) as NumberGuard.Result.Accepted
        return InsightCardState.Ready(facts, InsightText.Model(model.text))
    }

    private fun anomaliesState(): AnomaliesState {
        val tx = SampleData.transactions.first { it.id == 4L }
        val history = listOf(
            tx.copy(id = 101, occurredAt = tx.occurredAt.minusSeconds(86_400L * 20), amountMinor = 35_000),
            tx.copy(id = 102, occurredAt = tx.occurredAt.minusSeconds(86_400L * 13), amountMinor = 40_000),
            tx.copy(id = 103, occurredAt = tx.occurredAt.minusSeconds(86_400L * 6), amountMinor = 38_000),
            tx,
            tx.copy(id = 104, occurredAt = tx.occurredAt.plusSeconds(240)),
        )
        val list = AnomalyDetector.detect(history, SampleData.subscriptions, now)
        return AnomaliesState.Ready(list, emptyMap(), explaining = false, today, zone)
    }

    private fun chatState(): AiChatState {
        val month = Period.month(today, zone)
        val spend = QueryCall.Spend(null, "такси", month)
        return AiChatState(
            availability = AiAvailability.Ready,
            turns = listOf(ChatTurn.Answered(1, "Сколько ушло на такси в этом месяце?", QueryEngine.answer(spend, SampleData.transactions, SampleData.categories, "RUB"))),
            categories = SampleData.categories,
            today = today,
        )
    }

    private object NoopOverview : OverviewActions {
        override fun setPeriod(period: UiPeriod) = Unit
        override fun toggleCategory(selection: CategorySelection) = Unit
        override fun toggleBar(key: Long) = Unit
        override fun clearSelection() = Unit
        override fun openTransaction(transactionId: Long) = Unit
        override fun openReview() = Unit
        override fun openHistory() = Unit
        override fun openSubscriptions() = Unit
        override fun add() = Unit
    }

    private object NoopFilters : HistoryFilterActions {
        override fun setQuery(query: String) = Unit
        override fun setPeriod(period: HistoryPeriod) = Unit
        override fun setSort(sort: TransactionSort) = Unit
        override fun toggleDirection(direction: Direction) = Unit
        override fun toggleCategoryFilter(categoryId: Long) = Unit
        override fun toggleUncategorized() = Unit
        override fun clearCategoryFilter() = Unit
        override fun setAmountRange(min: Long?, max: Long?) = Unit
        override fun resetFilters() = Unit
    }
}
