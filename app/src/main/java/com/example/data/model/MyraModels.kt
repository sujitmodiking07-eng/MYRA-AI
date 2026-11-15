package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class OrbStyle(
    val key: String,
    val displayName: String,
    val subtitle: String
) {
    CLASSIC("classic", "Classic Core", "Calm radial pulse with orbital halo"),
    ENERGY("energy", "Energy Plasma", "Reactive multi-ring aurora waves"),
    NEON("neon", "Neon Matrix", "High-contrast cyber equalizer arcs");

    companion object {
        fun fromKey(key: String?): OrbStyle =
            entries.firstOrNull { it.key == key } ?: ENERGY
    }
}

enum class AssistantLanguage(
    val code: String,
    val label: String,
    val localeTag: String,
    val instructionHint: String
) {
    AUTO(
        "auto",
        "Auto Detect",
        "en-IN",
        "Detect the user's language (English, Hindi, Hinglish, Tamil, Spanish, Japanese, etc.) and reply naturally in the same language."
    ),
    ENGLISH(
        "en",
        "English",
        "en-US",
        "Reply in crisp, clear English."
    ),
    HINDI(
        "hi",
        "हिन्दी (Hindi)",
        "hi-IN",
        "Reply in natural Hindi using Devanagari script."
    ),
    HINGLISH(
        "hinglish",
        "Hinglish",
        "en-IN",
        "Reply in natural conversational Hinglish (Hindi mixed with English in Roman script)."
    ),
    TAMIL(
        "ta",
        "தமிழ் (Tamil)",
        "ta-IN",
        "Reply in natural Tamil script."
    ),
    SPANISH(
        "es",
        "Español",
        "es-ES",
        "Reply in natural Spanish."
    ),
    JAPANESE(
        "ja",
        "日本語",
        "ja-JP",
        "Reply in natural Japanese."
    );

    companion object {
        fun fromCode(code: String?): AssistantLanguage =
            entries.firstOrNull { it.code == code } ?: AUTO
    }
}

enum class MemoryCategory(val key: String, val label: String) {
    PERSONAL("personal", "Personal"),
    PREFERENCE("preference", "Preference"),
    WORK("work", "Work"),
    GENERAL("general", "General");

    companion object {
        fun fromKey(key: String?): MemoryCategory =
            entries.firstOrNull { it.key == key } ?: GENERAL
    }
}

enum class WorkflowCategory(val key: String, val label: String) {
    BRIEFING("briefing", "Briefing"),
    PRODUCTIVITY("productivity", "Productivity"),
    CODING("coding", "Coding"),
    TRANSLATE("translate", "Translate");

    companion object {
        fun fromKey(key: String?): WorkflowCategory =
            entries.firstOrNull { it.key == key } ?: PRODUCTIVITY
    }
}

@IgnoreExtraProperties
data class UserPreferences(
    val userId: String = "",
    val orbStyle: String = OrbStyle.ENERGY.key,
    val language: String = AssistantLanguage.AUTO.code,
    val autoSpeak: Boolean = false,
    val hapticsEnabled: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val lastMessagePreview: String = "",
    val language: String = AssistantLanguage.AUTO.code,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user",
    val content: String = "",
    val actionLabel: String? = null,
    val actionType: String? = null,
    val actionQuery: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MemoryNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val content: String = "",
    val category: String = MemoryCategory.GENERAL.key,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AutomationWorkflow(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val triggerPhrase: String = "",
    val promptTemplate: String = "",
    val category: String = WorkflowCategory.PRODUCTIVITY.key,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
