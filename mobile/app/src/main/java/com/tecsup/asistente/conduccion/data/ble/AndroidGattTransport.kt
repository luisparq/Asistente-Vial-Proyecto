package com.tecsup.asistente.conduccion.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.tecsup.asistente.conduccion.core.permissions.BlePermissionPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

/** A single platform GATT per session; all callbacks delivered on Android's main looper. */
class AndroidGattTransport @Inject constructor(@ApplicationContext private val context: Context) : GattTransport {
    @SuppressLint("MissingPermission") // Explicit CONNECT check; revocation handled by connector/close.
    override fun connect(address: String, listener: GattTransport.Listener): GattTransport.Session {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context,
                BlePermissionPolicy.CONNECT) != PackageManager.PERMISSION_GRANTED) throw SecurityException()
        val adapter = checkNotNull(context.getSystemService(BluetoothManager::class.java)?.adapter)
        check(adapter.isEnabled)
        return AndroidSession(listener).apply {
            gatt = checkNotNull(adapter.getRemoteDevice(address).connectGatt(context, false, callback,
                BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK, Handler(Looper.getMainLooper())))
        }
    }

    @SuppressLint("MissingPermission")
    private class AndroidSession(private val listener: GattTransport.Listener) : GattTransport.Session {
        var gatt: BluetoothGatt? = null
        private var closed = false
        private var characteristic: BluetoothGattCharacteristic? = null
        private var gpsCharacteristic: BluetoothGattCharacteristic? = null
        private var gpsSubscribed = false
        private var buzzerCharacteristic: BluetoothGattCharacteristic? = null
        private var buzzerSubscribed = false
        private val handler = Handler(Looper.getMainLooper())
        private val gpsTimeout = Runnable {
            if (!closed && !gpsSubscribed) listener.gpsUnavailable()
        }
        private val serviceUuid = UUID.fromString(TelemetryGattProfile.SERVICE)
        private val characteristicUuid = UUID.fromString(TelemetryGattProfile.IMU_RAW)
        private val cccdUuid = UUID.fromString(TelemetryGattProfile.CCCD)

        private fun current(value: BluetoothGatt) = !closed && value === gatt
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (!current(gatt)) return
                when {
                    status != BluetoothGatt.GATT_SUCCESS -> listener.failed("Error GATT de Android: $status.")
                    newState == BluetoothProfile.STATE_CONNECTED -> listener.connected()
                    newState == BluetoothProfile.STATE_DISCONNECTED -> listener.disconnected()
                }
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (!current(gatt)) return
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    listener.failed("Error al descubrir servicios GATT: $status.")
                    return
                }
                val service = gatt.getService(serviceUuid)
                characteristic = service?.getCharacteristic(characteristicUuid)
                gpsCharacteristic = service?.getCharacteristic(UUID.fromString(TelemetryGattProfile.GPS))
                buzzerCharacteristic = service?.getCharacteristic(UUID.fromString(TelemetryGattProfile.BUZZER))
                val target = characteristic
                listener.services(GattProfileDescription(service != null, target != null,
                    target != null && (target.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0,
                    target?.getDescriptor(cccdUuid) != null))
            }
            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (!current(gatt) || descriptor.uuid != cccdUuid) return
                if (descriptor.characteristic === characteristic) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        listener.subscribed()
                        // One outstanding GATT write: buzzer CCCD before GPS so BUZZER_DONE is not missed.
                        if (!subscribeBuzzer()) subscribeGps()
                    } else listener.failed("Android rechazó la suscripción CCCD: $status.")
                } else if (descriptor.characteristic === buzzerCharacteristic) {
                    buzzerSubscribed = status == BluetoothGatt.GATT_SUCCESS
                    subscribeGps()
                } else if (descriptor.characteristic === gpsCharacteristic) {
                    handler.removeCallbacks(gpsTimeout)
                    gpsSubscribed = status == BluetoothGatt.GATT_SUCCESS
                    if (!gpsSubscribed) listener.gpsUnavailable()
                }
            }
            // Android 13+ provides a memory-safe snapshot of the notification value.
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                if (current(gatt)) {
                    if (characteristic === this@AndroidSession.characteristic) listener.frame(value.copyOf())
                    else if (gpsSubscribed && characteristic === gpsCharacteristic) listener.gpsFrame(value.copyOf())
                    else if (buzzerSubscribed && characteristic === buzzerCharacteristic) {
                        val text = String(value, Charsets.UTF_8).trim()
                        if (text == "BUZZER_DONE" || value.contentEquals(byteArrayOf(0x02))) {
                            listener.buzzerDone()
                        }
                    }
                }
            }
            @Deprecated("Legacy callback for Android 12 and earlier")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (Build.VERSION.SDK_INT < 33 && current(gatt)) {
                    characteristic.value?.let {
                        if (characteristic === this@AndroidSession.characteristic) listener.frame(it.copyOf())
                        else if (gpsSubscribed && characteristic === gpsCharacteristic) listener.gpsFrame(it.copyOf())
                        else if (buzzerSubscribed && characteristic === buzzerCharacteristic) {
                            val text = String(it, Charsets.UTF_8).trim()
                            if (text == "BUZZER_DONE" || it.contentEquals(byteArrayOf(0x02))) {
                                listener.buzzerDone()
                            }
                        }
                    }
                }
            }
        }

        override fun discover(): Boolean = !closed && gatt?.discoverServices() == true

        override fun subscribe(): Boolean = characteristic?.let { enable(it) } ?: false

        private fun subscribeBuzzer(): Boolean {
            val buzzer = buzzerCharacteristic ?: return false
            return try {
                buzzer.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 && enable(buzzer)
            } catch (_: SecurityException) { false }
              catch (_: IllegalStateException) { false }
        }

        private fun subscribeGps() {
            val gps = gpsCharacteristic
            val requested = try {
                gps != null && gps.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 && enable(gps)
            } catch (_: SecurityException) { false }
              catch (_: IllegalStateException) { false }
            if (requested) handler.postDelayed(gpsTimeout, 5000)
            else listener.gpsUnavailable()
        }

        override fun writeBuzzer(command: ByteArray): Boolean {
            val active = gatt ?: return false
            val buzzer = buzzerCharacteristic ?: return false
            if (closed || !buzzerSubscribed) return false
            return try {
                if (Build.VERSION.SDK_INT >= 33) {
                    active.writeCharacteristic(buzzer, command, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothStatusCodes.SUCCESS
                } else {
                    buzzer.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    buzzer.value = command
                    active.writeCharacteristic(buzzer)
                }
            } catch (_: SecurityException) { false }
              catch (_: IllegalStateException) { false }
        }

        @Suppress("DEPRECATION")
        private fun enable(target: BluetoothGattCharacteristic): Boolean {
            val active = gatt ?: return false
            val descriptor = target.getDescriptor(cccdUuid) ?: return false
            if (closed || !active.setCharacteristicNotification(target, true)) return false
            return if (Build.VERSION.SDK_INT >= 33) {
                active.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
            } else {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                active.writeDescriptor(descriptor)
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            handler.removeCallbacks(gpsTimeout)
            gpsSubscribed = false
            gpsCharacteristic = null
            buzzerSubscribed = false
            buzzerCharacteristic = null
            val previous = gatt
            gatt = null
            characteristic = null
            try { previous?.disconnect() } catch (_: SecurityException) { /* permission revoked */ }
            catch (_: IllegalStateException) { /* Bluetooth stopped */ }
            finally {
                try { previous?.close() } catch (_: SecurityException) { /* permission revoked */ }
                catch (_: IllegalStateException) { /* Bluetooth stopped */ }
            }
        }
    }
}
