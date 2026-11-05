package com.foldfinance.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foldfinance.app.DeviceConfig
import com.foldfinance.app.DeviceConfigs
import com.foldfinance.app.ScreenshotClock
import com.foldfinance.app.ai.features.anomalies.Anomaly
import com.foldfinance.app.ai.features.anomalies.AnomalyDetector
import com.foldfinance.app.ai.features.common.AiAvailability
import com.foldfinance.app.ai.features.common.NumberGuard
import com.foldfinance.app.ai.features.query.ChatScope
import com.foldfinance.app.ai.features.query.GroupBy
import com.foldfinance.app.ai.features.query.PeriodSpec
import com.foldfinance.app.ai.features.query.QueryCall
import com.foldfinance.app.ai.features.query.QueryEngine
import com.foldfinance.app.ai.features.query.QueryRejection
import com.foldfinance.app.ai.features.receipt.SplitMode
import com.foldfinance.app.ai.features.summary.InsightText
import com.foldfinance.app.ai.features.summary.MonthlyFactsBuilder
import com.foldfinance.app.ui.ai.anomalies.AnomaliesCardContent
import com.foldfinance.app.ui.ai.anomalies.AnomaliesState
import com.foldfinance.app.ui.ai.chat.AiChatActions
import com.foldfinance.app.ui.ai.chat.AiChatContent
import com.foldfinance.app.ui.ai.chat.AiChatState
import com.foldfinance.app.ui.ai.chat.ChatTurn
import com.foldfinance.app.ui.ai.insight.InsightCardState
import com.foldfinance.app.ui.ai.insight.MonthlyInsightCardContent
import com.foldfinance.app.ui.ai.receipt.EditableLine
import com.foldfinance.app.ui.ai.receipt.ReceiptActions
import com.foldfinance.app.ui.ai.receipt.ReceiptPhase
import com.foldfinance.app.ui.ai.receipt.ReceiptReviewContent
import com.foldfinance.app.ui.ai.receipt.ReceiptUiState
import com.foldfinance.app.ui.ai.voice.VoiceListeningContent
import com.foldfinance.app.ui.ai.voice.VoicePhase
import com.foldfinance.app.ui.analytics.InsightsSamples
import com.foldfinance.app.ui.components.ListGroup
import com.foldfinance.app.ui.components.TransactionRow
import com.foldfinance.app.ui.fakes.SampleData
import com.foldfinance.app.ui.layout.TwoPane
import com.foldfinance.app.ui.layout.isTwoPane
import com.foldfinance.app.ui.transaction.categoryVisualOrNone
import com.foldfinance.app.ui.transaction.formatTime
import com.foldfinance.app.ui.transaction.title
import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.model.Subscription
import com.foldfinance.core.model.SubscriptionPeriod
import com.foldfinance.core.model.SubscriptionStatus
import com.foldfinance.app.captureInAllConfigs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * AI composables on the outer (475×751) and inner (933×704) Fold 8 screens × light/dark, on
 * deterministic data. Model texts are fixed strings passed through [NumberGuard], as in the app.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiScreenshotTest {

    private val configs = listOf(DeviceConfigs.Outer, DeviceConfigs.Inner)
    private val zone = SampleData.zone
    private val today = LocalDate.now(ScreenshotClock)
    private val categories = SampleData.categories
    private val txs = SampleData.transactions

    /** Cards as they sit in a pane: full width outside, one pane wide inside. */
    @Composable
    private fun DeviceConfig.Cards(content: @Composable () -> Unit) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .then(if (layout.isTwoPane) Modifier.widthIn(max = 440.dp) else Modifier.fillMaxWidth()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }

    private fun chatState(): AiChatState {
        val month = Period.month(today, zone)
        val (cur, prev) = PeriodSpec.comparisonPair(month, today)
        val spend = QueryCall.Spend(null, "такси", month)
        val compare = QueryCall.Compare(cur, prev, GroupBy.CATEGORY)
        return AiChatState(
            availability = AiAvailability.Ready,
            // The list scrolls to the newest turn: the taxi answer with its operations is in view.
            turns = listOf(
                ChatTurn.NotUnderstood(1, "Сколько на кофейни?", QueryRejection.UNKNOWN_CATEGORY),
                ChatTurn.Answered(2, "Где я потратил больше, чем в прошлом месяце?", QueryEngine.answer(compare, txs, categories, "RUB")),
                ChatTurn.Answered(3, "Сколько ушло на такси в этом месяце?", QueryEngine.answer(spend, txs, categories, "RUB")),
            ),
            input = "Топ-5 трат за неделю",
            categories = categories,
            today = today,
        )
    }

    @Test fun chat() = captureInAllConfigs("ai_chat", configs) {
        val scope = ChatScope(period = Period.month(today, zone))
        if (layout.isTwoPane) {
            TwoPane(
                first = {
                    Box(Modifier.fillMaxSize().padding(16.dp)) {
                        ListGroup(Modifier.fillMaxWidth()) {
                            txs.take(6).forEach { tx ->
                                TransactionRow(
                                    merchant = tx.title(),
                                    time = formatTime(tx.occurredAt, zone),
                                    amountMinor = tx.amountMinor,
                                    currency = tx.currency,
                                    direction = tx.direction,
                                    category = categoryVisualOrNone(categories.firstOrNull { it.id == tx.categoryId }),
                                    source = tx.source,
                                    needsReview = tx.needsReview,
                                )
                            }
                        }
                    }
                },
                second = { AiChatContent(chatState(), AiChatActions(), scope, Modifier.fillMaxSize()) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AiChatContent(chatState(), AiChatActions(), scope, Modifier.fillMaxSize())
        }
    }

    @Test fun chatEmpty() = captureInAllConfigs("ai_chat_empty", listOf(DeviceConfigs.Outer)) {
        AiChatContent(AiChatState(availability = AiAvailability.Ready, categories = categories, today = today), AiChatActions(), ChatScope(), Modifier.fillMaxSize())
    }

    @Test fun insight() = captureInAllConfigs("ai_insight", configs) {
        val subs = listOf(
            Subscription(1, "Кинопоиск", 29_900, "RUB", SubscriptionPeriod.MONTHLY, ScreenshotClock.instant(), ScreenshotClock.instant(), SubscriptionStatus.CANDIDATE),
        )
        val facts = MonthlyFactsBuilder.build(InsightsSamples.history, InsightsSamples.categories, subs, today, zone, "RUB")
        val model = NumberGuard.render(
            "За {period} вы потратили {total}. По сравнению с прошлым периодом это {total_delta}. Сильнее всего выросли расходы на «{up1_name}».",
            facts.toFacts(),
        ) as NumberGuard.Result.Accepted
        Cards {
            MonthlyInsightCardContent(InsightCardState.Ready(facts, InsightText.Model(model.text)))
            MonthlyInsightCardContent(InsightCardState.Ready(facts, InsightText.Template))
            MonthlyInsightCardContent(InsightCardState.Ready(facts, null))
        }
    }

    @Test fun anomalies() = captureInAllConfigs("ai_anomalies", configs) {
        val tx = SampleData.transactions.first { it.id == 4L } // Кофемания, 380 ₽ at 09:12
        val history = listOf(
            tx.copy(id = 101, occurredAt = tx.occurredAt.minusSeconds(86_400L * 20), amountMinor = 35_000),
            tx.copy(id = 102, occurredAt = tx.occurredAt.minusSeconds(86_400L * 13), amountMinor = 40_000),
            tx.copy(id = 103, occurredAt = tx.occurredAt.minusSeconds(86_400L * 6), amountMinor = 38_000),
            tx,
            tx.copy(id = 104, occurredAt = tx.occurredAt.plusSeconds(240)),
            SampleData.transactions.first { it.id == 11L }.let { plus ->
                plus.copy(id = 105, occurredAt = plus.occurredAt.minusSeconds(86_400L * 31), amountMinor = 29_900)
            },
            SampleData.transactions.first { it.id == 11L },
            tx.copy(id = 106, merchant = "Кофемания", occurredAt = tx.occurredAt.minusSeconds(86_400L * 2), amountMinor = 190_000),
        )
        val list: List<Anomaly> = AnomalyDetector.detect(history, SampleData.subscriptions, ScreenshotClock.instant())
        val explanations = list.filterIsInstance<Anomaly.DoubleCharge>().associate {
            it.key to "В Кофемании дважды списали одинаковую сумму почти подряд — похоже на повтор, проверьте выписку."
        }
        Cards {
            AnomaliesCardContent(AnomaliesState.Ready(list, explanations, explaining = false, today, zone))
        }
    }

    @Test fun receipt() = captureInAllConfigs("ai_receipt", configs) {
        val tx = SampleData.transactions.first { it.id == 1L } // Пятёрочка, 1 240,50 ₽
        val groceries = SampleData.categoryId("groceries")
        val state = ReceiptUiState(
            phase = ReceiptPhase.REVIEW,
            imagesSupported = true,
            transaction = tx,
            lines = listOf(
                EditableLine(1, "Молоко 3,2% 1 л", "89,9", groceries),
                EditableLine(2, "Хлеб бородинский", "54", groceries),
                EditableLine(3, "Сыр российский", "389,6", groceries),
                EditableLine(4, "Средство для посуды", "219", null, categoryUncertain = true),
                EditableLine(5, "Пакет", "", SampleData.categoryId("other")),
            ),
            categories = categories,
            printedTotalMinor = 124_050,
            splitMode = SplitMode.NOTE,
        )
        Box(Modifier.fillMaxSize()) {
            ReceiptReviewContent(state, ReceiptActions(), if (layout.isTwoPane) Modifier.widthIn(max = 520.dp) else Modifier)
        }
    }

    @Test fun voice() = captureInAllConfigs("ai_voice", configs) {
        Cards {
            VoiceListeningContent(VoicePhase.Listening("кофе двести пятьдесят"))
            VoiceListeningContent(VoicePhase.Parsing("кофе двести пятьдесят наличными"))
            VoiceListeningContent(VoicePhase.Error(languageMissing = true))
        }
    }
}
