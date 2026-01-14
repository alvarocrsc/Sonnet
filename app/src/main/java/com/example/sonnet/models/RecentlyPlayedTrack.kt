package com.example.sonnet.models

data class RecentlyPlayedTrack(
    val trackName: String = "",
    val artistName: String = "",
    val albumName: String = "",
    val playedAt: Long = 0L, // Timestamp in milliseconds
    val imageUrl: String? = null
)
