package com.tecsup.asistente.conduccion.feature.login

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout

@Composable
fun ActivationScreen(onBack: () -> Unit, onLogin: (String) -> Unit, viewModel: ActivationViewModel = hiltViewModel(), configuredBaseUrl: String? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val url = configuredBaseUrl ?: viewModel.initialBaseUrl
    var code by remember { mutableStateOf("") }
    // Secrets stay in memory only, never rememberSaveable or persistent storage.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    ScreenLayout("Activar cuenta", "Introduce el código que tu empresa te entregó de forma privada.", onBack) {
        if (!state.activated) {
            OutlinedTextField(code, { code = it }, label = { Text("Código de activación") }, singleLine = true, enabled = !state.busy)
            PasswordField(password, { password = it }, "Nueva contraseña", enabled = !state.busy)
            PasswordField(confirmation, { confirmation = it }, "Confirmar contraseña", enabled = !state.busy)
            Text("La contraseña debe tener al menos 12 caracteres. El código vence en 24 horas y permite un solo uso.")
            Button(onClick = {
                viewModel.activate(url, code, password, confirmation)
                password = ""; confirmation = ""
            }, enabled = !state.busy && code.isNotBlank() && password.isNotEmpty() && confirmation.isNotEmpty()) {
                Text(if (state.busy) "Activando…" else "Activar cuenta")
            }
        }
        state.message?.let { Text(it) }
        if (state.busy) CircularProgressIndicator()
        if (state.activated) Button(onClick = { onLogin(url) }) { Text("Continuar al inicio de sesión") }
    }
}
