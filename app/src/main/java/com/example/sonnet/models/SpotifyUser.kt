package com.example.sonnet.models

import com.google.firebase.firestore.PropertyName

/**
 * Data class representing Spotify user profile data
 * This will be stored in Firebase Firestore
 */
data class SpotifyUser(
    @PropertyName("id")
    val id: String = "",
    
    @PropertyName("display_name")
    val displayName: String = "",
    
    @PropertyName("email")
    val email: String? = null,
    
    @PropertyName("country")
    val country: String? = null,
    
    @PropertyName("product")
    val product: String? = null, // "free", "premium", etc.
    
    @PropertyName("profile_image_url")
    val profileImageUrl: String? = null,
    
    @PropertyName("followers")
    val followers: Int = 0,
    
    @PropertyName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @PropertyName("last_updated")
    val lastUpdated: Long = System.currentTimeMillis()
) {
    // No-argument constructor required for Firebase Firestore
    constructor() : this("", "", null, null, null, null, 0, 0, 0)
}
