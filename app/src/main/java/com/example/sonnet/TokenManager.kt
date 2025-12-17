package com.example.sonnet

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple token manager using SharedPreferences
 * In production, use EncryptedSharedPreferences for security
 */
object TokenManager {
    
    private const val PREFS_NAME = "spotify_auth"
    private const val KEY_ACCESS_TOKEN = "access_token"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun saveToken(context: Context, token: String) {
        getPrefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, token)
            .apply()
    }
    
    fun getToken(context: Context): String? {
        return getPrefs(context).getString(KEY_ACCESS_TOKEN, null)
    }
    
    fun hasValidToken(context: Context): Boolean {
        return getToken(context) != null
    }
    
    fun clearToken(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_ACCESS_TOKEN)
            .apply()
    }
}
