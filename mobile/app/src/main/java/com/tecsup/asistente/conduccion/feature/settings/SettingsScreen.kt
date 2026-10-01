package com.tecsup.asistente.conduccion.feature.settings

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.asistente.conduccion.BuildConfig
import java.time.Instant
import com.tecsup.asistente.conduccion.core.ui.formatTimestamp

@Composable
fun SettingsScreen(onBack: () -> Unit, onReturnToLogin: () -> Unit, viewModel: SyncViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val unassigned by viewModel.unassigned.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf(session?.baseUrl ?: BuildConfig.API_BASE_URL) }
    var email by remember { mutableStateOf(session?.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var adoption by remember { mutableStateOf<Long?>(null) }
    ScreenLayout(stringResource(R.string.settings_title), stringResource(R.string.settings_description), onBack) {
        Text("Servidor y sincronización", style = MaterialTheme.typography.titleLarge)
        Text("Los recorridos funcionan sin Internet. Solo se envían eventos SIMULATED de prueba; no son detecciones físicas.")
        session?.let { Text("Cuenta: ${it.email}\nEmpresa: ${it.companyName}\nServidor: ${it.baseUrl}") }
        if (session?.token == null) Text("Sin sesión de envío válida. Los registros permanecen en este dispositivo.")
        OutlinedTextField(url, { url = it }, label = { Text("URL base del servidor") }, singleLine = true, enabled = !busy)
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, enabled = !busy)
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy)
        Button(onClick = { viewModel.login(url, email, password); password = "" }, enabled = !busy && password.isNotBlank()) { Text("Iniciar / renovar sesión") }
        if (session != null) {
            OutlinedButton(onClick = viewModel::logout, enabled = !busy) { Text("Cerrar sesión") }
            Button(onClick = viewModel::sync, enabled = !busy && session?.token != null) { Text(if (syncing) "Sincronizando…" else "Sincronizar") }
            statuses.forEach { Text("${it.state}: ${it.count} operaciones") }
            Text("ACKED: recibido. RETRY_WAIT: reintento diferido. BLOCKED_AUTH: renovar sesión/permisos. BLOCKED_CONFLICT o BLOCKED_VALIDATION: requiere revisión; no se descartan datos.")
        }
        message?.let { Text(it) }
        if (busy) CircularProgressIndicator()
        if (unassigned.isNotEmpty()) {
            Text("Historial sin propietario: no se enviará automáticamente.")
            unassigned.forEach { trip ->
                OutlinedButton(onClick = { adoption = trip.id }, enabled = !busy && session?.token != null) {
                    Text("Asignar recorrido ${trip.id} · ${formatTimestamp(Instant.ofEpochMilli(trip.startedAt))}")
                }
            }
        }
        OutlinedButton(onClick = onReturnToLogin) { Text(stringResource(R.string.return_access)) }
    }
    adoption?.let { id ->
        AlertDialog(onDismissRequest = { adoption = null }, title = { Text("Asignar recorrido $id") },
            text = { Text("Se enviará este recorrido de prueba y sus eventos a ${session?.companyName}, como ${session?.email}, en ${session?.baseUrl}. La asignación no cambia quién conducía ni certifica datos reales.") },
            confirmButton = { TextButton(onClick = { adoption = null; viewModel.adopt(id) }) { Text("Confirmar asignación") } },
            dismissButton = { TextButton(onClick = { adoption = null }) { Text("Cancelar") } })
    }
}
