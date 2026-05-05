package com.webscare.pixels.data.local.mapper

import com.webscare.pixels.data.local.db.PhotoEntity
import com.webscare.pixels.data.remote.dto.PhotoDto
import com.webscare.pixels.domain.model.Photo

object PhotoEntityMapper {

    fun PhotoDto.toEntity(query: String, cachedAt: Long): PhotoEntity = PhotoEntity(
        id = id,
        width = width,
        height = height,
        photographer = photographer,
        photographerUrl = photographerUrl,
        photographerId = photographerId,
        avgColor = avgColor,
        srcOriginal = src.original,
        srcLarge2x = src.large2x,
        srcLarge = src.large,
        srcMedium = src.medium,
        srcSmall = src.small,
        srcPortrait = src.portrait,
        srcLandscape = src.landscape,
        srcTiny = src.tiny,
        alt = alt,
        query = query,
        cachedAt = cachedAt
    )

    fun PhotoEntity.toDomain(): Photo = Photo(
        id = id,
        width = width,
        height = height,
        photographer = photographer,
        photographerUrl = photographerUrl,
        photographerId = photographerId,
        avgColor = avgColor,
        alt = alt,
        srcOriginal = srcOriginal,
        srcLarge2x = srcLarge2x,
        srcLarge = srcLarge,
        srcMedium = srcMedium,
        srcSmall = srcSmall,
        srcPortrait = srcPortrait,
        srcLandscape = srcLandscape,
        srcTiny = srcTiny
    )

    fun PhotoDto.toDomain(): Photo = Photo(
        id = id,
        width = width,
        height = height,
        photographer = photographer,
        photographerUrl = photographerUrl,
        photographerId = photographerId,
        avgColor = avgColor,
        alt = alt,
        srcOriginal = src.original,
        srcLarge2x = src.large2x,
        srcLarge = src.large,
        srcMedium = src.medium,
        srcSmall = src.small,
        srcPortrait = src.portrait,
        srcLandscape = src.landscape,
        srcTiny = src.tiny
    )
}

