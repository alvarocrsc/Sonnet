package com.example.sonnet.spotify

import android.util.Log
import com.example.sonnet.models.SpotifyUser
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Repository class to handle Spotify Web API calls
 */
class SpotifyRepository private constructor() {
    
    private val apiService: SpotifyApiService
    
    companion object {
        private const val TAG = "SpotifyRepository"
        private const val BASE_URL = "https://api.spotify.com/"
        
        @Volatile
        private var instance: SpotifyRepository? = null
        
        fun getInstance(): SpotifyRepository {
            return instance ?: synchronized(this) {
                instance ?: SpotifyRepository().also { instance = it }
            }
        }
    }
    
    init {
        // Set up logging interceptor
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        // Create OkHttp client
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        
        // Create Retrofit instance
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        apiService = retrofit.create(SpotifyApiService::class.java)
    }
    
    /**
     * Fetch current user's profile from Spotify Web API
     * @param accessToken Spotify access token
     * @return SpotifyUser if successful, null otherwise
     */
    suspend fun fetchUserProfile(accessToken: String): SpotifyUser? {
        return try {
            val response = apiService.getCurrentUserProfile("Bearer $accessToken")
            
            if (response.isSuccessful && response.body() != null) {
                val userResponse = response.body()!!
                
                // Convert Spotify API response to our SpotifyUser model
                val spotifyUser = SpotifyUser(
                    id = userResponse.id,
                    displayName = userResponse.displayName ?: "Unknown User",
                    email = userResponse.email,
                    country = userResponse.country,
                    product = userResponse.product,
                    profileImageUrl = userResponse.images?.firstOrNull()?.url,
                    followers = userResponse.followers?.total ?: 0,
                    createdAt = System.currentTimeMillis(),
                    lastUpdated = System.currentTimeMillis()
                )
                
                Log.d(TAG, "Successfully fetched user profile: ${spotifyUser.displayName}")
                spotifyUser
            } else {
                Log.e(TAG, "Failed to fetch user profile: ${response.code()} - ${response.message()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user profile: ${e.message}", e)
            null
        }
    }
}
