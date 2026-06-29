package com.drakosanctis.auriga.mind

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Auriga Voice Query Engine
 *
 * Wraps Android's on-device SpeechRecognizer for push-to-talk voice queries
 * (the "Ask Auriga" button flow). Deliberately push-to-talk rather than
 * always-listening for MVP v1: always-on speech recognition has real
 * battery/privacy implications that deserve a dedicated design decision,
 * not a default.
 *
 * Respects the co-pilot-not-assistant priority ordering: callers should
 * check whether a CRITICAL hazard utterance is in-flight via AudioEngine
 * before treating a voice query response as immediately speakable — this
 * class only handles capture + transcription, not that arbitration (see
 * AurigaPipeline / MainActivity for how results should be sequenced
 * against hazard audio).
 */

sealed class VoiceQueryResult {
    data class Recognized(val text: String) : VoiceQueryResult()
    data class Failed(val reason: String) : VoiceQueryResult()
    object NoSpeechDetected : VoiceQueryResult()
    object NotAvailable : VoiceQueryResult()
}

class VoiceQueryEngine(private val context: Context) {

    companion object {
        private const val TAG = "AurigaVoiceQuery"
    }

    private var recognizer: SpeechRecognizer? = null
    private var isListening = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Starts a single voice capture. Calls [onResult] exactly once per
     * invocation (either with a transcription or a specific failure reason
     * — never silently does nothing). Safe to call again after a previous
     * result has been delivered; calling while already listening is a no-op
     * rather than starting overlapping sessions.
     */
    fun startListening(localeTag: String, onResult: (VoiceQueryResult) -> Unit) {
        if (isListening) {
            Log.w(TAG, "startListening called while already listening — ignoring.")
            return
        }

        if (!isAvailable()) {
            onResult(VoiceQueryResult.NotAvailable)
            return
        }

        val newRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = newRecognizer

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        newRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                isListening = false
                val reason = describeError(error)
                if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_NO_MATCH
                ) {
                    onResult(VoiceQueryResult.NoSpeechDetected)
                } else {
                    Log.w(TAG, "Speech recognition error: $reason")
                    onResult(VoiceQueryResult.Failed(reason))
                }
                cleanup()
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()
                if (best != null && best.isNotBlank()) {
                    onResult(VoiceQueryResult.Recognized(best))
                } else {
                    onResult(VoiceQueryResult.NoSpeechDetected)
                }
                cleanup()
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        isListening = true
        newRecognizer.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
        isListening = false
    }

    private fun cleanup() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing RECORD_AUDIO permission"
        SpeechRecognizer.ERROR_NETWORK -> "Network error during recognition"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout during recognition"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected"
        else -> "Unknown recognition error (code $error)"
    }

    fun destroy() {
        stopListening()
        cleanup()
    }
}
