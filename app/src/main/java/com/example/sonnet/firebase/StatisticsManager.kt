package com.example.sonnet.firebase

import android.util.Log
import com.example.sonnet.models.ListeningHistory
import com.example.sonnet.models.TimeRange
import com.example.sonnet.models.stats.UserStats
import com.example.sonnet.models.stats.ArtistStats
import com.example.sonnet.models.stats.AlbumStats
import com.example.sonnet.models.stats.TrackStats
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Manager class for calculating and storing listening statistics
 * Aggregates listening history data into ranked statistics
 */
class StatisticsManager private constructor() {
    
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val listeningDataManager = ListeningDataManager.getInstance()
    
    companion object {
        private const val TAG = "StatisticsManager"
        private const val BATCH_SIZE = 500
        
        @Volatile
        private var instance: StatisticsManager? = null
        
        fun getInstance(): StatisticsManager {
            return instance ?: synchronized(this) {
                instance ?: StatisticsManager().also { instance = it }
            }
        }
    }
    
    // Firestore collection references
    private fun getUserStatsDoc(userId: String) = 
        db.collection("user_stats").document(userId)
    
    private fun getArtistStatsCollection() = 
        db.collection("artist_stats")
    
    private fun getAlbumStatsCollection() = 
        db.collection("album_stats")
    
    private fun getTrackStatsCollection() = 
        db.collection("track_stats")
    
    /**
     * Calculate and save all statistics for a user
     * This is the main entry point after importing data
     * 
     * @param userId User's Spotify ID
     * @param timeRange Time range to calculate stats for (defaults to ALL_TIME)
     * @param onProgress Callback to report progress
     * @return true if successful, false otherwise
     */
    suspend fun calculateAndSaveAllStats(
        userId: String,
        timeRange: TimeRange = TimeRange.ALL_TIME,
        onProgress: ((String) -> Unit)? = null
    ): Boolean {
        return try {
            Log.d(TAG, "Starting statistics calculation for user: $userId (${timeRange.name})")
            
            onProgress?.invoke("Fetching listening history...")
            val allHistory = getAllListeningHistory(userId, timeRange)
            
            if (allHistory.isEmpty()) {
                Log.w(TAG, "No listening history found for user: $userId")
                return true
            }
            
            Log.d(TAG, "Calculating statistics from ${allHistory.size} listening entries")
            
            // Calculate user stats
            onProgress?.invoke("Calculating user statistics...")
            val userStats = calculateUserStats(userId, allHistory)
            saveUserStats(userStats)
            
            // Calculate artist stats
            onProgress?.invoke("Calculating artist statistics...")
            val artistStats = calculateArtistStats(userId, allHistory)
            saveArtistStats(artistStats)
            
            // Calculate album stats
            onProgress?.invoke("Calculating album statistics...")
            val albumStats = calculateAlbumStats(userId, allHistory)
            saveAlbumStats(albumStats)
            
            // Calculate track stats
            onProgress?.invoke("Calculating track statistics...")
            val trackStats = calculateTrackStats(userId, allHistory)
            saveTrackStats(trackStats)
            
            onProgress?.invoke("Statistics updated successfully")
            Log.d(TAG, "All statistics calculated and saved successfully for user: $userId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating statistics: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get all listening history for a user (for statistics calculation)
     * @param userId User's Spotify ID
     * @param timeRange Optional time range filter (defaults to ALL_TIME)
     */
    private suspend fun getAllListeningHistory(
        userId: String,
        timeRange: TimeRange = TimeRange.ALL_TIME
    ): List<ListeningHistory> {
        return try {
            val cutoffTimestamp = timeRange.getCutoffTimestamp()
            
            val query = if (cutoffTimestamp != null) {
                // Filter by time range
                db.collection("users")
                    .document(userId)
                    .collection("listening_history")
                    .whereGreaterThanOrEqualTo("playedAt", com.google.firebase.Timestamp(java.util.Date(cutoffTimestamp)))
            } else {
                // Get all data
                db.collection("users")
                    .document(userId)
                    .collection("listening_history")
            }
            
            val snapshot = query.get().await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(ListeningHistory::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching listening history: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Calculate overall user statistics
     */
    private fun calculateUserStats(userId: String, history: List<ListeningHistory>): UserStats {
        val totalListeningTimeMs = history.sumOf { it.durationMs }
        val totalStreams = history.size
        val uniqueTracks = history.mapNotNull { it.trackId }.distinct().size
        val uniqueArtists = history.mapNotNull { it.artistName }.distinct().size
        val uniqueAlbums = history.mapNotNull { it.albumName }.distinct().size
        
        return UserStats(
            userId = userId,
            totalListeningTimeMs = totalListeningTimeMs,
            totalStreams = totalStreams,
            uniqueTracks = uniqueTracks,
            uniqueArtists = uniqueArtists,
            uniqueAlbums = uniqueAlbums,
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    /**
     * Calculate artist statistics and rank by listening time
     */
    private fun calculateArtistStats(userId: String, history: List<ListeningHistory>): List<ArtistStats> {
        // Group by artist name
        val artistGroups = history
            .filter { !it.artistName.isNullOrEmpty() }
            .groupBy { it.artistName!! }
        
        // Calculate stats for each artist
        val stats = artistGroups.map { (artistName, entries) ->
            val totalTime = entries.sumOf { it.durationMs }
            val playCount = entries.size
            val lastPlayed = entries.maxOfOrNull { it.playedAt.toDate().time } ?: 0L
            
            ArtistStats(
                id = "${userId}_${artistName.hashCode()}",
                userId = userId,
                artistId = artistName.hashCode().toString(),
                artistName = artistName,
                imageUrl = entries.firstNotNullOfOrNull { it.artistImageUrl },
                totalListeningTimeMs = totalTime,
                playCount = playCount,
                rank = 0, // Will be set after sorting
                lastPlayed = lastPlayed
            )
        }
        
        // Sort by listening time (descending) and assign ranks
        return stats
            .sortedByDescending { it.totalListeningTimeMs }
            .mapIndexed { index, stat -> 
                stat.copy(rank = index + 1)
            }
    }
    
    /**
     * Calculate album statistics and rank by listening time
     */
    private fun calculateAlbumStats(userId: String, history: List<ListeningHistory>): List<AlbumStats> {
        // Group by album
        val albumGroups = history
            .filter { !it.albumName.isNullOrEmpty() }
            .groupBy { it.albumName!! }
        
        // Calculate stats for each album
        val stats = albumGroups.map { (albumName, entries) ->
            val totalTime = entries.sumOf { it.durationMs }
            val playCount = entries.size
            val lastPlayed = entries.maxOfOrNull { it.playedAt.toDate().time } ?: 0L
            val firstEntry = entries.first()
            
            AlbumStats(
                id = "${userId}_${albumName.hashCode()}",
                userId = userId,
                albumId = albumName.hashCode().toString(),
                albumName = albumName,
                artistName = firstEntry.artistName ?: "",
                imageUrl = firstEntry.albumImageUrl,
                totalListeningTimeMs = totalTime,
                playCount = playCount,
                rank = 0, // Will be set after sorting
                lastPlayed = lastPlayed
            )
        }
        
        // Sort by listening time (descending) and assign ranks
        return stats
            .sortedByDescending { it.totalListeningTimeMs }
            .mapIndexed { index, stat -> 
                stat.copy(rank = index + 1)
            }
    }
    
    /**
     * Calculate track statistics and rank by listening time
     */
    private fun calculateTrackStats(userId: String, history: List<ListeningHistory>): List<TrackStats> {
        // Group by track
        val trackGroups = history
            .filter { !it.trackId.isNullOrEmpty() }
            .groupBy { it.trackId!! }
        
        // Calculate stats for each track
        val stats = trackGroups.map { (trackId, entries) ->
            val totalTime = entries.sumOf { it.durationMs }
            val playCount = entries.size
            val lastPlayed = entries.maxOfOrNull { it.playedAt.toDate().time } ?: 0L
            val firstEntry = entries.first()
            
            TrackStats(
                id = "${userId}_${trackId}",
                userId = userId,
                trackId = trackId,
                trackName = firstEntry.trackName ?: "",
                artistName = firstEntry.artistName ?: "",
                albumName = firstEntry.albumName ?: "",
                albumImageUrl = firstEntry.albumImageUrl,
                durationMs = firstEntry.durationMs,
                totalListeningTimeMs = totalTime,
                playCount = playCount,
                rank = 0, // Will be set after sorting
                lastPlayed = lastPlayed
            )
        }
        
        // Sort by listening time (descending) and assign ranks
        return stats
            .sortedByDescending { it.totalListeningTimeMs }
            .mapIndexed { index, stat -> 
                stat.copy(rank = index + 1)
            }
    }
    
    /**
     * Save user statistics to Firestore
     */
    private suspend fun saveUserStats(stats: UserStats): Boolean {
        return try {
            getUserStatsDoc(stats.userId).set(stats).await()
            Log.d(TAG, "User stats saved for ${stats.userId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user stats: ${e.message}", e)
            false
        }
    }
    
    /**
     * Save artist statistics to Firestore in batches
     */
    private suspend fun saveArtistStats(statsList: List<ArtistStats>): Boolean {
        return try {
            statsList.chunked(BATCH_SIZE).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { stats ->
                    val docRef = getArtistStatsCollection().document(stats.id)
                    batch.set(docRef, stats)
                }
                batch.commit().await()
            }
            Log.d(TAG, "Saved ${statsList.size} artist stats")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving artist stats: ${e.message}", e)
            false
        }
    }
    
    /**
     * Save album statistics to Firestore in batches
     */
    private suspend fun saveAlbumStats(statsList: List<AlbumStats>): Boolean {
        return try {
            statsList.chunked(BATCH_SIZE).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { stats ->
                    val docRef = getAlbumStatsCollection().document(stats.id)
                    batch.set(docRef, stats)
                }
                batch.commit().await()
            }
            Log.d(TAG, "Saved ${statsList.size} album stats")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving album stats: ${e.message}", e)
            false
        }
    }
    
    /**
     * Save track statistics to Firestore in batches
     */
    private suspend fun saveTrackStats(statsList: List<TrackStats>): Boolean {
        return try {
            statsList.chunked(BATCH_SIZE).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { stats ->
                    val docRef = getTrackStatsCollection().document(stats.id)
                    batch.set(docRef, stats)
                }
                batch.commit().await()
            }
            Log.d(TAG, "Saved ${statsList.size} track stats")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving track stats: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get user statistics
     * @param userId User's Spotify ID
     * @param timeRange Time range filter (calculates for non-ALL_TIME)
     */
    suspend fun getUserStats(
        userId: String,
        timeRange: TimeRange = TimeRange.ALL_TIME
    ): UserStats? {
        return try {
            // For ALL_TIME, use cached stats from Firestore
            if (timeRange == TimeRange.ALL_TIME) {
                val doc = getUserStatsDoc(userId).get().await()
                return doc.toObject(UserStats::class.java)
            }
            
            // For time ranges, calculate
            val history = getAllListeningHistory(userId, timeRange)
            calculateUserStats(userId, history)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user stats: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get top artists for a user
     * @param userId User's Spotify ID
     * @param timeRange Time range filter (calculates for non-ALL_TIME)
     * @param limit Number of top artists to return
     */
    suspend fun getTopArtists(
        userId: String,
        timeRange: TimeRange = TimeRange.ALL_TIME,
        limit: Int = 50
    ): List<ArtistStats> {
        return try {
            // For ALL_TIME, use cached stats from Firestore
            if (timeRange == TimeRange.ALL_TIME) {
                val snapshot = getArtistStatsCollection()
                    .whereEqualTo("userId", userId)
                    .orderBy("rank")
                    .limit(limit.toLong())
                    .get()
                    .await()
                
                return snapshot.documents.mapNotNull { doc ->
                    doc.toObject(ArtistStats::class.java)
                }
            }
            
            // For time ranges, calculate
            val history = getAllListeningHistory(userId, timeRange)
            calculateArtistStats(userId, history).take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting top artists: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get top albums for a user
     * @param userId User's Spotify ID
     * @param timeRange Time range filter (calculates for non-ALL_TIME)
     * @param limit Number of top albums to return
     */
    suspend fun getTopAlbums(
        userId: String,
        timeRange: TimeRange = TimeRange.ALL_TIME,
        limit: Int = 50
    ): List<AlbumStats> {
        return try {
            // For ALL_TIME, use cached stats from Firestore
            if (timeRange == TimeRange.ALL_TIME) {
                val snapshot = getAlbumStatsCollection()
                    .whereEqualTo("userId", userId)
                    .orderBy("rank")
                    .limit(limit.toLong())
                    .get()
                    .await()
                
                return snapshot.documents.mapNotNull { doc ->
                    doc.toObject(AlbumStats::class.java)
                }
            }
            
            // For time ranges, calculate
            val history = getAllListeningHistory(userId, timeRange)
            calculateAlbumStats(userId, history).take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting top albums: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get top tracks for a user
     * @param userId User's Spotify ID
     * @param timeRange Time range filter (calculates on-the-fly for non-ALL_TIME)
     * @param limit Number of top tracks to return
     */
    suspend fun getTopTracks(
        userId: String,
        timeRange: TimeRange = TimeRange.ALL_TIME,
        limit: Int = 50
    ): List<TrackStats> {
        return try {
            // For ALL_TIME, use cached stats from Firestore
            if (timeRange == TimeRange.ALL_TIME) {
                val snapshot = getTrackStatsCollection()
                    .whereEqualTo("userId", userId)
                    .orderBy("rank")
                    .limit(limit.toLong())
                    .get()
                    .await()
                
                return snapshot.documents.mapNotNull { doc ->
                    doc.toObject(TrackStats::class.java)
                }
            }
            
            // For time ranges, calculate on-the-fly
            val history = getAllListeningHistory(userId, timeRange)
            calculateTrackStats(userId, history).take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting top tracks: ${e.message}", e)
            emptyList()
        }
    }
}
