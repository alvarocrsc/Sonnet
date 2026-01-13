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
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

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
            
            // Step 3: Convert to ListeningHistory (filtering happens in toListeningHistory)
            onProgress(0, file.validEntryCount, "Converting ${file.fileName}...")
            val historyList = entries.mapNotNull { it.toListeningHistory(userId, file.sourceFileId) }
            
            if (historyList.isEmpty()) {
                return@withContext SingleFileImportResult(
                    fileName = file.fileName,
                    success = false,
                    entriesSaved = 0,
                    error = "No valid entries after filtering"
                )
            }
            
            // Step 4: Batch save to Firebase
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
     * Import multiple files sequentially
     * Processes each file one by one with progress tracking
     * 
     * @param files List of ImportedFile objects to process
     * @param userId User's Spotify ID
     * @param onFileProgress Callback for individual file progress (fileIndex, current, total)
     * @param onOverallProgress Callback for overall progress (filesComplete, totalFiles)
     * @return ImportResult with overall statistics
     */
    suspend fun importMultipleFiles(
        files: List<ImportedFile>,
        userId: String,
        onFileProgress: (fileIndex: Int, current: Int, total: Int) -> Unit,
        onOverallProgress: (filesComplete: Int, totalFiles: Int) -> Unit
    ): ImportResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var totalSaved = 0
        val errors = mutableListOf<String>()
        val fileResults = mutableListOf<SingleFileImportResult>()
        
        Log.d(TAG, "Starting import of ${files.size} files")
        
        files.forEachIndexed { index, file ->
            try {
                // Import this file
                val result = importSingleFile(file, userId) { current, total, status ->
                    onFileProgress(index, current, total)
                }
                
                fileResults.add(result)
                
                if (result.success) {
                    totalSaved += result.entriesSaved
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
