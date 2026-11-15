package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.signOutUser
import com.example.data.model.AssistantLanguage
import com.example.data.model.MemoryCategory
import com.example.data.model.OrbStyle
import com.example.ui.theme.MyraCyan
import com.example.ui.theme.MyraViolet
import com.example.ui.viewmodel.MyraViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun SettingsScreen(
    viewModel: MyraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val user = Firebase.auth.currentUser

    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val memoriesState by viewModel.memoriesState.collectAsStateWithLifecycle()

    val selectedOrb = OrbStyle.fromKey(prefs.orbStyle)
    val selectedLang = AssistantLanguage.fromCode(prefs.language)

    var newMemoryText by remember { mutableStateOf("") }
    var newMemoryCategory by remember { mutableStateOf(MemoryCategory.PERSONAL) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 760.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "MYRA Memory & Preferences",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Personalize your orb, multilingual voice & long-term memory",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Memory Vault Section
        Text(
            text = "PERSONAL MEMORY VAULT",
            style = MaterialTheme.typography.labelSmall,
            color = MyraCyan
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                OutlinedTextField(
                    value = newMemoryText,
                    onValueChange = { newMemoryText = it },
                    label = { Text("Teach MYRA something to remember…") },
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memory_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MemoryCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = newMemoryCategory == cat,
                            onClick = { newMemoryCategory = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (newMemoryText.isNotBlank()) {
                            viewModel.addMemory(newMemoryText, newMemoryCategory.key)
                            newMemoryText = ""
                        }
                    },
                    enabled = newMemoryText.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("add_memory_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Memory Note")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (val state = memoriesState) {
            is UiState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            is UiState.Error -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "No memories saved yet. MYRA injects these into every conversation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.data.forEach { note ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = note.category.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MyraViolet
                                    )
                                    Text(
                                        text = note.content,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteMemory(note.id) }) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Delete memory",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Voice Orb Style Selector
        Text(
            text = "VOICE ORB STYLE",
            style = MaterialTheme.typography.labelSmall,
            color = MyraCyan
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OrbStyle.entries.forEach { style ->
                val isSelected = selectedOrb == style
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) MyraCyan.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MyraCyan else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { viewModel.updatePreferences(orbStyle = style) }
                        .padding(14.dp)
                        .testTag("orb_style_${style.key}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(style.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            style.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelected) {
                        Text(
                            text = "ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MyraCyan
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Language & Speech Settings
        Text(
            text = "LANGUAGE & VOICE",
            style = MaterialTheme.typography.labelSmall,
            color = MyraCyan
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistantLanguage.entries.forEach { lang ->
                FilterChip(
                    selected = selectedLang == lang,
                    onClick = { viewModel.updatePreferences(language = lang) },
                    label = { Text(lang.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Speak Replies", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Speak MYRA's responses aloud using Text-to-Speech",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.autoSpeak,
                        onCheckedChange = { viewModel.updatePreferences(autoSpeak = it) }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Haptic Feedback", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Subtle vibration when activating the Voice Orb",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.hapticsEnabled,
                        onCheckedChange = { viewModel.updatePreferences(hapticsEnabled = it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Account Sign Out Card
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user?.displayName ?: "Signed in with Google",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = user?.email ?: user?.uid.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = {
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope
                        )
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sign Out")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
