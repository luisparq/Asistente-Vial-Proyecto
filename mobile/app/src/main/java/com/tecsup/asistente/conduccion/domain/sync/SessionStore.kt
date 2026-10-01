package com.tecsup.asistente.conduccion.domain.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable

@Serializable
data class Session(val baseUrl: String, val companyId: Long, val userId: Long, val companyName: String, val email: String, val token: String?) {
    val ownerKey: String get() = "$baseUrl#$companyId/$userId"
    override fun toString() = "Session(redacted)"
}
interface SessionStore {
    val state: StateFlow<Session?>
    // Serializes account changes, trip ownership and requests already in flight.
    val gate: Mutex
    fun save(session: Session?)
}
class MemorySessionStore : SessionStore {
    override val state = MutableStateFlow<Session?>(null)
    override val gate = Mutex()
    override fun save(session: Session?) { state.value = session }
}
fun interface SyncScheduler {
    fun schedule()
    // A manual attempt needs a backup, not another dependent worker in the chain.
    fun scheduleFallback() = schedule()
}
