package com.example.sonnet

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    
    private var currentScreen = Screen.PROFILE
    
    enum class Screen {
        PROFILE,
        STATS,
        FRIENDS,
        HOME
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Start with profile screen
        showProfileScreen()
    }

    private fun showHomeScreen() {
        setContentView(R.layout.home)
        currentScreen = Screen.HOME
        setupBottomNavigation()
    }

    private fun showProfileScreen() {
        setContentView(R.layout.profile)
        currentScreen = Screen.PROFILE
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
    
    private fun setupBottomNavigation() {
        // Get references to bottom navigation icons
        val homeIcon = findViewById<ImageView>(R.id.home_icon) ?: findViewById<ImageView>(R.id.home_icon_stats)
        val statsIcon = findViewById<ImageView>(R.id.stats_icon) ?: findViewById<ImageView>(R.id.stats_icon_stats)
        val discoverIcon = findViewById<ImageView>(R.id.discover_icon) ?: findViewById<ImageView>(R.id.discover_icon_stats)
        val profileIcon = findViewById<ImageView>(R.id.profile_icon) ?: findViewById<ImageView>(R.id.profile_icon_stats)
        
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
