package com.tecsup.asistente.conduccion.feature.login

import android.content.Context
import com.tecsup.asistente.conduccion.BuildConfig
import com.tecsup.asistente.conduccion.data.network.LaravelApi
import com.tecsup.asistente.conduccion.domain.sync.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Only the server address is stored here; credentials remain in the existing secure store. */
@Singleton
class ServerConfiguration @Inject constructor(
    @ApplicationContext context: Context,
    private val api: LaravelApi,
    private val sessions: SessionStore,
) {
    private val preferences = context.getSharedPreferences("server_configuration", Context.MODE_PRIVATE)
    val baseUrl: String
        get() = preferences.getString("base_url", null) ?: sessions.state.value?.baseUrl ?: BuildConfig.API_BASE_URL

    fun save(value: String) {
        preferences.edit().putString("base_url", api.normalizeBase(value.trim())).apply()
    }
}
