package com.webscare.pixels.domain.usecase

import com.webscare.pixels.domain.model.Photo
import com.webscare.pixels.domain.repository.PhotoRepository

class GetCuratedPhotosUseCase(private val repository: PhotoRepository) {

    suspend operator fun invoke(page: Int, forceRefresh: Boolean = false): Result<List<Photo>> {
        return repository.getCuratedPhotos(page, forceRefresh)
    }
}
