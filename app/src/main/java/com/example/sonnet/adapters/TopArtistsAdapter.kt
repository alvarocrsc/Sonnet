package com.example.sonnet.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.sonnet.R
import com.example.sonnet.models.stats.ArtistStats
import com.example.sonnet.spotify.SpotifyImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TopArtistsAdapter : RecyclerView.Adapter<TopArtistsAdapter.ArtistViewHolder>() {

    private val artists = mutableListOf<ArtistStats>()

    fun setArtists(newArtists: List<ArtistStats>) {
        artists.clear()
        artists.addAll(newArtists)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_top_artist, parent, false)
        return ArtistViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
        holder.bind(artists[position])
    }

    override fun getItemCount(): Int = artists.size

    class ArtistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val artistImage: ImageView = itemView.findViewById(R.id.artist_image)
        private val artistRank: TextView = itemView.findViewById(R.id.artist_rank)
        private val artistName: TextView = itemView.findViewById(R.id.artist_name)
        private val artistStats: TextView = itemView.findViewById(R.id.artist_stats)
        private val imageLoader = SpotifyImageLoader(itemView.context)

        fun bind(artist: ArtistStats) {
            // Set rank
            artistRank.text = "${artist.rank}."
            
            // Set name
            artistName.text = artist.artistName
            
            // Calculate minutes from milliseconds
            val minutes = artist.totalListeningTimeMs / 60000
            
            // Set stats (minutes ✦ streams)
            artistStats.text = "$minutes minutes ✦ ${artist.playCount} streams"
            
            // Load artist image
            if (!artist.imageUrl.isNullOrEmpty()) {
                // Use cached image URL from Firestore
                Glide.with(itemView.context)
                    .load(artist.imageUrl)
                    .transform(CircleCrop())
                    .placeholder(R.drawable.artist_placeholder)
                    .error(R.drawable.artist_placeholder)
                    .into(artistImage)
            } else {
                // Fetch image from Spotify API
                artistImage.setImageResource(R.drawable.artist_placeholder)
                CoroutineScope(Dispatchers.Main).launch {
                    val imageUrl = withContext(Dispatchers.IO) {
                        imageLoader.getArtistImageUrl(artist.artistName)
                    }
                    if (imageUrl != null) {
                        Glide.with(itemView.context)
                            .load(imageUrl)
                            .transform(CircleCrop())
                            .placeholder(R.drawable.artist_placeholder)
                            .error(R.drawable.artist_placeholder)
                            .into(artistImage)
                    }
                }
            }
        }
    }
}
