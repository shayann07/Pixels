package com.webscare.pixels.data.remote.dto

import org.json.JSONObject

data class PhotoDto(
    val id: Int,
    val width: Int,
    val height: Int,
    val photographer: String,
    val photographerUrl: String,
    val photographerId: Int,
    val avgColor: String,
    val src: PhotoSrcDto,
    val alt: String
) {
    companion object {
        fun fromJson(json: JSONObject): PhotoDto = PhotoDto(
            id = json.optInt("id", 0),
            width = json.optInt("width", 0),
            height = json.optInt("height", 0),
            photographer = json.optString("photographer", ""),
            photographerUrl = json.optString("photographer_url", ""),
            photographerId = json.optInt("photographer_id", 0),
            avgColor = json.optString("avg_color", "#000000"),
            src = PhotoSrcDto.fromJson(json.getJSONObject("src")),
            alt = json.optString("alt", "")
        )
    }
}

