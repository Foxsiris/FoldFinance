package com.foldfinance.core.data.subscriptions

import com.foldfinance.core.data.repository.SubscriptionRepository
import com.foldfinance.core.data.repository.TransactionFilter
import com.foldfinance.core.data.repository.TransactionRepository
import com.foldfinance.core.data.repository.TransactionSort
import com.foldfinance.core.di.DataClock
import com.foldfinance.core.domain.category.MerchantNormalizer
import com.foldfinance.core.domain.subscriptions.SubscriptionCandidate
import com.foldfinance.core.domain.subscriptions.SubscriptionDetector
import com.foldfinance.core.model.Subscription
import com.foldfinance.core.model.SubscriptionStatus
import com.foldfinance.parser.Direction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Итог обновления подписок.
 *
 * @property detected сколько серий нашёл детектор.
 * @property inserted новых кандидатов «Похоже на подписку».
 * @property updated обновлённых записей (кандидаты с новыми суммами/датами, расписание подтверждённых).
 * @property removed кандидатов, которые больше не находятся (серия прервалась) и были удалены.
 */
data class RefreshSubscriptionsReport(
    val detected: Int,
    val inserted: Int,
    val updated: Int,
    val removed: Int,
)

/**
 * Обновляет таблицу подписок по истории операций (этап 8).
 *
 * Берёт расходы за последние [HISTORY_DAYS] дней, запускает [SubscriptionDetector] и сверяет
 * найденные серии с сохранёнными подписками по ключу «канонический мерчант + валюта + период»
 * (при нескольких записях с тем же ключом — ближайшая по сумме, в пределах [MATCH_TOLERANCE_PERCENT]%).
 *
 * Решения пользователя не перезаписываются:
 * - [SubscriptionStatus.DISMISSED] («Не подписка») не трогается вообще и не воскрешается кандидатом;
 * - у [SubscriptionStatus.CONFIRMED] статус, мерчант, сумма и категория остаются как есть; при новом
 *   списании обновляется только расписание — `lastChargeAt` и `nextChargeAt`, чтобы календарь был верным;
 * - [SubscriptionStatus.CANDIDATE] обновляется до свежих значений детектора, а если серия больше не
 *   находится — удаляется (пользователь о нём ещё ничего не решал).
 *
 * Идемпотентно: повторный запуск на тех же данных ничего не меняет. Параллельные запуски
 * выполняются по очереди.
 */
@Singleton
class RefreshSubscriptionsUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val subscriptions: SubscriptionRepository,
    @DataClock private val clock: Clock,
) {
    private val mutex = Mutex()

    /**
     * @param zone часовой пояс календарной арифметики следующего списания (день месяца).
     */
    suspend operator fun invoke(zone: ZoneId = ZoneId.systemDefault()): RefreshSubscriptionsReport = mutex.withLock {
        val now = clock.instant()
        val history = transactions.observe(
            TransactionFilter(
                from = now.minus(Duration.ofDays(HISTORY_DAYS)),
                directions = setOf(Direction.EXPENSE),
                sort = TransactionSort.DATE_ASC,
            ),
        ).first()
        val detected = SubscriptionDetector.detect(history, now, zone)
        val existing = subscriptions.observeAll().first()

        val unmatched = existing.toMutableList()
        var inserted = 0
        var updated = 0
        for (candidate in detected) {
            val match = bestMatch(candidate, unmatched)
            if (match == null) {
                subscriptions.upsert(candidate.toSubscription(SubscriptionStatus.CANDIDATE))
                inserted++
                continue
            }
            unmatched -= match
            val refreshed = when (match.status) {
                SubscriptionStatus.DISMISSED -> match
                SubscriptionStatus.CONFIRMED ->
                    if (candidate.lastChargeAt.isAfter(match.lastChargeAt)) {
                        match.copy(lastChargeAt = candidate.lastChargeAt, nextChargeAt = candidate.nextChargeAt)
                    } else {
                        match
                    }
                SubscriptionStatus.CANDIDATE ->
                    candidate.toSubscription(SubscriptionStatus.CANDIDATE).copy(id = match.id)
            }
            if (refreshed != match) {
                subscriptions.upsert(refreshed)
                updated++
            }
        }

        val stale = unmatched.filter { it.status == SubscriptionStatus.CANDIDATE }
        stale.forEach { subscriptions.delete(it.id) }

        RefreshSubscriptionsReport(
            detected = detected.size,
            inserted = inserted,
            updated = updated,
            removed = stale.size,
        )
    }

    private fun bestMatch(candidate: SubscriptionCandidate, pool: List<Subscription>): Subscription? =
        pool.asSequence()
            .filter {
                it.currency == candidate.currency &&
                    it.period == candidate.period &&
                    MerchantNormalizer.canonical(it.merchant) == candidate.merchantKey
            }
            .filter { withinTolerance(it.amountMinor, candidate.typicalAmountMinor) }
            .minByOrNull { Math.abs(it.amountMinor - candidate.typicalAmountMinor) }

    private fun withinTolerance(a: Long, b: Long): Boolean =
        Math.abs(a - b) * 100 <= maxOf(a, b) * MATCH_TOLERANCE_PERCENT

    companion object {
        /** Глубина истории для детектора: год и запас, чтобы годовые подписки находились по двум списаниям. */
        const val HISTORY_DAYS: Long = 400

        /**
         * Насколько может отличаться сумма сохранённой подписки от новой медианы серии, чтобы это
         * считалось той же подпиской (подорожание тарифа), %. Шире допуска детектора (10%).
         */
        const val MATCH_TOLERANCE_PERCENT: Int = 25
    }
}
