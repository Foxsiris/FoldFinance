package com.foldfinance.app.ai.features.voice

import com.foldfinance.app.ai.AiTier
import com.foldfinance.app.ai.features.AiData
import com.foldfinance.app.ai.features.FakeLocalAi
import com.foldfinance.app.ui.ai.voice.toTransactionDraft
import com.foldfinance.parser.Direction
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class VoiceParserTest {

    private val restaurants = AiData.category("restaurants").id

    private fun build(transcript: String, model: String?) = VoiceParser.build(transcript, model, AiData.categories, "RUB")

    @Test fun `coffee two hundred fifty cash`() {
        val d = build("кофе двести пятьдесят наличными", """{"amount_text":"двести пятьдесят","what":"кофе","category":"Рестораны"}""")
        assertThat(d.amountMinor).isEqualTo(25_000)
        assertThat(d.merchant).isEqualTo("Кофе")
        assertThat(d.categoryId).isEqualTo(restaurants)
        assertThat(d.isCash).isTrue()
        assertThat(d.direction).isEqualTo(Direction.EXPENSE)
        assertThat(d.uncertain).isFalse()
    }

    @Test fun `a fragment that is not in the phrase is ignored and the draft is flagged`() {
        val d = build("кофе двести пятьдесят наличными", """{"amount_text":"триста","what":"латте","category":"Кофейни"}""")
        assertThat(d.amountMinor).isEqualTo(25_000) // Kotlin's own reading of the phrase
        assertThat(d.uncertain).isTrue()
        assertThat(d.merchant).isEqualTo("Кофе") // «латте» was never said
        assertThat(d.categoryId).isNull() // not one of the user's categories
    }

    @Test fun `without the model the Kotlin scan is used`() {
        val d = build("такси 1 500 картой", null)
        assertThat(d.amountMinor).isEqualTo(150_000)
        assertThat(d.merchant).isEqualTo("Такси")
        assertThat(d.isCash).isFalse()
        assertThat(d.uncertain).isFalse()
    }

    @Test fun `no amount heard`() {
        val d = build("купил кофе", """{"amount_text":null,"what":"кофе"}""")
        assertThat(d.amountMinor).isNull()
        assertThat(d.uncertain).isTrue()
    }

    @Test fun `model and Kotlin disagree when the phrase has two numbers`() {
        val d = build("два кофе по сто пятьдесят", """{"amount_text":"сто пятьдесят","what":"кофе"}""")
        assertThat(d.amountMinor).isEqualTo(15_000)
        // «два» is also a number in the phrase; the user is asked to check rather than told a total.
        assertThat(d.uncertain).isTrue()
    }

    @Test fun `income words`() {
        assertThat(build("получил долг пятьсот", null).direction).isEqualTo(Direction.INCOME)
    }

    @Test fun `parse uses the fast tier and the form draft keeps the exact amount`() = runTest {
        val ai = FakeLocalAi.answering("""{"amount_text":"тысяча двести","what":"продукты","category":"Продукты"}""")
        val d = VoiceParser(ai).parse("продукты тысяча двести наличкой", AiData.categories, "RUB")
        assertThat(ai.requests.single().tier).isEqualTo(AiTier.FAST)
        val draft = d.toTransactionDraft("Наличные")
        assertThat(draft.amountText).isEqualTo("1200")
        assertThat(draft.amountMinor).isEqualTo(120_000)
        assertThat(draft.note).isEqualTo("Наличные")
        assertThat(draft.categoryId).isEqualTo(AiData.category("groceries").id)
    }
}
