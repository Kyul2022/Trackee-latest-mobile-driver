package com.example.trackee_driver

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object TokenManager {
    private const val PREFS_NAME = "trackee_prefs"
    private const val ACCESS_TOKEN_KEY = "access_token"
    private const val REFRESH_TOKEN_KEY = "refresh_token"

    fun saveAccessToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(ACCESS_TOKEN_KEY, token).apply()
    }

    fun getAccessToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(ACCESS_TOKEN_KEY, null)
    }

    fun saveRefreshToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(REFRESH_TOKEN_KEY, token).apply()
    }

    fun getRefreshToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(REFRESH_TOKEN_KEY, null)
    }

    suspend fun refreshAccessToken(context: Context): String? {
        val refreshToken = getRefreshToken(context) ?: return null
        val clientId = "microservices-client"
        val clientSecret = "xzqSYMI1pzumAj9BGD7BSKhI3IFAwy5e"

        val client = OkHttpClient()
        val body = "grant_type=refresh_token&refresh_token=$refreshToken"
            .toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("http://192.168.137.1:8180/realms/microservices/protocol/openid-connect/token")
            .addHeader(
                "Authorization",
                "Basic " + android.util.Base64.encodeToString(
                    "$clientId:$clientSecret".toByteArray(),
                    android.util.Base64.NO_WRAP
                )
            )
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body!!.string())
                val newAccessToken = json.getString("access_token")
                saveAccessToken(context, newAccessToken)  // update stored access token
                saveRefreshToken(context, json.optString("refresh_token"))
                newAccessToken
            }
        } catch (e: Exception) {
            Log.e("TokenManager", "Error refreshing token", e)
            null
        }
    }

}
