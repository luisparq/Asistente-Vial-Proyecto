package com.tecsup.asistente.conduccion.core.di

import com.tecsup.asistente.conduccion.data.ble.AndroidBlePlatform
import com.tecsup.asistente.conduccion.data.ble.DefaultBleRepository
import com.tecsup.asistente.conduccion.data.ble.GattBleConnection
import com.tecsup.asistente.conduccion.data.ble.RawSensorDataSource
import com.tecsup.asistente.conduccion.data.ble.GattTransport
import com.tecsup.asistente.conduccion.data.ble.AndroidGattTransport
import com.tecsup.asistente.conduccion.domain.ble.BleConnection
import com.tecsup.asistente.conduccion.domain.ble.BlePlatform
import com.tecsup.asistente.conduccion.domain.ble.BleRepository
import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BleScope

@Module
@InstallIn(SingletonComponent::class)
abstract class BleModule {
    @Binds @Singleton abstract fun deviceReports(value: com.tecsup.asistente.conduccion.data.devices.SecureDeviceReportStore): com.tecsup.asistente.conduccion.data.devices.DeviceReportStore
    @Binds @Singleton abstract fun remembered(value: com.tecsup.asistente.conduccion.data.ble.SecureBleDeviceStore): com.tecsup.asistente.conduccion.domain.ble.RememberedBleStore
    @Binds @Singleton abstract fun platform(value: AndroidBlePlatform): BlePlatform
    @Binds @Singleton abstract fun repository(value: DefaultBleRepository): BleRepository
    @Binds @Singleton abstract fun connection(value: GattBleConnection): BleConnection
    @Binds @Singleton abstract fun sensorData(value: RawSensorDataSource): SensorDataSource
    @Binds @Singleton abstract fun transport(value: AndroidGattTransport): GattTransport

    companion object {
        @Provides @Singleton @BleScope
        fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
