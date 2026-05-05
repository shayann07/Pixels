package com.webscare.pixels.data.remote.dto

import org.json.JSONObject

data class PhotoSrcDto(
    val original: String,
    val large2x: String,
    val large: String,
    val medium: String,
    val small: String,
    val portrait: String,
    val landscape: String,
    val tiny: String
) {
    companion object {
        fun fromJson(json: JSONObject): PhotoSrcDto = PhotoSrcDto(
            original = json.optString("original", ""),
            large2x = json.optString("large2x", ""),
            large = json.optString("large", ""),
            medium = json.optString("medium", ""),
            small = json.optString("small", ""),
            portrait = json.optString("portrait", ""),
            landscape = json.optString("landscape", ""),
            tiny = json.optString("tiny", "")
        )
    }
}

