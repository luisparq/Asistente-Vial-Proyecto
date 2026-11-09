package com.tecsup.asistente.conduccion.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * Símbolo provisional AVIGEM: vía en perspectiva, carril central y punto de asistencia.
 * [progress] va de 0 a 1 y permite dibujarlo en la entrada; 1 es el símbolo completo.
 */
@Composable
fun AvigemMark(modifier: Modifier = Modifier, progress: Float = 1f, color: Color = AvigemColors.Sillar100, node: Color = AvigemColors.Citrine) {
    Canvas(modifier) {
        val u = size.minDimension / 48f
        fun point(x: Float, y: Float) = Offset(x * u, y * u)
        fun segment(fromX: Float, fromY: Float, toX: Float, toY: Float, width: Float, amount: Float) {
            if (amount <= 0f) return
            val start = point(fromX, fromY)
            val end = point(lerp(fromX, toX, amount), lerp(fromY, toY, amount))
            drawLine(color, start, end, width * u, StrokeCap.Round)
        }
        val legs = (progress / 0.7f).coerceIn(0f, 1f)
        val lanes = ((progress - 0.35f) / 0.4f).coerceIn(0f, 1f)
        val dot = ((progress - 0.65f) / 0.35f).coerceIn(0f, 1f)
        segment(10f, 41f, 21f, 14f, 5.5f, legs)
        segment(38f, 41f, 27f, 14f, 5.5f, legs)
        segment(24f, 41f, 24f, 35f, 4f, lanes)
        segment(24f, 28.5f, 24f, 25f, 3.2f, ((lanes - 0.5f) * 2f).coerceIn(0f, 1f))
        if (dot > 0f) drawCircle(node.copy(alpha = dot), radius = lerp(3.2f, 4.6f, dot) * u, center = point(24f, 8f))
    }
}

/** Marca sobre placa volcánica: icono reducido coherente en tema claro y oscuro. */
@Composable
fun AvigemTile(size: Dp = 48.dp, progress: Float = 1f, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(AvigemColors.Volcanic, RoundedCornerShape(size * 0.26f)), contentAlignment = Alignment.Center) {
        AvigemMark(Modifier.size(size * 0.68f), progress)
    }
}
