package com.tecsup.asistente.conduccion

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.data.local.AsistenteVialDatabase
import com.tecsup.asistente.conduccion.data.repository.RoomTripRepository
import com.tecsup.asistente.conduccion.domain.model.DataOrigin
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import com.tecsup.asistente.conduccion.domain.model.TripStatus
import java.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TripFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun createPersistFinishAndReadHistoryWithoutShowingSimulatedEvents() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun text(id: Int, vararg args: Any) = context.getString(id, *args)
        val database = Room.databaseBuilder(context, AsistenteVialDatabase::class.java, AsistenteVialDatabase.NAME)
            .addMigrations(com.tecsup.asistente.conduccion.data.local.MIGRATION_1_2, com.tecsup.asistente.conduccion.data.local.MIGRATION_2_3, com.tecsup.asistente.conduccion.data.local.MIGRATION_3_4, com.tecsup.asistente.conduccion.data.local.MIGRATION_4_5).build()
        val repository = RoomTripRepository(database, Clock.systemUTC())
        try {
            compose.openAnonymousTripFixture()
            val existing = runBlocking { repository.observeActiveTrip().first() }
            compose.clickText(text(if (existing == null) R.string.start_trip else R.string.resume_trip))
            compose.waitForText(text(R.string.trip_active))
            val active = runBlocking { repository.observeActiveTrip().first()!! }
            val beforeCount = runBlocking { repository.observeTrip(active.id).first()!!.events.size }
            // Los eventos de prueba se guardan, pero la pantalla activa no debe contarlos ni listarlos.
            DrivingEventType.entries.forEach { type ->
                runBlocking { repository.addSimulatedEvent(active.id, type) }
            }
            runBlocking { repository.observeTrip(active.id).first { it?.events?.size == beforeCount + 3 } }
            compose.waitForText(text(R.string.event_count, 0))
            if (beforeCount == 0) compose.waitForText(text(R.string.active_no_events))
            compose.waitForIdle()
            listOf(R.string.event_braking, R.string.event_acceleration, R.string.event_turn).forEach { label ->
                compose.onAllNodesWithText(text(label)).assertCountEquals(0)
            }
            val events = runBlocking { repository.observeTrip(active.id).first()!!.events }
            assertEquals(beforeCount + 3, events.size)
            assertTrue(events.takeLast(3).all { it.origin == DataOrigin.SIMULATED && it.tripId == active.id })

            compose.activityRule.scenario.recreate()
            compose.waitForText(text(R.string.event_count, 0))
            assertEquals(active.id, runBlocking { repository.observeActiveTrip().first()!!.id })
            compose.clickText(text(R.string.finish_trip))
            compose.clickText("Finalizar")
            compose.waitForText(text(R.string.history_title))
            val title = text(R.string.trip_number, active.id)
            compose.waitForText(title)
            compose.clickText(title)
            compose.waitForText(text(R.string.trip_detail_title))
            val countLabel = text(R.string.event_count, beforeCount + 3)
            compose.onNodeWithTag("events_list").performScrollToNode(hasText(countLabel))
            compose.onNodeWithText(countLabel).assertIsDisplayed()
            compose.onNodeWithTag("events_list").performScrollToNode(hasText(text(R.string.origin_simulated)))
            val saved = runBlocking { repository.observeTrip(active.id).first()!! }
            assertEquals(TripStatus.COMPLETED, saved.trip.status)
            assertNotNull(saved.trip.endedAt)
            assertNull(runBlocking { repository.observeActiveTrip().first() })
            compose.onNodeWithContentDescription(text(R.string.back)).performClick()
            compose.waitForText(text(R.string.history_title))
            compose.onNodeWithContentDescription(text(R.string.back)).performClick()
            compose.waitForText(text(R.string.start_trip))
        } finally {
            database.close()
        }
    }
}
