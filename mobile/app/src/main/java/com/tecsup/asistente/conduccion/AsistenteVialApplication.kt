package com.tecsup.asistente.conduccion

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.tecsup.asistente.conduccion.data.sync.WorkSyncScheduler
import javax.inject.Inject

@HiltAndroidApp
class AsistenteVialApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scheduler: WorkSyncScheduler
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
    override fun onCreate() {
        super.onCreate()
        scheduler.reconcile()
    }
}
