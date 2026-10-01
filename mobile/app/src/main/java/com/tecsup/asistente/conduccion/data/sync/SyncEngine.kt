package com.tecsup.asistente.conduccion.data.sync

import androidx.room.withTransaction
import com.tecsup.asistente.conduccion.data.local.*
import com.tecsup.asistente.conduccion.data.network.*
import com.tecsup.asistente.conduccion.domain.sync.SessionStore
import java.io.IOException
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

data class ManualSyncResult(val acknowledged: Int, val pending: Int, val blocked: Int, val sessionExpired: Boolean)

@Singleton
class SyncEngine @Inject constructor(
    private val db: AsistenteVialDatabase, private val session: SessionStore,
    private val api: LaravelApi, private val clock: Clock,
) {
    private val manualGate = Mutex()

    suspend fun syncNow(): ManualSyncResult {
        check(manualGate.tryLock()) { "Ya hay una sincronización manual en curso." }
        try {
            val current = checkNotNull(session.state.value) { "Inicia sesión para sincronizar." }
            check(current.token != null) { "Renueva la sesión para sincronizar." }
            val before = db.syncDao().statuses(current.ownerKey).first().sumOf { if (it.state == "ACKED") it.count else 0 }
            // Advance only once per explicit action, not on each iteration/retry.
            db.syncDao().advanceNetworkRetries(current.ownerKey)
            drain()
            check(session.state.value?.ownerKey == current.ownerKey) { "La cuenta cambió. Consulta la cola de la sesión actual." }
            val after = db.syncDao().statuses(current.ownerKey).first()
            return ManualSyncResult(
                acknowledged = (after.sumOf { if (it.state == "ACKED") it.count else 0 } - before).coerceAtLeast(0),
                pending = after.sumOf { if (it.state in listOf("PENDING", "RETRY_WAIT", "IN_FLIGHT")) it.count else 0 },
                blocked = after.sumOf { if (it.state.startsWith("BLOCKED_")) it.count else 0 },
                sessionExpired = session.state.value?.token == null,
            )
        } finally { manualGate.unlock() }
    }

    // No network lock is held by local trip writes. Leases coordinate concurrent workers.
    suspend fun drain(): Boolean {
        repeat(50) {
            val current = session.state.value ?: return false
            val token = current.token ?: return false
            val lease = UUID.randomUUID().toString()
            val operation = db.withTransaction {
                db.syncDao().recover(clock.millis())
                db.syncDao().next(current.ownerKey, clock.millis())?.takeIf {
                    db.syncDao().claim(it.operationId, lease, clock.millis() + 120_000) == 1
                }
            } ?: return db.syncDao().pending(current.ownerKey) > 0
            if (session.state.value != current) {
                db.syncDao().complete(operation.operationId, lease, "PENDING", null, 0)
                return false
            }
            try {
                val reply = api.request(current.baseUrl, operation.path, token, operation.payload)
                if (reply.code == 200 || reply.code == 201) {
                    val valid = try { validateReceipt(operation, reply.body) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { false }
                    db.syncDao().complete(operation.operationId, lease, if (valid) "ACKED" else "BLOCKED_CONFLICT", if (valid) null else "invalid_receipt", 0)
                } else {
                    db.syncDao().complete(operation.operationId, lease, RetryPolicy.state(reply.code), "HTTP_${reply.code}", RetryPolicy.next(clock.millis(), operation.attempts, reply.retryAfter))
                    if (reply.code == 401) {
                        session.gate.lock()
                        try { if (session.state.value == current) session.save(current.copy(token = null)) } finally { session.gate.unlock() }
                        return false
                    }
                }
            } catch (cancelled: CancellationException) {
                // An unacknowledged request is retried with the same key after lease expiry.
                throw cancelled
            } catch (_: IOException) {
                db.syncDao().complete(operation.operationId, lease, "RETRY_WAIT", "network_unavailable", RetryPolicy.next(clock.millis(), operation.attempts, null))
                return true
            }
        }
        return true
    }

    private suspend fun validateReceipt(op: OutboxEntity, body: String): Boolean {
        val meta = checkNotNull(db.syncDao().trip(op.localTripId))
        if (op.eventUuid != null) {
            val sent = Contract.json.decodeFromString<EventDto>(op.payload)
            val receipt = Contract.json.decodeFromString<Receipt<EventReceipt>>(body)
            return receipt.sync.disposition in listOf("created", "already_applied") && receipt.data == EventReceipt(op.eventUuid, meta.clientTripId, sent.type, sent.occurred_at, sent.origin)
        }
        val sent = Contract.json.decodeFromString<TripDto>(op.payload)
        val receipt = Contract.json.decodeFromString<Receipt<TripReceipt>>(body)
        val data = receipt.data
        if (data.client_trip_id != meta.clientTripId || data.started_at != sent.started_at || receipt.sync.acknowledged_revision != op.revision || receipt.sync.current_revision != data.client_revision) return false
        if (receipt.sync.disposition == "stale_ignored") {
            val local = checkNotNull(db.tripDao().findTrip(op.localTripId))
            return op.revision == 1 && data.client_revision == 2 && meta.revision == 2 && data.status == "COMPLETED" && data.ended_at == local.endedAt?.let(Contract::timestamp)
        }
        return receipt.sync.disposition in listOf("created", "updated", "already_applied") && data.client_revision == op.revision && data.status == sent.status && data.ended_at == sent.ended_at
    }
}
