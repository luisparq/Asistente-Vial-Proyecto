package com.tecsup.asistente.conduccion.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.asistente.conduccion.domain.profile.DriverProfileRepository
import com.tecsup.asistente.conduccion.domain.profile.ProfileState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ProfileViewModel @Inject constructor(private val repository: DriverProfileRepository) : ViewModel() {
    val state = repository.observe().stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), ProfileState.Loading)
    fun refresh() = repository.refresh()
}
