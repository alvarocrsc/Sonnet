package com.example.sonnet.profile

import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sonnet.R
import com.example.sonnet.TokenManager
import com.example.sonnet.adapters.TopAlbumsAdapter
import com.example.sonnet.adapters.TopArtistsAdapter
import com.example.sonnet.adapters.TopTracksAdapter
import com.example.sonnet.firebase.StatisticsManager
import com.example.sonnet.models.TimeRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class ProfileManager(
    private val profileView: View,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    private var currentTimeRange = TimeRange.MONTH
    private val statsManager = StatisticsManager.getInstance()
    private lateinit var topArtistsAdapter: TopArtistsAdapter
    private lateinit var topAlbumsAdapter: TopAlbumsAdapter
    private lateinit var topTracksAdapter: TopTracksAdapter
    
    // UI elements
    private val filter7Days: TextView = profileView.findViewById(R.id.filter_7days)
    private val filter30Days: TextView = profileView.findViewById(R.id.filter_30days)
    private val filter6Months: TextView = profileView.findViewById(R.id.filter_6months)
    private val filterAllTime: TextView = profileView.findViewById(R.id.filter_alltime)
    
    // Stats values
    private val minutesValue: TextView = profileView.findViewById(R.id.minutes_value)
    private val streamsValue: TextView = profileView.findViewById(R.id.streams_value)
    private val tracksValue: TextView = profileView.findViewById(R.id.tracks_value)
    private val artistsValue: TextView = profileView.findViewById(R.id.artists_value)
    
    // RecyclerView
    private val topArtistsRecycler: RecyclerView = profileView.findViewById(R.id.top_artists_recycler)
    private val topArtistsSkeleton: View = profileView.findViewById(R.id.top_artists_skeleton)
    
    private val topAlbumsRecycler: RecyclerView = profileView.findViewById(R.id.top_albums_recycler)
    private val topAlbumsSkeleton: View = profileView.findViewById(R.id.top_albums_skeleton)
    
    private val topTracksRecycler: RecyclerView = profileView.findViewById(R.id.top_tracks_recycler)
    private val topTracksSkeleton: View = profileView.findViewById(R.id.top_tracks_skeleton)
    
    companion object {
        private const val TAG = "ProfileManager"
    }
    
    private var currentUserId: String = ""
    
    fun initialize(userId: String) {
        Log.d(TAG, "Initializing profile for user: $userId")
        currentUserId = userId
        setupFilterButtons(userId)
        setupRecyclerView()
        
        // Start with ALL_TIME to use cached stats (fast)
        currentTimeRange = TimeRange.ALL_TIME
        selectFilter(TimeRange.ALL_TIME)
        loadStatistics(userId, TimeRange.ALL_TIME)
    }
    
    /**
     * Refresh profile data - call this when returning to the profile screen
     * to reload statistics that may have been updated
     */
    fun refresh() {
        if (currentUserId.isNotEmpty()) {
            Log.d(TAG, "Refreshing profile for user: $currentUserId")
            loadStatistics(currentUserId, currentTimeRange)
        }
    }
    
    private fun setupFilterButtons(userId: String) {
        filter7Days.setOnClickListener {
            selectFilter(TimeRange.WEEK)
            loadStatistics(userId, TimeRange.WEEK)
        }
        
        filter30Days.setOnClickListener {
            selectFilter(TimeRange.MONTH)
            loadStatistics(userId, TimeRange.MONTH)
        }
        
        filter6Months.setOnClickListener {
            selectFilter(TimeRange.SIX_MONTHS)
            loadStatistics(userId, TimeRange.SIX_MONTHS)
        }
        
        filterAllTime.setOnClickListener {
            selectFilter(TimeRange.ALL_TIME)
            loadStatistics(userId, TimeRange.ALL_TIME)
        }
    }
    
    private fun selectFilter(timeRange: TimeRange) {
        currentTimeRange = timeRange
        
        // Reset all filters to unselected state
        filter7Days.setBackgroundResource(R.drawable.bg_filter_unselected)
        filter7Days.setTextColor(profileView.context.getColor(android.R.color.white))
        
        filter30Days.setBackgroundResource(R.drawable.bg_filter_unselected)
        filter30Days.setTextColor(profileView.context.getColor(android.R.color.white))
        
        filter6Months.setBackgroundResource(R.drawable.bg_filter_unselected)
        filter6Months.setTextColor(profileView.context.getColor(android.R.color.white))
        
        filterAllTime.setBackgroundResource(R.drawable.bg_filter_unselected)
        filterAllTime.setTextColor(profileView.context.getColor(android.R.color.white))
        
        // Set selected filter
        val selectedTextView = when(timeRange) {
            TimeRange.WEEK -> filter7Days
            TimeRange.MONTH -> filter30Days
            TimeRange.SIX_MONTHS -> filter6Months
            TimeRange.ALL_TIME -> filterAllTime
        }
        
        selectedTextView.setBackgroundResource(R.drawable.bg_filter_selected)
        selectedTextView.setTextColor(profileView.context.getColor(R.color.accent_red))
    }
    
    private fun setupRecyclerView() {
        topArtistsAdapter = TopArtistsAdapter()
        topArtistsRecycler.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = topArtistsAdapter
        }
        
        topAlbumsAdapter = TopAlbumsAdapter()
        topAlbumsRecycler.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = topAlbumsAdapter
        }
        
        topTracksAdapter = TopTracksAdapter()
        topTracksRecycler.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = topTracksAdapter
        }
    }
    
    private fun loadStatistics(userId: String, timeRange: TimeRange) {
        Log.d(TAG, "Loading statistics for ${timeRange.name}...")
        
        // Currently only ALL_TIME is supported (time-based stats not yet implemented)
        if (timeRange != TimeRange.ALL_TIME) {
            Log.w(TAG, "Time-based filtering not yet implemented - showing ALL_TIME data")
            android.widget.Toast.makeText(
                profileView.context,
                "Time-based filtering coming soon - showing all-time stats",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            // Load ALL_TIME data instead
            loadStatistics(userId, TimeRange.ALL_TIME)
            return
        }
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Fetching user stats...")
                val userStats = withContext(Dispatchers.IO) {
                    statsManager.getUserStats(userId, timeRange)
                }
                Log.d(TAG, "User stats: $userStats")
                
                // If stats don't exist, show empty state instead of auto-calculating
                if (userStats == null) {
                    Log.w(TAG, "No statistics found - user needs to import data")
                    
                    // Show toast on main thread
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            profileView.context,
                            "No statistics found. Please import your Spotify data first.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    
                    return@launch
                }
                
                Log.d(TAG, "Fetching top artists...")
                val topArtists = withContext(Dispatchers.IO) {
                    statsManager.getTopArtists(userId, timeRange, limit = 50)
                }
                Log.d(TAG, "Got ${topArtists.size} artists")
                
                Log.d(TAG, "Fetching top albums...")
                val topAlbums = withContext(Dispatchers.IO) {
                    statsManager.getTopAlbums(userId, timeRange, limit = 50)
                }
                Log.d(TAG, "Got ${topAlbums.size} albums")
                
                Log.d(TAG, "Fetching top tracks...")
                val topTracks = withContext(Dispatchers.IO) {
                    statsManager.getTopTracks(userId, timeRange, limit = 50)
                }
                Log.d(TAG, "Got ${topTracks.size} tracks")
                
                // Update UI
                userStats?.let { stats ->
                    // Convert milliseconds to minutes
                    val minutes = stats.totalListeningTimeMs / 60000
                    minutesValue.text = formatNumber(minutes)
                    streamsValue.text = formatNumber(stats.totalStreams)
                    tracksValue.text = formatNumber(stats.uniqueTracks)
                    artistsValue.text = formatNumber(stats.uniqueArtists)
                    Log.d(TAG, "Updated user stats UI")
                }
                
                // Update top artists
                if (topArtists.isNotEmpty()) {
                    topArtistsAdapter.setArtists(topArtists)
                    
                    // Hide skeleton and show RecyclerView
                    topArtistsSkeleton.visibility = View.GONE
                    topArtistsRecycler.visibility = View.VISIBLE
                    
                    Log.d(TAG, "Updated top artists adapter with ${topArtists.size} artists")
                } else {
                    Log.w(TAG, "No artists found for ${timeRange.name}")
                    // Hide skeleton even if no data
                    topArtistsSkeleton.visibility = View.GONE
                    topArtistsRecycler.visibility = View.VISIBLE
                }
                
                // Update top albums
                if (topAlbums.isNotEmpty()) {
                    topAlbumsAdapter.setAlbums(topAlbums)
                    
                    // Hide skeleton and show RecyclerView
                    topAlbumsSkeleton.visibility = View.GONE
                    topAlbumsRecycler.visibility = View.VISIBLE
                    
                    Log.d(TAG, "Updated top albums adapter with ${topAlbums.size} albums")
                } else {
                    Log.w(TAG, "No albums found for ${timeRange.name}")
                    // Hide skeleton even if no data
                    topAlbumsSkeleton.visibility = View.GONE
                    topAlbumsRecycler.visibility = View.VISIBLE
                }
                
                // Update top tracks
                if (topTracks.isNotEmpty()) {
                    topTracksAdapter.setTracks(topTracks)
                    
                    // Hide skeleton and show RecyclerView
                    topTracksSkeleton.visibility = View.GONE
                    topTracksRecycler.visibility = View.VISIBLE
                    
                    Log.d(TAG, "Updated top tracks adapter with ${topTracks.size} tracks")
                } else {
                    Log.w(TAG, "No tracks found for ${timeRange.name}")
                    // Hide skeleton even if no data
                    topTracksSkeleton.visibility = View.GONE
                    topTracksRecycler.visibility = View.VISIBLE
                }
                
                Log.d(TAG, "Loaded stats for $userId (${timeRange.name}): ${topArtists.size} artists, ${topAlbums.size} albums, ${topTracks.size} tracks")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading statistics: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }
    
    private fun formatNumber(number: Int): String {
        return when {
            number >= 1000 -> String.format(Locale.US, "%.2fK", number / 1000.0)
            else -> NumberFormat.getInstance(Locale.US).format(number)
        }
    }
    
    private fun formatNumber(number: Long): String {
        return formatNumber(number.toInt())
    }
}
