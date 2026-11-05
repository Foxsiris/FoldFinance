package com.foldfinance.app.ai

/**
 * Значения [AiResponse.Failed.reason] и [AiStatus.Unavailable.reason] у [MlKitLocalAi]. Стабильные строки:
 * на них опираются UI настроек и фоновая дообработка. Тексты сообщений сюда не попадают.
 */
object AiFailureReasons {
    /** «Локальный ИИ» выключен в настройках. */
    const val DISABLED: String = "disabled"

    /** AICore сообщил, что модель на устройстве недоступна (FeatureStatus.UNAVAILABLE). */
    const val NOT_SUPPORTED: String = "not_supported"

    /** Модель ещё не скачана / скачивается. */
    const val NOT_READY: String = "not_ready"

    /** AICore занят другим запросом. */
    const val BUSY: String = "busy"

    /** AICore не выполняет запросы, пока приложение не на переднем плане. */
    const val BACKGROUND_BLOCKED: String = "background_blocked"

    /** Исчерпана квота AICore на расход батареи для приложения. */
    const val QUOTA: String = "quota"

    const val TIMEOUT: String = "timeout"
    const val REQUEST_TOO_LARGE: String = "request_too_large"
    const val EMPTY_RESPONSE: String = "empty_response"
    const val NEEDS_SYSTEM_UPDATE: String = "needs_system_update"
    const val AICORE_INCOMPATIBLE: String = "aicore_incompatible"
    const val DOWNLOAD_FAILED: String = "download_failed"
    const val NOT_ENOUGH_DISK: String = "not_enough_disk"

    /** Прочие ошибки AICore: `error_<код GenAiException>` или просто `error`. */
    const val ERROR: String = "error"

    /** Модель может стать доступной сама (без смены запроса): задачу повторить позже, а не отбрасывать. */
    val TRANSIENT: Set<String> = setOf(DISABLED, NOT_READY, BUSY, BACKGROUND_BLOCKED, QUOTA, NOT_SUPPORTED)
}
