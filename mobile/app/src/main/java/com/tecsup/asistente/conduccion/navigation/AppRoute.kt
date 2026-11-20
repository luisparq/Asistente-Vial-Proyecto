package com.tecsup.asistente.conduccion.navigation

enum class AppRoute(val path: String) {
    Splash("splash"),
    Login("login"),
    Activation("activation"),
    Welcome("welcome"),
    Home("home"),
    Trip("trip"),
    Ble("ble"),
    Diagnostic("diagnostic"),
    DetectorLaboratory("detector-laboratory"),
    History("history"),
    TripDetail("history/{tripId}"),
    Settings("settings"),
    Profile("profile"),
    Map("map"),
}
