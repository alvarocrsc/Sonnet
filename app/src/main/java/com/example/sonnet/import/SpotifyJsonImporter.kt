package com.example.sonnet.import

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.sonnet.firebase.ListeningDataManager
import com.example.sonnet.models.ImportedFile
import com.example.sonnet.models.ImportResult
import com.example.sonnet.models.ImportStatus
import com.example.sonnet.models.SingleFileImportResult
import com.example.sonnet.models.SpotifyJsonHistory
import com.example.sonnet.models.toListeningHistory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class SpotifyJsonImporter(private val context: Context) {
    
    private val gson = Gson()
    
    companion object {
        private const val TAG = "SpotifyJsonImporter"
    }
    
    /**
     * Extract metadata from a JSON file without importing to Firebase
     * Parses the file to get entry count, date range, and validity statistics
     * 
     * @param uri The URI of the selected JSON file
     * @return ImportedFile with metadata, or null if parsing fails
     */
    suspend fun getFileMetadata(uri: Uri): ImportedFile? = withContext(Dispatchers.IO) {
        return@withContext try {
            // Get file name and size
            val fileName = getFileName(uri)
            val fileSize = getFileSize(uri)
            
            Log.d(TAG, "Getting metadata for file: $fileName")
            
            // Read and parse JSON
            val jsonString = readJsonFile(uri)
            val entries = parseJson(jsonString)
            
            if (entries.isEmpty()) {
                Log.w(TAG, "No entries found in $fileName")
                return@withContext null
            }
            
            // Filter valid entries (>30s, has track name, has track URI)
            val validEntries = entries.filter { entry ->
                entry.msPlayed >= 30000 && 
                !entry.trackName.isNullOrBlank() && 
                !entry.trackUri.isNullOrBlank()
            }
            
            // Extract date range from timestamps
            val timestamps = entries.mapNotNull { 
                try {
                    it.ts
                } catch (e: Exception) {
                    null
                }
            }
            
            val dateRange = if (timestamps.isNotEmpty()) {
                val sortedTimestamps = timestamps.sorted()
                val first = sortedTimestamps.first().take(10) // Get just the date part
                val last = sortedTimestamps.last().take(10)
                "$first to $last"
            } else {
                "Unknown"
            }
            
            Log.d(TAG, "File metadata: $fileName - ${entries.size} entries (${validEntries.size} valid), $dateRange")
            
            // Generate unique file ID
            val fileId = java.util.UUID.randomUUID().toString()
            
            ImportedFile(
                uri = uri,
                fileName = fileName,
                fileSizeBytes = fileSize,
                entryCount = entries.size,
                validEntryCount = validEntries.size,
                dateRange = dateRange,
                status = ImportStatus.PENDING,
                sourceFileId = fileId
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file metadata: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get the file name from a URI
     */
    private fun getFileName(uri: Uri): String {
        var fileName = "Unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }
    
    /**
     * Get the file size in bytes from a URI
     */
    private fun getFileSize(uri: Uri): Long {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
        return size
    }
    
    /**
     * Read JSON file content from URI
     */
    private fun readJsonFile(uri: Uri): String {
        val stringBuilder = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line)
                }
            }
        }
        return stringBuilder.toString()
    }
    
    /**
     * Parse JSON string to list of SpotifyJsonHistory objects
     */
    private fun parseJson(jsonString: String): List<SpotifyJsonHistory> {
        return try {
            val listType = object : TypeToken<List<SpotifyJsonHistory>>() {}.type
            gson.fromJson(jsonString, listType) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Import a single file to Firebase
     * Reads, parses, converts, and saves listening history entries
     * 
     * @param file The ImportedFile to process
     * @param userId User's Spotify ID
     * @param onProgress Callback for progress updates (current, total, status message)
     * @return SingleFileImportResult with import statistics
     */
    suspend fun importSingleFile(
        file: ImportedFile,
        userId: String,
        onProgress: (current: Int, total: Int, status: String) -> Unit
    ): SingleFileImportResult = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting import of ${file.fileName}")
            
            // Step 1: Read file
            onProgress(0, file.validEntryCount, "Reading ${file.fileName}...")
            val jsonString = readJsonFile(file.uri)
            
            // Step 2: Parse JSON
            onProgress(0, file.validEntryCount, "Parsing ${file.fileName}...")
            val entries = parseJson(jsonString)
            
            if (entries.isEmpty()) {
                return@withContext SingleFileImportResult(
                    fileName = file.fileName,
                    success = false,
                    entriesSaved = 0,
                    error = "No entries found in file"
                )
            }
            
            // Step 3: Filter valid entries first
            onProgress(0, file.validEntryCount, "Filtering ${file.fileName}...")
            val validEntries = entries.filter { entry ->
                entry.msPlayed >= 30000 && 
                !entry.trackName.isNullOrBlank() && 
                !entry.trackUri.isNullOrBlank()
            }
            
            if (validEntries.isEmpty()) {
                return@withContext SingleFileImportResult(
                    fileName = file.fileName,
                    success = false,
                    entriesSaved = 0,
                    error = "No valid entries after filtering"
                )
            }
            
            // Step 4: Convert to ListeningHistory (images will be fetched lazily in UI)
            onProgress(0, validEntries.size, "Converting ${file.fileName}...")
            val historyList = validEntries.mapNotNull { entry ->
                entry.toListeningHistory(
                    userId = userId,
                    sourceFileId = file.sourceFileId
                )
            }
            
            if (historyList.isEmpty()) {
                Log.w(TAG, "No listening history converted from ${file.fileName}")
                return@withContext SingleFileImportResult(
                    fileName = file.fileName,
                    success = false,
                    entriesSaved = 0,
                    error = "Failed to convert entries"
                )
            }
            
            // Step 5: Batch save to Firebase
            val success = ListeningDataManager.getInstance()
                .batchSaveListeningHistory(userId, historyList) { current, total ->
                    onProgress(current, total, "Importing ${file.fileName}...")
                }
            
            if (success) {
                Log.d(TAG, "Successfully imported ${historyList.size} entries from ${file.fileName}")
                SingleFileImportResult(
                    fileName = file.fileName,
                    success = true,
                    entriesSaved = historyList.size
                )
            } else {
                SingleFileImportResult(
                    fileName = file.fileName,
                    success = false,
                    entriesSaved = 0,
                    error = "Failed to save to Firebase"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing ${file.fileName}: ${e.message}", e)
            SingleFileImportResult(
                fileName = file.fileName,
                success = false,
                entriesSaved = 0,
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Parse ISO 8601 timestamp to milliseconds
     */
    private fun parseTimestamp(timestampStr: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(timestampStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse timestamp: $timestampStr", e)
            System.currentTimeMillis()
        }
    }
    
    /**
     * Import multiple files sequentially and calculate statistics
     * Processes each file one by one with progress tracking and builds statistics incrementally
     * 
     * @param files List of ImportedFile objects to process
     * @param userId User's Spotify ID
     * @param onFileProgress Callback for individual file progress (fileIndex, current, total)
     * @param onOverallProgress Callback for overall progress (filesComplete, totalFiles)
     * @param calculateStats Whether to calculate statistics during import (default true)
     * @return ImportResult with overall statistics
     */
    suspend fun importMultipleFiles(
        files: List<ImportedFile>,
        userId: String,
        onFileProgress: (fileIndex: Int, current: Int, total: Int) -> Unit,
        onOverallProgress: (filesComplete: Int, totalFiles: Int) -> Unit,
        calculateStats: Boolean = true
    ): ImportResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var totalSaved = 0
        val errors = mutableListOf<String>()
        val fileResults = mutableListOf<SingleFileImportResult>()
        
        // Incremental statistics aggregation
        val artistStats = mutableMapOf<String, ArtistAggregation>()
        val albumStats = mutableMapOf<String, AlbumAggregation>()
        val trackStats = mutableMapOf<String, TrackAggregation>()
        var totalMinutes = 0
        var totalStreams = 0
        
        Log.d(TAG, "Starting import of ${files.size} files (calculateStats=$calculateStats)")
        
        files.forEachIndexed { index, file ->
            try {
                // Read and parse file
                val jsonString = readJsonFile(file.uri)
                val entries = parseJson(jsonString)
                val validEntries = entries.filter { entry ->
                    entry.msPlayed >= 30000 && 
                    !entry.trackName.isNullOrBlank() && 
                    !entry.trackUri.isNullOrBlank()
                }
                
                // Convert to listening history
                val historyList = validEntries.mapNotNull { entry ->
                    // Aggregate statistics if enabled
                    if (calculateStats && entry.artistName != null && entry.trackName != null) {
                        val minutes = (entry.msPlayed / 60000).toInt()
                        val timestamp = parseTimestamp(entry.ts)
                        totalMinutes += minutes
                        totalStreams++
                        
                        // Aggregate artist stats
                        val artistKey = entry.artistName!!
                        artistStats.getOrPut(artistKey) {
                            ArtistAggregation(
                                artistName = entry.artistName!!,
                                totalMinutes = 0,
                                totalStreams = 0,
                                firstPlayed = timestamp,
                                lastPlayed = timestamp
                            )
                        }.apply {
                            this.totalMinutes += minutes
                            this.totalStreams++
                            if (timestamp < this.firstPlayed) this.firstPlayed = timestamp
                            if (timestamp > this.lastPlayed) this.lastPlayed = timestamp
                        }
                        
                        // Aggregate album stats
                        if (entry.albumName != null) {
                            val albumKey = "${entry.artistName}:::${entry.albumName}"
                            albumStats.getOrPut(albumKey) {
                                AlbumAggregation(
                                    albumName = entry.albumName!!,
                                    artistName = entry.artistName!!,
                                    totalMinutes = 0,
                                    totalStreams = 0,
                                    firstPlayed = timestamp,
                                    lastPlayed = timestamp
                                )
                            }.apply {
                                this.totalMinutes += minutes
                                this.totalStreams++
                                if (timestamp < this.firstPlayed) this.firstPlayed = timestamp
                                if (timestamp > this.lastPlayed) this.lastPlayed = timestamp
                            }
                        }
                        
                        // Aggregate track stats
                        val trackKey = entry.trackUri!!
                        trackStats.getOrPut(trackKey) {
                            TrackAggregation(
                                trackName = entry.trackName!!,
                                trackUri = entry.trackUri!!,
                                artistName = entry.artistName!!,
                                albumName = entry.albumName,
                                totalMinutes = 0,
                                totalStreams = 0,
                                firstPlayed = timestamp,
                                lastPlayed = timestamp
                            )
                        }.apply {
                            this.totalMinutes += minutes
                            this.totalStreams++
                            if (timestamp < this.firstPlayed) this.firstPlayed = timestamp
                            if (timestamp > this.lastPlayed) this.lastPlayed = timestamp
                        }
                    }
                    
                    entry.toListeningHistory(
                        userId = userId,
                        sourceFileId = file.sourceFileId
                    )
                }
                
                // Save to Firebase
                val success = ListeningDataManager.getInstance()
                    .batchSaveListeningHistory(userId, historyList) { current, total ->
                        onFileProgress(index, current, total)
                    }
                
                val result = if (success) {
                    totalSaved += historyList.size
                    SingleFileImportResult(
                        fileName = file.fileName,
                        success = true,
                        entriesSaved = historyList.size
                    )
                } else {
                    SingleFileImportResult(
                        fileName = file.fileName,
                        success = false,
                        entriesSaved = 0,
                        error = "Failed to save to Firebase"
                    )
                }
                
                fileResults.add(result)
                
                if (result.success) {
                    Log.d(TAG, "File ${index + 1}/${files.size} completed: ${result.entriesSaved} entries saved")
                } else {
                    errors.add("${file.fileName}: ${result.error ?: "Unknown error"}")
                    Log.w(TAG, "File ${index + 1}/${files.size} failed: ${result.error}")
                }
            } catch (e: Exception) {
                errors.add("${file.fileName}: ${e.message ?: "Unknown error"}")
                Log.e(TAG, "Error processing file ${index + 1}/${files.size}: ${e.message}", e)
            }
            
            // Report overall progress
            onOverallProgress(index + 1, files.size)
        }
        
        // Save calculated statistics if enabled
        if (calculateStats && totalStreams > 0) {
            try {
                Log.d(TAG, "Saving calculated statistics: $totalStreams streams, $totalMinutes minutes, ${artistStats.size} artists")
                saveCalculatedStatistics(userId, artistStats, albumStats, trackStats, totalMinutes, totalStreams)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving statistics: ${e.message}", e)
                errors.add("Statistics calculation failed: ${e.message}")
            }
        }
        
        val processingTime = System.currentTimeMillis() - startTime
        val overallDateRange = calculateOverallDateRange(files)
        
        Log.d(TAG, "Import complete: $totalSaved entries from ${files.size} files in ${processingTime}ms")
        
        return@withContext ImportResult(
            success = errors.isEmpty(),
            totalFiles = files.size,
            filesProcessed = files.size,
            totalEntries = files.sumOf { it.entryCount },
            entriesSaved = totalSaved,
            errors = errors,
            overallDateRange = overallDateRange,
            processingTimeMs = processingTime
        )
    }
    
    /**
     * Merge calculated statistics with existing Firestore data
     * This allows incremental updates when importing multiple files
     */
    private suspend fun saveCalculatedStatistics(
        userId: String,
        artistStats: Map<String, ArtistAggregation>,
        albumStats: Map<String, AlbumAggregation>,
        trackStats: Map<String, TrackAggregation>,
        totalMinutes: Int,
        totalStreams: Int
    ) {
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        
        Log.d(TAG, "Merging statistics: ${artistStats.size} artists, ${albumStats.size} albums, ${trackStats.size} tracks")
        
        // Step 1: Fetch existing stats from Firestore
        val existingArtists = fetchExistingArtistStats(userId, firestore)
        val existingAlbums = fetchExistingAlbumStats(userId, firestore)
        val existingTracks = fetchExistingTrackStats(userId, firestore)
        val existingUserStats = fetchExistingUserStats(userId, firestore)
        
        Log.d(TAG, "Found existing: ${existingArtists.size} artists, ${existingAlbums.size} albums, ${existingTracks.size} tracks")
        
        // Step 2: Merge new stats with existing
        val mergedArtists = mergeArtistStats(existingArtists, artistStats)
        val mergedAlbums = mergeAlbumStats(existingAlbums, albumStats)
        val mergedTracks = mergeTrackStats(existingTracks, trackStats)
        
        // Step 3: Delete all old stats (we'll replace with merged)
        deleteAllStatsForUser(userId, firestore)
        
        // Step 4: Save merged stats with updated ranks
        val batch = firestore.batch()
        
        // Save merged user statistics
        val userStatsRef = firestore.collection("user_stats").document(userId)
        val mergedUserStats = hashMapOf(
            "userId" to userId,
            "totalListeningTimeMs" to ((existingUserStats["totalMinutes"] ?: 0) + totalMinutes) * 60000L,
            "totalStreams" to ((existingUserStats["totalStreams"] ?: 0) + totalStreams),
            "uniqueArtists" to mergedArtists.size,
            "uniqueTracks" to mergedTracks.size,
            "uniqueAlbums" to mergedAlbums.size,
            "lastUpdated" to System.currentTimeMillis()
        )
        batch.set(userStatsRef, mergedUserStats)
        
        // Save top 100 artists by total minutes
        val topArtists = mergedArtists.values
            .sortedByDescending { it.totalMinutes }
            .take(100)
        
        topArtists.forEachIndexed { index, artist ->
            val artistDoc = firestore.collection("artist_stats").document()
            val artistData = hashMapOf(
                "userId" to userId,
                "artistName" to artist.artistName,
                "imageUrl" to null,
                "totalListeningTimeMs" to (artist.totalMinutes * 60000L),
                "playCount" to artist.totalStreams,
                "rank" to (index + 1),
                "lastPlayed" to artist.lastPlayed,
                "lastUpdated" to System.currentTimeMillis()
            )
            batch.set(artistDoc, artistData)
        }
        
        // Save top 100 albums by total minutes
        val topAlbums = mergedAlbums.values
            .sortedByDescending { it.totalMinutes }
            .take(100)
        
        topAlbums.forEachIndexed { index, album ->
            val albumDoc = firestore.collection("album_stats").document()
            val albumData = hashMapOf(
                "userId" to userId,
                "albumName" to album.albumName,
                "artistName" to album.artistName,
                "imageUrl" to null,
                "totalListeningTimeMs" to (album.totalMinutes * 60000L),
                "playCount" to album.totalStreams,
                "rank" to (index + 1),
                "lastPlayed" to album.lastPlayed,
                "lastUpdated" to System.currentTimeMillis()
            )
            batch.set(albumDoc, albumData)
        }
        
        // Save top 100 tracks by total minutes
        val topTracks = mergedTracks.values
            .sortedByDescending { it.totalMinutes }
            .take(100)
        
        topTracks.forEachIndexed { index, track ->
            val trackDoc = firestore.collection("track_stats").document()
            val trackData = hashMapOf(
                "userId" to userId,
                "trackName" to track.trackName,
                "trackUri" to track.trackUri,
                "artistName" to track.artistName,
                "albumName" to (track.albumName ?: ""),
                "imageUrl" to null,
                "totalListeningTimeMs" to (track.totalMinutes * 60000L),
                "playCount" to track.totalStreams,
                "rank" to (index + 1),
                "lastPlayed" to track.lastPlayed,
                "lastUpdated" to System.currentTimeMillis()
            )
            batch.set(trackDoc, trackData)
        }
        
        // Commit batch
        batch.commit().await()
        Log.d(TAG, "Statistics merged and saved: ${topArtists.size} artists, ${topAlbums.size} albums, ${topTracks.size} tracks")
    }
    
    // Data classes for aggregation
    private data class ArtistAggregation(
        val artistName: String,
        var totalMinutes: Int,
        var totalStreams: Int,
        var firstPlayed: Long,
        var lastPlayed: Long
    )
    
    private data class AlbumAggregation(
        val albumName: String,
        val artistName: String,
        var totalMinutes: Int,
        var totalStreams: Int,
        var firstPlayed: Long,
        var lastPlayed: Long
    )
    
    private data class TrackAggregation(
        val trackName: String,
        val trackUri: String,
        val artistName: String,
        val albumName: String?,
        var totalMinutes: Int,
        var totalStreams: Int,
        var firstPlayed: Long,
        var lastPlayed: Long
    )
    
    /**
     * Fetch existing artist stats from Firestore
     */
    private suspend fun fetchExistingArtistStats(
        userId: String,
        firestore: com.google.firebase.firestore.FirebaseFirestore
    ): Map<String, ArtistAggregation> {
        return try {
            val snapshot = firestore.collection("artist_stats")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                val artistName = doc.getString("artistName") ?: return@mapNotNull null
                val totalMs = doc.getLong("totalListeningTimeMs") ?: 0L
                val playCount = doc.getLong("playCount")?.toInt() ?: 0
                val lastPlayed = doc.getLong("lastPlayed") ?: System.currentTimeMillis()
                
                artistName to ArtistAggregation(
                    artistName = artistName,
                    totalMinutes = (totalMs / 60000).toInt(),
                    totalStreams = playCount,
                    firstPlayed = lastPlayed, // We don't have firstPlayed in Firestore
                    lastPlayed = lastPlayed
                )
            }.toMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching existing artist stats: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * Fetch existing album stats from Firestore
     */
    private suspend fun fetchExistingAlbumStats(
        userId: String,
        firestore: com.google.firebase.firestore.FirebaseFirestore
    ): Map<String, AlbumAggregation> {
        return try {
            val snapshot = firestore.collection("album_stats")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                val albumName = doc.getString("albumName") ?: return@mapNotNull null
                val artistName = doc.getString("artistName") ?: return@mapNotNull null
                val albumKey = "$artistName:::$albumName"
                val totalMs = doc.getLong("totalListeningTimeMs") ?: 0L
                val playCount = doc.getLong("playCount")?.toInt() ?: 0
                val lastPlayed = doc.getLong("lastPlayed") ?: System.currentTimeMillis()
                
                albumKey to AlbumAggregation(
                    albumName = albumName,
                    artistName = artistName,
                    totalMinutes = (totalMs / 60000).toInt(),
                    totalStreams = playCount,
                    firstPlayed = lastPlayed,
                    lastPlayed = lastPlayed
                )
            }.toMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching existing album stats: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * Fetch existing track stats from Firestore
     */
    private suspend fun fetchExistingTrackStats(
        userId: String,
        firestore: com.google.firebase.firestore.FirebaseFirestore
    ): Map<String, TrackAggregation> {
        return try {
            val snapshot = firestore.collection("track_stats")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                val trackUri = doc.getString("trackUri") ?: return@mapNotNull null
                val trackName = doc.getString("trackName") ?: return@mapNotNull null
                val artistName = doc.getString("artistName") ?: return@mapNotNull null
                val albumName = doc.getString("albumName")
                val totalMs = doc.getLong("totalListeningTimeMs") ?: 0L
                val playCount = doc.getLong("playCount")?.toInt() ?: 0
                val lastPlayed = doc.getLong("lastPlayed") ?: System.currentTimeMillis()
                
                trackUri to TrackAggregation(
                    trackName = trackName,
                    trackUri = trackUri,
                    artistName = artistName,
                    albumName = albumName,
                    totalMinutes = (totalMs / 60000).toInt(),
                    totalStreams = playCount,
                    firstPlayed = lastPlayed,
                    lastPlayed = lastPlayed
                )
            }.toMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching existing track stats: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * Fetch existing user stats from Firestore
     */
    private suspend fun fetchExistingUserStats(
        userId: String,
        firestore: com.google.firebase.firestore.FirebaseFirestore
    ): Map<String, Int> {
        return try {
            val doc = firestore.collection("user_stats")
                .document(userId)
                .get()
                .await()
            
            if (doc.exists()) {
                mapOf(
                    "totalMinutes" to ((doc.getLong("totalListeningTimeMs") ?: 0L) / 60000).toInt(),
                    "totalStreams" to (doc.getLong("totalStreams") ?: 0L).toInt()
                )
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching existing user stats: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * Merge artist stats (add minutes and streams for same artist)
     */
    private fun mergeArtistStats(
        existing: Map<String, ArtistAggregation>,
        new: Map<String, ArtistAggregation>
    ): Map<String, ArtistAggregation> {
        val merged = existing.toMutableMap()
        
        new.forEach { (key, newStats) ->
            val existingStats = merged[key]
            if (existingStats != null) {
                // Merge: add minutes and streams
                existingStats.totalMinutes += newStats.totalMinutes
                existingStats.totalStreams += newStats.totalStreams
                // Update timestamps
                if (newStats.firstPlayed < existingStats.firstPlayed) {
                    existingStats.firstPlayed = newStats.firstPlayed
                }
                if (newStats.lastPlayed > existingStats.lastPlayed) {
                    existingStats.lastPlayed = newStats.lastPlayed
                }
            } else {
                // New artist
                merged[key] = newStats
            }
        }
        
        return merged
    }
    
    /**
     * Merge album stats (add minutes and streams for same album)
     */
    private fun mergeAlbumStats(
        existing: Map<String, AlbumAggregation>,
        new: Map<String, AlbumAggregation>
    ): Map<String, AlbumAggregation> {
        val merged = existing.toMutableMap()
        
        new.forEach { (key, newStats) ->
            val existingStats = merged[key]
            if (existingStats != null) {
                existingStats.totalMinutes += newStats.totalMinutes
                existingStats.totalStreams += newStats.totalStreams
                if (newStats.firstPlayed < existingStats.firstPlayed) {
                    existingStats.firstPlayed = newStats.firstPlayed
                }
                if (newStats.lastPlayed > existingStats.lastPlayed) {
                    existingStats.lastPlayed = newStats.lastPlayed
                }
            } else {
                merged[key] = newStats
            }
        }
        
        return merged
    }
    
    /**
     * Merge track stats (add minutes and streams for same track)
     */
    private fun mergeTrackStats(
        existing: Map<String, TrackAggregation>,
        new: Map<String, TrackAggregation>
    ): Map<String, TrackAggregation> {
        val merged = existing.toMutableMap()
        
        new.forEach { (key, newStats) ->
            val existingStats = merged[key]
            if (existingStats != null) {
                existingStats.totalMinutes += newStats.totalMinutes
                existingStats.totalStreams += newStats.totalStreams
                if (newStats.firstPlayed < existingStats.firstPlayed) {
                    existingStats.firstPlayed = newStats.firstPlayed
                }
                if (newStats.lastPlayed > existingStats.lastPlayed) {
                    existingStats.lastPlayed = newStats.lastPlayed
                }
            } else {
                merged[key] = newStats
            }
        }
        
        return merged
    }
    
    /**
     * Delete all existing stats for a user (before saving merged stats)
     */
    private suspend fun deleteAllStatsForUser(
        userId: String,
        firestore: com.google.firebase.firestore.FirebaseFirestore
    ) {
        try {
            // Delete artist stats
            val artistDocs = firestore.collection("artist_stats")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            artistDocs.documents.chunked(100).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            
            // Delete album stats
            val albumDocs = firestore.collection("album_stats")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            albumDocs.documents.chunked(100).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            
            // Delete track stats
            val trackDocs = firestore.collection("track_stats")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            trackDocs.documents.chunked(100).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            
            Log.d(TAG, "Deleted old stats before saving merged data")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting old stats: ${e.message}", e)
        }
    }
    
    /**
     * Calculate the overall date range from multiple files
     */
    private fun calculateOverallDateRange(files: List<ImportedFile>): String {
        val dateRanges = files.mapNotNull { file ->
            try {
                // Extract dates from "YYYY-MM-DD to YYYY-MM-DD" format
                val parts = file.dateRange.split(" to ")
                if (parts.size == 2) {
                    parts[0] to parts[1]
                } else null
            } catch (e: Exception) {
                null
            }
        }
        
        if (dateRanges.isEmpty()) return "Unknown"
        
        val firstDates = dateRanges.map { it.first }
        val lastDates = dateRanges.map { it.second }
        
        val earliestDate = firstDates.minOrNull() ?: "Unknown"
        val latestDate = lastDates.maxOrNull() ?: "Unknown"
        
        return "$earliestDate to $latestDate"
    }
}
