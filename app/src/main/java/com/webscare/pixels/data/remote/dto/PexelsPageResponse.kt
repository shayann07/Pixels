package com.webscare.pixels.data.remote.dto

import org.json.JSONObject

data class PexelsPageResponse(
    val page: Int,
    val perPage: Int,
    val totalResults: Int,
    val photos: List<PhotoDto>
) {
    companion object {
        fun fromJson(json: JSONObject): PexelsPageResponse {
            val photosArray = json.getJSONArray("photos")
            val photos = (0 until photosArray.length()).map { index ->
                PhotoDto.fromJson(photosArray.getJSONObject(index))
            }
            return PexelsPageResponse(
                page = json.optInt("page", 1),
                perPage = json.optInt("per_page", 15),
                totalResults = json.optInt("total_results", 0),
                photos = photos
            )
        }
    }
}

