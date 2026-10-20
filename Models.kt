package com.aurora.media.data

import org.json.JSONObject
import java.security.MessageDigest

private fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }.take(20)

data class FtpSettings(
    val host: String = "",
    val port: Int = 21,
    val username: String = "",
    val password: String = "",
    val directory: String = "/",
    val tmdbToken: String = ""
) {
    val ready: Boolean get() = host.isNotBlank() && username.isNotBlank() && port in 1..65535
}

data class MediaEntry(
    val path: String,
    val fileName: String,
    val title: String,
    val year: Int? = null,
    val isEpisode: Boolean = false,
    val seriesName: String? = null,
    val episodeCode: String? = null,
    val bytes: Long = 0L,
    val poster: String? = null,
    val backdrop: String? = null,
    val description: String? = null,
    val remoteId: Int? = null,
    val mediaType: String = "movie",
    val lastPositionMs: Long = 0L,
    val durationMs: Long = 0L
) {
    val id: String get() = fingerprint(path)
    val displayTitle: String get() = if (isEpisode) "${seriesName ?: title} • ${episodeCode.orEmpty()}" else title

    fun toJson() = JSONObject().apply {
        put("path", path); put("fileName", fileName); put("title", title)
        put("year", year ?: JSONObject.NULL); put("isEpisode", isEpisode)
        put("seriesName", seriesName ?: JSONObject.NULL); put("episodeCode", episodeCode ?: JSONObject.NULL)
        put("bytes", bytes); put("poster", poster ?: JSONObject.NULL)
        put("backdrop", backdrop ?: JSONObject.NULL); put("description", description ?: JSONObject.NULL)
        put("remoteId", remoteId ?: JSONObject.NULL); put("mediaType", mediaType)
        put("lastPositionMs", lastPositionMs); put("durationMs", durationMs)
    }

    companion object {
        fun fromJson(o: JSONObject) = MediaEntry(
            path = o.getString("path"), fileName = o.getString("fileName"),
            title = o.optString("title", o.getString("fileName")),
            year = if (o.isNull("year")) null else o.optInt("year"),
            isEpisode = o.optBoolean("isEpisode"),
            seriesName = o.optString("seriesName").takeIf { it.isNotBlank() && it != "null" },
            episodeCode = o.optString("episodeCode").takeIf { it.isNotBlank() && it != "null" },
            bytes = o.optLong("bytes"),
            poster = o.optString("poster").takeIf { it.startsWith("http") },
            backdrop = o.optString("backdrop").takeIf { it.startsWith("http") },
            description = o.optString("description").takeIf { it.isNotBlank() && it != "null" },
            remoteId = if (o.isNull("remoteId")) null else o.optInt("remoteId"),
            mediaType = o.optString("mediaType", "movie"),
            lastPositionMs = o.optLong("lastPositionMs"), durationMs = o.optLong("durationMs")
        )
    }
}
