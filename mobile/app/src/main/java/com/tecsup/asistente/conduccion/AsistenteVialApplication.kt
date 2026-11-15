package com.tecsup.asistente.conduccion

import android.app.Application
import com.tecsup.asistente.conduccion.core.di.BleScope
import com.tecsup.asistente.conduccion.data.repository.RoomTripRepository
import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataSource
import dagger.hilt.android.HiltAndroidApp
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.tecsup.asistente.conduccion.data.sync.WorkSyncScheduler
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@HiltAndroidApp
class AsistenteVialApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scheduler: WorkSyncScheduler
    @Inject @BleScope lateinit var bleScope: CoroutineScope
    @Inject lateinit var sensors: SensorDataSource
    @Inject lateinit var trips: RoomTripRepository
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
    override fun onCreate() {
        super.onCreate()
        scheduler.reconcile()
        bleScope.launch { sensors.gps.collect(trips::recordTrackPoint) }
    }
}
