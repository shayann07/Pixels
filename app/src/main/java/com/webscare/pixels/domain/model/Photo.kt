package com.webscare.pixels.domain.model

data class Photo(
    val id: Int,
    val width: Int,
    val height: Int,
    val photographer: String,
    val photographerUrl: String,
    val photographerId: Int,
    val avgColor: String,
    val alt: String,
    val srcOriginal: String,
    val srcLarge2x: String,
    val srcLarge: String,
    val srcMedium: String,
    val srcSmall: String,
    val srcPortrait: String,
    val srcLandscape: String,
    val srcTiny: String
)

