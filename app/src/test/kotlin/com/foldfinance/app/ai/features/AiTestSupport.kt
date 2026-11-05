package com.foldfinance.app.ai.features

import com.foldfinance.app.ai.AiRequest
import com.foldfinance.app.ai.AiResponse
import com.foldfinance.app.ai.AiStatus
import com.foldfinance.app.ai.LocalAi
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.DefaultCategories
import com.foldfinance.core.model.Source
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import com.foldfinance.parser.Kind
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Scripted [LocalAi] for tests: records every request and answers with [respond].
 * The default answer is a failure, so a test that forgets to script the model sees the fallback.
 */
class FakeLocalAi(
    initial: AiStatus = AiStatus.Ready,
    var respond: (AiRequest) -> AiResponse = { AiResponse.Failed("not_scripted") },
) : LocalAi {
    val requests = mutableListOf<AiRequest>()
    val state = MutableStateFlow(initial)
    override val status = state
    val images = MutableStateFlow(false)
    override val supportsImages = images
    override suspend fun prepare() = Unit
    override suspend fun generate(request: AiRequest): AiResponse {
        requests += request
        return respond(request)
    }

    companion object {
        fun answering(text: String) = FakeLocalAi(respond = { AiResponse.Text(text) })
    }
}

/** Deterministic data for the AI feature tests (test-only, CLAUDE.md «Только реальные данные»). */
object AiData {
    val zone: ZoneId = ZoneId.of("Europe/Moscow")
    val today: LocalDate = LocalDate.of(2026, 11, 5)
    val now: Instant = LocalDateTime.of(2026, 11, 5, 14, 5).atZone(zone).toInstant()

    val categories: List<Category> = DefaultCategories.all.mapIndexed { index, seed ->
        Category(id = index + 1L, key = seed.key, name = seed.name, iconKey = seed.iconKey, isSystem = true, sortOrder = index)
    }

    fun category(key: String): Category = categories.first { it.key == key }

    private var nextId = 1000L

    fun tx(
        local: String,
        amountMinor: Long,
        merchant: String?,
        categoryKey: String?,
        direction: Direction = Direction.EXPENSE,
        currency: String = "RUB",
        id: Long = nextId++,
        card: String? = "*4821",
    ): Transaction {
        val at = LocalDateTime.parse(local).atZone(zone).toInstant()
        return Transaction(
            id = id,
            occurredAt = at,
            amountMinor = amountMinor,
            currency = currency,
            direction = direction,
            kind = if (direction == Direction.REFUND) Kind.REFUND else Kind.PURCHASE,
            merchant = merchant,
            cardMask = card,
            categoryId = categoryKey?.let { category(it).id },
            confidence = 0.95f,
            needsReview = false,
            source = Source.SMS,
            dedupKey = "ai-$id",
            createdAt = at,
            updatedAt = at,
        )
    }
}
