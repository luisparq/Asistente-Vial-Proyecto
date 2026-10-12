package com.tecsup.asistente.conduccion.data.devices

import com.tecsup.asistente.conduccion.data.network.*
import com.tecsup.asistente.conduccion.data.sync.RetryPolicy
import com.tecsup.asistente.conduccion.domain.sync.SessionStore
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Latest technical snapshot, independent of the immutable trip/event outbox in Room. */
@Singleton class DeviceReportSync @Inject constructor(private val store: DeviceReportStore,
    private val sessions: SessionStore, private val api: LaravelApi, private val clock: Clock) {
    private val lock = Mutex()
    suspend fun capture(expectedOwner: String, address: String, report: DeviceReport): Boolean = lock.withLock {
        val session = sessions.state.value ?: return@withLock false
        if (session.ownerKey != expectedOwner || session.token == null) return@withLock false
        val previous = store.read(session.ownerKey)
        val now = clock.millis()
        if (!DeviceReportPolicy.captureDue(previous,now)) return@withLock false
        val same = previous?.address == address
        val id = if (same) previous!!.deviceId else UUID.randomUUID().toString()
        store.write(session.ownerKey, DeviceReportEntry(address,id,now,report.copy(
            last_contact_at = report.last_contact_at ?: previous?.report?.last_contact_at?.takeIf { same }),
            nextAttemptAt=previous?.nextAttemptAt ?: 0, attempts=previous?.attempts ?: 0,
            blocked=previous?.blocked, tokenHash=previous?.tokenHash))
        true
    }
    /** True only for a transient pending retry; permanent failures stay blocked. */
    suspend fun flush(): Boolean = lock.withLock {
        val session = sessions.state.value ?: return@withLock false
        val token = session.token ?: return@withLock false
        val entry = store.read(session.ownerKey) ?: return@withLock false
        if (!DeviceReportPolicy.eligible(entry,clock.millis(),token)) return@withLock entry.pending && entry.blocked == null
        val reply = try { api.request(session.baseUrl,"api/v1/devices/${entry.deviceId}/status",token,Contract.json.encodeToString(entry.report)) }
            catch (_: IOException) { HttpReply(0,"") }
        val accepted = reply.code in 200..299 && runCatching {
            val data = Contract.json.parseToJsonElement(reply.body).jsonObject.getValue("data").jsonObject
            data.getValue("client_device_id").jsonPrimitive.content == entry.deviceId &&
                !Instant.parse(data.getValue("observed_at").jsonPrimitive.content).isBefore(Instant.parse(entry.report.observed_at))
        }.getOrDefault(false)
        val state = if (reply.code in 200..299) "BLOCKED_VALIDATION" else RetryPolicy.state(reply.code)
        store.write(session.ownerKey, if (accepted) entry.copy(pending=false,attempts=0,nextAttemptAt=0,blocked=null)
            else entry.copy(attempts=entry.attempts+1, tokenHash=tokenHash(token),
                nextAttemptAt=if(state == "RETRY_WAIT") RetryPolicy.next(clock.millis(),entry.attempts,reply.retryAfter) else 0,
                blocked=state.takeUnless { it == "RETRY_WAIT" }))
        if (reply.code == 401) sessions.gate.withLock {
            if (sessions.state.value == session) sessions.save(session.copy(token=null))
        }
        !accepted && state == "RETRY_WAIT"
    }
}
