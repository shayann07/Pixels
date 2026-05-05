package com.webscare.pixels.presentation.main

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.webscare.pixels.R
import com.webscare.pixels.di.ServiceLocator
import com.webscare.pixels.domain.model.Photo
import com.webscare.pixels.presentation.adapter.PhotoAdapter
import com.webscare.pixels.presentation.detail.DetailActivity
import com.webscare.pixels.presentation.state.UiState
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var photoAdapter: PhotoAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var shimmerLayout: ShimmerFrameLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var offlineBanner: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var loadMoreProgress: ProgressBar
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Tracks whether user has actually performed a search
    // Prevents wiping the curated feed when search bar is just opened/closed
    private var hasActiveSearch = false

    // Set to true when a swipe-to-refresh is triggered so showSuccess/showError can toast feedback
    private var swipeRefreshPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        ServiceLocator.init(this)

        val factory = MainViewModel.Factory(
            ServiceLocator.getCuratedPhotosUseCase,
            ServiceLocator.searchPhotosUseCase
        )
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerPhotos)
        shimmerLayout = findViewById(R.id.shimmerLayout)
        emptyState = findViewById(R.id.emptyState)
        offlineBanner = findViewById(R.id.offlineBanner)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        loadMoreProgress = findViewById(R.id.loadMoreProgress)

        setSupportActionBar(toolbar)
        setupRecyclerView()
        setupSwipeRefresh()
        observeState()
        registerNetworkCallback()
        checkInitialNetworkState()
    }

    private fun checkInitialNetworkState() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        offlineBanner.visibility = if (online) View.GONE else View.VISIBLE
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread { offlineBanner.visibility = View.GONE }
            }

            override fun onLost(network: Network) {
                runOnUiThread { offlineBanner.visibility = View.VISIBLE }
            }
        }
        cm.registerNetworkCallback(request, networkCallback!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
    }

    private fun setupRecyclerView() {
        photoAdapter = PhotoAdapter { photo, imageView ->
            navigateToDetail(photo, imageView)
        }

        val layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        layoutManager.gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS

        recyclerView.apply {
            this.layoutManager = layoutManager
            adapter = photoAdapter
            setHasFixedSize(false)
            // Larger cache reduces re-binds during fast scroll on staggered grid
            setItemViewCacheSize(8)
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                checkLoadMore(layoutManager)
            }

            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    checkLoadMore(layoutManager)
                }
            }

            private fun checkLoadMore(lm: StaggeredGridLayoutManager) {
                val totalItemCount = lm.itemCount
                if (totalItemCount == 0) return
                val lastVisibleItems = lm.findLastVisibleItemPositions(null)
                val lastVisibleItem = lastVisibleItems.maxOrNull() ?: 0

                if (lastVisibleItem >= totalItemCount - 5) {
                    viewModel.loadNextPage()
                }
            }
        })
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(
            com.google.android.material.R.color.design_default_color_primary
        )
        swipeRefresh.setOnRefreshListener {
            swipeRefreshPending = true
            if (hasActiveSearch) {
                // Re-run current search instead of switching back to curated
                viewModel.refreshCurrent()
            } else {
                viewModel.loadCuratedPhotos(refresh = true)
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Loading -> showLoading()
                        is UiState.Success -> showSuccess(state.data)
                        is UiState.Error -> showError(state.message)
                        is UiState.Empty -> showEmpty()
                    }
                }
            }
        }

        // Show/hide the bottom spinner while paginating
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoadingMore.collect { loading ->
                    loadMoreProgress.visibility = if (loading) View.VISIBLE else View.GONE
                }
            }
        }

        // Show a Snackbar with a Retry button whenever a page 2+ fetch fails
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.paginationError.collect { message ->
                    val isOnline = com.webscare.pixels.util.NetworkUtil.isNetworkAvailable(this@MainActivity)
                    val displayMsg = if (!isOnline) "Offline: Cannot load more photos" else message
                    Snackbar.make(findViewById(R.id.main), displayMsg, Snackbar.LENGTH_LONG)
                        .setAction("Retry") { viewModel.retryLoadMore() }
                        .show()
                }
            }
        }
    }

    private fun showLoading() {
        shimmerLayout.visibility = View.VISIBLE
        shimmerLayout.startShimmer()
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.GONE
        swipeRefresh.isRefreshing = false
    }

    private fun showSuccess(photos: List<Photo>) {
        shimmerLayout.stopShimmer()
        shimmerLayout.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        swipeRefresh.isRefreshing = false

        photoAdapter.submitList(photos)

        if (swipeRefreshPending) {
            swipeRefreshPending = false
            val label = if (hasActiveSearch) "Search refreshed" else "Photos refreshed"
            Snackbar.make(findViewById(R.id.main), label, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showError(message: String) {
        shimmerLayout.stopShimmer()
        shimmerLayout.visibility = View.GONE
        swipeRefresh.isRefreshing = false
        swipeRefreshPending = false   // clear regardless so it doesn't linger

        val isOnline = com.webscare.pixels.util.NetworkUtil.isNetworkAvailable(this)
        val displayMsg = if (!isOnline) "Offline: Please connect to the internet" else message

        Snackbar.make(findViewById(R.id.main), displayMsg, Snackbar.LENGTH_LONG)
            .setAction("Retry") {
                if (hasActiveSearch) viewModel.refreshCurrent()
                else viewModel.loadCuratedPhotos(refresh = true)
            }
            .show()
    }

    private fun showEmpty() {
        shimmerLayout.stopShimmer()
        shimmerLayout.visibility = View.GONE
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        swipeRefresh.isRefreshing = false

        if (swipeRefreshPending) {
            swipeRefreshPending = false
            Snackbar.make(findViewById(R.id.main), "No photos found", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun navigateToDetail(photo: Photo, imageView: ImageView) {
        val intent = Intent(this, DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_PHOTO_ID, photo.id)
            putExtra(DetailActivity.EXTRA_PHOTO_URL, photo.srcLarge2x)
            putExtra(DetailActivity.EXTRA_PHOTO_MEDIUM, photo.srcMedium)
            putExtra(DetailActivity.EXTRA_PHOTO_ORIGINAL, photo.srcOriginal)
            putExtra(DetailActivity.EXTRA_PHOTOGRAPHER, photo.photographer)
            putExtra(DetailActivity.EXTRA_PHOTOGRAPHER_URL, photo.photographerUrl)
            putExtra(DetailActivity.EXTRA_WIDTH, photo.width)
            putExtra(DetailActivity.EXTRA_HEIGHT, photo.height)
            putExtra(DetailActivity.EXTRA_AVG_COLOR, photo.avgColor)
            putExtra(DetailActivity.EXTRA_ALT, photo.alt)
        }

        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            this, imageView, "photo_${photo.id}"
        )
        startActivity(intent, options.toBundle())
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = "Search photos..."
        searchView.maxWidth = Int.MAX_VALUE

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.takeIf { it.isNotBlank() }?.let {
                    hasActiveSearch = true
                    viewModel.searchPhotos(it)
                    searchView.clearFocus()
                }
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean = false
        })

        searchItem.setOnActionExpandListener(object : android.view.MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: android.view.MenuItem): Boolean = true
            override fun onMenuItemActionCollapse(item: android.view.MenuItem): Boolean {
                // Only revert to curated if user actually had an active search
                if (hasActiveSearch) {
                    hasActiveSearch = false
                    viewModel.loadCuratedPhotos(refresh = true)
                }
                return true
            }
        })

        return true
    }
}

