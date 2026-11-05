package com.foldfinance.app.ui.review

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.foldfinance.app.ScreenshotClock
import com.foldfinance.app.ui.fakes.FakeCategoryRepository
import com.foldfinance.app.ui.fakes.FakeMerchantRuleRepository
import com.foldfinance.app.ui.fakes.FakeTransactionRepository
import com.foldfinance.core.data.repository.UnrecognizedMessagesRepository
import com.foldfinance.core.model.ParseStatus
import com.foldfinance.core.model.RawMessage
import com.foldfinance.parser.MessageChannel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** «Не распознано» in Review: listed, hidden, turned into a manual operation. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewUnrecognizedTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeUnrecognized(initial: List<RawMessage>) : UnrecognizedMessagesRepository {
        val messages = MutableStateFlow(initial)
        val hidden = mutableListOf<Long>()
        val attached = mutableListOf<Pair<Long, Long>>()

        override fun observeUnrecognized(): Flow<List<RawMessage>> =
            messages.map { list -> list.filter { it.id !in hidden && it.transactionId == null } }

        override suspend fun hide(rawMessageId: Long) {
            hidden += rawMessageId
            messages.update { it.toList() }
        }

        override suspend fun attachToTransaction(rawMessageId: Long, transactionId: Long): Boolean {
            attached += rawMessageId to transactionId
            messages.update { list -> list.map { if (it.id == rawMessageId) it.copy(transactionId = transactionId) else it } }
            return true
        }
    }

    private fun message(id: Long, text: String) = RawMessage(
        id = id,
        channel = MessageChannel.SMS,
        sender = "900",
        sourceKey = "$id|0|0",
        receivedAt = Instant.parse("2026-11-05T09:00:00Z").plusSeconds(id),
        text = text,
        textHash = "h$id",
        parseStatus = ParseStatus.UNCERTAIN,
        parserVersion = 2,
        transactionId = null,
    )

    private fun vm(handle: SavedStateHandle, repo: FakeUnrecognized) =
        ReviewViewModel(handle, FakeTransactionRepository(), FakeCategoryRepository(), FakeMerchantRuleRepository(), ScreenshotClock, repo)

    @Test fun `unrecognized messages are listed and can be hidden`() = runTest {
        val repo = FakeUnrecognized(listOf(message(1, "Операция 1"), message(2, "Операция 2")))
        val vm = vm(SavedStateHandle(), repo)
        vm.state.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            assertThat(state.unrecognized.map { it.id }).containsExactly(1L, 2L)

            vm.openUnrecognized()
            assertThat(expectMostRecentItem().unrecognizedOpen).isTrue()

            vm.hideUnrecognized(1L)
            assertThat(expectMostRecentItem().unrecognized.map { it.id }).containsExactly(2L)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repo.hidden).containsExactly(1L)
    }

    @Test fun `manual add from an unrecognized message attaches it to the saved operation, across recreation`() = runTest {
        val repo = FakeUnrecognized(listOf(message(7, "Списание 1 500 RUB")))
        val handle = SavedStateHandle()
        val first = vm(handle, repo)
        first.state.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        val prefill = first.startManualAdd(7L)
        assertThat(prefill?.text).isEqualTo("Списание 1 500 RUB")

        // The form survives recreation (fold/unfold); so does the pending message.
        val restored = vm(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }), repo)
        restored.onManualAdded(transactionId = 42L)

        assertThat(repo.attached).containsExactly(7L to 42L)
        restored.state.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            assertThat(state.unrecognized).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
        // A later unrelated add does not attach anything.
        restored.onManualAdded(transactionId = 43L)
        assertThat(repo.attached).hasSize(1)
    }

    @Test fun `without the repository the section is empty`() = runTest {
        val vm = ReviewViewModel(SavedStateHandle(), FakeTransactionRepository(), FakeCategoryRepository(), FakeMerchantRuleRepository(), ScreenshotClock)
        vm.state.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            assertThat(state.unrecognized).isEmpty()
            assertThat(state.unrecognizedOpen).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
