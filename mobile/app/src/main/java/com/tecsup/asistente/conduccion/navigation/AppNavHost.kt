package com.tecsup.asistente.conduccion.navigation

import androidx.compose.runtime.Composable
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
import com.tecsup.asistente.conduccion.feature.settings.SettingsScreen
import com.tecsup.asistente.conduccion.feature.splash.SplashScreen
import com.tecsup.asistente.conduccion.feature.trip.TripScreen

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val onBack: () -> Unit = { navController.popBackStack() }
    NavHost(navController = navController, startDestination = AppRoute.Splash.path) {
        composable(AppRoute.Splash.path) {
            SplashScreen(onReady = {
                navController.navigate(AppRoute.Login.path) {
                    popUpTo(AppRoute.Splash.path) { inclusive = true }
                    launchSingleTop = true
                }
            })
        }
        composable(AppRoute.Login.path) {
            LoginScreen(onExplore = {
                navController.navigate(AppRoute.Home.path) {
                    popUpTo(AppRoute.Login.path) { inclusive = true }
                    launchSingleTop = true
                }
            })
        }
        composable(AppRoute.Home.path) {
            HomeScreen(
                onTrip = { navController.open(AppRoute.Trip) },
                onBle = { navController.open(AppRoute.Ble) },
                onDiagnostic = { navController.open(AppRoute.Diagnostic) },
                onHistory = { navController.open(AppRoute.History) },
                onSettings = { navController.open(AppRoute.Settings) },
                onProfile = { navController.open(AppRoute.Profile) },
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
        composable(AppRoute.Ble.path) { BleScreen(onBack) }
        composable(AppRoute.Profile.path) {
            com.tecsup.asistente.conduccion.feature.profile.ProfileScreen(onBack, onSettings = { navController.open(AppRoute.Settings) })
        }
        composable(AppRoute.Diagnostic.path) { DiagnosticScreen(onBack) }
        composable(AppRoute.History.path) {
            HistoryScreen(onBack, onTripSelected = { id -> navController.navigate("history/$id") })
        }
        composable(AppRoute.TripDetail.path, arguments = listOf(navArgument("tripId") { type = NavType.LongType })) {
            TripDetailScreen(onBack)
        }
        composable(AppRoute.Settings.path) {
            SettingsScreen(onBack, onReturnToLogin = {
                navController.navigate(AppRoute.Login.path) {
                    popUpTo(AppRoute.Home.path) { inclusive = true }
                    launchSingleTop = true
                }
            })
        }
    }
}

private fun NavHostController.open(route: AppRoute) {
    navigate(route.path) { launchSingleTop = true }
}
