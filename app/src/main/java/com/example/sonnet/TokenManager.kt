package com.example.sonnet

import android.content.Context
import android.content.SharedPreferences

/**
 * Token manager with refresh token support
 * In production, use EncryptedSharedPreferences for security
 */
object TokenManager {
    
    private const val PREFS_NAME = "spotify_auth"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_TOKEN_EXPIRY = "token_expiry"
    private const val KEY_USER_ID = "user_id"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun saveToken(context: Context, token: String) {
        getPrefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, token)
            .apply()
    }
    
    fun saveTokens(context: Context, accessToken: String, refreshToken: String?, expiresIn: Int) {
        val expiryTime = System.currentTimeMillis() + (expiresIn * 1000L)
        getPrefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_TOKEN_EXPIRY, expiryTime)
            .apply()
    }
    
    fun getToken(context: Context): String? {
        return getPrefs(context).getString(KEY_ACCESS_TOKEN, null)
    }
    
    fun getRefreshToken(context: Context): String? {
        return getPrefs(context).getString(KEY_REFRESH_TOKEN, null)
    }
    
    fun isTokenExpired(context: Context): Boolean {
        val expiryTime = getPrefs(context).getLong(KEY_TOKEN_EXPIRY, 0)
        // Consider expired if within 5 minutes of expiry to avoid edge cases
        return System.currentTimeMillis() >= (expiryTime - 300000)
    }
    
    fun saveUserId(context: Context, userId: String) {
        getPrefs(context).edit()
            .putString(KEY_USER_ID, userId)
            .apply()
    }
    
    fun getUserId(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_ID, null)
    }
    
    fun hasValidToken(context: Context): Boolean {
        return getToken(context) != null
    }
    
    fun clearToken(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRY)
            .remove(KEY_USER_ID)
            .apply()
    }
}
