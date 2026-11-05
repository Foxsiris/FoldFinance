package com.foldfinance.app.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.prompt.Content
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.content
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Тонкая прослойка над ML Kit GenAI Prompt API (`com.google.mlkit:genai-prompt:1.0.0-beta5`), чтобы
 * логику [MlKitLocalAi] (статусы, таймауты, ошибки) можно было проверить без AICore.
 * Исключения ML Kit (`GenAiException`) пробрасываются — их разбирает [MlKitLocalAi].
 */
interface NanoBackend {
    /** `com.google.mlkit.genai.common.FeatureStatus`: UNAVAILABLE / DOWNLOADABLE / DOWNLOADING / AVAILABLE. */
    suspend fun checkStatus(tier: AiTier): Int

    /** Загрузка модели системным AICore (сам файл приложению не нужен, сеть — у AICore). */
    fun download(tier: AiTier): Flow<DownloadStatus>

    /** Текст первого кандидата ответа или `null`, если кандидатов нет. */
    suspend fun generate(tier: AiTier, request: AiRequest): String?
}

/**
 * Реализация на ML Kit. Вариант модели выбирается через `ModelConfig.preference`:
 * [AiTier.FAST] → `ModelPreference.FAST`, [AiTier.FULL] → `ModelPreference.FULL`; стадия релиза —
 * `ModelReleaseStage.STABLE`. Какая именно модель (Nano v2/v3/…) стоит за предпочтением, решает AICore.
 *
 * Системная инструкция передаётся как `SystemInstruction`, если AICore её поддерживает
 * (`isSystemPromptAvailable`), иначе приклеивается к началу промпта. Картинки — частями `Content`.
 */
@Singleton
class MlKitNanoBackend @Inject constructor() : NanoBackend {

    private val clients = ConcurrentHashMap<AiTier, GenerativeModel>()
    private val systemPromptSupport = ConcurrentHashMap<AiTier, Boolean>()

    private fun client(tier: AiTier): GenerativeModel = clients.getOrPut(tier) {
        Generation.getClient(
            generationConfig {
                modelConfig = modelConfig {
                    preference = when (tier) {
                        AiTier.FAST -> ModelPreference.FAST
                        AiTier.FULL -> ModelPreference.FULL
                    }
                    releaseStage = ModelReleaseStage.STABLE
                }
            },
        )
    }

    override suspend fun checkStatus(tier: AiTier): Int = client(tier).checkStatus()

    override fun download(tier: AiTier): Flow<DownloadStatus> = client(tier).download()

    override suspend fun generate(tier: AiTier, request: AiRequest): String? {
        val model = client(tier)
        val system = request.system?.takeIf { it.isNotBlank() }
        val nativeSystem = system != null && supportsSystemPrompt(tier, model)
        val text = buildString {
            if (system != null && !nativeSystem) append(system).append("\n\n")
            append(request.prompt)
            if (request.jsonOnly) append(JSON_ONLY_SUFFIX)
        }
        val builder = if (request.images.isEmpty()) {
            GenerateContentRequest.Builder(TextPart(text))
        } else {
            val parts: Content = content {
                request.images.forEach { image(it) }
                text(text)
            }
            GenerateContentRequest.Builder(parts)
        }
        builder.temperature = request.temperature
        builder.maxOutputTokens = request.maxOutputTokens
        if (nativeSystem) builder.systemInstruction = SystemInstruction(requireNotNull(system))
        return model.generateContent(builder.build()).candidates.firstOrNull()?.text
    }

    private suspend fun supportsSystemPrompt(tier: AiTier, model: GenerativeModel): Boolean {
        systemPromptSupport[tier]?.let { return it }
        val supported = model.isSystemPromptAvailable()
        systemPromptSupport[tier] = supported
        return supported
    }

    private companion object {
        const val JSON_ONLY_SUFFIX = "\n\nОтветь только одним JSON-объектом, без пояснений и без Markdown."
    }
}
