package com.foldfinance.core.data.ai

import androidx.room.withTransaction
import com.foldfinance.core.data.db.FoldFinanceDatabase
import com.foldfinance.core.data.db.TextNorm
import com.foldfinance.core.data.db.entity.MerchantAliasEntity
import com.foldfinance.core.data.db.entity.RawMessageEntity
import com.foldfinance.core.data.db.toModel
import com.foldfinance.core.data.ingest.OperationWriter
import com.foldfinance.core.di.DataClock
import com.foldfinance.core.domain.category.Categorizer
import com.foldfinance.core.domain.category.CategorySource
import com.foldfinance.core.domain.category.MerchantAliasRules
import com.foldfinance.core.domain.category.MerchantNormalizer
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.DefaultCategories
import com.foldfinance.core.model.ParseStatus
import com.foldfinance.parser.MessageMeta
import com.foldfinance.parser.ParseResult
import com.foldfinance.parser.ParserRegistry
import com.foldfinance.parser.ai.AI_PARSER_ID
import com.foldfinance.parser.ai.AiFallbackPolicy
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Ответ локальной модели на одну задачу. */
sealed interface AiOutcome<out T> {
    data class Done<T>(val value: T) : AiOutcome<T>

    /** Модель ответила, но ответ не прошёл проверку (или задача невыполнима): больше не спрашиваем. */
    data class Rejected(val reason: String) : AiOutcome<Nothing>

    /** Модель сейчас недоступна (не скачана, выключена, занята, приложение в фоне): повторим позже. */
    data object Unavailable : AiOutcome<Nothing>
}

/**
 * Разбор одного сообщения моделью. Реализация (`:app`) строит промпт, разбирает JSON и сверяет
 * ответ с текстом (`AiExtractionValidator`); здесь получаем уже проверенный результат с
 * `parserId = "ai"`.
 */
fun interface AiMessageReader {
    suspend fun read(text: String, meta: MessageMeta, regexResult: ParseResult): AiOutcome<ParseResult.Parsed>
}

/** Ручное решение пользователя «мерчант → категория» — пример для модели. */
data class CategoryExample(val merchant: String, val categoryName: String)

/**
 * Ответ модели по мерчанту.
 * @property displayName короткое понятное имя (ещё не проверено).
 * @property categoryName название категории из переданного списка (ещё не проверено).
 */
data class MerchantAdvice(val displayName: String?, val categoryName: String?)

/** Имя и категория мерчанта от модели. */
fun interface AiMerchantAdvisor {
    suspend fun advise(merchant: String, categories: List<Category>, examples: List<CategoryExample>): AiOutcome<MerchantAdvice>
}

/**
 * Итог прохода.
 * @property stopped модель стала недоступна посреди прохода — остальное ждёт следующего запуска.
 */
data class AiBackfillReport(
    val messagesSent: Int,
    val operationsRecorded: Int,
    val merchantsDescribed: Int,
    val recategorized: Int,
    val stopped: Boolean,
)

/**
 * Фоновая дообработка локальной моделью (запускается WorkManager-задачей `:app`, когда модель готова).
 * Приём сообщений модель никогда не ждёт: всё, что здесь, — догоняющий проход.
 *
 * 1. **Сообщения.** `RawMessage` со статусом UNCERTAIN (в том числе с операцией на проверке) или
 *    NOT_FINANCIAL, которые модели ещё не отдавали. Отбор — `AiFallbackPolicy` по текущему разбору
 *    regex-парсера; операции, которые пользователь правил или подтверждал, не трогаются. Принятый
 *    ответ записывается обычным путём записи операций (дедуп, категории, остаток) с `parserId = "ai"`.
 *    Каждое сообщение отдаётся модели один раз (`ai_attempted_at`), кроме случая «модель недоступна».
 * 2. **Мерчанты.** Для каждого нового мерчанта (ключ — `MerchantNormalizer.canonical`) модель один раз
 *    предлагает понятное имя и категорию из списка категорий пользователя; примеры — последние ручные
 *    решения пользователя (правила и правленые операции). Имя принимается, только если узнаётся в
 *    сыром названии ([MerchantAliasRules]); имя пользователя не перезаписывается. Предложенная
 *    категория назначается операциям этого мерчанта, у которых правило и словарь ничего не дали
 *    (сейчас «Другое»), с пометкой «предложено ИИ» и в очередь проверки.
 */
@Singleton
class AiBackfillUseCase @Inject constructor(
    private val db: FoldFinanceDatabase,
    private val registry: ParserRegistry,
    @DataClock private val clock: Clock,
) {
    private val rawDao = db.rawMessageDao()
    private val txDao = db.transactionDao()
    private val aliasDao = db.merchantAliasDao()
    private val writer = OperationWriter(db, clock)

    /** Есть ли что отдать модели (дёшево: без вызова модели). */
    suspend fun hasPendingWork(): Boolean = rawDao.countAiCandidates() > 0 || pendingMerchants().isNotEmpty()

    suspend operator fun invoke(
        reader: AiMessageReader,
        advisor: AiMerchantAdvisor,
        maxMessages: Int = DEFAULT_MAX_MESSAGES,
        maxMerchants: Int = DEFAULT_MAX_MERCHANTS,
    ): AiBackfillReport {
        val messages = processMessages(reader, maxMessages)
        if (messages.stopped) return AiBackfillReport(messages.sent, messages.recorded, 0, 0, stopped = true)
        val merchants = processMerchants(advisor, maxMerchants)
        return AiBackfillReport(
            messagesSent = messages.sent,
            operationsRecorded = messages.recorded,
            merchantsDescribed = merchants.described,
            recategorized = merchants.recategorized,
            stopped = merchants.stopped,
        )
    }

    // ------------------------------------------------------------------ сообщения

    private class MessagesPass(val sent: Int, val recorded: Int, val stopped: Boolean)

    private suspend fun processMessages(reader: AiMessageReader, max: Int): MessagesPass {
        var afterId = 0L
        var sent = 0
        var recorded = 0
        while (sent < max) {
            val batch = rawDao.getAiCandidates(afterId, BATCH)
            if (batch.isEmpty()) break
            for (raw in batch) {
                if (sent >= max) break
                afterId = raw.id
                val meta = MessageMeta(raw.channel, raw.sender, raw.receivedAt.toEpochMilli(), raw.title)
                val regex = registry.parse(raw.text, meta)
                if (!eligible(raw, meta, regex)) {
                    rawDao.markAiAttempted(raw.id, clock.millis())
                    continue
                }
                sent++
                when (val outcome = reader.read(raw.text, meta, regex)) {
                    AiOutcome.Unavailable -> return MessagesPass(sent - 1, recorded, stopped = true)
                    is AiOutcome.Rejected -> rawDao.markAiAttempted(raw.id, clock.millis())
                    is AiOutcome.Done -> recorded += record(raw.id, outcome.value)
                }
            }
        }
        return MessagesPass(sent, recorded, stopped = false)
    }

    private suspend fun eligible(raw: RawMessageEntity, meta: MessageMeta, regex: ParseResult): Boolean {
        if (!AiFallbackPolicy.isCandidate(raw.text, meta, regex)) return false
        // Пользователь уже разобрался с операцией этого сообщения — модель не нужна.
        val existing = txDao.getByRawMessage(raw.id) + listOfNotNull(raw.transactionId?.let { txDao.getById(it) })
        return existing.none { it.userEdited || it.reviewConfirmed }
    }

    /** @return сколько операций создано или обновлено. */
    private suspend fun record(rawId: Long, result: ParseResult.Parsed): Int {
        require(result.parserId == AI_PARSER_ID) { "AI result must carry parserId=$AI_PARSER_ID" }
        return db.withTransaction {
            val current = rawDao.getById(rawId) ?: return@withTransaction 0
            val stillCandidate = current.aiAttemptedAt == null && !current.userDeleted &&
                (current.parseStatus == ParseStatus.UNCERTAIN || current.parseStatus == ParseStatus.NOT_FINANCIAL)
            val applied = if (stillCandidate) writer.apply(current, result) else null
            rawDao.markAiAttempted(rawId, clock.millis())
            applied?.let { it.created + it.merged + it.updated } ?: 0
        }
    }

    // ------------------------------------------------------------------ мерчанты

    private class MerchantsPass(val described: Int, val recategorized: Int, val stopped: Boolean)

    /** Ключ → сырые названия мерчантов, по которым модель ещё не спрашивали. */
    private suspend fun pendingMerchants(): Map<String, List<String>> {
        val checked = aliasDao.getCheckedKeys().toHashSet()
        return txDao.getDistinctMerchants()
            .groupBy { MerchantNormalizer.canonical(it) }
            .filterKeys { it.isNotEmpty() && it !in checked }
    }

    private suspend fun processMerchants(advisor: AiMerchantAdvisor, max: Int): MerchantsPass {
        val pending = pendingMerchants()
        if (pending.isEmpty()) return MerchantsPass(0, 0, stopped = false)
        val categories = db.categoryDao().getAll().map { it.toModel() }
        val offered = categories.filter { it.key != DefaultCategories.OTHER_KEY && it.key != DefaultCategories.TRANSFERS_KEY }
        val examples = examples(categories)
        var described = 0
        var recategorized = 0
        for ((key, names) in pending.entries.take(max)) {
            val sample = names.minBy { it.length }
            when (val outcome = advisor.advise(sample, offered, examples)) {
                AiOutcome.Unavailable -> return MerchantsPass(described, recategorized, stopped = true)
                is AiOutcome.Rejected -> saveAdvice(key, name = null, categoryId = null)
                is AiOutcome.Done -> {
                    val name = outcome.value.displayName?.trim()?.takeIf { MerchantAliasRules.isPlausible(sample, it) }
                    val categoryId = outcome.value.categoryName?.let { categoryByName(it, offered) }
                    saveAdvice(key, name, categoryId)
                    if (categoryId != null) recategorized += recategorize(key)
                }
            }
            described++
        }
        return MerchantsPass(described, recategorized, stopped = false)
    }

    private fun categoryByName(name: String, offered: List<Category>): Long? {
        val wanted = TextNorm.normalize(name.trim().trim('"', '«', '»', '.')) ?: return null
        return offered.firstOrNull { TextNorm.normalize(it.name) == wanted || it.key == wanted }?.id
    }

    private suspend fun saveAdvice(key: String, name: String?, categoryId: Long?) {
        db.withTransaction {
            val now = clock.instant()
            val existing = aliasDao.get(key)
            aliasDao.upsert(
                existing?.copy(
                    aiName = name,
                    aiCategoryId = categoryId,
                    categoryConfirmed = existing.categoryConfirmed && existing.aiCategoryId == categoryId,
                    aiCheckedAt = now,
                    updatedAt = now,
                ) ?: MerchantAliasEntity(
                    merchantKey = key,
                    aiName = name,
                    userName = null,
                    aiCategoryId = categoryId,
                    aiCheckedAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    /** Назначить категорию модели операциям мерчанта, где правило и словарь ничего не дали. */
    private suspend fun recategorize(key: String): Int = db.withTransaction {
        val alias = aliasDao.get(key) ?: return@withTransaction 0
        val categories = db.categoryDao().getAll().map { it.toModel() }
        val rules = db.merchantRuleDao().getAll().map { it.toModel() }
        val otherId = categories.firstOrNull { it.key == DefaultCategories.OTHER_KEY }?.id
        val now = clock.instant()
        var count = 0
        txDao.getAiRecategorizable(otherId)
            .filter { MerchantNormalizer.canonical(it.merchant) == key }
            .forEach { tx ->
                val resolution = Categorizer.resolveWithSource(
                    merchant = tx.merchant,
                    direction = tx.direction,
                    kind = tx.kind,
                    rules = rules,
                    categories = categories,
                    aiCategoryId = alias.aiCategoryId,
                )
                if (resolution.source == CategorySource.AI && resolution.categoryId != tx.categoryId) {
                    val pendingConfirmation = !alias.categoryConfirmed
                    txDao.update(
                        tx.copy(
                            categoryId = resolution.categoryId,
                            aiCategory = pendingConfirmation,
                            needsReview = tx.needsReview || pendingConfirmation,
                            updatedAt = now,
                        ),
                    )
                    count++
                }
            }
        count
    }

    /** Последние ручные решения пользователя: правила и правленые операции, по одному на мерчанта. */
    private suspend fun examples(categories: List<Category>): List<CategoryExample> {
        val names = categories.associate { it.id to it.name }
        val fromRules = db.merchantRuleDao().getAll()
            .sortedByDescending { it.createdAt }
            .mapNotNull { r -> names[r.categoryId]?.let { CategoryExample(r.merchantPattern, it) } }
        val fromEdits = txDao.getUserCategoryExamples(EXAMPLE_SCAN)
            .mapNotNull { t -> t.categoryId?.let(names::get)?.let { CategoryExample(requireNotNull(t.merchant), it) } }
        return (fromEdits + fromRules)
            .filter { MerchantNormalizer.canonical(it.merchant).isNotEmpty() }
            .distinctBy { MerchantNormalizer.canonical(it.merchant) }
            .take(MAX_EXAMPLES)
    }

    companion object {
        const val DEFAULT_MAX_MESSAGES: Int = 40
        const val DEFAULT_MAX_MERCHANTS: Int = 40
        const val MAX_EXAMPLES: Int = 8
        private const val EXAMPLE_SCAN = 40
        private const val BATCH = 50
    }
}
