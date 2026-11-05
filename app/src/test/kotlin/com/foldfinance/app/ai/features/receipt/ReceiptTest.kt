package com.foldfinance.app.ai.features.receipt

import com.foldfinance.app.ai.features.AiData
import com.foldfinance.app.ui.fakes.FakeTransactionRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReceiptTest {

    private val groceries = AiData.category("groceries").id
    private val other = AiData.category("other").id

    @Test fun `lines are copied exactly and categories validated`() {
        val scan = ReceiptParser.parse(
            """
            {"items":[
              {"name":"Молоко 3,2%","amount":"89.90","category":"Продукты"},
              {"name":"Порошок","amount":"349,00","category":"Бытовая химия"},
              {"name":"Хлеб","amount":null,"category":"продукты"},
              {"name":"  ","amount":"10"}
            ],"total":"1 240,50"}
            """.trimIndent(),
            AiData.categories,
        )!!
        assertThat(scan.lines.map { it.name }).containsExactly("Молоко 3,2%", "Порошок", "Хлеб").inOrder()
        assertThat(scan.lines.map { it.amountMinor }).containsExactly(8_990L, 34_900L, null).inOrder()
        assertThat(scan.lines.map { it.categoryId }).containsExactly(groceries, null, groceries).inOrder()
        assertThat(scan.lines[1].categoryUncertain).isTrue()
        assertThat(scan.printedTotalMinor).isEqualTo(124_050)
    }

    @Test fun `amounts that are not exact positive money become null`() {
        assertThat(ReceiptParser.amountMinor("12.345")).isNull()
        assertThat(ReceiptParser.amountMinor("-5")).isNull()
        assertThat(ReceiptParser.amountMinor("0")).isNull()
        assertThat(ReceiptParser.amountMinor("abc")).isNull()
        assertThat(ReceiptParser.amountMinor("1 299 ₽")).isEqualTo(129_900)
        assertThat(ReceiptParser.amountMinor("99,9")).isEqualTo(9_990)
    }

    @Test fun `not a receipt`() {
        assertThat(ReceiptParser.parse("""{"items":[]}""", AiData.categories)).isNull()
        assertThat(ReceiptParser.parse("Это фото кота", AiData.categories)).isNull()
    }

    @Test fun `split sums are Kotlin and grouped by category`() {
        val parent = AiData.tx("2026-11-05T13:42", 100_000, "Пятёрочка", "groceries")
        val plan = ReceiptSplitPlanner.plan(
            parent,
            listOf(
                ReceiptSplitPlanner.Line("Молоко", 8_990, groceries),
                ReceiptSplitPlanner.Line("Сыр", 34_900, groceries),
                ReceiptSplitPlanner.Line("Губки", 20_000, other),
                ReceiptSplitPlanner.Line("Пакет", null, other),
            ),
        )
        assertThat(plan.itemsTotalMinor).isEqualTo(63_890)
        assertThat(plan.remainderMinor).isEqualTo(36_110)
        assertThat(plan.parts.map { it.categoryId to it.amountMinor }).containsExactly(groceries to 43_890L, other to 20_000L).inOrder()
        assertThat(plan.isValid).isTrue()

        val over = ReceiptSplitPlanner.plan(parent, listOf(ReceiptSplitPlanner.Line("Телевизор", 150_000, other)))
        assertThat(over.remainderMinor).isEqualTo(-50_000)
        assertThat(over.isValid).isFalse()
    }

    @Test fun `interim store writes the breakdown to the note without touching the amount`() = runTest {
        val parent = AiData.tx("2026-11-05T13:42", 100_000, "Пятёрочка", "groceries", id = 7).copy(note = "к ужину")
        val repo = FakeTransactionRepository(listOf(parent))
        val store = NoteReceiptSplitStore(repo)
        val plan = ReceiptSplitPlanner.plan(parent, listOf(ReceiptSplitPlanner.Line("Сыр", 40_000, groceries)))
        store.save(plan, "Продукты — 400 ₽; остальное — 600 ₽")
        val saved = repo.observeById(7).first()!!
        assertThat(saved.amountMinor).isEqualTo(100_000)
        assertThat(saved.categoryId).isEqualTo(groceries)
        assertThat(saved.note).isEqualTo("к ужину\nЧек: Продукты — 400 ₽; остальное — 600 ₽")

        // A second receipt replaces the receipt block, the user's own note stays.
        store.save(ReceiptSplitPlanner.plan(saved, listOf(ReceiptSplitPlanner.Line("Сыр", 50_000, groceries))), "Продукты — 500 ₽")
        assertThat(repo.observeById(7).first()!!.note).isEqualTo("к ужину\nЧек: Продукты — 500 ₽")
    }
}
