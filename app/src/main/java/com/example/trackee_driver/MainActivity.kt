package com.example.trackee_driver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.example.trackee_driver.ui.theme.TrackeeDriverTheme

class MainActivity : ComponentActivity() {

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startLocationService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TrackeeDriverTheme {
                var isLoggedIn by remember { mutableStateOf(false) }
                var token by remember { mutableStateOf("") }

                if (isLoggedIn) {
                    DashboardScreen() { requestLocationPermissions() }
                } else {
                    LoginScreen { receivedToken ->
                        token = receivedToken
                        isLoggedIn = true
                        TokenManager.saveToken(this@MainActivity, receivedToken)
                        requestLocationPermissions()
                    }
                }
            }
        }
    }

    private fun requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.FOREGROUND_SERVICE
                )
            )
        } else {
            startLocationService()
        }
    }

    private fun startLocationService() {
        Intent(this, LocationService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }
}
