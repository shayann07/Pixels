package com.webscare.pixels.presentation.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import com.webscare.pixels.R
import com.webscare.pixels.domain.model.Photo

class PhotoAdapter(
    private val onItemClick: (Photo, ImageView) -> Unit
) : ListAdapter<Photo, PhotoAdapter.PhotoViewHolder>(PhotoDiffCallback()) {

    // Cached column width — calculated once from first parent
    private var columnWidthPx: Int = 0

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        if (columnWidthPx == 0) {
            // Account for RecyclerView padding (4dp each side) + card margin (4dp each side) = 16dp total per column
            val parentWidth = parent.width.takeIf { it > 0 }
                ?: parent.context.resources.displayMetrics.widthPixels
            val horizontalPaddingPx = (16 * parent.context.resources.displayMetrics.density).toInt()
            columnWidthPx = (parentWidth - horizontalPaddingPx) / 2
        }
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position), columnWidthPx)
    }

    override fun onViewRecycled(holder: PhotoViewHolder) {
        super.onViewRecycled(holder)
        holder.clear()
    }

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imagePhoto: ImageView = itemView.findViewById(R.id.imagePhoto)
        private val textPhotographer: TextView = itemView.findViewById(R.id.textPhotographer)

        fun bind(photo: Photo, columnWidth: Int) {
            // Set aspect ratio height — modify layoutParams in place (no allocation)
            val aspectRatio = photo.height.toFloat() / photo.width.toFloat()
            val targetHeight = (columnWidth * aspectRatio).toInt()
            if (imagePhoto.layoutParams.height != targetHeight) {
                imagePhoto.layoutParams.height = targetHeight
                imagePhoto.requestLayout()
            }

            // Placeholder background color (parsed safely)
            val bgColor = parseColorSafe(photo.avgColor)
            imagePhoto.setBackgroundColor(bgColor)

            // Load image with Coil — explicit size hint helps Coil pick optimal bitmap
            imagePhoto.load(photo.srcMedium) {
                crossfade(true)
                size(columnWidth, targetHeight)
            }

            textPhotographer.text = photo.photographer
            imagePhoto.transitionName = "photo_${photo.id}"

            // Click on image (not text/card) — keeps shared element clean
            imagePhoto.setOnClickListener { 
                imagePhoto.isEnabled = false
                imagePhoto.postDelayed({ imagePhoto.isEnabled = true }, 500)
                onItemClick(photo, imagePhoto) 
            }
        }

        fun clear() {
            // Cancel pending Coil request when view is recycled (Coil handles this automatically
            // via tag, but explicit clear avoids flashes of old bitmap)
            imagePhoto.setImageDrawable(null)
        }
    }

    private fun parseColorSafe(hex: String): Int = try {
        Color.parseColor(hex)
    } catch (_: Exception) {
        Color.GRAY
    }

    class PhotoDiffCallback : DiffUtil.ItemCallback<Photo>() {
        override fun areItemsTheSame(oldItem: Photo, newItem: Photo): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Photo, newItem: Photo): Boolean =
            oldItem == newItem
    }
}
