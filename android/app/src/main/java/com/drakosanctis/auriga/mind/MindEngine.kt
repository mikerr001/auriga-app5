package com.drakosanctis.auriga.mind

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.drakosanctis.auriga.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Auriga MindEngine — Hybrid On-Device / Cloud Language Model
 *
 * Built on LiteRT-LM (com.google.ai.edge.litertlm), NOT the older MediaPipe
 * LLM Inference API (com.google.mediapipe:tasks-genai). Google has placed
 * tasks-genai into maintenance-only mode with an explicit migration
 * recommendation to LiteRT-LM. Staying on tasks-genai would have reintroduced
 * the exact class of risk this project was built to fix — real-world
 * tasks-genai failures (NoClassDefFoundError on internal proto classes, JNI
 * .so resolution failures on Samsung devices specifically, JDK17 class-file
 * version mismatches) match the shape of the original "model failed to
 * load: null" bug report almost exactly.
 *
 * Directly addresses that original root cause: the v1 implementation
 * swallowed the real Throwable in a catch-all and returned null, hiding
 * the actual failure. This rewrite never silently swallows a load failure —
 * every failure path produces a specific, logged reason via
 * [MindEngineState.Failed].
 *
 * RAM-tier gating, addressing real device-fragmentation research for the
 * target markets (Kenya/Nigeria/Ethiopia): current-generation budget Android
 * phones in these markets (Samsung Galaxy A05/A06 series, Tecno, Infinix)
 * commonly ship with 4-6GB RAM, but a real population of older/lower-tier
 * devices (some still distributed via Android Go, as low as 1GB RAM) remains
 * in circulation. Attempting to load even a small on-device model on those
 * devices risks an OOM crash rather than a graceful failure. [hasSufficientRam]
 * checks total device RAM against [BuildConfig.ONDEVICE_MODEL_MIN_DEVICE_RAM_BYTES]
 * before ever attempting a load.
 *
 * Lifecycle: phones are not LLM servers. This engine is LOAD-ON-DEMAND and
 * UNLOAD-ON-IDLE rather than load-at-startup-keep-forever — the same
 * lifecycle pattern explicitly designed in the original debugging
 * conversation ("Voice request -> Need LLM? -> Load model -> Answer ->
 * Unload model").
 *
 * Hybrid routing: the on-device model handles requests when available;
 * deeper/general conversation or on-device-unavailable cases route to the
 * cloud LLM via [CloudMindClient] when the device is online. CloudMindClient
 * is a placeholder interface — wire up the real provider/API key later.
 */

sealed class MindEngineState {
    object Idle : MindEngineState()
    object Loading : MindEngineState()
    data class Ready(val backend: MindBackend) : MindEngineState()
    data class Failed(val reason: String, val cause: Throwable?) : MindEngineState()
    /**
     * Device RAM is below the safe on-device threshold — on-device mode was
     * never attempted, by design, not as a failure. All of Auriga's other
     * features (hazard detection, distance/bearing, guidance, audio,
     * haptics, place memory) are completely unaffected by this — only the
     * on-device conversational LLM layer is skipped. The cloud LLM remains
     * reachable normally if the device is online; see [MindEngine.generate],
     * which already falls through to cloud automatically in this state.
     */
    object OnDeviceUnavailableLowRam : MindEngineState()
}

enum class MindBackend { ON_DEVICE, CLOUD }

data class MindResponse(
    val text: String,
    val backend: MindBackend
)

class MindEngine(
    private val context: Context,
    private val modelFile: File,
    private val cloudClient: CloudMindClient = CloudMindClient(),
    private val idleUnloadMs: Long = 60_000L
) {
    companion object {
        private const val TAG = "AurigaMindEngine"
    }

    @Volatile
    private var state: MindEngineState = MindEngineState.Idle

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var idleUnloadJob: Job? = null
    private val engineScope = CoroutineScope(Dispatchers.Default)

    fun currentState(): MindEngineState = state

    /**
     * Reads total device RAM via ActivityManager and compares against the
     * configured minimum. Checked once before any load attempt — a phone's
     * total RAM doesn't change at runtime, so this is cheap to call freely.
     */
    fun hasSufficientRam(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem >= BuildConfig.ONDEVICE_MODEL_MIN_DEVICE_RAM_BYTES
    }

    /**
     * Picks CPU or GPU backend based on device RAM. GPU delegate initialization
     * itself has overhead that isn't worth it below a reasonable RAM tier —
     * mirrors the real-world pattern of falling back to CPU under a
     * higher-than-minimum RAM threshold, not just the bare minimum-to-run threshold.
     */
    private fun pickBackend(): Backend {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val totalRamBytes = memInfo.totalMem
        // GPU backend reserves additional memory for its own delegate context;
        // only worth it comfortably above the minimum run threshold.
        val gpuThresholdBytes = BuildConfig.ONDEVICE_MODEL_MIN_DEVICE_RAM_BYTES * 2
        return if (totalRamBytes >= gpuThresholdBytes) Backend.GPU() else Backend.CPU()
    }

    /**
     * Loads the on-device model if not already loaded. This is the function
     * that, in v1, hid its real failure behind "model failed to load: null".
     * Here every failure path is explicit and the underlying Throwable is
     * preserved for logging/diagnostics — never swallowed.
     */
    suspend fun ensureOnDeviceModelLoaded(): MindEngineState = withContext(Dispatchers.IO) {
        cancelIdleUnload()

        val current = state
        if (current is MindEngineState.Ready && current.backend == MindBackend.ON_DEVICE) {
            return@withContext current
        }

        if (!hasSufficientRam()) {
            val unsupported = MindEngineState.OnDeviceUnavailableLowRam
            state = unsupported
            Log.i(TAG, "Device RAM below on-device model threshold — skipping on-device load attempt.")
            return@withContext unsupported
        }

        state = MindEngineState.Loading

        if (!modelFile.exists()) {
            val failure = MindEngineState.Failed(
                reason = "Model file not found at ${modelFile.absolutePath}. " +
                    "It must be downloaded or resolved from a bundled asset before first use.",
                cause = null
            )
            state = failure
            return@withContext failure
        }

        if (modelFile.length() < BuildConfig.ONDEVICE_MODEL_MIN_BYTES) {
            val failure = MindEngineState.Failed(
                reason = "Model file at ${modelFile.absolutePath} is only " +
                    "${modelFile.length()} bytes — smaller than the expected minimum " +
                    "of ${BuildConfig.ONDEVICE_MODEL_MIN_BYTES} bytes. Likely an incomplete " +
                    "or corrupted download. Re-resolve before retrying.",
                cause = null
            )
            state = failure
            return@withContext failure
        }

        try {
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = pickBackend(),
                cacheDir = context.cacheDir.absolutePath,
                maxNumTokens = 2048
            )

            // This is the call most likely to surface a real native-layer
            // failure (LiteRtLmJniException) if the model file is incompatible
            // with the bundled runtime version, or initialization otherwise
            // fails. If it throws here, we log the real Throwable and surface
            // a specific reason instead of collapsing it to "null".
            val newEngine = Engine(config)
            newEngine.initialize()

            val newConversation = newEngine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(
                        listOf(
                            Content.Text(
                                "You are Auriga, an assistive vision co-pilot for a blind or " +
                                    "low-vision user. Keep responses brief and conversational, " +
                                    "suitable for being spoken aloud. Never invent details about " +
                                    "the user's physical surroundings — that information comes " +
                                    "from a separate hazard-detection system, not from you."
                            )
                        )
                    )
                )
            )

            engine = newEngine
            conversation = newConversation
            val ready = MindEngineState.Ready(MindBackend.ON_DEVICE)
            state = ready
            scheduleIdleUnload()
            ready
        } catch (t: Throwable) {
            Log.e(TAG, "On-device model load failed", t)
            val failure = MindEngineState.Failed(
                reason = "On-device model failed to initialize: ${t::class.simpleName}: ${t.message}. " +
                    "Common causes: model file format incompatible with the bundled " +
                    "LiteRT-LM runtime version, or insufficient device RAM despite passing " +
                    "the pre-check.",
                cause = t
            )
            state = failure
            // Release any partial resources rather than leaking them. Order
            // matters: conversation must close before engine.
            closeQuietly()
            failure
        }
    }

    /**
     * Generates a response, routing to on-device or cloud based on
     * [preferOnDevice] and actual availability. Falls back to cloud if
     * on-device load fails, is unsupported on this device, or inference
     * itself fails — rather than failing the whole request outright.
     */
    suspend fun generate(
        prompt: String,
        preferOnDevice: Boolean,
        isOnline: Boolean
    ): Result<MindResponse> = withContext(Dispatchers.IO) {
        if (preferOnDevice) {
            val loadResult = ensureOnDeviceModelLoaded()
            if (loadResult is MindEngineState.Ready) {
                val onDeviceResult = runOnDeviceInference(prompt)
                if (onDeviceResult.isSuccess) return@withContext onDeviceResult
                // On-device inference itself failed after a successful load —
                // fall through to cloud if available rather than giving up.
            }
        }

        if (isOnline) {
            return@withContext cloudClient.generate(prompt).map { MindResponse(it, MindBackend.CLOUD) }
        }

        Result.failure(
            IllegalStateException(
                "No mind backend available: on-device " +
                    "${if (preferOnDevice) "failed, unsupported, or unavailable" else "not requested"}, " +
                    "and device is offline so cloud cannot be reached."
            )
        )
    }

    private fun runOnDeviceInference(prompt: String): Result<MindResponse> {
        val activeConversation = conversation ?: return Result.failure(
            IllegalStateException("On-device conversation not initialized.")
        )
        return try {
            scheduleIdleUnload() // reset idle timer on activity
            val response = activeConversation.sendMessage(
                Contents.of(listOf(Content.Text(prompt)))
            )
            Result.success(MindResponse(response.toString(), MindBackend.ON_DEVICE))
        } catch (t: Throwable) {
            Log.e(TAG, "On-device inference failed", t)
            Result.failure(t)
        }
    }

    /**
     * Unloads the on-device model after [idleUnloadMs] of inactivity. This is
     * the direct fix for the RAM/battery concern: even a small on-device
     * model still occupies real memory once loaded, so Auriga shouldn't hold
     * it indefinitely if the user isn't actively in conversation.
     */
    private fun scheduleIdleUnload() {
        cancelIdleUnload()
        idleUnloadJob = engineScope.launch {
            delay(idleUnloadMs)
            unloadOnDeviceModel()
        }
    }

    private fun cancelIdleUnload() {
        idleUnloadJob?.cancel()
        idleUnloadJob = null
    }

    /** Closes conversation before engine — required order per LiteRT-LM docs. */
    private fun closeQuietly() {
        try {
            conversation?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "Error closing Conversation during cleanup", t)
        }
        conversation = null
        try {
            engine?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "Error closing Engine during cleanup", t)
        }
        engine = null
    }

    fun unloadOnDeviceModel() {
        closeQuietly()
        if (state is MindEngineState.Ready) {
            state = MindEngineState.Idle
        }
        System.gc()
        Log.i(TAG, "On-device model unloaded after idle period.")
    }

    fun shutdown() {
        cancelIdleUnload()
        unloadOnDeviceModel()
    }
}
