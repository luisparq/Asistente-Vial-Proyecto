package com.tecsup.asistente.conduccion.navigation

enum class AppRoute(val path: String) {
    Splash("splash"),
    Login("login"),
    Home("home"),
    Trip("trip"),
    Ble("ble"),
    Diagnostic("diagnostic"),
    History("history"),
    TripDetail("history/{tripId}"),
    Settings("settings"),
}
