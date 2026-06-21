package com.drakosanctis.auriga.mind

import com.drakosanctis.auriga.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Auriga Cloud Mind Client — PLACEHOLDER INTERFACE
 *
 * This is intentionally a stub/placeholder, as agreed: the actual cloud LLM
 * provider and API key are not wired up yet. CLOUD_LLM_ENDPOINT and
 * CLOUD_LLM_API_KEY in BuildConfig are empty/placeholder values.
 *
 * TO ACTIVATE CLOUD MODE LATER:
 * 1. Pick a provider (e.g. Anthropic, OpenAI, or any HTTP-based chat API).
 * 2. Set CLOUD_LLM_ENDPOINT and CLOUD_LLM_API_KEY in app/build.gradle.kts
 *    (or better: inject via a local.properties / CI secret, never commit
 *    a real key to source control).
 * 3. Adjust [buildRequestBody] and [parseResponse] to match that provider's
 *    actual request/response JSON shape — this stub assumes a generic
 *    {"prompt": "..."} -> {"text": "..."} shape that will NOT match any
 *    real provider as-is.
 *
 * Until activated, [generate] always returns a failure Result, which
 * MindEngine handles gracefully (falls back to "cloud unavailable" messaging
 * rather than crashing).
 */
class CloudMindClient(
    private val endpoint: String = BuildConfig.CLOUD_LLM_ENDPOINT,
    private val apiKey: String = BuildConfig.CLOUD_LLM_API_KEY
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean = apiKey.isNotBlank() && endpoint.isNotBlank() &&
        !endpoint.contains("placeholder.invalid")

    suspend fun generate(prompt: String): Result<String> {
        if (!isConfigured()) {
            return Result.failure(
                IllegalStateException(
                    "Cloud LLM is not configured. Set CLOUD_LLM_ENDPOINT and " +
                        "CLOUD_LLM_API_KEY before enabling cloud mode."
                )
            )
        }

        return try {
            val body = buildRequestBody(prompt)
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(
                        IOException("Cloud LLM request failed: HTTP ${response.code}")
                    )
                }
                val bodyString = response.body?.string()
                    ?: return Result.failure(IOException("Empty response body from cloud LLM"))
                Result.success(parseResponse(bodyString))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** PLACEHOLDER shape — replace to match the real provider's API contract. */
    private fun buildRequestBody(prompt: String): JSONObject {
        return JSONObject().apply {
            put("prompt", prompt)
            put("max_tokens", 512)
        }
    }

    /** PLACEHOLDER shape — replace to match the real provider's response contract. */
    private fun parseResponse(body: String): String {
        return try {
            JSONObject(body).optString("text", "")
        } catch (e: Exception) {
            ""
        }
    }
}
