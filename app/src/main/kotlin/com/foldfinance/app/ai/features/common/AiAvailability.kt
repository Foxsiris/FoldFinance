package com.foldfinance.app.ai.features.common

import com.foldfinance.app.ai.AiResponse
import com.foldfinance.app.ai.AiStatus

/**
 * What an AI element shows for the current [AiStatus]: nothing at all or the feature. Every AI
 * element is hidden unless the model is [Ready] (CLAUDE.md: what is not ready is hidden, no dead
 * buttons); the download and its progress are shown only in Settings → «Локальный ИИ».
 */
sealed interface AiAvailability {
    data object Hidden : AiAvailability

    data object Ready : AiAvailability
}

fun AiStatus.toAvailability(): AiAvailability =
    if (this == AiStatus.Ready) AiAvailability.Ready else AiAvailability.Hidden

/** Text of a successful response, or `null` (the caller falls back to its non-AI path). */
val AiResponse.textOrNull: String? get() = (this as? AiResponse.Text)?.text
