package com.tecsup.asistente.conduccion.data.ble

import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataSource
import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

class PendingSensorDataSource @Inject constructor() : SensorDataSource {
    override val status = MutableStateFlow(SensorDataStatus.PENDING_HARDWARE).asStateFlow()
    override val frames = emptyFlow<ByteArray>()
}
