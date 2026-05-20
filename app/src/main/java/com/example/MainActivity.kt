package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsStore
import com.example.service.AiTranslator
import com.example.service.TranslationResult
import com.example.ui.KeyboardCanvas
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settingsStore = SettingsStore(applicationContext)

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    "AI KEYBOARD", 
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.5.sp
                                )
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                ) { innerPadding ->
                    MainDashboard(
                        settingsStore = settingsStore,
                        onEnableKeyboard = { openKeyboardSettings() },
                        onSelectKeyboard = { showKeyboardPicker() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun openKeyboardSettings() {
        try {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback setting
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun showKeyboardPicker() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainDashboard(
    settingsStore: SettingsStore,
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // 1. Keyboard settings check (Reactive polling on lifecycle check)
    var isKeyboardEnabled by remember { mutableStateOf(false) }
    var isKeyboardSelected by remember { mutableStateOf(false) }

    fun checkKeyboardStatus() {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val enabledList = imm.enabledInputMethodList
            isKeyboardEnabled = enabledList?.any { it.packageName == context.packageName } ?: false

            val defaultKeyboard = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            isKeyboardSelected = defaultKeyboard != null && defaultKeyboard.contains(context.packageName)
        } catch (e: Exception) {
            e.printStackTrace()
            isKeyboardEnabled = false
            isKeyboardSelected = false
        }
    }

    // Perform check on initialization
    LaunchedEffect(Unit) {
        checkKeyboardStatus()
    }

    // Trigger check periodically or on interaction
    var showApiKeysErrorWarning by remember { mutableStateOf(false) }

    // Settings States
    var selectedProvider by remember { mutableStateOf(settingsStore.selectedProvider) }
    var useDefaultGemini by remember { mutableStateOf(settingsStore.useDefaultGemini) }
    var geminiKey by remember { mutableStateOf(settingsStore.geminiApiKey) }
    var gptKey by remember { mutableStateOf(settingsStore.gptApiKey) }
    var claudeKey by remember { mutableStateOf(settingsStore.claudeApiKey) }
    var qwenKey by remember { mutableStateOf(settingsStore.qwenApiKey) }

    var targetLang by remember { mutableStateOf(settingsStore.targetLanguage) }
    var writingStyle by remember { mutableStateOf(settingsStore.writingStyle) }
    var customPrompt by remember { mutableStateOf(settingsStore.customPrompt) }

    // Security warning visibility state
    var showSecurityWarning by remember { mutableStateOf(true) }

    // Key Masking Visibility States
    var isGeminiKeyVisible by remember { mutableStateOf(false) }
    var isGptKeyVisible by remember { mutableStateOf(false) }
    var isClaudeKeyVisible by remember { mutableStateOf(false) }
    var isQwenKeyVisible by remember { mutableStateOf(false) }

    // Simulated Keyboard States
    var simulatedTypedText by remember { mutableStateOf("") }
    var simulatedStatusText by remember { mutableStateOf("Simulator Ready. Tap keys to type!") }
    var isSimulatedTranslating by remember { mutableStateOf(false) }

    // Save configurations
    fun saveConfigs() {
        settingsStore.selectedProvider = selectedProvider
        settingsStore.useDefaultGemini = useDefaultGemini
        settingsStore.geminiApiKey = geminiKey
        settingsStore.gptApiKey = gptKey
        settingsStore.claudeApiKey = claudeKey
        settingsStore.qwenApiKey = qwenKey
        settingsStore.targetLanguage = targetLang
        settingsStore.writingStyle = writingStyle
        settingsStore.customPrompt = customPrompt
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Welcome Hero Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🌐  ",
                        fontSize = 22.sp
                    )
                    Text(
                        text = "Real-time AI Translation Keyboard",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Translate anything you type across all apps automatically. Power your chats with multiple state-of-the-art translation frameworks.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // --- SECTION 1: System-wide Keyboard Status ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "System keyboard integration",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    IconButton(onClick = { checkKeyboardStatus() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Check device status")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Condition 1: Keyboard Enabled in Settings
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isKeyboardEnabled) Icons.Default.CheckCircle else Icons.Default.Close,
                        contentDescription = "Status icon",
                        tint = if (isKeyboardEnabled) Color(0xFF66BB6A) else Color(0xFFEF5350),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isKeyboardEnabled) "AI Keyboard is enabled in Settings." else "AI Keyboard is disabled in Settings.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Condition 2: Keyboard Selected as Active
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isKeyboardSelected) Icons.Default.CheckCircle else Icons.Default.Close,
                        contentDescription = "Active status icon",
                        tint = if (isKeyboardSelected) Color(0xFF66BB6A) else Color(0xFFEF5350),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isKeyboardSelected) "AI Keyboard is set as your CURRENT active on-screen keyboard." else "AI Keyboard is NOT selected as your current active keyboard.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isKeyboardEnabled) {
                        Button(
                            onClick = {
                                onEnableKeyboard()
                                checkKeyboardStatus()
                            },
                            modifier = Modifier.weight(1f).height(40.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("1. Enable Keyboard", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            onSelectKeyboard()
                            checkKeyboardStatus()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("2. Activate Keyboard", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- SECTION 2: Dynamic Keys Configuration ---
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "AI Engines & Credentials",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Configure API keys for the translation services you plan on using. Key entries are securely stored locally.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Engine Select Dropdown
                Text("Primary Translation Engine:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SettingsStore.PROVIDERS.forEach { provider ->
                        val isSelected = selectedProvider == provider
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedProvider = provider
                                saveConfigs()
                            },
                            label = { Text(provider, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Gemini Specific Configuration
                if (selectedProvider == "Gemini") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            useDefaultGemini = !useDefaultGemini
                            saveConfigs()
                        }
                    ) {
                        Checkbox(
                            checked = useDefaultGemini,
                            onCheckedChange = {
                                useDefaultGemini = it
                                saveConfigs()
                            }
                        )
                        Column {
                            Text("Use AI Studio developer key", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text("Uses the secure pre-configured pipeline from Gemini", color = Color.Gray, fontSize = 10.sp)
                        }
                    }

                    if (!useDefaultGemini) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = geminiKey,
                            onValueChange = {
                                geminiKey = it
                                saveConfigs()
                            },
                            label = { Text("Personal Gemini API Key") },
                            placeholder = { Text("Paste AI Studio API Key...") },
                            visualTransformation = if (isGeminiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                Text(
                                    text = if (isGeminiKeyVisible) "HIDE" else "SHOW",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { isGeminiKeyVisible = !isGeminiKeyVisible }
                                        .padding(8.dp)
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ChatGPT Specific Configuration
                if (selectedProvider == "ChatGPT") {
                    OutlinedTextField(
                        value = gptKey,
                        onValueChange = {
                            gptKey = it
                            saveConfigs()
                        },
                        label = { Text("OpenAI API Key") },
                        placeholder = { Text("sk-proj-...") },
                        visualTransformation = if (isGptKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            Text(
                                    text = if (isGptKeyVisible) "HIDE" else "SHOW",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { isGptKeyVisible = !isGptKeyVisible }
                                        .padding(8.dp)
                                )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Claude Specific Configuration
                if (selectedProvider == "Claude") {
                    OutlinedTextField(
                        value = claudeKey,
                        onValueChange = {
                            claudeKey = it
                            saveConfigs()
                        },
                        label = { Text("Anthropic Claude API Key") },
                        placeholder = { Text("sk-ant-...") },
                        visualTransformation = if (isClaudeKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            Text(
                                    text = if (isClaudeKeyVisible) "HIDE" else "SHOW",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { isClaudeKeyVisible = !isClaudeKeyVisible }
                                        .padding(8.dp)
                                )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Qwen Specific Configuration
                if (selectedProvider == "Qwen") {
                    OutlinedTextField(
                        value = qwenKey,
                        onValueChange = {
                            qwenKey = it
                            saveConfigs()
                        },
                        label = { Text("Alibaba DashScope Key") },
                        placeholder = { Text("Paste DashScope API Key...") },
                        visualTransformation = if (isQwenKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            Text(
                                    text = if (isQwenKeyVisible) "HIDE" else "SHOW",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { isQwenKeyVisible = !isQwenKeyVisible }
                                        .padding(8.dp)
                                )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Note: Qwen utilizes Alibaba's high-speed DashScope chat model 'qwen-plus'.",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        // --- SECTION 3: Tuning Translation parameters ---
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "AI Translation Tuning",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Adjust standard speech values, formatting and styles for translated output.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Target Language select
                Text("Target Language:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(SettingsStore.LANGUAGES) { lang ->
                        val isSelected = targetLang == lang
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                targetLang = lang
                                saveConfigs()
                            },
                            label = { Text(lang, fontSize = 10.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Writing Style select
                Text("Writing Tone / Style Direction:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(SettingsStore.WRITING_STYLES) { style ->
                        val isSelected = writingStyle == style
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                writingStyle = style
                                saveConfigs()
                            },
                            label = { Text(style, fontSize = 10.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom constraints
                OutlinedTextField(
                    value = customPrompt,
                    onValueChange = {
                        customPrompt = it
                        saveConfigs()
                    },
                    label = { Text("Custom Prompt rules (Optional)") },
                    placeholder = { Text("e.g. Always append 🤖, translate as British English...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // --- SECURITY WARNING ACCORDION ---
        if (showSecurityWarning) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)), // Visual Warning yellow background
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = "Alert", tint = Color(0xFFF57F17), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Security Information", fontWeight = FontWeight.Bold, color = Color(0xFF5D4037), fontSize = 12.sp)
                        }
                        IconButton(onClick = { showSecurityWarning = false }, modifier = Modifier.size(24.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close warning", tint = Color.Gray, modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Security Warning: I have included your API keys in the generated APK file for this prototype. Please be aware that Android APKs can be easily decompiled, and these keys can be extracted by anyone who has access to the file. Do not share this APK file publicly or with unauthorized individuals to prevent potential misuse.",
                        fontSize = 10.sp,
                        color = Color(0xFF5D4037),
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // --- SECTION 4: Built-in Live Keyboard Simulator ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Sandbox", tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Live On-Screen Simulator",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Text(
                    text = "Type using the hardware-simulated keypad below to watch real translation occurrences without modifying system settings.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Simulated Interactive App Text Field Editor Input Connection
                OutlinedTextField(
                    value = simulatedTypedText,
                    onValueChange = { simulatedTypedText = it },
                    label = { Text("Active Simulated App Input Field") },
                    placeholder = { Text("Type here using keypad below or tap test buttons...") },
                    modifier = Modifier.fillMaxWidth().testTag("simulated_input_field"),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Simulated Keypad Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    KeyboardCanvas(
                        typedText = simulatedTypedText,
                        statusText = simulatedStatusText,
                        isLoading = isSimulatedTranslating,
                        settingsStore = settingsStore,
                        onKeyTyped = { char ->
                            simulatedTypedText += char
                        },
                        onBackspace = {
                            if (simulatedTypedText.isNotEmpty()) {
                                simulatedTypedText = simulatedTypedText.dropLast(1)
                            }
                        },
                        onSpace = {
                            simulatedTypedText += " "
                        },
                        onEnter = {
                            simulatedTypedText += "\n"
                        },
                        onClear = {
                            simulatedTypedText = ""
                            simulatedStatusText = "Cleared typed inputs."
                        },
                        onTranslateAction = { text ->
                            if (text.trim().isEmpty()) {
                                simulatedStatusText = "Error: Input is empty! Type text on keyboard first."
                                return@KeyboardCanvas
                            }

                            isSimulatedTranslating = true
                            simulatedStatusText = "Translating text into $targetLang via $selectedProvider..."

                            coroutineScope.launch {
                                val result = AiTranslator.translate(text, settingsStore)
                                isSimulatedTranslating = false
                                when (result) {
                                    is TranslationResult.Success -> {
                                        simulatedTypedText = result.text
                                        simulatedStatusText = "Translation finalized successfully!"
                                    }
                                    is TranslationResult.Failure -> {
                                        simulatedStatusText = "Translation failed: ${result.error}"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pre-baked testing quick suggestions
                Text("Select Sample Text to Keyboard:", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val samples = listOf("Hello, how are you today?", "Please wait, I am busy.")
                    samples.forEach { sample ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    simulatedTypedText = sample
                                    simulatedStatusText = "Loaded sample text. Tap AI Translate ✨!"
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(sample, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

