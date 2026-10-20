package com.aurora.media.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.media.data.FtpSettings
import com.aurora.media.data.LibraryStore
import com.aurora.media.data.MediaEntry
import com.aurora.media.data.SecureSettings
import com.aurora.media.ftp.FtpScanner
import com.aurora.media.metadata.TmdbService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

sealed class Screen {
    data object Setup : Screen()
    data object Library : Screen()
    data class Details(val id: String) : Screen()
    data class Player(val id: String) : Screen()
}

data class AuroraState(
    val settings: FtpSettings = FtpSettings(),
    val screen: Screen = Screen.Setup,
    val entries: List<MediaEntry> = emptyList(),
    val busy: Boolean = false,
    val message: String = "",
    val isScanning: Boolean = false,
    val identified: Int = 0
)

class AuroraViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = SecureSettings(application)
    private val library = LibraryStore(application)
    var state by mutableStateOf(AuroraState())
        private set

    init {
        val saved = preferences.load()
        state = state.copy(settings = saved, screen = if (saved.ready) Screen.Library else Screen.Setup)
        viewModelScope.launch {
            state = state.copy(entries = withContext(Dispatchers.IO) { library.read() })
            if (saved.ready) refresh()
        }
    }

    fun saveSetup(settings: FtpSettings) {
        if (settings.host.isBlank() || settings.username.isBlank() || settings.port !in 1..65535) {
            state = state.copy(message = "Enter a valid FTP host, port, and username.")
            return
        }
        viewModelScope.launch {
            state = state.copy(busy = true, message = "Checking FTP connection…")
            try {
                withContext(Dispatchers.IO) { FtpScanner.test(settings) }
                val previous = state.settings
                withContext(Dispatchers.IO) { preferences.save(settings) }
                val locationChanged = previous.host != settings.host || previous.port != settings.port ||
                    previous.directory != settings.directory || previous.username != settings.username
                if (locationChanged) withContext(Dispatchers.IO) { library.write(emptyList()) }
                state = state.copy(
                    settings = settings, entries = if (locationChanged) emptyList() else state.entries,
                    screen = Screen.Library, busy = false, message = "FTP connected. Scanning your library…"
                )
                refresh()
            } catch (error: Exception) {
                state = state.copy(busy = false, message = "Connection failed: ${error.message ?: "Unknown FTP error"}")
            }
        }
    }

    fun refresh() {
        if (state.isScanning || !state.settings.ready) return
        val settings = state.settings
        viewModelScope.launch {
            state = state.copy(isScanning = true, message = "Finding videos on your FTP server…", identified = 0)
            try {
                val scanned = withContext(Dispatchers.IO) { FtpScanner.scan(settings) }
                val previous = state.entries.associateBy { it.path }
                var merged = scanned.map { current ->
                    previous[current.path]?.takeIf { it.fileName == current.fileName && it.bytes == current.bytes }
                        ?.copy(bytes = current.bytes) ?: current
                }
                state = state.copy(entries = merged, message = "Found ${merged.size} videos", isScanning = false)
                withContext(Dispatchers.IO) { library.write(merged) }
                if (settings.tmdbToken.isNotBlank()) {
                    val matcher = TmdbService(settings.tmdbToken)
                    for (item in merged.filter { it.poster.isNullOrBlank() }) {
                        // A failed individual match must not prevent videos from being played.
                        val result = withContext(Dispatchers.IO) { runCatching { matcher.enrich(item) }.getOrNull() }
                        if (result != null && result.poster != null) {
                            merged = merged.map { if (it.id == item.id) result else it }
                            state = state.copy(entries = merged, identified = state.identified + 1)
                            withContext(Dispatchers.IO) { library.write(merged) }
                        }
                    }
                }
                state = state.copy(message = "Library ready • ${merged.size} videos")
            } catch (error: Exception) {
                // Keep offline cache visible on FTP failure.
                state = state.copy(isScanning = false, message = "FTP unavailable: ${error.message ?: "Connection error"}. Cached library remains visible.")
            }
        }
    }

    fun navigate(screen: Screen) { state = state.copy(screen = screen, message = "") }
    fun select(id: String) = navigate(Screen.Details(id))
    fun play(id: String) = navigate(Screen.Player(id))
    fun updateProgress(id: String, position: Long, duration: Long) {
        val old = state.entries.firstOrNull { it.id == id } ?: return
        if (kotlin.math.abs(old.lastPositionMs - position) < 5_000L && duration == old.durationMs) return
        val next = state.entries.map { if (it.id == id) it.copy(lastPositionMs = position, durationMs = duration) else it }
        state = state.copy(entries = next)
        viewModelScope.launch(Dispatchers.IO) { library.write(next) }
    }
    fun exitPlayer() { navigate(Screen.Details((state.screen as? Screen.Player)?.id ?: return)) }
}
