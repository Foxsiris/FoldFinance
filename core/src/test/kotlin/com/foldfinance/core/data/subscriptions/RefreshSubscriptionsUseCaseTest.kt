package com.foldfinance.core.data.subscriptions

import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.fixedClock
import com.foldfinance.core.data.inMemoryDb
import com.foldfinance.core.data.repository.ManualTransactionInput
import com.foldfinance.core.data.repository.SubscriptionRepositoryImpl
import com.foldfinance.core.data.repository.TransactionRepositoryImpl
import com.foldfinance.core.model.SubscriptionPeriod
import com.foldfinance.core.model.SubscriptionStatus
import com.foldfinance.parser.Direction
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class RefreshSubscriptionsUseCaseTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private lateinit var db: FoldFinanceDatabase
    private val subscriptions get() = SubscriptionRepositoryImpl(db)

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    private fun at(date: String, time: String = "09:00"): Instant =
        LocalDate.parse(date).atTime(java.time.LocalTime.parse(time)).atZone(zone).toInstant()

    private suspend fun charge(date: String, amountMinor: Long, merchant: String = "Яндекс Плюс") {
        TransactionRepositoryImpl(db, fixedClock()).addManual(
            ManualTransactionInput(
                occurredAt = at(date),
                amountMinor = amountMinor,
                currency = "RUB",
                direction = Direction.EXPENSE,
                merchant = merchant,
            ),
        )
    }

    private fun useCase(now: Instant) =
        RefreshSubscriptionsUseCase(TransactionRepositoryImpl(db, fixedClock(now)), subscriptions, fixedClock(now))

    private suspend fun monthlyPlus() {
        charge("2026-08-03", 39_900)
        charge("2026-09-03", 39_900)
        charge("2026-10-03", 39_900)
    }

    @Test
    fun monthlySeries_becomesCandidate_withNextCharge() = runTest {
        monthlyPlus()
        charge("2026-10-10", 120_000, merchant = "Пятёрочка")

        val report = useCase(at("2026-10-20")).invoke(zone)

        assertThat(report).isEqualTo(RefreshSubscriptionsReport(detected = 1, inserted = 1, updated = 0, removed = 0))
        val saved = subscriptions.observeAll().first().single()
        assertThat(saved.status).isEqualTo(SubscriptionStatus.CANDIDATE)
        assertThat(saved.merchant).isEqualTo("Яндекс Плюс")
        assertThat(saved.amountMinor).isEqualTo(39_900)
        assertThat(saved.period).isEqualTo(SubscriptionPeriod.MONTHLY)
        assertThat(saved.lastChargeAt).isEqualTo(at("2026-10-03"))
        assertThat(saved.nextChargeAt).isEqualTo(at("2026-11-03"))
    }

    @Test
    fun repeatedRefresh_isIdempotent() = runTest {
        monthlyPlus()
        val useCase = useCase(at("2026-10-20"))

        useCase(zone)
        val first = subscriptions.observeAll().first()
        val second = useCase(zone)
        useCase(zone)

        assertThat(second).isEqualTo(RefreshSubscriptionsReport(detected = 1, inserted = 0, updated = 0, removed = 0))
        assertThat(subscriptions.observeAll().first()).isEqualTo(first)
    }

    @Test
    fun candidate_followsNewCharges() = runTest {
        monthlyPlus()
        useCase(at("2026-10-20"))(zone)
        val id = subscriptions.observeAll().first().single().id

        charge("2026-11-03", 42_900) // подорожание в пределах допуска детектора
        val report = useCase(at("2026-11-05"))(zone)

        assertThat(report.updated).isEqualTo(1)
        val saved = subscriptions.observeAll().first().single()
        assertThat(saved.id).isEqualTo(id)
        assertThat(saved.lastChargeAt).isEqualTo(at("2026-11-03"))
        assertThat(saved.nextChargeAt).isEqualTo(at("2026-12-03"))
    }

    @Test
    fun confirmed_keepsUserDecision_butScheduleMovesOn() = runTest {
        monthlyPlus()
        useCase(at("2026-10-20"))(zone)
        val id = subscriptions.observeAll().first().single().id
        subscriptions.setStatus(id, SubscriptionStatus.CONFIRMED)
        subscriptions.upsert(subscriptions.observeAll().first().single().copy(amountMinor = 39_000))

        charge("2026-11-03", 42_900)
        useCase(at("2026-11-05"))(zone)

        val saved = subscriptions.observeAll().first().single()
        assertThat(saved.status).isEqualTo(SubscriptionStatus.CONFIRMED)
        assertThat(saved.merchant).isEqualTo("Яндекс Плюс")
        assertThat(saved.amountMinor).isEqualTo(39_000)
        assertThat(saved.lastChargeAt).isEqualTo(at("2026-11-03"))
        assertThat(saved.nextChargeAt).isEqualTo(at("2026-12-03"))
    }

    @Test
    fun dismissed_isNeverTouched_norRecreated() = runTest {
        monthlyPlus()
        useCase(at("2026-10-20"))(zone)
        val dismissed = subscriptions.observeAll().first().single()
        subscriptions.setStatus(dismissed.id, SubscriptionStatus.DISMISSED)

        charge("2026-11-03", 39_900)
        val report = useCase(at("2026-11-05"))(zone)

        assertThat(report).isEqualTo(RefreshSubscriptionsReport(detected = 1, inserted = 0, updated = 0, removed = 0))
        val all = subscriptions.observeAll().first()
        assertThat(all).containsExactly(dismissed.copy(status = SubscriptionStatus.DISMISSED))
    }

    @Test
    fun brokenSeries_removesCandidate_butKeepsConfirmed() = runTest {
        monthlyPlus()
        charge("2026-08-15", 29_900, merchant = "Кинопоиск")
        charge("2026-09-15", 29_900, merchant = "Кинопоиск")
        charge("2026-10-15", 29_900, merchant = "Кинопоиск")
        useCase(at("2026-10-20"))(zone)
        val kino = subscriptions.observeAll().first().first { it.merchant == "Кинопоиск" }
        subscriptions.setStatus(kino.id, SubscriptionStatus.CONFIRMED)

        // Через полгода без списаний обе серии неактивны: детектор их больше не возвращает.
        val report = useCase(at("2027-03-20"))(zone)

        assertThat(report.removed).isEqualTo(1)
        val left = subscriptions.observeAll().first()
        assertThat(left.map { it.merchant to it.status })
            .containsExactly("Кинопоиск" to SubscriptionStatus.CONFIRMED)
    }

    @Test
    fun twoPlansOfOneMerchant_areMatchedByAmount() = runTest {
        charge("2026-08-05", 29_900, merchant = "VK Музыка")
        charge("2026-09-05", 29_900, merchant = "VK Музыка")
        charge("2026-10-05", 29_900, merchant = "VK Музыка")
        charge("2026-08-07", 99_000, merchant = "VK Музыка")
        charge("2026-09-07", 99_000, merchant = "VK Музыка")
        charge("2026-10-07", 99_000, merchant = "VK Музыка")
        val useCase = useCase(at("2026-10-20"))

        useCase(zone)
        val report = useCase(zone)

        assertThat(report.inserted).isEqualTo(0)
        assertThat(subscriptions.observeAll().first().map { it.amountMinor }).containsExactly(29_900L, 99_000L)
    }
}
