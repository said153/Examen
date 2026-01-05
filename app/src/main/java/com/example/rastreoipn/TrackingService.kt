package com.ipn.rastreo

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.ipn.rastreo.database.AppDatabase
import com.ipn.rastreo.database.LocationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var updateInterval = 10000L // 10 segundos por defecto
    private var notificationEnabled = true

    companion object {
        const val CHANNEL_ID = "tracking_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()

        // Crear canal de notificación
        createNotificationChannel()

        // Iniciar servicio en foreground
        startForeground(NOTIFICATION_ID, createNotification("Iniciando rastreo..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Obtener intervalo del intent
        intent?.getLongExtra("interval", 10000L)?.let {
            updateInterval = it
        }

        // Iniciar actualizaciones de ubicación
        startLocationUpdates()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servicio de Rastreo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra que el rastreo está activo"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rastreo IPN Activo")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    // Guardar ubicación en base de datos
                    saveLocation(location)

                    // Actualizar notificación
                    if (notificationEnabled) {
                        updateNotification(location)
                    }

                    // Enviar ubicación a MainActivity si está activa
                    sendLocationToActivity(location)
                }
            }
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, updateInterval)
            .setMinUpdateIntervalMillis(updateInterval)
            .build()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            // Actualizar notificación
            val notification = createNotification("Rastreando ubicación cada ${updateInterval/1000} segundos")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun saveLocation(location: Location) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val locationEntity = LocationEntity(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = System.currentTimeMillis(),
                    accuracy = location.accuracy
                )

                val db = AppDatabase.getDatabase(this@TrackingService)
                db.locationDao().insert(locationEntity)

                Log.d("TrackingService", "Ubicación guardada: ${location.latitude}, ${location.longitude}")
            } catch (e: Exception) {
                Log.e("TrackingService", "Error al guardar ubicación: ${e.message}")
            }
        }
    }

    private fun updateNotification(location: Location) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = createNotification(
            "Lat: ${"%.6f".format(location.latitude)}, Lng: ${"%.6f".format(location.longitude)}"
        )
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun sendLocationToActivity(location: Location) {
        val intent = Intent("LOCATION_UPDATE")
        intent.putExtra("latitude", location.latitude)
        intent.putExtra("longitude", location.longitude)
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Detener actualizaciones de ubicación
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}