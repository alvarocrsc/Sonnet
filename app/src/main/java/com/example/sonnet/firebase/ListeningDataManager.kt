package com.example.sonnet.firebase

import android.util.Log
import com.example.sonnet.models.ListeningHistory
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * Manager class to handle Firebase Firestore operations for listening history
 * Uses subcollection structure: users/{userId}/listening_history/{docId}
 */
class ListeningDataManager private constructor() {
    
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        // Enable offline persistence for instant loading from cache
        try {
            firestoreSettings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(100 * 1024 * 1024) // 100MB cache (reasonable limit)
                .build()
        } catch (e: Exception) {
            // Persistence already enabled or other error
            Log.d(TAG, "Firestore settings: ${e.message}")
        }
    }
    
    companion object {
        private const val TAG = "ListeningDataManager"
        private const val BATCH_SIZE = 500 // Firestore batch write limit
        
        @Volatile
        private var instance: ListeningDataManager? = null
        
        fun getInstance(): ListeningDataManager {
            return instance ?: synchronized(this) {
                instance ?: ListeningDataManager().also { instance = it }
            }
        }
    }
    
    /**
     * Get listening history subcollection reference for a user
     */
    private fun getListeningHistoryCollection(userId: String) =
        db.collection("users").document(userId).collection("listening_history")
    
    /**
     * Get recent tracks collection reference for a user
     * This is a small, optimized collection that stores only the latest 10 tracks
     */
    private fun getRecentTracksCollection(userId: String) =
        db.collection("users").document(userId).collection("recent_tracks")
    
    /**
     * Save a single listening history entry to Firestore
     * @param userId User's Spotify ID
     * @param history ListeningHistory entry to save
     * @return true if successful, false otherwise
     */
    suspend fun saveListeningHistory(userId: String, history: ListeningHistory): Boolean {
        return try {
            getListeningHistoryCollection(userId).document(history.id).set(history).await()
            Log.d(TAG, "Listening history ${history.id} saved successfully")
            
            // Also update recent_tracks collection for fast loading
            updateRecentTrack(userId, history)
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving listening history: ${e.message}", e)
            false
        }
    }
    
    /**
     * Update the recent_tracks collection with a new track
     * Maintains only the latest 10 tracks for fast querying
     */
    suspend fun updateRecentTrack(userId: String, history: ListeningHistory): Boolean {
        return try {
            // Add the new track with timestamp as ID for easy ordering
            val trackId = history.playedAt.toDate().time.toString()
            getRecentTracksCollection(userId)
                .document(trackId)
                .set(history)
                .await()
            
            // Clean up old tracks (keep only latest 10)
            val snapshot = getRecentTracksCollection(userId)
                .orderBy("playedAt", Query.Direction.DESCENDING)
                .get()
                .await()
            
            // Delete excess tracks
            if (snapshot.size() > 10) {
                snapshot.documents.drop(10).forEach { doc ->
                    doc.reference.delete()
                }
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating recent track: ${e.message}", e)
            false
        }
    }
    
    /**
     * Batch save multiple listening history entries
     * Uses Firebase batch writes (max 500 operations per batch)
     * 
     * @param userId User's Spotify ID
     * @param historyList List of ListeningHistory entries to save
     * @param onProgress Callback to report progress (current, total)
     * @return true if all batches successful, false otherwise
     */
    suspend fun batchSaveListeningHistory(
        userId: String,
        historyList: List<ListeningHistory>,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Boolean {
        return try {
            val total = historyList.size
            var saved = 0
            val collection = getListeningHistoryCollection(userId)
            
            // Split into chunks of BATCH_SIZE (500)
            historyList.chunked(BATCH_SIZE).forEach { chunk ->
                val batch = db.batch()
                
                chunk.forEach { history ->
                    val docRef = collection.document(history.id)
                    batch.set(docRef, history)
                }
                
                // Commit batch
                batch.commit().await()
                
                saved += chunk.size
                onProgress?.invoke(saved, total)
                
                Log.d(TAG, "Batch saved: $saved/$total entries")
            }
            
            Log.d(TAG, "All listening history saved successfully: $total entries")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error batch saving listening history: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get recent listening history for a user
     * @param userId User's Spotify ID
     * @param limit Maximum number of entries to retrieve
     * @return List of ListeningHistory entries, ordered by playedAt (most recent first)
     */
    suspend fun getRecentListeningHistory(
        userId: String,
        limit: Int = 50
    ): List<ListeningHistory> {
        return try {
            val snapshot = getListeningHistoryCollection(userId)
                .orderBy("playedAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
            
            val history = snapshot.documents.mapNotNull { document ->
                document.toObject(ListeningHistory::class.java)
            }
            
            Log.d(TAG, "Retrieved ${history.size} recent listening entries for user $userId")
            history
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recent listening history: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get listening history since a specific timestamp
     * Useful for syncing with Spotify API (only get new plays)
     * 
     * @param userId User's Spotify ID
     * @param sinceTimestamp Get all plays after this timestamp (milliseconds)
     * @return List of ListeningHistory entries after the timestamp
     */
    suspend fun getListeningHistorySince(
        userId: String,
        sinceTimestamp: Long
    ): List<ListeningHistory> {
        return try {
            val snapshot = getListeningHistoryCollection(userId)
                .whereGreaterThan("playedAt", com.google.firebase.Timestamp(sinceTimestamp / 1000, 0))
                .orderBy("playedAt", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val history = snapshot.documents.mapNotNull { document ->
                document.toObject(ListeningHistory::class.java)
            }
            
            Log.d(TAG, "Retrieved ${history.size} listening entries since timestamp for user $userId")
            history
        } catch (e: Exception) {
            Log.e(TAG, "Error getting listening history since timestamp: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get the timestamp of the user's most recent play
     * Useful for determining the starting point for API sync
     * 
     * @param userId User's Spotify ID
     * @return Timestamp in milliseconds, or null if no history found
     */
    suspend fun getLastPlayTimestamp(userId: String): Long? {
        return try {
            val snapshot = getListeningHistoryCollection(userId)
                .orderBy("playedAt", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
            
            val lastPlay = snapshot.documents.firstOrNull()?.toObject(ListeningHistory::class.java)
            val timestamp = lastPlay?.playedAt?.toDate()?.time
            
            Log.d(TAG, "Last play timestamp for user $userId: $timestamp")
            timestamp
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last play timestamp: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get total listening history count for a user
     * @param userId User's Spotify ID
     * @return Total number of listening entries
     */
    suspend fun getListeningHistoryCount(userId: String): Int {
        return try {
            val snapshot = getListeningHistoryCollection(userId)
                .get()
                .await()
            
            val count = snapshot.size()
            Log.d(TAG, "Total listening history count for user $userId: $count")
            count
        } catch (e: Exception) {
            Log.e(TAG, "Error getting listening history count: ${e.message}", e)
            0
        }
    }
    
    /**
     * Check if a listening entry already exists for preventing duplicate imports
     * 
     * @param userId User's Spotify ID
     * @param historyId The ID of the listening history entry
     * @return true if exists, false otherwise
     */
    suspend fun listeningHistoryExists(userId: String, historyId: String): Boolean {
        return try {
            val document = getListeningHistoryCollection(userId).document(historyId).get().await()
            document.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking listening history existence: ${e.message}", e)
            false
        }
    }
    
    /**
     * Delete all listening history for a user
     * 
     * @param userId User's Spotify ID
     * @return true if successful, false otherwise
     */
    suspend fun deleteUserListeningHistory(userId: String): Boolean {
        return try {
            val snapshot = getListeningHistoryCollection(userId)
                .get()
                .await()
            
            // Delete in batches
            snapshot.documents.chunked(BATCH_SIZE).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { document ->
                    batch.delete(document.reference)
                }
                batch.commit().await()
            }
            
            Log.d(TAG, "Deleted all listening history for user $userId (${snapshot.size()} documents)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user listening history: ${e.message}", e)
            false
        }
    }
    
    /**
     * Delete all listening history entries from a specific imported file
     * 
     * Note: This loads all matching documents at once (30-60 second delay for large imports)
     * because Firestore must fetch full documents before deletion. Alternative approaches
     * (batching, pagination) are slower since each query takes 20+ seconds.
     * 
     * @param userId User ID (for subcollection path)
     * @param sourceFileId Unique ID of the file to delete entries from
     * @return true if successful, false otherwise
     */
    suspend fun deleteListeningHistoryByFileId(userId: String, sourceFileId: String): Boolean {
        return try {
            // Query all documents with this sourceFileId
            // This will take 30-60 seconds for large datasets due to loading images/data
            val snapshot = getListeningHistoryCollection(userId)
                .whereEqualTo("sourceFileId", sourceFileId)
                .get()
                .await()
            
            if (snapshot.isEmpty) {
                Log.d(TAG, "No documents found with sourceFileId: $sourceFileId")
                return true
            }
            
            Log.d(TAG, "Deleting ${snapshot.size()} documents with sourceFileId: $sourceFileId")
            
            // Delete in batches (Firestore limit: 500 operations per batch)
            snapshot.documents.chunked(BATCH_SIZE).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { document ->
                    batch.delete(document.reference)
                }
                batch.commit().await()
            }
            
            Log.d(TAG, "Successfully deleted ${snapshot.size()} documents from file $sourceFileId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting listening history by fileId: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get recently played tracks for home screen display (OPTIMIZED)
     * Uses dedicated recent_tracks collection for instant loading from cache
     * Falls back to listening_history if recent_tracks is empty
     * @param userId User's Spotify ID
     * @param limit Number of tracks to return (default 5)
     * @return List of RecentlyPlayedTrack entries
     */
    suspend fun getRecentlyPlayedTracks(
        userId: String,
        limit: Int = 5
    ): List<com.example.sonnet.models.RecentlyPlayedTrack> {
        return try {
            // Try to get from recent_tracks collection first (fast!)
            val snapshot = getRecentTracksCollection(userId)
                .orderBy("playedAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
            
            val recentTracks = snapshot.documents.mapNotNull { document ->
                document.toObject(ListeningHistory::class.java)
            }
            
            // If recent_tracks collection is empty, fall back to listening_history
            val history = if (recentTracks.isEmpty()) {
                Log.d(TAG, "recent_tracks empty, falling back to listening_history")
                getRecentListeningHistory(userId, limit)
            } else {
                Log.d(TAG, "Loaded ${recentTracks.size} tracks from recent_tracks collection")
                recentTracks
            }
            
            history.map { entry ->
                com.example.sonnet.models.RecentlyPlayedTrack(
                    trackName = entry.trackName ?: "Unknown Track",
                    artistName = entry.artistName ?: "Unknown Artist",
                    albumName = entry.albumName ?: "",
                    playedAt = entry.playedAt.toDate().time,
                    imageUrl = null // Will be fetched from Spotify API by adapter
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recently played tracks: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Migrate existing listening history to recent_tracks collection
     * Call this once to populate recent_tracks for existing users
     * Returns true if migration was successful or already completed
     */
    suspend fun migrateToRecentTracks(userId: String): Boolean {
        return try {
            // Check if recent_tracks already has enough data (at least 5 tracks)
            val existingSnapshot = getRecentTracksCollection(userId).limit(5).get().await()
            if (existingSnapshot.size() >= 5) {
                return true // Already migrated
            }
            
            Log.d(TAG, "Starting migration for user $userId (current tracks: ${existingSnapshot.size()})")
            
            // Get latest 10 tracks from listening_history
            val recentHistory = getRecentListeningHistory(userId, 10)
            
            if (recentHistory.isEmpty()) {
                return true // No data to migrate
            }
            
            // Populate recent_tracks
            recentHistory.forEach { history ->
                updateRecentTrack(userId, history)
            }
            
            Log.d(TAG, "Migration complete: ${recentHistory.size} tracks")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Migration error: ${e.message}", e)
            false
        }
    }
}
