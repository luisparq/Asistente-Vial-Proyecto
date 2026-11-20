package com.tecsup.asistente.conduccion.feature.diagnostic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.asistente.conduccion.feature.trip.TripVoice

@Composable
fun DemonstrationScreen(onBack: () -> Unit, viewModel: DiagnosticViewModel = hiltViewModel()) {
    val demo by viewModel.demo.collectAsStateWithLifecycle()
    val publishError by viewModel.publishError.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopCharts() }
    DisposableEffect(viewModel) {
        onDispose { viewModel.stopCharts() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(24.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) { Text("Volver") }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Modo demostración", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Text(
                if (demo.active) demo.statusText else "Inicia la demostración con AVIGEM One conectado por Bluetooth.",
                color = Color.White,
            )
            Button(onClick = {
                if (demo.active) viewModel.stopDemoTest()
                else {
                    viewModel.startCharts()
                    viewModel.startDemoTest(publish = true)
                }
            }) { Text(if (demo.active) "Detener demostración" else "Iniciar modo demostración") }
            if (publishError) Text("No se pudo guardar el evento de demostración.", color = Color.White)
            if (!demo.active && demo.statusText != "Inactiva") Text(demo.statusText, color = Color.White)
        }
    }
    DemonstrationAlert(demo, viewModel::dismissDemoAlert)
}

@Composable
internal fun DemonstrationAlert(demo: DemoTestUiState, onDismiss: () -> Unit, technicalDetails: Boolean = false) {
    if (!demo.alertVisible) return
    val id = "demo-${demo.triggerTimestampMillis}"
    TripVoice(false, id, alertMessage = demo.alertMessage, onAlertDone = { onDismiss() })
    DemoAlertOverlay(
        demo.alertTitle,
        demo.alertObserved.takeIf { technicalDetails }.orEmpty(),
        demo.alertThreshold.takeIf { technicalDetails }.orEmpty(),
        onDismiss,
        message = demo.alertMessage,
    )
}
