package com.foldfinance.core.data.db

import com.foldfinance.core.data.db.entity.CardEntity
import com.foldfinance.core.data.db.entity.CategoryEntity
import com.foldfinance.core.data.db.entity.KnownBalanceEntity
import com.foldfinance.core.data.db.entity.MerchantAliasEntity
import com.foldfinance.core.data.db.entity.MerchantRuleEntity
import com.foldfinance.core.data.db.entity.RawMessageEntity
import com.foldfinance.core.data.db.entity.SubscriptionEntity
import com.foldfinance.core.data.db.entity.TransactionEntity
import com.foldfinance.core.data.db.entity.TransactionSplitEntity
import com.foldfinance.core.model.Card
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.KnownBalance
import com.foldfinance.core.model.MerchantAlias
import com.foldfinance.core.model.AliasSource
import com.foldfinance.core.domain.category.MerchantNormalizer
import com.foldfinance.core.model.MerchantRule
import com.foldfinance.core.model.RawMessage
import com.foldfinance.core.model.Subscription
import com.foldfinance.core.model.Transaction
import com.foldfinance.core.model.TransactionSplit

/**
 * @param displayName отображаемое имя мерчанта из `merchant_alias` (см. [aliasDisplayNames]).
 * @param splits части операции из `transaction_split`.
 */
internal fun TransactionEntity.toModel(
    displayName: String? = null,
    splits: List<TransactionSplit> = emptyList(),
): Transaction = Transaction(
    id = id,
    occurredAt = occurredAt,
    amountMinor = amountMinor,
    currency = currency,
    direction = direction,
    kind = kind,
    merchant = merchant,
    cardMask = cardMask,
    categoryId = categoryId,
    confidence = confidence,
    needsReview = needsReview,
    source = source,
    dedupKey = dedupKey,
    note = note,
    linkedTransactionId = linkedTransactionId,
    userEdited = userEdited,
    createdAt = createdAt,
    updatedAt = updatedAt,
    aiParsed = aiParsed,
    aiCategory = aiCategory,
    merchantDisplayName = displayName,
    splits = splits,
    counterpartyCardMask = counterpartyCardMask,
    parsedDirection = parsedDirection,
)

internal fun TransactionSplitEntity.toModel(): TransactionSplit = TransactionSplit(
    id = id,
    parentId = parentId,
    categoryId = categoryId,
    amountMinor = amountMinor,
    label = label,
)

/** Части операций по id родителя. */
internal fun List<TransactionSplitEntity>.byParent(): Map<Long, List<TransactionSplit>> =
    groupBy({ it.parentId }, { it.toModel() })

internal fun RawMessageEntity.toModel(): RawMessage = RawMessage(
    id = id,
    channel = channel,
    sender = sender,
    sourceKey = sourceKey,
    receivedAt = receivedAt,
    text = text,
    textHash = textHash,
    parseStatus = parseStatus,
    parserVersion = parserVersion,
    transactionId = transactionId,
    parserId = parserId,
)

internal fun CategoryEntity.toModel(): Category = Category(
    id = id,
    key = key,
    name = name,
    iconKey = iconKey,
    colorArgb = colorArgb,
    isSystem = isSystem,
    sortOrder = sortOrder,
)

internal fun MerchantRuleEntity.toModel(): MerchantRule = MerchantRule(
    id = id,
    merchantPattern = merchantPattern,
    categoryId = categoryId,
    createdAt = createdAt,
)

internal fun CardEntity.toModel(): Card = Card(mask = mask, label = label, isOwn = isOwn)

internal fun KnownBalanceEntity.toModel(): KnownBalance = KnownBalance(
    cardMask = cardMask,
    amountMinor = amountMinor,
    currency = currency,
    asOf = asOf,
    rawMessageId = rawMessageId,
)

internal fun SubscriptionEntity.toModel(): Subscription = Subscription(
    id = id,
    merchant = merchant,
    amountMinor = amountMinor,
    currency = currency,
    period = period,
    lastChargeAt = lastChargeAt,
    nextChargeAt = nextChargeAt,
    status = status,
    categoryId = categoryId,
)

internal fun Subscription.toEntity(): SubscriptionEntity = SubscriptionEntity(
    id = id,
    merchant = merchant,
    amountMinor = amountMinor,
    currency = currency,
    period = period,
    lastChargeAt = lastChargeAt,
    nextChargeAt = nextChargeAt,
    status = status,
    categoryId = categoryId,
)

internal fun MerchantAliasEntity.toModel(): MerchantAlias = MerchantAlias(
    merchantKey = merchantKey,
    displayName = userName ?: aiName,
    source = if (userName != null) AliasSource.USER else AliasSource.AI,
    aiCategoryId = aiCategoryId,
    categoryConfirmed = categoryConfirmed,
    updatedAt = updatedAt,
)

/** Ключ → отображаемое имя (имя пользователя приоритетнее имени модели); строки без имени пропускаются. */
internal fun List<MerchantAliasEntity>.aliasDisplayNames(): Map<String, String> =
    mapNotNull { a -> (a.userName ?: a.aiName)?.let { a.merchantKey to it } }.toMap()

/**
 * Операции с отображаемыми именами мерчантов и частями разбиения. Канонический ключ считается один
 * раз на мерчанта.
 */
internal fun List<TransactionEntity>.toModels(
    displayNames: Map<String, String>,
    splits: Map<Long, List<TransactionSplit>> = emptyMap(),
): List<Transaction> {
    val keys = HashMap<String, String>()
    return map { e ->
        val name = if (displayNames.isEmpty()) null else e.merchant?.let { m -> displayNames[keys.getOrPut(m) { MerchantNormalizer.canonical(m) }] }
        e.toModel(name, splits[e.id].orEmpty())
    }
}
