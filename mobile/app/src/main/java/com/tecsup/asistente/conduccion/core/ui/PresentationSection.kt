package com.tecsup.asistente.conduccion.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tecsup.asistente.conduccion.domain.ble.ConnectionStatus

/** Visual grouping only; callers retain their state, conditions and callbacks. */
@Composable
fun PresentationSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            content()
        }
    }
}

enum class StatusTone { NEUTRAL, OK, PROGRESS, WARNING, ERROR }

private data class TonePalette(val background: Color, val content: Color, val icon: ImageVector)

@Composable
private fun StatusTone.palette(): TonePalette {
    val dark = isSystemInDarkTheme()
    return when (this) {
        StatusTone.OK -> TonePalette(if (dark) Color(0xFF183626) else Color(0xFFE2EDD9), if (dark) Color(0xFFA9DDB5) else Color(0xFF2C6339), Icons.Filled.Check)
        StatusTone.PROGRESS -> TonePalette(if (dark) Color(0xFF17324A) else Color(0xFFDFE9F2), if (dark) Color(0xFFA5CCEF) else Color(0xFF22507A), Icons.Filled.Refresh)
        StatusTone.WARNING -> TonePalette(if (dark) Color(0xFF3B2C10) else Color(0xFFF8EACB), if (dark) Color(0xFFF4CF85) else Color(0xFF7D5200), Icons.Filled.Warning)
        StatusTone.ERROR -> TonePalette(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Filled.Close)
        StatusTone.NEUTRAL -> TonePalette(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Icons.Filled.Info)
    }
}

fun ConnectionStatus.tone(): StatusTone = when (this) {
    ConnectionStatus.CONNECTED -> StatusTone.OK
    ConnectionStatus.CONNECTING -> StatusTone.PROGRESS
    ConnectionStatus.FAILED -> StatusTone.ERROR
    ConnectionStatus.DISCONNECTED -> StatusTone.WARNING
    ConnectionStatus.NOT_SELECTED, ConnectionStatus.SELECTED -> StatusTone.NEUTRAL
}

/** Estado siempre con texto, icono y color. */
@Composable
fun StatusBadge(text: String, tone: StatusTone = StatusTone.NEUTRAL) {
    val palette = tone.palette()
    Surface(color = palette.background, shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(palette.icon, null, Modifier.size(14.dp), tint = palette.content)
            Text(text, style = MaterialTheme.typography.labelMedium, color = palette.content)
        }
    }
}

/** Etiqueta discreta de naturaleza experimental del dato. */
@Composable
fun EvidenceLabel(text: String) = StatusBadge(text, StatusTone.WARNING)
