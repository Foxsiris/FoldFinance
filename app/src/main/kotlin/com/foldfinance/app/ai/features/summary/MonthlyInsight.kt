package com.foldfinance.app.ai.features.summary

import com.foldfinance.app.ai.AiRequest
import com.foldfinance.app.ai.AiResponse
import com.foldfinance.app.ai.AiTier
import com.foldfinance.app.ai.LocalAi
import com.foldfinance.app.ai.features.common.Fact
import com.foldfinance.app.ai.features.common.FactFormat
import com.foldfinance.app.ai.features.common.NumberGuard
import com.foldfinance.core.domain.analytics.AmountChange
import com.foldfinance.core.domain.analytics.AnalyticsCalculator
import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.domain.category.MerchantNormalizer
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.Subscription
import com.foldfinance.core.model.SubscriptionStatus
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** A category whose spending changed. [category] `null` — operations without a category. */
data class CategoryChange(val category: Category?, val change: AmountChange)

/** A subscription that started recently. */
data class NewSubscription(val merchant: String, val amountMinor: Long, val currency: String)

/**
 * Everything the monthly summary may say, computed by Kotlin (п.7).
 *
 * @property total net expenses now vs before, in [currency].
 * @property increases categories that grew, largest growth first (≤ 3).
 * @property decreases categories that fell, largest drop first (≤ 2).
 * @property newSubscriptions subscriptions whose first charge is in [previous] or [current].
 */
data class MonthlyFacts(
    val current: Period,
    val previous: Period,
    val currency: String,
    val total: AmountChange,
    val increases: List<CategoryChange>,
    val decreases: List<CategoryChange>,
    val newSubscriptions: List<NewSubscription>,
    val operationCount: Int,
) {
    val hasData: Boolean get() = total.currentMinor != 0L || total.previousMinor != 0L

    /** Facts for the model, with ids it uses as placeholders. Values are formatted here. */
    fun toFacts(): List<Fact> = buildList {
        add(Fact("period", FactFormat.period(current)))
        add(Fact("prev_period", FactFormat.period(previous)))
        add(Fact("total", FactFormat.money(total.currentMinor, currency)))
        add(Fact("total_prev", FactFormat.money(total.previousMinor, currency)))
        add(Fact("total_delta", FactFormat.signedMoney(total.deltaMinor, currency)))
        FactFormat.percent(total)?.let { add(Fact("total_pct", it)) }
        increases.forEachIndexed { i, c ->
            add(Fact("up${i + 1}_name", c.category?.name ?: NO_CATEGORY))
            add(Fact("up${i + 1}_now", FactFormat.money(c.change.currentMinor, currency)))
            add(Fact("up${i + 1}_delta", FactFormat.signedMoney(c.change.deltaMinor, currency)))
        }
        decreases.forEachIndexed { i, c ->
            add(Fact("down${i + 1}_name", c.category?.name ?: NO_CATEGORY))
            add(Fact("down${i + 1}_delta", FactFormat.signedMoney(c.change.deltaMinor, currency)))
        }
        newSubscriptions.forEachIndexed { i, s ->
            add(Fact("sub${i + 1}_name", s.merchant))
            add(Fact("sub${i + 1}_amount", FactFormat.money(s.amountMinor, s.currency)))
        }
    }

    companion object {
        /** Name of the «no category» bucket in facts; data shown to the model, matches the UI wording. */
        const val NO_CATEGORY = "Без категории"
    }
}

object MonthlyFactsBuilder {

    /** Up to this day of the month the summary is about the previous, complete month. */
    const val LAST_MONTH_UNTIL_DAY: Int = 5

    /**
     * Periods to compare. Early in a month — the last complete month with the one before it;
     * later — this month so far with the same days of the previous month (never a partial month
     * against a full one).
     */
    fun periods(today: LocalDate, zone: ZoneId): Pair<Period, Period> {
        if (today.dayOfMonth <= LAST_MONTH_UNTIL_DAY) {
            val last = Period.month(YearMonth.from(today).minusMonths(1), zone)
            return last to last.previous()
        }
        val cur = Period.custom(today.withDayOfMonth(1), today, zone)
        val prevMonth = YearMonth.from(today).minusMonths(1)
        val prevEnd = prevMonth.atDay(minOf(today.dayOfMonth, prevMonth.lengthOfMonth()))
        return cur to Period.custom(prevMonth.atDay(1), prevEnd, zone)
    }

    fun build(
        transactions: List<Transaction>,
        categories: List<Category>,
        subscriptions: List<Subscription>,
        today: LocalDate,
        zone: ZoneId,
        currency: String,
    ): MonthlyFacts {
        val (current, previous) = periods(today, zone)
        val cur = AnalyticsCalculator.summarize(transactions, current)
        val prev = AnalyticsCalculator.summarize(transactions, previous)
        val cmp = AnalyticsCalculator.compare(cur, prev).byCurrency[currency]
        val byId = categories.associateBy { it.id }
        val changes = cmp?.categories.orEmpty().map { (id, change) -> CategoryChange(id?.let(byId::get), change) }
        return MonthlyFacts(
            current = current,
            previous = previous,
            currency = currency,
            total = cmp?.netExpense ?: AmountChange(0, 0),
            increases = changes.filter { it.change.deltaMinor > 0 }.sortedByDescending { it.change.deltaMinor }.take(3),
            decreases = changes.filter { it.change.deltaMinor < 0 }.sortedBy { it.change.deltaMinor }.take(2),
            newSubscriptions = newSubscriptions(transactions, subscriptions, previous.startDate, zone),
            operationCount = cur.currency(currency).operationCount,
        )
    }

    /** Non-dismissed subscriptions whose earliest charge in history is on or after [since]. */
    fun newSubscriptions(transactions: List<Transaction>, subscriptions: List<Subscription>, since: LocalDate, zone: ZoneId): List<NewSubscription> {
        val firstCharge = transactions.asSequence()
            .filter { it.direction == Direction.EXPENSE }
            .groupBy { MerchantNormalizer.canonical(it.merchant) to it.currency }
            .mapValues { (_, list) -> list.minOf { it.occurredAt } }
        return subscriptions
            .filter { it.status != SubscriptionStatus.DISMISSED }
            .mapNotNull { sub ->
                val first = firstCharge[MerchantNormalizer.canonical(sub.merchant) to sub.currency] ?: return@mapNotNull null
                if (first.atZone(zone).toLocalDate().isBefore(since)) null else NewSubscription(sub.merchant, sub.amountMinor, sub.currency)
            }
            .take(2)
    }
}

/** Text of the monthly card: model prose that passed [NumberGuard], or the app's template. */
sealed interface InsightText {
    data class Model(val text: String) : InsightText
    data object Template : InsightText
}

/**
 * Month summary cache, in memory only: the generated text contains amounts, and the app keeps
 * financial data only in the encrypted database. The key includes the facts, so new operations
 * produce a new text; the same facts never call the model twice in a process.
 */
@Singleton
class MonthlyInsightCache @Inject constructor() {
    private val map = ConcurrentHashMap<MonthlyFacts, InsightText>()

    operator fun get(facts: MonthlyFacts): InsightText? = map[facts]

    operator fun set(facts: MonthlyFacts, text: InsightText) {
        // One month at a time: older months are dropped.
        map.keys.removeAll { it.current != facts.current }
        map[facts] = text
    }
}

/**
 * The model writes 3–4 sentences from [MonthlyFacts] only, with placeholders instead of numbers;
 * [NumberGuard] checks the text. Any failure gives [InsightText.Template].
 */
class MonthlyInsightGenerator @Inject constructor(
    private val ai: LocalAi,
    private val cache: MonthlyInsightCache,
) {
    suspend fun generate(facts: MonthlyFacts): InsightText {
        cache[facts]?.let { return it }
        val list = facts.toFacts()
        val response = ai.generate(
            AiRequest(
                prompt = prompt(list),
                system = SYSTEM,
                tier = AiTier.FULL,
                temperature = 0.3f,
                maxOutputTokens = 320,
            ),
        )
        val text = when (response) {
            is AiResponse.Failed -> InsightText.Template
            is AiResponse.Text -> when (val guarded = NumberGuard.render(response.text, list)) {
                is NumberGuard.Result.Accepted -> InsightText.Model(guarded.text)
                is NumberGuard.Result.Rejected -> InsightText.Template
            }
        }
        // A model failure is not cached: the next composition may try again.
        if (response is AiResponse.Text) cache[facts] = text
        return text
    }

    companion object {
        val SYSTEM: String = """
            Ты пишешь короткую сводку расходов за месяц: 3–4 простых предложения по-русски, без списков и заголовков.
            Используй только факты из списка. Никаких цифр, сумм, процентов и количеств словами — вместо них ставь
            плейсхолдер факта в фигурных скобках, например {total} или {up1_delta}. Названия тоже через плейсхолдеры: {up1_name}.
            Ничего не придумывай, не давай советов, не оценивай пользователя.
        """.trimIndent()

        fun prompt(facts: List<Fact>): String = buildString {
            appendLine("Факты:")
            facts.forEach { appendLine("{${it.id}} = ${it.value}") }
            append("Напиши сводку.")
        }
    }
}
