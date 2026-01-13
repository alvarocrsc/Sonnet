package com.example.sonnet.models.stats

/**
 * Overall user listening statistics
 */
data class UserStats(
    val userId: String = "",
    val totalListeningTimeMs: Long = 0,      // Total listening time in milliseconds
    val totalStreams: Int = 0,                // Total number of streams/plays
    val uniqueTracks: Int = 0,                // Number of unique tracks played
    val uniqueArtists: Int = 0,               // Number of unique artists played
    val uniqueAlbums: Int = 0,                // Number of unique albums played
    val lastUpdated: Long = System.currentTimeMillis()
)
