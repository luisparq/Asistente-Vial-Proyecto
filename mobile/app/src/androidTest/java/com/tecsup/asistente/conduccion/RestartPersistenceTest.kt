package com.tecsup.asistente.conduccion

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.data.local.AsistenteVialDatabase
import com.tecsup.asistente.conduccion.data.repository.RoomTripRepository
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import com.tecsup.asistente.conduccion.domain.model.TripStatus
import com.tecsup.asistente.conduccion.domain.model.TripWithEvents
import java.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Ejecutar a, forzar cierre sin borrar datos, b, forzar cierre otra vez y c.
 * El orden de JUnit permite también ejecutar la clase completa, pero eso por sí
 * solo NO prueba muerte de proceso. Ver scripts/verify-process-restart.ps1.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RestartPersistenceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixture get() = context.getSharedPreferences("restart-test-expectation", 0)

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun <T> withRepository(block: (RoomTripRepository) -> T): T {
        val database = Room.databaseBuilder(context, AsistenteVialDatabase::class.java, AsistenteVialDatabase.NAME).build()
        return try { block(RoomTripRepository(database, Clock.systemUTC())) } finally { database.close() }
    }

    @Test
    fun aPrepareActiveTrip(): Unit = withRepository { repository ->
        compose.clickText(text(R.string.explore))
        val active = runBlocking { repository.observeActiveTrip().first() }
        compose.clickText(text(if (active == null) R.string.start_trip else R.string.resume_trip))
        compose.waitForText(text(R.string.trip_active))
        val trip = runBlocking { repository.observeActiveTrip().first()!! }
        val beforeCount = runBlocking { repository.observeTrip(trip.id).first()!!.events.size }
        val button = text(R.string.simulate_event, text(R.string.event_braking))
        compose.onNodeWithTag("trip_list").performScrollToNode(hasText(button))
        compose.clickText(button)
        compose.waitForText(text(R.string.event_count, beforeCount + 1))
        // Esperar confirmación de Room; no se toma el estado del ViewModel como evidencia.
        val snapshot = runBlocking { repository.observeTrip(trip.id).first()!! }
        assertEquals(DrivingEventType.HARSH_BRAKING, snapshot.events.last().type)
        assertTrue(fixture.edit().putLong("tripId", trip.id).putString("active", snapshotJson(snapshot)).commit())
    }

    @Test
    fun bVerifyActiveAndFinish(): Unit = withRepository { repository ->
        val id = fixture.getLong("tripId", -1)
        assertTrue("Primero ejecutar aPrepareActiveTrip", id > 0)
        val persisted = runBlocking { repository.observeTrip(id).first()!! }
        assertEquals(fixture.getString("active", null), snapshotJson(persisted))
        assertEquals(TripStatus.ACTIVE, persisted.trip.status)
        compose.clickText(text(R.string.explore))
        compose.clickText(text(R.string.resume_trip))
        compose.waitForText(text(R.string.trip_active))
        val count = text(R.string.event_count, persisted.events.size)
        compose.waitForText(count)
        compose.onNodeWithTag("trip_list").performScrollToNode(hasText(text(R.string.finish_trip)))
        compose.clickText(text(R.string.finish_trip))
        compose.waitForText(text(R.string.history_title))
        val completed = runBlocking { repository.observeTrip(id).first()!! }
        assertEquals(TripStatus.COMPLETED, completed.trip.status)
        assertNotNull(completed.trip.endedAt)
        assertEquals(persisted.events, completed.events)
        assertTrue(fixture.edit().putString("completed", snapshotJson(completed)).commit())
    }

    @Test
    fun cVerifyCompletedHistory(): Unit = withRepository { repository ->
        val id = fixture.getLong("tripId", -1)
        assertTrue("Primero ejecutar a y b", id > 0)
        val saved = runBlocking { repository.observeTrip(id).first()!! }
        assertEquals(fixture.getString("completed", null), snapshotJson(saved))
        assertNull(runBlocking { repository.observeActiveTrip().first() })
        compose.clickText(text(R.string.explore))
        compose.waitForText(text(R.string.start_trip))
        compose.clickText(text(R.string.history_title))
        val title = text(R.string.trip_number, id)
        compose.waitForText(title)
        compose.clickText(title)
        compose.waitForText(text(R.string.trip_detail_title))
        val count = text(R.string.event_count, saved.events.size)
        compose.onNodeWithTag("events_list").performScrollToNode(hasText(count))
        compose.waitForText(count)
        compose.onNodeWithTag("events_list").performScrollToNode(hasText(text(R.string.origin_simulated)))
    }

    private fun snapshotJson(value: TripWithEvents): String = JSONObject()
        .put("id", value.trip.id)
        .put("startedAt", value.trip.startedAt.toEpochMilli())
        .put("endedAt", value.trip.endedAt?.toEpochMilli() ?: JSONObject.NULL)
        .put("status", value.trip.status.name)
        .put("events", JSONArray().apply {
            value.events.forEach { event ->
                put(JSONObject().put("id", event.id).put("tripId", event.tripId)
                    .put("type", event.type.name).put("at", event.occurredAt.toEpochMilli())
                    .put("origin", event.origin.name))
            }
        }).toString()
}
