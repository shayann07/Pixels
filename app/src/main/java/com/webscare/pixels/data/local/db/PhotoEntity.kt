package com.webscare.pixels.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photos",
    indices = [Index(value = ["query"]), Index(value = ["cachedAt"])]
)
data class PhotoEntity(
    @PrimaryKey val id: Int,
    val width: Int,
    val height: Int,
    val photographer: String,
    val photographerUrl: String,
    val photographerId: Int,
    val avgColor: String,
    val srcOriginal: String,
    val srcLarge2x: String,
    val srcLarge: String,
    val srcMedium: String,
    val srcSmall: String,
    val srcPortrait: String,
    val srcLandscape: String,
    val srcTiny: String,
    val alt: String,
    val query: String,
    val cachedAt: Long
)

