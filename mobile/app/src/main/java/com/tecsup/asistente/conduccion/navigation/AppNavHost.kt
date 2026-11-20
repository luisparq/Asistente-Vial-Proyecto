package com.tecsup.asistente.conduccion.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.tecsup.asistente.conduccion.feature.trip.TripVoice
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import com.tecsup.asistente.conduccion.core.ui.LocalDemoMode
import com.tecsup.asistente.conduccion.core.ui.LocalMainNav
import com.tecsup.asistente.conduccion.core.ui.MainNav
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
import com.tecsup.asistente.conduccion.feature.diagnostic.VehicleLabScreen
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
    var welcomeRequested by remember { mutableStateOf(false) }
    LaunchedEffect(accessState.destination) {
        accessState.destination?.let { destination ->
            welcomeRequested = false
            navController.navigate(destination) {
                popUpTo(navController.graph.id) { inclusive = false }
                launchSingleTop = true
            }
            access.navigationHandled()
        }
    }
    TripVoice(
        welcomeRequested, null,
        greeting = "Hola, soy avigem, tu asistente vial inteligente.",
        prepare = entry?.destination?.route == AppRoute.Welcome.path,
    )
    val onBack: () -> Unit = { navController.popBackStack() }
    val mainNav = MainNav(entry?.destination?.route) { route ->
        navController.navigate(route) {
            popUpTo(AppRoute.Home.path)
            launchSingleTop = true
        }
    }
    CompositionLocalProvider(LocalDemoMode provides demo, LocalMainNav provides mainNav) {
    val reducedMotion = com.tecsup.asistente.conduccion.core.ui.rememberReducedMotion()
    NavHost(navController = navController, startDestination = AppRoute.Splash.path,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
    ) {
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
                welcomeRequested = true
                navController.navigate(AppRoute.Home.path) {
                    popUpTo(AppRoute.Welcome.path) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        composable(AppRoute.Home.path, enterTransition = {
            fadeIn(tween(if (reducedMotion) 0 else 180))
        }) {
            HomeScreen(
                onTrip = { startedNew ->
                    navController.navigate(tripRoute(startedNew)) { launchSingleTop = true }
                },
                onBle = { navController.open(AppRoute.Ble) },
                onHistory = { navController.open(AppRoute.History) },
                onSettings = { navController.open(AppRoute.Settings) },
                onProfile = { navController.open(AppRoute.Profile) },
                onMap = { navController.open(AppRoute.Map) },
                onPresentation = { navController.open(AppRoute.Diagnostic) },
                name = name, company = session?.companyName, connection = connection,
            )
        }
        composable(
            TRIP_ROUTE,
            arguments = listOf(navArgument(STARTED_NEW_ARG) { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            TripScreen(
                onBack,
                onFinished = {
                    navController.navigate(AppRoute.History.path) {
                        popUpTo(AppRoute.Home.path)
                        launchSingleTop = true
                    }
                },
                startedNew = entry.arguments?.getBoolean(STARTED_NEW_ARG) ?: false,
                connection = connection,
            )
        }
        composable(AppRoute.Ble.path) {
            BleScreen(
                onBack,
                onDiagnostic = { navController.open(AppRoute.Diagnostic) },
                onLaboratory = { navController.open(AppRoute.DetectorLaboratory) },
            )
        }
        composable(AppRoute.Profile.path) {
            com.tecsup.asistente.conduccion.feature.profile.ProfileScreen(
                onBack, onSettings = { navController.open(AppRoute.Settings) },
                onDemonstration = if (session?.token != null) ({ navController.navigate("demonstration") }) else null,
                onLogout = if (session != null) access::logout else null,
                accessMessage = accessState.message,
            )
        }
        composable("demonstration") {
            if (session != null) com.tecsup.asistente.conduccion.feature.diagnostic.DemonstrationScreen(onBack)
        }
        composable(AppRoute.Map.path) {
            com.tecsup.asistente.conduccion.feature.map.DriverMapScreen(onBack, onDevice = { navController.open(AppRoute.Ble) })
        }
        composable(AppRoute.Diagnostic.path) { DiagnosticScreen(onBack) }
        composable(AppRoute.DetectorLaboratory.path) { VehicleLabScreen(onBack) }
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

private const val STARTED_NEW_ARG = "startedNew"
private const val TRIP_ROUTE = "trip?$STARTED_NEW_ARG={$STARTED_NEW_ARG}"

private fun tripRoute(startedNew: Boolean) = "trip?$STARTED_NEW_ARG=$startedNew"

private fun NavHostController.open(route: AppRoute) {
    navigate(route.path) { launchSingleTop = true }
}
