package com.webscare.pixels.domain.repository

import com.webscare.pixels.domain.model.Photo

interface PhotoRepository {

    suspend fun getCuratedPhotos(page: Int, forceRefresh: Boolean = false): Result<List<Photo>>

    suspend fun searchPhotos(query: String, page: Int, forceRefresh: Boolean = false): Result<List<Photo>>
}
