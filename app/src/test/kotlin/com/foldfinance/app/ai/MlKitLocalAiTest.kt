package com.foldfinance.app.ai

import com.google.common.truth.Truth.assertThat
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Логика [MlKitLocalAi] поверх фейкового AICore: статусы, загрузка, выбор варианта, ошибки, таймаут. */
class MlKitLocalAiTest {

    private class FakeBackend : NanoBackend {
        val statuses = mutableMapOf(AiTier.FAST to FeatureStatus.AVAILABLE, AiTier.FULL to FeatureStatus.AVAILABLE)
        var statusError: Exception? = null
        var downloads: Map<AiTier, List<DownloadStatus>> = emptyMap()
        var answer: suspend (AiTier, AiRequest) -> String? = { _, _ -> "ok" }
        val generatedWith = mutableListOf<AiTier>()

        override suspend fun checkStatus(tier: AiTier): Int {
            statusError?.let { throw it }
            return statuses.getValue(tier)
        }

        override fun download(tier: AiTier): Flow<DownloadStatus> = flow {
            downloads[tier].orEmpty().forEach { emit(it) }
            statuses[tier] = FeatureStatus.AVAILABLE
        }

        override suspend fun generate(tier: AiTier, request: AiRequest): String? {
            generatedWith += tier
            return answer(tier, request)
        }
    }

    private val backend = FakeBackend()
    private val enabled = MutableStateFlow(true)

    private fun kotlinx.coroutines.test.TestScope.newAi() = MlKitLocalAi(backend, enabled, backgroundScope)

    @Test
    fun ready_andGenerates() = runTest(UnconfinedTestDispatcher()) {
        val ai = newAi()
        assertThat(ai.status.value).isEqualTo(AiStatus.Ready)
        assertThat(ai.generate(AiRequest("привет"))).isEqualTo(AiResponse.Text("ok"))
    }

    @Test
    fun disabled_isUnavailable_andNeverCallsModel() = runTest(UnconfinedTestDispatcher()) {
        enabled.value = false
        val ai = newAi()
        assertThat(ai.status.value).isEqualTo(AiStatus.Unavailable(AiFailureReasons.DISABLED))
        assertThat(ai.generate(AiRequest("x"))).isEqualTo(AiResponse.Failed(AiFailureReasons.DISABLED))
        assertThat(backend.generatedWith).isEmpty()

        enabled.value = true
        assertThat(ai.status.value).isEqualTo(AiStatus.Ready)
    }

    @Test
    fun unavailableDevice_isReported() = runTest(UnconfinedTestDispatcher()) {
        backend.statuses.replaceAll { _, _ -> FeatureStatus.UNAVAILABLE }
        val ai = newAi()
        assertThat(ai.status.value).isEqualTo(AiStatus.Unavailable(AiFailureReasons.NOT_SUPPORTED))
        assertThat(ai.generate(AiRequest("x"))).isEqualTo(AiResponse.Failed(AiFailureReasons.NOT_READY))
    }

    @Test
    fun aicoreError_onStatus_isMappedNotThrown() = runTest(UnconfinedTestDispatcher()) {
        backend.statusError = GenAiException("old", null, GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE)
        val ai = newAi()
        assertThat(ai.status.value).isEqualTo(AiStatus.Unavailable(AiFailureReasons.NEEDS_SYSTEM_UPDATE))
    }

    @Test
    fun prepare_downloadsWithProgress_thenReady() = runTest(UnconfinedTestDispatcher()) {
        backend.statuses.replaceAll { _, _ -> FeatureStatus.DOWNLOADABLE }
        val seen = mutableListOf<AiStatus>()
        lateinit var ai: MlKitLocalAi
        val withProgress = object : NanoBackend by backend {
            override fun download(tier: AiTier): Flow<DownloadStatus> = flow {
                emit(DownloadStatus.DownloadStarted(1_000L))
                emit(DownloadStatus.DownloadProgress(400L))
                yield()
                seen += ai.status.value
                emit(DownloadStatus.DownloadCompleted)
                backend.statuses[tier] = FeatureStatus.AVAILABLE
            }
        }
        ai = MlKitLocalAi(withProgress, enabled, backgroundScope)
        assertThat(ai.status.value).isEqualTo(AiStatus.Downloadable)

        ai.prepare()

        assertThat(seen).contains(AiStatus.Downloading(400L, 1_000L))
        assertThat(ai.status.value).isEqualTo(AiStatus.Ready)
    }

    @Test
    fun downloadFailure_isReported_andRetryable() = runTest(UnconfinedTestDispatcher()) {
        backend.statuses.replaceAll { _, _ -> FeatureStatus.DOWNLOADABLE }
        val failing = object : NanoBackend by backend {
            override fun download(tier: AiTier): Flow<DownloadStatus> = flow {
                emit(DownloadStatus.DownloadFailed(GenAiException("disk", null, GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE)))
            }
        }
        val ai = MlKitLocalAi(failing, enabled, backgroundScope)
        ai.prepare()
        assertThat(ai.status.value).isEqualTo(AiStatus.Unavailable(AiFailureReasons.NOT_ENOUGH_DISK))
    }

    @Test
    fun fullTier_fallsBackToFast_whenOnlyFastIsReady() = runTest(UnconfinedTestDispatcher()) {
        backend.statuses[AiTier.FULL] = FeatureStatus.DOWNLOADABLE
        val ai = newAi()
        ai.generate(AiRequest("x", tier = AiTier.FULL))
        assertThat(backend.generatedWith).containsExactly(AiTier.FAST)
    }

    @Test
    fun errors_becomeFailedReasons() = runTest(UnconfinedTestDispatcher()) {
        val ai = newAi()
        backend.answer = { _, _ -> throw GenAiException("bg", null, GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED) }
        assertThat(ai.generate(AiRequest("x"))).isEqualTo(AiResponse.Failed(AiFailureReasons.BACKGROUND_BLOCKED))
        backend.answer = { _, _ -> throw GenAiException("busy", null, GenAiException.ErrorCode.BUSY) }
        assertThat(ai.generate(AiRequest("x"))).isEqualTo(AiResponse.Failed(AiFailureReasons.BUSY))
        backend.answer = { _, _ -> throw IllegalStateException("boom") }
        assertThat(ai.generate(AiRequest("x"))).isEqualTo(AiResponse.Failed(AiFailureReasons.ERROR))
        backend.answer = { _, _ -> "  " }
        assertThat(ai.generate(AiRequest("x"))).isEqualTo(AiResponse.Failed(AiFailureReasons.EMPTY_RESPONSE))
    }

    @Test
    fun slowModel_timesOut() = runTest(UnconfinedTestDispatcher()) {
        val ai = newAi()
        backend.answer = { _, _ -> awaitCancellation() }
        assertThat(ai.generate(AiRequest("x"))).isEqualTo(AiResponse.Failed(AiFailureReasons.TIMEOUT))
    }

    @Test
    fun jsonOnly_stripsMarkdownAroundObject() = runTest(UnconfinedTestDispatcher()) {
        val ai = newAi()
        backend.answer = { _, _ -> "Вот ответ:\n```json\n{\"a\": \"{x}\"}\n```" }
        assertThat(ai.generate(AiRequest("x", jsonOnly = true))).isEqualTo(AiResponse.Text("{\"a\": \"{x}\"}"))
    }
}
