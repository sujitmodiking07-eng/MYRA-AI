package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AssistantLanguage
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.MemoryCategory
import com.example.data.model.OrbStyle
import com.example.ui.components.MyraVoiceOrb
import com.example.ui.components.OrbVisualState
import com.example.ui.theme.MyraCyan
import com.example.ui.theme.MyraEmerald
import com.example.ui.theme.MyraViolet
import com.example.ui.viewmodel.MyraViewModel
import com.example.ui.viewmodel.StreamUiState
import com.example.ui.viewmodel.UiState
import com.example.util.DeviceActionExecutor
import com.example.util.VoiceAssistantManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: MyraViewModel,
    voiceManager: VoiceAssistantManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val messages by viewModel.messagesState.collectAsStateWithLifecycle()
    val chatStreamState by viewModel.chatStreamState.collectAsStateWithLifecycle()

    val isListening by voiceManager.isListening.collectAsStateWithLifecycle()
    val isSpeaking by voiceManager.isSpeaking.collectAsStateWithLifecycle()
    val rmsLevel by voiceManager.rmsLevel.collectAsStateWithLifecycle()
    val partialTranscript by voiceManager.partialTranscript.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var voiceFeedback by remember { mutableStateOf<String?>(null) }
    var showSessionsSheet by remember { mutableStateOf(false) }
    var showLangMenu by remember { mutableStateOf(false) }

    val orbStyle = OrbStyle.fromKey(prefs.orbStyle)
    val language = AssistantLanguage.fromCode(prefs.language)
    val listState = rememberLazyListState()

    val triggerSend: (String) -> Unit = { prompt ->
        voiceFeedback = null
        viewModel.sendChatPrompt(prompt) { reply ->
            if (prefs.autoSpeak) {
                voiceManager.speak(reply, language)
            }
        }
    }

    val startVoiceCapture: () -> Unit = {
        if (prefs.hapticsEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        voiceFeedback = null
        voiceManager.startListening(
            language = language,
            onFinalResult = { spokenText ->
                triggerSend(spokenText)
            },
            onError = { err ->
                voiceFeedback = err
            }
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceCapture()
        } else {
            voiceFeedback = context.getString(R.string.mic_permission_needed)
        }
    }

    val handleOrbOrMicTap: () -> Unit = {
        when {
            isListening -> voiceManager.stopListening()
            isSpeaking -> voiceManager.stopSpeaking()
            else -> {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    startVoiceCapture()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    LaunchedEffect(messages.size, chatStreamState) {
        val total = messages.size + if (chatStreamState is StreamUiState.Streaming) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    val orbVisualState = when {
        isListening -> OrbVisualState.LISTENING
        chatStreamState is StreamUiState.Thinking || chatStreamState is StreamUiState.Streaming ->
            OrbVisualState.THINKING
        isSpeaking -> OrbVisualState.SPEAKING
        else -> OrbVisualState.IDLE
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 760.dp)
            .padding(horizontal = 16.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MYRA",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box {
                    FilterChip(
                        selected = true,
                        onClick = { showLangMenu = true },
                        label = {
                            Text(
                                text = language.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Language,
                                contentDescription = "Select language",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("language_chip")
                    )
                    DropdownMenu(
                        expanded = showLangMenu,
                        onDismissRequest = { showLangMenu = false }
                    ) {
                        AssistantLanguage.entries.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang.label) },
                                onClick = {
                                    viewModel.updatePreferences(language = lang)
                                    showLangMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showSessionsSheet = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("sessions_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = "Chat history"
                    )
                }
                IconButton(
                    onClick = { viewModel.startNewSession() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("new_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddComment,
                        contentDescription = "New conversation"
                    )
                }
            }
        }

        // Main Chat or Hero Orb Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && chatStreamState is StreamUiState.Idle) {
                EmptyAssistantHero(
                    orbStyle = orbStyle,
                    orbVisualState = orbVisualState,
                    rmsLevel = rmsLevel,
                    partialTranscript = partialTranscript,
                    onOrbTap = handleOrbOrMicTap,
                    onQuickPrompt = { prompt -> triggerSend(prompt) }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatMessageBubble(
                            message = msg,
                            onSpeak = { voiceManager.speak(msg.content, language) },
                            onSaveMemory = {
                                viewModel.addMemory(msg.content.take(450), MemoryCategory.GENERAL.key)
                            },
                            onExecuteAction = { type, query ->
                                val res = DeviceActionExecutor.executeAction(context, type, query)
                                if (res.isFailure) {
                                    voiceFeedback = res.exceptionOrNull()?.localizedMessage
                                }
                            }
                        )
                    }

                    when (val state = chatStreamState) {
                        is StreamUiState.Thinking -> {
                            item(key = "thinking") {
                                StreamingCard(text = "Thinking…", isLoading = true)
                            }
                        }
                        is StreamUiState.Streaming -> {
                            item(key = "streaming") {
                                StreamingCard(text = state.text, isLoading = false)
                            }
                        }
                        is StreamUiState.Error -> {
                            item(key = "error") {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { viewModel.clearChatError() }) {
                                            Text("Dismiss")
                                        }
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }

        // Active voice dock when conversation is active or listening
        AnimatedVisibility(visible = isListening || isSpeaking || messages.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        RoundedCornerShape(18.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MyraVoiceOrb(
                    orbStyle = orbStyle,
                    visualState = orbVisualState,
                    rmsLevel = rmsLevel,
                    onClick = handleOrbOrMicTap,
                    size = 54.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isListening -> partialTranscript.ifBlank { stringResource(R.string.mic_listening) }
                            isSpeaking -> "MYRA is speaking… tap orb to stop"
                            chatStreamState is StreamUiState.Thinking -> "Processing with Gemini AI…"
                            chatStreamState is StreamUiState.Streaming -> "Streaming reply…"
                            else -> "Voice Orb ready (${orbStyle.displayName})"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isListening) MyraEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isSpeaking) {
                    IconButton(onClick = { voiceManager.stopSpeaking() }) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Stop speaking",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        if (!voiceFeedback.isNullOrBlank()) {
            Text(
                text = voiceFeedback.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        // Bottom Prompt Composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.ask_myra_hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank()) {
                            val text = inputText
                            inputText = ""
                            triggerSend(text)
                        }
                    }
                ),
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input")
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = handleOrbOrMicTap,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isListening) MyraEmerald.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .testTag("mic_button")
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (isListening) "Stop voice input" else "Voice input",
                    tint = if (isListening) MyraEmerald else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        val text = inputText
                        inputText = ""
                        triggerSend(text)
                    }
                },
                enabled = inputText.isNotBlank() &&
                    chatStreamState !is StreamUiState.Thinking &&
                    chatStreamState !is StreamUiState.Streaming,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .testTag("send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send prompt",
                    tint = if (inputText.isNotBlank()) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }

    if (showSessionsSheet) {
        ModalBottomSheet(onDismissRequest = { showSessionsSheet = false }) {
            SessionsSheetContent(
                sessionsState = sessionsState,
                activeSessionId = activeSessionId,
                onSelect = { id ->
                    viewModel.selectSession(id)
                    showSessionsSheet = false
                },
                onDelete = { id -> viewModel.deleteSession(id) },
                onNewChat = {
                    viewModel.startNewSession()
                    showSessionsSheet = false
                }
            )
        }
    }
}

@Composable
private fun EmptyAssistantHero(
    orbStyle: OrbStyle,
    orbVisualState: OrbVisualState,
    rmsLevel: Float,
    partialTranscript: String,
    onOrbTap: () -> Unit,
    onQuickPrompt: (String) -> Unit
) {
    val starterPrompts = remember {
        listOf(
            "Hinglish mein aaj ka smart productivity plan banao",
            "Set a 5 minute focus timer",
            "Explain Kotlin StateFlow vs SharedFlow simply",
            "Give me a quick global tech & AI briefing"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MyraVoiceOrb(
            orbStyle = orbStyle,
            visualState = orbVisualState,
            rmsLevel = rmsLevel,
            onClick = onOrbTap,
            size = 148.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when (orbVisualState) {
                OrbVisualState.LISTENING -> partialTranscript.ifBlank { "Listening… speak naturally" }
                OrbVisualState.THINKING -> "MYRA is thinking…"
                OrbVisualState.SPEAKING -> "Speaking… tap orb to pause"
                OrbVisualState.IDLE -> "Tap the ${orbStyle.displayName} Orb to talk"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Multilingual Voice • Android Actions • Cloud Memory",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            starterPrompts.forEachIndexed { idx, prompt ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                            RoundedCornerShape(16.dp)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onQuickPrompt(prompt) }
                        .testTag("starter_prompt_$idx")
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    onSpeak: () -> Unit,
    onSaveMemory: () -> Unit,
    onExecuteAction: (String, String) -> Unit
) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) {
                MyraViolet.copy(alpha = 0.24f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            modifier = Modifier
                .widthIn(max = 560.dp)
                .border(
                    width = 1.dp,
                    color = if (isUser) MyraViolet.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (isUser) "You" else "MYRA AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) MyraViolet else MyraCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!isUser && !message.actionLabel.isNullOrBlank() && !message.actionType.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    AssistChip(
                        onClick = {
                            onExecuteAction(
                                message.actionType,
                                message.actionQuery.orEmpty()
                            )
                        },
                        label = { Text(message.actionLabel) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MyraCyan.copy(alpha = 0.16f),
                            labelColor = MyraCyan,
                            leadingIconContentColor = MyraCyan
                        )
                    )
                }

                if (!isUser) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onSpeak,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = "Read aloud",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onSaveMemory,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BookmarkAdd,
                                contentDescription = "Save to memory",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingCard(text: String, isLoading: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MyraCyan.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "MYRA AI",
                style = MaterialTheme.typography.labelSmall,
                color = MyraCyan
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MyraCyan
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SessionsSheetContent(
    sessionsState: UiState<List<ChatSession>>,
    activeSessionId: String?,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNewChat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Conversation History",
                style = MaterialTheme.typography.titleLarge
            )
            TextButton(onClick = onNewChat) {
                Text("New Chat")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        when (sessionsState) {
            is UiState.Loading -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is UiState.Error -> {
                Text(
                    text = sessionsState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            is UiState.Success -> {
                if (sessionsState.data.isEmpty()) {
                    Text(
                        text = "No saved conversations yet. Start speaking or typing with MYRA!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(320.dp)
                    ) {
                        items(sessionsState.data, key = { it.id }) { session ->
                            val isSelected = session.id == activeSessionId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSelected) MyraCyan.copy(alpha = 0.14f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { onSelect(session.id) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = session.lastMessagePreview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onDelete(session.id) }) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Delete session",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
