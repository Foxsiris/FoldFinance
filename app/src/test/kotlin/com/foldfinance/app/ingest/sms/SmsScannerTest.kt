package com.foldfinance.app.ingest.sms

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foldfinance.app.ingest.ScanHarness
import com.foldfinance.app.ingest.Texts
import com.foldfinance.app.ingest.sms
import com.foldfinance.core.data.ingest.SourceKeys
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsScannerTest {

    private lateinit var h: ScanHarness

    @After fun tearDown() = h.close()

    @Test fun `full scan records bank operations and counts them`() = runTest {
        h = ScanHarness(listOf(sms(1, Texts.PURCHASE), sms(2, Texts.REFUND), sms(3, Texts.OTP)))

        val result = h.scanner.scan(ScanMode.FULL)

        assertThat(result.scanned).isEqualTo(3)
        assertThat(result.total).isEqualTo(3)
        assertThat(result.found).isEqualTo(2)
        assertThat(result.expenseCount).isEqualTo(1)
        assertThat(result.expenseRubMinor).isEqualTo(150_000)
        assertThat(result.incomeCount).isEqualTo(0) // a refund is not income
        assertThat(h.countRows("transactions")).isEqualTo(2)
        assertThat(h.settings.lastProcessedSmsId()).isEqualTo(3)
    }

    @Test fun `repeated full scan creates no duplicates and reports the same totals`() = runTest {
        h = ScanHarness(listOf(sms(1, Texts.PURCHASE), sms(2, Texts.REFUND)))

        val first = h.scanner.scan(ScanMode.FULL)
        val second = h.scanner.scan(ScanMode.FULL)

        assertThat(h.countRows("transactions")).isEqualTo(2)
        assertThat(h.countRows("raw_messages")).isEqualTo(2)
        assertThat(second.found).isEqualTo(first.found)
        assertThat(second.expenseRubMinor).isEqualTo(first.expenseRubMinor)
    }

    @Test fun `delta scan reads only ids after the cursor`() = runTest {
        h = ScanHarness(listOf(sms(1, Texts.PURCHASE), sms(2, Texts.REFUND)))
        h.scanner.scan(ScanMode.FULL)
        h.ingest.received.clear()
        h.reader.pageRequests.clear()

        h.reader.inbox += sms(3, Texts.PURCHASE)
        h.reader.inbox += sms(4, Texts.REFUND)
        val delta = h.scanner.scan(ScanMode.DELTA)

        assertThat(h.reader.pageRequests.first()).isEqualTo(2)
        assertThat(h.ingest.received.map { it.sourceKey }).containsExactly(SourceKeys.sms(3), SourceKeys.sms(4)).inOrder()
        assertThat(delta.scanned).isEqualTo(2)
        assertThat(h.settings.lastProcessedSmsId()).isEqualTo(4)
        assertThat(h.countRows("transactions")).isEqualTo(4)
    }

    @Test fun `delta scan with nothing new touches nothing`() = runTest {
        h = ScanHarness(listOf(sms(1, Texts.PURCHASE)))
        h.scanner.scan(ScanMode.FULL)
        h.ingest.received.clear()

        val delta = h.scanner.scan(ScanMode.DELTA)

        assertThat(delta.scanned).isEqualTo(0)
        assertThat(h.ingest.received).isEmpty()
        assertThat(h.settings.lastProcessedSmsId()).isEqualTo(1)
    }

    @Test fun `cursor moves only past successfully written messages`() = runTest {
        h = ScanHarness((1L..5L).map { sms(it, Texts.PURCHASE) })
        h.ingest.failOnSourceKeys = setOf(SourceKeys.sms(3))

        val error = runCatching { h.scanner.scan(ScanMode.DELTA, pageSize = 10) }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(h.settings.lastProcessedSmsId()).isEqualTo(2)
        assertThat(h.countRows("raw_messages")).isEqualTo(2)

        // Retry after the problem is gone: resumes at 3, nothing lost, nothing doubled.
        h.ingest.failOnSourceKeys = emptySet()
        h.ingest.received.clear()
        h.scanner.scan(ScanMode.DELTA, pageSize = 10)
        assertThat(h.ingest.received.map { it.sourceKey })
            .containsExactly(SourceKeys.sms(3), SourceKeys.sms(4), SourceKeys.sms(5)).inOrder()
        assertThat(h.settings.lastProcessedSmsId()).isEqualTo(5)
        assertThat(h.countRows("transactions")).isEqualTo(5)
    }

    @Test fun `pages are walked until the inbox is exhausted`() = runTest {
        h = ScanHarness((1L..7L).map { sms(it, Texts.PURCHASE) })
        val seen = mutableListOf<Int>()

        val result = h.scanner.scan(ScanMode.FULL, pageSize = 3) { seen += it.scanned }

        assertThat(result.scanned).isEqualTo(7)
        assertThat(h.reader.pageRequests).containsExactly(0L, 3L, 6L).inOrder()
        assertThat(seen).containsExactly(0, 3, 6, 7).inOrder()
    }

    @Test fun `not financial and private sms are never stored`() = runTest {
        h = ScanHarness(
            listOf(
                sms(1, Texts.OTP),
                sms(2, Texts.PROMO),
                sms(3, Texts.CHAT, address = "+79990000000"),
                sms(4, Texts.TBANK_PURCHASE, address = "ShopBank"),
            ),
        )

        val result = h.scanner.scan(ScanMode.FULL)

        assertThat(result.scanned).isEqualTo(4)
        // Only the operation from the unknown sender (GenericParser found money) was stored.
        assertThat(h.ingest.received.map { it.sourceKey }).containsExactly(SourceKeys.sms(4))
        assertThat(h.countRows("raw_messages")).isEqualTo(1)
        assertThat(h.settings.lastProcessedSmsId()).isEqualTo(4)
    }
}
