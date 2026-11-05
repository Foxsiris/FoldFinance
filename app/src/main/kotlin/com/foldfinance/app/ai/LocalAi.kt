package com.foldfinance.app.ai

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Локальная языковая модель на устройстве (Gemini Nano через AICore). Ничего не уходит в сеть:
 * у приложения нет INTERNET, модель скачивает и обновляет системный AICore.
 *
 * Правило проекта: модель никогда не считает деньги. Суммы, итоги и сравнения считает Kotlin;
 * модель только извлекает поля из текста, выбирает функцию/категорию или пишет текст по готовым цифрам.
 */
interface LocalAi {
    /** Готовность модели; UI показывает статус и прячет ИИ-функции, пока не [AiStatus.Ready]. */
    val status: StateFlow<AiStatus>

    /** Проверить доступность и, если модель можно скачать, запустить загрузку через AICore. */
    suspend fun prepare()

    /** Один запрос к модели. Никогда не бросает исключения: ошибка — [AiResponse.Failed]. */
    suspend fun generate(request: AiRequest): AiResponse
}

/** Вариант модели: FAST — разбор и классификация, FULL — рассуждения (вопросы, чеки). */
enum class AiTier { FAST, FULL }

/**
 * @property jsonOnly ответ должен быть одним JSON-объектом; вызывающий всё равно валидирует его сам.
 * @property images изображения для мультимодальных запросов (чеки); пусто для текстовых.
 */
data class AiRequest(
    val prompt: String,
    val system: String? = null,
    val images: List<Bitmap> = emptyList(),
    val tier: AiTier = AiTier.FAST,
    val temperature: Float = 0.2f,
    val maxOutputTokens: Int = 512,
    val jsonOnly: Boolean = false,
)

sealed interface AiResponse {
    data class Text(val text: String) : AiResponse
    data class Failed(val reason: String) : AiResponse
}

sealed interface AiStatus {
    data object Checking : AiStatus
    /** Устройство или система не поддерживают локальную модель, либо ИИ выключен в настройках. */
    data class Unavailable(val reason: String) : AiStatus
    data object Downloadable : AiStatus
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long?) : AiStatus
    data object Ready : AiStatus
}

/** Заглушка каркаса: ИИ недоступен. Заменяется реализацией на ML Kit GenAI. */
class UnavailableLocalAi @Inject constructor() : LocalAi {
    private val state = MutableStateFlow<AiStatus>(AiStatus.Unavailable("not_implemented"))
    override val status: StateFlow<AiStatus> = state.asStateFlow()
    override suspend fun prepare() = Unit
    override suspend fun generate(request: AiRequest): AiResponse = AiResponse.Failed("not_implemented")
}
