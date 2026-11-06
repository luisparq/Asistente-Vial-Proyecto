package com.tecsup.asistente.conduccion.data.diagnostics

import com.tecsup.asistente.conduccion.domain.sync.Session
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class DiagnosticsBatchPayload(val deviceCode: String, val startedAt: String,
    val samples: List<DiagnosticsSamplePayload>)

@Serializable
data class DiagnosticsSamplePayload(val sequence: Long, val sampledAt: String,
    val axRaw: Int, val ayRaw: Int, val azRaw: Int, val gxRaw: Int, val gyRaw: Int, val gzRaw: Int)

data class DiagnosticsHttpReply(val code: Int, val body: String)

@Singleton
class DiagnosticsApi @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build()

    suspend fun samples(session: Session, clientSessionId: String, payload: DiagnosticsBatchPayload) =
        post(session, "sessions/$clientSessionId/samples", json.encodeToString(payload))

    suspend fun complete(session: Session, clientSessionId: String) =
        post(session, "sessions/$clientSessionId/complete", "{}")

    private suspend fun post(session: Session, path: String, payload: String): DiagnosticsHttpReply = withContext(Dispatchers.IO) {
        val token = session.token?.takeIf(String::isNotBlank) ?: throw IOException("Sesión Laravel sin token")
        val base = session.baseUrl.toHttpUrl()
        require(base.isHttps) { "Diagnostics REAL_BLE requiere HTTPS" }
        val url = base.newBuilder().encodedPath("/diagnostics-api/$path").query(null).fragment(null).build()
        val request = Request.Builder().url(url).header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        client.newCall(request).execute().use { DiagnosticsHttpReply(it.code, it.body?.string().orEmpty()) }
    }
}
