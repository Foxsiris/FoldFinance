package com.foldfinance.app.ai.features.anomalies

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.foldfinance.app.ai.AiRequest
import com.foldfinance.app.ai.AiResponse
import com.foldfinance.app.ai.AiTier
import com.foldfinance.app.ai.LocalAi
import com.foldfinance.app.ai.features.common.AiJson
import com.foldfinance.app.ai.features.common.NumberGuard
import com.foldfinance.app.ai.features.common.array
import com.foldfinance.app.ai.features.common.text
import com.foldfinance.app.ai.features.di.AiFeaturesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Human-language explanations of anomalies. One FAST request for the whole list:
 * `{"items":[{"id":"a1","text":"…{merchant}…{amount}…"}]}`; each text goes through
 * [NumberGuard] against that anomaly's own facts. Missing or rejected texts are absent from the
 * result — the card then shows its template sentence. Kept in memory only.
 */
@Singleton
class AnomalyExplainer @Inject constructor(private val ai: LocalAi) {
    private val cache = ConcurrentHashMap<String, String>()

    /** Explanations by [Anomaly.key]; only accepted texts. */
    suspend fun explain(anomalies: List<Anomaly>): Map<String, String> {
        val missing = anomalies.filterNot { cache.containsKey(it.key) }
        if (missing.isNotEmpty()) {
            val response = ai.generate(
                AiRequest(
                    prompt = prompt(missing),
                    system = SYSTEM,
                    tier = AiTier.FAST,
                    temperature = 0.2f,
                    maxOutputTokens = 120 * missing.size,
                    jsonOnly = true,
                ),
            )
            if (response is AiResponse.Text) cache.putAll(accept(response.text, missing))
        }
        return anomalies.mapNotNull { a -> cache[a.key]?.let { a.key to it } }.toMap()
    }

    companion object {
        val SYSTEM: String = """
            Объясни каждое замеченное отклонение в расходах одним спокойным предложением по-русски.
            Ответ — только JSON: {"items":[{"id":"a1","text":"..."}]}.
            Никаких цифр и сумм — только плейсхолдеры фактов этого пункта в фигурных скобках, например {amount}.
            Не пугай, не обвиняй, не советуй звонить в банк; можно предложить проверить операцию.
        """.trimIndent()

        fun kind(a: Anomaly): String = when (a) {
            is Anomaly.UnusualAmount -> "сумма у мерчанта намного больше обычной"
            is Anomaly.DoubleCharge -> "одинаковое списание дважды за несколько минут"
            is Anomaly.PriceIncrease -> "подписка подорожала"
        }

        fun prompt(list: List<Anomaly>): String = buildString {
            list.forEachIndexed { i, a ->
                appendLine("a${i + 1}: ${kind(a)}. Факты: " + a.facts().joinToString("; ") { "{${it.id}} = ${it.value}" })
            }
        }

        /** Validated texts from the model answer, by [Anomaly.key]. */
        fun accept(modelText: String, list: List<Anomaly>): Map<String, String> {
            val items = AiJson.parseObject(modelText)?.array("items") ?: return emptyMap()
            val out = HashMap<String, String>()
            for (item in items) {
                val obj = item as? JsonObject ?: continue
                val index = obj.text("id")?.removePrefix("a")?.toIntOrNull()?.minus(1) ?: continue
                val anomaly = list.getOrNull(index) ?: continue
                val text = obj.text("text") ?: continue
                val result = NumberGuard.render(text, anomaly.facts())
                if (result is NumberGuard.Result.Accepted) out[anomaly.key] = result.text
            }
            return out
        }
    }
}

/** Anomalies the user marked «это нормально». Stores only keys (operation ids), no amounts. */
@Singleton
class DismissedAnomalies @Inject constructor(
    @AiFeaturesStore private val store: DataStore<Preferences>,
) {
    private val key = stringSetPreferencesKey("dismissed_anomalies")

    val keys: Flow<Set<String>> = store.data.map { it[key].orEmpty() }

    suspend fun dismiss(anomalyKey: String) {
        store.edit { prefs -> prefs[key] = (prefs[key].orEmpty() + anomalyKey).toList().takeLast(MAX_KEYS).toSet() }
    }

    private companion object {
        const val MAX_KEYS = 500
    }
}
