package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.AssistantLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceAssistantManager(context: Context) {
    private val appContext = context.applicationContext

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        try {
            tts = TextToSpeech(appContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                        }
                    })
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TTS initialization skipped: ${e.message}")
        }
    }

    fun isSpeechRecognitionSupported(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(appContext)

    fun startListening(
        language: AssistantLanguage,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isSpeechRecognitionSupported()) {
            onError("Speech recognition service is not available on this device. You can type your command below.")
            return
        }

        stopListening()
        stopSpeaking()

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            speechRecognizer = recognizer
            _partialTranscript.value = ""
            _isListening.value = true

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    _rmsLevel.value = normalized
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _isListening.value = false
                    _rmsLevel.value = 0f
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    _rmsLevel.value = 0f
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Tap the orb and speak again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Try again."
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                        else -> "Voice recognition unavailable (code $error). Try typing your prompt."
                    }
                    onError(message)
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    _rmsLevel.value = 0f
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                        .trim()
                    _partialTranscript.value = ""
                    if (text.isNotEmpty()) {
                        onFinalResult(text)
                    } else {
                        onError("No speech recognized. Please try again.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isNotEmpty()) {
                        _partialTranscript.value = text
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            _isListening.value = false
            _rmsLevel.value = 0f
            onError("Could not start voice input: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            speechRecognizer = null
            _isListening.value = false
            _rmsLevel.value = 0f
        }
    }

    fun speak(text: String, language: AssistantLanguage) {
        if (!ttsReady || text.isBlank()) return
        try {
            val locale = Locale.forLanguageTag(language.localeTag)
            tts?.language = locale
            val cleanForSpeech = text
                .replace(Regex("[*_`#>]"), "")
                .take(3500)
            tts?.speak(cleanForSpeech, TextToSpeech.QUEUE_FLUSH, null, "myra_tts_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.w(TAG, "TTS speak failed: ${e.message}")
        }
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        } finally {
            _isSpeaking.value = false
        }
    }

    fun release() {
        stopListening()
        stopSpeaking()
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }

    private companion object {
        const val TAG = "VoiceAssistantManager"
    }
}
