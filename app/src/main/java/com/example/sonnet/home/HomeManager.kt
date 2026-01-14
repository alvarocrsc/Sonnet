package com.example.sonnet.home

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sonnet.R
import com.example.sonnet.TokenManager
import com.example.sonnet.adapters.RecentlyPlayedAdapter
import com.example.sonnet.firebase.ListeningDataManager
import com.example.sonnet.utils.DisplayNameHelper
import com.example.sonnet.models.RecentlyPlayedTrack
import com.example.sonnet.spotify.SpotifyImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeManager(
    private val homeView: View,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    private val greetingText: TextView = homeView.findViewById(R.id.greeting_text)
    private val dateText: TextView = homeView.findViewById(R.id.date_text)
    private val recentlyPlayedRecycler: RecyclerView = homeView.findViewById(R.id.recently_played_recycler)
    private val monthlyRewindCard: android.view.ViewGroup? = homeView.findViewById(R.id.monthly_rewind_card)
    private val annualRewindCard: android.view.ViewGroup? = homeView.findViewById(R.id.annual_rewind_card)
    private lateinit var adapter: RecentlyPlayedAdapter
    private val listeningDataManager = ListeningDataManager.getInstance()
    private val imageLoader = SpotifyImageLoader(homeView.context)
    
    companion object {
        private const val TAG = "HomeManager"
    }
    
    fun initialize(userId: String) {
        Log.d(TAG, "Initializing HomeManager for user: $userId")
        updateGreetingAndDate(userId)
        setupRecyclerView()
        setupRewindCardListeners()
        loadRecentlyPlayed(userId)
    }
    
    private fun updateGreetingAndDate(userId: String) {
        lifecycleScope.launch {
            // Get display name using helper (handles caching and Firebase fallback)
            val displayName = DisplayNameHelper.getDisplayName(
                context = homeView.context,
                userId = userId,
                defaultName = "there"
            )
            
            // Update greeting
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            
            val greeting = when (hour) {
                in 0..11 -> "Good morning"
                in 12..17 -> "Good afternoon"
                else -> "Good night"
            }
            
            greetingText.text = "$greeting, $displayName!"
            
            // Update date
            val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
            val currentDate = dateFormat.format(Calendar.getInstance().time)
            dateText.text = currentDate
        }
    }
    
    private fun setupRecyclerView() {
        adapter = RecentlyPlayedAdapter(lifecycleScope, imageLoader)
        recentlyPlayedRecycler.layoutManager = LinearLayoutManager(homeView.context)
        recentlyPlayedRecycler.adapter = adapter
        Log.d(TAG, "RecyclerView setup complete")
    }
    
    private fun setupRewindCardListeners() {
        monthlyRewindCard?.setOnClickListener {
            android.widget.Toast.makeText(homeView.context, "Implementing soon...", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        annualRewindCard?.setOnClickListener {
            android.widget.Toast.makeText(homeView.context, "Implementing soon...", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadRecentlyPlayed(userId: String) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading recently played tracks...")
                
                // Check if migration has already been done for this user
                val prefs = homeView.context.getSharedPreferences("sonnet_prefs", Context.MODE_PRIVATE)
                val migrationKey = "recent_tracks_migrated_$userId"
                val alreadyMigrated = prefs.getBoolean(migrationKey, false)
                
                if (!alreadyMigrated) {
                    Log.d(TAG, "First load - triggering one-time migration")
                    val success = withContext(Dispatchers.IO) {
                        listeningDataManager.migrateToRecentTracks(userId)
                    }
                    if (success) {
                        prefs.edit().putBoolean(migrationKey, true).apply()
                        Log.d(TAG, "Migration complete and marked as done")
                    }
                } else {
                    Log.d(TAG, "Migration already done, skipping")
                }
                
                val tracks = withContext(Dispatchers.IO) {
                    listeningDataManager.getRecentlyPlayedTracks(userId, limit = 5)
                }
                Log.d(TAG, "Loaded ${tracks.size} recently played tracks")
                adapter.updateTracks(tracks)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading recently played tracks", e)
            }
        }
    }
    
    fun refresh(userId: String) {
        loadRecentlyPlayed(userId)
    }
}
