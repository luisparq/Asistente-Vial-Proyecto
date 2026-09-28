package com.tecsup.asistente.conduccion.feature.login

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout

@Composable
fun LoginScreen(onExplore: () -> Unit) {
    ScreenLayout(stringResource(R.string.login_title), stringResource(R.string.login_description)) {
        Button(onClick = onExplore) { Text(stringResource(R.string.explore)) }
    }
}
