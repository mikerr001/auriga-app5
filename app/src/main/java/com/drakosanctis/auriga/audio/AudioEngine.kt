package com.drakosanctis.auriga.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.drakosanctis.auriga.guidance.GuidanceUtterance
import com.drakosanctis.auriga.guidance.UrgencyLevel
import com.drakosanctis.auriga.locale.LocaleSupport
import com.drakosanctis.auriga.locale.SupportedLocale
import java.util.PriorityQueue
import java.util.UUID

/**
 * Auriga Audio Engine
 *
 * Designed from the Auriga product deliberations (auriga-audio-engine repo
 * was an empty scaffold — no implementation existed to port).
 *
 * Principles, as specified across the design conversations:
 * - Silent by default. Auriga does not narrate everything it sees; it speaks
 *   only when there's a hazard, an instruction, or a direct response to the user.
 * - CRITICAL utterances interrupt immediately (flush queue, speak now).
 * - ELEVATED utterances queue ahead of ROUTINE ones but wait for the current
 *   utterance to finish a natural phrase boundary.
 * - ROUTINE utterances queue normally and may be dropped if stale (e.g. a
 *   superseded distance update) rather than read out late.
 * - Volume and priority both escalate with danger — a CRITICAL warning should
 *   be audible even over loud ambient environments.
 * - One-earbud-mode awareness: when the user has only one earbud connected
 *   (common so the other ear stays free for traffic/people), Auriga should
 *   prefer mono output rather than panning content to a dead channel.
 * - Bone-conduction-friendly: Auriga never assumes blocked ears. It should
 *   work acceptably through conduction transducers, which have weaker bass
 *   response — so it avoids relying on low-frequency cues alone.
 */

private data class QueuedUtterance(
    val id: String,
    val utterance: GuidanceUtterance,
    val enqueuedAtMs: Long,
    /** Routine items older than this are considered stale and skipped rather than spoken late. */
    val staleAfterMs: Long = 4000L
) : Comparable<QueuedUtterance> {
    private fun priorityRank(): Int = when (utterance.urgency) {
        UrgencyLevel.CRITICAL -> 0
        UrgencyLevel.ELEVATED -> 1
        UrgencyLevel.ROUTINE -> 2
    }

    override fun compareTo(other: QueuedUtterance): Int {
        val rankCompare = priorityRank().compareTo(other.priorityRank())
        if (rankCompare != 0) return rankCompare
        return enqueuedAtMs.compareTo(other.enqueuedAtMs)
    }

    fun isStale(nowMs: Long): Boolean =
        utterance.urgency == UrgencyLevel.ROUTINE && (nowMs - enqueuedAtMs) > staleAfterMs
}

class AudioEngine(
    private val context: Context,
    private val locale: java.util.Locale = java.util.Locale.getDefault()
) {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val queue = PriorityQueue<QueuedUtterance>()
    private var isSpeaking = false
    private var currentlySpeakingUrgency: UrgencyLevel? = null
    private var lastTtsLanguageResult: Int? = null

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private var audioFocusRequest: AudioFocusRequest? = null

    /**
     * True once initialize() has completed and a final language decision has
     * been made (whether that's the requested locale, a degraded Tier-2
     * fallback, or English). Callers (e.g. AurigaPipeline / settings UI)
     * should check [lastTtsCheckResult] after [initialize]'s callback fires
     * to know whether the user's requested language actually got reliable
     * on-device voice support.
     */
    sealed class TtsAvailability {
        object FullySupported : TtsAvailability()
        data class DegradedFallbackToEnglish(val requestedLocale: SupportedLocale) : TtsAvailability()
        data class NotInitialized(val reason: String) : TtsAvailability()
    }

    private var lastTtsCheckResult: TtsAvailability = TtsAvailability.NotInitialized("Not yet initialized")
    fun ttsAvailability(): TtsAvailability = lastTtsCheckResult

    fun initialize(onReady: (success: Boolean) -> Unit) {
        tts = TextToSpeech(context) { status ->
            isTtsReady = status == TextToSpeech.SUCCESS

            if (!isTtsReady) {
                lastTtsCheckResult = TtsAvailability.NotInitialized(
                    "TextToSpeech engine failed to initialize (status=$status)"
                )
                onReady(false)
                return@TextToSpeech
            }

            val requestedLocale = LocaleSupport.findByTag(localeTagOf(locale))
                ?: LocaleSupport.defaultForDevice(locale)

            val result = tts?.setLanguage(locale)
            lastTtsLanguageResult = result
            val degraded = result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED

            if (degraded) {
                // Explicit runtime check rather than assuming success — this
                // matters most for Tier 2 languages (Hausa, Yoruba, Amharic,
                // Igbo, Persian) where on-device voice data availability
                // varies significantly by device/OEM. Fall back to English
                // rather than failing silently, and record exactly what
                // happened so the UI/settings screen can tell the user their
                // language isn't fully supported on this device instead of
                // them just wondering why Auriga sounds different than expected.
                tts?.setLanguage(java.util.Locale.US)
                lastTtsCheckResult = TtsAvailability.DegradedFallbackToEnglish(requestedLocale)
            } else {
                lastTtsCheckResult = TtsAvailability.FullySupported
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    currentlySpeakingUrgency = null
                    speakNextFromQueue()
                }

                @Deprecated("Deprecated in TextToSpeech")
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    currentlySpeakingUrgency = null
                    speakNextFromQueue()
                }
            })

            onReady(true)
        }
    }

    private fun localeTagOf(locale: java.util.Locale): String =
        locale.toLanguageTag()

    /**
     * Main entry point. CRITICAL utterances interrupt immediately; everything
     * else is queued by priority. Empty-text utterances (e.g. the "aligned"
     * beep moment from GuidanceEngine) skip speech and rely on haptic only.
     */
    fun enqueue(utterance: GuidanceUtterance) {
        if (utterance.text.isBlank()) return // haptic-only moment, nothing to speak

        val item = QueuedUtterance(
            id = UUID.randomUUID().toString(),
            utterance = utterance,
            enqueuedAtMs = System.currentTimeMillis()
        )

        if (utterance.urgency == UrgencyLevel.CRITICAL) {
            // Flush lower-priority queued speech and speak this immediately.
            queue.clear()
            tts?.stop()
            isSpeaking = false
            speakNow(item)
            return
        }

        queue.add(item)
        if (!isSpeaking) {
            speakNextFromQueue()
        }
    }

    private fun speakNextFromQueue() {
        val now = System.currentTimeMillis()
        var next = queue.poll()
        while (next != null && next.isStale(now)) {
            next = queue.poll()
        }
        if (next != null) {
            speakNow(next)
        }
    }

    private fun speakNow(item: QueuedUtterance) {
        if (!isTtsReady) return

        requestAudioFocus(item.utterance.urgency)
        applyVolumeForUrgency(item.utterance.urgency)

        isSpeaking = true
        currentlySpeakingUrgency = item.utterance.urgency
        tts?.speak(item.utterance.text, TextToSpeech.QUEUE_FLUSH, null, item.id)
    }

    /**
     * True if Auriga is currently mid-speech on a CRITICAL hazard warning.
     * Callers handling non-hazard speech (e.g. MindEngine voice query
     * responses) should check this before enqueuing, since a navigation
     * hazard should never be talked over by a conversational answer.
     */
    fun hasCriticalUtteranceInFlight(): Boolean =
        isSpeaking && currentlySpeakingUrgency == UrgencyLevel.CRITICAL

    /**
     * Volume escalates with urgency so CRITICAL warnings cut through ambient
     * noise (traffic, crowds) without the user needing to manually raise volume.
     */
    private fun applyVolumeForUrgency(urgency: UrgencyLevel) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetFraction = when (urgency) {
            UrgencyLevel.CRITICAL -> 1.0f
            UrgencyLevel.ELEVATED -> 0.8f
            UrgencyLevel.ROUTINE -> 0.6f
        }
        val targetVolume = (maxVolume * targetFraction).toInt().coerceAtLeast(1)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
    }

    private fun requestAudioFocus(urgency: UrgencyLevel) {
        val attributes = AudioAttributes.Builder()
            .setUsage(
                if (urgency == UrgencyLevel.CRITICAL)
                    AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
                else
                    AudioAttributes.USAGE_ASSISTANT
            )
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val focusGain = if (urgency == UrgencyLevel.CRITICAL)
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        else
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT

        val request = AudioFocusRequest.Builder(focusGain)
            .setAudioAttributes(attributes)
            .build()

        audioFocusRequest = request
        audioManager.requestAudioFocus(request)
    }

    /**
     * Detects whether output should be forced to mono. Common case: a single
     * earbud is connected so the other ear stays free for ambient awareness.
     * Without this, stereo-panned content could go silent in one ear and be
     * lost entirely if that's the connected one.
     */
    fun shouldForceMonoOutput(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bluetoothOrWiredCount = devices.count {
            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
        }
        // Heuristic: exactly one external audio output device strongly suggests
        // single-earbud use rather than a full stereo pair or the phone speaker.
        return bluetoothOrWiredCount == 1
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }
}
