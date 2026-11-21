package com.tecsup.asistente.conduccion.feature.map

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import com.tecsup.asistente.conduccion.MainActivity
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.labelResource
import com.tecsup.asistente.conduccion.domain.ble.BleRepository
import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataSource
import com.tecsup.asistente.conduccion.domain.repository.TripRepository
import com.tecsup.asistente.conduccion.domain.sync.SessionStore
import com.tecsup.asistente.conduccion.feature.trip.TripSensorMonitor
import com.tecsup.asistente.conduccion.feature.trip.selectSpanishVoice
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TripRoadVoiceService : Service() {
    @Inject lateinit var trips: TripRepository
    @Inject lateinit var ble: BleRepository
    @Inject lateinit var gps: SensorDataSource
    @Inject lateinit var sessions: SessionStore
    @Inject lateinit var roads: TripRoadData
    @Inject lateinit var sensorMonitor: TripSensorMonitor

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitoring: Job? = null
    private var engine: TextToSpeech? = null
    private var voiceReady = false

    override fun onBind(intent: Intent?): IBinder? = null

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (monitoring != null) return START_NOT_STICKY
        try {
            startForeground(8481, notification())
        } catch (error: SecurityException) {
            Log.w("AVIGEM", "Permiso Bluetooth no disponible para monitoreo vial", error)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        ble.setTripMonitoring(true)

        engine = TextToSpeech(applicationContext) { status ->
            scope.launch(Dispatchers.Main) {
                voiceReady = status == TextToSpeech.SUCCESS && engine?.selectSpanishVoice() == true
            }
        }
        monitoring = scope.launch {
            launch {
                sensorMonitor.observe { notice ->
                    postTripNotice(8482, getString(notice.type.labelResource()), "Evento registrado por AVIGEM One")
                }
            }
            launch {
                trips.observeActiveTrip().flatMapLatest { trip ->
                    if (trip == null) flowOf(null) else trips.observeConfirmedManeuverAlert(trip.id)
                }.distinctUntilChanged().collect { alertId ->
                    if (alertId != null) {
                        postTripNotice(8483, "Alerta de conducción", "Se registraron varias maniobras en este recorrido")
                    }
                }
            }
            combine(trips.observeActiveTrip(), sessions.state) { trip, session -> trip?.id to session }
                .collectLatest { (tripId, session) ->
                    if (tripId == null) stopSelf()
                    else roads.monitor(session, gps.gps) { id, message ->
                        voiceReady && engine?.speak(message, TextToSpeech.QUEUE_ADD, null, id) == TextToSpeech.SUCCESS
                    }
                }
        }
        return START_NOT_STICKY
    }

    private fun postTripNotice(id: Int, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = "avigem-trip-maneuvers"
        manager.createNotificationChannel(
            NotificationChannel(channel, "Maniobras y alertas del sensor", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.notify(id, Notification.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(openApp())
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build())
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE)

    private fun notification(): Notification {
        val channel = "avigem-trip-road-voice"
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel, "Avisos del recorrido", NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("AVIGEM · Recorrido activo")
            .setContentText("Monitoreando GPS y avisos viales")
            .setContentIntent(openApp())
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        ble.setTripMonitoring(false)
        engine?.stop()
        engine?.shutdown()
        engine = null
        super.onDestroy()
    }
}
