package com.webscare.pixels.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.webscare.pixels.domain.model.Photo
import com.webscare.pixels.domain.usecase.GetCuratedPhotosUseCase
import com.webscare.pixels.domain.usecase.SearchPhotosUseCase
import com.webscare.pixels.presentation.state.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val getCuratedPhotosUseCase: GetCuratedPhotosUseCase,
    private val searchPhotosUseCase: SearchPhotosUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<Photo>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Photo>>> = _uiState.asStateFlow()

    // True while a page 2+ fetch is in flight — drives the bottom progress indicator
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    // One-shot events for pagination errors so the Activity can show a Snackbar
    private val _paginationError = MutableSharedFlow<String>()
    val paginationError: SharedFlow<String> = _paginationError.asSharedFlow()

    private var currentPage = 1
    private var currentQuery: String? = null // null = curated
    private val photosList = mutableListOf<Photo>()
    private val photoIds = HashSet<Int>() // O(1) dedup lookup
    private var isLoading = false
    private var hasMorePages = true
    private var fetchJob: Job? = null

    // Auto-skip: if a page returns ALL duplicates, jump ahead. Capped to avoid infinite loop.
    private var consecutiveDuplicatePages = 0
    private val maxConsecutiveDuplicatePages = 3

    init {
        loadCuratedPhotos(refresh = true)
    }

    fun loadCuratedPhotos(refresh: Boolean = false) {
        if (refresh) {
            resetState()
            currentQuery = null
            fetchPhotos(forceRefresh = false) // initial launch — use cache
        } else {
            fetchPhotos(forceRefresh = false)
        }
    }

    fun searchPhotos(query: String) {
        resetState()
        currentQuery = query.trim()
        _uiState.value = UiState.Loading
        fetchPhotos(forceRefresh = false)
    }

    fun loadNextPage() {
        if (isLoading || !hasMorePages) return
        // Reset the duplicate-skip counter on every fresh user-initiated scroll attempt
        consecutiveDuplicatePages = 0
        currentPage++
        fetchPhotos(forceRefresh = false)
    }

    /**
     * Called by the Snackbar Retry button after a pagination error.
     * Re-enables pagination (which the error path pauses to prevent scroll-spam)
     * then tries the same page again.
     */
    fun retryLoadMore() {
        if (isLoading) return
        hasMorePages = true
        loadNextPage()
    }

    /**
     * Re-fetch from page 1, BYPASSING cache. Use for swipe-to-refresh / Retry.
     */
    fun refreshCurrent() {
        resetState()
        fetchPhotos(forceRefresh = true) // bypass 24h cache
    }

    private fun resetState() {
        fetchJob?.cancel()
        isLoading = false
        currentPage = 1
        photosList.clear()
        photoIds.clear()
        hasMorePages = true
        consecutiveDuplicatePages = 0
    }

    private fun fetchPhotos(forceRefresh: Boolean) {
        if (isLoading) return
        isLoading = true

        val isPaginating = photosList.isNotEmpty()
        if (!isPaginating) {
            _uiState.value = UiState.Loading
        } else {
            _isLoadingMore.value = true
        }

        fetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = if (currentQuery == null) {
                    getCuratedPhotosUseCase(currentPage, forceRefresh)
                } else {
                    searchPhotosUseCase(currentQuery!!, currentPage, forceRefresh)
                }

                result.onSuccess { photos ->
                    when {
                        photos.isEmpty() && photosList.isEmpty() -> {
                            _uiState.value = UiState.Empty
                        }
                        photos.isEmpty() -> {
                            // True end of pagination
                            hasMorePages = false
                            _uiState.value = UiState.Success(data = ArrayList(photosList))
                        }
                        else -> {
                            // Dedup
                            var newCount = 0
                            for (photo in photos) {
                                if (photoIds.add(photo.id)) {
                                    photosList.add(photo)
                                    newCount++
                                }
                            }

                            if (newCount == 0) {
                                // All duplicates — try next page automatically (with safety cap).
                                // If we hit the cap, DON'T permanently disable pagination — just
                                // stop the auto-skip burst. The next user scroll will reset the
                                // counter and try again.
                                consecutiveDuplicatePages++
                                if (consecutiveDuplicatePages < maxConsecutiveDuplicatePages) {
                                    isLoading = false
                                    currentPage++
                                    fetchPhotos(forceRefresh = false)
                                    return@onSuccess
                                }
                                // else: fall through and emit current list; user can scroll again
                            } else {
                                consecutiveDuplicatePages = 0
                            }

                            _uiState.value = UiState.Success(data = ArrayList(photosList))
                        }
                    }
                }.onFailure { error ->
                    if (photosList.isEmpty()) {
                        _uiState.value = UiState.Error(error.message ?: "Unknown error")
                    } else {
                        // Roll back the page so retryLoadMore() will re-attempt the same page
                        if (currentPage > 1) currentPage--
                        // Pause scroll-triggered loading to avoid spamming the same Snackbar
                        // every time the user reaches the bottom. retryLoadMore() re-enables it.
                        hasMorePages = false
                        _paginationError.emit(error.message ?: "Failed to load more photos")
                    }
                }
            } finally {
                isLoading = false
                _isLoadingMore.value = false
            }
        }
    }

    class Factory(
        private val getCuratedPhotosUseCase: GetCuratedPhotosUseCase,
        private val searchPhotosUseCase: SearchPhotosUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(getCuratedPhotosUseCase, searchPhotosUseCase) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
