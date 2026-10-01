package com.tecsup.asistente.conduccion.core.di

import com.tecsup.asistente.conduccion.data.network.SecureSessionStore
import com.tecsup.asistente.conduccion.data.sync.WorkSyncScheduler
import com.tecsup.asistente.conduccion.domain.sync.SessionStore
import com.tecsup.asistente.conduccion.domain.sync.SyncScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds @Singleton abstract fun sessions(value: SecureSessionStore): SessionStore
    @Binds @Singleton abstract fun scheduler(value: WorkSyncScheduler): SyncScheduler
}
