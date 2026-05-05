package com.webscare.pixels.presentation.detail

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import coil3.load
import coil3.request.crossfade
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.webscare.pixels.R

class DetailActivity : AppCompatActivity() {

    private lateinit var viewModel: DetailViewModel

    companion object {
        const val EXTRA_PHOTO_ID = "extra_photo_id"
        const val EXTRA_PHOTO_URL = "extra_photo_url"
        const val EXTRA_PHOTO_MEDIUM = "extra_photo_medium"
        const val EXTRA_PHOTO_ORIGINAL = "extra_photo_original"
        const val EXTRA_PHOTOGRAPHER = "extra_photographer"
        const val EXTRA_PHOTOGRAPHER_URL = "extra_photographer_url"
        const val EXTRA_WIDTH = "extra_width"
        const val EXTRA_HEIGHT = "extra_height"
        const val EXTRA_AVG_COLOR = "extra_avg_color"
        const val EXTRA_ALT = "extra_alt"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_detail)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viewModel = ViewModelProvider(this)[DetailViewModel::class.java]

        val photoId = intent.getIntExtra(EXTRA_PHOTO_ID, 0)
        val photoUrl = intent.getStringExtra(EXTRA_PHOTO_URL).orEmpty()
        val mediumUrl = intent.getStringExtra(EXTRA_PHOTO_MEDIUM).orEmpty()
        val originalUrl = intent.getStringExtra(EXTRA_PHOTO_ORIGINAL).orEmpty()
        val photographer = intent.getStringExtra(EXTRA_PHOTOGRAPHER).orEmpty()
        val photographerUrl = intent.getStringExtra(EXTRA_PHOTOGRAPHER_URL).orEmpty()
        val width = intent.getIntExtra(EXTRA_WIDTH, 0)
        val height = intent.getIntExtra(EXTRA_HEIGHT, 0)
        val avgColor = intent.getStringExtra(EXTRA_AVG_COLOR) ?: "#000000"
        val alt = intent.getStringExtra(EXTRA_ALT).orEmpty()

        val photoView: PhotoView = findViewById(R.id.photoDetail)
        val textPhotographer: TextView = findViewById(R.id.textDetailPhotographer)
        val textDimensions: TextView = findViewById(R.id.textDetailDimensions)
        val textAlt: TextView = findViewById(R.id.textDetailAlt)
        val btnDownload: MaterialButton = findViewById(R.id.btnDownload)
        val btnShare: MaterialButton = findViewById(R.id.btnShare)
        val bottomSheet: View = findViewById(R.id.bottomSheet)

        photoView.transitionName = "photo_$photoId"

        // Background placeholder color
        photoView.setBackgroundColor(parseColorSafe(avgColor))

        // Two-stage load: medium (cached/instant) → upgrade to large2x in background
        loadPhoto(photoView, mediumUrl, originalUrl, savedInstanceState == null)

        textPhotographer.text = photographer
        textDimensions.text = getString(R.string.photo_dimensions, width, height)
        textAlt.text = alt
        textAlt.visibility = if (alt.isBlank()) View.GONE else View.VISIBLE

        // Tap photo to toggle immersive mode
        photoView.setOnPhotoTapListener { _, _, _ ->
            viewModel.toggleImmersiveMode()
            bottomSheet.visibility = if (viewModel.isImmersiveMode) View.GONE else View.VISIBLE
        }

        btnDownload.setOnClickListener {
            if (originalUrl.isBlank()) {
                Snackbar.make(bottomSheet, "Image URL unavailable", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            downloadPhoto(originalUrl, "pexels_$photoId.jpeg")
            Snackbar.make(bottomSheet, "Download started", Snackbar.LENGTH_SHORT)
                .setAnchorView(bottomSheet)
                .show()
        }

        btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    getString(R.string.share_text_template, photographer, photographerUrl)
                )
            }
            startActivity(Intent.createChooser(shareIntent, "Share photo"))
        }
    }

    private fun loadPhoto(photoView: PhotoView, mediumUrl: String, highResUrl: String, isFirstLaunch: Boolean) {
        val isOnline = com.webscare.pixels.util.NetworkUtil.isNetworkAvailable(this)

        // If offline or no high-res available, just load the cached medium image and stop.
        if (!isOnline || highResUrl.isBlank() || highResUrl == mediumUrl) {
            photoView.load(mediumUrl) { crossfade(true) }
            
            if (!isOnline && highResUrl.isNotBlank() && highResUrl != mediumUrl && isFirstLaunch) {
                photoView.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        com.google.android.material.snackbar.Snackbar.make(
                            findViewById(R.id.main),
                            "Offline: Showing cached thumbnail",
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                        ).apply {
                            val bottomSheet = findViewById<View>(R.id.bottomSheet)
                            if (bottomSheet?.visibility == View.VISIBLE) {
                                anchorView = bottomSheet
                            }
                        }.show()
                    }
                }, 400) // Wait for shared element transition to finish
            }
            return
        }

        photoView.load(mediumUrl) {
            crossfade(false)
            listener(
                onSuccess = { _, result ->
                    photoView.load(highResUrl) { 
                        crossfade(true)
                        // Use the successfully loaded medium image as a placeholder
                        placeholder(result.image)
                        // Keep medium image if the high-res download fails mid-flight
                        error(result.image)
                    }
                },
                onError = { _, _ ->
                    photoView.load(highResUrl) { crossfade(true) }
                }
            )
        }
    }

    private fun parseColorSafe(hex: String): Int = try {
        Color.parseColor(hex)
    } catch (_: Exception) {
        Color.BLACK
    }

    private fun downloadPhoto(url: String, fileName: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("Downloading photo from Pexels")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "Pixels/$fileName")
            }
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
        } catch (e: Exception) {
            Snackbar.make(
                findViewById(R.id.main),
                "Download failed: ${e.message}",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }
}
