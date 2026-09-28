package com.tecsup.asistente.conduccion.feature.settings

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout

@Composable
fun SettingsScreen(onBack: () -> Unit, onReturnToLogin: () -> Unit) {
    ScreenLayout(stringResource(R.string.settings_title), stringResource(R.string.settings_description), onBack) {
        OutlinedButton(onClick = onReturnToLogin) { Text(stringResource(R.string.return_access)) }
    }
}
