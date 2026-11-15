package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.MemoryCategory
import com.example.data.model.OrbStyle
import com.example.data.model.UserPreferences
import com.example.data.model.WorkflowCategory
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MyraRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun preferences_createAndObserve_succeedsForOwner() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = MyraRepository(firestore)

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.savePreferences(
                UserPreferences(
                    userId = uid,
                    orbStyle = OrbStyle.NEON.key,
                    language = "hinglish",
                    autoSpeak = true,
                    hapticsEnabled = true
                )
            )
        }
        assertTrue("Expected savePreferences to succeed", saveResult.isSuccess)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observePreferences(uid).first { it.orbStyle == OrbStyle.NEON.key }
        }
        assertEquals("hinglish", observed.language)
        assertTrue(observed.autoSpeak)
    }

    @Test
    fun sessionAndMessages_createObserveDelete_succeedsForOwner() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = MyraRepository(firestore)

        val sessionId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createSessionWithFirstMessage(
                title = "Voice Briefing",
                language = "en",
                firstMessageContent = "Hey MYRA, summarize my day."
            ).getOrThrow()
        }
        assertTrue(sessionId.isNotBlank())

        val replyId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addMessageToSession(
                sessionId = sessionId,
                role = "model",
                content = "Good evening! Here is your summary.",
                actionLabel = "Search News",
                actionType = "search",
                actionQuery = "top tech news today"
            ).getOrThrow()
        }
        assertTrue(replyId.isNotBlank())

        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMessages(uid, sessionId).first { it.size >= 2 }
        }
        assertEquals(2, messages.size)
        assertEquals("Search News", messages.last().actionLabel)

        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.deleteSession(sessionId)
        }
        assertTrue(deleteResult.isSuccess)
    }

    @Test
    fun memoryAndAutomation_createAndObserve_succeedsForOwner() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = MyraRepository(firestore)

        val memId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addMemory("Prefer concise Kotlin examples", MemoryCategory.WORK.key).getOrThrow()
        }
        val autoId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createAutomation(
                title = "Hinglish Digest",
                triggerPhrase = "hinglish digest",
                promptTemplate = "Summarize today's key tech updates in friendly Hinglish.",
                category = WorkflowCategory.BRIEFING.key
            ).getOrThrow()
        }

        val memories = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMemories(uid).first { list -> list.any { it.id == memId } }
        }
        assertTrue(memories.any { it.id == memId })

        val automations = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeAutomations(uid).first { list -> list.any { it.id == autoId } }
        }
        assertTrue(automations.any { it.id == autoId })
    }

    @Test
    fun crossUserAccess_observeOtherUsersMemories_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = MyraRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addMemory("Alice secret memory", MemoryCategory.PERSONAL.key).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = MyraRepository(firestore)
        val failure = try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeMemories(aliceUid).first()
            }
            null
        } catch (e: Exception) {
            e
        }

        val firestoreEx = failure?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun unauthenticatedAccess_observeSessions_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repo = MyraRepository(firestore)

        val failure = try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeSessions(aliceUid).first()
            }
            null
        } catch (e: Exception) {
            e
        }

        val firestoreEx = failure?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_myra@test.com"
        const val BOB_EMAIL = "bob_myra@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
