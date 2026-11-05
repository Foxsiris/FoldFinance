package com.foldfinance.app.ai.features.common

import com.foldfinance.app.ai.AiResponse
import com.foldfinance.app.ai.AiStatus
import com.foldfinance.app.ai.LocalAi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What an AI element shows for the current [AiStatus]: nothing at all, «Модель загружается…»,
 * or the feature. Every AI element is hidden unless the model is [Ready] (CLAUDE.md: what is not
 * ready is hidden, no dead buttons).
 */
sealed interface AiAvailability {
    data object Hidden : AiAvailability

    /** @property progress 0…1 when AICore reports the size, otherwise `null`. */
    data class Loading(val progress: Float?) : AiAvailability

    data object Ready : AiAvailability
}

fun AiStatus.toAvailability(): AiAvailability = when (this) {
    AiStatus.Ready -> AiAvailability.Ready
    is AiStatus.Downloading -> AiAvailability.Loading(
        totalBytes?.takeIf { it > 0 }?.let { (bytesDownloaded.toDouble() / it).toFloat().coerceIn(0f, 1f) },
    )
    AiStatus.Checking, AiStatus.Downloadable, is AiStatus.Unavailable -> AiAvailability.Hidden
}

/**
 * Whether the model accepts images (receipts). The [LocalAi] contract has no capability flag,
 * so this starts optimistic once the model is ready and switches off for the session after a
 * request with an image fails because images are unsupported. When `LocalAi` gets an explicit
 * flag, bind an implementation that reads it instead (one line in `AiFeaturesModule`).
 */
interface ImageInputSupport {
    val supported: Flow<Boolean>

    /** Called with the failure reason of a request that carried images. */
    fun onImageRequestFailed(reason: String)
}

@Singleton
class ProbingImageInputSupport @Inject constructor(ai: LocalAi) : ImageInputSupport {
    private val unsupported = MutableStateFlow(false)

    override val supported: Flow<Boolean> =
        combine(ai.status, unsupported) { status, off -> status == AiStatus.Ready && !off }

    override fun onImageRequestFailed(reason: String) {
        val r = reason.lowercase(Locale.ROOT)
        if (listOf("image", "multimodal", "unsupported", "not_supported", "not supported").any { it in r }) {
            unsupported.value = true
        }
    }
}

/** Text of a successful response, or `null` (the caller falls back to its non-AI path). */
val AiResponse.textOrNull: String? get() = (this as? AiResponse.Text)?.text
