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
        
        // Start with home screen
        showHomeScreen()
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
