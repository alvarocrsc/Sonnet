package com.example.sonnet.stats

import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.bumptech.glide.Glide
import com.example.sonnet.R
import com.example.sonnet.firebase.StatisticsManager
import com.example.sonnet.models.TimeRange
import com.example.sonnet.models.stats.AlbumStats
import com.example.sonnet.models.stats.ArtistStats
import com.example.sonnet.models.stats.TrackStats
import com.example.sonnet.models.stats.UserStats
import com.example.sonnet.spotify.SpotifyImageLoader
import com.example.sonnet.utils.DisplayNameHelper
import com.example.sonnet.utils.FilterHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class StatsManager(
    private val statsView: View,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    private var currentTimeRange = TimeRange.ALL_TIME
    private val statsManager = StatisticsManager.getInstance()
    private val imageLoader = SpotifyImageLoader(statsView.context)
    
    // User handle
    private val handleStats: TextView = statsView.findViewById(R.id.handle_stats)
    
    // Filter buttons
    private val filter7Days: TextView = statsView.findViewById(R.id.filter_7days)
    private val filter30Days: TextView = statsView.findViewById(R.id.filter_30days)
    private val filter6Months: TextView = statsView.findViewById(R.id.filter_6months)
    private val filterAllTime: TextView = statsView.findViewById(R.id.filter_alltime)
    
    // Stats values
    private val minutesValue: TextView = statsView.findViewById(R.id.value_minutes)
    private val artistsValue: TextView = statsView.findViewById(R.id.value_artists)
    private val streamsValue: TextView = statsView.findViewById(R.id.value_streams)
    private val tracksValue: TextView = statsView.findViewById(R.id.value_tracks)
    private val albumsValue: TextView = statsView.findViewById(R.id.value_albums)
    
    // Skeleton loaders
    private val minutesSkeleton: View = statsView.findViewById(R.id.skeleton_minutes)
    private val artistsSkeleton: View = statsView.findViewById(R.id.skeleton_artists)
    private val streamsSkeleton: View = statsView.findViewById(R.id.skeleton_streams)
    private val tracksSkeleton: View = statsView.findViewById(R.id.skeleton_tracks)
    private val albumsSkeleton: View = statsView.findViewById(R.id.skeleton_albums)
    
    // Top Artists UI elements
    private val artist1Name: TextView = statsView.findViewById(R.id.artist1_name_stats)
    private val artist1Stats: TextView = statsView.findViewById(R.id.artist1_stats_stats)
    private val artist1ImageCard: View = statsView.findViewById(R.id.artist1_image_stats)
    private val artist2Name: TextView = statsView.findViewById(R.id.artist2_name_stats)
    private val artist2Stats: TextView = statsView.findViewById(R.id.artist2_stats_stats)
    private val artist2ImageCard: View = statsView.findViewById(R.id.artist2_image_stats)
    private val artist3Name: TextView = statsView.findViewById(R.id.artist3_name_stats)
    private val artist3Stats: TextView = statsView.findViewById(R.id.artist3_stats_stats)
    private val artist3ImageCard: View = statsView.findViewById(R.id.artist3_image_stats)
    private val artist4Name: TextView = statsView.findViewById(R.id.artist4_name_stats)
    private val artist4Stats: TextView = statsView.findViewById(R.id.artist4_stats_stats)
    private val artist4ImageCard: View = statsView.findViewById(R.id.artist4_image_stats)
    private val artist5Name: TextView = statsView.findViewById(R.id.artist5_name_stats)
    private val artist5Stats: TextView = statsView.findViewById(R.id.artist5_stats_stats)
    private val artist5ImageCard: View = statsView.findViewById(R.id.artist5_image_stats)
    
    // Top Albums UI elements
    private val album1Name: TextView = statsView.findViewById(R.id.album1_name_stats)
    private val album1Stats: TextView = statsView.findViewById(R.id.album1_artist_stats)
    private val album1ImageCard: View = statsView.findViewById(R.id.album1_image_stats)
    private val album2Name: TextView = statsView.findViewById(R.id.album2_name_stats)
    private val album2Stats: TextView = statsView.findViewById(R.id.album2_artist_stats)
    private val album2ImageCard: View = statsView.findViewById(R.id.album2_image_stats)
    private val album3Name: TextView = statsView.findViewById(R.id.album3_name_stats)
    private val album3Stats: TextView = statsView.findViewById(R.id.album3_artist_stats)
    private val album3ImageCard: View = statsView.findViewById(R.id.album3_image_stats)
    private val album4Name: TextView = statsView.findViewById(R.id.album4_name_stats)
    private val album4Stats: TextView = statsView.findViewById(R.id.album4_artist_stats)
    private val album4ImageCard: View = statsView.findViewById(R.id.album4_image_stats)
    private val album5Name: TextView = statsView.findViewById(R.id.album5_name_stats)
    private val album5Stats: TextView = statsView.findViewById(R.id.album5_artist_stats)
    private val album5ImageCard: View = statsView.findViewById(R.id.album5_image_stats)
    
    // Top Tracks UI elements
    private val track1Name: TextView = statsView.findViewById(R.id.track1_name_stats)
    private val track1Stats: TextView = statsView.findViewById(R.id.track1_artist_stats)
    private val track1ImageCard: View = statsView.findViewById(R.id.track1_image_stats)
    private val track2Name: TextView = statsView.findViewById(R.id.track2_name_stats)
    private val track2Stats: TextView = statsView.findViewById(R.id.track2_artist_stats)
    private val track2ImageCard: View = statsView.findViewById(R.id.track2_image_stats)
    private val track3Name: TextView = statsView.findViewById(R.id.track3_name_stats)
    private val track3Stats: TextView = statsView.findViewById(R.id.track3_artist_stats)
    private val track3ImageCard: View = statsView.findViewById(R.id.track3_image_stats)
    private val track4Name: TextView = statsView.findViewById(R.id.track4_name_stats)
    private val track4Stats: TextView = statsView.findViewById(R.id.track4_artist_stats)
    private val track4ImageCard: View = statsView.findViewById(R.id.track4_image_stats)
    private val track5Name: TextView = statsView.findViewById(R.id.track5_name_stats)
    private val track5Stats: TextView = statsView.findViewById(R.id.track5_artist_stats)
    private val track5ImageCard: View = statsView.findViewById(R.id.track5_image_stats)
    
    companion object {
        private const val TAG = "StatsManager"
    }
    
    private var currentUserId: String = ""
    
    fun initialize(userId: String) {
        Log.d(TAG, "Initializing stats for user: $userId")
        currentUserId = userId
        updateDisplayName(userId)
        setupFilterButtons(userId)
        
        // Start with ALL_TIME to use cached stats (fast)
        currentTimeRange = TimeRange.ALL_TIME
        selectFilter(TimeRange.ALL_TIME)
        loadStatistics(userId, TimeRange.ALL_TIME)
    }
    
    fun refresh() {
        if (currentUserId.isNotEmpty()) {
            Log.d(TAG, "Refreshing stats for user: $currentUserId")
            loadStatistics(currentUserId, currentTimeRange)
        }
    }
    
    private fun updateDisplayName(userId: String) {
        lifecycleScope.launch {
            // Get display name using helper (handles caching and Firebase fallback)
            val displayName = DisplayNameHelper.getDisplayName(
                context = statsView.context,
                userId = userId,
                defaultName = "unknown"
            )
            
            // Update handle text
            handleStats.text = "@$displayName"
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
        FilterHelper.updateFilterSelection(
            context = statsView.context,
            timeRange = timeRange,
            filter7Days = filter7Days,
            filter30Days = filter30Days,
            filter6Months = filter6Months,
            filterAllTime = filterAllTime
        )
    }
    
    private fun loadStatistics(userId: String, timeRange: TimeRange) {
        Log.d(TAG, "Loading statistics for ${timeRange.name}...")
        
        // Show skeleton loaders immediately
        showSkeletonLoading()
        
        lifecycleScope.launch {
            try {
                if (timeRange == TimeRange.ALL_TIME) {
                    // For ALL_TIME, use cached stats (fast)
                    Log.d(TAG, "Fetching cached user stats...")
                    val userStats = withContext(Dispatchers.IO) {
                        statsManager.getUserStats(userId, timeRange)
                    }
                    
                    if (userStats == null) {
                        Log.w(TAG, "No statistics found")
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                statsView.context,
                                "No statistics found. Please import your Spotify data first.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        return@launch
                    }
                    
                    val topArtists = withContext(Dispatchers.IO) {
                        statsManager.getTopArtists(userId, timeRange, limit = 5)
                    }
                    Log.d(TAG, "Got ${topArtists.size} artists")
                    
                    val topAlbums = withContext(Dispatchers.IO) {
                        statsManager.getTopAlbums(userId, timeRange, limit = 5)
                    }
                    Log.d(TAG, "Got ${topAlbums.size} albums")
                    
                    val topTracks = withContext(Dispatchers.IO) {
                        statsManager.getTopTracks(userId, timeRange, limit = 5)
                    }
                    Log.d(TAG, "Got ${topTracks.size} tracks")
                    
                    updateUI(userStats, topArtists, topAlbums, topTracks)
                    return@launch
                }
                
                // For time-based filters, calculate on-the-fly
                Log.d(TAG, "Calculating time-based stats for ${timeRange.name}...")
                val result = withContext(Dispatchers.IO) {
                    statsManager.calculateTimeBasedStats(userId, timeRange, limit = 5)
                }
                Log.d(TAG, "Calculated stats: ${result.topArtists.size} artists, ${result.topAlbums.size} albums, ${result.topTracks.size} tracks")
                
                updateUI(result.userStats, result.topArtists, result.topAlbums, result.topTracks)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading statistics: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }
    
    private fun updateUI(
        userStats: UserStats?,
        topArtists: List<ArtistStats>,
        topAlbums: List<AlbumStats>,
        topTracks: List<TrackStats>
    ) {
        // Update stats values
        userStats?.let { stats ->
            val minutes = stats.totalListeningTimeMs / 60000
            minutesValue.text = formatNumber(minutes)
            artistsValue.text = formatNumber(stats.uniqueArtists)
            streamsValue.text = formatNumber(stats.totalStreams)
            tracksValue.text = formatNumber(stats.uniqueTracks)
            albumsValue.text = formatNumber(stats.uniqueAlbums)
        }
        
        // Update top artists
        if (topArtists.isNotEmpty()) {
            Log.d(TAG, "Updating ${topArtists.size} artists")
            topArtists.getOrNull(0)?.let { artist ->
                Log.d(TAG, "Artist 1: ${artist.artistName}, imageUrl: ${artist.imageUrl}")
                artist1Name.text = artist.artistName
                val minutes = artist.totalListeningTimeMs / 60000
                artist1Stats.text = "$minutes minutes ✦ ${artist.playCount} streams"
                loadArtistImage(artist1ImageCard, artist.imageUrl, artist.artistName)
            }
            topArtists.getOrNull(1)?.let { artist ->
                artist2Name.text = artist.artistName
                val minutes = artist.totalListeningTimeMs / 60000
                artist2Stats.text = "$minutes minutes ✦ ${artist.playCount} streams"
                loadArtistImage(artist2ImageCard, artist.imageUrl, artist.artistName)
            }
            topArtists.getOrNull(2)?.let { artist ->
                artist3Name.text = artist.artistName
                val minutes = artist.totalListeningTimeMs / 60000
                artist3Stats.text = "$minutes minutes ✦ ${artist.playCount} streams"
                loadArtistImage(artist3ImageCard, artist.imageUrl, artist.artistName)
            }
            topArtists.getOrNull(3)?.let { artist ->
                artist4Name.text = artist.artistName
                val minutes = artist.totalListeningTimeMs / 60000
                artist4Stats.text = "$minutes minutes ✦ ${artist.playCount} streams"
                loadArtistImage(artist4ImageCard, artist.imageUrl, artist.artistName)
            }
            topArtists.getOrNull(4)?.let { artist ->
                artist5Name.text = artist.artistName
                val minutes = artist.totalListeningTimeMs / 60000
                artist5Stats.text = "$minutes minutes ✦ ${artist.playCount} streams"
                loadArtistImage(artist5ImageCard, artist.imageUrl, artist.artistName)
            }
        }
        
        // Update top albums
        if (topAlbums.isNotEmpty()) {
            topAlbums.getOrNull(0)?.let { album ->
                album1Name.text = album.albumName
                val minutes = album.totalListeningTimeMs / 60000
                album1Stats.text = "$minutes minutes ✦ ${album.playCount} streams"
                loadAlbumImage(album1ImageCard, album.imageUrl, album.albumName, album.artistName)
            }
            topAlbums.getOrNull(1)?.let { album ->
                album2Name.text = album.albumName
                val minutes = album.totalListeningTimeMs / 60000
                album2Stats.text = "$minutes minutes ✦ ${album.playCount} streams"
                loadAlbumImage(album2ImageCard, album.imageUrl, album.albumName, album.artistName)
            }
            topAlbums.getOrNull(2)?.let { album ->
                album3Name.text = album.albumName
                val minutes = album.totalListeningTimeMs / 60000
                album3Stats.text = "$minutes minutes ✦ ${album.playCount} streams"
                loadAlbumImage(album3ImageCard, album.imageUrl, album.albumName, album.artistName)
            }
            topAlbums.getOrNull(3)?.let { album ->
                album4Name.text = album.albumName
                val minutes = album.totalListeningTimeMs / 60000
                album4Stats.text = "$minutes minutes ✦ ${album.playCount} streams"
                loadAlbumImage(album4ImageCard, album.imageUrl, album.albumName, album.artistName)
            }
            topAlbums.getOrNull(4)?.let { album ->
                album5Name.text = album.albumName
                val minutes = album.totalListeningTimeMs / 60000
                album5Stats.text = "$minutes minutes ✦ ${album.playCount} streams"
                loadAlbumImage(album5ImageCard, album.imageUrl, album.albumName, album.artistName)
            }
        }
        
        // Update top tracks
        if (topTracks.isNotEmpty()) {
            topTracks.getOrNull(0)?.let { track ->
                track1Name.text = track.trackName
                val minutes = track.totalListeningTimeMs / 60000
                track1Stats.text = "$minutes minutes ✦ ${track.playCount} streams"
                loadAlbumImage(track1ImageCard, null, track.albumName, track.artistName)
            }
            topTracks.getOrNull(1)?.let { track ->
                track2Name.text = track.trackName
                val minutes = track.totalListeningTimeMs / 60000
                track2Stats.text = "$minutes minutes ✦ ${track.playCount} streams"
                loadAlbumImage(track2ImageCard, null, track.albumName, track.artistName)
            }
            topTracks.getOrNull(2)?.let { track ->
                track3Name.text = track.trackName
                val minutes = track.totalListeningTimeMs / 60000
                track3Stats.text = "$minutes minutes ✦ ${track.playCount} streams"
                loadAlbumImage(track3ImageCard, null, track.albumName, track.artistName)
            }
            topTracks.getOrNull(3)?.let { track ->
                track4Name.text = track.trackName
                val minutes = track.totalListeningTimeMs / 60000
                track4Stats.text = "$minutes minutes ✦ ${track.playCount} streams"
                loadAlbumImage(track4ImageCard, null, track.albumName, track.artistName)
            }
            topTracks.getOrNull(4)?.let { track ->
                track5Name.text = track.trackName
                val minutes = track.totalListeningTimeMs / 60000
                track5Stats.text = "$minutes minutes ✦ ${track.playCount} streams"
                loadAlbumImage(track5ImageCard, null, track.albumName, track.artistName)
            }
        }
        
        // Hide skeletons and show values
        hideSkeletonLoading()
        
        Log.d(TAG, "Updated stats UI")
    }
    
    private fun loadArtistImage(cardView: View, imageUrl: String?, artistName: String) {
        val imageView = (cardView as? android.view.ViewGroup)?.getChildAt(0) as? ImageView
        if (imageView != null) {
            if (!imageUrl.isNullOrEmpty()) {
                Glide.with(statsView.context)
                    .load(imageUrl)
                    .centerCrop()
                    .into(imageView)
            } else {
                lifecycleScope.launch {
                    val fetchedImageUrl = withContext(Dispatchers.IO) {
                        imageLoader.getArtistImageUrl(artistName)
                    }
                    if (fetchedImageUrl != null) {
                        Glide.with(statsView.context)
                            .load(fetchedImageUrl)
                            .centerCrop()
                            .into(imageView)
                    }
                }
            }
        }
    }
    
    private fun loadAlbumImage(cardView: View, imageUrl: String?, albumName: String, artistName: String) {
        val imageView = (cardView as? android.view.ViewGroup)?.getChildAt(0) as? ImageView
        if (imageView != null) {
            if (!imageUrl.isNullOrEmpty()) {
                Glide.with(statsView.context)
                    .load(imageUrl)
                    .centerCrop()
                    .into(imageView)
            } else {
                lifecycleScope.launch {
                    val fetchedImageUrl = withContext(Dispatchers.IO) {
                        imageLoader.getAlbumImageUrl(albumName, artistName)
                    }
                    if (fetchedImageUrl != null) {
                        Glide.with(statsView.context)
                            .load(fetchedImageUrl)
                            .centerCrop()
                            .into(imageView)
                    }
                }
            }
        }
    }
    
    private fun showSkeletonLoading() {
        // Hide values and show skeletons
        minutesValue.visibility = View.INVISIBLE
        artistsValue.visibility = View.INVISIBLE
        streamsValue.visibility = View.INVISIBLE
        tracksValue.visibility = View.INVISIBLE
        albumsValue.visibility = View.INVISIBLE
        
        minutesSkeleton.visibility = View.VISIBLE
        artistsSkeleton.visibility = View.VISIBLE
        streamsSkeleton.visibility = View.VISIBLE
        tracksSkeleton.visibility = View.VISIBLE
        albumsSkeleton.visibility = View.VISIBLE
    }
    
    private fun hideSkeletonLoading() {
        // Show values and hide skeletons
        minutesSkeleton.visibility = View.GONE
        artistsSkeleton.visibility = View.GONE
        streamsSkeleton.visibility = View.GONE
        tracksSkeleton.visibility = View.GONE
        albumsSkeleton.visibility = View.GONE
        
        minutesValue.visibility = View.VISIBLE
        artistsValue.visibility = View.VISIBLE
        streamsValue.visibility = View.VISIBLE
        tracksValue.visibility = View.VISIBLE
        albumsValue.visibility = View.VISIBLE
    }
    
    private fun formatNumber(number: Int): String {
        return when {
            number >= 1000 -> {
                val valueInK = number / 1000.0
                // Determine decimals based on digit count
                // 100-999K (3 digits): 0 decimals → 143K
                // 10-99K (2 digits): 1 decimal → 46.0K
                // 1-9K (1 digit): 2 decimals → 3.00K
                val decimals = when {
                    valueInK >= 100 -> 0
                    valueInK >= 10 -> 1
                    else -> 2
                }
                String.format(Locale.US, "%.${decimals}fK", valueInK)
            }
            else -> NumberFormat.getInstance(Locale.US).format(number)
        }
    }
    
    private fun formatNumber(number: Long): String {
        return formatNumber(number.toInt())
    }
}
