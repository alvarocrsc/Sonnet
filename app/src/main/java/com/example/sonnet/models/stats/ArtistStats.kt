package com.example.sonnet.models.stats

/**
 * Artist listening statistics
 */
data class ArtistStats(
    val id: String = "",                      // Document ID: {userId}_{artistId}
    val userId: String = "",
    val artistId: String = "",
    val artistName: String = "",
    val imageUrl: String? = null,
    val totalListeningTimeMs: Long = 0,       // Total listening time in milliseconds
    val playCount: Int = 0,                   // Number of streams/plays
    val rank: Int = 0,                        // Ranking position (1 = most listened)
    val lastPlayed: Long = 0,                 // Timestamp of last play
    val lastUpdated: Long = System.currentTimeMillis()
)
