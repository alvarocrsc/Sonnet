package com.example.sonnet


import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.sdk.android.auth.LoginActivity

class MainActivity : ComponentActivity() {
    
    private var currentScreen = Screen.PROFILE
    private var spotifyAppRemote: SpotifyAppRemote? = null
    enum class Screen {
        PROFILE,
        STATS,
        FRIENDS,
        HOME,
        LOGIN
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        // Start with home screen
        login()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, Prueba::class.java)
        val connectionParams = ConnectionParams.Builder("5b47f2ccc4e441a6b4a4997b9bf80ec1")
            .setRedirectUri("com.example.sonnet://callback")
            .showAuthView(true)
            .build()
        SpotifyAppRemote.connect(this, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                Log.d("MainActivity", "Connected! Yay!")
                // Now you can start interacting with App Remote
                // connected()
                startActivity(intent)
            }

            override fun onFailure(throwable: Throwable) {
                Log.e("MainActivity", throwable.message, throwable)
                // Something went wrong when attempting to connect! Handle errors here
            }
        })
    }

    override fun onStop() {
        SpotifyAppRemote.disconnect(spotifyAppRemote)
        super.onStop()
    }
    private fun showLoginScreen() {
        setContentView(R.layout.login)
        currentScreen = Screen.LOGIN
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
    }
    private fun login() {
        showLoginScreen()
        val loginBtn = findViewById<View>(R.id.spotify_login_button)
        loginBtn?.setOnClickListener {
            showHomeScreen()
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
