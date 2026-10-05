package com.tecsup.asistente.conduccion

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.data.local.AsistenteVialDatabase
import com.tecsup.asistente.conduccion.data.network.LaravelApi
import com.tecsup.asistente.conduccion.data.repository.RoomTripRepository
import com.tecsup.asistente.conduccion.data.sync.SyncAccount
import com.tecsup.asistente.conduccion.domain.ble.*
import com.tecsup.asistente.conduccion.domain.profile.*
import com.tecsup.asistente.conduccion.domain.sync.*
import com.tecsup.asistente.conduccion.feature.login.*
import java.time.Clock
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*

/** Isolated Room, preferences, TEST identities and mocked HTTP; no production data or Workers. */
class AccessFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AsistenteVialDatabase
    private lateinit var server: MockWebServer
    private lateinit var sessions: MemorySessionStore
    private lateinit var repository: RoomTripRepository
    private lateinit var model: AccessViewModel
    private lateinit var configuration: ServerConfiguration
    private val models = ViewModelStore()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AsistenteVialDatabase::class.java).build()
        server = MockWebServer().apply { start() }
        sessions = MemorySessionStore()
        val scheduler = SyncScheduler {}
        repository = RoomTripRepository(db, Clock.systemUTC(), sessions, scheduler)
        val api = LaravelApi()
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("TEST-access-$name", mode)
        }
        isolated.getSharedPreferences("server_configuration", Context.MODE_PRIVATE).edit().clear().commit()
        configuration = ServerConfiguration(isolated, api, sessions).apply { save(server.url("/").toString()) }
        val profile = object : DriverProfileRepository {
            override fun observe(): Flow<ProfileState> = flowOf(ProfileState.SignedOut)
            override fun refresh() {}
        }
        val ble = object : BleRepository {
            override val scan = MutableStateFlow(BleScanState())
            override val connection = MutableStateFlow(BleConnectionState())
            override fun refresh() {}
            override fun startScan() {}
            override fun stopScan() {}
            override fun selectDevice(address: String) {}
            override fun prepareConnection() {}
            override fun clearSelection() {}
        }
        model = AccessViewModel(SyncAccount(db, sessions, api, scheduler), sessions, repository, profile, ble, db, configuration)
        models.put("TEST", model)
    }

    @After fun cleanup() {
        compose.runOnIdle { models.clear() }
        server.shutdown()
        db.close()
    }

    private fun enqueueLogin() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"data":{"access_token":"TEST-token","token_type":"Bearer","expires_at":"2030-01-01T00:00:00Z"}}"""))
        server.enqueue(MockResponse().setBody("""{"data":{"user":{"id":1,"name":"TEST conductor","email":"test@example.invalid"},"company":{"id":1,"name":"TEST company"},"capabilities":["trips:write"]}}"""))
    }

    @Test fun loginReusesTokenAndIdentityFlowAndIgnoresRepeatedTaps() {
        enqueueLogin()
        compose.runOnIdle { repeat(3) { model.login("test@example.invalid", "TEST password 123") } }
        compose.waitUntil(10000) { !model.state.value.busy }
        assertEquals("welcome", model.state.value.destination)
        assertEquals("TEST company", sessions.state.value?.companyName)
        assertEquals(2, server.requestCount)
        assertEquals("/api/v1/auth/token", server.takeRequest().path)
        assertEquals("/api/v1/me", server.takeRequest().path)
    }

    @Test fun activeDemoMustFinishBeforeLoginAndFinishedDemoIsNotAdopted() = runBlocking {
        val demo = repository.startOrResumeTrip()
        compose.runOnIdle { model.login("test@example.invalid", "TEST password 123") }
        compose.waitUntil(5000) { !model.state.value.busy }
        assertNull(sessions.state.value)
        assertTrue(model.state.value.message!!.contains("demostración"))
        assertEquals(0, server.requestCount)
        repository.finishTrip(demo.id)
        enqueueLogin()
        compose.runOnIdle { model.login("test@example.invalid", "TEST password 123") }
        compose.waitUntil(10000) { !model.state.value.busy }
        assertEquals("welcome", model.state.value.destination)
        assertNull(db.syncDao().trip(demo.id)!!.ownerKey)
    }

    @Test fun demoDoesNotInheritRealSessionOrChangeItsRecords() = runBlocking {
        val real = Session(server.url("/").toString(), 1, 1, "TEST company", "test@example.invalid", "TEST-token")
        sessions.save(real)
        val trip = repository.startOrResumeTrip()
        compose.runOnIdle { model.demo() }
        compose.waitUntil(5000) { !model.state.value.busy }
        assertEquals(real, sessions.state.value)
        assertEquals(0, server.requestCount)
        repository.finishTrip(trip.id)
        server.enqueue(MockResponse().setResponseCode(204))
        compose.runOnIdle { model.demo() }
        compose.waitUntil(10000) { !model.state.value.busy }
        assertNull(sessions.state.value)
        assertEquals("home", model.state.value.destination)
        assertEquals(real.ownerKey, db.syncDao().trip(trip.id)!!.ownerKey)
        val demo = repository.startOrResumeTrip()
        assertNull(db.syncDao().trip(demo.id)!!.ownerKey)
        assertEquals(listOf(demo.id), repository.observeHistory().first().map { it.trip.id })
    }
}
