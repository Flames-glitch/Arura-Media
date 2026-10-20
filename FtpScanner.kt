package com.aurora.media.ftp

import com.aurora.media.data.FtpSettings
import com.aurora.media.data.MediaEntry
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

object FtpScanner {
    private val extensions = setOf("mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv", "webm", "ts", "m2ts", "mpg", "mpeg", "3gp", "vob", "ogv")

    private fun normalizedPath(path: String): String = "/" + path.trim().trim('/').takeIf { it.isNotBlank() }.orEmpty()

    fun connect(settings: FtpSettings): FTPClient {
        val ftp = FTPClient()
        ftp.connectTimeout = 10_000
        ftp.defaultTimeout = 10_000
        ftp.setDataTimeout(java.time.Duration.ofSeconds(20))
        ftp.controlEncoding = "UTF-8"
        ftp.connect(settings.host, settings.port)
        if (!ftp.login(settings.username, settings.password)) {
            ftp.disconnect()
            error("FTP login was rejected. Check the username and password.")
        }
        ftp.enterLocalPassiveMode()
        if (!ftp.setFileType(FTP.BINARY_FILE_TYPE)) error("FTP binary transfer mode unavailable")
        return ftp
    }

    inline fun <T> withConnection(settings: FtpSettings, task: (FTPClient) -> T): T {
        val ftp = connect(settings)
        return try { task(ftp) } finally {
            runCatching { ftp.logout() }
            runCatching { ftp.disconnect() }
        }
    }

    fun test(settings: FtpSettings) = withConnection(settings) { ftp ->
        val directory = normalizedPath(settings.directory)
        if (!ftp.changeWorkingDirectory(directory)) error("FTP folder '$directory' does not exist or is inaccessible")
    }

    fun scan(settings: FtpSettings, maxFiles: Int = 8_000, maxDepth: Int = 20): List<MediaEntry> =
        withConnection(settings) { ftp ->
            val results = mutableListOf<MediaEntry>()
            val start = normalizedPath(settings.directory)
            if (!ftp.changeWorkingDirectory(start)) error("FTP directory '$start' cannot be accessed")

            fun walk(path: String, depth: Int) {
                if (depth > maxDepth || results.size >= maxFiles) return
                val entries: Array<FTPFile> = ftp.listFiles(path) ?: emptyArray()
                for (file in entries) {
                    if (results.size >= maxFiles) break
                    if (file.name == "." || file.name == ".." || file.name.contains('/')) continue
                    // Avoid symlink traversal, which can otherwise create directory cycles.
                    if (file.isSymbolicLink) continue
                    val fullPath = "${path.trimEnd('/')}/${file.name}"
                    if (file.isDirectory) walk(fullPath, depth + 1)
                    else if (file.isFile && file.name.substringAfterLast('.', "").lowercase(Locale.ROOT) in extensions) {
                        results += FilenameParser.parse(fullPath, file.size)
                    }
                }
            }
            walk(start, 0)
            results
        }

    /** Credentials remain on-device; this URL is handed only to local LibVLC, never metadata APIs. */
    fun mediaUrl(settings: FtpSettings, filePath: String): String {
        fun encoded(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        val authority = "${encoded(settings.username)}:${encoded(settings.password)}@${settings.host}:${settings.port}"
        val path = filePath.split('/').joinToString("/") { encoded(it) }
        return URI.create("ftp://$authority$path").toASCIIString()
    }
}

object FilenameParser {
    private val releaseYear = Regex("(?:19|20)\\d{2}")
    private val tvEpisode = Regex("(?i)S(\\d{1,2})[. _\\-]*E(\\d{1,3})")
    private val noise = Regex("(?i)\\b(2160p|1080p|720p|480p|bluray|blu-ray|brrip|bdrip|dvdrip|webrip|web-dl|hdtv|x264|x265|h264|h265|hevc|aac|dts|hdr10|hdr|remux|proper|repack|extended|multi)\\b")

    fun parse(path: String, bytes: Long): MediaEntry {
        val filename = path.substringAfterLast('/')
        val stem = filename.substringBeforeLast('.')
        val match = tvEpisode.find(stem)
        val yearMatch = releaseYear.find(stem)
        val titlePortion = when {
            match != null -> stem.substring(0, match.range.first)
            yearMatch != null -> stem.substring(0, yearMatch.range.first)
            else -> noise.find(stem)?.let { stem.substring(0, it.range.first) } ?: stem
        }
        val title = titlePortion.replace(Regex("[._]+"), " ")
            .replace(Regex("[\\[\\]()]"), " ")
            .replace(Regex("\\s+"), " ").trim(' ', '-', '_').ifBlank { stem }
        val code = match?.let { "S${it.groupValues[1].padStart(2, '0')}E${it.groupValues[2].padStart(2, '0')}" }
        return MediaEntry(
            path = path, fileName = filename, title = title,
            year = yearMatch?.value?.toIntOrNull(),
            isEpisode = match != null, seriesName = if (match != null) title else null,
            episodeCode = code, bytes = bytes,
            mediaType = if (match != null) "tv" else "movie"
        )
    }
}
