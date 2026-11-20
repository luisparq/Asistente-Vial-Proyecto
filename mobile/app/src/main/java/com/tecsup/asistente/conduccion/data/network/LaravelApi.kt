package com.tecsup.asistente.conduccion.data.network

import com.tecsup.asistente.conduccion.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl

data class HttpReply(val code: Int, val body: String, val retryAfter: String? = null)

@Singleton
class LaravelApi @Inject constructor() {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    suspend fun driverPhoto(base: String, token: String): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(normalizeBase(base) + "api/v1/me/driver/photo")
            .header("Authorization", "Bearer $token").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null
            else response.body?.byteStream()?.let {
                android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })
            }
        }
    }

    suspend fun updateDriver(base: String, token: String, phone: String, address: String, photo: ByteArray?): Int =
        withContext(Dispatchers.IO) {
            val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("phone", phone).addFormDataPart("address", address)
            if (photo != null) {
                val png = photo.size >= 8 && photo[0] == 0x89.toByte() &&
                    photo[1] == 0x50.toByte() && photo[2] == 0x4e.toByte() && photo[3] == 0x47.toByte()
                val name = if (png) "profile.png" else "profile.jpg"
                val media = if (png) "image/png" else "image/jpeg"
                form.addFormDataPart("photo", name, photo.toRequestBody(media.toMediaType()))
            }
            val request = Request.Builder().url(normalizeBase(base) + "api/v1/me/driver")
                .header("Accept", "application/json").header("Authorization", "Bearer $token")
                .post(form.build()).build()
            client.newCall(request).execute().use { it.code }
        }

    fun normalizeBase(value: String): String {
        val url = value.trim().toHttpUrl()
        require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "URL no válida." }
        val parts = url.host.split('.').mapNotNull { it.toIntOrNull() }
        val localHost = url.host == "localhost" || url.host == "::1" || (parts.size == 4 && parts.all { it in 0..255 } &&
            (parts[0] == 127 || parts[0] == 10 || (parts[0] == 192 && parts[1] == 168) || (parts[0] == 172 && parts[1] in 16..31)))
        require(url.isHttps || (BuildConfig.DEBUG && localHost)) { "Usa HTTPS. HTTP solo se permite en debug para direcciones locales." }
        return url.toString().trimEnd('/') + "/"
    }
    suspend fun request(base: String, path: String, token: String? = null, payload: String? = null, method: String = "PUT"): HttpReply = withContext(Dispatchers.IO) {
        val url = normalizeBase(base).toHttpUrl().resolve(path) ?: throw IOException("URL inválida")
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")
        builder.method(method, payload?.toRequestBody("application/json; charset=utf-8".toMediaType()))
        client.newCall(builder.build()).execute().use {
            HttpReply(it.code, it.body?.string().orEmpty(), it.header("Retry-After"))
        }
    }
}
