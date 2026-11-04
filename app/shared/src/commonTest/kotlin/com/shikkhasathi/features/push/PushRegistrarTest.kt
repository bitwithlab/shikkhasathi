package com.shikkhasathi.features.push

import com.shikkhasathi.core.datastore.InMemoryKeyValueStore
import com.shikkhasathi.core.model.ClientPlatform
import com.shikkhasathi.core.testing.sampleMe
import com.shikkhasathi.features.auth.FakeAuthRepository
import com.shikkhasathi.features.auth.data.SessionState
import com.shikkhasathi.features.notifications.data.PushRegistrar
import com.shikkhasathi.features.testing.FakeNotificationsRepository
import com.shikkhasathi.features.testing.networkError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PushRegistrarTest {
    private val token = "fcm-token-AAAAAAAAAAAAAAAAAAAA"

    private fun TestScope.setup(
        prefs: InMemoryKeyValueStore = InMemoryKeyValueStore(),
    ): Triple<PushRegistrar, FakeNotificationsRepository, FakeAuthRepository> {
        val repo = FakeNotificationsRepository(emptyMap())
        val auth = FakeAuthRepository()
        val registrar = PushRegistrar(repo, auth, prefs, ClientPlatform.Android, backgroundScope, retryDelaysMs = listOf(1_000, 5_000))
        return Triple(registrar, repo, auth)
    }

    @Test
    fun registersOncePerAccountAndToken() =
        runTest {
            val prefs = InMemoryKeyValueStore()
            val (registrar, repo, auth) = setup(prefs)
            registrar.onNewToken(token)
            runCurrent()
            assertTrue(repo.pushCalls.isEmpty(), "signed out → wait")

            auth.sessionFlow.value = SessionState.SignedIn(sampleMe())
            runCurrent()
            assertEquals(listOf("register:Android:$token"), repo.pushCalls)

            // App restart with the same account and token: nothing to send.
            val (_, repo2, auth2) = setup(prefs)
            auth2.sessionFlow.value = SessionState.SignedIn(sampleMe())
            runCurrent()
            assertTrue(repo2.pushCalls.isEmpty())

            registrar.onNewToken("fcm-token-BBBBBBBBBBBBBBBBBBBB")
            runCurrent()
            assertEquals("register:Android:fcm-token-BBBBBBBBBBBBBBBBBBBB", repo.pushCalls.last())
            registrar.onNewToken("short")
            runCurrent()
            assertEquals(2, repo.pushCalls.size, "invalid token ignored")
        }

    @Test
    fun retriesWithBackoffThenUnregistersBeforeSignOut() =
        runTest {
            val (registrar, repo, auth) = setup()
            repo.pushError = networkError
            auth.sessionFlow.value = SessionState.SignedIn(sampleMe())
            registrar.onNewToken(token)
            runCurrent()
            assertEquals(1, repo.pushCalls.size)
            advanceTimeBy(1_001)
            assertEquals(2, repo.pushCalls.size)
            repo.pushError = null
            advanceTimeBy(5_001)
            assertEquals(3, repo.pushCalls.size)

            registrar.unregister()
            assertEquals("unregister:$token", repo.pushCalls.last())
            registrar.unregister()
            assertEquals(4, repo.pushCalls.size, "already unregistered")
        }
}
