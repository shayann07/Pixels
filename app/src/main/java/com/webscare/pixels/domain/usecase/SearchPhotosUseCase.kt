package com.webscare.pixels.domain.usecase

import com.webscare.pixels.domain.model.Photo
import com.webscare.pixels.domain.repository.PhotoRepository

class SearchPhotosUseCase(private val repository: PhotoRepository) {

    suspend operator fun invoke(query: String, page: Int, forceRefresh: Boolean = false): Result<List<Photo>> {
        return repository.searchPhotos(query, page, forceRefresh)
    }
}
