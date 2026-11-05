package com.foldfinance.core.data.ai

import com.foldfinance.core.data.T0
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.fixedClock
import com.foldfinance.core.data.inMemoryDb
import com.foldfinance.core.data.ingest.IngestOutcome
import com.foldfinance.core.data.ingest.IngestRepositoryImpl
import com.foldfinance.core.data.ingest.ReparseUseCase
import com.foldfinance.core.data.op
import com.foldfinance.core.data.parsed
import com.foldfinance.core.data.registryOf
import com.foldfinance.core.data.repository.MerchantAliasRepositoryImpl
import com.foldfinance.core.data.repository.MerchantRuleRepositoryImpl
import com.foldfinance.core.data.repository.TransactionRepositoryImpl
import com.foldfinance.core.data.sms
import com.foldfinance.core.model.AliasSource
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.ParseStatus
import com.foldfinance.parser.PARSER_VERSION
import com.foldfinance.parser.ParseResult
import com.foldfinance.parser.ParserRegistry
import com.foldfinance.parser.UncertainReasons
import com.foldfinance.parser.ai.AI_PARSER_ID
import com.foldfinance.parser.ai.AI_UNVERIFIED_CONFIDENCE
import com.foldfinance.parser.ai.AI_VERIFIED_CONFIDENCE
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class AiBackfillUseCaseTest {

    private lateinit var db: FoldFinanceDatabase
    private val clock = fixedClock()
    private val transactions get() = TransactionRepositoryImpl(db, clock)

    private val unknownText = "Операция 350р KOFEMANIYA *1234"
    private val uncertain = ParseResult.Uncertain(reason = UncertainReasons.UNKNOWN_OPERATION, parserId = "sber")
    private val registry: ParserRegistry = registryOf(unknownText to uncertain, "Покупка TANUKI" to parsed(op(500_00, merchant = "TANUKI 17")))

    /** Фейковый «ИИ»: считает вызовы, ответ задаётся тестом. */
    private class FakeReader(var answer: AiOutcome<ParseResult.Parsed>) : AiMessageReader {
        var calls = 0
        override suspend fun read(text: String, meta: com.foldfinance.parser.MessageMeta, regexResult: ParseResult) =
            answer.also { calls++ }
    }

    private class FakeAdvisor(var answer: AiOutcome<MerchantAdvice>) : AiMerchantAdvisor {
        val asked = mutableListOf<String>()
        var lastCategories: List<Category> = emptyList()
        var lastExamples: List<CategoryExample> = emptyList()
        override suspend fun advise(merchant: String, categories: List<Category>, examples: List<CategoryExample>): AiOutcome<MerchantAdvice> {
            asked += merchant
            lastCategories = categories
            lastExamples = examples
            return answer
        }
    }

    private fun aiParsed(confidence: Float = AI_VERIFIED_CONFIDENCE, merchant: String? = "KOFEMANIYA") =
        ParseResult.Parsed(listOf(op(350_00, merchant = merchant, confidence = confidence)), parserId = AI_PARSER_ID)

    private val noAdvice = FakeAdvisor(AiOutcome.Rejected("unused"))

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    private suspend fun ingestUnknown(id: Long = 1): Long {
        val out = IngestRepositoryImpl(db, registry, clock).ingest(sms(id, unknownText, T0.plus(Duration.ofHours(id))))
        return (out as IngestOutcome.Unrecognized).rawMessageId
    }

    @Test
    fun verifiedAiResult_createsOperationMarkedAsAi() = runTest {
        val rawId = ingestUnknown()
        val reader = FakeReader(AiOutcome.Done(aiParsed()))
        val useCase = AiBackfillUseCase(db, registry, clock)
        assertThat(useCase.hasPendingWork()).isTrue()

        val report = useCase(reader, noAdvice)

        assertThat(report.messagesSent).isEqualTo(1)
        assertThat(report.operationsRecorded).isEqualTo(1)
        val tx = transactions.observe().first().single()
        assertThat(tx.aiParsed).isTrue()
        assertThat(tx.needsReview).isFalse()
        assertThat(tx.amountMinor).isEqualTo(350_00L)
        val raw = db.rawMessageDao().getById(rawId)!!
        assertThat(raw.parserId).isEqualTo(AI_PARSER_ID)
        assertThat(raw.parseStatus).isEqualTo(ParseStatus.PARSED)
        assertThat(raw.aiAttemptedAt).isNotNull()
        assertThat(transactions.observeRawMessages(tx.id).first().single().parserId).isEqualTo(AI_PARSER_ID)

        // Повторный проход сообщение модели не отдаёт.
        useCase(reader, noAdvice)
        assertThat(reader.calls).isEqualTo(1)
    }

    @Test
    fun unverifiedAiResult_goesToReview() = runTest {
        ingestUnknown()
        AiBackfillUseCase(db, registry, clock)(FakeReader(AiOutcome.Done(aiParsed(AI_UNVERIFIED_CONFIDENCE))), noAdvice)
        val tx = transactions.observe().first().single()
        assertThat(tx.aiParsed).isTrue()
        assertThat(tx.needsReview).isTrue()
    }

    @Test
    fun rejectedAnswer_leavesMessageUnrecognized_andIsNotRetried() = runTest {
        val rawId = ingestUnknown()
        val reader = FakeReader(AiOutcome.Rejected("amount_not_in_text"))
        val useCase = AiBackfillUseCase(db, registry, clock)

        useCase(reader, noAdvice)
        useCase(reader, noAdvice)

        assertThat(reader.calls).isEqualTo(1)
        assertThat(transactions.observe().first()).isEmpty()
        val raw = db.rawMessageDao().getById(rawId)!!
        assertThat(raw.parseStatus).isEqualTo(ParseStatus.UNCERTAIN)
        assertThat(raw.transactionId).isNull()
    }

    @Test
    fun unavailableModel_stopsPass_andRetriesLater() = runTest {
        val rawId = ingestUnknown()
        val reader = FakeReader(AiOutcome.Unavailable)
        val useCase = AiBackfillUseCase(db, registry, clock)

        val first = useCase(reader, noAdvice)
        assertThat(first.stopped).isTrue()
        assertThat(db.rawMessageDao().getById(rawId)!!.aiAttemptedAt).isNull()

        reader.answer = AiOutcome.Done(aiParsed())
        useCase(reader, noAdvice)
        assertThat(transactions.observe().first()).hasSize(1)
    }

    @Test
    fun userConfirmedOperation_isNotSentToModel() = runTest {
        // Uncertain с частичной операцией → операция на проверке; пользователь её подтвердил.
        val partial = ParseResult.Uncertain(UncertainReasons.UNKNOWN_OPERATION, op(350_00, confidence = 0.3f), parserId = "sber")
        val reg = registryOf(unknownText to partial)
        val out = IngestRepositoryImpl(db, reg, clock).ingest(sms(1, unknownText, T0)) as IngestOutcome.Recorded
        transactions.confirmReview(out.transactionIds)

        val reader = FakeReader(AiOutcome.Done(aiParsed()))
        AiBackfillUseCase(db, reg, clock)(reader, noAdvice)
        assertThat(reader.calls).isEqualTo(0)
    }

    @Test
    fun reparse_keepsAiResultWhenRegexStillFails() = runTest {
        val rawId = ingestUnknown()
        AiBackfillUseCase(db, registry, clock)(FakeReader(AiOutcome.Done(aiParsed())), noAdvice)
        val raw = db.rawMessageDao().getById(rawId)!!
        db.rawMessageDao().update(raw.copy(parserVersion = PARSER_VERSION - 1))

        ReparseUseCase(db, registry, clock)()

        val after = db.rawMessageDao().getById(rawId)!!
        assertThat(after.parserVersion).isEqualTo(PARSER_VERSION)
        assertThat(after.parserId).isEqualTo(AI_PARSER_ID)
        assertThat(transactions.observe().first().single().aiParsed).isTrue()
    }

    // ------------------------------------------------------------------ мерчанты

    @Test
    fun merchantAdvice_isCached_secondRunDoesNotCallModel() = runTest {
        IngestRepositoryImpl(db, registry, clock).ingest(sms(5, "Покупка TANUKI", T0))
        val advisor = FakeAdvisor(AiOutcome.Done(MerchantAdvice("Тануки", "Рестораны")))
        val useCase = AiBackfillUseCase(db, registry, clock)
        val reader = FakeReader(AiOutcome.Unavailable)

        val report = useCase(reader, advisor)
        assertThat(report.merchantsDescribed).isEqualTo(1)
        assertThat(report.recategorized).isEqualTo(1)
        useCase(reader, advisor)
        assertThat(advisor.asked).containsExactly("TANUKI 17")
        assertThat(useCase.hasPendingWork()).isFalse()

        // Категории «Другое» и «Переводы» модели не предлагаются.
        assertThat(advisor.lastCategories.map { it.key }).containsNoneOf("other", "transfers")

        val tx = transactions.observe().first().single()
        assertThat(tx.merchant).isEqualTo("TANUKI 17")
        assertThat(tx.merchantDisplayName).isEqualTo("Тануки")
        assertThat(tx.displayMerchant).isEqualTo("Тануки")
        assertThat(tx.aiCategory).isTrue()
        assertThat(tx.needsReview).isTrue()
        val restaurants = db.categoryDao().getByKey("restaurants")!!.id
        assertThat(tx.categoryId).isEqualTo(restaurants)

        // Новая операция того же мерчанта: категория из кэша, без вызова модели.
        IngestRepositoryImpl(db, registry, clock).ingest(sms(6, "Покупка TANUKI", T0.plusSeconds(3600)).copy(text = "Покупка TANUKI"))
        assertThat(advisor.asked).hasSize(1)
    }

    @Test
    fun cachedCategory_appliesAtIngest_andConfirmationIsRemembered() = runTest {
        val ingest = IngestRepositoryImpl(db, registry, clock)
        ingest.ingest(sms(5, "Покупка TANUKI", T0))
        AiBackfillUseCase(db, registry, clock)(FakeReader(AiOutcome.Unavailable), FakeAdvisor(AiOutcome.Done(MerchantAdvice(null, "Рестораны"))))
        val first = transactions.observe().first().single()
        assertThat(first.aiCategory).isTrue()

        transactions.confirmReview(first.id)
        assertThat(transactions.observeById(first.id).first()!!.aiCategory).isFalse()

        // Следующая операция мерчанта: категория подтверждена — без очереди проверки.
        val second = ingest.ingest(sms(7, "Покупка TANUKI", T0.plusSeconds(7200))) as IngestOutcome.Recorded
        val tx = transactions.observeById(second.transactionIds.single()).first()!!
        assertThat(tx.categoryId).isEqualTo(db.categoryDao().getByKey("restaurants")!!.id)
        assertThat(tx.needsReview).isFalse()
        assertThat(tx.aiCategory).isFalse()
    }

    @Test
    fun categoryPriority_ruleAndDictionaryBeatModel() = runTest {
        val groceries = db.categoryDao().getByKey("groceries")!!.id
        val coffee = db.categoryDao().getByKey("restaurants")!!.id
        val reg = registryOf(
            "Покупка PYATEROCHKA" to parsed(op(100_00, merchant = "PYATEROCHKA 1234")),
            "Покупка SHOKO" to parsed(op(200_00, merchant = "SHOKO BAR")),
        )
        MerchantRuleRepositoryImpl(db, clock).add("shoko", groceries)
        val ingest = IngestRepositoryImpl(db, reg, clock)
        ingest.ingest(sms(1, "Покупка PYATEROCHKA", T0))
        ingest.ingest(sms(2, "Покупка SHOKO", T0.plusSeconds(60)))

        // Модель для обоих предлагает «Рестораны» — но словарь и правило пользователя приоритетнее.
        val advisor = FakeAdvisor(AiOutcome.Done(MerchantAdvice(null, "Рестораны")))
        val report = AiBackfillUseCase(db, reg, clock)(FakeReader(AiOutcome.Unavailable), advisor)
        assertThat(report.recategorized).isEqualTo(0)
        // Пример для модели — правило пользователя.
        assertThat(advisor.lastExamples.map { it.categoryName }).contains("Продукты")

        val all = transactions.observe().first()
        assertThat(all.map { it.categoryId }.toSet()).containsExactly(groceries)
        assertThat(all.none { it.aiCategory }).isTrue()
        assertThat(coffee).isNotEqualTo(groceries)
    }

    @Test
    fun implausibleName_isNotUsed_butUserRenameWins() = runTest {
        IngestRepositoryImpl(db, registry, clock).ingest(sms(5, "Покупка TANUKI", T0))
        val aliases = MerchantAliasRepositoryImpl(db, clock)
        AiBackfillUseCase(db, registry, clock)(FakeReader(AiOutcome.Unavailable), FakeAdvisor(AiOutcome.Done(MerchantAdvice("Старбакс", null))))
        assertThat(transactions.observe().first().single().merchantDisplayName).isNull()

        aliases.rename("TANUKI 17", "Моя кофейня")
        assertThat(transactions.observe().first().single().displayMerchant).isEqualTo("Моя кофейня")
        assertThat(aliases.observeFor("Tanuki").first()!!.source).isEqualTo(AliasSource.USER)

        aliases.rename("TANUKI 17", null)
        assertThat(transactions.observe().first().single().displayMerchant).isEqualTo("TANUKI 17")
    }

    @Test
    fun userRenameBeforeModel_isKeptWhenModelAnswers() = runTest {
        IngestRepositoryImpl(db, registry, clock).ingest(sms(5, "Покупка TANUKI", T0))
        MerchantAliasRepositoryImpl(db, clock).rename("TANUKI 17", "Суши у дома")
        val advisor = FakeAdvisor(AiOutcome.Done(MerchantAdvice("Тануки", "Рестораны")))
        AiBackfillUseCase(db, registry, clock)(FakeReader(AiOutcome.Unavailable), advisor)

        assertThat(advisor.asked).hasSize(1)
        val tx = transactions.observe().first().single()
        assertThat(tx.displayMerchant).isEqualTo("Суши у дома")
        assertThat(tx.aiCategory).isTrue()
    }
}
