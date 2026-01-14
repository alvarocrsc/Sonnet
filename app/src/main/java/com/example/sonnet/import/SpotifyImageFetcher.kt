package com.example.sonnet.import

import android.util.Log
import com.example.sonnet.TokenManager
import com.example.sonnet.spotify.SpotifyApiService
import kotlinx.coroutines.delay
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Helper class to fetch images from Spotify API during import
 * Implements caching and rate limiting to avoid excessive API calls
 */
class SpotifyImageFetcher(private val context: android.content.Context) {
    
    private val apiService: SpotifyApiService
    private val imageCache = mutableMapOf<String, ImageData>()
    private var unauthorizedCount = 0
    
    companion object {
        private const val TAG = "SpotifyImageFetcher"
        private const val SPOTIFY_API_BASE_URL = "https://api.spotify.com/"
        private const val RATE_LIMIT_DELAY_MS = 200L // Conservative delay to avoid 429 errors
    }
    
    data class ImageData(
        val albumImageUrl: String? = null,
        val artistImageUrl: String? = null
    )
    
    init {
        val retrofit = Retrofit.Builder()
            .baseUrl(SPOTIFY_API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        apiService = retrofit.create(SpotifyApiService::class.java)
    }
    
    /**
     * Fetch images for a track (album cover and artist image)
     * Uses caching to avoid duplicate API calls
     * 
     * @param trackId Spotify track ID
     * @return ImageData with album and artist image URLs, or null on error
     */
    suspend fun fetchImagesForTrack(trackId: String): ImageData? {
        // Check cache first
        if (imageCache.containsKey(trackId)) {
            return imageCache[trackId]
        }
        
        return try {
            // Get access token
            val accessToken = TokenManager.getToken(context)
            if (accessToken.isNullOrEmpty()) {
                Log.w(TAG, "No access token available")
                return null
            }
            
            // Rate limiting to avoid hitting API limits
            delay(RATE_LIMIT_DELAY_MS)
            
            // Fetch track info from Spotify API
            val response = apiService.getTrack(trackId, "Bearer $accessToken")
            
            if (response.isSuccessful && response.body() != null) {
                val track = response.body()!!
                
                // Get album image (prefer medium size - typically 300x300)
                val albumImageUrl = track.album.images
                    .sortedByDescending { it.height ?: 0 }
                    .getOrNull(1)?.url // Index 1 is usually medium size
                    ?: track.album.images.firstOrNull()?.url
                
                // Get artist ID for fetching artist image
                val artistId = track.artists.firstOrNull()?.id
                
                // Fetch artist image if we have an artist ID
                val artistImageUrl = if (artistId != null) {
                    fetchArtistImage(artistId, accessToken)
                } else null
                
                val imageData = ImageData(
                    albumImageUrl = albumImageUrl,
                    artistImageUrl = artistImageUrl
                )
                
                // Cache the result
                imageCache[trackId] = imageData
                
                Log.d(TAG, "Fetched images for track $trackId: album=${albumImageUrl != null}, artist=${artistImageUrl != null}")
                imageData
            } else {
                if (response.code() == 401) {
                    unauthorizedCount++
                    if (unauthorizedCount == 1) {
                        Log.e(TAG, "TOKEN EXPIRED: Spotify returned 401 Unauthorized. Please log out and log back in to refresh your token.")
                    }
                }
                Log.w(TAG, "Failed to fetch track info: ${response.code()} ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching images for track $trackId: ${e.message}", e)
            null
        }
    }
    
    /**
     * Fetch artist image from Spotify API
     */
    private suspend fun fetchArtistImage(artistId: String, accessToken: String): String? {
        return try {
            delay(RATE_LIMIT_DELAY_MS)
            
            val response = apiService.getArtist(artistId, "Bearer $accessToken")
            
            if (response.isSuccessful && response.body() != null) {
                val artist = response.body()!!
                // Prefer medium size artist image
                artist.images
                    .sortedByDescending { it.height ?: 0 }
                    .getOrNull(1)?.url
                    ?: artist.images.firstOrNull()?.url
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching artist image: ${e.message}", e)
            null
        }
    }
    
    /**
     * Clear the image cache
     */
    fun clearCache() {
        imageCache.clear()
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): String {
        return "Cache size: ${imageCache.size} tracks"
    }
    
    fun hasTokenExpired(): Boolean {
        return unauthorizedCount > 0
    }
}
