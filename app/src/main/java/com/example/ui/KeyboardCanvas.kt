package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardCanvas(
    typedText: String,
    statusText: String,
    isLoading: Boolean,
    settingsStore: SettingsStore,
    onKeyTyped: (String) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    onClear: () -> Unit,
    onTranslateAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isShiftEnabled by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf(settingsStore.selectedProvider) }
    var selectedLang by remember { mutableStateOf(settingsStore.targetLanguage) }
    var selectedStyle by remember { mutableStateOf(settingsStore.writingStyle) }
    
    var showLangPicker by remember { mutableStateOf(false) }
    var showProviderPicker by remember { mutableStateOf(false) }

    // Synchronize settings Store changes
    LaunchedEffect(selectedProvider) {
        settingsStore.selectedProvider = selectedProvider
    }
    LaunchedEffect(selectedLang) {
        settingsStore.targetLanguage = selectedLang
    }

    val row1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val row2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    val row3 = listOf("z", "x", "c", "v", "b", "n", "m")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E)) // Dark professional theme for the keyboard bounds
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .testTag("hardware_simulated_keyboard")
    ) {
        // --- 1. Keyboard Tool Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left block: Quick selection chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Provider Chip
                Button(
                    onClick = {
                        showProviderPicker = !showProviderPicker
                        showLangPicker = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF333333),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(selectedProvider, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Language Chip
                Button(
                    onClick = {
                        showLangPicker = !showLangPicker
                        showProviderPicker = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00bcd4), // Cyan theme for target selector
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("→ $selectedLang", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // AI Instant Translate Button (Prominent Action)
            Button(
                onClick = { onTranslateAction(typedText) },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF5722), // Vibrant translation color orange
                    contentColor = Color.White,
                    disabledContainerColor = Color(0x66FF5722)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("ai_translate_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("AI Translate ✨", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        // --- 2. Live Pickers (Collapsed by Default) ---
        AnimatedVisibility(visible = showProviderPicker) {
            Surface(
                color = Color(0xFF252525),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    Text("Select AI Translation Engine:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(SettingsStore.PROVIDERS) { item ->
                            val isSelected = selectedProvider == item
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFFFF5722) else Color(0xFF444444))
                                    .clickable {
                                        selectedProvider = item
                                        showProviderPicker = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = item,
                                    color = if (isSelected) Color.White else Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = showLangPicker) {
            Surface(
                color = Color(0xFF252525),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    Text("Select Target Language:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(SettingsStore.LANGUAGES) { item ->
                            val isSelected = selectedLang == item
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF00bcd4) else Color(0xFF444444))
                                    .clickable {
                                        selectedLang = item
                                        showLangPicker = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = item,
                                    color = if (isSelected) Color.Black else Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 3. Keyboard Status Field ---
        if (statusText.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2C2C2C))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Status",
                    tint = if (statusText.contains("Error") || statusText.contains("not set")) Color(0xFFE57373) else Color(0xFF81C784),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = statusText,
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clickable { onClear() }
                        .padding(horizontal = 4.dp)
                ) {
                    Text("CLEAR", color = Color(0xFFFF5722), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- 4. Character Grid Rows (Classic Keyboard Layout) ---
        Spacer(modifier = Modifier.height(2.dp))

        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            row1.forEach { char ->
                val label = if (isShiftEnabled) char.uppercase() else char
                KeyboardKey(text = label, modifier = Modifier.weight(1f)) {
                    onKeyTyped(label)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            row2.forEach { char ->
                val label = if (isShiftEnabled) char.uppercase() else char
                KeyboardKey(text = label, modifier = Modifier.weight(1f)) {
                    onKeyTyped(label)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Row 3 (Shift + Chars + Backspace)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shift Key
            KeyboardSpecialKey(
                text = "⬆",
                isSelected = isShiftEnabled,
                modifier = Modifier.weight(1.3f)
            ) {
                isShiftEnabled = !isShiftEnabled
            }

            row3.forEach { char ->
                val label = if (isShiftEnabled) char.uppercase() else char
                KeyboardKey(text = label, modifier = Modifier.weight(1f)) {
                    onKeyTyped(label)
                    // Reset Shift button single tap behavior
                    if (isShiftEnabled) {
                        isShiftEnabled = false
                    }
                }
            }

            // Backspace
            KeyboardSpecialKey(
                text = "⌫",
                isSelected = false,
                modifier = Modifier.weight(1.3f)
            ) {
                onBackspace()
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Row 4 (Style Picker, Space, Enter)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clear / Style Quick Information
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1.8f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF333333))
                    .clickable {
                        // Quick toggle style
                        val index = SettingsStore.WRITING_STYLES.indexOf(selectedStyle)
                        val nextIndex = (index + 1) % SettingsStore.WRITING_STYLES.size
                        selectedStyle = SettingsStore.WRITING_STYLES[nextIndex]
                        settingsStore.writingStyle = selectedStyle
                    }
            ) {
                Text(
                    text = "✍️ $selectedStyle",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            // Space
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(4f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF4E4E4E))
                    .clickable { onSpace() }
            ) {
                Text(
                    text = "Space",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }

            // Enter
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1.8f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF555555))
                    .clickable { onEnter() }
            ) {
                Text(
                    text = "⏎",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun RowScope.KeyboardKey(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF3A3A3A))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color.White)
            ) { onClick() }
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun KeyboardSpecialKey(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) Color(0xFFFF5722) else Color(0xFF2C2C2C))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color.White)
            ) { onClick() }
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}
