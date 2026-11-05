package com.foldfinance.app.ai

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.foldfinance.app.BuildConfig
import com.foldfinance.app.ai.di.AiCoroutineScope
import com.foldfinance.app.prefs.AppPreferences
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LocalAi] на Gemini Nano через системный AICore (ML Kit GenAI Prompt API).
 *
 * - **Статус.** При старте и при каждом возврате приложения на передний план опрашивается AICore
 *   (`checkStatus`) для обоих вариантов модели. Итог: [AiStatus.Ready], если готов хотя бы один,
 *   иначе Downloading / Downloadable / Unavailable. Выключенный переключатель «Локальный ИИ» →
 *   `Unavailable("disabled")`, AICore при этом не опрашивается.
 * - **Загрузка.** [prepare] запускает `download()` у всех вариантов, которые можно скачать, в
 *   собственном scope: уход с экрана настроек загрузку не прерывает. Скачивает AICore (у приложения
 *   нет INTERNET).
 * - **Запрос.** [generate] выбирает запрошенный вариант, а если он не готов — другой готовый (на
 *   части устройств FAST и FULL — одна модель). Таймаут [timeoutMillis]; любые ошибки →
 *   [AiResponse.Failed] с причиной из [AiFailureReasons]. Отмена корутины вызывающего пробрасывается.
 * - **Изображения.** [supportsImages] — есть готовый вариант, принимающий картинки
 *   ([NanoBackend.supportsImages], опрашивается вместе со статусом). Запрос с картинками идёт только в
 *   такой вариант, иначе [AiFailureReasons.IMAGES_NOT_SUPPORTED].
 */
@Singleton
class MlKitLocalAi internal constructor(
    private val backend: NanoBackend,
    enabledSetting: Flow<Boolean>,
    private val scope: CoroutineScope,
    private val timeoutMillis: (AiRequest) -> Long = ::defaultTimeout,
    observeProcess: Boolean = false,
) : LocalAi {

    @Inject constructor(
        backend: MlKitNanoBackend,
        prefs: AppPreferences,
        @AiCoroutineScope scope: CoroutineScope,
    ) : this(backend, prefs.localAiEnabled, scope, ::defaultTimeout, observeProcess = true)

    private val enabled: StateFlow<Boolean?> = enabledSetting.stateIn(scope, SharingStarted.Eagerly, null)
    private val model = MutableStateFlow<AiStatus>(AiStatus.Checking)
    private val tierStatus = MutableStateFlow<Map<AiTier, Int>>(emptyMap())
    private val imageTiers = MutableStateFlow<Set<AiTier>>(emptySet())
    private val refreshMutex = Mutex()
    private val downloadMutex = Mutex()
    private var downloadJob: Job? = null

    override val status: StateFlow<AiStatus> = combine(enabled, model) { on, state ->
        when (on) {
            null -> AiStatus.Checking
            false -> AiStatus.Unavailable(AiFailureReasons.DISABLED)
            true -> state
        }
    }.stateIn(scope, SharingStarted.Eagerly, AiStatus.Checking)

    override val supportsImages: StateFlow<Boolean> = combine(status, imageTiers) { st, tiers ->
        st == AiStatus.Ready && tiers.isNotEmpty()
    }.stateIn(scope, SharingStarted.Eagerly, false)

    init {
        scope.launch {
            enabled.filterNotNull().distinctUntilChanged().collect { on -> if (on) refresh() }
        }
        if (observeProcess) {
            scope.launch(Dispatchers.Main.immediate) {
                ProcessLifecycleOwner.get().lifecycle.addObserver(
                    object : DefaultLifecycleObserver {
                        override fun onStart(owner: LifecycleOwner) {
                            scope.launch { if (enabled.value == true) refresh() }
                        }
                    },
                )
            }
        }
    }

    /** Опросить AICore. Во время загрузки статус «Загрузка» не перетирается. */
    suspend fun refresh() {
        refreshMutex.withLock {
            var failure: String? = null
            val statuses = AiTier.entries.associateWith { tier ->
                try {
                    backend.checkStatus(tier)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failure = reasonOf(e)
                    FeatureStatus.UNAVAILABLE
                }
            }
            tierStatus.value = statuses
            imageTiers.value = probeImages(statuses)
            if (downloadJob?.isActive == true) return
            model.value = aggregate(statuses, failure)
            if (statuses.values.any { it == FeatureStatus.DOWNLOADING }) {
                // AICore уже качает (например, начатая раньше загрузка): подключаемся ради прогресса.
                startDownload(statuses.filterValues { it == FeatureStatus.DOWNLOADING }.keys.toList())
            }
        }
    }

    override suspend fun prepare() {
        if (awaitEnabled() != true) return
        refresh()
        val pending = tierStatus.value
            .filterValues { it == FeatureStatus.DOWNLOADABLE || it == FeatureStatus.DOWNLOADING }
            .keys.sortedBy { it.ordinal }
        if (pending.isEmpty()) return
        startDownload(pending).join()
    }

    private suspend fun startDownload(tiers: List<AiTier>): Job = downloadMutex.withLock {
        downloadJob?.takeIf { it.isActive }?.let { return@withLock it }
        scope.launch {
            var failure: String? = null
            for (tier in tiers) {
                var total: Long? = null
                try {
                    backend.download(tier).collect { st ->
                        when (st) {
                            is DownloadStatus.DownloadStarted -> {
                                total = st.bytesToDownload.takeIf { it > 0 }
                                model.value = AiStatus.Downloading(0L, total)
                            }
                            is DownloadStatus.DownloadProgress ->
                                model.value = AiStatus.Downloading(st.totalBytesDownloaded, total)
                            is DownloadStatus.DownloadFailed -> failure = downloadReason(st.e)
                            else -> Unit // DownloadCompleted: итог узнаём из checkStatus ниже.
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failure = if (e is GenAiException) downloadReason(e) else AiFailureReasons.DOWNLOAD_FAILED
                }
                if (failure != null) break
            }
            val statuses = AiTier.entries.associateWith { tier ->
                runCatching { backend.checkStatus(tier) }.getOrDefault(FeatureStatus.UNAVAILABLE)
            }
            tierStatus.value = statuses
            imageTiers.value = probeImages(statuses)
            val aggregated = aggregate(statuses, null)
            model.value = if (failure != null && aggregated !is AiStatus.Ready) AiStatus.Unavailable(failure!!) else aggregated
            if (BuildConfig.DEBUG && failure != null) Timber.w("AICore download failed: %s", failure)
        }.also { downloadJob = it }
    }

    override suspend fun generate(request: AiRequest): AiResponse {
        if (awaitEnabled() != true) return AiResponse.Failed(AiFailureReasons.DISABLED)
        if (tierStatus.value.isEmpty()) refresh()
        val tier = pickTier(request.tier) ?: return AiResponse.Failed(AiFailureReasons.NOT_READY)
        val target = if (request.images.isEmpty()) {
            tier
        } else {
            pickImageTier(request.tier) ?: return AiResponse.Failed(AiFailureReasons.IMAGES_NOT_SUPPORTED)
        }
        return try {
            val text = withTimeout(timeoutMillis(request)) { backend.generate(target, request) }
            when {
                text.isNullOrBlank() -> AiResponse.Failed(AiFailureReasons.EMPTY_RESPONSE)
                request.jsonOnly -> AiResponse.Text(AiJson.extractObject(text) ?: text.trim())
                else -> AiResponse.Text(text.trim())
            }
        } catch (e: TimeoutCancellationException) {
            AiResponse.Failed(AiFailureReasons.TIMEOUT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = reasonOf(e)
            if (reason == AiFailureReasons.NOT_READY) scope.launch { refresh() }
            if (BuildConfig.DEBUG) Timber.w("AICore request failed: %s", reason)
            AiResponse.Failed(reason)
        }
    }

    private fun pickTier(requested: AiTier): AiTier? {
        val statuses = tierStatus.value
        if (statuses[requested] == FeatureStatus.AVAILABLE) return requested
        return AiTier.entries.firstOrNull { statuses[it] == FeatureStatus.AVAILABLE }
    }

    private fun pickImageTier(requested: AiTier): AiTier? {
        val tiers = imageTiers.value
        return if (requested in tiers) requested else AiTier.entries.firstOrNull { it in tiers }
    }

    /** Готовые варианты, принимающие изображения; ошибка опроса — вариант считается текстовым. */
    private suspend fun probeImages(statuses: Map<AiTier, Int>): Set<AiTier> =
        statuses.filterValues { it == FeatureStatus.AVAILABLE }.keys.filterTo(LinkedHashSet()) { tier ->
            try {
                backend.supportsImages(tier)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        }

    private suspend fun awaitEnabled(): Boolean? = enabled.value ?: enabled.filterNotNull().first()

    internal companion object {
        const val FAST_TIMEOUT_MILLIS: Long = 30_000
        const val FULL_TIMEOUT_MILLIS: Long = 90_000

        fun defaultTimeout(request: AiRequest): Long =
            if (request.tier == AiTier.FULL || request.images.isNotEmpty()) FULL_TIMEOUT_MILLIS else FAST_TIMEOUT_MILLIS

        fun aggregate(statuses: Map<AiTier, Int>, failure: String?): AiStatus {
            val values = statuses.values
            return when {
                FeatureStatus.AVAILABLE in values -> AiStatus.Ready
                FeatureStatus.DOWNLOADING in values -> AiStatus.Downloading(0L, null)
                FeatureStatus.DOWNLOADABLE in values -> AiStatus.Downloadable
                else -> AiStatus.Unavailable(failure ?: AiFailureReasons.NOT_SUPPORTED)
            }
        }

        fun reasonOf(e: Exception): String = when (e) {
            is GenAiException -> when (e.errorCode) {
                GenAiException.ErrorCode.BUSY -> AiFailureReasons.BUSY
                GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED -> AiFailureReasons.BACKGROUND_BLOCKED
                GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED -> AiFailureReasons.QUOTA
                GenAiException.ErrorCode.NOT_AVAILABLE -> AiFailureReasons.NOT_READY
                GenAiException.ErrorCode.REQUEST_TOO_LARGE -> AiFailureReasons.REQUEST_TOO_LARGE
                GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE -> AiFailureReasons.NEEDS_SYSTEM_UPDATE
                GenAiException.ErrorCode.AICORE_INCOMPATIBLE -> AiFailureReasons.AICORE_INCOMPATIBLE
                GenAiException.ErrorCode.NOT_SUPPORTED -> AiFailureReasons.NOT_SUPPORTED
                GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE -> AiFailureReasons.NOT_ENOUGH_DISK
                else -> "${AiFailureReasons.ERROR}_${e.errorCode}"
            }
            else -> AiFailureReasons.ERROR
        }

        private fun downloadReason(e: GenAiException): String =
            if (e.errorCode == GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE) {
                AiFailureReasons.NOT_ENOUGH_DISK
            } else {
                AiFailureReasons.DOWNLOAD_FAILED
            }
    }
}
