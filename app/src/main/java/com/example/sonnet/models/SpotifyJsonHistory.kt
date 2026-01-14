package com.example.sonnet.models

import com.google.firebase.Timestamp
import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.util.UUID

data class SpotifyJsonHistory(
    @SerializedName("ts")
    val ts: String,

    @SerializedName("ms_played")
    val msPlayed: Int,

    @SerializedName("spotify_track_uri")
    val trackUri: String?,

    @SerializedName("master_metadata_track_name")
    val trackName: String?,

    @SerializedName("master_metadata_album_artist_name")
    val artistName: String?,

    @SerializedName("master_metadata_album_album_name")
    val albumName: String?
)

fun SpotifyJsonHistory.toListeningHistory(
    userId: String, 
    sourceFileId: String
): ListeningHistory? {
    // Filter 1: Must have played the track during more than 30 seconds
    if (msPlayed < 30000) return null

    // Filter 2: Must have track URI
    if (trackUri == null) return null

    // Filter 3: Must have track name
    if (trackName.isNullOrBlank()) return null

    // Extract ID from "spotify:track:10AsRVRdU07cMAFHeGYO3c"
    val trackId = trackUri.split(":").getOrNull(2)

    // Parse ISO 8601 timestamp to Firebase Timestamp
    val instant = Instant.parse(ts)
    val timestampMs = instant.toEpochMilli()

    return ListeningHistory(
        id = generateListeningHistoryId(timestampMs),
        playedAt = Timestamp(instant.epochSecond, instant.nano),
        durationMs = msPlayed.toLong(),
        trackId = trackId,
        trackName = trackName,
        artistName = artistName ?: "Unknown Artist",
        albumName = albumName,
        source = "import",
        sourceFileId = sourceFileId
    )
}

/**
 * Generate document ID for subcollection structure
 * Format: timestamp_randomId (no userId prefix needed)
 */
fun generateListeningHistoryId(timestamp: Long): String {
    return "${timestamp}_${UUID.randomUUID().toString().take(8)}"
}