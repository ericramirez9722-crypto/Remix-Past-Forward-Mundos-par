package com.pastforward.data.network

import android.util.Log
import com.google.gson.Gson
import com.pastforward.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.pow

object GeminiService {
    private const val TAG = "GeminiService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    // Endpoint for gemini-2.5-flash-image
    private const val MODEL_NAME = "gemini-2.5-flash-image"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    // Fallback prompt template
    fun getFallbackPrompt(decadeOrWorld: String): String {
        return "Create a photograph of the person in this image as if they were living in the $decadeOrWorld. " +
                "The photograph should capture the distinct fashion, hairstyles, and overall atmosphere of that time period or environment. " +
                "Ensure the final image is a clear photograph that looks authentic."
    }

    suspend fun generateStyledImage(
        base64Image: String,
        prompt: String,
        fallbackStyle: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty()) {
            return@withContext Result.failure(Exception("Gemini API key is not configured. Please check your environment variables."))
        }

        // Clean base64 if it has header like "data:image/jpeg;base64,"
        val cleanBase64 = if (base64Image.contains(",")) {
            base64Image.substringAfter(",")
        } else {
            base64Image
        }

        // Detect mimeType (default to image/jpeg)
        val mimeType = if (base64Image.contains("image/png")) "image/png" else "image/jpeg"

        val url = "$BASE_URL?key=$apiKey"

        Log.d(TAG, "Starting generation with prompt: $prompt")
        var lastError: Throwable? = null

        // Attempt 1: Call Gemini with Retry and the original prompt
        try {
            val base64Response = callGeminiWithRetry(cleanBase64, mimeType, prompt, url)
            return@withContext Result.success(base64Response)
        } catch (e: Exception) {
            Log.w(TAG, "Original prompt failed, checking if it was blocked or rate limited. Error: ${e.message}")
            lastError = e
        }

        // Attempt 2: Fallback prompt if fallbackStyle is provided and the original is censored/failed
        if (fallbackStyle != null) {
            try {
                val fallbackPrompt = getFallbackPrompt(fallbackStyle)
                Log.d(TAG, "Attempting generation with fallback prompt: $fallbackPrompt")
                val base64Response = callGeminiWithRetry(cleanBase64, mimeType, fallbackPrompt, url)
                return@withContext Result.success(base64Response)
            } catch (e: Exception) {
                Log.e(TAG, "Fallback prompt also failed: ${e.message}")
                lastError = e
            }
        }

        return@withContext Result.failure(lastError ?: Exception("Unknown image generation error"))
    }

    private suspend fun callGeminiWithRetry(
        base64Image: String,
        mimeType: String,
        prompt: String,
        url: String
    ): String {
        val maxRetries = 4
        val initialDelay = 2000L

        for (attempt in 1..maxRetries) {
            try {
                return executeApiRequest(base64Image, mimeType, prompt, url)
            } catch (e: Exception) {
                Log.e(TAG, "API attempt $attempt/$maxRetries failed: ${e.message}")
                val errorMsg = e.message ?: ""
                val isRateLimit = errorMsg.contains("429") || errorMsg.contains("quota") || errorMsg.contains("RESOURCE_EXHAUSTED")
                val isInternal = errorMsg.contains("500") || errorMsg.contains("INTERNAL") || errorMsg.contains("overloaded")

                if ((isRateLimit || isInternal) && attempt < maxRetries) {
                    val baseDelay = if (isRateLimit) 8000L else initialDelay
                    // Exponential backoff + jitter
                    val delay = (baseDelay * 2.0.pow(attempt - 1).toLong()) + (0..1000).random()
                    Log.d(TAG, "Retrying in ${delay}ms. Reason: ${if (isRateLimit) "Rate Limit" else "Internal Error"}")
                    kotlinx.coroutines.delay(delay)
                    continue
                }

                // Handle friendly errors
                if (isRateLimit) {
                    if (errorMsg.contains("quota")) {
                        throw Exception("Quota exceeded. Please check your Gemini API billing or wait for the quota to reset.")
                    }
                    throw Exception("Rate limit exceeded. The AI is busy, please wait a minute and try again.")
                }
                throw e
            }
        }
        throw Exception("API call failed after max retries.")
    }

    private fun executeApiRequest(
        base64Image: String,
        mimeType: String,
        prompt: String,
        url: String
    ): String {
        // Construct standard REST JSON payload equivalent to Javascript client
        val payload = mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf(
                            "inlineData" to mapOf(
                                "mimeType" to mimeType,
                                "data" to base64Image
                            )
                        ),
                        mapOf(
                            "text" to prompt
                        )
                    )
                )
            )
        )

        val jsonString = gson.toJson(payload)
        val body = jsonString.toRequestBody(mediaTypeJson)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IOException("Gemini API Error (HTTP ${response.code}): $responseBody")
            }

            // Parse response
            val parsed = gson.fromJson(responseBody, GeminiResponse::class.java)
            val inlineData = parsed.candidates?.firstOrNull()
                ?.content?.parts?.firstOrNull()
                ?.inlineData

            if (inlineData != null) {
                return "data:${inlineData.mimeType};base64,${inlineData.data}"
            }

            // If response text is present but no image, check safety block text
            val fallbackText = parsed.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            if (fallbackText.lowercase().contains("safety") || fallbackText.isEmpty()) {
                throw SafetyException("Content filtered for safety. Try a different photo.")
            }

            throw IOException("Gemini API replied with text instead of image: \"$fallbackText\"")
        }
    }
}

// Custom safety block exception
class SafetyException(message: String) : IOException(message)

// GSON classes mapping standard Gemini API Response structure
data class GeminiResponse(val candidates: List<GeminiCandidate>?)
data class GeminiCandidate(val content: GeminiContent?)
data class GeminiContent(val parts: List<GeminiPart>?)
data class GeminiPart(val text: String?, val inlineData: GeminiInlineData?)
data class GeminiInlineData(val mimeType: String, val data: String)
