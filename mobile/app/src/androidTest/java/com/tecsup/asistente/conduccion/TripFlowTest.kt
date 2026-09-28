package com.tecsup.asistente.conduccion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.data.local.AsistenteVialDatabase
import com.tecsup.asistente.conduccion.data.repository.RoomTripRepository
import com.tecsup.asistente.conduccion.domain.model.DataOrigin
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
    fun createSimulateRecreateFinishAndReadHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun text(id: Int, vararg args: Any) = context.getString(id, *args)
        val database = Room.databaseBuilder(context, AsistenteVialDatabase::class.java, AsistenteVialDatabase.NAME).build()
        val repository = RoomTripRepository(database, Clock.systemUTC())
        try {
            compose.clickText(text(R.string.explore))
            val existing = runBlocking { repository.observeActiveTrip().first() }
            compose.clickText(text(if (existing == null) R.string.start_trip else R.string.resume_trip))
            compose.waitForText(text(R.string.trip_active))
            val active = runBlocking { repository.observeActiveTrip().first()!! }
            val beforeCount = runBlocking { repository.observeTrip(active.id).first()!!.events.size }
            listOf(R.string.event_braking, R.string.event_acceleration, R.string.event_turn).forEach { label ->
                val button = text(R.string.simulate_event, text(label))
                compose.onNodeWithTag("trip_list").performScrollToNode(hasText(button))
                compose.clickText(button)
            }
            val countLabel = text(R.string.event_count, beforeCount + 3)
            compose.waitForText(countLabel)
            val events = runBlocking { repository.observeTrip(active.id).first()!!.events }
            assertEquals(beforeCount + 3, events.size)
            assertTrue(events.all { it.origin == DataOrigin.SIMULATED && it.tripId == active.id })

            compose.activityRule.scenario.recreate()
            compose.waitForText(countLabel)
            assertEquals(active.id, runBlocking { repository.observeActiveTrip().first()!!.id })
            compose.onNodeWithTag("trip_list").performScrollToNode(hasText(text(R.string.finish_trip)))
            compose.clickText(text(R.string.finish_trip))
            compose.waitForText(text(R.string.history_title))
            val title = text(R.string.trip_number, active.id)
            compose.waitForText(title)
            compose.clickText(title)
            compose.waitForText(text(R.string.trip_detail_title))
            compose.onNodeWithTag("events_list").performScrollToNode(hasText(countLabel))
            compose.onNodeWithText(countLabel).assertIsDisplayed()
            compose.onNodeWithTag("events_list").performScrollToNode(hasText(text(R.string.origin_simulated)))
            val saved = runBlocking { repository.observeTrip(active.id).first()!! }
            assertEquals(TripStatus.COMPLETED, saved.trip.status)
            assertNotNull(saved.trip.endedAt)
            assertNull(runBlocking { repository.observeActiveTrip().first() })
            compose.clickText(text(R.string.back))
            compose.waitForText(text(R.string.history_title))
            compose.clickText(text(R.string.back))
            compose.waitForText(text(R.string.start_trip))
        } finally {
            database.close()
        }
    }
}
