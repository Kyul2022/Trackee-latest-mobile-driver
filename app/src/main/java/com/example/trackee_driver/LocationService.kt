package com.example.trackee_driver

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.PackageManager
import android.content.Intent
import android.location.Geocoder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.*

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val intervalMillis = 1 * 60 * 1000L // 5 minutes

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startForeground(1, createNotification())
        startLocationUpdates()
    }

    private fun createNotification(): Notification {
        val channelId = "location_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Trackee Driver")
            .setContentText("Sending location every 5 minutes")
            .setSmallIcon(R.drawable.ic_location) // remplace par ton icône
            .build()
    }

    private fun startLocationUpdates() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val token = TokenManager.getToken(this@LocationService)
                    if (token != null) {
                        val location = getLastLocation()
                        if (location != null) {
                            val city = getCityFromLocation(location.latitude, location.longitude)
                            sendToBackend(city, token)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(intervalMillis)
            }
        }
    }

    private fun getLastLocation(): android.location.Location? {
        return try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                null
            } else {
                Tasks.await(fusedLocationClient.lastLocation)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getCityFromLocation(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            addresses?.firstOrNull()?.locality ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun sendToBackend(city: String, token: String) {
        try {
            val json = JSONObject().put("city", city).toString()
            val client = OkHttpClient()
            Log.d("LocationUpdate", "Payload: $json")
            val request = Request.Builder()
                .url("http://172.20.197.225:8080/location-test") // remplace par ton endpoint
                .addHeader("Authorization", "Bearer $token")
                .post(json.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
