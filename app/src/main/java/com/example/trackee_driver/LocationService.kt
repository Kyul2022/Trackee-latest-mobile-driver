package com.example.trackee_driver

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.*

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val updateIntervalMillis = 20 * 1000L // 20 seconds
    private val fastestIntervalMillis = 5 * 1000L  // 5 seconds

    private var currentLatitude = 0.0
    private var currentLongitude = 0.0
    private var currentCity = "Unknown"

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "location_channel"
        private const val TAG = "LocationService"
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationRequest()
        createLocationCallback()

        // Start as foreground service immediately
        startForeground(NOTIFICATION_ID, createNotification())

        // Check if location is enabled, if not try to enable it
        checkLocationSettings()
    }

    private fun createLocationRequest() {
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            updateIntervalMillis
        ).apply {
            setMinUpdateIntervalMillis(fastestIntervalMillis)
            setMaxUpdateDelayMillis(updateIntervalMillis * 2)
        }.build()
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    currentLatitude = location.latitude
                    currentLongitude = location.longitude
                    currentCity = getCityFromLocation(location.latitude, location.longitude)

                    Log.d(TAG, "Location updated: $currentCity ($currentLatitude, $currentLongitude)")

                    // Update notification with current city
                    updateNotification()

                    // Send to backend
                    serviceScope.launch {
                        val token = TokenManager.getAccessToken(this@LocationService)
                        if (token != null) {
                            sendToBackend(currentCity, currentLatitude, currentLongitude, token, this@LocationService, retry = true)
                        }
                    }
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    Log.w(TAG, "Location is not available")
                    currentCity = "Location unavailable"
                    updateNotification()
                }
            }
        }
    }

    private fun checkLocationSettings() {
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        val client = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // Location settings are satisfied, start location updates
            startLocationUpdates()
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                Log.w(TAG, "Location settings not satisfied, but can be resolved")
                // For service, we can't show resolution dialog, but we can still try to get location
                startLocationUpdates()
            } else {
                Log.e(TAG, "Location settings not satisfied and cannot be resolved")
                // Still try to get location updates
                startLocationUpdates()
            }
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permissions not granted")
            return
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        Log.d(TAG, "Location updates started")
    }

    private fun createNotification(): Notification {
        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows current location and tracking status"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // Create intent to open the app when notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Trackee Driver Active")
            .setContentText("Current location: $currentCity")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation) // Using system location icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun getCityFromLocation(lat: Double, lng: Double): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // For Android 13+, use the new async method
                var cityName = "Unknown"
                val geocoder = Geocoder(this, Locale.getDefault())

                // Fallback to synchronous method for now
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                cityName = addresses?.firstOrNull()?.locality ?: "Unknown"
                cityName
            } else {
                // For older Android versions
                val geocoder = Geocoder(this, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                addresses?.firstOrNull()?.locality ?: "Unknown"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting city from location", e)
            "Unknown"
        }
    }

    private suspend fun sendToBackend(city: String, lat: Double, lon: Double, token: String, context: Context, retry: Boolean = true // <-- guard

    ) {
        try {
            val json = JSONObject().apply {
                put("lat", lat)
                put("lon", lon)
                put("city", city)
            }.toString()

            val client = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            Log.d(TAG, "Sending location update: $json")
/*
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Token: $token", Toast.LENGTH_SHORT).show()
            }*/
            val request = Request.Builder()
                .url("http://192.168.137.1:8080/api/tracking/setInfos")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Success: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                        Log.d(TAG, "Location sent successfully")
                    }
                    else if (response.code == 401 && retry) {
                        // Access token expired → refresh and retry once
                        val newToken = TokenManager.refreshAccessToken(context)
                        if (newToken != null) {
                            sendToBackend(city, lat, lon, newToken, context, retry = false)
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Token expired, please login again", Toast.LENGTH_SHORT).show()
                            }
                            Log.e(TAG, "Access token expired and refresh failed")
                        }
                    }


                    else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed: ${response.message}", Toast.LENGTH_SHORT).show()
                        }
                        Log.e(TAG, "Failed to send location: ${response.code} ${response.message}")
                    }
                }
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            Log.e(TAG, "Error sending location to backend", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Restart service if killed by system
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
        Log.d(TAG, "LocationService destroyed")
    }
}