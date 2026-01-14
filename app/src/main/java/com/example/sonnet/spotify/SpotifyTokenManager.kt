package com.example.sonnet.spotify

import android.content.Context
import android.util.Log
import com.example.sonnet.TokenManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Helper for managing Spotify tokens with automatic refresh
 */
object SpotifyTokenManager {
    
    private const val TAG = "SpotifyTokenManager"
    private val refreshMutex = Mutex()
    
    /**
     * Get a valid access token, refreshing if expired
     * @param context Application context
     * @return Valid access token or null if refresh failed
     */
    suspend fun getValidToken(context: Context): String? {
        // Check if token exists
        val currentToken = TokenManager.getToken(context)
        if (currentToken == null) {
            Log.w(TAG, "No access token available")
            return null
        }
        
        // Check if token is expired
        if (!TokenManager.isTokenExpired(context)) {
            return currentToken
        }
        
        // Token expired, try to refresh
        Log.d(TAG, "Access token expired, attempting refresh...")
        
        // Use mutex to prevent multiple simultaneous refresh attempts
        return refreshMutex.withLock {
            // Check again in case another coroutine just refreshed
            if (!TokenManager.isTokenExpired(context)) {
                return@withLock TokenManager.getToken(context)
            }
            
            val refreshToken = TokenManager.getRefreshToken(context)
            if (refreshToken == null) {
                Log.w(TAG, "No refresh token available, user needs to log in again")
                return@withLock null
            }
            
            try {
                val tokenResponse = SpotifyAuthRepository.getInstance().refreshAccessToken(refreshToken)
                
                if (tokenResponse != null) {
                    // Save new tokens
                    TokenManager.saveTokens(
                        context,
                        tokenResponse.access_token,
                        tokenResponse.refresh_token ?: refreshToken, // Use old refresh token if not provided
                        tokenResponse.expires_in
                    )
                    
                    Log.d(TAG, "Successfully refreshed access token")
                    return@withLock tokenResponse.access_token
                } else {
                    Log.e(TAG, "Failed to refresh token")
                    return@withLock null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing token: ${e.message}", e)
                return@withLock null
            }
        }
    }
}
