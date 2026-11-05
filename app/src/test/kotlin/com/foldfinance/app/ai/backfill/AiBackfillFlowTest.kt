package com.foldfinance.app.ai.backfill

import com.foldfinance.app.ai.AiFailureReasons
import com.foldfinance.app.ai.AiJson
import com.foldfinance.app.ai.AiRequest
import com.foldfinance.app.ai.AiResponse
import com.foldfinance.app.ai.AiStatus
import com.foldfinance.app.ai.LocalAi
import com.foldfinance.app.ingest.Registry
import com.foldfinance.app.ingest.T0
import com.foldfinance.app.ingest.TestClock
import com.foldfinance.app.ingest.TestZone
import com.foldfinance.app.ingest.inMemoryDb
import com.foldfinance.core.data.ai.AiBackfillUseCase
import com.foldfinance.core.data.ai.AiOutcome
import com.foldfinance.core.data.ai.CategoryExample
import com.foldfinance.core.data.ai.MerchantAdvice
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.ingest.IncomingMessage
import com.foldfinance.core.data.ingest.IngestOutcome
import com.foldfinance.core.data.ingest.IngestRepositoryImpl
import com.foldfinance.core.data.ingest.SourceKeys
import com.foldfinance.core.data.repository.TransactionRepositoryImpl
import com.foldfinance.core.data.repository.UnrecognizedMessagesRepositoryImpl
import com.foldfinance.core.model.Category
import com.foldfinance.parser.MessageChannel
import com.foldfinance.parser.MessageMeta
import com.foldfinance.parser.ParseResult
import com.foldfinance.parser.ai.AI_PARSER_ID
import com.foldfinance.parser.ai.AiExtractionValidator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Фоновая дообработка с фейковым [LocalAi] и настоящими парсером, БД и записью операций:
 * разбор JSON, сверка с текстом (галлюцинация суммы отклоняется), кэш мерчантов.
 */
@RunWith(RobolectricTestRunner::class)
class AiBackfillFlowTest {

    /** Фейковая модель: отвечает по типу запроса (системному промпту) и считает вызовы. */
    private class FakeLocalAi : LocalAi {
        override val status = MutableStateFlow<AiStatus>(AiStatus.Ready)
        override val supportsImages = MutableStateFlow(false)
        val requests = mutableListOf<AiRequest>()
        var smsReply: AiResponse = AiResponse.Failed(AiFailureReasons.NOT_READY)
        var merchantReply: AiResponse = AiResponse.Failed(AiFailureReasons.NOT_READY)
        override suspend fun prepare() = Unit
        override suspend fun generate(request: AiRequest): AiResponse {
            requests += request
            return if (request.system == SmsPrompt.SYSTEM) smsReply else merchantReply
        }
        fun smsCalls() = requests.count { it.system == SmsPrompt.SYSTEM }
        fun merchantCalls() = requests.count { it.system == MerchantPrompt.SYSTEM }
    }

    /** Синтетический текст: regex не знает слова «Операция» → Uncertain(UNKNOWN_OPERATION). */
    private val text = "Операция 1 500 ₽ по карте *1234 KOFEMANIYA. Баланс: 10 234,56 ₽"
    private val meta = MessageMeta(MessageChannel.SMS, "Tinkoff", T0.toEpochMilli())
    private val goodJson =
        "```json\n{\"is_operation\": true, \"amount\": \"1 500\", \"currency\": \"RUB\", \"type\": \"debit\", " +
            "\"direction\": null, \"merchant\": \"KOFEMANIYA\", \"card_last4\": \"1234\", \"balance\": \"10 234,56\"}\n```"

    private lateinit var db: FoldFinanceDatabase
    private val ai = FakeLocalAi()
    private val reader get() = LocalAiMessageReader(ai, AiExtractionValidator(TestZone))
    private val advisor get() = LocalAiMerchantAdvisor(ai)
    private val transactions get() = TransactionRepositoryImpl(db, TestClock)

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    private suspend fun ingest(id: Long = 1): IngestOutcome = IngestRepositoryImpl(db, Registry, TestClock).ingest(
        IncomingMessage(MessageChannel.SMS, "Tinkoff", SourceKeys.sms(id, T0.toEpochMilli(), text), T0, text),
    )

    @Test
    fun reader_parsesFencedJson_andChecksAgainstText() = runTest {
        ai.smsReply = AiResponse.Text(goodJson)
        val regex = Registry.parse(text, meta)
        val outcome = reader.read(text, meta, regex)
        assertThat(outcome).isInstanceOf(AiOutcome.Done::class.java)
        val parsed = (outcome as AiOutcome.Done<ParseResult.Parsed>).value
        assertThat(parsed.parserId).isEqualTo(AI_PARSER_ID)
        assertThat(parsed.operations.single().amountMinor).isEqualTo(150_000L)
        assertThat(parsed.balance?.amountMinor).isEqualTo(1_023_456L)
        // Запрос: FAST, JSON, текст сообщения внутри промпта.
        val request = ai.requests.single()
        assertThat(request.jsonOnly).isTrue()
        assertThat(request.prompt).contains(text)
    }

    @Test
    fun reader_rejectsHallucinations_andBrokenJson() = runTest {
        val regex = Registry.parse(text, meta)
        ai.smsReply = AiResponse.Text(goodJson.replace("1 500", "2 500"))
        assertThat(reader.read(text, meta, regex)).isEqualTo(AiOutcome.Rejected("amount_not_in_text"))
        ai.smsReply = AiResponse.Text(goodJson.replace("\"1234\"", "\"9999\""))
        assertThat(reader.read(text, meta, regex)).isEqualTo(AiOutcome.Rejected("card_not_in_text"))
        ai.smsReply = AiResponse.Text("Сумма 1500, карта 1234")
        assertThat(reader.read(text, meta, regex)).isEqualTo(AiOutcome.Rejected("bad_json"))
        ai.smsReply = AiResponse.Text("{\"amount\": [1500]}")
        assertThat(reader.read(text, meta, regex)).isEqualTo(AiOutcome.Rejected("no_amount"))
    }

    @Test
    fun reader_mapsModelFailures() = runTest {
        val regex = Registry.parse(text, meta)
        ai.smsReply = AiResponse.Failed(AiFailureReasons.BACKGROUND_BLOCKED)
        assertThat(reader.read(text, meta, regex)).isEqualTo(AiOutcome.Unavailable)
        ai.smsReply = AiResponse.Failed(AiFailureReasons.TIMEOUT)
        assertThat(reader.read(text, meta, regex)).isEqualTo(AiOutcome.Rejected(AiFailureReasons.TIMEOUT))
    }

    @Test
    fun advisor_sendsCategoriesAndExamples_andParsesAnswer() = runTest {
        ai.merchantReply = AiResponse.Text("{\"name\": \"Яндекс Go\", \"category\": \"Транспорт\"}")
        val categories = listOf(Category(1, "transport", "Транспорт", "bus", null, true, 0))
        val outcome = advisor.advise("YANDEX*GO*TAXI MOSCOW", categories, listOf(CategoryExample("PYATEROCHKA 1234", "Продукты")))
        assertThat(outcome).isEqualTo(AiOutcome.Done(MerchantAdvice("Яндекс Go", "Транспорт")))
        val prompt = ai.requests.single().prompt
        assertThat(prompt).contains("YANDEX*GO*TAXI MOSCOW")
        assertThat(prompt).contains("Транспорт")
        assertThat(prompt).contains("\"PYATEROCHKA 1234\" → Продукты")
    }

    @Test
    fun backfill_replacesRegexDraftWithVerifiedAiOperation_andCachesMerchant() = runTest {
        // Regex создаёт черновик «вид не понят» на проверке (мерчант не извлечён).
        val before = ingest() as IngestOutcome.Recorded
        assertThat(before.needsReview).isTrue()
        ai.smsReply = AiResponse.Text(goodJson)
        ai.merchantReply = AiResponse.Text("{\"name\": \"Кофемания\", \"category\": \"Рестораны\"}")
        val useCase = AiBackfillUseCase(db, Registry, TestClock)

        val report = useCase(reader, advisor)

        assertThat(report.operationsRecorded).isEqualTo(1)
        val tx = transactions.observe().first().single()
        assertThat(tx.id).isEqualTo(before.transactionIds.single())
        assertThat(tx.aiParsed).isTrue()
        assertThat(tx.needsReview).isFalse()
        assertThat(tx.kind).isEqualTo(com.foldfinance.parser.Kind.PURCHASE)
        assertThat(tx.amountMinor).isEqualTo(150_000L)
        assertThat(tx.cardMask).isEqualTo("*1234")
        assertThat(tx.merchant).isEqualTo("KOFEMANIYA")
        assertThat(tx.displayMerchant).isEqualTo("Кофемания")
        assertThat(UnrecognizedMessagesRepositoryImpl(db).observeUnrecognized().first()).isEmpty()

        // Второй проход: модель не вызывается ни для сообщения, ни для мерчанта.
        val calls = ai.requests.size
        useCase(reader, advisor)
        assertThat(ai.requests.size).isEqualTo(calls)
        assertThat(ai.smsCalls()).isEqualTo(1)
        assertThat(ai.merchantCalls()).isEqualTo(1)
        assertThat(useCase.hasPendingWork()).isFalse()
    }

    @Test
    fun backfill_hallucinatedAmount_leavesRegexDraftUntouched() = runTest {
        ingest()
        ai.smsReply = AiResponse.Text(goodJson.replace("1 500", "15 000"))
        AiBackfillUseCase(db, Registry, TestClock)(reader, advisor)

        // Как раньше: черновик regex на проверке, модель его не тронула.
        val tx = transactions.observe().first().single()
        assertThat(tx.aiParsed).isFalse()
        assertThat(tx.needsReview).isTrue()
        assertThat(tx.amountMinor).isEqualTo(150_000L)
    }

    @Test
    fun json_helpers() {
        assertThat(AiJson.extractObject("a {\"x\": \"}\"} b {\"y\":1}")).isEqualTo("{\"x\": \"}\"}")
        assertThat(AiJson.parseObject("нет json")).isNull()
        assertThat(AiJson.parseObject("[1,2]")).isNull()
        with(AiJson) {
            val obj = requireNotNull(parseObject("{\"a\": 1500.5, \"b\": null, \"c\": \"null\", \"d\": \"false\"}"))
            assertThat(obj.str("a")).isEqualTo("1500.5")
            assertThat(obj.str("b")).isNull()
            assertThat(obj.str("c")).isNull()
            assertThat(obj.bool("d")).isFalse()
        }
    }
}
