package com.example.sonnet.models.stats

/**
 * Track listening statistics
 */
data class TrackStats(
    val id: String = "",                      // Document ID: {userId}_{trackId}
    val userId: String = "",
    val trackId: String = "",
    val trackName: String = "",
    val artistName: String = "",
    val albumName: String = "",
    val durationMs: Long = 0,                 // Track duration in milliseconds
    val totalListeningTimeMs: Long = 0,       // Total listening time in milliseconds
    val playCount: Int = 0,                   // Number of streams/plays
    val rank: Int = 0,                        // Ranking position (1 = most listened)
    val lastPlayed: Long = 0,                 // Timestamp of last play
    val lastUpdated: Long = System.currentTimeMillis()
)
