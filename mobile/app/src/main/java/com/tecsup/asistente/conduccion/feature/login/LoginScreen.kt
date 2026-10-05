package com.tecsup.asistente.conduccion.feature.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout

@Composable
fun LoginScreen(onExplore: () -> Unit, onActivate: () -> Unit, onLogin: (String, String) -> Unit,
    onSettings: () -> Unit, state: AccessState = AccessState(), savedEmail: String = "",
    onContinueSession: (() -> Unit)? = null) {
    var email by remember(savedEmail) { mutableStateOf(savedEmail) }
    // Passwords stay in memory only.
    var password by remember { mutableStateOf("") }
    var help by remember { mutableStateOf(false) }
    ScreenLayout(stringResource(R.string.login_title), stringResource(R.string.login_description)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Inicia sesión", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(email, { email = it }, label = { Text("Correo") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth())
                PasswordField(password, { password = it }, "Contraseña", enabled = !state.busy)
                Button(onClick = { onLogin(email, password); password = "" },
                    enabled = !state.busy && email.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.busy) "Iniciando sesión…" else "Iniciar sesión")
                }
                TextButton(onClick = { help = true }, enabled = !state.busy) { Text("¿Olvidaste tu contraseña?") }
                onContinueSession?.let { action ->
                    TextButton(onClick = action, enabled = !state.busy) { Text("Continuar con mi sesión guardada") }
                }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        Text("¿Primera vez?", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = onActivate, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Activar mi cuenta") }
        Text("¿Quieres conocer la aplicación?", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onExplore, enabled = !state.busy) { Text(stringResource(R.string.explore)) }
        TextButton(onClick = onSettings, enabled = !state.busy) { Text("Ajustes") }
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("Recuperar acceso") },
        text = { Text("Contacta al administrador de tu empresa para solicitar una nueva activación de tu cuenta. Después introduce el código en Activar mi cuenta.") },
        confirmButton = { TextButton(onClick = { help = false }) { Text("Entendido") } })
}

@Composable
internal fun PasswordField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TextButton(onClick = { visible = !visible }, enabled = enabled) { Text(if (visible) "Ocultar" else "Mostrar") } })
}

@Composable
fun WelcomeScreen(name: String?, onContinue: () -> Unit) {
    ScreenLayout("Asistente Vial", "Bienvenido a Asistente Vial") {
        Text(name?.takeIf { it.isNotBlank() }?.let { "Hola, $it" } ?: "Hola", style = MaterialTheme.typography.headlineLarge)
        Text("Asistente Vial te acompaña durante tus recorridos y utiliza la información del dispositivo conectado para apoyar el monitoreo de la conducción.")
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Comenzar") }
    }
}
