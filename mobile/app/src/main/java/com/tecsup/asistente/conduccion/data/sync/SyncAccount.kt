package com.tecsup.asistente.conduccion.data.sync

import androidx.room.withTransaction
import com.tecsup.asistente.conduccion.data.local.*
import com.tecsup.asistente.conduccion.data.network.*
import com.tecsup.asistente.conduccion.domain.sync.*
import javax.inject.Inject
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString

class SyncAccount @Inject constructor(private val db: AsistenteVialDatabase, private val session: SessionStore, private val api: LaravelApi, private val scheduler: SyncScheduler) {
    suspend fun login(baseInput: String, email: String, password: String) {
        val base = api.normalizeBase(baseInput)
        val response = api.request(base, "api/v1/auth/token", payload = Contract.json.encodeToString(LoginDto(email.trim(), password)), method = "POST")
        check(response.code == 201) { "No se pudo iniciar sesión (HTTP ${response.code})." }
        val token = Contract.json.decodeFromString<Envelope<TokenDto>>(response.body).data
        check(token.token_type == "Bearer")
        val meReply = api.request(base, "api/v1/me", token.access_token, method = "GET")
        check(meReply.code == 200) { "No se pudo verificar la identidad." }
        val me = Contract.json.decodeFromString<Envelope<MeDto>>(meReply.body).data
        check("trips:write" in me.capabilities) { "La cuenta no permite sincronizar viajes." }
        val next = Session(base, me.company.id, me.user.id, me.company.name, me.user.email, token.access_token)
        session.gate.withLock {
            val active = db.tripDao().findActive()
            val owner = active?.let { db.syncDao().trip(it.id)?.ownerKey }
            check(owner == null || owner == next.ownerKey) { "Finaliza el viaje con su cuenta original antes de cambiar de cuenta o servidor." }
            session.save(next)
            db.syncDao().resumeAuth(next.ownerKey)
        }
        scheduler.schedule()
    }
    suspend fun logout() {
        val previous = session.gate.withLock {
            val active = db.tripDao().findActive()
            check(active == null) { "Finaliza el recorrido antes de cerrar sesión." }
            session.state.value.also { session.save(null) }
        }
        if (previous?.token != null) runCatching { api.request(previous.baseUrl, "api/v1/auth/token", previous.token, method = "DELETE") }
    }
    suspend fun adopt(id: Long) {
        session.gate.withLock {
            val current = checkNotNull(session.state.value) { "Inicia sesión primero." }
            check(current.token != null) { "Renueva la sesión antes de asignar el historial." }
            db.withTransaction {
                val trip = checkNotNull(db.tripDao().findTrip(id))
                check(trip.status == "COMPLETED") { "Solo se pueden asignar recorridos finalizados." }
                val meta = checkNotNull(db.syncDao().trip(id))
                check(meta.ownerKey == null) { "El recorrido ya tiene propietario." }
                check(db.syncDao().adopt(id, current.ownerKey) == 1)
                OutboxWriter.trip(db, trip, meta.copy(ownerKey = current.ownerKey, revision = 2))
                db.tripDao().events(id).forEach { event ->
                    OutboxWriter.event(db, event, meta.clientTripId, checkNotNull(db.syncDao().event(event.id)).clientEventId)
                }
            }
        }
        scheduler.schedule()
    }
}
