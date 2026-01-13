package com.example.sonnet.import

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sonnet.R
import com.example.sonnet.TokenManager
import com.example.sonnet.firebase.ListeningDataManager
import com.example.sonnet.models.ImportStatus
import com.example.sonnet.models.ImportedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportActivity : ComponentActivity() {

    private lateinit var backButton: ImageView
    private lateinit var uploadContainer: View
    private lateinit var filesRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var dateRangeText: TextView
    private lateinit var adapter: ImportFileAdapter
    private lateinit var importer: SpotifyJsonImporter

    private var isImporting = false
    
    companion object {
        private const val PREFS_NAME = "import_history"
        private const val KEY_IMPORTED_FILES = "imported_files"
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            handleFileSelection(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.decorView.setBackgroundColor(android.graphics.Color.parseColor("#121212"))
        setContentView(R.layout.activity_import)

        initializeViews()
        setupRecyclerView()
        setupClickListeners()
        
        importer = SpotifyJsonImporter(this)
        
        // Load previously imported files
        loadImportedFiles()
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.back_button)
        uploadContainer = findViewById(R.id.upload_files_container)
        filesRecyclerView = findViewById(R.id.imported_files_recycler)
        emptyStateText = findViewById(R.id.empty_state_text)
        dateRangeText = findViewById(R.id.date_range_text)
    }

    private fun setupRecyclerView() {
        adapter = ImportFileAdapter { position, file ->
            handleDeleteFile(position, file)
        }
        
        filesRecyclerView.layoutManager = LinearLayoutManager(this)
        filesRecyclerView.adapter = adapter
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            if (isImporting) {
                Toast.makeText(this, "Import in progress, please wait", Toast.LENGTH_SHORT).show()
            } else {
                finish()
            }
        }

        uploadContainer.setOnClickListener {
            if (!isImporting) {
                filePickerLauncher.launch(arrayOf("application/json"))
            } else {
                Toast.makeText(this, "Import in progress, please wait", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleFileSelection(uris: List<Uri>) {
        lifecycleScope.launch {
            try {
                // Extract metadata for all selected files
                val newFiles = mutableListOf<ImportedFile>()
                
                withContext(Dispatchers.IO) {
                    uris.forEach { uri ->
                        val metadata = importer.getFileMetadata(uri)
                        if (metadata != null) {
                            newFiles.add(metadata)
                        }
                    }
                }

                // Check for duplicates before adding
                val existingFiles = adapter.getFiles()
                val duplicates = mutableListOf<String>()
                val filesToAdd = mutableListOf<ImportedFile>()
                
                newFiles.forEach { newFile ->
                    val isDuplicate = existingFiles.any { it.fileName == newFile.fileName }
                    if (isDuplicate) {
                        duplicates.add(newFile.fileName)
                    } else {
                        filesToAdd.add(newFile)
                    }
                }
                
                // Show warning if duplicates found
                if (duplicates.isNotEmpty()) {
                    val message = if (duplicates.size == 1) {
                        "File \"${duplicates[0]}\" has already been imported"
                    } else {
                        "${duplicates.size} files have already been imported"
                    }
                    Toast.makeText(this@ImportActivity, message, Toast.LENGTH_LONG).show()
                }
                
                // Only add non-duplicate files
                if (filesToAdd.isEmpty()) {
                    return@launch
                }

                // Add files to adapter with PENDING status
                filesToAdd.forEach { file ->
                    adapter.addFile(file)
                }

                // Save to persistence
                saveImportedFiles()
                
                // Update UI state
                updateEmptyState()

                // Start import automatically for newly added files
                if (filesToAdd.isNotEmpty()) {
                    startImport(filesToAdd)
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@ImportActivity,
                    "Error loading files: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startImport(filesToImport: List<ImportedFile>) {
        lifecycleScope.launch {
            isImporting = true
            
            try {
                val userId = TokenManager.getUserId(this@ImportActivity)
                if (userId == null) {
                    Toast.makeText(
                        this@ImportActivity,
                        "User not authenticated",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Get all files from adapter to find indices
                val allFiles = adapter.getFiles()
                
                val result = withContext(Dispatchers.IO) {
                    importer.importMultipleFiles(
                        files = filesToImport,
                        userId = userId,
                        onFileProgress = { fileIndex, current, total ->
                            // Find the real index in the adapter for this file
                            val realIndex = allFiles.indexOfFirst { it.uri == filesToImport[fileIndex].uri }
                            if (realIndex != -1) {
                                // Update status to PROCESSING for current file
                                launch(Dispatchers.Main) {
                                    adapter.updateFileStatus(realIndex, ImportStatus.PROCESSING)
                                }
                            }
                        },
                        onOverallProgress = { filesComplete, totalFiles ->
                            // Optional: Update overall progress UI if needed
                        }
                    )
                }

                // Update all file statuses based on final result
                filesToImport.forEachIndexed { index, file ->
                    val realIndex = allFiles.indexOfFirst { it.uri == file.uri }
                    if (realIndex != -1) {
                        val hasError = result.errors.any { it.startsWith(file.fileName) }
                        val status = if (hasError) ImportStatus.ERROR else ImportStatus.COMPLETED
                        adapter.updateFileStatus(realIndex, status)
                    }
                }
                
                // Show overall date range
                if (result.success && result.overallDateRange.isNotEmpty()) {
                    dateRangeText.text = result.overallDateRange
                    dateRangeText.visibility = View.VISIBLE
                }

                // Show result message
                val message = if (result.success) {
                    "Successfully imported ${result.entriesSaved} entries"
                } else {
                    "Import completed with ${result.errors.size} error(s)"
                }
                Toast.makeText(this@ImportActivity, message, Toast.LENGTH_LONG).show()
                
                // Save updated statuses
                saveImportedFiles()

            } catch (e: Exception) {
                Toast.makeText(
                    this@ImportActivity,
                    "Import failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isImporting = false
            }
        }
    }

    private fun handleDeleteFile(position: Int, file: ImportedFile) {
        if (isImporting) {
            Toast.makeText(this, "Cannot delete files during import", Toast.LENGTH_SHORT).show()
            return
        }

        // Show confirmation dialog
        AlertDialog.Builder(this)
            .setTitle("Delete imported file?")
            .setMessage("This will delete \"${file.fileName}\" from the import history and remove all ${file.validEntryCount} listening entries from your database. This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteFileAndData(position, file)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteFileAndData(position: Int, file: ImportedFile) {
        lifecycleScope.launch {
            try {
                // Show progress
                Toast.makeText(
                    this@ImportActivity,
                    "Deleting ${file.validEntryCount} entries...",
                    Toast.LENGTH_SHORT
                ).show()
                
                val userId = TokenManager.getUserId(this@ImportActivity)
                if (userId == null) {
                    Toast.makeText(
                        this@ImportActivity,
                        "User not authenticated",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                
                // Delete from Firestore
                val success = withContext(Dispatchers.IO) {
                    ListeningDataManager.getInstance()
                        .deleteListeningHistoryByFileId(userId, file.sourceFileId)
                }
                
                if (success) {
                    // Remove from adapter and save
                    adapter.removeFile(position)
                    saveImportedFiles()
                    updateEmptyState()
                    
                    Toast.makeText(
                        this@ImportActivity,
                        "File and data deleted successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@ImportActivity,
                        "Failed to delete data from database",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@ImportActivity,
                    "Error deleting file: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateEmptyState() {
        if (adapter.itemCount == 0) {
            emptyStateText.visibility = View.VISIBLE
            dateRangeText.visibility = View.GONE
        } else {
            emptyStateText.visibility = View.GONE
        }
    }
    
    private fun saveImportedFiles() {
        val files = adapter.getFiles()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Convert to a serializable format (URI as string)
        val serializable = files.map { file ->
            mapOf(
                "fileName" to file.fileName,
                "fileSizeBytes" to file.fileSizeBytes,
                "entryCount" to file.entryCount,
                "validEntryCount" to file.validEntryCount,
                "dateRange" to file.dateRange,
                "status" to file.status.name,
                "uri" to file.uri.toString(),
                "sourceFileId" to file.sourceFileId
            )
        }
        
        val json = com.google.gson.Gson().toJson(serializable)
        prefs.edit().putString(KEY_IMPORTED_FILES, json).apply()
    }
    
    private fun loadImportedFiles() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_IMPORTED_FILES, null) ?: return
        
        try {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
            val serializable: List<Map<String, Any>> = gson.fromJson(json, type)
            
            val files = serializable.mapNotNull { map ->
                val sourceFileId = map["sourceFileId"] as? String ?: ""
                
                // Filter out old files without valid sourceFileId
                if (sourceFileId.isEmpty()) {
                    Log.d("ImportActivity", "Skipping old file without sourceFileId: ${map["fileName"]}")
                    return@mapNotNull null
                }
                
                ImportedFile(
                    uri = Uri.parse(map["uri"] as String),
                    fileName = map["fileName"] as String,
                    fileSizeBytes = (map["fileSizeBytes"] as Double).toLong(),
                    entryCount = (map["entryCount"] as Double).toInt(),
                    validEntryCount = (map["validEntryCount"] as Double).toInt(),
                    dateRange = map["dateRange"] as String,
                    status = ImportStatus.valueOf(map["status"] as String),
                    sourceFileId = sourceFileId
                )
            }
            
            adapter.setFiles(files)
            updateEmptyState()
            
            // Save back the filtered list to remove old entries permanently
            if (files.size != serializable.size) {
                saveImportedFiles()
            }
            
            // Update date range if there are completed files
            val completedFiles = files.filter { it.status == ImportStatus.COMPLETED }
            if (completedFiles.isNotEmpty()) {
                val dateRanges = completedFiles.mapNotNull { file ->
                    try {
                        val parts = file.dateRange.split(" to ")
                        if (parts.size == 2) parts[0] to parts[1] else null
                    } catch (e: Exception) {
                        null
                    }
                }
                
                if (dateRanges.isNotEmpty()) {
                    val earliestDate = dateRanges.map { it.first }.minOrNull() ?: ""
                    val latestDate = dateRanges.map { it.second }.maxOrNull() ?: ""
                    dateRangeText.text = "$earliestDate to $latestDate"
                    dateRangeText.visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            // If loading fails, just start fresh
            android.util.Log.e("ImportActivity", "Failed to load imported files: ${e.message}")
        }
    }
}