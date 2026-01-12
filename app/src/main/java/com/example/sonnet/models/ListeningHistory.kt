package com.example.sonnet.models

import com.google.firebase.Timestamp

data class ListeningHistory(
    // Document ID (timestamp_randomId)
    val id: String = "",

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