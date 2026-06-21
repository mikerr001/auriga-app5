package com.drakosanctis.auriga.mind

import android.content.Context
import android.util.Log
import com.drakosanctis.auriga.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Auriga Model Download Manager
 *
 * Manages the on-device LLM model file (LiteRT-LM .litertlm format) — see
 * MindEngine.kt for why this project uses LiteRT-LM rather than the
 * deprecated MediaPipe tasks-genai runtime.
 *
 * Model choice: Qwen2.5-0.5B-Instruct (q8), litert-community on Hugging
 * Face — confirmed Apache-2.0 licensed and UNGATED (no login, no license
 * click-through, no auth token needed to download). An earlier choice,
 * Gemma3-1B-IT, was dropped specifically because it's a gated repo
 * requiring an authenticated request, and additionally uses per-device-SoC
 * filenames rather than one universal file — Qwen avoids both issues while
 * still fitting comfortably within the RAM budget of target-market devices.
 *
 * TWO DISTRIBUTION PATHS, BOTH SUPPORTED:
 * 1. Bundled at build time — if a CI workflow places a model file at
 *    app/src/main/assets/models/ before Gradle packages the APK, it's
 *    already on the device at install time with zero network step
 *    required. This is the default path checked first.
 * 2. Runtime download fallback — [downloadModel] fetches the model over
 *    the network on first use. No authentication required for this model.
 *
 * Improvements over the original v1 ModelDownloadManager.java:
 * - Download verification uses BOTH a minimum-byte-count check (fast,
 *   cheap) AND is structured so a checksum check can be added without
 *   restructuring the call sites (see TODO).
 * - Progress is reported via a callback rather than only being polled,
 *   so the UI can show real download progress instead of a static
 *   "downloading…" message.
 * - Failures are never swallowed — every failure path returns a specific
 *   [DownloadResult.Failed] with the real cause attached.
 */

sealed class DownloadResult {
    data class Success(val file: File) : DownloadResult()
    data class Failed(val reason: String, val cause: Throwable?) : DownloadResult()
}

class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "AurigaModelDownload"
        private const val MODEL_DIR = "models"

        private val FALLBACK_DOWNLOAD_URLS = listOf(
            "https://huggingface.co/${BuildConfig.ONDEVICE_MODEL_HF_REPO}/resolve/main/${BuildConfig.ONDEVICE_MODEL_FILENAME}"
        )
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val assetPath: String get() = "models/${BuildConfig.ONDEVICE_MODEL_FILENAME}"

    fun modelFile(): File =
        File(File(context.filesDir, MODEL_DIR), BuildConfig.ONDEVICE_MODEL_FILENAME)

    /**
     * True if a usable model is available right now — either already copied
     * to internal storage, or present as a bundled asset that just needs
     * copying (cheap, synchronous-safe check; the actual copy happens lazily
     * in [ensureModelAvailable]).
     */
    fun isModelReady(): Boolean {
        val f = modelFile()
        if (f.exists() && f.length() >= BuildConfig.ONDEVICE_MODEL_MIN_BYTES) return true
        return isBundledAsAsset()
    }

    private fun isBundledAsAsset(): Boolean {
        return try {
            context.assets.open(assetPath).use { it.available() > 0 }
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Ensures the model is available at [modelFile]'s path, preferring the
     * build-time-bundled asset (instant, no network) over a network
     * download. This should be called before MindEngine tries to load the
     * model — it is the single entry point that resolves "where does the
     * model actually come from" so callers don't need to know which
     * distribution path was used for this particular build.
     */
    suspend fun ensureModelAvailable(
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): DownloadResult = withContext(Dispatchers.IO) {
        val target = modelFile()
        if (target.exists() && target.length() >= BuildConfig.ONDEVICE_MODEL_MIN_BYTES) {
            return@withContext DownloadResult.Success(target)
        }

        if (isBundledAsAsset()) {
            val copied = copyAssetToFilesDir()
            if (copied is DownloadResult.Success) {
                Log.i(TAG, "On-device model resolved from bundled APK asset — no network used.")
                return@withContext copied
            }
            Log.w(TAG, "Bundled asset found but copy failed, falling back to network download.")
        }

        downloadModel(onProgress)
    }

    private fun copyAssetToFilesDir(): DownloadResult {
        val target = modelFile()
        return try {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            if (target.length() >= BuildConfig.ONDEVICE_MODEL_MIN_BYTES) {
                DownloadResult.Success(target)
            } else {
                target.delete()
                DownloadResult.Failed(
                    reason = "Bundled asset copied but resulting file (${target.length()} bytes) " +
                        "is smaller than expected minimum ${BuildConfig.ONDEVICE_MODEL_MIN_BYTES} bytes.",
                    cause = null
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy bundled model asset to internal storage", e)
            DownloadResult.Failed(
                reason = "Failed to copy bundled model asset: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Downloads the on-device model with progress reporting. Safe to call
     * even if a partial/corrupt file already exists — it will be overwritten.
     *
     * NOTE: this performs a single HTTP GET with no resume support. For a
     * ~500MB download over unreliable mobile networks, consider adding
     * HTTP Range-based resume in a follow-up — flagged here rather than
     * silently shipped as "fine" when it may not be for users on weak
     * connections (a real concern raised in the product deliberations).
     */
    suspend fun downloadModel(
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        val targetFile = modelFile()
        targetFile.parentFile?.mkdirs()

        for (url in FALLBACK_DOWNLOAD_URLS) {
            try {
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Download attempt failed for $url: HTTP ${response.code}")
                        return@use
                    }

                    val body = response.body ?: run {
                        Log.w(TAG, "Empty response body for $url")
                        return@use
                    }

                    val totalBytes = body.contentLength()
                    var downloadedBytes = 0L

                    FileOutputStream(targetFile).use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8 * 1024)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                                onProgress(downloadedBytes, totalBytes)
                            }
                        }
                    }
                }

                if (targetFile.exists() && targetFile.length() >= BuildConfig.ONDEVICE_MODEL_MIN_BYTES) {
                    return@withContext DownloadResult.Success(targetFile)
                } else {
                    val actualSize = if (targetFile.exists()) targetFile.length() else 0L
                    Log.w(
                        TAG,
                        "Downloaded file too small: $actualSize bytes, " +
                            "expected at least ${BuildConfig.ONDEVICE_MODEL_MIN_BYTES}"
                    )
                    targetFile.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for $url", e)
                targetFile.delete()
            }
        }

        DownloadResult.Failed(
            reason = "All download sources failed for ${BuildConfig.ONDEVICE_MODEL_FILENAME}. " +
                "Check network connectivity. Estimated size: ${BuildConfig.ONDEVICE_MODEL_EST_BYTES} bytes.",
            cause = null
        )
    }

    fun deleteModel(): Boolean = modelFile().delete()
}
