package com.foldfinance.app.ui.categories

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.foldfinance.app.ui.analytics.FakeCategoryRepository
import com.foldfinance.app.ui.analytics.awaitUntil
import com.foldfinance.app.ui.components.categoryPaletteArgb
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

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val repository = FakeCategoryRepository()

    @Test fun `new category is validated, saved and announced`() = runTest {
        val vm = CategoriesViewModel(SavedStateHandle(), repository)
        vm.eventFlow.test {
            vm.state.test {
                awaitUntil { !it.isLoading }
                vm.startNew()
                awaitUntil { it.draft != null }

                vm.save()
                assertThat(awaitUntil { it.draft?.nameError == true }.draft!!.id).isNull()

                vm.updateName("  Путешествия ")
                vm.updateIcon("flight")
                vm.updateColor(categoryPaletteArgb(1))
                vm.save()
                val saved = awaitUntil { it.draft == null }
                val added = saved.categories.last()
                assertThat(added.name).isEqualTo("Путешествия")
                assertThat(added.iconKey).isEqualTo("flight")
                assertThat(added.colorArgb).isEqualTo(categoryPaletteArgb(1))
                assertThat(added.isSystem).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(awaitItem()).isEqualTo(CategoriesEvent.Saved)
        }
    }

    @Test fun `editing a base category renames it and changes icon and colour`() = runTest {
        val vm = CategoriesViewModel(SavedStateHandle(), repository)
        vm.state.test {
            val groceries = awaitUntil { !it.isLoading }.categories.first { it.key == "groceries" }
            vm.edit(groceries)
            assertThat(awaitUntil { it.draft != null }.draft!!.name).isEqualTo("Продукты")
            vm.updateName("Еда домой")
            vm.updateIcon("local_cafe")
            vm.updateColor(null)
            vm.save()
            val after = awaitUntil { it.draft == null }.categories.first { it.id == groceries.id }
            assertThat(after.name).isEqualTo("Еда домой")
            assertThat(after.iconKey).isEqualTo("local_cafe")
            assertThat(after.colorArgb).isNull()
            assertThat(after.key).isEqualTo("groceries")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `unsaved input survives recreation`() = runTest {
        val handle = SavedStateHandle()
        val vm = CategoriesViewModel(handle, repository)
        vm.state.test {
            awaitUntil { !it.isLoading }
            vm.startNew()
            vm.updateName("Хобби")
            vm.updateIcon("brush")
            awaitUntil { it.draft?.iconKey == "brush" }
            cancelAndIgnoreRemainingEvents()
        }
        val restored = CategoriesViewModel(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }), repository)
        restored.state.test {
            val draft = awaitUntil { !it.isLoading }.draft
            assertThat(draft).isEqualTo(CategoryDraft(id = null, name = "Хобби", iconKey = "brush", colorArgb = null))
            restored.cancel()
            assertThat(awaitItem().draft).isNull()
        }
        assertThat(repository.rows.value.none { it.name == "Хобби" }).isTrue()
    }
}
