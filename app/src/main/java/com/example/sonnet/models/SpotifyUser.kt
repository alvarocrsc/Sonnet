package com.example.sonnet.models

import com.google.firebase.firestore.PropertyName

/**
 * Data class representing Spotify user profile data
 * This will be stored in Firebase Firestore
 */
data class SpotifyUser(
    @PropertyName("id")
    var id: String = "",
    
    @PropertyName("display_name")
    var displayName: String = "",
    
    @PropertyName("email")
    var email: String? = null,
    
    @PropertyName("country")
    var country: String? = null,
    
    @PropertyName("product")
    var product: String? = null, // "free", "premium", etc.
    
    @PropertyName("profile_image_url")
    var profileImageUrl: String? = null,
    
    @PropertyName("followers")
    var followers: Int = 0,
    
    @PropertyName("created_at")
    var createdAt: Long = System.currentTimeMillis(),
    
    @PropertyName("last_updated")
    var lastUpdated: Long = System.currentTimeMillis()
) {
    // No-argument constructor required for Firebase Firestore
    constructor() : this("", "", null, null, null, null, 0, 0, 0)
}
