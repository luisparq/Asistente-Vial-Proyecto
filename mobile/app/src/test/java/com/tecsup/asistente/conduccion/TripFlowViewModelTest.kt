package com.tecsup.asistente.conduccion

import androidx.lifecycle.ViewModelStore
import com.tecsup.asistente.conduccion.core.ui.LoadState
import com.tecsup.asistente.conduccion.domain.model.DataOrigin
import com.tecsup.asistente.conduccion.domain.model.DrivingEvent
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import com.tecsup.asistente.conduccion.domain.model.Trip
import com.tecsup.asistente.conduccion.domain.model.TripStatus
import com.tecsup.asistente.conduccion.domain.model.TripSummary
import com.tecsup.asistente.conduccion.domain.model.TripWithEvents
import com.tecsup.asistente.conduccion.domain.repository.StartedTrip
import com.tecsup.asistente.conduccion.domain.repository.TripRepository
import com.tecsup.asistente.conduccion.feature.home.HomeViewModel
import com.tecsup.asistente.conduccion.feature.trip.TripViewModel
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripFlowViewModelTest {
    @Test
    fun startOnlyMarksNewTripWhenItWasCreated() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val repository = FakeTripRepository()
            val viewModel = HomeViewModel(repository)
            store.put("home", viewModel)

            viewModel.startOrResume()
            assertTrue(viewModel.action.value.openTrip)
            assertTrue(viewModel.action.value.startedNewTrip)

            viewModel.navigationHandled()
            viewModel.startOrResume()
            assertTrue(viewModel.action.value.openTrip)
            assertFalse(viewModel.action.value.startedNewTrip)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun activeTripShowsOnlyNonSimulatedEventsButKeepsThemStored() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val repository = FakeTripRepository()
            val trip = repository.startOrResume().trip
            repository.storeEvent(trip.id, DataOrigin.SIMULATED, DrivingEventType.HARSH_BRAKING)
            repository.storeEvent(trip.id, DataOrigin.SENSOR, DrivingEventType.SHARP_TURN)

            val viewModel = TripViewModel(repository)
            store.put("trip", viewModel)
            val state = viewModel.trip.first { it is LoadState.Ready }
            val ready = state as LoadState.Ready<TripWithEvents?>
            val visible = ready.value!!.events

            assertEquals(listOf(DataOrigin.SENSOR), visible.map { it.origin })
            assertEquals(2, repository.observeTrip(trip.id).first()!!.events.size)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    private class FakeTripRepository : TripRepository {
        override suspend fun simulateManeuver(tripId: Long, type: DrivingEventType): String? =
            error("No se simulan maniobras en esta prueba")
        private val active = MutableStateFlow<Trip?>(null)
        private val events = MutableStateFlow<List<DrivingEvent>>(emptyList())
        private var nextTripId = 1L
        private var nextEventId = 1L

        override fun observeActiveTrip(): Flow<Trip?> = active

        override fun observeHistory(): Flow<List<TripSummary>> = flowOf(emptyList())

        override fun observeTrip(id: Long): Flow<TripWithEvents?> =
            combine(active, events) { trip, stored ->
                trip?.takeIf { it.id == id }?.let { current ->
                    TripWithEvents(current, stored.filter { it.tripId == id }.sortedBy { it.id })
                }
            }

        override suspend fun startOrResume(): StartedTrip {
            active.value?.let { return StartedTrip(it, created = false) }
            val trip = Trip(nextTripId++, Instant.ofEpochMilli(1_000), null, TripStatus.ACTIVE)
            active.value = trip
            return StartedTrip(trip, created = true)
        }

        override suspend fun addSimulatedEvent(tripId: Long, type: DrivingEventType): DrivingEvent =
            storeEvent(tripId, DataOrigin.SIMULATED, type)

        fun storeEvent(tripId: Long, origin: DataOrigin, type: DrivingEventType): DrivingEvent {
            val event = DrivingEvent(nextEventId++, tripId, type, Instant.ofEpochMilli(2_000 + nextEventId), origin)
            events.value = events.value + event
            return event
        }

        override suspend fun finishTrip(tripId: Long) {
            active.value = null
        }
    }
}
