package com.webscare.pixels.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PhotoDao {

    @Query("SELECT * FROM photos WHERE query = :query ORDER BY cachedAt DESC LIMIT :limit")
    suspend fun getPhotosByQuery(query: String, limit: Int = 500): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<PhotoEntity>)

    @Query("DELETE FROM photos WHERE query = :query")
    suspend fun deleteByQuery(query: String)

    @Query("DELETE FROM photos WHERE cachedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    /**
     * Prune oldest entries beyond [keepCount] for a given query to bound cache size.
     */
    @Query("""
        DELETE FROM photos
        WHERE query = :query
        AND id NOT IN (
            SELECT id FROM photos
            WHERE query = :query
            ORDER BY cachedAt DESC
            LIMIT :keepCount
        )
    """)
    suspend fun pruneExcess(query: String, keepCount: Int = 500)
}
