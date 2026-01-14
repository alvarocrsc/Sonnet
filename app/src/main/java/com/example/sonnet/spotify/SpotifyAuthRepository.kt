package com.example.sonnet.spotify

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Repository for handling Spotify OAuth token exchange
 */
class SpotifyAuthRepository private constructor() {
    
    private val tokenService: SpotifyTokenService
    
    companion object {
        private const val TAG = "SpotifyAuthRepository"
        private const val BASE_URL = "https://accounts.spotify.com/"
        
        @Volatile
        private var instance: SpotifyAuthRepository? = null
        
        fun getInstance(): SpotifyAuthRepository {
            return instance ?: synchronized(this) {
                instance ?: SpotifyAuthRepository().also { instance = it }
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
        
        tokenService = retrofit.create(SpotifyTokenService::class.java)
    }
    
    /**
     * Exchange authorization code for access token using PKCE
     * @param code Authorization code from Spotify
     * @param codeVerifier PKCE code verifier
     * @return TokenResponse if successful, null otherwise
     */
    suspend fun exchangeCodeForToken(code: String, codeVerifier: String): TokenResponse? {
        return try {
            Log.d(TAG, "Exchanging authorization code for access token...")
            
            val response = tokenService.exchangeCodeForToken(
                code = code,
                redirectUri = SpotifyConfig.REDIRECT_URI,
                clientId = SpotifyConfig.CLIENT_ID,
                codeVerifier = codeVerifier
            )
            
            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                Log.d(TAG, "Successfully exchanged code for access token")
                Log.d(TAG, "Token type: ${tokenResponse.token_type}")
                Log.d(TAG, "Expires in: ${tokenResponse.expires_in} seconds")
                Log.d(TAG, "Has refresh token: ${tokenResponse.refresh_token != null}")
                tokenResponse
            } else {
                Log.e(TAG, "Failed to exchange code: ${response.code()} - ${response.message()}")
                Log.e(TAG, "Error body: ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exchanging code for token: ${e.message}", e)
            null
        }
    }
    
    /**
     * Refresh access token using refresh token
     * @param refreshToken The refresh token
     * @return TokenResponse if successful, null otherwise
     */
    suspend fun refreshAccessToken(refreshToken: String): TokenResponse? {
        return try {
            Log.d(TAG, "Refreshing access token...")
            
            val response = tokenService.refreshToken(
                refreshToken = refreshToken,
                clientId = SpotifyConfig.CLIENT_ID
            )
            
            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                Log.d(TAG, "Successfully refreshed access token")
                Log.d(TAG, "New token expires in: ${tokenResponse.expires_in} seconds")
                tokenResponse
            } else {
                Log.e(TAG, "Failed to refresh token: ${response.code()} - ${response.message()}")
                Log.e(TAG, "Error body: ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing token: ${e.message}", e)
            null
        }
    }
}
