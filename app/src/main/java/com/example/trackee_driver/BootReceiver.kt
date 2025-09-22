package com.example.trackee_driver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.d(TAG, "Device booted, checking if user is logged in...")
            
            // Check if user is logged in
            val accessToken = TokenManager.getAccessToken(context)
            if (!accessToken.isNullOrEmpty()) {
                Log.d(TAG, "User is logged in, restarting location service...")
                
                val serviceIntent = Intent(context, LocationService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.d(TAG, "Location service started successfully after boot")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start location service after boot", e)
                }
            } else {
                Log.d(TAG, "User not logged in, skipping service start")
            }
        }
    }
}