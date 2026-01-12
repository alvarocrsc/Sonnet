package com.example.sonnet.models

import android.net.Uri

/**
 * Represents a Spotify JSON file selected for import
 * Used to display file information before importing to Firebase
 */
data class ImportedFile(
    val uri: Uri,
    val fileName: String,
    val fileSizeBytes: Long,
    val entryCount: Int = 0,           // Total entries in JSON
    val validEntryCount: Int = 0,      // Entries that pass filters (>30s, has trackName)
    val dateRange: String = "",         // e.g., "2019-01-01 to 2019-12-31"
    val status: ImportStatus = ImportStatus.PENDING
)

/**
 * Status of an imported file
 */
enum class ImportStatus {
    PENDING,      // File selected but not yet imported
    PROCESSING,   // Currently being imported to Firebase
    COMPLETED,    // Successfully imported
    ERROR         // Failed to import
}
