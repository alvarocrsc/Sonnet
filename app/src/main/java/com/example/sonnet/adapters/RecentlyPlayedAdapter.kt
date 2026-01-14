package com.example.sonnet.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.sonnet.R
import com.example.sonnet.models.RecentlyPlayedTrack
import com.example.sonnet.spotify.SpotifyImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class RecentlyPlayedAdapter(
    private val lifecycleScope: LifecycleCoroutineScope,
    private val imageLoader: SpotifyImageLoader
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val tracks = mutableListOf<RecentlyPlayedTrack>()
    private var isLoading = true
    
    companion object {
        private const val TAG = "RecentlyPlayedAdapter"
        private const val VIEW_TYPE_SKELETON = 0
        private const val VIEW_TYPE_TRACK = 1
        private const val SKELETON_COUNT = 5
    }

    class TrackViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val trackImage: ImageView = view.findViewById(R.id.track_image_view)
        val trackName: TextView = view.findViewById(R.id.track_name)
        val artistName: TextView = view.findViewById(R.id.artist_name)
        val timeText: TextView = view.findViewById(R.id.time_text)
        val trackImageCard: View = view.findViewById(R.id.track_image)
    }
    
    class SkeletonViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemViewType(position: Int): Int {
        return if (isLoading) VIEW_TYPE_SKELETON else VIEW_TYPE_TRACK
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SKELETON) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recently_played_skeleton, parent, false)
            SkeletonViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recently_played, parent, false)
            TrackViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is TrackViewHolder) {
            val track = tracks[position]
            
            Log.d(TAG, "Binding track at position $position: ${track.trackName}")
            holder.trackName.text = track.trackName
            holder.artistName.text = track.artistName
            holder.timeText.text = getElapsedTime(track.playedAt)
            
            // Load image
            loadTrackImage(holder, track)
        }
        // Skeleton items don't need binding
    }

    override fun getItemCount(): Int {
        val count = if (isLoading) SKELETON_COUNT else tracks.size
        Log.d(TAG, "getItemCount: $count (isLoading: $isLoading)")
        return count
    }

    fun updateTracks(newTracks: List<RecentlyPlayedTrack>) {
        Log.d(TAG, "updateTracks called with ${newTracks.size} tracks")
        isLoading = false
        tracks.clear()
        tracks.addAll(newTracks)
        notifyDataSetChanged()
    }

    private fun loadTrackImage(holder: TrackViewHolder, track: RecentlyPlayedTrack) {
        if (!track.imageUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(track.imageUrl)
                .centerCrop()
                .into(holder.trackImage)
        } else {
            // Fetch album image from Spotify API
            lifecycleScope.launch {
                val fetchedUrl = withContext(Dispatchers.IO) {
                    imageLoader.getAlbumImageUrl(track.albumName, track.artistName)
                }
                if (fetchedUrl != null) {
                    Glide.with(holder.itemView.context)
                        .load(fetchedUrl)
                        .centerCrop()
                        .into(holder.trackImage)
                }
            }
        }
    }

    private fun getElapsedTime(playedAtMs: Long): String {
        val now = System.currentTimeMillis()
        val diffMs = now - playedAtMs
        
        val days = TimeUnit.MILLISECONDS.toDays(diffMs)
        if (days > 0) return "${days}d"
        
        val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
        if (hours > 0) return "${hours}h"
        
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
        if (minutes > 0) return "${minutes}m"
        
        val seconds = TimeUnit.MILLISECONDS.toSeconds(diffMs)
        return "${seconds}s"
    }
}
