package com.tecsup.asistente.conduccion.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.asistente.conduccion.data.local.AsistenteVialDatabase
import com.tecsup.asistente.conduccion.data.sync.SyncAccount
import com.tecsup.asistente.conduccion.data.sync.SyncEngine
import com.tecsup.asistente.conduccion.domain.sync.SessionStore
import com.tecsup.asistente.conduccion.domain.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SyncViewModel @Inject constructor(store: SessionStore, db: AsistenteVialDatabase, private val account: SyncAccount, private val scheduler: SyncScheduler, private val engine: SyncEngine) : ViewModel() {
    val session = store.state
    val statuses = session.flatMapLatest { db.syncDao().statuses(it?.ownerKey) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val unassigned = db.syncDao().unassigned().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val busy = MutableStateFlow(false)
    val syncing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    private fun perform(isSync: Boolean = false, action: suspend () -> String) {
        // Reserve synchronously, before launching: rapid taps cannot queue extra attempts.
        if (!busy.compareAndSet(false, true)) return
        syncing.value = isSync
        message.value = if (isSync) "Sincronizando…" else null
        viewModelScope.launch {
            try { message.value = action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { message.value = if (e is IllegalStateException || e is IllegalArgumentException) e.message else "No se pudo conectar. Los datos locales se conservan." }
            finally { syncing.value = false; busy.value = false }
        }
    }
    fun login(url: String, email: String, password: String) = perform { account.login(url, email, password); "Operación completada." }
    fun logout() = perform { account.logout(); "Operación completada." }
    fun adopt(id: Long) = perform { account.adopt(id); "Operación completada." }
    fun sync() = perform(isSync = true) {
        var needsBackup = true
        try {
            val result = engine.syncNow()
            needsBackup = result.pending > 0
            "Confirmadas en este intento: ${result.acknowledged}. Pendientes: ${result.pending}. Bloqueadas: ${result.blocked}." +
                (if (result.sessionExpired) " Renueva la sesión en Ajustes." else "") +
                (if (result.pending > 0) " Los datos se conservan; el envío automático seguirá intentando cuando corresponda." else "") +
                (if (result.blocked > 0) " Revisa el motivo de bloqueo en la cola." else "")
        } finally {
            // Also retain a backup if the screen is closed/cancelled during HTTP.
            if (needsBackup) scheduler.scheduleFallback()
        }
    }
}
