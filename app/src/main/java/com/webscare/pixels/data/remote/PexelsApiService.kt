package com.webscare.pixels.data.remote

import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.JsonObjectRequest
import com.webscare.pixels.BuildConfig
import com.webscare.pixels.data.remote.dto.PexelsPageResponse
import com.webscare.pixels.util.Constants
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PexelsApiService(private val volleySingleton: VolleySingleton) {

    suspend fun getCuratedPhotos(page: Int): PexelsPageResponse {
        val url = "${Constants.BASE_URL}curated?page=$page&per_page=${Constants.PER_PAGE}"
        return executeRequest(url)
    }

    suspend fun searchPhotos(query: String, page: Int): PexelsPageResponse {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "${Constants.BASE_URL}search?query=$encodedQuery&page=$page&per_page=${Constants.PER_PAGE}"
        return executeRequest(url)
    }

    private suspend fun executeRequest(url: String): PexelsPageResponse {
        return suspendCancellableCoroutine { continuation ->
            val request = object : JsonObjectRequest(
                Request.Method.GET, url, null,
                Response.Listener { response ->
                    try {
                        val pageResponse = PexelsPageResponse.fromJson(response)
                        continuation.resume(pageResponse)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                },
                Response.ErrorListener { error ->
                    val message = when {
                        error.networkResponse != null -> {
                            val statusCode = error.networkResponse.statusCode
                            when (statusCode) {
                                401 -> "Invalid API key"
                                429 -> "Rate limit exceeded. Try again later"
                                in 500..599 -> "Server error ($statusCode). Try again"
                                else -> "Request failed ($statusCode)"
                            }
                        }
                        error.cause != null -> error.cause?.message ?: "Connection failed"
                        else -> "Network error. Check your connection"
                    }
                    continuation.resumeWithException(Exception(message))
                }
            ) {
                override fun getHeaders(): MutableMap<String, String> {
                    return mutableMapOf("Authorization" to BuildConfig.PEXELS_API_KEY)
                }

                override fun parseNetworkResponse(response: NetworkResponse): Response<JSONObject> {
                    // Extract rate limit headers for tracking
                    val rateLimitRemaining = response.headers?.get("X-Ratelimit-Remaining")
                    rateLimitRemaining?.toIntOrNull()?.let {
                        lastRateLimitRemaining = it
                    }
                    return super.parseNetworkResponse(response)
                }
            }

            // Set generous timeout (15s) and 2 retries with exponential backoff
            request.retryPolicy = DefaultRetryPolicy(
                15_000, // 15 seconds
                2,      // max retries
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )

            volleySingleton.requestQueue.add(request)

            continuation.invokeOnCancellation {
                request.cancel()
            }
        }
    }

    companion object {
        @Volatile
        var lastRateLimitRemaining: Int = 200
            private set
    }
}

