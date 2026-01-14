package com.example.sonnet.spotify

import android.content.Context
import android.util.Log
import com.example.sonnet.TokenManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Service to fetch and cache artist images from Spotify API
 */
class SpotifyImageLoader(private val context: Context) {
    
    private val apiService: SpotifyApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.spotify.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SpotifyApiService::class.java)
    }
    
    private val firestore = FirebaseFirestore.getInstance()
    
    companion object {
        private const val TAG = "SpotifyImageLoader"
        private val imageCache = mutableMapOf<String, String?>()
    }
    
    /**
     * Get artist image URL by artist name, fetching from Spotify API if needed
     * Uses memory cache and Firestore cache to minimize API calls
     * Automatically refreshes expired tokens
     */
    suspend fun getArtistImageUrl(artistName: String): String? = withContext(Dispatchers.IO) {
        try {
            // Use artist name as cache key
            val cacheKey = artistName.lowercase()
            
            // Check memory cache first
            if (imageCache.containsKey(cacheKey)) {
                return@withContext imageCache[cacheKey]
            }
            
            // Check if we already have it in Firestore
            val cachedUrl = getImageFromFirestore(cacheKey, "artist_images")
            if (cachedUrl != null) {
                imageCache[cacheKey] = cachedUrl
                return@withContext cachedUrl
            }
            
            // Get valid token (refreshes if expired)
            val token = SpotifyTokenManager.getValidToken(context)
            if (token == null) {
                Log.w(TAG, "Unable to get valid access token for fetching artist images")
                return@withContext null
            }
            
            // Search for artist by name with exact matching
            val searchResponse = apiService.search(
                query = "\"$artistName\"", // Use quotes for exact match
                type = "artist",
                limit = 5, // Get top 5 to find exact match
                authorization = "Bearer $token"
            )
            
            if (searchResponse.isSuccessful && searchResponse.body() != null) {
                // Find exact match (case-insensitive)
                val artist = searchResponse.body()?.artists?.items?.find { 
                    it.name.equals(artistName, ignoreCase = true)
                } ?: searchResponse.body()?.artists?.items?.firstOrNull() // Fallback to first result
                
                val imageUrl = artist?.images?.firstOrNull()?.url
                
                // Cache in memory and Firestore
                imageCache[cacheKey] = imageUrl
                saveImageToFirestore(cacheKey, "artist_images", imageUrl)
                
                if (artist != null) {
                    Log.d(TAG, "Fetched image for artist: $artistName (matched: ${artist.name})")
                } else {
                    Log.d(TAG, "No results for artist: $artistName")
                }
                return@withContext imageUrl
            } else {
                Log.w(TAG, "Failed to fetch artist '$artistName': ${searchResponse.code()}")
                return@withContext null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching artist image for $artistName: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Get album image URL by album name and artist name, fetching from Spotify API if needed
     * Uses memory cache and Firestore cache to minimize API calls
     * Automatically refreshes expired tokens
     */
    suspend fun getAlbumImageUrl(albumName: String, artistName: String): String? = withContext(Dispatchers.IO) {
        try {
            // Use album+artist as cache key
            val cacheKey = "${albumName.lowercase()}_${artistName.lowercase()}"
            
            // Check memory cache first
            if (imageCache.containsKey(cacheKey)) {
                return@withContext imageCache[cacheKey]
            }
            
            // Check if we already have it in Firestore
            val cachedUrl = getImageFromFirestore(cacheKey, "album_images")
            if (cachedUrl != null) {
                imageCache[cacheKey] = cachedUrl
                return@withContext cachedUrl
            }
            
            // Get valid token (refreshes if expired)
            val token = SpotifyTokenManager.getValidToken(context)
            if (token == null) {
                Log.w(TAG, "Unable to get valid access token for fetching album images")
                return@withContext null
            }
            
            // Search for album by name and artist with exact matching
            val searchResponse = apiService.search(
                query = "album:\"$albumName\" artist:\"$artistName\"", // Use quotes for exact match
                type = "album",
                limit = 5, // Get top 5 to find exact match
                authorization = "Bearer $token"
            )
            
            if (searchResponse.isSuccessful && searchResponse.body() != null) {
                // Find exact match (case-insensitive)
                val album = searchResponse.body()?.albums?.items?.find { 
                    it.name.equals(albumName, ignoreCase = true)
                } ?: searchResponse.body()?.albums?.items?.firstOrNull() // Fallback to first result
                
                val imageUrl = album?.images?.firstOrNull()?.url
                
                // Cache in memory and Firestore
                imageCache[cacheKey] = imageUrl
                saveImageToFirestore(cacheKey, "album_images", imageUrl)
                
                if (album != null) {
                    Log.d(TAG, "Fetched image for album: $albumName by $artistName (matched: ${album.name})")
                } else {
                    Log.d(TAG, "No results for album: $albumName by $artistName")
                }
                return@withContext imageUrl
            } else {
                Log.w(TAG, "Failed to fetch album '$albumName' by '$artistName': ${searchResponse.code()}")
                return@withContext null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching album image for $albumName by $artistName: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Get image URL from Firestore cache
     */
    private suspend fun getImageFromFirestore(id: String, collection: String): String? {
        return try {
            val doc = firestore.collection(collection)
                .document(id)
                .get()
                .await()
            
            doc.getString("imageUrl")
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Save image URL to Firestore cache
     */
    private suspend fun saveImageToFirestore(id: String, collection: String, imageUrl: String?) {
        try {
            firestore.collection(collection)
                .document(id)
                .set(mapOf(
                    "imageUrl" to imageUrl,
                    "cachedAt" to System.currentTimeMillis()
                ))
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error caching image URL: ${e.message}")
        }
    }
}
