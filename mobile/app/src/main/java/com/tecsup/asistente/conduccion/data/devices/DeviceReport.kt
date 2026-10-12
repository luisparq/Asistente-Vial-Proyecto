package com.tecsup.asistente.conduccion.data.devices

import com.tecsup.asistente.conduccion.data.network.Contract
import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable data class DeviceReport(val name: String, val observed_at: String, val last_contact_at: String?,
    val ble_state: String, val imu_state: String, val gps_state: String, val technical_error: String?)
@Serializable data class DeviceReportEntry(val address: String, val deviceId: String, val capturedAt: Long,
    val report: DeviceReport, val pending: Boolean = true, val attempts: Int = 0,
    val nextAttemptAt: Long = 0, val blocked: String? = null, val tokenHash: String? = null)
interface DeviceReportStore {
    fun read(owner: String): DeviceReportEntry?
    fun write(owner: String, value: DeviceReportEntry)
}
internal fun tokenHash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }
object DeviceReportPolicy {
    const val INTERVAL_MS = 60_000L
    fun captureDue(previous: DeviceReportEntry?, now: Long) = previous == null || now - previous.capturedAt >= INTERVAL_MS
    fun eligible(entry: DeviceReportEntry, now: Long, token: String): Boolean = entry.pending && now >= entry.nextAttemptAt &&
        (entry.blocked == null || (entry.blocked == "BLOCKED_AUTH" && entry.tokenHash != tokenHash(token)))
    fun contact(now: Long, imu: Long?, gps: Long?, previous: String?): String? = listOfNotNull(imu,gps)
        .filter { it <= now }.maxOrNull()?.let(Contract::timestamp) ?: previous
}
