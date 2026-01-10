package com.example.sonnet.models

import com.google.firebase.Timestamp

data class ListeningHistory(
    // Indexation for Firebase and User Data
    val id: String = "",
    val userId: String = "",

    // Listening Data
    val playedAt: Timestamp = Timestamp.now(),
    val durationMs: Long = 0,

    // Track Data
    val trackId: String? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,

    // Source Data
    val source: String = "import"
)