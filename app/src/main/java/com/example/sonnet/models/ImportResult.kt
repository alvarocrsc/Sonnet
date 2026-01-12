package com.example.sonnet.models

/**
 * Result of importing multiple Spotify JSON files
 * Contains statistics and status information about the import operation
 */
data class ImportResult(
    val success: Boolean,                      // True if all files imported successfully
    val totalFiles: Int,                       // Number of files attempted
    val filesProcessed: Int,                   // Number of files actually processed
    val totalEntries: Int,                     // Total entries across all files
    val entriesSaved: Int,                     // Number of entries saved to Firebase
    val errors: List<String> = emptyList(),    // List of error messages, if any
    val overallDateRange: String = "",         // e.g., "2019-01-01 to 2024-12-31"
    val processingTimeMs: Long = 0             // Time taken to process all files
)

/**
 * Result of importing a single file
 * Used internally during multi-file imports
 */
data class SingleFileImportResult(
    val fileName: String,
    val success: Boolean,
    val entriesSaved: Int,
    val error: String? = null
)
