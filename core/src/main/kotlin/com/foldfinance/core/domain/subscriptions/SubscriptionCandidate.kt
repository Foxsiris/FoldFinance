package com.foldfinance.core.domain.subscriptions

import com.foldfinance.core.model.Subscription
import com.foldfinance.core.model.SubscriptionPeriod
import com.foldfinance.core.model.SubscriptionStatus
import java.time.Instant

/**
 * Найденный детектором регулярный платёж — «Похоже на подписку, подтвердить?».
 *
 * @property merchant название мерчанта из последнего списания (для показа).
 * @property merchantKey канонический ключ мерчанта ([com.foldfinance.core.domain.category.MerchantNormalizer.canonical]).
 * @property typicalAmountMinor медиана сумм списаний.
 * @property lastAmountMinor сумма последнего списания.
 * @property lastChargeAt последнее списание.
 * @property nextChargeAt ожидаемое следующее списание (для месячных — с переносом на последний
 *   день короткого месяца: 31 янв → 28/29 фев → 31 мар).
 * @property occurrences число списаний в найденной серии.
 * @property transactionIds id операций серии по времени.
 * @property categoryId самая частая категория операций серии.
 * @property isActive следующее списание ещё не просрочено больше допуска периода.
 */
data class SubscriptionCandidate(
    val merchant: String,
    val merchantKey: String,
    val currency: String,
    val typicalAmountMinor: Long,
    val lastAmountMinor: Long,
    val period: SubscriptionPeriod,
    val lastChargeAt: Instant,
    val nextChargeAt: Instant,
    val occurrences: Int,
    val transactionIds: List<Long>,
    val categoryId: Long?,
    val isActive: Boolean,
) {
    /** Доменная [Subscription] для сохранения (по умолчанию со статусом CANDIDATE). */
    fun toSubscription(status: SubscriptionStatus = SubscriptionStatus.CANDIDATE): Subscription = Subscription(
        merchant = merchant,
        amountMinor = typicalAmountMinor,
        currency = currency,
        period = period,
        lastChargeAt = lastChargeAt,
        nextChargeAt = nextChargeAt,
        status = status,
        categoryId = categoryId,
    )
}
