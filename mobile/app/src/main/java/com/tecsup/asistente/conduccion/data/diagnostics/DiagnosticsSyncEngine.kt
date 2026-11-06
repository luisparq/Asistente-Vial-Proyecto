package com.tecsup.asistente.conduccion.data.diagnostics

import com.tecsup.asistente.conduccion.domain.sync.SessionStore
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticsSyncEngine @Inject constructor(private val dao: DiagnosticsUploadDao,
    private val sessions: SessionStore, private val api: DiagnosticsApi) {

    /** Devuelve true únicamente para errores transitorios que WorkManager debe reintentar. */
    suspend fun flush(): Boolean {
        val account = sessions.state.value ?: return false
        repeat(20) {
            val local = dao.nextSession(account.ownerKey) ?: return false
            val batch = dao.samples(local.clientSessionId, 200)
            if (batch.isNotEmpty()) {
                val payload = DiagnosticsBatchPayload(local.deviceCode,
                    Instant.ofEpochMilli(local.startedAtMillis).toString(), batch.map {
                        DiagnosticsSamplePayload(it.sequenceNumber, Instant.ofEpochMilli(it.sampledAtMillis).toString(),
                            it.axRaw, it.ayRaw, it.azRaw, it.gxRaw, it.gyRaw, it.gzRaw)
                    })
                val reply = try { api.samples(account, local.clientSessionId, payload) }
                    catch (_: Exception) { return true }
                when {
                    reply.code in 200..299 -> dao.deleteThrough(local.clientSessionId, batch.last().sequenceNumber)
                    reply.code == 401 || reply.code == 403 -> return false
                    reply.code == 429 || reply.code >= 500 -> return true
                    else -> return false
                }
            } else if (local.state == "COMPLETING") {
                val reply = try { api.complete(account, local.clientSessionId) }
                    catch (_: Exception) { return true }
                when {
                    reply.code in 200..299 || reply.code == 404 -> dao.deleteSession(local.clientSessionId)
                    reply.code == 401 || reply.code == 403 -> return false
                    reply.code == 429 || reply.code >= 500 -> return true
                    else -> return false
                }
            } else return false
        }
        return dao.nextSession(account.ownerKey) != null
    }
}
