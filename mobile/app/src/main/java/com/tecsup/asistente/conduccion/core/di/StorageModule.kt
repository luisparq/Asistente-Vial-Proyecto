package com.tecsup.asistente.conduccion.core.di

import android.content.Context
import androidx.room.Room
import com.tecsup.asistente.conduccion.data.local.AsistenteVialDatabase
import com.tecsup.asistente.conduccion.data.repository.RoomTripRepository
import com.tecsup.asistente.conduccion.domain.repository.TripRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AsistenteVialDatabase =
        Room.databaseBuilder(context, AsistenteVialDatabase::class.java, AsistenteVialDatabase.NAME)
            .build()

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun tripRepository(implementation: RoomTripRepository): TripRepository
}
