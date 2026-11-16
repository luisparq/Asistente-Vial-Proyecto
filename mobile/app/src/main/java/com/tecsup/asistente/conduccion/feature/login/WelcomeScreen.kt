package com.tecsup.asistente.conduccion.feature.login

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.AvigemColors
import com.tecsup.asistente.conduccion.core.ui.AvigemTile
import com.tecsup.asistente.conduccion.core.ui.rememberReducedMotion

private data class WelcomeMessage(val title: String, val description: String)

private val welcomeMessages = listOf(
    WelcomeMessage("Prepárate para el camino", "Ajusta tu asiento y tus espejos antes de iniciar el recorrido."),
    WelcomeMessage("Deja espacio para reaccionar", "Mantén una distancia segura con el vehículo de adelante."),
    WelcomeMessage("Mantén la vista en la vía", "Configura el teléfono antes de arrancar y evita manipularlo mientras conduces."),
    WelcomeMessage("Anticipa tus maniobras", "Señaliza con tiempo y revisa tus puntos ciegos antes de cambiar de carril."),
)

@Composable
fun WelcomeScreen(name: String?, onContinue: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("driver_welcome", Context.MODE_PRIVATE)
    }
    val messageIndex = rememberSaveable {
        Math.floorMod(preferences.getInt("next_message", 0), welcomeMessages.size)
    }
    LaunchedEffect(messageIndex) {
        preferences.edit().putInt("next_message", (messageIndex + 1) % welcomeMessages.size).apply()
    }
    val message = welcomeMessages[messageIndex]
    val firstName = name?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }
    val reducedMotion = rememberReducedMotion()
    val entrance = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) entrance.snapTo(1f) else entrance.animateTo(1f, tween(520))
    }

    val backdrop = Color(0xFF111821)
    val typography = MaterialTheme.typography
    Box(Modifier.fillMaxSize().background(backdrop)) {
        Image(
            painter = painterResource(R.drawable.driver_welcome_road),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val scale = 1f + (1f - entrance.value) * 0.035f
                scaleX = scale
                scaleY = scale
            },
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to backdrop.copy(alpha = 0.68f),
                    0.25f to Color.Transparent,
                    0.46f to backdrop.copy(alpha = 0.10f),
                    0.74f to backdrop.copy(alpha = 0.96f),
                    1f to backdrop,
                )
            )
        )
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val viewportHeight = maxHeight
            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).heightIn(min = viewportHeight)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AvigemTile(size = 40.dp)
                    Text(
                        stringResource(R.string.login_title),
                        style = typography.titleMedium,
                        color = AvigemColors.Sillar100,
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 44.dp).graphicsLayer {
                        alpha = entrance.value
                        translationY = (1f - entrance.value) * 16.dp.toPx()
                    },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        if (firstName != null) "Hola, $firstName" else "Bienvenido a AVIGEM",
                        style = typography.titleMedium,
                        color = AvigemColors.Citrine300,
                    )
                    Text(message.title, style = typography.headlineLarge, color = AvigemColors.Sillar100)
                    Text(message.description, style = typography.bodyLarge, color = Color(0xFFD5DDE6))
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.padding(top = 10.dp).fillMaxWidth().heightIn(min = 54.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AvigemColors.Sillar100,
                            contentColor = AvigemColors.Volcanic,
                        ),
                    ) { Text("Comenzar", style = typography.titleSmall) }
                }
            }
        }
    }
}
