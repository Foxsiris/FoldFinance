package com.foldfinance.app.ui.fakes

import com.foldfinance.app.ui.transaction.KeypadKey
import com.foldfinance.app.ui.transaction.TransactionDetailActions
import com.foldfinance.app.ui.transaction.TransactionFormActions
import com.foldfinance.parser.Direction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.time.Instant

/**
 * Builds screen states with the real ViewModels over fakes, synchronously: `Dispatchers.Main`
 * is an unconfined test dispatcher, so `viewModelScope` work runs eagerly, and [snapshot]
 * subscribes, lets everything settle on the virtual clock and returns the current value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelHarness {
    val scheduler = TestCoroutineScheduler()

    fun install() = Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))

    fun reset() = Dispatchers.resetMain()

    fun <T> snapshot(flow: StateFlow<T>): T {
        val job = Job()
        CoroutineScope(UnconfinedTestDispatcher(scheduler) + job).launch { flow.collect { } }
        scheduler.advanceUntilIdle()
        val value = flow.value
        job.cancel()
        return value
    }

    fun <T> first(flow: Flow<T>): T = runBlocking { flow.first() }
}

/** Callbacks that do nothing, for stateless screens in tests. */
object NoopFormActions : TransactionFormActions {
    override fun press(key: KeypadKey) = Unit
    override fun setDirection(direction: Direction) = Unit
    override fun toggleCategory(categoryId: Long) = Unit
    override fun setMerchant(merchant: String) = Unit
    override fun setNote(note: String) = Unit
    override fun setOccurredAt(instant: Instant) = Unit
    override fun save() = Unit
    override fun close() = Unit
}

object NoopDetailActions : TransactionDetailActions {
    override fun setCategory(transactionId: Long, categoryId: Long?) = Unit
    override fun confirm(transactionId: Long) = Unit
    override fun edit(transactionId: Long) = Unit
    override fun delete(transactionId: Long) = Unit
}

