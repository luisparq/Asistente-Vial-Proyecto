package com.tecsup.asistente.conduccion.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.AvigemMark
import com.tecsup.asistente.conduccion.core.ui.AvigemColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onExplore: () -> Unit,
    onActivate: () -> Unit,
    onLogin: (String, String) -> Unit,
    onSettings: () -> Unit,
    state: AccessState = AccessState(),
    savedEmail: String = "",
    onContinueSession: (() -> Unit)? = null,
) {
    var showLogin by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val paper = ButtonDefaults.buttonColors(
        containerColor = AvigemColors.Sillar100,
        contentColor = AvigemColors.Volcanic,
    )
    val links = ButtonDefaults.textButtonColors(contentColor = colors.onSurface)
    Surface(Modifier.fillMaxSize(), color = colors.background) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF233245), Color(0xFF192330), colors.background))
            ).systemBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val viewportHeight = maxHeight
            Column(
                modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight).padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.login_title), Modifier.weight(1f), style = typography.titleLarge)
                    TextButton(onClick = onSettings, enabled = !state.busy, colors = links) { Text("Ajustes") }
                }
                Column(Modifier.padding(vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AvigemMark(Modifier.size(82.dp))
                    Text("Asistente vial", style = typography.displaySmall)
                    Text(
                        "Alertas durante el recorrido para ayudar a prevenir maniobras de riesgo.",
                        style = typography.bodyLarge,
                        color = Color(0xFFD1D8E2),
                    )
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = { showLogin = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = paper,
                    ) { Text("Iniciar sesión", style = typography.titleSmall) }
                    TextButton(onClick = onExplore, enabled = !state.busy, colors = links) {
                        Text(stringResource(R.string.explore))
                    }
                    Text(
                        stringResource(R.string.login_description),
                        style = typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
    if (showLogin) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showLogin = false },
            sheetState = sheet,
            containerColor = colors.surface,
        ) {
            LoginForm(
                state = state,
                savedEmail = savedEmail,
                onLogin = onLogin,
                onContinueSession = onContinueSession,
                onActivate = {
                    showLogin = false
                    onActivate()
                },
            )
        }
    }
}

@Composable
private fun LoginForm(
    state: AccessState,
    savedEmail: String,
    onLogin: (String, String) -> Unit,
    onContinueSession: (() -> Unit)?,
    onActivate: () -> Unit,
) {
    var email by remember(savedEmail) { mutableStateOf(savedEmail) }
    // Passwords stay in memory only.
    var password by remember { mutableStateOf("") }
    var help by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val links = ButtonDefaults.textButtonColors(contentColor = colors.onSurface)
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Iniciar sesión", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            email, { email = it }, label = { Text("Correo electrónico") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
        )
        PasswordField(password, { password = it }, "Contraseña", enabled = !state.busy)
        TextButton(
            onClick = { help = true },
            enabled = !state.busy,
            modifier = Modifier.align(Alignment.End),
            colors = links,
        ) { Text("Recuperar contraseña") }
        state.message?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium) }
        Button(
            onClick = {
                onLogin(email, password)
                password = ""
            },
            enabled = !state.busy && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = AvigemColors.Sillar100,
                contentColor = AvigemColors.Volcanic,
            ),
        ) { Text(if (state.busy) "Iniciando sesión…" else "Entrar", style = MaterialTheme.typography.titleSmall) }
        onContinueSession?.let { action ->
            FilledTonalButton(
                onClick = action,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.small,
            ) { Text("Continuar con mi sesión guardada") }
        }
        TextButton(
            onClick = onActivate,
            enabled = !state.busy,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = links,
        ) { Text("Activar cuenta") }
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
