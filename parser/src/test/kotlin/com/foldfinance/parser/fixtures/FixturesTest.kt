package com.foldfinance.parser.fixtures

import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.foldfinance.parser.ParseResult
import com.foldfinance.parser.ParserRegistry
import com.foldfinance.parser.bank.GenericParser
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Каждая пара `fixtures/<bank>/NNN.txt` ↔ `NNN.expected.json` прогоняется через [ParserRegistry.default]. */
@RunWith(Parameterized::class)
class FixturesTest(private val id: String, private val fixture: Fixture) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): List<Array<Any>> = Fixtures.all()
            .filter { it.txt != null && it.json != null }
            .map { arrayOf(it.id, it) }

        private val registry = ParserRegistry.default(Fixtures.ZONE)
    }

    @Test
    fun matchesExpectation() {
        val expectation = Fixtures.readExpectation(fixture.json!!)
        val text = Fixtures.readText(fixture.txt!!)
        val result = registry.parse(text, Fixtures.toMeta(expectation.meta))

        assertWithMessage("$id: ${expectation.description}")
            .that(Fixtures.describe(result))
            .isEqualTo(Fixtures.body(expectation))

        // parserId совпадает с каталогом — значит, реестр выбрал парсер по отправителю.
        val parserId = (result as? ParseResult.Parsed)?.parserId ?: (result as? ParseResult.Uncertain)?.parserId
        if (parserId != null) assertWithMessage("$id parserId").that(parserId).isEqualTo(fixture.bank)
    }

    @Test
    fun respectsDomainInvariants() {
        val expectation = Fixtures.readExpectation(fixture.json!!)
        val result = registry.parse(Fixtures.readText(fixture.txt!!), Fixtures.toMeta(expectation.meta))
        val ops = when (result) {
            is ParseResult.Parsed -> result.operations
            is ParseResult.Uncertain -> listOfNotNull(result.partial)
            ParseResult.NotFinancial -> emptyList()
        }
        for (op in ops) {
            // INTERNAL_TRANSFER решает :core по списку своих карт, не парсер.
            assertThat(op.direction).isNotEqualTo(Direction.INTERNAL_TRANSFER)
            // Возврат — всегда REFUND/REFUND, никогда не доход.
            if (op.kind == Kind.REFUND || op.direction == Direction.REFUND) {
                assertThat(op.kind).isEqualTo(Kind.REFUND)
                assertThat(op.direction).isEqualTo(Direction.REFUND)
            }
            if (op.kind == Kind.FEE) assertThat(op.direction).isEqualTo(Direction.EXPENSE)
            if (fixture.bank == GenericParser.ID) {
                assertWithMessage("$id generic confidence").that(op.confidence).isAtMost(GenericParser.MAX_CONFIDENCE)
            }
        }
        if (result is ParseResult.Uncertain) {
            result.partial?.let { assertThat(it.confidence).isLessThan(com.foldfinance.parser.ParsedOperation.NEEDS_REVIEW_THRESHOLD) }
        }
    }
}
