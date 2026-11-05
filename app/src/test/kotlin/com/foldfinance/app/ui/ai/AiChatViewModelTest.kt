package com.foldfinance.app.ui.ai

import androidx.lifecycle.SavedStateHandle
import com.foldfinance.app.ScreenshotClock
import com.foldfinance.app.ai.AiResponse
import com.foldfinance.app.ai.AiStatus
import com.foldfinance.app.ai.features.FakeLocalAi
import com.foldfinance.app.ai.features.common.AiAvailability
import com.foldfinance.app.ai.features.query.ChatScope
import com.foldfinance.app.ai.features.query.QueryAssistant
import com.foldfinance.app.ai.features.query.QueryRejection
import com.foldfinance.app.ui.ai.chat.AiChatViewModel
import com.foldfinance.app.ui.ai.chat.ChatTurn
import com.foldfinance.app.ui.fakes.FakeCategoryRepository
import com.foldfinance.app.ui.fakes.SampleData
import com.foldfinance.app.ui.fakes.fakePreferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val transactions = SampleData.transactionRepository()

    private fun vm(ai: FakeLocalAi, handle: SavedStateHandle = SavedStateHandle()) = AiChatViewModel(
        handle = handle,
        ai = ai,
        assistant = QueryAssistant(ai),
        transactions = transactions,
        categoryRepository = FakeCategoryRepository(),
        preferences = fakePreferences(),
        clock = ScreenshotClock,
    )

    private val taxiToday = """{"fn":"spend","args":{"merchant":"такси","period":"today"}}"""

    @Test fun `question is answered from the database and survives recreation`() = runTest {
        val handle = SavedStateHandle()
        val ai = FakeLocalAi.answering(taxiToday)
        val chat = vm(ai, handle)
        chat.actions.setInput("Сколько ушло на такси сегодня?")
        chat.actions.send(ChatScope())
        val state = chat.state.first { s -> s.turns.any { it is ChatTurn.Answered } }
        val answered = state.turns.single() as ChatTurn.Answered
        assertThat(answered.question).isEqualTo("Сколько ушло на такси сегодня?")
        assertThat(answered.answer.totals.single().amountMinor).isEqualTo(41_900) // «Яндекс Такси» today in SampleData
        assertThat(state.input).isEmpty()

        // Fold/unfold or process death: a new ViewModel over the same handle shows the same chat
        // without asking the model again.
        val restored = vm(ai, handle).state.first { it.turns.isNotEmpty() }
        assertThat((restored.turns.single() as ChatTurn.Answered).answer.totals.single().amountMinor).isEqualTo(41_900)
        assertThat(ai.requests).hasSize(1)
    }

    @Test fun `unknown category is not understood`() = runTest {
        val chat = vm(FakeLocalAi.answering("""{"fn":"spend","args":{"category":"Кофейни"}}"""))
        chat.actions.setInput("Сколько на кофейни?")
        chat.actions.send(ChatScope())
        val turn = chat.state.first { it.turns.isNotEmpty() && !it.asking }.turns.single()
        assertThat(turn).isInstanceOf(ChatTurn.NotUnderstood::class.java)
        assertThat((turn as ChatTurn.NotUnderstood).reason).isEqualTo(QueryRejection.UNKNOWN_CATEGORY)
    }

    @Test fun `hidden while the model is not ready, also while it downloads`() = runTest {
        val ai = FakeLocalAi(AiStatus.Unavailable("no_aicore"), respond = { AiResponse.Text(taxiToday) })
        val chat = vm(ai)
        assertThat(chat.state.first { it.categories.isNotEmpty() }.availability).isEqualTo(AiAvailability.Hidden)
        ai.state.value = AiStatus.Downloading(50, 200)
        assertThat(chat.state.first { it.categories.isNotEmpty() }.availability).isEqualTo(AiAvailability.Hidden)
        chat.actions.setInput("вопрос")
        assertThat(chat.state.first { it.input == "вопрос" }.canSend).isFalse()
    }

    @Test fun `sheet open state is kept in the handle`() = runTest {
        val handle = SavedStateHandle()
        vm(FakeLocalAi(), handle).actions.setSheetOpen(true)
        assertThat(vm(FakeLocalAi(), handle).state.first { it.categories.isNotEmpty() }.sheetOpen).isTrue()
    }
}
