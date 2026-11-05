package com.foldfinance.app.ui.ai.anomalies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.foldfinance.app.R
import com.foldfinance.app.ai.LocalAi
import com.foldfinance.app.ai.features.anomalies.Anomaly
import com.foldfinance.app.ai.features.anomalies.AnomalyDetector
import com.foldfinance.app.ai.features.anomalies.AnomalyExplainer
import com.foldfinance.app.ai.features.anomalies.DismissedAnomalies
import com.foldfinance.app.ai.features.anomalies.facts
import com.foldfinance.app.ai.features.common.AiAvailability
import com.foldfinance.app.ai.features.common.toAvailability
import com.foldfinance.app.ui.ai.AiCard
import com.foldfinance.app.ui.ai.AiFootnote
import com.foldfinance.app.ui.ai.AiLoadingCard
import com.foldfinance.app.ui.components.MoneyText
import com.foldfinance.app.ui.transaction.UiClock
import com.foldfinance.app.ui.transaction.formatDate
import com.foldfinance.app.ui.transaction.formatTime
import com.foldfinance.app.ui.transaction.title
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
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** What the anomalies card shows. */
sealed interface AnomaliesState {
    data object Hidden : AnomaliesState
    data class Loading(val availability: AiAvailability.Loading) : AnomaliesState

    /** @property explanations accepted model texts by [Anomaly.key]; missing → template sentence. */
    data class Ready(
        val anomalies: List<Anomaly>,
        val explanations: Map<String, String>,
        val explaining: Boolean,
        val today: LocalDate,
        val zone: ZoneId,
    ) : AnomaliesState
}

/** Subscriptions and anomalies (п.8): [AnomalyDetector] finds, [AnomalyExplainer] explains. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnomaliesViewModel @Inject constructor(
    ai: LocalAi,
    private val explainer: AnomalyExplainer,
    private val dismissed: DismissedAnomalies,
    transactions: TransactionRepository,
    subscriptions: SubscriptionRepository,
    @UiClock private val clock: Clock,
) : ViewModel() {

    private val found = combine(transactions.observe(), subscriptions.observeAll(), dismissed.keys) { txs, subs, hidden ->
        AnomalyDetector.detect(txs, subs, clock.instant()).filterNot { it.key in hidden }.take(MAX_SHOWN)
    }.distinctUntilChanged()

    val state: StateFlow<AnomaliesState> = combine(ai.status, found) { status, list -> status.toAvailability() to list }
        .distinctUntilChanged()
        .flatMapLatest { (availability, list) ->
            when {
                availability is AiAvailability.Loading -> flowOf(AnomaliesState.Loading(availability))
                availability != AiAvailability.Ready || list.isEmpty() -> flowOf(AnomaliesState.Hidden)
                else -> flow {
                    val today = LocalDate.now(clock)
                    emit(AnomaliesState.Ready(list, emptyMap(), explaining = true, today, clock.zone))
                    emit(AnomaliesState.Ready(list, explainer.explain(list), explaining = false, today, clock.zone))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnomaliesState.Hidden)

    fun dismiss(key: String) {
        viewModelScope.launch { dismissed.dismiss(key) }
    }

    private companion object {
        const val MAX_SHOWN = 5
    }
}

/** «Стоит проверить» card: double charges, price rises, unusual amounts. Hidden when there are none. */
@Composable
fun AnomaliesCard(
    modifier: Modifier = Modifier,
    viewModel: AnomaliesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AnomaliesCardContent(state, modifier, onDismiss = viewModel::dismiss)
}

@Composable
fun AnomaliesCardContent(state: AnomaliesState, modifier: Modifier = Modifier, onDismiss: (String) -> Unit = {}) {
    val title = stringResource(R.string.aif_anomalies_title)
    when (state) {
        AnomaliesState.Hidden -> Unit
        is AnomaliesState.Loading -> AiLoadingCard(state.availability, title, modifier)
        is AnomaliesState.Ready -> AiCard(title = title, modifier = modifier) {
            state.anomalies.forEachIndexed { index, anomaly ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AnomalyItem(anomaly, state.explanations[anomaly.key], state, onDismiss)
            }
            if (!state.explaining) AiFootnote(modelText = state.explanations.isNotEmpty())
        }
    }
}

@Composable
private fun AnomalyItem(anomaly: Anomaly, explanation: String?, state: AnomaliesState.Ready, onDismiss: (String) -> Unit) {
    val tx = anomaly.transaction
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        when (anomaly) {
                            is Anomaly.DoubleCharge -> R.string.aif_anomaly_double
                            is Anomaly.PriceIncrease -> R.string.aif_anomaly_price
                            is Anomaly.UnusualAmount -> R.string.aif_anomaly_unusual
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    tx.title() + " · " + formatDate(tx.occurredAt.atZone(state.zone).toLocalDate(), state.today) + ", " + formatTime(tx.occurredAt, state.zone),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            MoneyText(amountMinor = tx.amountMinor, currency = tx.currency, direction = tx.direction)
        }
        Text(
            explanation ?: templateSentence(anomaly),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { onDismiss(anomaly.key) }) { Text(stringResource(R.string.aif_anomaly_ok)) }
    }
}

/** Fixed sentence from the anomaly's own facts. */
@Composable
fun templateSentence(anomaly: Anomaly): String {
    val f = anomaly.facts().associate { it.id to it.value }
    return when (anomaly) {
        is Anomaly.UnusualAmount -> stringResource(R.string.aif_anomaly_unusual_t, f.getValue("amount"), f.getValue("ratio"), f.getValue("typical"))
        is Anomaly.DoubleCharge -> stringResource(R.string.aif_anomaly_double_t, f.getValue("amount"), f.getValue("minutes"))
        is Anomaly.PriceIncrease -> stringResource(R.string.aif_anomaly_price_t, f.getValue("previous"), f.getValue("amount"), f.getValue("delta"))
    }
}
