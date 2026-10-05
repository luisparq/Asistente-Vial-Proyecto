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
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tecsup.asistente.conduccion.core.ui.formatTimestamp

@Composable
fun SettingsScreen(onBack: () -> Unit, onReturnToLogin: () -> Unit, viewModel: SyncViewModel = hiltViewModel(), initialBaseUrl: String? = null,
    onSaveServer: ((String) -> Unit)? = null) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val unassigned by viewModel.unassigned.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf(initialBaseUrl ?: session?.baseUrl ?: BuildConfig.API_BASE_URL) }
    var email by remember { mutableStateOf(session?.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var adoption by remember { mutableStateOf<Long?>(null) }
    var configurationMessage by remember { mutableStateOf<String?>(null) }
    ScreenLayout(stringResource(R.string.settings_title), stringResource(R.string.settings_description), onBack) {
        Text("Mi cuenta", style = MaterialTheme.typography.titleLarge)
        session?.let { Text("${it.email}\n${it.companyName}") } ?: Text("Sin cuenta iniciada.")
        if (session != null) OutlinedButton(onClick = viewModel::logout, enabled = !busy) { Text("Cerrar sesión") }
        Card(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Configuración avanzada", style = MaterialTheme.typography.titleLarge)
        Text("Configuración del servidor", style = MaterialTheme.typography.titleMedium)
        Text("Los recorridos funcionan sin Internet. Solo se envían eventos SIMULATED de prueba; no son detecciones físicas.")
        session?.let { Text("Servidor de la sesión actual: ${it.baseUrl}", style = MaterialTheme.typography.bodySmall) }
        if (session?.token == null) Text("Sin sesión de envío válida. Los registros permanecen en este dispositivo.")
        OutlinedTextField(url, { url = it }, label = { Text("URL base del servidor") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        onSaveServer?.let { save ->
            OutlinedButton(onClick = {
                configurationMessage = try { save(url); "Configuración guardada para el inicio de sesión y la activación. La sesión actual conserva su servidor." }
                catch (_: IllegalArgumentException) { "Revisa la dirección del servidor. Fuera del desarrollo local debe utilizar HTTPS." }
            }, enabled = !busy && url.isNotBlank()) { Text("Guardar configuración") }
        }
        configurationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (session != null) {
        Text("Renovar acceso", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, enabled = !busy)
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy)
        Button(onClick = { viewModel.login(url, email, password); password = "" }, enabled = !busy && password.isNotBlank()) { Text("Iniciar / renovar sesión") }
        }
        if (session != null) {
            Text("Sincronización", style = MaterialTheme.typography.titleMedium)
            Button(onClick = viewModel::sync, enabled = !busy && session?.token != null) { Text(if (syncing) "Sincronizando…" else "Sincronizar") }
            statuses.forEach { Text("${it.state}: ${it.count} operaciones") }
            Text("ACKED: recibido. RETRY_WAIT: reintento diferido. BLOCKED_AUTH: renovar sesión/permisos. BLOCKED_CONFLICT o BLOCKED_VALIDATION: requiere revisión; no se descartan datos.")
        }
        message?.let { Text(it) }
        if (busy) CircularProgressIndicator()
        if (unassigned.isNotEmpty()) {
            Text("Recorridos de demostración: separados de tu historial y sin envío automático. Solo se asignan con confirmación explícita.")
            unassigned.forEach { trip ->
                OutlinedButton(onClick = { adoption = trip.id }, enabled = !busy && session?.token != null) {
                    Text("Asignar recorrido ${trip.id} · ${formatTimestamp(Instant.ofEpochMilli(trip.startedAt))}")
                }
            }
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
