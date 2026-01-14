package com.example.sonnet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import com.example.sonnet.firebase.FirebaseManager
import com.example.sonnet.spotify.SpotifyAuthRepository
import com.example.sonnet.spotify.SpotifyConfig
import com.example.sonnet.spotify.SpotifyRepository
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom

class LoginActivity : ComponentActivity() {
    
    private var codeVerifier: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Check if user is already logged in
        if (TokenManager.hasValidToken(this)) {
            Log.d("LoginActivity", "User already authenticated, going to MainActivity")
            navigateToMainActivity()
            return
        }
        
        // Handle redirect from Spotify auth
        handleAuthCallback(intent)
        
        // Show login screen
        setContentView(R.layout.login)
        setupLoginButton()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthCallback(intent)
    }
    
    private fun setupLoginButton() {
        val loginBtn = findViewById<View>(R.id.spotify_login_button)
        loginBtn?.setOnClickListener {
            authenticateWithSpotify()
        }
    }
    
    private fun handleAuthCallback(intent: Intent?) {
        val uri = intent?.data
        Log.d("LoginActivity", "handleAuthCallback - URI: $uri")
        
        if (uri != null && uri.toString().startsWith(SpotifyConfig.REDIRECT_URI)) {
            Log.d("LoginActivity", "Redirect URI matched! Full URI: $uri")
            
            // Check for authorization code (PKCE flow)
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error")
            
            when {
                code != null -> {
                    Log.d("LoginActivity", "Got authorization code: ${code.take(10)}...")
                    
                    // Exchange authorization code for access token
                    lifecycleScope.launch {
                        if (codeVerifier != null) {
                            exchangeCodeAndFetchProfile(code, codeVerifier!!)
                        } else {
                            Log.e("LoginActivity", "Code verifier is null!")
                            Toast.makeText(
                                this@LoginActivity,
                                "Authentication error: Missing code verifier",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                error != null -> {
                    Log.e("LoginActivity", "Auth error: $error")
                    Toast.makeText(this, "Authentication failed: $error", Toast.LENGTH_LONG).show()
                }
                else -> {
                    Log.e("LoginActivity", "No code or error in callback")
                }
            }
        } else {
            Log.d("LoginActivity", "URI doesn't match redirect URI or is null")
        }
    }
    
    private fun authenticateWithSpotify() {
        // Generate PKCE code verifier and challenge
        codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier!!)
        
        // Build authorization URL with PKCE
        val scopes = SpotifyConfig.SCOPES.joinToString("%20")
        val authUrl = "https://accounts.spotify.com/authorize" +
                "?client_id=${SpotifyConfig.CLIENT_ID}" +
                "&response_type=code" +
                "&redirect_uri=${Uri.encode(SpotifyConfig.REDIRECT_URI)}" +
                "&scope=$scopes" +
                "&code_challenge_method=S256" +
                "&code_challenge=$codeChallenge"
        
        Log.d("LoginActivity", "Opening auth URL with PKCE")
        Log.d("LoginActivity", "Code verifier: ${codeVerifier?.take(20)}...")
        Log.d("LoginActivity", "Code challenge: ${codeChallenge.take(20)}...")
        
        // Build Custom Tab with smooth animations and Spotify branding
        val customTabsIntent = CustomTabsIntent.Builder()
            .setToolbarColor(android.graphics.Color.parseColor("#1DB954"))
            .setStartAnimations(this, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .setExitAnimations(this, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .build()
        
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        customTabsIntent.launchUrl(this, Uri.parse(authUrl))
    }
    
    private fun generateCodeVerifier(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
    
    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
    
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        // Clear back stack so user can't go back to login
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    /**
     * Exchange authorization code for access token and fetch user profile
     */
    private suspend fun exchangeCodeAndFetchProfile(code: String, codeVerifier: String) {
        try {
            // Exchange code for access token
            Log.d("LoginActivity", "Exchanging authorization code for access token...")
            val tokenResponse = SpotifyAuthRepository.getInstance().exchangeCodeForToken(code, codeVerifier)
            
            if (tokenResponse != null) {
                val accessToken = tokenResponse.access_token
                Log.d("LoginActivity", "Access token received: ${accessToken.take(20)}...")
                Log.d("LoginActivity", "Refresh token received: ${tokenResponse.refresh_token?.take(20) ?: "NULL"}")
                Log.d("LoginActivity", "Token expires in: ${tokenResponse.expires_in} seconds")
                
                // Save tokens with expiry
                TokenManager.saveTokens(
                    this@LoginActivity, 
                    accessToken, 
                    tokenResponse.refresh_token,
                    tokenResponse.expires_in
                )
                
                // Verify tokens were saved
                val savedRefreshToken = TokenManager.getRefreshToken(this@LoginActivity)
                Log.d("LoginActivity", "Verified saved refresh token: ${savedRefreshToken?.take(20) ?: "NULL"}")
                
                // Fetch and save user profile
                fetchAndSaveUserProfile(accessToken)
            } else {
                Log.e("LoginActivity", "Failed to exchange code for token")
                runOnUiThread {
                    Toast.makeText(
                        this@LoginActivity,
                        "Failed to authenticate with Spotify",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in exchangeCodeAndFetchProfile: ${e.message}", e)
            runOnUiThread {
                Toast.makeText(
                    this@LoginActivity,
                    "Authentication error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Fetch user profile from Spotify API and save to Firebase
     */
    private suspend fun fetchAndSaveUserProfile(accessToken: String) {
        try {
            // Fetch user profile from Spotify
            val spotifyUser = SpotifyRepository.getInstance().fetchUserProfile(accessToken)
            
            if (spotifyUser != null) {
                // Save to Firebase
                val success = FirebaseManager.getInstance().saveUser(spotifyUser)
                
                if (success) {
                    Log.d("LoginActivity", "User profile saved to Firebase: ${spotifyUser.displayName}")
                    // Save user ID and display name for future reference
                    TokenManager.saveUserId(this@LoginActivity, spotifyUser.id)
                    TokenManager.saveDisplayName(this@LoginActivity, spotifyUser.displayName)
                } else {
                    Log.e("LoginActivity", "Failed to save user profile to Firebase")
                }
                
                // Navigate to MainActivity regardless of Firebase save result
                runOnUiThread {
                    // Clear intent data
                    intent.data = null
                    navigateToMainActivity()
                }
            } else {
                Log.e("LoginActivity", "Failed to fetch user profile from Spotify")
                runOnUiThread {
                    Toast.makeText(
                        this@LoginActivity,
                        "Failed to fetch user profile. Please try again.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in fetchAndSaveUserProfile: ${e.message}", e)
            runOnUiThread {
                Toast.makeText(
                    this@LoginActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
