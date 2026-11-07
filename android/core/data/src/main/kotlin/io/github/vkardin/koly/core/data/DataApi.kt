package io.github.vkardin.koly.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named

sealed interface FetchResult {
    data class Ok(val body: String, val etag: String?, val lastModified: String?) : FetchResult
    data object NotModified : FetchResult
}

/** Reads the published JSON files with conditional requests. */
class DataApi @Inject constructor(
    private val client: OkHttpClient,
    @param:Named("dataBaseUrl") private val baseUrl: String,
) {
    suspend fun get(path: String, etag: String?, lastModified: String?): FetchResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl + path)
            .header("Accept", "application/json")
            .apply {
                if (etag != null) header("If-None-Match", etag)
                if (lastModified != null) header("If-Modified-Since", lastModified)
            }
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 304 -> FetchResult.NotModified
                !response.isSuccessful -> throw IOException("HTTP ${response.code} for $path")
                else -> FetchResult.Ok(
                    body = response.body.string(),
                    etag = response.header("ETag"),
                    lastModified = response.header("Last-Modified"),
                )
            }
        }
    }
}
