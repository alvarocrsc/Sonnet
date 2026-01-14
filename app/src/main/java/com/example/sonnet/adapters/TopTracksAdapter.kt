package com.example.sonnet.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.sonnet.R
import com.example.sonnet.models.stats.TrackStats
import com.example.sonnet.spotify.SpotifyImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TopTracksAdapter : RecyclerView.Adapter<TopTracksAdapter.TrackViewHolder>() {

    private val tracks = mutableListOf<TrackStats>()

    fun setTracks(newTracks: List<TrackStats>) {
        tracks.clear()
        tracks.addAll(newTracks)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_top_track, parent, false)
        return TrackViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(tracks[position])
    }

    override fun getItemCount(): Int = tracks.size

    class TrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val trackImage: ImageView = itemView.findViewById(R.id.track_image)
        private val trackRank: TextView = itemView.findViewById(R.id.track_rank)
        private val trackName: TextView = itemView.findViewById(R.id.track_name)
        private val trackStats: TextView = itemView.findViewById(R.id.track_stats)
        private val imageLoader = SpotifyImageLoader(itemView.context)

        fun bind(track: TrackStats) {
            // Set rank
            trackRank.text = "${track.rank}."
            
            // Set name
            trackName.text = track.trackName
            
            // Calculate minutes from milliseconds
            val minutes = track.totalListeningTimeMs / 60000
            
            // Set stats (minutes ✦ streams)
            trackStats.text = "$minutes minutes ✦ ${track.playCount} streams"
            
            // Load track image (album cover)
            trackImage.setImageResource(R.drawable.album_placeholder)
            CoroutineScope(Dispatchers.Main).launch {
                val imageUrl = withContext(Dispatchers.IO) {
                    imageLoader.getAlbumImageUrl(track.albumName, track.artistName)
                }
                if (imageUrl != null) {
                    Glide.with(itemView.context)
                        .load(imageUrl)
                        .transform(RoundedCorners(24))
                        .placeholder(R.drawable.album_placeholder)
                        .error(R.drawable.album_placeholder)
                        .into(trackImage)
                }
            }
        }
    }
}
