package com.foldfinance.app.ui.ai.insight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.foldfinance.app.R
import com.foldfinance.app.ui.ai.AiProgress
import com.foldfinance.app.ai.LocalAi
import com.foldfinance.app.ai.features.common.AiAvailability
import com.foldfinance.app.ai.features.common.FactFormat
import com.foldfinance.app.ai.features.common.toAvailability
import com.foldfinance.app.ai.features.summary.InsightText
import com.foldfinance.app.ai.features.summary.MonthlyFacts
import com.foldfinance.app.ai.features.summary.MonthlyFactsBuilder
import com.foldfinance.app.ai.features.summary.MonthlyInsightGenerator
import com.foldfinance.app.prefs.AppPreferences
import com.foldfinance.app.ui.ai.AiCard
import com.foldfinance.app.ui.ai.AiFootnote
import com.foldfinance.app.ui.ai.AiLoadingCard
import com.foldfinance.app.ui.transaction.UiClock
import com.foldfinance.core.data.repository.CategoryRepository
import com.foldfinance.core.data.repository.SubscriptionRepository
import com.foldfinance.core.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** What the monthly card shows. */
sealed interface InsightCardState {
    data object Hidden : InsightCardState
    data class Loading(val availability: AiAvailability.Loading) : InsightCardState

    /** @property text `null` while the model is writing. */
    data class Ready(val facts: MonthlyFacts, val text: InsightText?) : InsightCardState
}

/**
 * Monthly summary (п.7): Kotlin computes [MonthlyFacts]; the model writes 3–4 sentences about
 * them only ([MonthlyInsightGenerator], cached per facts in memory).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MonthlyInsightViewModel @Inject constructor(
    ai: LocalAi,
    generator: MonthlyInsightGenerator,
    transactions: TransactionRepository,
    categories: CategoryRepository,
    subscriptions: SubscriptionRepository,
    preferences: AppPreferences,
    @UiClock clock: Clock,
) : ViewModel() {

    private val facts = combine(
        transactions.observe(),
        categories.observeAll(),
        subscriptions.observeAll(),
        preferences.defaultCurrency,
    ) { txs, cats, subs, currency ->
        MonthlyFactsBuilder.build(txs, cats, subs, LocalDate.now(clock), clock.zone, currency)
    }.distinctUntilChanged()

    val state: StateFlow<InsightCardState> = combine(ai.status, facts) { status, f -> status.toAvailability() to f }
        .distinctUntilChanged()
        .flatMapLatest { (availability, f) ->
            when {
                availability is AiAvailability.Loading -> flowOf(InsightCardState.Loading(availability))
                availability != AiAvailability.Ready || !f.hasData -> flowOf(InsightCardState.Hidden)
                else -> flow {
                    emit(InsightCardState.Ready(f, null))
                    emit(InsightCardState.Ready(f, generator.generate(f)))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightCardState.Hidden)
}

/** Monthly summary card for the main screen. Hidden unless the model is ready and there is data. */
@Composable
fun MonthlyInsightCard(
    modifier: Modifier = Modifier,
    viewModel: MonthlyInsightViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MonthlyInsightCardContent(state, modifier)
}

@Composable
fun MonthlyInsightCardContent(state: InsightCardState, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.aif_insight_title)
    when (state) {
        InsightCardState.Hidden -> Unit
        is InsightCardState.Loading -> AiLoadingCard(state.availability, title, modifier)
        is InsightCardState.Ready -> AiCard(title = title, modifier = modifier) {
            Text(
                FactFormat.period(state.facts.current),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val text = state.text) {
                null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.aif_insight_writing), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AiProgress(Modifier.fillMaxWidth())
                }
                is InsightText.Model -> Text(text.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                InsightText.Template -> Text(templateText(state.facts), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            if (state.text != null) AiFootnote(modelText = state.text is InsightText.Model)
        }
    }
}

/** The summary without the model: the same facts in fixed sentences. */
@Composable
fun templateText(facts: MonthlyFacts): String {
    val cur = facts.currency
    val total = facts.total
    val sentences = mutableListOf<String>()
    val period = FactFormat.period(facts.current)
    val prev = FactFormat.period(facts.previous)
    val spent = FactFormat.money(total.currentMinor, cur)
    val delta = FactFormat.money(kotlin.math.abs(total.deltaMinor), cur)
    sentences += when {
        total.deltaMinor > 0 -> stringResource(R.string.aif_insight_t_more, period, spent, delta, prev)
        total.deltaMinor < 0 -> stringResource(R.string.aif_insight_t_less, period, spent, delta, prev)
        else -> stringResource(R.string.aif_insight_t_same, period, spent, prev)
    }
    facts.increases.firstOrNull()?.let { c ->
        sentences += stringResource(
            R.string.aif_insight_t_up,
            c.category?.name ?: stringResource(R.string.tx_no_category),
            FactFormat.signedMoney(c.change.deltaMinor, cur),
        )
    }
    facts.decreases.firstOrNull()?.let { c ->
        sentences += stringResource(
            R.string.aif_insight_t_down,
            c.category?.name ?: stringResource(R.string.tx_no_category),
            FactFormat.signedMoney(c.change.deltaMinor, cur),
        )
    }
    facts.newSubscriptions.forEach { s ->
        sentences += stringResource(R.string.aif_insight_t_sub, s.merchant, FactFormat.money(s.amountMinor, s.currency))
    }
    return sentences.joinToString(" ")
}
