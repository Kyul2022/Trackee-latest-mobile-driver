package com.example.trackee_driver

import android.content.Context
import android.content.SharedPreferences

object TokenManager {
    private const val PREFS_NAME = "trackee_prefs"
    private const val TOKEN_KEY = "auth_token"

    fun saveToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(TOKEN_KEY, token).apply()
    }

    fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(TOKEN_KEY, null)
    }
}
