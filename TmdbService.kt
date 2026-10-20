package com.aurora.media.metadata

import com.aurora.media.data.MediaEntry
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Official TMDb v3 API; requires user's own API Read Access Token (Bearer token). */
class TmdbService(private val bearerToken: String) {
    fun enrich(item: MediaEntry): MediaEntry {
        if (bearerToken.isBlank()) return item
        val query = URLEncoder.encode(item.seriesName ?: item.title, "UTF-8")
        val year = item.year?.let { if (item.isEpisode) "" else "&year=$it" }.orEmpty()
        val type = if (item.isEpisode) "tv" else "movie"
        val endpoint = "https://api.themoviedb.org/3/search/$type?query=$query$year&include_adult=false"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Authorization", "Bearer $bearerToken")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val status = connection.responseCode
            if (status != 200) {
                if (status == 401 || status == 403) error("TMDb token is invalid or lacks access")
                return item
            }
            val response = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val results = response.optJSONArray("results") ?: return item
            if (results.length() == 0) return item
            val match = results.getJSONObject(0)
            val posterPath = match.optString("poster_path").takeIf { it.startsWith('/') }
            val backdropPath = match.optString("backdrop_path").takeIf { it.startsWith('/') }
            item.copy(
                poster = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                backdrop = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                description = match.optString("overview").takeIf { it.isNotBlank() },
                remoteId = match.optInt("id", 0).takeIf { it > 0 },
                mediaType = type
            )
        } finally {
            connection.disconnect()
        }
    }
}
