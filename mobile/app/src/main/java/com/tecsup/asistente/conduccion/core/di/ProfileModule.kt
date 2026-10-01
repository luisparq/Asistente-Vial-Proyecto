package com.tecsup.asistente.conduccion.core.di

import com.tecsup.asistente.conduccion.data.repository.LaravelDriverProfileRepository
import com.tecsup.asistente.conduccion.domain.profile.DriverProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileModule {
    @Binds abstract fun profile(repository: LaravelDriverProfileRepository): DriverProfileRepository
}
