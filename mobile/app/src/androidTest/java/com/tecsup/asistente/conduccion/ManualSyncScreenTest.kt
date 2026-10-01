package com.tecsup.asistente.conduccion

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.data.local.*
import com.tecsup.asistente.conduccion.data.network.*
import com.tecsup.asistente.conduccion.data.repository.RoomTripRepository
import com.tecsup.asistente.conduccion.data.sync.*
import com.tecsup.asistente.conduccion.domain.sync.*
import com.tecsup.asistente.conduccion.feature.settings.*
import java.time.Clock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*

/** Uses isolated Room/session and mocked HTTP, never the user's database or WorkManager. */
class ManualSyncScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AsistenteVialDatabase
    private lateinit var server: MockWebServer
    private lateinit var vm: SyncViewModel
    private val store = ViewModelStore()
    private val release = CountDownLatch(1)
    private var fallbackCalls = 0
    private var automaticCalls = 0
    private val scheduler = object : SyncScheduler {
        override fun schedule() { automaticCalls++ }
        override fun scheduleFallback() { fallbackCalls++ }
    }

    @Before fun setup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AsistenteVialDatabase::class.java).build()
        server = MockWebServer().apply { start() }
        val session = MemorySessionStore().apply {
            save(Session(server.url("/").toString(), 1, 1, "TEST", "test@example.invalid", "TEST-token"))
        }
        val clock = Clock.systemUTC()
        RoomTripRepository(db, clock, session, scheduler).startOrResumeTrip()
        val api = LaravelApi()
        vm = SyncViewModel(session, db, SyncAccount(db, session, api, scheduler), scheduler, SyncEngine(db, session, api, clock))
        store.put("TEST", vm)
        compose.setContent { MaterialTheme { SettingsScreen({}, {}, vm) } }
    }

    @After fun cleanup() {
        release.countDown()
        compose.runOnIdle { store.clear() }
        server.shutdown()
        db.close()
    }

    private fun clickAndRepeatWhileWaiting(response: MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                release.await(10, TimeUnit.SECONDS)
                return response
            }
        }
        compose.onNodeWithText("Sincronizar").performScrollTo().performClick()
        compose.onNode(hasText("Sincronizando…") and hasClickAction()).assertIsNotEnabled()
        compose.runOnIdle { repeat(10) { vm.sync() }; assertTrue(vm.busy.value) }
        release.countDown()
        compose.waitUntil(10_000) { !vm.busy.value }
        compose.onNodeWithText("Sincronizar").assertIsEnabled()
    }

    @Test fun buttonSendsImmediatelyAndRepeatedTapsDoNotDuplicateRequestsOrWorkers() = runBlocking {
        val operation = db.syncDao().all().single()
        val payload = Contract.json.parseToJsonElement(operation.payload).jsonObject
        val data = buildJsonObject {
            payload.forEach { (k, v) -> put(k, v) }
            put("client_trip_id", db.syncDao().trip(operation.localTripId)!!.clientTripId)
        }
        val receipt = buildJsonObject {
            put("data", data)
            putJsonObject("sync") {
                put("disposition", "created")
                put("acknowledged_revision", 1)
                put("current_revision", 1)
            }
        }
        clickAndRepeatWhileWaiting(MockResponse().setBody(receipt.toString()))
        assertEquals(1, server.requestCount)
        assertEquals("ACKED", db.syncDao().all().single().state)
        assertEquals(0, fallbackCalls)
        assertEquals(1, automaticCalls) // only the original local trip write
        assertTrue(vm.message.value!!.contains("Confirmadas en este intento: 1. Pendientes: 0."))
        compose.runOnIdle { vm.sync() }
        compose.waitUntil(10_000) { !vm.busy.value }
        assertEquals(1, server.requestCount)
        assertEquals(0, fallbackCalls)
    }

    @Test fun unavailableServerRetainsQueueAndRequestsOnlyOneFallbackForRepeatedTaps() = runBlocking {
        clickAndRepeatWhileWaiting(MockResponse().setResponseCode(503).setHeader("Retry-After", "3600"))
        assertEquals(1, server.requestCount)
        assertEquals("RETRY_WAIT", db.syncDao().all().single().state)
        assertEquals(1, fallbackCalls)
        assertEquals(1, automaticCalls)
        assertTrue(vm.message.value!!.contains("Pendientes: 1"))
        compose.runOnIdle { vm.sync() }
        compose.waitUntil(10_000) { !vm.busy.value }
        assertEquals(1, server.requestCount) // manual action must still honor Retry-After
        assertEquals(2, fallbackCalls) // KEEP, never APPEND, on each completed explicit attempt
    }
}
