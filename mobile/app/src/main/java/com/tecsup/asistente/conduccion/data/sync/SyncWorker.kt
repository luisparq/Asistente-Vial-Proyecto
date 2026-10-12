package com.tecsup.asistente.conduccion.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.tecsup.asistente.conduccion.domain.sync.SyncScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class SyncWorker @AssistedInject constructor(@Assisted context: Context, @Assisted params: WorkerParameters, private val engine: SyncEngine, private val deviceReports: com.tecsup.asistente.conduccion.data.devices.DeviceReportSync) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val tripsRetry = engine.drain()
        val devicesRetry = deviceReports.flush()
        return if (tripsRetry || devicesRetry) Result.retry() else Result.success()
    }
}

@Singleton
class WorkSyncScheduler @Inject constructor(@ApplicationContext private val context: Context) : SyncScheduler {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    override fun schedule() {
        enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE)
    }
    override fun scheduleFallback() {
        enqueue(ExistingWorkPolicy.KEEP)
    }
    private fun enqueue(policy: ExistingWorkPolicy) {
        // Scheduling failure cannot roll back an already committed local journey.
        // Startup and periodic reconciliation will rediscover its persistent outbox.
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork("trip-sync-now", policy,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        }
    }
    fun reconcile() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("trip-sync-reconcile", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build())
        schedule()
    }
}
