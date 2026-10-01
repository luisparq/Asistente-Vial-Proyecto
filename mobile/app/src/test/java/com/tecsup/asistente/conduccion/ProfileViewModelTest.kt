package com.tecsup.asistente.conduccion

import androidx.lifecycle.ViewModelStore
import com.tecsup.asistente.conduccion.domain.profile.DriverProfile
import com.tecsup.asistente.conduccion.domain.profile.DriverProfileRepository
import com.tecsup.asistente.conduccion.domain.profile.ProfileState
import com.tecsup.asistente.conduccion.feature.profile.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @Test fun stateClearsWhenSessionChangesOrScreenStopsAndRefreshIsExplicit() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val source = MutableStateFlow<ProfileState>(ProfileState.Loading)
        var refreshes = 0
        val repository = object : DriverProfileRepository {
            override fun observe() = source
            override fun refresh() { refreshes++ }
        }
        try {
            val model = ProfileViewModel(repository); store.put("profile", model)
            val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
            source.value = ProfileState.Ready(DriverProfile("TEST private", "test@example.test", "ACTIVE", "ACTIVE", null, emptyList(), null))
            runCurrent(); assertTrue(model.state.value is ProfileState.Ready)
            source.value = ProfileState.SignedOut
            runCurrent(); assertEquals(ProfileState.SignedOut, model.state.value)
            model.refresh(); assertEquals(1, refreshes)
            collection.cancel(); runCurrent()
            assertEquals(ProfileState.Loading, model.state.value)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
