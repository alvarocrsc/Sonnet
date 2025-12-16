package com.example.sonnet.spotify

/**
 * Spotify API Configuration
 * 
 * SETUP INSTRUCTIONS:
 * 1. Go to https://developer.spotify.com/dashboard
 * 2. Create a new app (or use existing one)
 * 3. In app settings, add this EXACT redirect URI: com.example.sonnet://callback
 * 4. Copy your Client ID and paste it below (replace YOUR_CLIENT_ID_HERE)
 */
object SpotifyConfig {
    // TODO: Replace with your actual Client ID from Spotify Dashboard
    const val CLIENT_ID = "5b47f2ccc4e441a6b4a4997b9bf80ec1"
    
    // This MUST match the redirect URI you set in Spotify Dashboard
    const val REDIRECT_URI = "com.example.sonnet://callback"
    
    // Scopes define what permissions your app requests
    val SCOPES = arrayOf(
        // User Profile
        "user-read-private",           // Read user's subscription details
        "user-read-email",             // Read user's email
        
        // Listening History
        "user-top-read",               // Read user's top artists and tracks
        "user-read-recently-played",   // Read recently played tracks
        "user-read-playback-state",    // Read current playback state
        "user-read-currently-playing", // Read currently playing track
        
        // Library
        "user-library-read",           // Read saved tracks and albums
        
        // Playlists
        "playlist-read-private",       // Read private playlists
        "playlist-read-collaborative", // Read collaborative playlists
        
        // Follow
        "user-follow-read",            // Read followed artists and users
        
        // Playback Control (for App Remote)
        "app-remote-control",          // Control playback via App Remote
        "streaming"                    // Stream music (required for App Remote)
    )
    
    // Connection type for authentication
    const val AUTH_TOKEN_REQUEST_CODE = 0x10
    const val CONNECTION_REQUEST_CODE = 0x11
}
