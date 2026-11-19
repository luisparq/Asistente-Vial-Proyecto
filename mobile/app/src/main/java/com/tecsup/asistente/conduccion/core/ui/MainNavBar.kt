package com.tecsup.asistente.conduccion.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector

/** Estado de la barra principal: ruta actual y acción de navegación. Lo provee AppNavHost. */
class MainNav(val current: String?, val go: (String) -> Unit)

val LocalMainNav = staticCompositionLocalOf<MainNav?> { null }

private data class MainTab(val route: String, val label: String, val icon: ImageVector)

private val mainTabs = listOf(
    MainTab("home", "Inicio", Icons.Default.Home),
    MainTab("map", "Mapa", Icons.Default.Place),
    MainTab("history", "Historial", Icons.Default.DateRange),
    MainTab("profile", "Perfil", Icons.Default.Person),
)

/** Navegación principal del conductor. Colores de marca: citrino sobre volcánico. */
@Composable
fun MainNavBar(nav: MainNav) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        mainTabs.forEach { tab ->
            val selected = nav.current == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) nav.go(tab.route) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AvigemColors.Volcanic,
                    selectedTextColor = AvigemColors.Citrine,
                    indicatorColor = AvigemColors.Citrine,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
