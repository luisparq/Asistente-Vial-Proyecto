package com.tecsup.asistente.conduccion

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.data.local.AsistenteVialDatabase
import com.tecsup.asistente.conduccion.data.network.LaravelApi
import com.tecsup.asistente.conduccion.data.repository.RoomTripRepository
import com.tecsup.asistente.conduccion.data.sync.SyncAccount
import com.tecsup.asistente.conduccion.data.sync.SyncEngine
import com.tecsup.asistente.conduccion.domain.sync.MemorySessionStore
import com.tecsup.asistente.conduccion.domain.sync.Session
import com.tecsup.asistente.conduccion.domain.sync.SyncScheduler
import com.tecsup.asistente.conduccion.feature.settings.SettingsScreen
import com.tecsup.asistente.conduccion.feature.settings.SyncViewModel
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Room, sesión y HTTP aislados; nunca usa la base ni la sesión reales del usuario. */
class LogoutFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AsistenteVialDatabase
    private lateinit var server: MockWebServer
    private lateinit var session: MemorySessionStore
    private lateinit var trips: RoomTripRepository
    private lateinit var viewModel: SyncViewModel
    private var returnsToLogin = 0
    private val scheduler = SyncScheduler { }

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AsistenteVialDatabase::class.java).build()
        server = MockWebServer().apply { enqueue(MockResponse().setResponseCode(204)); start() }
        session = MemorySessionStore().apply {
            save(Session(server.url("/").toString(), 1, 1, "TEST", "test@example.invalid", "TEST-token"))
        }
        val clock = Clock.systemUTC()
        trips = RoomTripRepository(db, clock, session, scheduler)
        val api = LaravelApi()
        viewModel = SyncViewModel(session, db, SyncAccount(db, session, api, scheduler), scheduler, SyncEngine(db, session, api, clock))
    }

    @After fun cleanup() {
        server.shutdown()
        db.close()
    }

    private fun showSettingsAndLogout() {
        compose.setContent { MaterialTheme { SettingsScreen({}, { returnsToLogin++ }, viewModel) } }
        compose.onNodeWithText("Cerrar sesión").performClick()
    }

    @Test fun logoutClearsSessionRevokesTokenAndReturnsToLogin() = runBlocking {
        trips.finishTrip(trips.startOrResumeTrip().id)
        showSettingsAndLogout()
        compose.waitUntil(10_000) { returnsToLogin > 0 }
        assertNull(session.state.value)
        assertEquals(1, returnsToLogin)
        val revoke = server.takeRequest(10, TimeUnit.SECONDS)
        assertEquals("DELETE", revoke?.method)
        assertEquals("/api/v1/auth/token", revoke?.path)
    }

    @Test fun activeTripKeepsSessionAndShowsTheReasonNextToTheButton() = runBlocking {
        trips.startOrResumeTrip()
        showSettingsAndLogout()
        compose.onNodeWithText("Finaliza el recorrido antes de cerrar sesión.").assertIsDisplayed()
        assertNotNull(session.state.value)
        assertEquals(0, returnsToLogin)
        assertEquals(0, server.requestCount)
    }
}
