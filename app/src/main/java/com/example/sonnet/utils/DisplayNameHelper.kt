package com.example.sonnet.utils

import android.content.Context
import com.example.sonnet.TokenManager
import com.example.sonnet.firebase.FirebaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper object to manage display name fetching and caching
 * Consolidates duplicate logic across HomeManager, StatsManager, and ProfileManager
 */
object DisplayNameHelper {
    
    /**
     * Fetches user's display name from SharedPreferences cache or Firebase
     * @param context Android context for SharedPreferences access
     * @param userId User's Spotify ID for Firebase lookup
     * @param defaultName Fallback name if display name cannot be retrieved
     * @return User's display name or defaultName if not available
     */
    suspend fun getDisplayName(
        context: Context,
        userId: String,
        defaultName: String = "User"
    ): String {
        // Try to get from SharedPreferences cache first (fast)
        var displayName = TokenManager.getDisplayName(context)
        
        if (displayName == null) {
            // Fetch from Firebase for existing users who don't have cached name
            val user = withContext(Dispatchers.IO) {
                FirebaseManager.getInstance().getUser(userId)
            }
            displayName = user?.displayName
            
            // Cache to SharedPreferences for future instant access
            if (displayName != null) {
                TokenManager.saveDisplayName(context, displayName)
            }
        }
        
        return displayName ?: defaultName
    }
}
