package com.tecsup.asistente.conduccion.data.repository

import com.tecsup.asistente.conduccion.data.network.Contract
import com.tecsup.asistente.conduccion.data.network.DriverProfileDto
import com.tecsup.asistente.conduccion.data.network.Envelope
import com.tecsup.asistente.conduccion.data.network.LaravelApi
import com.tecsup.asistente.conduccion.domain.profile.DriverProfileRepository
import com.tecsup.asistente.conduccion.domain.profile.ProfileState
import com.tecsup.asistente.conduccion.domain.sync.SessionStore
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update

/** Personal data is never written to Room, preferences, logs or a disk HTTP cache. */
class LaravelDriverProfileRepository @Inject constructor(
    private val api: LaravelApi,
    private val sessions: SessionStore,
) : DriverProfileRepository {
    private val refreshVersion = MutableStateFlow(0L)
    override fun refresh() { refreshVersion.update { it + 1 } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe() = combine(sessions.state, refreshVersion) { session, _ -> session }
        .transformLatest { session ->
            // Clear previous account's data before starting any request.
            emit(ProfileState.Loading)
            if (session == null) { emit(ProfileState.SignedOut); return@transformLatest }
            if (session.token == null) { emit(ProfileState.SessionExpired); return@transformLatest }
            val result = try {
                val response = api.request(session.baseUrl, "api/v1/me/driver", session.token, method = "GET")
                when (response.code) {
                    200 -> ProfileState.Ready(Contract.json.decodeFromString<Envelope<DriverProfileDto>>(response.body).data.toDomain())
                    401 -> ProfileState.SessionExpired
                    403 -> ProfileState.Forbidden
                    404 -> ProfileState.NotRegistered
                    else -> ProfileState.ServerError
                }
            } catch (error: CancellationException) { throw error
            } catch (_: IOException) { ProfileState.Offline
            } catch (_: Exception) { ProfileState.ServerError }
            // A response from a session that changed while HTTP was running must never be displayed.
            if (sessions.state.value == session) emit(result)
        }
}
