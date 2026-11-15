package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.theme.MyraCyan
import com.example.ui.theme.MyraViolet
import com.example.ui.viewmodel.ChatUiMessage
import com.example.ui.viewmodel.ChatUiState
import com.example.ui.viewmodel.ChatViewModel

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val isBusy = uiState is ChatUiState.Loading || uiState is ChatUiState.Streaming

    val submitMessage: () -> Unit = {
        if (inputText.isNotBlank() && !isBusy) {
            val prompt = inputText
            inputText = ""
            viewModel.sendMessage(prompt)
        }
    }

    LaunchedEffect(messages.size, uiState) {
        val totalItems = messages.size + if (uiState is ChatUiState.Loading || uiState is ChatUiState.Streaming) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 760.dp)
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MyraCyan.copy(alpha = 0.16f),
                    modifier = Modifier.border(1.dp, MyraCyan.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MyraCyan,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.chat_screen_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Powered by Firebase Gemini API",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (messages.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.clearConversation() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("chat_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = "Clear chat messages",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Scrollable Message List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && uiState is ChatUiState.Idle) {
                ChatEmptyState(
                    onSuggestionClick = { suggestion ->
                        viewModel.sendMessage(suggestion)
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("chat_message_list")
                ) {
                    items(messages, key = { it.id }) { message ->
                        ChatBubble(message = message)
                    }

                    when (val state = uiState) {
                        is ChatUiState.Loading -> {
                            item(key = "chat_loading") {
                                StreamingGeminiBubble(
                                    text = "Thinking…",
                                    isLoading = true
                                )
                            }
                        }
                        is ChatUiState.Streaming -> {
                            item(key = "chat_streaming") {
                                StreamingGeminiBubble(
                                    text = state.partialText,
                                    isLoading = false
                                )
                            }
                        }
                        is ChatUiState.Error -> {
                            item(key = "chat_error") {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("chat_error_card")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { viewModel.dismissError() }) {
                                            Text("Dismiss")
                                        }
                                    }
                                }
                            }
                        }
                        ChatUiState.Idle -> Unit
                    }
                }
            }
        }

        // Bottom Text Input & Send Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.chat_input_placeholder),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submitMessage() }),
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_message_input")
            )

            Spacer(modifier = Modifier.width(10.dp))

            FilledIconButton(
                onClick = submitMessage,
                enabled = inputText.isNotBlank() && !isBusy,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .size(50.dp)
                    .testTag("chat_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message to Gemini"
                )
            }
        }
    }
}

@Composable
private fun ChatEmptyState(onSuggestionClick: (String) -> Unit) {
    val suggestions = remember {
        listOf(
            "Summarize how Jetpack Compose manages state",
            "Write a short Kotlin coroutine example",
            "Give me 3 productivity habits for developers"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MyraCyan,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Start a conversation with Gemini",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Ask questions, draft text, or brainstorm ideas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            suggestions.forEachIndexed { index, prompt ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onSuggestionClick(prompt) }
                        .testTag("chat_suggestion_$index")
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatUiMessage) {
    val isUser = message.role == "user"
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isUser) 18.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 18.dp
    )

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
            shape = bubbleShape,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .border(
                    width = 1.dp,
                    color = if (isUser) MyraViolet.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline,
                    shape = bubbleShape
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (isUser) "You" else "Gemini",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) MyraViolet else MyraCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun StreamingGeminiBubble(text: String, isLoading: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MyraCyan.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Gemini",
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
