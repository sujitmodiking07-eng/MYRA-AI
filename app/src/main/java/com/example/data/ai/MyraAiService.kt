package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AssistantLanguage
import com.example.data.model.AutomationWorkflow
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryNote
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

data class ParsedQuickAction(
    val label: String,
    val type: String,
    val query: String
)

data class StreamCompletion(
    val cleanText: String,
    val action: ParsedQuickAction?
)

class MyraAiService {

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME
        )
    }

    suspend fun streamChatReply(
        history: List<ChatMessage>,
        userPrompt: String,
        memories: List<MemoryNote>,
        language: AssistantLanguage,
        onPartialText: (String) -> Unit
    ): StreamCompletion {
        val systemPreamble = buildSystemPreamble(language, memories)
        val chatHistory = mutableListOf<Content>()
        chatHistory.add(content("user") { text(systemPreamble) })
        chatHistory.add(
            content("model") {
                text("Understood. I am MYRA AI, ready to assist clearly, warmly, and accurately.")
            }
        )

        // Include recent turns for contextual continuity
        history.takeLast(12).forEach { msg ->
            if (msg.content.isNotBlank()) {
                val role = if (msg.role == "model") "model" else "user"
                chatHistory.add(content(role) { text(msg.content) })
            }
        }

        val fullBuffer = StringBuilder()
        try {
            withTimeout(TIMEOUT_MS) {
                val chat = model.startChat(history = chatHistory)
                chat.sendMessageStream(userPrompt).collect { chunk ->
                    val delta = chunk.text.orEmpty()
                    if (delta.isNotEmpty()) {
                        fullBuffer.append(delta)
                        onPartialText(stripActionTagForDisplay(fullBuffer.toString()))
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Chat generation timed out", e)
            throw IllegalStateException("MYRA timed out after 60 seconds. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Chat generation failed", e)
            throw e
        }

        val raw = fullBuffer.toString().trim()
        if (raw.isEmpty()) {
            throw IllegalStateException("MYRA returned an empty response. Please try again.")
        }
        return extractActionAndCleanText(raw)
    }

    suspend fun streamVisionAnalysis(
        bitmap: Bitmap,
        prompt: String,
        language: AssistantLanguage,
        onPartialText: (String) -> Unit
    ): String {
        val fullPrompt = buildString {
            append("You are MYRA AI Visual Lens, an expert multimodal assistant. ")
            append(language.instructionHint)
            append("\nUser request: ")
            append(prompt.ifBlank { "Analyze this image in detail, extract any visible text, and highlight key insights." })
        }

        val buffer = StringBuilder()
        try {
            withTimeout(TIMEOUT_MS) {
                val inputContent = content {
                    image(bitmap)
                    text(fullPrompt)
                }
                model.generateContentStream(inputContent).collect { chunk ->
                    val delta = chunk.text.orEmpty()
                    if (delta.isNotEmpty()) {
                        buffer.append(delta)
                        onPartialText(buffer.toString())
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Vision analysis timed out", e)
            throw IllegalStateException("Vision analysis timed out after 60 seconds.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Vision analysis failed", e)
            throw e
        }

        val result = buffer.toString().trim()
        if (result.isEmpty()) {
            throw IllegalStateException("MYRA Visual Lens returned an empty response.")
        }
        return result
    }

    suspend fun streamWorkflowExecution(
        workflow: AutomationWorkflow,
        language: AssistantLanguage,
        memories: List<MemoryNote>,
        onPartialText: (String) -> Unit
    ): String {
        val memoryContext = if (memories.isEmpty()) {
            ""
        } else {
            "\nUser context memories:\n" + memories.take(8).joinToString("\n") { "- ${it.content}" }
        }
        val prompt = """
            You are MYRA AI executing the automation workflow "${workflow.title}" (${workflow.category}).
            ${language.instructionHint}$memoryContext

            Task instructions:
            ${workflow.promptTemplate}

            Provide a crisp, structured, immediately actionable result.
        """.trimIndent()

        val buffer = StringBuilder()
        try {
            withTimeout(TIMEOUT_MS) {
                model.generateContentStream(prompt).collect { chunk ->
                    val delta = chunk.text.orEmpty()
                    if (delta.isNotEmpty()) {
                        buffer.append(delta)
                        onPartialText(buffer.toString())
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Workflow execution timed out", e)
            throw IllegalStateException("Workflow timed out after 60 seconds.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Workflow execution failed", e)
            throw e
        }

        val result = buffer.toString().trim()
        if (result.isEmpty()) {
            throw IllegalStateException("Workflow returned an empty response.")
        }
        return result
    }

    private fun buildSystemPreamble(
        language: AssistantLanguage,
        memories: List<MemoryNote>
    ): String {
        val memorySection = if (memories.isEmpty()) {
            "No saved personal memory notes yet."
        } else {
            memories.take(12).joinToString("\n") { "- [${it.category}] ${it.content}" }
        }

        return """
            You are MYRA, a fast, intelligent, voice-first AI assistant designed for Android.
            Tone: helpful, crisp, conversational, and reliable. Avoid overly long filler paragraphs unless asked for deep explanations.
            Language rule: ${language.instructionHint}

            User's saved personal memory notes:
            $memorySection

            Android Quick Actions:
            ONLY if the user explicitly asks to perform one of these device actions—set a timer/alarm, search the web, open a website URL, compose an email, dial a phone number, or open device settings—append ONE tag on its own final line in this exact format:
            [[ACTION:type|Button Label|payload]]
            Allowed types: timer (payload in seconds, e.g. 300), alarm (payload label), search (payload search query), url (payload https://...), email (payload email or subject), dial (payload digits), settings (payload wifi/bluetooth/general).
            Do NOT emit an ACTION tag for normal informational questions.
        """.trimIndent()
    }

    private fun stripActionTagForDisplay(raw: String): String {
        val idx = raw.indexOf("[[ACTION:")
        return if (idx >= 0) raw.substring(0, idx).trimEnd() else raw
    }

    private fun extractActionAndCleanText(raw: String): StreamCompletion {
        val regex = Regex("""\[\[ACTION:([a-zA-Z]+)\|([^|\]]+)\|([^\]]*)\]\]""")
        val match = regex.find(raw)
        val cleanText = raw.replace(regex, "").trim()
        val action = if (match != null) {
            ParsedQuickAction(
                type = match.groupValues[1].trim().lowercase(),
                label = match.groupValues[2].trim(),
                query = match.groupValues[3].trim()
            )
        } else {
            null
        }
        return StreamCompletion(cleanText = cleanText.ifEmpty { raw }, action = action)
    }

    private companion object {
        const val TAG = "MyraAiService"
        const val MODEL_NAME = "gemini-3.8-flash"
        const val TIMEOUT_MS = 60_000L
    }
}
