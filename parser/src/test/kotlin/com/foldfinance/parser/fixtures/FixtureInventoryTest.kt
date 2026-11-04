package com.foldfinance.parser.fixtures

import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Целостность набора фикстур: пары полные, ничего лишнего, обязательные кейсы есть. */
class FixtureInventoryTest {

    private val all = Fixtures.all()

    @Test
    fun everyTxtHasExpectedJson() {
        val missing = all.filter { it.json == null }.map { it.id }
        assertWithMessage(".txt без .expected.json").that(missing).isEmpty()
    }

    @Test
    fun everyExpectedJsonHasTxt() {
        val missing = all.filter { it.txt == null }.map { it.id }
        assertWithMessage(".expected.json без .txt").that(missing).isEmpty()
    }

    @Test
    fun noStrayFilesInBankDirs() {
        assertThat(Fixtures.strayFiles().map { it.path }).isEmpty()
    }

    @Test
    fun allBanksPresentWithEnoughFixtures() {
        val perBank = all.groupingBy { it.bank }.eachCount()
        assertThat(perBank.keys).containsExactly("sber", "tbank", "sovcombank", "wbbank", "generic")
        perBank.forEach { (bank, count) -> assertWithMessage(bank).that(count).isAtLeast(6) }
        assertThat(all.size).isAtLeast(30)
    }

    @Test
    fun mandatoryCasesAreCovered() {
        val expectations = all.mapNotNull { it.json }.map(Fixtures::readExpectation)
        val ops = expectations.flatMap { it.operations }
        fun has(direction: Direction, kind: Kind) = ops.any { it.direction == direction.name && it.kind == kind.name }

        assertWithMessage("возврат").that(has(Direction.REFUND, Kind.REFUND)).isTrue()
        assertWithMessage("комиссия отдельной операцией").that(
            expectations.any { e -> e.operations.size >= 2 && e.operations.any { it.kind == "FEE" } },
        ).isTrue()
        assertWithMessage("валютная покупка").that(ops.any { it.kind == "PURCHASE" && it.currency != "RUB" }).isTrue()
        assertWithMessage("перевод на свою карту").that(
            ops.any { it.kind == "TRANSFER" && it.direction == "EXPENSE" && it.counterpartyCardMask != null },
        ).isTrue()
        assertWithMessage("зачисление").that(has(Direction.INCOME, Kind.TOP_UP)).isTrue()
        assertWithMessage("снятие наличных").that(has(Direction.EXPENSE, Kind.CASH_WITHDRAWAL)).isTrue()
        assertWithMessage("подписка").that(has(Direction.EXPENSE, Kind.SUBSCRIPTION)).isTrue()
        assertWithMessage("NotFinancial").that(expectations.count { it.result == "NOT_FINANCIAL" }).isAtLeast(4)
        assertWithMessage("Uncertain").that(expectations.count { it.result == "UNCERTAIN" }).isAtLeast(4)
        assertWithMessage("push").that(expectations.count { it.meta.channel == "PUSH" }).isAtLeast(3)
    }
}
