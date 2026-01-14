package com.example.sonnet.spotify

import com.google.gson.annotations.SerializedName

/**
 * Response models for Spotify Web API
 */

data class SpotifyUserResponse(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("display_name")
    val displayName: String?,
    
    @SerializedName("email")
    val email: String?,
    
    @SerializedName("country")
    val country: String?,
    
    @SerializedName("product")
    val product: String?,
    
    @SerializedName("images")
    val images: List<SpotifyImage>?,
    
    @SerializedName("followers")
    val followers: Followers?
)

data class SpotifyImage(
    @SerializedName("url")
    val url: String,
    
    @SerializedName("height")
    val height: Int?,
    
    @SerializedName("width")
    val width: Int?
)

data class Followers(
    @SerializedName("total")
    val total: Int
)
/**
 * Track response from Spotify API
 */
data class SpotifyTrackResponse(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("album")
    val album: SpotifyAlbumInfo,
    
    @SerializedName("artists")
    val artists: List<SpotifyArtistInfo>
)

data class SpotifyAlbumInfo(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("images")
    val images: List<SpotifyImage>
)

data class SpotifyArtistInfo(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String
)

/**
 * Artist full response from Spotify API
 */
data class SpotifyArtistResponse(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("images")
    val images: List<SpotifyImage>
)