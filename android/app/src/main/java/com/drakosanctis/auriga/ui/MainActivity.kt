package com.drakosanctis.auriga.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.drakosanctis.auriga.core.AurigaCoPilotService
import com.drakosanctis.auriga.core.AurigaPipeline
import com.drakosanctis.auriga.databinding.ActivityMainBinding
import com.drakosanctis.auriga.locale.LocaleSupport
import com.drakosanctis.auriga.mind.VoiceQueryEngine
import com.drakosanctis.auriga.mind.VoiceQueryResult

/**
 * Entry point for Auriga MVP v1. Deliberately minimal UI: a camera preview,
 * a status line, and a single "Ask Auriga" button for direct voice queries.
 * The core experience is meant to run hands-off — the pipeline starts as
 * soon as camera permission is granted and runs continuously with no further
 * user action required, per the "no manual capture trigger" requirement.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pipeline: AurigaPipeline? = null
    private lateinit var voiceQueryEngine: VoiceQueryEngine

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startPipeline()
            startCoPilotForegroundService()
        } else {
            binding.statusText.text = "Camera permission is required for Auriga to function."
        }
    }

    private val microphonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginVoiceCapture()
        } else {
            binding.statusText.text = "Microphone permission is required to talk to Auriga."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        voiceQueryEngine = VoiceQueryEngine(this)

        binding.talkButton.setOnClickListener {
            if (hasMicrophonePermission()) {
                beginVoiceCapture()
            } else {
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        if (hasCameraPermission()) {
            startPipeline()
            startCoPilotForegroundService()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun startPipeline() {
        binding.statusText.text = "Starting Auriga…"
        val newPipeline = AurigaPipeline(
            context = this,
            lifecycleOwner = this,
            onStatusUpdate = { status ->
                runOnUiThread { binding.statusText.text = status }
            }
        )
        pipeline = newPipeline
        newPipeline.start(binding.cameraPreview)
    }

    /**
     * Starts the camera-type foreground service. CRITICAL: this must only
     * be called AFTER camera permission is confirmed granted.
     *
     * Root cause of an early instant-crash-on-launch bug: on Android 14+
     * (API 34, this app's targetSdk), starting a foreground service whose
     * foregroundServiceType is "camera" requires the CAMERA permission to
     * already be granted at the moment startForeground() is called inside
     * the service — otherwise the system throws a SecurityException /
     * ForegroundServiceStartNotAllowedException, which is fatal and crashes
     * the app before any UI (including the permission dialog itself) gets
     * a chance to render. The original code called this unconditionally in
     * onCreate(), every launch, regardless of permission state — exactly
     * the trigger for that crash. It is now called only from the two paths
     * where permission is already confirmed granted.
     *
     * Wrapped in try/catch anyway, per this project's standing rule of
     * never letting a service-start failure crash the whole app silently —
     * core navigation (camera + detection, owned by AurigaPipeline directly,
     * not by this service) continues even if the foreground service/
     * notification itself fails to start for some other reason.
     */
    private fun startCoPilotForegroundService() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, AurigaCoPilotService::class.java))
        } catch (t: Throwable) {
            // Log and continue — the foreground service is a background-
            // longevity nicety (keeps the app alive if the user briefly
            // switches apps), not the safety-critical pipeline itself.
            android.util.Log.e("AurigaMainActivity", "Failed to start co-pilot foreground service", t)
        }
    }

    private fun beginVoiceCapture() {
        if (!voiceQueryEngine.isAvailable()) {
            binding.statusText.text = "Voice recognition is not available on this device."
            return
        }

        val localeTag = LocaleSupport.defaultForDevice().languageTag
        binding.statusText.text = "Listening…"

        voiceQueryEngine.startListening(localeTag) { result ->
            runOnUiThread {
                when (result) {
                    is VoiceQueryResult.Recognized -> {
                        binding.statusText.text = "You asked: \"${result.text}\""
                        pipeline?.askMind(
                            prompt = result.text,
                            isOnline = isDeviceOnline(),
                            onSpoken = { responseText ->
                                runOnUiThread { binding.statusText.text = responseText }
                            }
                        )
                    }
                    is VoiceQueryResult.NoSpeechDetected -> {
                        binding.statusText.text = "Didn't catch that — try again."
                    }
                    is VoiceQueryResult.Failed -> {
                        binding.statusText.text = "Voice input error: ${result.reason}"
                    }
                    is VoiceQueryResult.NotAvailable -> {
                        binding.statusText.text = "Voice recognition is not available on this device."
                    }
                }
            }
        }
    }

    private fun isDeviceOnline(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onDestroy() {
        voiceQueryEngine.destroy()
        pipeline?.shutdown()
        stopService(Intent(this, AurigaCoPilotService::class.java))
        super.onDestroy()
    }
}
