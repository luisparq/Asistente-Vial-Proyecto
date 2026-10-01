package com.tecsup.asistente.conduccion.feature.profile

import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout
import com.tecsup.asistente.conduccion.domain.profile.ProfileState
import java.time.format.DateTimeFormatter

@Composable
fun ProfileScreen(onBack: () -> Unit, onSettings: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileContent(state, onBack, onSettings, viewModel::refresh)
}

@Composable
fun ProfileContent(state: ProfileState, onBack: () -> Unit, onSettings: () -> Unit, onRefresh: () -> Unit) {
    ScreenLayout(stringResource(R.string.profile_title), stringResource(R.string.profile_description), onBack) {
        when (state) {
            ProfileState.Loading -> CircularProgressIndicator()
            is ProfileState.Ready -> {
                val profile = state.profile
                Text(profile.name, style = MaterialTheme.typography.headlineSmall)
                Text(profile.email)
                Text(stringResource(R.string.profile_status, statusLabel(profile.status), statusLabel(profile.accessStatus)))
                Text(stringResource(R.string.profile_contact), style = MaterialTheme.typography.titleMedium)
                profile.emergencyContact?.let { Text(it.name); Text(it.relationship); Text(it.phone) }
                    ?: Text(stringResource(R.string.profile_no_contact))
                Text(stringResource(R.string.profile_vehicle), style = MaterialTheme.typography.titleMedium)
                profile.vehicle?.let { Text(it.plate); Text("${it.brand} ${it.model}"); Text(statusLabel(it.status)) }
                    ?: Text(stringResource(R.string.profile_no_vehicle))
                Text(stringResource(R.string.profile_documents), style = MaterialTheme.typography.titleMedium)
                if (profile.documents.isEmpty()) Text(stringResource(R.string.profile_no_documents))
                profile.documents.forEach { document ->
                    Text(document.typeName, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.profile_review, statusLabel(document.reviewStatus)))
                    Text(stringResource(R.string.profile_expiry, document.expiresOn?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        ?: stringResource(R.string.profile_no_expiry)))
                    if (document.expired) Text(stringResource(R.string.profile_expired))
                }
                Text(stringResource(R.string.profile_review_notice), style = MaterialTheme.typography.bodySmall)
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
