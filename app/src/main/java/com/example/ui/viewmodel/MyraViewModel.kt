package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.MyraAiService
import com.example.data.model.AssistantLanguage
import com.example.data.model.AutomationWorkflow
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.MemoryNote
import com.example.data.model.OrbStyle
import com.example.data.model.UserPreferences
import com.example.data.repository.MyraRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface StreamUiState {
    data object Idle : StreamUiState
    data object Thinking : StreamUiState
    data class Streaming(val text: String) : StreamUiState
    data class Completed(val text: String) : StreamUiState
    data class Error(val message: String) : StreamUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class MyraViewModel(
    private val repository: MyraRepository,
    private val aiService: MyraAiService,
    private val currentUserId: String
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = repository.observePreferences(currentUserId)
        .catch { error ->
            val root = unwrapFirestoreOrSelf(error)
            if (root == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing preferences", root ?: error)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UserPreferences(userId = currentUserId)
        )

    val sessionsState: StateFlow<UiState<List<ChatSession>>> = repository.observeSessions(currentUserId)
        .map<List<ChatSession>, UiState<List<ChatSession>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreOrSelf(error)
            if (root == null && error is CancellationException) throw error
            val displayError = root ?: error
            Log.w(TAG, "Error observing sessions", displayError)
            emit(UiState.Error(displayError.message ?: "Failed to load chat sessions"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val memoriesState: StateFlow<UiState<List<MemoryNote>>> = repository.observeMemories(currentUserId)
        .map<List<MemoryNote>, UiState<List<MemoryNote>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreOrSelf(error)
            if (root == null && error is CancellationException) throw error
            val displayError = root ?: error
            Log.w(TAG, "Error observing memories", displayError)
            emit(UiState.Error(displayError.message ?: "Failed to load memories"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val automationsState: StateFlow<UiState<List<AutomationWorkflow>>> = repository.observeAutomations(currentUserId)
        .map<List<AutomationWorkflow>, UiState<List<AutomationWorkflow>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreOrSelf(error)
            if (root == null && error is CancellationException) throw error
            val displayError = root ?: error
            Log.w(TAG, "Error observing automations", displayError)
            emit(UiState.Error(displayError.message ?: "Failed to load automation workflows"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    val messagesState: StateFlow<List<ChatMessage>> = _activeSessionId
        .flatMapLatest { sessionId ->
            if (sessionId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeMessages(currentUserId, sessionId)
                    .catch { error ->
                        val root = unwrapFirestoreOrSelf(error)
                        if (root == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing messages", root ?: error)
                        emit(emptyList())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    private val _chatStreamState = MutableStateFlow<StreamUiState>(StreamUiState.Idle)
    val chatStreamState: StateFlow<StreamUiState> = _chatStreamState.asStateFlow()

    private val _visionStreamState = MutableStateFlow<StreamUiState>(StreamUiState.Idle)
    val visionStreamState: StateFlow<StreamUiState> = _visionStreamState.asStateFlow()

    private val _workflowStreamState = MutableStateFlow<StreamUiState>(StreamUiState.Idle)
    val workflowStreamState: StateFlow<StreamUiState> = _workflowStreamState.asStateFlow()

    private val _activeWorkflowTitle = MutableStateFlow<String?>(null)
    val activeWorkflowTitle: StateFlow<String?> = _activeWorkflowTitle.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    fun startNewSession() {
        _activeSessionId.value = null
        _chatStreamState.value = StreamUiState.Idle
    }

    fun selectSession(sessionId: String) {
        _activeSessionId.value = sessionId
        _chatStreamState.value = StreamUiState.Idle
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            val res = repository.deleteSession(sessionId)
            if (res.isSuccess && _activeSessionId.value == sessionId) {
                _activeSessionId.value = null
            } else if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to delete session"
            }
        }
    }

    fun sendChatPrompt(
        rawPrompt: String,
        onReplyCompleted: (String) -> Unit = {}
    ) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty()) return
        if (_chatStreamState.value is StreamUiState.Thinking ||
            _chatStreamState.value is StreamUiState.Streaming
        ) return

        viewModelScope.launch {
            _chatStreamState.value = StreamUiState.Thinking
            val currentPrefs = preferences.value
            val language = AssistantLanguage.fromCode(currentPrefs.language)
            val memories = (memoriesState.value as? UiState.Success)?.data.orEmpty()
            val priorMessages = messagesState.value

            try {
                val existingSessionId = _activeSessionId.value
                val sessionId = if (existingSessionId == null) {
                    val title = prompt.take(42).let { if (prompt.length > 42) "$it…" else it }
                    val createdId = repository.createSessionWithFirstMessage(
                        title = title,
                        language = language.code,
                        firstMessageContent = prompt
                    ).getOrThrow()
                    _activeSessionId.value = createdId
                    createdId
                } else {
                    repository.addMessageToSession(
                        sessionId = existingSessionId,
                        role = "user",
                        content = prompt
                    ).getOrThrow()
                    existingSessionId
                }

                val completion = aiService.streamChatReply(
                    history = priorMessages,
                    userPrompt = prompt,
                    memories = memories,
                    language = language,
                    onPartialText = { partial ->
                        _chatStreamState.value = StreamUiState.Streaming(partial)
                    }
                )

                repository.addMessageToSession(
                    sessionId = sessionId,
                    role = "model",
                    content = completion.cleanText,
                    actionLabel = completion.action?.label,
                    actionType = completion.action?.type,
                    actionQuery = completion.action?.query
                ).getOrThrow()

                _chatStreamState.value = StreamUiState.Idle
                onReplyCompleted(completion.cleanText)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "sendChatPrompt failed", e)
                _chatStreamState.value = StreamUiState.Error(
                    e.localizedMessage ?: "Unable to reach MYRA AI right now."
                )
            }
        }
    }

    fun analyzeImage(bitmap: Bitmap, prompt: String) {
        viewModelScope.launch {
            _visionStreamState.value = StreamUiState.Thinking
            val language = AssistantLanguage.fromCode(preferences.value.language)
            try {
                val result = aiService.streamVisionAnalysis(
                    bitmap = bitmap,
                    prompt = prompt,
                    language = language,
                    onPartialText = { partial ->
                        _visionStreamState.value = StreamUiState.Streaming(partial)
                    }
                )
                _visionStreamState.value = StreamUiState.Completed(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "analyzeImage failed", e)
                _visionStreamState.value = StreamUiState.Error(
                    e.localizedMessage ?: "Visual analysis failed."
                )
            }
        }
    }

    fun runWorkflow(workflow: AutomationWorkflow) {
        viewModelScope.launch {
            _activeWorkflowTitle.value = workflow.title
            _workflowStreamState.value = StreamUiState.Thinking
            val language = AssistantLanguage.fromCode(preferences.value.language)
            val memories = (memoriesState.value as? UiState.Success)?.data.orEmpty()
            try {
                val result = aiService.streamWorkflowExecution(
                    workflow = workflow,
                    language = language,
                    memories = memories,
                    onPartialText = { partial ->
                        _workflowStreamState.value = StreamUiState.Streaming(partial)
                    }
                )
                _workflowStreamState.value = StreamUiState.Completed(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "runWorkflow failed", e)
                _workflowStreamState.value = StreamUiState.Error(
                    e.localizedMessage ?: "Workflow execution failed."
                )
            }
        }
    }

    fun createWorkflow(
        title: String,
        triggerPhrase: String,
        promptTemplate: String,
        category: String
    ) {
        if (title.isBlank() || triggerPhrase.isBlank() || promptTemplate.isBlank()) return
        viewModelScope.launch {
            val res = repository.createAutomation(title, triggerPhrase, promptTemplate, category)
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Could not save workflow"
            }
        }
    }

    fun deleteWorkflow(workflowId: String) {
        viewModelScope.launch {
            val res = repository.deleteAutomation(workflowId)
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Could not delete workflow"
            }
        }
    }

    fun addMemory(content: String, category: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val res = repository.addMemory(content, category)
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Could not save memory"
            } else {
                _statusBanner.value = "Saved to MYRA Memory"
            }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            val res = repository.deleteMemory(memoryId)
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Could not delete memory"
            }
        }
    }

    fun updatePreferences(
        orbStyle: OrbStyle? = null,
        language: AssistantLanguage? = null,
        autoSpeak: Boolean? = null,
        hapticsEnabled: Boolean? = null
    ) {
        viewModelScope.launch {
            val current = preferences.value
            val updated = current.copy(
                userId = currentUserId,
                orbStyle = orbStyle?.key ?: current.orbStyle,
                language = language?.code ?: current.language,
                autoSpeak = autoSpeak ?: current.autoSpeak,
                hapticsEnabled = hapticsEnabled ?: current.hapticsEnabled
            )
            val res = repository.savePreferences(updated)
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to update preferences"
            }
        }
    }

    fun clearChatError() {
        if (_chatStreamState.value is StreamUiState.Error) {
            _chatStreamState.value = StreamUiState.Idle
        }
    }

    fun clearVisionState() {
        _visionStreamState.value = StreamUiState.Idle
    }

    fun clearWorkflowState() {
        _workflowStreamState.value = StreamUiState.Idle
        _activeWorkflowTitle.value = null
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    private fun unwrapFirestoreOrSelf(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private companion object {
        const val TAG = "MyraViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
