package com.tecsup.asistente.conduccion.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tecsup.asistente.conduccion.core.ui.AvigemTile
import com.tecsup.asistente.conduccion.core.ui.InfoCard
import com.tecsup.asistente.conduccion.core.ui.labelResource
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import com.tecsup.asistente.conduccion.domain.profile.DriverProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.PresentationSection
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout
import com.tecsup.asistente.conduccion.core.ui.StatusBadge
import com.tecsup.asistente.conduccion.core.ui.StatusTone
import com.tecsup.asistente.conduccion.domain.profile.ProfileState
import java.time.format.DateTimeFormatter

@Composable
fun ProfileScreen(
    onBack: () -> Unit, onSettings: () -> Unit, viewModel: ProfileViewModel = hiltViewModel(),
    onDemonstration: (() -> Unit)? = null, onLogout: (() -> Unit)? = null, accessMessage: String? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<DriverProfile?>(null) }
    ProfileContent(state, onBack, onSettings, viewModel::refresh, onDemonstration,
        onEdit = { editing = it }, onLogout = onLogout, accessMessage = accessMessage)
    editing?.let { ProfileEditor(it, onClose = { editing = null }, onSave = viewModel::update) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileContent(
    state: ProfileState, onBack: () -> Unit, onSettings: () -> Unit, onRefresh: () -> Unit,
    onDemonstration: (() -> Unit)? = null, onEdit: ((DriverProfile) -> Unit)? = null,
    onLogout: (() -> Unit)? = null, accessMessage: String? = null,
) {
    var showDemonstration by remember { mutableStateOf(false) }
    ScreenLayout(stringResource(R.string.profile_title), stringResource(R.string.profile_description), onBack, mainNav = true) {
        AvigemTile(modifier = if (onDemonstration == null) Modifier else Modifier.combinedClickable(
            onClick = {}, onLongClick = { showDemonstration = true },
        ))
        if (showDemonstration && onDemonstration != null) Button(onClick = onDemonstration) { Text("Demostración") }
        when (state) {
            ProfileState.Loading -> CircularProgressIndicator()
            is ProfileState.Ready -> {
                val profile = state.profile
                InfoCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center) {
                            val photo = profile.photo
                            if (photo != null) Image(photo.asImageBitmap(), "Foto de perfil",
                                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Text(profile.name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull() }
                                .joinToString("").uppercase(), style = MaterialTheme.typography.headlineSmall)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.headlineSmall)
                            Text(profile.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            profile.phone?.takeIf { it.isNotBlank() }?.let { Text(it) }
                            profile.address?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        }
                    }
                    StatusBadge(stringResource(R.string.profile_status, statusLabel(profile.status), statusLabel(profile.accessStatus)))
                    if (onEdit != null) Button(onClick = { onEdit(profile) }) { Text("Editar perfil") }
                }
                PresentationSection("Tu desempeño este mes") {
                    Text("Total de maniobras reales registradas: ${profile.monthlyManeuvers.values.sum()}")
                    DrivingEventType.entries.forEach { type ->
                        InfoCard {
                            Text(stringResource(type.labelResource()))
                            Text((profile.monthlyManeuvers[type.name] ?: 0).toString(), style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    Text(profile.monthlyScore?.let { "$it / 100 puntos" } ?: "Sin datos suficientes")
                    Text("La calificación parte de 100 puntos y resta 5 por cada maniobra real registrada en el mes, con un mínimo de 0.",
                        style = MaterialTheme.typography.bodySmall)
                }
                PresentationSection(stringResource(R.string.profile_contact)) {
                    profile.emergencyContact?.let { Text(it.name); Text(it.relationship); Text(it.phone) }
                        ?: Text(stringResource(R.string.profile_no_contact))
                }
                PresentationSection(stringResource(R.string.profile_vehicle)) {
                    profile.vehicle?.let { Text(it.plate, style = MaterialTheme.typography.titleSmall); Text("${it.brand} ${it.model}"); StatusBadge(statusLabel(it.status)) }
                        ?: Text(stringResource(R.string.profile_no_vehicle))
                }
                PresentationSection(stringResource(R.string.profile_documents)) {
                    if (profile.documents.isEmpty() && profile.missingDocuments.isEmpty()) Text(stringResource(R.string.profile_no_documents))
                    profile.missingDocuments.forEach { name ->
                        InfoCard { Text(name); StatusBadge("Faltante", StatusTone.WARNING) }
                    }
                    profile.documents.forEach { document ->
                        InfoCard {
                            Text(document.typeName, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.profile_review, statusLabel(document.reviewStatus)))
                            Text(stringResource(R.string.profile_expiry, document.expiresOn?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                ?: stringResource(R.string.profile_no_expiry)))
                            if (document.expired) StatusBadge(stringResource(R.string.profile_expired), StatusTone.WARNING)
                        }
                    }
                    Text(stringResource(R.string.profile_review_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> Text(stringResource(when (state) {
                ProfileState.SignedOut -> R.string.profile_signed_out
                ProfileState.NotRegistered -> R.string.profile_not_registered
                ProfileState.Offline -> R.string.profile_offline
                ProfileState.SessionExpired -> R.string.profile_session_expired
                ProfileState.Forbidden -> R.string.profile_forbidden
                else -> R.string.profile_server_error
            }))
        }
        accessMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (onLogout != null) TextButton(onClick = onLogout) { Text("Cerrar sesión") }
        if (state != ProfileState.Loading) Button(onClick = onRefresh) { Text(stringResource(R.string.profile_refresh)) }
        if (state == ProfileState.SignedOut || state == ProfileState.SessionExpired || state == ProfileState.Forbidden)
            Button(onClick = onSettings) { Text(stringResource(R.string.settings_title)) }
    }
}

@Composable
private fun statusLabel(status: String): String = stringResource(when (status) {
    "ACTIVE" -> R.string.profile_active
    "INACTIVE" -> R.string.profile_inactive
    "DISABLED" -> R.string.profile_disabled
    "PENDING" -> R.string.profile_pending
    "VERIFIED" -> R.string.profile_verified
    "REJECTED" -> R.string.profile_rejected
    else -> R.string.profile_unknown
})

@Composable
private fun ProfileEditor(
    profile: DriverProfile, onClose: () -> Unit, onSave: suspend (String, String, ByteArray?) -> Unit,
) {
    var phone by remember { mutableStateOf(profile.phone.orEmpty()) }
    var address by remember { mutableStateOf(profile.address.orEmpty()) }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val resolver = LocalContext.current.contentResolver
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            error = null
            try {
                photo = withContext(Dispatchers.IO) {
                    val bytes = requireNotNull(resolver.openInputStream(uri)).use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count == -1) break
                            require(output.size() + count <= 2 * 1024 * 1024)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    require(bytes.isNotEmpty() && bytes.size <= 2 * 1024 * 1024)
                    bytes
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                photo = null
                error = "Elige una imagen JPG o PNG de hasta 2 MiB."
            } finally {
                busy = false
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("Editar perfil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(phone, { if (it.length <= 40) phone = it }, label = { Text("Teléfono") }, enabled = !busy, singleLine = true)
                OutlinedTextField(address, { if (it.length <= 255) address = it }, label = { Text("Dirección") }, enabled = !busy)
                TextButton(onClick = { picker.launch(arrayOf("image/jpeg", "image/png")) }, enabled = !busy) {
                    Text(if (photo == null) "Adjuntar foto de perfil" else "Cambiar foto seleccionada")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    error = null
                    try {
                        onSave(phone, address, photo)
                        onClose()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (problem: IllegalStateException) {
                        error = problem.message
                    } catch (_: Exception) {
                        error = "No se pudo guardar. Comprueba tu conexión."
                    } finally {
                        busy = false
                    }
                }
            }) { Text(if (busy) "Procesando…" else "Guardar") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text("Cancelar") } },
    )
}
