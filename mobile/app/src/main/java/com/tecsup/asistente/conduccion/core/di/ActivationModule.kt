package com.tecsup.asistente.conduccion.core.di

import com.tecsup.asistente.conduccion.data.network.LaravelAccountActivation
import com.tecsup.asistente.conduccion.domain.access.AccountActivation
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ActivationModule {
    @Binds abstract fun activation(implementation: LaravelAccountActivation): AccountActivation
}
