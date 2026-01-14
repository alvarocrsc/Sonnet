package com.example.sonnet.spotify

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for Spotify Web API calls
 */
interface SpotifyApiService {
    
    @GET("v1/me")
    suspend fun getCurrentUserProfile(
        @Header("Authorization") authorization: String
    ): Response<SpotifyUserResponse>
    
    @GET("v1/tracks/{id}")
    suspend fun getTrack(
        @Path("id") trackId: String,
        @Header("Authorization") authorization: String
    ): Response<SpotifyTrackResponse>
    
    @GET("v1/artists/{id}")
    suspend fun getArtist(
        @Path("id") artistId: String,
        @Header("Authorization") authorization: String
    ): Response<SpotifyArtistResponse>
    
    @GET("v1/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String,
        @Query("limit") limit: Int = 1,
        @Header("Authorization") authorization: String
    ): Response<SpotifySearchResponse>
}
