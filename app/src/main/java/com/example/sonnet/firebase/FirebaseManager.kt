package com.example.sonnet.firebase

import android.util.Log
import com.example.sonnet.models.SpotifyUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Manager class to handle Firebase Firestore operations for user data
 */
class FirebaseManager private constructor() {
    
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")
    
    companion object {
        private const val TAG = "FirebaseManager"
        
        @Volatile
        private var instance: FirebaseManager? = null
        
        fun getInstance(): FirebaseManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseManager().also { instance = it }
            }
        }
    }
    
    /**
     * Save or update Spotify user data in Firestore
     * @param user SpotifyUser data to save
     * @return true if successful, false otherwise
     */
    suspend fun saveUser(user: SpotifyUser): Boolean {
        return try {
            // Use user's Spotify ID as document ID
            val updatedUser = user.copy(lastUpdated = System.currentTimeMillis())
            usersCollection.document(user.id).set(updatedUser).await()
            Log.d(TAG, "User ${user.id} saved successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get user data from Firestore by Spotify ID
     * @param userId Spotify user ID
     * @return SpotifyUser if found, null otherwise
     */
    suspend fun getUser(userId: String): SpotifyUser? {
        return try {
            val document = usersCollection.document(userId).get().await()
            if (document.exists()) {
                document.toObject(SpotifyUser::class.java)
            } else {
                Log.d(TAG, "User $userId not found")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user: ${e.message}", e)
            null
        }
    }
    
    /**
     * Check if a user exists in Firestore
     * @param userId Spotify user ID
     * @return true if user exists, false otherwise
     */
    suspend fun userExists(userId: String): Boolean {
        return try {
            val document = usersCollection.document(userId).get().await()
            document.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking user existence: ${e.message}", e)
            false
        }
    }
    
    /**
     * Delete a user from Firestore
     * @param userId Spotify user ID
     * @return true if successful, false otherwise
     */
    suspend fun deleteUser(userId: String): Boolean {
        return try {
            usersCollection.document(userId).delete().await()
            Log.d(TAG, "User $userId deleted successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user: ${e.message}", e)
            false
        }
    }
}
