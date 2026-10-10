package com.tecsup.asistente.conduccion.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import com.tecsup.asistente.conduccion.core.ui.LocalDemoMode
import com.tecsup.asistente.conduccion.domain.profile.ProfileState
import com.tecsup.asistente.conduccion.feature.login.AccessViewModel
import com.tecsup.asistente.conduccion.feature.login.WelcomeScreen
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tecsup.asistente.conduccion.feature.ble.BleScreen
import com.tecsup.asistente.conduccion.feature.diagnostic.DiagnosticScreen
import com.tecsup.asistente.conduccion.feature.history.HistoryScreen
import com.tecsup.asistente.conduccion.feature.history.TripDetailScreen
import com.tecsup.asistente.conduccion.feature.home.HomeScreen
import com.tecsup.asistente.conduccion.feature.login.LoginScreen
import com.tecsup.asistente.conduccion.feature.login.ActivationScreen
import com.tecsup.asistente.conduccion.feature.settings.SettingsScreen
import com.tecsup.asistente.conduccion.feature.splash.SplashScreen
import com.tecsup.asistente.conduccion.feature.trip.TripScreen

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val access: AccessViewModel = hiltViewModel()
    val session by access.session.collectAsStateWithLifecycle()
    val accessState by access.state.collectAsStateWithLifecycle()
    val profile by access.profile.collectAsStateWithLifecycle()
    val connection by access.connection.collectAsStateWithLifecycle()
    val demoTripIds by access.demoTripIds.collectAsStateWithLifecycle()
    val entry by navController.currentBackStackEntryAsState()
    val name = if (session != null) (profile as? ProfileState.Ready)?.profile?.name else null
    val demo = session == null && entry?.destination?.route !in listOf(null, "splash", "login", "activation", "welcome")
    LaunchedEffect(accessState.destination) {
        accessState.destination?.let { destination ->
            navController.navigate(destination) {
                popUpTo(AppRoute.Login.path) { inclusive = true }
                launchSingleTop = true
            }
            access.navigationHandled()
        }
    }
    val onBack: () -> Unit = { navController.popBackStack() }
    CompositionLocalProvider(LocalDemoMode provides demo) {
    NavHost(navController = navController, startDestination = AppRoute.Splash.path) {
        composable(AppRoute.Splash.path) {
            SplashScreen(onReady = {
                navController.navigate(AppRoute.Login.path) {
                    popUpTo(AppRoute.Splash.path) { inclusive = true }
                    launchSingleTop = true
                }
            })
        }
        composable(AppRoute.Activation.path) {
            ActivationScreen(onBack, onLogin = { navController.popBackStack(AppRoute.Login.path, false) },
                configuredBaseUrl = access.configuration.baseUrl)
        }
        composable(AppRoute.Login.path) {
            LoginScreen(onExplore = access::demo, onActivate = { navController.open(AppRoute.Activation) },
                onLogin = access::login, onSettings = { navController.open(AppRoute.Settings) },
                state = accessState, savedEmail = session?.email.orEmpty(),
                onContinueSession = if (session != null) access::continueSession else null)
        }
        composable(AppRoute.Welcome.path) {
            WelcomeScreen(name) {
                navController.navigate(AppRoute.Home.path) {
                    popUpTo(AppRoute.Welcome.path) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        composable(AppRoute.Home.path) {
            HomeScreen(
                onTrip = { navController.open(AppRoute.Trip) },
                onBle = { navController.open(AppRoute.Ble) },
                onHistory = { navController.open(AppRoute.History) },
                onSettings = { navController.open(AppRoute.Settings) },
                onProfile = { navController.open(AppRoute.Profile) },
                onMap = { navController.open(AppRoute.Map) },
                name = name, company = session?.companyName, connection = connection,
            )
        }
        composable(AppRoute.Trip.path) {
            TripScreen(onBack, onFinished = {
                navController.navigate(AppRoute.History.path) {
                    popUpTo(AppRoute.Home.path)
                    launchSingleTop = true
                }
            })
        }
        composable(AppRoute.Ble.path) { BleScreen(onBack, onDiagnostic = { navController.open(AppRoute.Diagnostic) }) }
        composable(AppRoute.Profile.path) {
            com.tecsup.asistente.conduccion.feature.profile.ProfileScreen(onBack, onSettings = { navController.open(AppRoute.Settings) })
        }
        composable(AppRoute.Map.path) { com.tecsup.asistente.conduccion.feature.map.DriverMapScreen(onBack) }
        composable(AppRoute.Diagnostic.path) { DiagnosticScreen(onBack) }
        composable(AppRoute.History.path) {
            HistoryScreen(onBack, onTripSelected = { id -> navController.navigate("history/$id") },
                excludedTripIds = if (session == null) emptySet() else demoTripIds)
        }
        composable(AppRoute.TripDetail.path, arguments = listOf(navArgument("tripId") { type = NavType.LongType })) {
            TripDetailScreen(onBack)
        }
        composable(AppRoute.Settings.path + "?activationBaseUrl={activationBaseUrl}", arguments = listOf(navArgument("activationBaseUrl") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
            SettingsScreen(onBack, onReturnToLogin = {
                navController.navigate(AppRoute.Login.path) {
                    popUpTo(navController.graph.id)
                    launchSingleTop = true
                }
            }, initialBaseUrl = entry.arguments?.getString("activationBaseUrl") ?: access.configuration.baseUrl,
                onSaveServer = access.configuration::save)
        }
    }
    }
}

private fun NavHostController.open(route: AppRoute) {
    navigate(route.path) { launchSingleTop = true }
}
