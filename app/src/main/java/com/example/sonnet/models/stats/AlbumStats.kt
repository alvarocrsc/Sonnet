package com.example.sonnet.models.stats

/**
 * Album listening statistics
 */
data class AlbumStats(
    val id: String = "",                      // Document ID: {userId}_{albumId}
    val userId: String = "",
    val albumId: String = "",
    val albumName: String = "",
    val artistName: String = "",
    val imageUrl: String? = null,
    val totalListeningTimeMs: Long = 0,       // Total listening time in milliseconds
    val playCount: Int = 0,                   // Number of streams/plays
    val rank: Int = 0,                        // Ranking position (1 = most listened)
    val lastPlayed: Long = 0,                 // Timestamp of last play
    val lastUpdated: Long = System.currentTimeMillis()
)
