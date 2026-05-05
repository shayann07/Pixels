package com.webscare.pixels.data.repository

import android.content.Context
import com.webscare.pixels.data.local.db.PhotoDao
import com.webscare.pixels.data.local.mapper.PhotoEntityMapper.toDomain
import com.webscare.pixels.data.local.mapper.PhotoEntityMapper.toEntity
import com.webscare.pixels.data.remote.PexelsApiService
import com.webscare.pixels.data.remote.dto.PexelsPageResponse
import com.webscare.pixels.domain.model.Photo
import com.webscare.pixels.domain.repository.PhotoRepository
import com.webscare.pixels.util.Constants

class PhotoRepositoryImpl(
    private val photoDao: PhotoDao,
    private val apiService: PexelsApiService,
    private val context: Context
) : PhotoRepository {

    override suspend fun getCuratedPhotos(page: Int, forceRefresh: Boolean): Result<List<Photo>> =
        getPhotos("curated", page, forceRefresh) { apiService.getCuratedPhotos(page) }

    override suspend fun searchPhotos(query: String, page: Int, forceRefresh: Boolean): Result<List<Photo>> =
        getPhotos(query, page, forceRefresh) { apiService.searchPhotos(query, page) }

    private suspend fun getPhotos(
        query: String,
        page: Int,
        forceRefresh: Boolean,
        apiFetch: suspend () -> PexelsPageResponse
    ): Result<List<Photo>> {
        // 1. Page 1 + not forced: serve fresh cache (< TTL) without hitting API
        if (page == 1 && !forceRefresh) {
            val cached = photoDao.getPhotosByQuery(query, Constants.PER_PAGE)
            if (cached.isNotEmpty() &&
                (System.currentTimeMillis() - cached.first().cachedAt) < Constants.CACHE_TTL_MS
            ) {
                return Result.success(cached.map { it.toDomain() })
            }
        }

        // 2. Rate limit guard — page 1 only.
        //    If the tracked header says we're nearly out of credits, serve page 1 from cache
        //    rather than burning the last request. For page > 1 we skip this guard and let the
        //    real API call happen: Pexels returns HTTP 429 if genuinely exhausted, which Volley
        //    surfaces as a proper error. Firing the guard pre-emptively for page > 1 causes
        //    false "Rate limit reached" messages right after a successful refresh.
        if (page == 1 && PexelsApiService.lastRateLimitRemaining <= 5) {
            val cached = photoDao.getPhotosByQuery(query)
            if (cached.isNotEmpty()) return Result.success(cached.map { it.toDomain() })
        }

        // 3. Fetch from API
        return try {
            val response = apiFetch()
            val now = System.currentTimeMillis()
            val entities = response.photos.map { it.toEntity(query, now) }

            // Page 1 = fresh data, clear old cache for this query
            if (page == 1) photoDao.deleteByQuery(query)
            photoDao.insertPhotos(entities)

            // Maintenance: prune stale + bound cache size
            photoDao.deleteOlderThan(now - Constants.CACHE_TTL_MS)
            photoDao.pruneExcess(query)

            val freshDomain = entities.map { it.toDomain() }
            Result.success(freshDomain)
        } catch (e: Exception) {
            // 4. Graceful fallback to cache — page 1 only (see note above).
            if (page == 1) {
                val cached = photoDao.getPhotosByQuery(query)
                if (cached.isNotEmpty()) Result.success(cached.map { it.toDomain() })
                else Result.failure(e)
            } else {
                Result.failure(e)
            }
        }
    }
}
