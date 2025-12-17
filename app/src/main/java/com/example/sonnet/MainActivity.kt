package com.example.sonnet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.example.sonnet.spotify.SpotifyConfig
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote

class MainActivity : ComponentActivity() {
    
    private var currentScreen = Screen.HOME
    private var previousScreen = Screen.HOME
    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var settingsOverlay: View? = null
    
    enum class Screen {
        HOME,
        STATS,
        FRIENDS,
        PROFILE,
        SETTINGS
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Set window background to prevent white flash during transitions
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.decorView.setBackgroundColor(android.graphics.Color.parseColor("#121212"))
        
        // Check if user is authenticated
        if (!TokenManager.hasValidToken(this)) {
            Log.d("MainActivity", "No valid token, redirecting to login")
            navigateToLogin()
            return
        }
        
        Log.d("MainActivity", "User authenticated, showing home screen")
        
        // Show home screen
        showHomeScreen()
        
        // Optionally connect to App Remote
        connectToSpotifyAppRemote()
    }
    
    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        SpotifyAppRemote.disconnect(spotifyAppRemote)
        super.onStop()
    }

    private fun showHomeScreen() {
        setContentView(R.layout.home)
        currentScreen = Screen.HOME
        setupBottomNavigation()
    }
    
    private fun showStatsScreen() {
        setContentView(R.layout.stats)
        currentScreen = Screen.STATS
        setupBottomNavigation()
    }

    private fun showFriendsScreen() {
        setContentView(R.layout.friends)
        currentScreen = Screen.FRIENDS
        setupBottomNavigation()
    }

    private fun showProfileScreen() {
        setContentView(R.layout.profile)
        currentScreen = Screen.PROFILE
        setupBottomNavigation()
        
        // Set up settings button click listener
        findViewById<View>(R.id.settings_button)?.setOnClickListener {
            showSettingsScreen()
        }
    }

    private fun showSettingsScreen() {
        previousScreen = currentScreen
        
        // Inflate settings layout
        val settingsView = layoutInflater.inflate(R.layout.settings, null)
        settingsOverlay = settingsView
        
        // Add settings view as overlay on top of current content
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        rootView.addView(settingsView)
        
        // Set up back button click listener
        settingsView.findViewById<View>(R.id.back_button)?.setOnClickListener {
            exitSettings()
        }

        settingsView.findViewById<View>(R.id.sign_out_item)?.setOnClickListener {
            TokenManager.clearToken(this)
            navigateToLogin()
        }
        
        // Wait for layout to complete, then animate
        settingsView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                settingsView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                
                // Start position: off screen to the right
                settingsView.translationX = settingsView.width.toFloat()
                
                // Animate slide in from right
                settingsView.animate()
                    .translationX(0f)
                    .setDuration(300)
                    .start()
            }
        })
        
        currentScreen = Screen.SETTINGS
    }
    
    fun exitSettings() {
        settingsOverlay?.let { settingsView ->
            // Animate slide out to right
            settingsView.animate()
                .translationX(settingsView.width.toFloat())
                .setDuration(300)
                .withEndAction {
                    // Remove settings overlay after animation
                    val rootView = findViewById<ViewGroup>(android.R.id.content)
                    rootView.removeView(settingsView)
                    settingsOverlay = null
                    currentScreen = previousScreen
                }
                .start()
        }
    }
    
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun connectToSpotifyAppRemote() {
        val connectionParams = ConnectionParams.Builder(SpotifyConfig.CLIENT_ID)
            .setRedirectUri(SpotifyConfig.REDIRECT_URI)
            .showAuthView(true)
            .build()
            
        SpotifyAppRemote.connect(this, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                Log.d("MainActivity", "App Remote Connected! Showing home screen...")
                
                // Show home screen
                showHomeScreen()
            }

            override fun onFailure(throwable: Throwable) {
                Log.e("MainActivity", "App Remote connection failed: ${throwable.message}", throwable)
                
                // If App Remote fails, still show home screen
                // (App Remote auth can be done later through Spotify app)
                Log.d("MainActivity", "Showing home screen anyway (App Remote can connect later)")
                showHomeScreen()
            }
        })
    }
    private fun setupBottomNavigation() {
        // Get references to bottom navigation containers for larger touch targets
        val homeIcon = findViewById<View>(R.id.home_icon_container)
        val statsIcon = findViewById<View>(R.id.stats_icon_container)
        val discoverIcon = findViewById<View>(R.id.discover_icon_container)
        val profileIcon = findViewById<View>(R.id.profile_icon_container)
        
        // Set up click listeners
        homeIcon?.setOnClickListener {
            if (currentScreen != Screen.HOME) {
                showHomeScreen()
            }
        }
        
        statsIcon?.setOnClickListener {
            if (currentScreen != Screen.STATS) {
                showStatsScreen()
            }
        }
        
        discoverIcon?.setOnClickListener {
            if (currentScreen != Screen.FRIENDS) {
                showFriendsScreen()
            }
        }
        
        profileIcon?.setOnClickListener {
            if (currentScreen != Screen.PROFILE) {
                showProfileScreen()
            }
        }
    }
}
