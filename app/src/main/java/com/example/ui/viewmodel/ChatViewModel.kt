package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

data class ChatUiMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String
)

sealed interface ChatUiState {
    data object Idle : ChatUiState
    data object Loading : ChatUiState
    data class Streaming(val partialText: String) : ChatUiState
    data class Error(val message: String) : ChatUiState
}

class ChatViewModel : ViewModel() {

    private val model by lazy {
        Firebase.ai.generativeModel(MODEL_NAME)
    }

    private val _messages = MutableStateFlow<List<ChatUiMessage>>(emptyList())
    val messages: StateFlow<List<ChatUiMessage>> = _messages.asStateFlow()

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun sendMessage(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty()) return
        if (_uiState.value is ChatUiState.Loading || _uiState.value is ChatUiState.Streaming) return

        val historySnapshot = _messages.value
        val userMessage = ChatUiMessage(role = "user", text = prompt)
        _messages.update { it + userMessage }
        _uiState.value = ChatUiState.Loading

        viewModelScope.launch {
            val accumulated = StringBuilder()
            try {
                val chatHistory = historySnapshot.map { msg ->
                    content(role = if (msg.role == "model") "model" else "user") {
                        text(msg.text)
                    }
                }
                val chat = model.startChat(history = chatHistory)

                withTimeout(TIMEOUT_MS) {
                    chat.sendMessageStream(prompt).collect { chunk ->
                        val delta = chunk.text.orEmpty()
                        if (delta.isNotEmpty()) {
                            accumulated.append(delta)
                            _uiState.value = ChatUiState.Streaming(accumulated.toString())
                        }
                    }
                }

                val finalText = accumulated.toString().trim()
                if (finalText.isNotEmpty()) {
                    _messages.update {
                        it + ChatUiMessage(role = "model", text = finalText)
                    }
                    _uiState.value = ChatUiState.Idle
                } else {
                    _uiState.value = ChatUiState.Error("Gemini returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini chat generation timed out", e)
                _uiState.value = ChatUiState.Error(
                    "Request timed out after 60 seconds. Please try again."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase Gemini chat failed", e)
                _uiState.value = ChatUiState.Error(
                    e.localizedMessage ?: "Failed to generate response."
                )
            }
        }
    }

    fun clearConversation() {
        _messages.value = emptyList()
        _uiState.value = ChatUiState.Idle
    }

    fun dismissError() {
        if (_uiState.value is ChatUiState.Error) {
            _uiState.value = ChatUiState.Idle
        }
    }

    private companion object {
        const val TAG = "ChatViewModel"
        const val MODEL_NAME = "gemini-3.8-flash"
        const val TIMEOUT_MS = 60_000L
    }
}
