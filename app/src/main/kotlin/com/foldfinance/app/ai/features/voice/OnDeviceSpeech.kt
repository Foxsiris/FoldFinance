package com.foldfinance.app.ai.features.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Offline speech recognition through `SpeechRecognizer.createOnDeviceSpeechRecognizer` (API 31+):
 * audio never leaves the device and the app has no INTERNET anyway. If the device has no on-device
 * recogniser, [isAvailable] is `false` and the voice button is not shown.
 *
 * Must be used from the main thread. One instance per listening session; call [destroy] after.
 */
class OnDeviceSpeech(private val context: Context) {

    sealed interface Event {
        data object Listening : Event
        data class Partial(val text: String) : Event
        data class Final(val text: String) : Event

        /** @property languageMissing the Russian on-device model is not installed. */
        data class Error(val code: Int, val languageMissing: Boolean) : Event
    }

    private var recognizer: SpeechRecognizer? = null

    fun start(onEvent: (Event) -> Unit) {
        destroy()
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onEvent(Event.Listening)
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.let { onEvent(Event.Partial(it)) }
            }
            override fun onResults(results: Bundle?) {
                val text = firstResult(results)
                if (text.isNullOrBlank()) onEvent(Event.Error(SpeechRecognizer.ERROR_NO_MATCH, false)) else onEvent(Event.Final(text))
            }
            override fun onError(error: Int) {
                val missing = error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                onEvent(Event.Error(error, missing))
            }
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1),
        )
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        const val LANGUAGE = "ru-RU"

        /** On-device recognition exists on this device (the language pack may still be missing). */
        fun isAvailable(context: Context): Boolean =
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
    }
}
