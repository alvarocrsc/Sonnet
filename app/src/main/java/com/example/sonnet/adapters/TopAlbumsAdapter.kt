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
import com.example.sonnet.models.stats.AlbumStats
import com.example.sonnet.spotify.SpotifyImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TopAlbumsAdapter : RecyclerView.Adapter<TopAlbumsAdapter.AlbumViewHolder>() {

    private val albums = mutableListOf<AlbumStats>()

    fun setAlbums(newAlbums: List<AlbumStats>) {
        albums.clear()
        albums.addAll(newAlbums)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_top_album, parent, false)
        return AlbumViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(albums[position])
    }

    override fun getItemCount(): Int = albums.size

    class AlbumViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val albumImage: ImageView = itemView.findViewById(R.id.album_image)
        private val albumRank: TextView = itemView.findViewById(R.id.album_rank)
        private val albumName: TextView = itemView.findViewById(R.id.album_name)
        private val albumStats: TextView = itemView.findViewById(R.id.album_stats)
        private val imageLoader = SpotifyImageLoader(itemView.context)

        fun bind(album: AlbumStats) {
            // Set rank
            albumRank.text = "${album.rank}."
            
            // Set name
            albumName.text = album.albumName
            
            // Calculate minutes from milliseconds
            val minutes = album.totalListeningTimeMs / 60000
            
            // Set stats (minutes ✦ streams)
            albumStats.text = "$minutes minutes ✦ ${album.playCount} streams"
            
            // Load album image
            if (!album.imageUrl.isNullOrEmpty()) {
                // Use cached image URL from Firestore
                Glide.with(itemView.context)
                    .load(album.imageUrl)
                    .transform(RoundedCorners(24)) // 10dp corner radius converted to pixels
                    .placeholder(R.drawable.album_placeholder)
                    .error(R.drawable.album_placeholder)
                    .into(albumImage)
            } else {
                // Fetch image from Spotify API
                albumImage.setImageResource(R.drawable.album_placeholder)
                CoroutineScope(Dispatchers.Main).launch {
                    val imageUrl = withContext(Dispatchers.IO) {
                        imageLoader.getAlbumImageUrl(album.albumName, album.artistName)
                    }
                    if (imageUrl != null) {
                        Glide.with(itemView.context)
                            .load(imageUrl)
                            .transform(RoundedCorners(24))
                            .placeholder(R.drawable.album_placeholder)
                            .error(R.drawable.album_placeholder)
                            .into(albumImage)
                    }
                }
            }
        }
    }
}
