package com.example.sonnet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.sonnet.firebase.FirebaseManager
import com.example.sonnet.models.SpotifyUser
import com.example.sonnet.spotify.SpotifyConfig
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    
    private var currentScreen = Screen.HOME
    private var previousScreen = Screen.HOME
    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var settingsOverlay: View? = null
    private var currentUser: SpotifyUser? = null
    
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
        
        // Set the main container layout with fixed bottom bar
        setContentView(R.layout.activity_main)
        
        // Load user data
        loadUserData()
        
        // Set up bottom navigation
        setupBottomNavigation()
        
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

    private fun showHomeScreen(preserveSettingsOverlay: Boolean = false) {
        val contentContainer = findViewById<FrameLayout>(R.id.content_container)
        if (preserveSettingsOverlay && settingsOverlay != null) {
            // Remove only the underlying content, not the settings overlay
            contentContainer.removeViewAt(0)
            // Inflate new screen at index 0 (below settings overlay)
            layoutInflater.inflate(R.layout.home, contentContainer, false).also {
                contentContainer.addView(it, 0)
            }
        } else {
            contentContainer.removeAllViews()
            layoutInflater.inflate(R.layout.home, contentContainer, true)
        }
        currentScreen = Screen.HOME
        updateBottomNavigation()
    }
    
    private fun showStatsScreen(preserveSettingsOverlay: Boolean = false) {
        val contentContainer = findViewById<FrameLayout>(R.id.content_container)
        if (preserveSettingsOverlay && settingsOverlay != null) {
            // Remove only the underlying content, not the settings overlay
            contentContainer.removeViewAt(0)
            // Inflate new screen at index 0 (below settings overlay)
            layoutInflater.inflate(R.layout.stats, contentContainer, false).also {
                contentContainer.addView(it, 0)
            }
        } else {
            contentContainer.removeAllViews()
            layoutInflater.inflate(R.layout.stats, contentContainer, true)
        }
        currentScreen = Screen.STATS
        updateBottomNavigation()
        
        // Load profile picture for stats screen
        findViewById<ImageView>(R.id.profile_picture_stats)?.let { imageView ->
            loadProfilePicture(imageView, currentUser?.profileImageUrl)
        }
    }

    private fun showFriendsScreen(preserveSettingsOverlay: Boolean = false) {
        val contentContainer = findViewById<FrameLayout>(R.id.content_container)
        if (preserveSettingsOverlay && settingsOverlay != null) {
            // Remove only the underlying content, not the settings overlay
            contentContainer.removeViewAt(0)
            // Inflate new screen at index 0 (below settings overlay)
            layoutInflater.inflate(R.layout.friends, contentContainer, false).also {
                contentContainer.addView(it, 0)
            }
        } else {
            contentContainer.removeAllViews()
            layoutInflater.inflate(R.layout.friends, contentContainer, true)
        }
        currentScreen = Screen.FRIENDS
        updateBottomNavigation()
    }

    private fun showProfileScreen() {
        val contentContainer = findViewById<FrameLayout>(R.id.content_container)
        contentContainer.removeAllViews()
        val profileView = layoutInflater.inflate(R.layout.profile, contentContainer, true)
        currentScreen = Screen.PROFILE
        updateBottomNavigation()
        
        // Load profile picture for profile screen
        profileView.findViewById<ImageView>(R.id.profile_picture)?.let { imageView ->
            loadProfilePicture(imageView, currentUser?.profileImageUrl)
        }
        
        // Set up settings button click listener
        profileView.findViewById<View>(R.id.settings_button)?.setOnClickListener {
            showSettingsScreen()
        }
    }

    private fun showSettingsScreen() {
        // Prevent opening settings if already open
        if (settingsOverlay != null) {
            return
        }
        
        previousScreen = currentScreen
        
        // Get the content container
        val contentContainer = findViewById<FrameLayout>(R.id.content_container)
        
        // Inflate settings layout
        val settingsView = layoutInflater.inflate(R.layout.settings, null)
        settingsOverlay = settingsView
        
        // Add settings view as overlay
        contentContainer.addView(settingsView)
        
        // Load profile picture for settings screen
        settingsView.findViewById<ImageView>(R.id.settings_profile_picture)?.let { imageView ->
            loadProfilePicture(imageView, currentUser?.profileImageUrl)
        }
        
        // Set up back button click listener
        settingsView.findViewById<View>(R.id.back_button)?.setOnClickListener {
            exitSettings()
        }

        settingsView.findViewById<View>(R.id.sign_out_item)?.setOnClickListener {
            TokenManager.clearToken(this)
            navigateToLogin()
        }
        
        settingsView.findViewById<View>(R.id.import_files_item)?.setOnClickListener {
            startActivity(Intent(this, com.example.sonnet.import.ImportActivity::class.java))
        }
        
        // Wait for layout to complete, then animate
        settingsView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                settingsView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                
                // Get the profile content (first child in container)
                val profileContent = contentContainer.getChildAt(0)
                
                // Start position: off screen to the right
                settingsView.translationX = settingsView.width.toFloat()
                
                // Animate profile screen moving left
                profileContent?.animate()
                    ?.translationX(-200f)
                    ?.setDuration(300)
                    ?.start()
                
                // Animate settings slide in from right
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
            val contentContainer = findViewById<FrameLayout>(R.id.content_container)
            val profileContent = contentContainer.getChildAt(0)
            
            // Animate profile screen moving back to original position
            profileContent?.animate()
                ?.translationX(0f)
                ?.setDuration(300)
                ?.start()
            
            // Animate settings slide out to right
            settingsView.animate()
                .translationX(settingsView.width.toFloat())
                .setDuration(300)
                .withEndAction {
                    // Remove settings overlay after animation
                    contentContainer.removeView(settingsView)
                    settingsOverlay = null
                    currentScreen = previousScreen
                }
                .start()
        }
    }
    
    private fun exitSettingsAndNavigateTo(navigationAction: () -> Unit) {
        settingsOverlay?.let { settingsView ->
            val contentContainer = findViewById<FrameLayout>(R.id.content_container)
            
            // Load the new target screen while preserving settings overlay
            navigationAction()
            
            // Get the newly loaded screen (should be at index 0, settings at index 1)
            val newContent = contentContainer.getChildAt(0)
            
            // Start the new screen at the left position (where profile was)
            newContent?.translationX = -200f
            
            // Animate new screen moving to original position
            newContent?.animate()
                ?.translationX(0f)
                ?.setDuration(300)
                ?.start()
            
            // Animate settings slide out to right
            settingsView.animate()
                .translationX(settingsView.width.toFloat())
                .setDuration(300)
                .withEndAction {
                    // Remove settings overlay after animation
                    contentContainer.removeView(settingsView)
                    settingsOverlay = null
                }
                .start()
        } ?: navigationAction() // If no settings overlay, just navigate
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
    
    private fun updateBottomNavigation() {
        // Get all icon ImageViews
        val homeIcon = findViewById<ImageView>(R.id.home_icon)
        val statsIcon = findViewById<ImageView>(R.id.stats_icon)
        val discoverIcon = findViewById<ImageView>(R.id.discover_icon)
        val profileCircle = findViewById<View>(R.id.profile_circle_bg)
        
        // Reset all to inactive state
        homeIcon?.setImageResource(R.drawable.ic_home_dark)
        statsIcon?.setImageResource(R.drawable.ic_stats_dark)
        discoverIcon?.setImageResource(R.drawable.ic_stars_dark)
        profileCircle?.visibility = View.GONE
        
        // Set active state based on currentScreen
        when (currentScreen) {
            Screen.HOME -> homeIcon?.setImageResource(R.drawable.ic_home_selected)
            Screen.STATS -> statsIcon?.setImageResource(R.drawable.ic_selected_stats)
            Screen.FRIENDS -> discoverIcon?.setImageResource(R.drawable.ic_discover_selected)
            Screen.PROFILE -> profileCircle?.visibility = View.VISIBLE
            Screen.SETTINGS -> profileCircle?.visibility = View.VISIBLE
        }
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
                if (currentScreen == Screen.SETTINGS) {
                    // Animate settings closing, then show home screen
                    exitSettingsAndNavigateTo { showHomeScreen(preserveSettingsOverlay = true) }
                } else {
                    showHomeScreen()
                }
            }
        }
        
        statsIcon?.setOnClickListener {
            if (currentScreen != Screen.STATS) {
                if (currentScreen == Screen.SETTINGS) {
                    // Animate settings closing, then show stats screen
                    exitSettingsAndNavigateTo { showStatsScreen(preserveSettingsOverlay = true) }
                } else {
                    showStatsScreen()
                }
            }
        }
        
        discoverIcon?.setOnClickListener {
            if (currentScreen != Screen.FRIENDS) {
                if (currentScreen == Screen.SETTINGS) {
                    // Animate settings closing, then show friends screen
                    exitSettingsAndNavigateTo { showFriendsScreen(preserveSettingsOverlay = true) }
                } else {
                    showFriendsScreen()
                }
            }
        }
        
        profileIcon?.setOnClickListener {
            if (currentScreen != Screen.PROFILE && currentScreen != Screen.SETTINGS) {
                showProfileScreen()
            } else if (currentScreen == Screen.SETTINGS) {
                // If already in settings, close it to go back to profile
                exitSettings()
            }
        }
    }
    
    private fun closeSettingsIfOpen() {
        settingsOverlay?.let { settingsView ->
            val contentContainer = findViewById<FrameLayout>(R.id.content_container)
            contentContainer.removeView(settingsView)
            settingsOverlay = null
        }
    }
    
    private fun loadUserData() {
        val userId = TokenManager.getUserId(this)
        if (userId != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val user = FirebaseManager.getInstance().getUser(userId)
                    currentUser = user
                    withContext(Dispatchers.Main) {
                        // Update all profile pictures once data is loaded
                        updateAllProfilePictures()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to load user data", e)
                }
            }
        }
    }
    
    private fun updateAllProfilePictures() {
        val profileImageUrl = currentUser?.profileImageUrl
        
        // Update menu bar profile icon
        findViewById<ImageView>(R.id.profile_icon)?.let { imageView ->
            loadProfilePicture(imageView, profileImageUrl)
        }
        
        // Update profile screen picture (if currently shown)
        if (currentScreen == Screen.PROFILE) {
            findViewById<ImageView>(R.id.profile_picture)?.let { imageView ->
                loadProfilePicture(imageView, profileImageUrl)
            }
        }
        
        // Update stats screen picture (if currently shown)
        if (currentScreen == Screen.STATS) {
            findViewById<ImageView>(R.id.profile_picture_stats)?.let { imageView ->
                loadProfilePicture(imageView, profileImageUrl)
            }
        }

        // Update settings picture (if currently shown)
        if (currentScreen == Screen.SETTINGS) {
            findViewById<ImageView>(R.id.settings_profile_picture)?.let { imageView ->
                loadProfilePicture(imageView, profileImageUrl)
            }
        }
    }
    
    private fun loadProfilePicture(imageView: ImageView, url: String?) {
        if (url != null && url.isNotEmpty()) {
            Glide.with(this)
                .load(url)
                .transform(CircleCrop())
                .placeholder(R.drawable.linkin_park)  // Fallback while loading
                .error(R.drawable.linkin_park)  // Fallback if error
                .into(imageView)
        } else {
            // No URL, use default
            imageView.setImageResource(R.drawable.linkin_park)
        }
    }
}
