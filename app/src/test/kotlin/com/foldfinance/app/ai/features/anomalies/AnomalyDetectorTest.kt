package com.foldfinance.app.ai.features.anomalies

import com.foldfinance.app.ai.AiResponse
import com.foldfinance.app.ai.features.AiData
import com.foldfinance.app.ai.features.AiData.tx
import com.foldfinance.app.ai.features.FakeLocalAi
import com.foldfinance.app.ai.features.common.FactFormat
import com.foldfinance.core.model.Subscription
import com.foldfinance.core.model.SubscriptionPeriod
import com.foldfinance.core.model.SubscriptionStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal

class AnomalyDetectorTest {

    private val now = AiData.now

    private fun detect(txs: List<com.foldfinance.core.model.Transaction>, subs: List<Subscription> = emptyList()) =
        AnomalyDetector.detect(txs, subs, now)

    private fun sub(merchant: String, amount: Long, status: SubscriptionStatus = SubscriptionStatus.CONFIRMED) = Subscription(
        id = 1,
        merchant = merchant,
        amountMinor = amount,
        currency = "RUB",
        period = SubscriptionPeriod.MONTHLY,
        lastChargeAt = now,
        nextChargeAt = now,
        status = status,
    )

    private val usualGroceries = listOf(
        tx("2026-09-20T19:00", 95_000, "Пятёрочка", "groceries"),
        tx("2026-10-01T19:00", 100_000, "PYATEROCHKA 1234", "groceries"),
        tx("2026-10-12T19:00", 110_000, "Пятерочка", "groceries"),
        tx("2026-10-20T19:00", 100_000, "Пятёрочка", "groceries"),
    )

    @Test fun `amount far above the usual one at the merchant`() {
        val big = tx("2026-11-03T19:00", 520_000, "Пятёрочка", "groceries")
        val found = detect(usualGroceries + big)
        assertThat(found).hasSize(1)
        val a = found.single() as Anomaly.UnusualAmount
        assertThat(a.transaction).isEqualTo(big)
        assertThat(a.typicalMinor).isEqualTo(100_000) // median of 95/100/100/110 → (100+100)/2
        assertThat(a.ratio).isEqualTo(BigDecimal("5.2"))
    }

    @Test fun `no verdict without enough history or below the ratio`() {
        assertThat(detect(usualGroceries.take(2) + tx("2026-11-03T19:00", 520_000, "Пятёрочка", "groceries"))).isEmpty()
        assertThat(detect(usualGroceries + tx("2026-11-03T19:00", 290_000, "Пятёрочка", "groceries"))).isEmpty()
    }

    @Test fun `same amount at the same merchant within ten minutes`() {
        val a = tx("2026-11-04T09:12", 38_000, "Кофемания", "restaurants")
        val b = tx("2026-11-04T09:18", 38_000, "Кофемания", "restaurants")
        val found = detect(listOf(a, b))
        assertThat(found).containsExactly(Anomaly.DoubleCharge(a, b, 6))
        assertThat(found.single().key).isEqualTo("double:${a.id}-${b.id}")
    }

    @Test fun `not a double charge`() {
        val a = tx("2026-11-04T09:12", 38_000, "Кофемания", "restaurants")
        assertThat(detect(listOf(a, tx("2026-11-04T09:30", 38_000, "Кофемания", "restaurants")))).isEmpty() // 18 minutes
        assertThat(detect(listOf(a, tx("2026-11-04T09:14", 38_500, "Кофемания", "restaurants")))).isEmpty() // other amount
        assertThat(detect(listOf(a, tx("2026-11-04T09:14", 38_000, "Шоколадница", "restaurants")))).isEmpty() // other merchant
        // Without merchants, the same card decides.
        val c = tx("2026-11-04T10:00", 150_000, null, null, card = "*1111")
        val d = tx("2026-11-04T10:03", 150_000, null, null, card = "*1111")
        assertThat(detect(listOf(c, d))).hasSize(1)
        assertThat(detect(listOf(c, d.copy(cardMask = "*2222")))).isEmpty()
    }

    @Test fun `subscription price increase`() {
        val charges = listOf(
            tx("2026-09-03T09:00", 39_900, "Яндекс Плюс", "subscriptions"),
            tx("2026-10-03T09:00", 39_900, "Яндекс Плюс", "subscriptions"),
            tx("2026-11-03T09:00", 49_900, "Яндекс Плюс", "subscriptions"),
        )
        val found = detect(charges, listOf(sub("Яндекс Плюс", 39_900)))
        val a = found.single() as Anomaly.PriceIncrease
        assertThat(a.previousMinor).isEqualTo(39_900)
        assertThat(a.deltaMinor).isEqualTo(10_000)
        // A dismissed subscription is not watched.
        assertThat(detect(charges, listOf(sub("Яндекс Плюс", 39_900, SubscriptionStatus.DISMISSED)))).isEmpty()
    }

    @Test fun `old events are outside the window`() {
        val a = tx("2026-09-04T09:12", 38_000, "Кофемания", "restaurants")
        val b = tx("2026-09-04T09:15", 38_000, "Кофемания", "restaurants")
        assertThat(detect(listOf(a, b))).isEmpty()
    }

    @Test fun `explanations pass the number guard or fall back`() = runTest {
        val a = tx("2026-11-04T09:12", 38_000, "Кофемания", "restaurants")
        val b = tx("2026-11-04T09:18", 38_000, "Кофемания", "restaurants")
        val big = tx("2026-11-03T19:00", 520_000, "Пятёрочка", "groceries")
        val list = detect(usualGroceries + listOf(a, b, big))
        assertThat(list).hasSize(2)
        val ai = FakeLocalAi(respond = {
            AiResponse.Text(
                """{"items":[
                  {"id":"a1","text":"В {merchant} дважды списали {amount} с разницей {minutes} мин — проверьте."},
                  {"id":"a2","text":"Покупка на 5 200 ₽ — в пять раз больше обычного."}
                ]}""",
            )
        })
        val texts = AnomalyExplainer(ai).explain(list)
        val double = list.first { it is Anomaly.DoubleCharge }
        assertThat(texts[double.key]).isEqualTo("В Кофемания дважды списали ${FactFormat.money(38_000, "RUB")} с разницей 6 мин — проверьте.")
        // The second text wrote its own numbers: rejected, the card shows the template.
        assertThat(texts).hasSize(1)
    }

    @Test fun `facts are formatted by Kotlin`() {
        val big = tx("2026-11-03T19:00", 520_000, "Пятёрочка", "groceries")
        val facts = detect(usualGroceries + big).single().facts().associate { it.id to it.value }
        assertThat(facts["ratio"]).isEqualTo("5,2")
        assertThat(facts["merchant"]).isEqualTo("Пятёрочка")
    }
}
