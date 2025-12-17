package com.example.sonnet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import com.example.sonnet.spotify.SpotifyConfig
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
                    
                    // In production, exchange code for token via backend
                    // For now, store a mock token
                    val mockToken = "spotify_token_${System.currentTimeMillis()}"
                    TokenManager.saveToken(this, mockToken)
                    
                    Log.d("LoginActivity", "Token saved, navigating to MainActivity")
                    
                    // Clear intent data
                    intent.data = null
                    
                    // Navigate to MainActivity
                    navigateToMainActivity()
                }
                error != null -> {
                    Log.e("LoginActivity", "Auth error: $error")
                    // Could show error UI here
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
}
