package com.tecsup.asistente.conduccion.feature.splash

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout

@Composable
fun SplashScreen(onReady: () -> Unit, viewModel: SplashViewModel = hiltViewModel()) {
    val isReady by viewModel.isReady.collectAsStateWithLifecycle()
    LaunchedEffect(isReady) { if (isReady) onReady() }
    ScreenLayout(stringResource(R.string.app_name), stringResource(R.string.splash_description)) {
        CircularProgressIndicator()
    }
}
