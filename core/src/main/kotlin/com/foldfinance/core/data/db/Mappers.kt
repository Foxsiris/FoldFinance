package com.foldfinance.core.data.db

import com.foldfinance.core.data.db.entity.CardEntity
import com.foldfinance.core.data.db.entity.CategoryEntity
import com.foldfinance.core.data.db.entity.KnownBalanceEntity
import com.foldfinance.core.data.db.entity.MerchantRuleEntity
import com.foldfinance.core.data.db.entity.RawMessageEntity
import com.foldfinance.core.data.db.entity.SubscriptionEntity
import com.foldfinance.core.data.db.entity.TransactionEntity
import com.foldfinance.core.model.Card
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.KnownBalance
import com.foldfinance.core.model.MerchantRule
import com.foldfinance.core.model.RawMessage
import com.foldfinance.core.model.Subscription
import com.foldfinance.core.model.Transaction

internal fun TransactionEntity.toModel(): Transaction = Transaction(
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
)

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
