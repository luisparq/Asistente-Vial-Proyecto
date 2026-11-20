package com.tecsup.asistente.conduccion.feature.map

import com.tecsup.asistente.conduccion.data.network.LaravelApi
import com.tecsup.asistente.conduccion.domain.sync.Session
import com.tecsup.asistente.conduccion.domain.sync.SessionStore
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.maplibre.android.module.http.HttpRequestUtil

data class RoadTileStatus(val ownerKey: String? = null, val message: String = "Cargando tráfico…")

/** Only the tile endpoint of the signed-in server may receive the driver's token. */
internal fun isDriverTileUrl(baseUrl: String, url: HttpUrl): Boolean {
    val base = baseUrl.toHttpUrl()
    val prefix = base.newBuilder().addPathSegments("api/v1/traffic-flow/").build()
    return url.scheme == base.scheme && url.host == base.host && url.port == base.port &&
        url.encodedPath.matches(Regex(Regex.escape(prefix.encodedPath) + "[0-9]+/[0-9]+/[0-9]+\\.png"))
}

@Singleton
class RoadTileClient @Inject constructor(private val sessions: SessionStore, private val api: LaravelApi) {
    private val mutableStatus = MutableStateFlow(RoadTileStatus())
    val status = mutableStatus.asStateFlow()

    internal val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false)
        .addInterceptor { chain ->
            val session = sessions.state.value
            val request = chain.request().newBuilder().removeHeader("Authorization")
            val protectedTile = session != null && isDriverTileUrl(api.normalizeBase(session.baseUrl), chain.request().url)
            if (protectedTile && !session?.token.isNullOrBlank()) {
                request.header("Authorization", "Bearer ${session!!.token}")
            }
            try {
                val response = chain.proceed(request.build())
                if (protectedTile && sessions.state.value == session) {
                    mutableStatus.value = RoadTileStatus(session?.ownerKey, if (response.isSuccessful) {
                        "Tráfico actualizado · TomTom"
                    } else roadServiceMessage(response.code))
                }
                response
            } catch (error: IOException) {
                if (protectedTile && sessions.state.value == session) {
                    mutableStatus.value = RoadTileStatus(session?.ownerKey, "No se pudo cargar el tráfico. Revisa la conexión")
                }
                throw error
            }
        }.build()

    private val publicClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()

    fun install() {
        HttpRequestUtil.setOkHttpClient { request ->
            val session = sessions.state.value
            if (session != null && isDriverTileUrl(api.normalizeBase(session.baseUrl), request.url)) {
                client.newCall(request)
            } else {
                publicClient.newCall(request.newBuilder().removeHeader("Authorization").build())
            }
        }
    }

    fun template(session: Session?, minute: Long): String? {
        if (session?.token.isNullOrBlank()) return null
        return api.normalizeBase(session!!.baseUrl) + "api/v1/traffic-flow/{z}/{x}/{y}.png?refresh=$minute"
    }
}
