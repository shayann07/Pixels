package com.webscare.pixels.di

import android.content.Context
import com.webscare.pixels.data.local.db.PixelsDatabase
import com.webscare.pixels.data.remote.PexelsApiService
import com.webscare.pixels.data.remote.VolleySingleton
import com.webscare.pixels.data.repository.PhotoRepositoryImpl
import com.webscare.pixels.domain.repository.PhotoRepository
import com.webscare.pixels.domain.usecase.GetCuratedPhotosUseCase
import com.webscare.pixels.domain.usecase.SearchPhotosUseCase

object ServiceLocator {

    @Volatile
    private var initialized = false

    private lateinit var appContext: Context

    val database: PixelsDatabase by lazy {
        PixelsDatabase.getInstance(appContext)
    }

    val volleySingleton: VolleySingleton by lazy {
        VolleySingleton.getInstance(appContext)
    }

    val apiService: PexelsApiService by lazy {
        PexelsApiService(volleySingleton)
    }

    val repository: PhotoRepository by lazy {
        PhotoRepositoryImpl(
            photoDao = database.photoDao(),
            apiService = apiService,
            context = appContext
        )
    }

    val getCuratedPhotosUseCase: GetCuratedPhotosUseCase by lazy {
        GetCuratedPhotosUseCase(repository)
    }

    val searchPhotosUseCase: SearchPhotosUseCase by lazy {
        SearchPhotosUseCase(repository)
    }

    fun init(context: Context) {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    appContext = context.applicationContext
                    initialized = true
                }
            }
        }
    }
}

