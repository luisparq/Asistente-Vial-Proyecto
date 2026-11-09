package com.tecsup.asistente.conduccion.feature.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.AvigemTile
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout

@Composable
fun LoginScreen(onExplore: () -> Unit, onActivate: () -> Unit, onLogin: (String, String) -> Unit,
    onSettings: () -> Unit, state: AccessState = AccessState(), savedEmail: String = "",
    onContinueSession: (() -> Unit)? = null) {
    var email by remember(savedEmail) { mutableStateOf(savedEmail) }
    // Passwords stay in memory only.
    var password by remember { mutableStateOf("") }
    var help by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            AvigemTile(size = 64.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.login_title), style = MaterialTheme.typography.displaySmall)
                Text(stringResource(R.string.login_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(email, { email = it }, label = { Text("Correo") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small)
            PasswordField(password, { password = it }, "Contraseña", enabled = !state.busy)
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(onClick = { onLogin(email, password); password = "" },
                enabled = !state.busy && email.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = MaterialTheme.shapes.small) {
                Text(if (state.busy) "Iniciando sesión…" else "Iniciar sesión", style = MaterialTheme.typography.titleSmall)
            }
            TextButton(onClick = { help = true }, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Recuperar contraseña") }
            onContinueSession?.let { action ->
                OutlinedButton(onClick = action, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = MaterialTheme.shapes.small) { Text("Continuar con mi sesión guardada") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            OutlinedButton(onClick = onActivate, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = MaterialTheme.shapes.small) { Text("Activar cuenta") }
            OutlinedButton(onClick = onExplore, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = MaterialTheme.shapes.small) { Text(stringResource(R.string.explore)) }
            TextButton(onClick = onSettings, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Ajustes") }
        }
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("Recuperar acceso") },
        text = { Text("Contacta al administrador de tu empresa para solicitar una nueva activación de tu cuenta. Después introduce el código en Activar cuenta.") },
        confirmButton = { TextButton(onClick = { help = false }) { Text("Entendido") } })
}

@Composable
internal fun PasswordField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TextButton(onClick = { visible = !visible }, enabled = enabled) { Text(if (visible) "Ocultar" else "Mostrar") } })
}

@Composable
fun WelcomeScreen(name: String?, onContinue: () -> Unit) {
    ScreenLayout("AVIGEM", "Bienvenido a AVIGEM") {
        Text(name?.takeIf { it.isNotBlank() }?.let { "Hola, $it" } ?: "Hola", style = MaterialTheme.typography.headlineLarge)
        Text("AVIGEM te acompaña durante tus recorridos y utiliza la información del dispositivo conectado para apoyar el monitoreo de la conducción.")
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = MaterialTheme.shapes.small) { Text("Comenzar") }
    }
}
