package com.example.service

import android.util.Log
import com.example.BuildConfig
import com.example.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiTranslator {
    private const val TAG = "AiTranslator"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun translate(text: String, settings: SettingsStore): TranslationResult = withContext(Dispatchers.IO) {
        if (text.trim().isEmpty()) {
            return@withContext TranslationResult.Success("")
        }

        val provider = settings.selectedProvider
        val targetLang = settings.targetLanguage
        val style = settings.writingStyle
        val customPrompt = settings.customPrompt

        // Construct a highly descriptive prompt
        val systemPrompt = "You are a professional, high-fidelity auto-translator keyboard assistant. " +
                "Translate input text into $targetLang using a '$style' tone. " +
                (if (customPrompt.isNotEmpty()) "Apply these extra instructions: $customPrompt. " else "") +
                "Output ONLY the exact translated text. Do NOT add any preamble, congratulations, " +
                "quotation marks, notes, formatting, or commentary. Your output will be committed directly " +
                "into the user's active keyboard selection, so absolute precision is required."

        val userPrompt = text

        try {
            when (provider) {
                "Gemini" -> callGemini(userPrompt, systemPrompt, settings)
                "ChatGPT" -> callChatGPT(userPrompt, systemPrompt, settings)
                "Claude" -> callClaude(userPrompt, systemPrompt, settings)
                "Qwen" -> callQwen(userPrompt, systemPrompt, settings)
                else -> TranslationResult.Failure("Unknown provider: $provider")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Translation error", e)
            TranslationResult.Failure("Network/API Error: ${e.localizedMessage ?: e.message}")
        }
    }

    private fun callGemini(prompt: String, systemInstruction: String, settings: SettingsStore): TranslationResult {
        val apiKey = if (settings.useDefaultGemini) {
            BuildConfig.GEMINI_API_KEY
        } else {
            settings.geminiApiKey
        }

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return TranslationResult.Failure("Gemini API Key is not set. Please configure it in settings.")
        }

        // Gemini REST API URL (using gemini-3.5-flash as specified in requirements)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val requestBodyJson = JSONObject().apply {
            // contents array
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        val partObj = JSONObject().apply {
                            put("text", "System Instructions: $systemInstruction\n\nText to translate: $prompt")
                        }
                        put(partObj)
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            // generationConfig
            val genConfigObj = JSONObject().apply {
                put("temperature", 0.3) // Lower temperature for accurate translation
            }
            put("generationConfig", genConfigObj)
        }

        val requestBody = requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val rawResponse = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return parseApiError(rawResponse, "Gemini error (${response.code})")
            }

            return try {
                val jsonResponse = JSONObject(rawResponse)
                val candidates = jsonResponse.getJSONArray("candidates")
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                val translatedText = parts.getJSONObject(0).getString("text").trim()
                TranslationResult.Success(translatedText)
            } catch (e: Exception) {
                TranslationResult.Failure("Failed to parse Gemini response: ${e.message}")
            }
        }
    }

    private fun callChatGPT(prompt: String, systemInstruction: String, settings: SettingsStore): TranslationResult {
        val apiKey = settings.gptApiKey
        if (apiKey.trim().isEmpty()) {
            return TranslationResult.Failure("ChatGPT API Key is not set. Please configure it in settings.")
        }

        val url = "https://api.openai.com/v1/chat/completions"

        val requestBodyJson = JSONObject().apply {
            put("model", "gpt-4o-mini")
            
            val messagesArray = JSONArray().apply {
                val sysMsg = JSONObject().apply {
                    put("role", "system")
                    put("content", systemInstruction)
                }
                val userMsg = JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }
                put(sysMsg)
                put(userMsg)
            }
            put("messages", messagesArray)
            put("temperature", 0.3)
        }

        val requestBody = requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val rawResponse = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return parseApiError(rawResponse, "ChatGPT error (${response.code})")
            }

            return try {
                val jsonResponse = JSONObject(rawResponse)
                val choices = jsonResponse.getJSONArray("choices")
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                val translatedText = message.getString("content").trim()
                TranslationResult.Success(translatedText)
            } catch (e: Exception) {
                TranslationResult.Failure("Failed to parse ChatGPT response: ${e.message}")
            }
        }
    }

    private fun callClaude(prompt: String, systemInstruction: String, settings: SettingsStore): TranslationResult {
        val apiKey = settings.claudeApiKey
        if (apiKey.trim().isEmpty()) {
            return TranslationResult.Failure("Claude API key not set. Please configure it in settings.")
        }

        val url = "https://api.anthropic.com/v1/messages"

        val requestBodyJson = JSONObject().apply {
            put("model", "claude-3-5-sonnet-20241022")
            put("max_tokens", 1024)
            put("system", systemInstruction)

            val messagesArray = JSONArray().apply {
                val userMsg = JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }
                put(userMsg)
            }
            put("messages", messagesArray)
            put("temperature", 0.3)
        }

        val requestBody = requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val rawResponse = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return parseApiError(rawResponse, "Claude error (${response.code})")
            }

            return try {
                val jsonResponse = JSONObject(rawResponse)
                val contentArray = jsonResponse.getJSONArray("content")
                val textObj = contentArray.getJSONObject(0)
                val translatedText = textObj.getString("text").trim()
                TranslationResult.Success(translatedText)
            } catch (e: Exception) {
                TranslationResult.Failure("Failed to parse Claude response: ${e.message}")
            }
        }
    }

    private fun callQwen(prompt: String, systemInstruction: String, settings: SettingsStore): TranslationResult {
        val apiKey = settings.qwenApiKey
        if (apiKey.trim().isEmpty()) {
            return TranslationResult.Failure("Qwen API key not set. Please configure it in settings.")
        }

        // Qwen uses standard OpenAI compatibility on DashScope
        val url = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"

        val requestBodyJson = JSONObject().apply {
            put("model", "qwen-plus")
            
            val messagesArray = JSONArray().apply {
                val sysMsg = JSONObject().apply {
                    put("role", "system")
                    put("content", systemInstruction)
                }
                val userMsg = JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }
                put(sysMsg)
                put(userMsg)
            }
            put("messages", messagesArray)
            put("temperature", 0.3)
        }

        val requestBody = requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val rawResponse = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return parseApiError(rawResponse, "Qwen error (${response.code})")
            }

            return try {
                val jsonResponse = JSONObject(rawResponse)
                val choices = jsonResponse.getJSONArray("choices")
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                val translatedText = message.getString("content").trim()
                TranslationResult.Success(translatedText)
            } catch (e: Exception) {
                TranslationResult.Failure("Failed to parse Qwen response: ${e.message}")
            }
        }
    }

    private fun parseApiError(rawResponse: String, defaultHeader: String): TranslationResult {
        return try {
            val json = JSONObject(rawResponse)
            if (json.has("error")) {
                val errObj = json.get("error")
                if (errObj is JSONObject) {
                    val message = errObj.optString("message", "")
                    if (message.isNotEmpty()) return TranslationResult.Failure("$defaultHeader: $message")
                } else if (errObj is String) {
                    return TranslationResult.Failure("$defaultHeader: $errObj")
                }
            }
            TranslationResult.Failure("$defaultHeader: $rawResponse")
        } catch (e: Exception) {
            TranslationResult.Failure("$defaultHeader: $rawResponse")
        }
    }
}

sealed class TranslationResult {
    data class Success(val text: String) : TranslationResult()
    data class Failure(val error: String) : TranslationResult()
}
