package com.example.service

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.data.SettingsStore
import com.example.ui.KeyboardCanvas
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AiTranslateInputMethodService : InputMethodService() {

    private lateinit var settingsStore: SettingsStore
    private val keyboardLifecycleOwner = KeyboardLifecycleOwner()
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val typedTextState = mutableStateOf("")
    private val statusTextState = mutableStateOf("")
    private val isLoadingState = mutableStateOf(false)
    private var composeView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(applicationContext)
        keyboardLifecycleOwner.onCreate()
    }

    override fun onCreateInputView(): View {
        // Transition custom lifecycle owner to RESUMED so Compose begins measuring and rendering instantly
        keyboardLifecycleOwner.onStart()
        keyboardLifecycleOwner.onResume()

        composeView?.let { existingView ->
            return existingView
        }

        val view = ComposeView(this).apply {
            // Use DisposeOnViewTreeLifecycleDestroyed so that the composition survives across multiple hide/show attachments
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            // Configure Compose Tree Owners for InputMethodService compatibility BEFORE calling setContent
            setViewTreeLifecycleOwner(keyboardLifecycleOwner)
            setViewTreeViewModelStoreOwner(keyboardLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(keyboardLifecycleOwner)

            setContent {
                MyApplicationTheme(dynamicColor = false) {
                    KeyboardCanvas(
                        typedText = typedTextState.value,
                        statusText = statusTextState.value,
                        isLoading = isLoadingState.value,
                        settingsStore = settingsStore,
                        onKeyTyped = { char ->
                            typedTextState.value = typedTextState.value + char
                            currentInputConnection?.commitText(char, 1)
                        },
                        onBackspace = {
                            if (typedTextState.value.isNotEmpty()) {
                                typedTextState.value = typedTextState.value.dropLast(1)
                            }
                            currentInputConnection?.deleteSurroundingText(1, 0)
                        },
                        onSpace = {
                            typedTextState.value = typedTextState.value + " "
                            currentInputConnection?.commitText(" ", 1)
                        },
                        onEnter = {
                            typedTextState.value = typedTextState.value + "\n"
                            currentInputConnection?.commitText("\n", 1)
                        },
                        onClear = {
                            typedTextState.value = ""
                            statusTextState.value = ""
                        },
                        onTranslateAction = { text ->
                            performAiTranslation(text)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        composeView = view
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Transition custom lifecycle owner to RESUMED to make Compose active
        keyboardLifecycleOwner.onStart()
        keyboardLifecycleOwner.onResume()

        // Reset local query buffer when user taps an elegant new input field
        typedTextState.value = ""
        statusTextState.value = "Ready to translate to " + settingsStore.targetLanguage
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        keyboardLifecycleOwner.onPause()
        keyboardLifecycleOwner.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        keyboardLifecycleOwner.onDestroy()
        composeView = null
        serviceJob.cancel()
    }

    private fun performAiTranslation(localText: String) {
        val connection = currentInputConnection ?: return
        
        // Grab the text to translate. If local texttyped buffer is empty,
        // let's try to grab whatever is currently in the active InputConnection!
        var textToTranslate = localText
        var charactersFetchedFromField = false
        
        if (textToTranslate.trim().isEmpty()) {
            val fetched = connection.getTextBeforeCursor(200, 0)
            if (!fetched.isNullOrEmpty()) {
                textToTranslate = fetched.toString()
                charactersFetchedFromField = true
            }
        }

        if (textToTranslate.trim().isEmpty()) {
            statusTextState.value = "Type something first to translate!"
            return
        }

        isLoadingState.value = true
        statusTextState.value = "Translating text using ${settingsStore.selectedProvider}..."

        serviceScope.launch {
            val result = AiTranslator.translate(textToTranslate, settingsStore)
            isLoadingState.value = false

            when (result) {
                is TranslationResult.Success -> {
                    val translatedText = result.text
                    statusTextState.value = "Translated successfully!"
                    
                    // Replace previous text in input editor
                    connection.beginBatchEdit()
                    try {
                        if (charactersFetchedFromField) {
                            // If we read from the active cursor, delete what was before
                            connection.deleteSurroundingText(textToTranslate.length, 0)
                        } else {
                            // If we typed directly on keypads, delete typed buffer length
                            connection.deleteSurroundingText(typedTextState.value.length, 0)
                        }
                        
                        // Insert translated response matching targeted language
                        connection.commitText(translatedText, 1)
                        typedTextState.value = ""
                    } catch (e: Exception) {
                        statusTextState.value = "Error inserting text: ${e.message}"
                    } finally {
                        connection.endBatchEdit()
                    }
                }
                is TranslationResult.Failure -> {
                    statusTextState.value = "Error: ${result.error}"
                }
            }
        }
    }
}

// Custom Lifecycle Owner implementation to host Compose inside custom Services
private class KeyboardLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val controller = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    fun onCreate() {
        if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) {
            controller.performAttach()
            controller.performRestore(null)
            setCurrentState(Lifecycle.State.CREATED)
        }
    }

    fun onStart() {
        setCurrentState(Lifecycle.State.STARTED)
    }

    fun onResume() {
        setCurrentState(Lifecycle.State.RESUMED)
    }

    fun onPause() {
        setCurrentState(Lifecycle.State.STARTED)
    }

    fun onStop() {
        setCurrentState(Lifecycle.State.CREATED)
    }

    fun onDestroy() {
        setCurrentState(Lifecycle.State.DESTROYED)
        store.clear()
    }

    private fun setCurrentState(state: Lifecycle.State) {
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.currentState = state
        }
    }
}
