package com.example.sonnet.spotify

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

/**
 * Retrofit interface for Spotify Web API calls
 */
interface SpotifyApiService {
    
    @GET("v1/me")
    suspend fun getCurrentUserProfile(
        @Header("Authorization") authorization: String
    ): Response<SpotifyUserResponse>
}
