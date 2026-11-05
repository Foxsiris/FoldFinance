package com.foldfinance.app.ui.transaction

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.foldfinance.app.ScreenshotClock
import com.foldfinance.app.ui.fakes.FakeCategoryRepository
import com.foldfinance.app.ui.fakes.FakeTransactionRepository
import com.foldfinance.app.ui.fakes.SampleData
import com.foldfinance.app.ui.fakes.fakePreferences
import com.foldfinance.app.prefs.AppPreferences
import com.foldfinance.core.model.Source
import com.foldfinance.parser.Direction
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionFormViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        handle: SavedStateHandle,
        repo: FakeTransactionRepository = FakeTransactionRepository(),
        preferences: AppPreferences = fakePreferences(),
    ) = TransactionFormViewModel(handle, repo, FakeCategoryRepository(), preferences, ScreenshotClock)

    private fun TransactionFormViewModel.type(text: String) = text.forEach { ch ->
        press(if (ch == ',') KeypadKey.Decimal else KeypadKey.Digit(ch - '0'))
    }

    @Test fun `unsaved input survives recreation through SavedStateHandle`() = runTest {
        val handle = SavedStateHandle()
        val first = vm(handle)
        first.openAdd()
        first.type("1240,5")
        first.setDirection(Direction.INCOME)
        first.toggleCategory(3L)
        first.setMerchant("Пятёрочка")

        // Activity recreated («Не сохранять действия»): a new ViewModel over the saved handle.
        val restored = vm(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
        restored.state.test {
            val state = awaitItem()
            assertThat(state.mode).isEqualTo(FormMode.Add)
            assertThat(state.draft.amountText).isEqualTo("1240,5")
            assertThat(state.draft.amountMinor).isEqualTo(124_050L)
            assertThat(state.draft.directionValue).isEqualTo(Direction.INCOME)
            assertThat(state.draft.categoryId).isEqualTo(3L)
            assertThat(state.draft.merchant).isEqualTo("Пятёрочка")
        }
    }

    @Test fun `opening add again keeps the draft, closing drops it`() {
        val handle = SavedStateHandle()
        val vm = vm(handle)
        vm.openAdd()
        vm.type("500")
        vm.openAdd() // e.g. the widget «+» while the form is already open
        assertThat(vm.state.value.draft.amountText).isEqualTo("500")
        vm.close()
        assertThat(vm.state.value.isOpen).isFalse()
        vm.openAdd()
        assertThat(vm.state.value.draft.amountText).isEmpty()
    }

    @Test fun `save adds a manual operation in exact kopecks and reports it`() = runTest {
        val repo = FakeTransactionRepository()
        val vm = vm(SavedStateHandle(), repo)
        vm.formEvents.test {
            vm.openAdd()
            vm.type("0,1")
            vm.setNote("  обед ")
            assertThat(vm.state.value.canSave).isTrue()
            vm.save()
            val event = awaitItem() as FormEvent.Saved
            assertThat(event.added).isTrue()
        }
        val input = repo.added.single()
        assertThat(input.amountMinor).isEqualTo(10L)
        assertThat(input.direction).isEqualTo(Direction.EXPENSE)
        assertThat(input.note).isEqualTo("обед")
        assertThat(input.occurredAt).isEqualTo(ScreenshotClock.instant())
        assertThat(vm.state.value.isOpen).isFalse()
    }

    @Test fun `zero amount cannot be saved`() {
        val repo = FakeTransactionRepository()
        val vm = vm(SavedStateHandle(), repo)
        vm.openAdd()
        vm.type("0")
        assertThat(vm.state.value.canSave).isFalse()
        vm.save()
        assertThat(repo.added).isEmpty()
    }

    @Test fun `edit prefills from the database and saves a user edit`() = runTest {
        val repo = SampleData.transactionRepository()
        val handle = SavedStateHandle()
        val vm = vm(handle, repo)
        vm.openEdit(1L)
        assertThat(vm.state.value.mode).isEqualTo(FormMode.Edit(1L))
        assertThat(vm.state.value.draft.amountText).isEqualTo("1240,5")
        assertThat(vm.state.value.draft.merchant).isEqualTo("Пятёрочка")

        vm.press(KeypadKey.Backspace)
        vm.press(KeypadKey.Backspace)
        vm.setOccurredAt(Instant.parse("2026-11-04T09:00:00Z"))
        vm.save()

        val saved = repo.updated.single()
        assertThat(saved.id).isEqualTo(1L)
        assertThat(saved.amountMinor).isEqualTo(124_000L)
        assertThat(saved.occurredAt).isEqualTo(Instant.parse("2026-11-04T09:00:00Z"))
        assertThat(saved.source).isEqualTo(Source.SMS)
        assertThat(repo.items.value.first { it.id == 1L }.userEdited).isTrue()
    }

    @Test fun `a new manual operation takes the currency from the settings`() = runTest {
        val preferences = fakePreferences(currency = "EUR")
        val repo = FakeTransactionRepository()
        val vm = vm(SavedStateHandle(), repo, preferences)
        vm.openAdd()
        assertThat(vm.state.value.draft.currency).isEqualTo("EUR")
        vm.type("12")
        vm.save()
        assertThat(repo.items.value.single().currency).isEqualTo("EUR")

        // Changed in «Настройки → Валюта»: the next add form uses the new one.
        preferences.setDefaultCurrency("KZT")
        vm.openAdd()
        assertThat(vm.state.value.draft.currency).isEqualTo("KZT")
    }
}
