package com.tecsup.asistente.conduccion.data.diagnostics

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataSource
import com.tecsup.asistente.conduccion.domain.sync.SessionStore
import com.tecsup.asistente.conduccion.domain.sync.SyncScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Entity(tableName="sensor_relay_outbox",indices=[Index(value=["owner","capturedAt"])])
data class SensorRelayRow(@PrimaryKey val id:String,val owner:String,val payload:String,val capturedAt:Long)
@Dao interface SensorRelayDao {
 @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(row:SensorRelayRow)
 @Query("SELECT * FROM sensor_relay_outbox WHERE owner=:owner ORDER BY capturedAt, id LIMIT 100") suspend fun batch(owner:String):List<SensorRelayRow>
 @Query("DELETE FROM sensor_relay_outbox WHERE owner=:owner AND id IN (:ids)") suspend fun acknowledge(owner:String,ids:List<String>)
 @Query("SELECT COUNT(*) FROM sensor_relay_outbox WHERE owner=:owner") suspend fun count(owner:String):Int
}
val MIGRATION_5_6=object:Migration(5,6){override fun migrate(db:SupportSQLiteDatabase){
 db.execSQL("CREATE TABLE IF NOT EXISTS sensor_relay_outbox (id TEXT NOT NULL, owner TEXT NOT NULL, payload TEXT NOT NULL, capturedAt INTEGER NOT NULL, PRIMARY KEY(id))")
 db.execSQL("CREATE INDEX IF NOT EXISTS index_sensor_relay_outbox_owner_capturedAt ON sensor_relay_outbox(owner,capturedAt)")
}}
data class SensorRelayState(val sending:Boolean=false,val pending:Int=0,val acknowledged:Int=0,val dropped:Int=0,val message:String="El celular reenvía las muestras; el ESP32 detecta y activa su buzzer.")
@Singleton
class SensorRelay @Inject constructor(
    private val source: SensorDataSource, private val sessions: SessionStore,
    private val dao: SensorRelayDao, private val scheduler: SyncScheduler
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(SensorRelayState())
    val state = mutable.asStateFlow()
    @Volatile private var owner: String? = null
    @Volatile private var captureStartedAt:Long=Long.MAX_VALUE
    private var displayedOwner:String?=null
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()

    init {
        scope.launch {
            source.relayFrames.collect { row ->
                val account = sessions.state.value
                if (!mutable.value.sending || account == null || account.ownerKey != owner || row.receivedAtNanos<captureStartedAt) return@collect
                try {
                    if (dao.count(account.ownerKey) >= 100000) {
                        stop()
                        mutable.update { it.copy(message = "Cola llena. Envía los pendientes antes de continuar.") }
                        return@collect
                    }
                    // Ownership is captured with the sample. Account changes never relabel existing rows.
                    val id = "${account.ownerKey}|${row.deviceCode}|${row.sessionId}|${row.sequence}"
                    dao.insert(SensorRelayRow(id, account.ownerKey, Json.encodeToString(row), row.capturedAt))
                    val pending = dao.count(account.ownerKey)
                    if (sessions.state.value?.ownerKey == account.ownerKey) mutable.update { it.copy(pending = pending) }
                    if (pending % 100 == 0) scheduler.scheduleFallback()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    stop()
                    mutable.update { it.copy(message = "Captura detenida: no se pudo guardar (${e.message}). Los pendientes anteriores se conservan.") }
                }
            }
        }
        scope.launch {
            source.status.collect { status ->
                if (mutable.value.sending && status != com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataStatus.RECEIVING) {
                    stop()
                    mutable.update { it.copy(message = "Recepción BLE interrumpida. Reconecta e inicia el envío otra vez; los pendientes se conservan.") }
                }
            }
        }
        scope.launch { source.relayDrops.collect { count -> mutable.update { it.copy(dropped = count) } } }
        scope.launch {
            sessions.state.collect { account ->
                if (owner != null && account?.ownerKey != owner) stop()
                if(displayedOwner!=account?.ownerKey){displayedOwner=account?.ownerKey;mutable.update{it.copy(acknowledged=0)}}
                try {
                    val pending = account?.let { dao.count(it.ownerKey) } ?: 0
                    if (sessions.state.value?.ownerKey == account?.ownerKey) mutable.update { it.copy(pending = pending) }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { mutable.update { it.copy(message = "No se pudo consultar la cola local: ${e.message}") } }
            }
        }
        scope.launch { while (isActive) { delay(3000); if (mutable.value.sending) flush() } }
    }

    fun start() {
        val account = sessions.state.value
        if (account?.token.isNullOrBlank()) {
            mutable.update { it.copy(message = "Inicia sesión para enviar muestras a los administradores.") }; return
        }
        if (!source.relayIdentity.value || source.status.value != com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataStatus.RECEIVING) {
            mutable.update { it.copy(message = "Esperando tramas con identidad del ESP32. Actualiza el firmware y reconecta.") }; return
        }
        captureStartedAt=System.nanoTime()
        owner = account!!.ownerKey
        mutable.update { it.copy(sending = true, message = "Enviando muestras a Laravel. Sin Internet quedan guardadas.") }
    }

    fun stop() {
        owner = null
        captureStartedAt=Long.MAX_VALUE
        mutable.update { it.copy(sending = false) }
        scheduler.scheduleFallback()
    }

    /** True only for transient failures. Successful backlog progress schedules an immediate next worker. */
    suspend fun flush(): Boolean = sessions.gate.withLock {
        val account = sessions.state.value ?: return@withLock false
        try {
            val base = account.baseUrl.toHttpUrl()
            require(base.isHttps) { "Configura una URL HTTPS de Laravel" }
            val path = base.encodedPath.trimEnd('/')
            val url = base.newBuilder().encodedPath(if (path.endsWith("/api/v1")) "$path/sensor-samples" else "/api/v1/sensor-samples")
                .query(null).fragment(null).build()
            repeat(20) {
                val batch = dao.batch(account.ownerKey)
                if (batch.isEmpty()) return@withLock false
                val body = buildJsonObject { put("samples", JsonArray(batch.map { Json.parseToJsonElement(it.payload) })) }.toString()
                val request = Request.Builder().url(url).header("Authorization", "Bearer ${account.token}")
                    .header("Accept", "application/json").post(body.toRequestBody("application/json".toMediaType())).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code in listOf(401, 403, 409, 422)) { owner = null; mutable.update { it.copy(sending = false) } }
                        mutable.update { it.copy(message = "Laravel respondió ${response.code}. Se conservan los pendientes; revisa sesión y vínculo del dispositivo.") }
                        return@withLock response.code == 429 || response.code >= 500
                    }
                    val result = Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject["accepted"]?.jsonArray
                    require(result?.size == batch.size) { "Confirmación incompleta de Laravel" }
                    batch.zip(result!!).forEach { (stored, ack) ->
                        val original = Json.parseToJsonElement(stored.payload).jsonObject
                        require(ack.jsonObject["deviceCode"] == original["deviceCode"] &&
                            ack.jsonObject["sessionId"] == original["sessionId"] && ack.jsonObject["sequence"] == original["sequence"])
                    }
                    dao.acknowledge(account.ownerKey, batch.map { it.id })
                    val pending = dao.count(account.ownerKey)
                    if (sessions.state.value?.ownerKey == account.ownerKey) mutable.update {
                        it.copy(pending = pending, acknowledged = it.acknowledged + batch.size,
                            message = "Laravel confirmó las muestras. Empresa y Vialigent ven el mismo registro.")
                    }
                }
            }
            if (dao.count(account.ownerKey) > 0) scheduler.schedule()
            false
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            mutable.update { it.copy(message = "No se pudo enviar: ${e.message}. Muestras conservadas.") }
            true
        }
    }
}
