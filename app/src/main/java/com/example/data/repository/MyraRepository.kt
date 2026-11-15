package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AutomationWorkflow
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.MemoryNote
import com.example.data.model.UserPreferences
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MyraRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userRoot(uid: String) = db.collection("users").document(uid)

    // --- Preferences ---

    fun observePreferences(userId: String): Flow<UserPreferences> = flow {
        val docRef = userRoot(userId).collection("preferences").document(PREFS_DOC_ID)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserPreferences::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: UserPreferences(userId = userId)
                }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, docRef.path, userId)
                }
        )
    }

    suspend fun savePreferences(prefs: UserPreferences): Result<Unit> {
        return try {
            val uid = requireUserId()
            val docRef = userRoot(uid).collection("preferences").document(PREFS_DOC_ID)
            val payload = mapOf(
                "userId" to uid,
                "orbStyle" to prefs.orbStyle,
                "language" to prefs.language,
                "autoSpeak" to prefs.autoSpeak,
                "hapticsEnabled" to prefs.hapticsEnabled,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val uid = auth.currentUser?.uid ?: "unauthenticated"
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/preferences/$PREFS_DOC_ID")
            Result.failure(e)
        }
    }

    // --- Chat Sessions & Messages ---

    fun observeSessions(userId: String): Flow<List<ChatSession>> = flow {
        val colRef = userRoot(userId).collection("sessions")
        emitAll(
            colRef.orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, colRef.path, userId)
                }
        )
    }

    fun observeMessages(userId: String, sessionId: String): Flow<List<ChatMessage>> = flow {
        val colRef = userRoot(userId).collection("sessions").document(sessionId).collection("messages")
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, colRef.path, userId)
                }
        )
    }

    suspend fun createSessionWithFirstMessage(
        title: String,
        language: String,
        firstMessageContent: String
    ): Result<String> {
        return try {
            val uid = requireUserId()
            val sessionsCol = userRoot(uid).collection("sessions")
            val sessionRef = sessionsCol.document()
            val messageRef = sessionRef.collection("messages").document()
            val safeTitle = title.trim().ifEmpty { "MYRA Chat" }.take(120)
            val safePreview = firstMessageContent.trim().take(300)

            val batch = db.batch()
            batch.set(
                sessionRef,
                mapOf(
                    "userId" to uid,
                    "title" to safeTitle,
                    "lastMessagePreview" to safePreview,
                    "language" to language,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                messageRef,
                mapOf(
                    "userId" to uid,
                    "role" to "user",
                    "content" to firstMessageContent.trim().take(20000),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(sessionRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val uid = auth.currentUser?.uid ?: "unauthenticated"
            handleFirestoreError(e, OperationType.CREATE, "users/$uid/sessions")
            Result.failure(e)
        }
    }

    suspend fun addMessageToSession(
        sessionId: String,
        role: String,
        content: String,
        actionLabel: String? = null,
        actionType: String? = null,
        actionQuery: String? = null
    ): Result<String> {
        return try {
            val uid = requireUserId()
            val sessionRef = userRoot(uid).collection("sessions").document(sessionId)
            val msgRef = sessionRef.collection("messages").document()
            val trimmedContent = content.trim().take(20000)

            val msgPayload = mutableMapOf<String, Any>(
                "userId" to uid,
                "role" to role,
                "content" to trimmedContent,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (!actionLabel.isNullOrBlank()) msgPayload["actionLabel"] = actionLabel.take(80)
            if (!actionType.isNullOrBlank()) msgPayload["actionType"] = actionType.take(40)
            if (!actionQuery.isNullOrBlank()) msgPayload["actionQuery"] = actionQuery.take(300)

            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(
                sessionRef,
                mapOf(
                    "lastMessagePreview" to trimmedContent.take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val uid = auth.currentUser?.uid ?: "unauthenticated"
            handleFirestoreError(e, OperationType.CREATE, "users/$uid/sessions/$sessionId/messages")
            Result.failure(e)
        }
    }

    suspend fun deleteSession(sessionId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val sessionRef = userRoot(uid).collection("sessions").document(sessionId)
            val messages = sessionRef.collection("messages").get().await()
            val batch = db.batch()
            for (doc in messages.documents) {
                batch.delete(doc.reference)
            }
            batch.delete(sessionRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val uid = auth.currentUser?.uid ?: "unauthenticated"
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/sessions/$sessionId")
            Result.failure(e)
        }
    }

    // --- Memories ---

    fun observeMemories(userId: String): Flow<List<MemoryNote>> = flow {
        val colRef = userRoot(userId).collection("memories")
        emitAll(
            colRef.orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MemoryNote::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, colRef.path, userId)
                }
        )
    }

    suspend fun addMemory(content: String, category: String): Result<String> {
        return try {
            val uid = requireUserId()
            val docRef = userRoot(uid).collection("memories").document()
            val payload = mapOf(
                "userId" to uid,
                "content" to content.trim().take(500),
                "category" to category,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val uid = auth.currentUser?.uid ?: "unauthenticated"
            handleFirestoreError(e, OperationType.CREATE, "users/$uid/memories")
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val docRef = userRoot(uid).collection("memories").document(memoryId)
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val uid = auth.currentUser?.uid ?: "unauthenticated"
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/memories/$memoryId")
            Result.failure(e)
        }
    }

    // --- Automation Workflows ---

    fun observeAutomations(userId: String): Flow<List<AutomationWorkflow>> = flow {
        val colRef = userRoot(userId).collection("automations")
        emitAll(
            colRef.orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AutomationWorkflow::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, colRef.path, userId)
                }
        )
    }

    suspend fun createAutomation(
        title: String,
        triggerPhrase: String,
        promptTemplate: String,
        category: String
    ): Result<String> {
        return try {
            val uid = requireUserId()
            val docRef = userRoot(uid).collection("automations").document()
            val payload = mapOf(
                "userId" to uid,
                "title" to title.trim().take(80),
                "triggerPhrase" to triggerPhrase.trim().take(80),
                "promptTemplate" to promptTemplate.trim().take(2000),
                "category" to category,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val uid = auth.currentUser?.uid ?: "unauthenticated"
            handleFirestoreError(e, OperationType.CREATE, "users/$uid/automations")
            Result.failure(e)
        }
    }

    suspend fun deleteAutomation(automationId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val docRef = userRoot(uid).collection("automations").document(automationId)
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val uid = auth.currentUser?.uid ?: "unauthenticated"
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/automations/$automationId")
            Result.failure(e)
        }
    }

    private fun handleFlowError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    companion object {
        const val PREFS_DOC_ID = "config"
    }
}
