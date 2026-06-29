package com.drakosanctis.auriga.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Auriga Haptic Engine
 *
 * Designed from the Auriga product deliberations (auriga-haptic-engine repo
 * was an empty scaffold — no implementation existed to port).
 *
 * Principles, as specified across the design conversations:
 * - Haptics are a backup communication channel, not the primary one — used
 *   alongside speech for CRITICAL hazards, and as the *only* channel when
 *   audio is impractical (Do Not Disturb, very loud environments, or when
 *   the user has muted Auriga but still wants hazard alerts).
 * - Directional vibration codes: distinct patterns for left / center / right
 *   so the user can orient without needing speech at all.
 * - Patterns are intentionally simple (pulse count + timing), since complex
 *   haptic "language" is hard to learn and recall under stress.
 */

enum class HapticPattern(val timingsMs: LongArray, val amplitudes: IntArray) {
    // Single short pulse — used for routine "aligned" beep during navigation.
    BEEP_ALIGNED(longArrayOf(0, 60), intArrayOf(0, 160)),

    // Single pulse, left-weighted timing convention: one pulse = left.
    PULSE_SINGLE_LEFT(longArrayOf(0, 90), intArrayOf(0, 200)),

    // Two pulses = right (distinguishable from left by count, not just feel).
    PULSE_SINGLE_RIGHT(longArrayOf(0, 90, 80, 90), intArrayOf(0, 200, 0, 200)),

    // Three rapid pulses = center / straight ahead.
    PULSE_SINGLE_CENTER(longArrayOf(0, 70, 60, 70, 60, 70), intArrayOf(0, 200, 0, 200, 0, 200)),

    // Rapid repeating buzz = critical hazard, direction-agnostic urgency cue.
    PULSE_RAPID_LEFT(longArrayOf(0, 50, 40, 50, 40, 50, 40, 50), intArrayOf(0, 255, 0, 255, 0, 255, 0, 255)),
    PULSE_RAPID_RIGHT(longArrayOf(0, 50, 40, 50, 40, 50, 40, 50, 200, 50, 40, 50), intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255)),
    PULSE_RAPID_CENTER(longArrayOf(0, 30, 30, 30, 30, 30, 30, 30, 30, 30), intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255)),

    // Long single buzz — used for generic critical alert with unknown direction.
    CRITICAL_GENERIC(longArrayOf(0, 400), intArrayOf(0, 255));

    companion object {
        /** Resolve a pattern by the string name GuidanceEngine emits, e.g. "pulse_single_left". */
        fun fromGuidanceCode(code: String): HapticPattern? = when (code) {
            "beep_aligned" -> BEEP_ALIGNED
            "pulse_single_left" -> PULSE_SINGLE_LEFT
            "pulse_single_right", "pulse_single_slight_right" -> PULSE_SINGLE_RIGHT
            "pulse_single_center" -> PULSE_SINGLE_CENTER
            "pulse_single_slight_left" -> PULSE_SINGLE_LEFT
            "pulse_rapid_left", "pulse_rapid_slight_left" -> PULSE_RAPID_LEFT
            "pulse_rapid_right", "pulse_rapid_slight_right" -> PULSE_RAPID_RIGHT
            "pulse_rapid_center" -> PULSE_RAPID_CENTER
            else -> if (code.startsWith("pulse_rapid")) CRITICAL_GENERIC else null
        }
    }
}

class HapticEngine(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** True if the device has a vibrator at all — some tablets/emulators don't. */
    fun isAvailable(): Boolean = vibrator?.hasVibrator() == true

    fun play(pattern: HapticPattern) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(pattern.timingsMs, pattern.amplitudes, -1)
            v.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern.timingsMs, -1)
        }
    }

    /** Convenience: play directly from a GuidanceEngine haptic code string. */
    fun playFromGuidanceCode(code: String?) {
        if (code == null) return
        HapticPattern.fromGuidanceCode(code)?.let { play(it) }
    }

    fun stop() {
        vibrator?.cancel()
    }
}
