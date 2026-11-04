package com.foldfinance.core.domain.subscriptions

import com.foldfinance.core.domain.TestTransactions.at
import com.foldfinance.core.domain.TestTransactions.tx
import com.foldfinance.core.model.Subscription
import com.foldfinance.core.model.SubscriptionPeriod
import com.foldfinance.core.model.SubscriptionStatus
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class SubscriptionDetectorTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private fun msk(local: String) = at(local, zone)

    private fun monthly(
        merchant: String,
        amount: Long,
        start: String,
        count: Int,
        currency: String = "RUB",
    ): List<Transaction> {
        val first = LocalDateTime.parse(start)
        return (0 until count).map { i ->
            tx(first.plusMonths(i.toLong()).atZone(zone).toInstant(), amount, merchant = merchant, currency = currency, categoryId = 7)
        }
    }

    private fun detect(txs: List<Transaction>, now: Instant, includeInactive: Boolean = false) =
        SubscriptionDetector.detect(txs, now, zone, includeInactive)

    @Test
    fun `monthly subscription is detected with next charge`() {
        val txs = monthly("YANDEX*PLUS", 299_00, "2026-01-15T10:00", 6)
        val result = detect(txs, msk("2026-06-20T12:00"))
        val c = result.single()
        assertThat(c.period).isEqualTo(SubscriptionPeriod.MONTHLY)
        assertThat(c.occurrences).isEqualTo(6)
        assertThat(c.typicalAmountMinor).isEqualTo(299_00)
        assertThat(c.currency).isEqualTo("RUB")
        assertThat(c.merchant).isEqualTo("YANDEX*PLUS")
        assertThat(c.categoryId).isEqualTo(7)
        assertThat(c.lastChargeAt).isEqualTo(msk("2026-06-15T10:00"))
        assertThat(c.nextChargeAt).isEqualTo(msk("2026-07-15T10:00"))
        assertThat(c.isActive).isTrue()
        assertThat(c.transactionIds).isEqualTo(txs.map { it.id })
    }

    @Test
    fun `monthly on the 31st moves to the last day of short months`() {
        val txs = listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30").map {
            tx(msk("${it}T09:00"), 149_00, merchant = "ICLOUD")
        }
        val c = detect(txs, msk("2026-05-02T09:00")).single()
        assertThat(c.period).isEqualTo(SubscriptionPeriod.MONTHLY)
        assertThat(c.nextChargeAt).isEqualTo(msk("2026-05-31T09:00"))

        // Последнее списание 28 февраля, привязка к 31-му → следующее 31 марта.
        val toFeb = listOf("2025-12-31", "2026-01-31", "2026-02-28").map {
            tx(msk("${it}T09:00"), 149_00, merchant = "ICLOUD")
        }
        assertThat(detect(toFeb, msk("2026-03-01T09:00")).single().nextChargeAt).isEqualTo(msk("2026-03-31T09:00"))
    }

    @Test
    fun `weekly subscription with small amount variation`() {
        val amounts = listOf(500_00L, 495_00, 505_00, 500_00, 510_00, 490_00)
        val txs = amounts.mapIndexed { i, a ->
            tx(msk("2026-09-07T08:00").plusSeconds(i * 7L * 86_400 + i * 3_600L), a, merchant = "SAMOKAT PLUS")
        }
        val c = detect(txs, msk("2026-10-14T08:00")).single()
        assertThat(c.period).isEqualTo(SubscriptionPeriod.WEEKLY)
        assertThat(c.occurrences).isEqualTo(6)
        assertThat(c.typicalAmountMinor).isEqualTo(500_00)
        assertThat(c.lastAmountMinor).isEqualTo(490_00)
        assertThat(c.nextChargeAt).isEqualTo(txs.last().occurredAt.plusSeconds(7L * 86_400))
    }

    @Test
    fun `yearly subscription needs only two charges`() {
        val txs = listOf(
            tx(msk("2025-03-10T12:00"), 1990_00, merchant = "KINOPOISK"),
            tx(msk("2026-03-10T12:00"), 1990_00, merchant = "Kinopoisk"),
        )
        val c = detect(txs, msk("2026-04-01T12:00")).single()
        assertThat(c.period).isEqualTo(SubscriptionPeriod.YEARLY)
        assertThat(c.occurrences).isEqualTo(2)
        assertThat(c.nextChargeAt).isEqualTo(msk("2027-03-10T12:00"))
        assertThat(c.merchant).isEqualTo("Kinopoisk")
    }

    @Test
    fun `yearly from leap day moves to february 28`() {
        val txs = listOf(
            tx(msk("2024-02-29T12:00"), 999_00, merchant = "NOTES APP"),
            tx(msk("2025-02-28T12:00"), 999_00, merchant = "NOTES APP"),
        )
        val c = detect(txs, msk("2025-03-10T12:00")).single()
        assertThat(c.nextChargeAt).isEqualTo(msk("2026-02-28T12:00"))
    }

    @Test
    fun `two monthly charges are not a subscription`() {
        val txs = monthly("NETFLIX.COM", 999_00, "2026-01-05T10:00", 2)
        assertThat(detect(txs, msk("2026-02-10T10:00"))).isEmpty()
    }

    @Test
    fun `noise with different amounts and irregular intervals is not a subscription`() {
        val offsets = listOf(0, 3, 11, 12, 25, 47, 52, 80, 81, 99)
        val amounts = listOf(1250_00L, 8730_00, 433_00, 2999_00, 15400_00, 610_00, 7200_00, 3900_00, 1180_00, 5025_00)
        val start = msk("2026-01-01T18:00")
        val noise = offsets.zip(amounts).map { (d, a) -> tx(start.plusSeconds(d * 86_400L), a, merchant = "PYATEROCHKA 1234") }
        val sameAmountIrregular = listOf(0, 12, 50, 51, 100).map {
            tx(start.plusSeconds(it * 86_400L), 300_00, merchant = "COFFEE POINT")
        }
        assertThat(detect(noise + sameAmountIrregular, msk("2026-04-15T10:00"))).isEmpty()
    }

    @Test
    fun `amount outside 10 percent breaks the series, inside does not`() {
        val outside = listOf(299_00L, 299_00, 399_00).mapIndexed { i, a ->
            tx(msk("2026-01-15T10:00").plusSeconds(i * 30L * 86_400), a, merchant = "SPOTIFY")
        }
        assertThat(detect(outside, msk("2026-03-20T10:00"))).isEmpty()

        val inside = listOf(299_00L, 299_00, 319_00).mapIndexed { i, a ->
            tx(msk("2026-01-15T10:00").plusSeconds(i * 30L * 86_400), a, merchant = "SPOTIFY")
        }
        val c = detect(inside, msk("2026-03-20T10:00")).single()
        assertThat(c.typicalAmountMinor).isEqualTo(299_00)
        assertThat(c.lastAmountMinor).isEqualTo(319_00)
    }

    @Test
    fun `occasional purchases at the same merchant do not break the series`() {
        val subs = monthly("SPOTIFY", 169_00, "2026-01-15T10:00", 5)
        val extra = tx(msk("2026-02-20T10:00"), 175_00, merchant = "Spotify")
        val c = detect(subs + extra, msk("2026-05-20T10:00")).single()
        assertThat(c.occurrences).isEqualTo(5)
        assertThat(c.transactionIds).doesNotContain(extra.id)
    }

    @Test
    fun `only expenses count and currencies are separate`() {
        val refunds = monthly("SPOTIFY", 169_00, "2026-01-15T10:00", 4).map { it.copy(direction = Direction.REFUND, kind = Kind.REFUND) }
        val income = monthly("SALARY CORP", 100_000_00, "2026-01-05T10:00", 4).map { it.copy(direction = Direction.INCOME) }
        val transfers = monthly("MY CARD", 10_000_00, "2026-01-05T10:00", 4).map { it.copy(direction = Direction.INTERNAL_TRANSFER) }
        assertThat(detect(refunds + income + transfers, msk("2026-04-20T10:00"))).isEmpty()

        val mixed = monthly("NETFLIX", 999, "2026-01-10T10:00", 2, currency = "USD") +
            monthly("NETFLIX", 999_00, "2026-03-10T10:00", 1, currency = "RUB")
        assertThat(detect(mixed, msk("2026-03-20T10:00"))).isEmpty()

        val both = monthly("NETFLIX", 999, "2026-01-10T10:00", 3, currency = "USD") +
            monthly("NETFLIX", 999_00, "2026-01-12T10:00", 3, currency = "RUB")
        assertThat(detect(both, msk("2026-03-20T10:00")).map { it.currency }).containsExactly("USD", "RUB")
    }

    @Test
    fun `cancelled subscription is inactive`() {
        val txs = monthly("SPOTIFY", 169_00, "2026-01-15T10:00", 4)
        assertThat(detect(txs, msk("2026-08-01T10:00"))).isEmpty()
        val c = detect(txs, msk("2026-08-01T10:00"), includeInactive = true).single()
        assertThat(c.isActive).isFalse()
        // В пределах допуска (+4 дня после ожидаемого 15 мая) ещё активна.
        assertThat(detect(txs, msk("2026-05-19T09:00")).single().isActive).isTrue()
    }

    @Test
    fun `merchant names are normalized for grouping`() {
        val names = listOf("SPOTIFY*1234", "Spotify", "SPOTIFY 5678", "spotify")
        val txs = names.mapIndexed { i, n -> tx(msk("2026-01-15T10:00").plusSeconds(i * 31L * 86_400), 169_00, merchant = n) }
        assertThat(detect(txs, msk("2026-04-20T10:00")).single().occurrences).isEqualTo(4)
    }

    @Test
    fun `future transactions are ignored`() {
        val txs = monthly("SPOTIFY", 169_00, "2026-01-15T10:00", 3)
        assertThat(detect(txs, msk("2026-03-01T10:00"))).isEmpty()
    }

    @Test
    fun `monthly equivalent`() {
        assertThat(SubscriptionCosts.monthlyEquivalent(299_00, SubscriptionPeriod.MONTHLY)).isEqualTo(299_00)
        assertThat(SubscriptionCosts.monthlyEquivalent(500_00, SubscriptionPeriod.WEEKLY)).isEqualTo(2166_67)
        assertThat(SubscriptionCosts.monthlyEquivalent(1990_00, SubscriptionPeriod.YEARLY)).isEqualTo(165_83)

        fun sub(amount: Long, period: SubscriptionPeriod, currency: String) = Subscription(
            merchant = "X", amountMinor = amount, currency = currency, period = period,
            lastChargeAt = Instant.EPOCH, nextChargeAt = Instant.EPOCH, status = SubscriptionStatus.CONFIRMED,
        )
        val subs = listOf(
            sub(299_00, SubscriptionPeriod.MONTHLY, "RUB"),
            sub(1200_00, SubscriptionPeriod.YEARLY, "RUB"),
            sub(999, SubscriptionPeriod.MONTHLY, "USD"),
        )
        assertThat(SubscriptionCosts.monthlyEquivalent(subs[1])).isEqualTo(100_00)
        assertThat(SubscriptionCosts.monthlyTotals(subs)).containsExactly("RUB", 399_00L, "USD", 999L)
    }

    @Test
    fun `candidate converts to subscription`() {
        val c = detect(monthly("SPOTIFY", 169_00, "2026-01-15T10:00", 3), msk("2026-03-20T10:00")).single()
        val s = c.toSubscription()
        assertThat(s.status).isEqualTo(SubscriptionStatus.CANDIDATE)
        assertThat(s.amountMinor).isEqualTo(169_00)
        assertThat(s.nextChargeAt).isEqualTo(msk("2026-04-15T10:00"))
        assertThat(SubscriptionCosts.monthlyEquivalent(c)).isEqualTo(169_00)
    }
}
