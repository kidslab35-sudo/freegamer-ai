package com.example.data

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ai_translate_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_PROVIDER = "key_provider"
        const val KEY_GEMINI_KEY = "key_gemini_key"
        const val KEY_GPT_KEY = "key_gpt_key"
        const val KEY_CLAUDE_KEY = "key_claude_key"
        const val KEY_QWEN_KEY = "key_qwen_key"
        const val KEY_TARGET_LANG = "key_target_lang"
        const val KEY_WRITING_STYLE = "key_writing_style"
        const val KEY_CUSTOM_PROMPT = "key_custom_prompt"
        const val KEY_USE_DEFAULT_GEMINI = "key_use_default_gemini"

        val PROVIDERS = listOf("Gemini", "ChatGPT", "Claude", "Qwen")
        val LANGUAGES = listOf(
            "English", "Spanish", "Japanese", "French", "German", "Korean", 
            "Chinese", "Arabic", "Portuguese", "Italian", "Russian", 
            "Vietnamese", "Thai", "Turkish", "Hindi"
        )
        val WRITING_STYLES = listOf(
            "Standard", "Polite & Formal", "Casual & Warm", "Professional", 
            "Academic", "Friendly with Emojis", "Youth Slang", "Ultra-Concise"
        )
    }

    var selectedProvider: String
        get() = prefs.getString(KEY_PROVIDER, "Gemini") ?: "Gemini"
        set(value) = prefs.edit().putString(KEY_PROVIDER, value).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_KEY, value).apply()

    var gptApiKey: String
        get() = prefs.getString(KEY_GPT_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GPT_KEY, value).apply()

    var claudeApiKey: String
        get() = prefs.getString(KEY_CLAUDE_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLAUDE_KEY, value).apply()

    var qwenApiKey: String
        get() = prefs.getString(KEY_QWEN_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_QWEN_KEY, value).apply()

    var targetLanguage: String
        get() = prefs.getString(KEY_TARGET_LANG, "English") ?: "English"
        set(value) = prefs.edit().putString(KEY_TARGET_LANG, value).apply()

    var writingStyle: String
        get() = prefs.getString(KEY_WRITING_STYLE, "Standard") ?: "Standard"
        set(value) = prefs.edit().putString(KEY_WRITING_STYLE, value).apply()

    var customPrompt: String
        get() = prefs.getString(KEY_CUSTOM_PROMPT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_PROMPT, value).apply()

    var useDefaultGemini: Boolean
        get() = prefs.getBoolean(KEY_USE_DEFAULT_GEMINI, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_DEFAULT_GEMINI, value).apply()
}
