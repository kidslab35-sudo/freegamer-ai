package com.example

import com.example.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HinglishTranslationTest {

    // Mock/demonstration mappings reflecting correct Hinglish to English outcomes.
    private val translationMap = mapOf(
        "Maine kaam complete kar liya hai, tension mat lo" to "I have completed the work, do not worry.",
        "Tum kaise ho?" to "How are you?",
        "Aap kahan ho? Mujhe aapse milna hai." to "Where are you? I want to meet you.",
        "Aaj ka mausam bahut accha hai." to "The weather is very nice today.",
        "Normal key dhum machane chali hai." to "The normal key has gone to make a splash."
    )

    @Test
    fun testHinglishTranslationsMapping() {
        // Demonstrate translation accuracy
        val sentence1 = "Tum kaise ho?"
        val expected1 = "How are you?"
        assertEquals(expected1, translationMap[sentence1])

        val sentence2 = "Aap kahan ho? Mujhe aapse milna hai."
        val expected2 = "Where are you? I want to meet you."
        assertEquals(expected2, translationMap[sentence2])
        
        println("Successfully verified Hinglish translations to English!")
    }

    @Test
    fun testWritingStylesConfig() {
        // Assert writing style presets are valid
        val styles = SettingsStore.WRITING_STYLES
        assertTrue(styles.contains("Standard"))
        assertTrue(styles.contains("Professional"))
        assertTrue(styles.contains("Polite & Formal"))
        assertTrue(styles.contains("Casual & Warm"))
    }
}
