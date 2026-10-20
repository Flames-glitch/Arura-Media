package com.aurora.media.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aurora.media.data.FtpSettings
import com.aurora.media.data.MediaEntry
import com.aurora.media.ftp.FtpScanner
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/** Local LibVLC playback. FTP URLs are never sent to TMDb or any online search endpoint. */
class VlcSession(
    context: Context,
    private val entry: MediaEntry,
    private val settings: FtpSettings,
    private val onProgress: (Long, Long) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val vlc = LibVLC(context.applicationContext, arrayListOf("--network-caching=1800", "--no-video-title-show"))
    private val player = MediaPlayer(vlc)
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var isBuffering by mutableStateOf(true)
        private set
    var error by mutableStateOf("")
        private set
    var aspect by mutableStateOf("Fit")
        private set
    var volume by mutableStateOf(100)
        private set
    var audioDelayMs by mutableStateOf(0L)
        private set
    var subtitleDelayMs by mutableStateOf(0L)
        private set
    var speed by mutableStateOf(1f)
        private set

    private var started = false
    private var restored = false

    fun attach(surface: SurfaceView) {
        if (started) return
        started = true
        player.vlcVout.setVideoView(surface)
        player.vlcVout.attachViews()
        player.setEventListener { event ->
            // libVLC callbacks can arrive on non-UI threads.
            main.post {
                when (event.type) {
                    MediaPlayer.Event.Playing -> { isPlaying = true; isBuffering = false }
                    MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> { isPlaying = false; isBuffering = false }
                    MediaPlayer.Event.Buffering -> isBuffering = true
                    MediaPlayer.Event.TimeChanged, MediaPlayer.Event.LengthChanged, MediaPlayer.Event.PositionChanged -> {
                        positionMs = player.time.coerceAtLeast(0L)
                        durationMs = player.length.coerceAtLeast(0L)
                        if (!restored && entry.lastPositionMs > 5_000L && durationMs > entry.lastPositionMs + 10_000L) {
                            restored = true
                            player.time = entry.lastPositionMs
                        }
                        if (durationMs > 0 && positionMs >= 0) onProgress(positionMs, durationMs)
                    }
                    MediaPlayer.Event.EndReached -> { isPlaying = false; onProgress(0L, durationMs) }
                    MediaPlayer.Event.EncounteredError -> {
                        error = "Cannot play this FTP file. Verify the server, video format and network connection."
                        isBuffering = false
                    }
                }
            }
        }
        try {
            val media = Media(vlc, Uri.parse(FtpScanner.mediaUrl(settings, entry.path)))
            media.setHWDecoderEnabled(true, false)
            player.media = media
            media.release()
            player.play()
        } catch (exception: Exception) {
            error = "Playback failed: ${exception.message ?: "Unknown error"}"
            isBuffering = false
        }
    }

    fun togglePause() { if (player.isPlaying) player.pause() else player.play() }
    fun skip(offsetMs: Long) { seek((player.time + offsetMs).coerceIn(0L, player.length.coerceAtLeast(0L))) }
    fun seek(ms: Long) { if (player.isSeekable) player.time = ms.coerceAtLeast(0L) }

    fun changeAspect(mode: String) {
        aspect = mode
        player.aspectRatio = when (mode) {
            "16:9" -> "16:9"
            "4:3" -> "4:3"
            "21:9" -> "21:9"
            else -> null
        }
        player.scale = if (mode == "Fill") 1f else 0f
    }

    fun setVolumePercent(percent: Int) {
        volume = percent.coerceIn(0, 125)
        player.volume = volume
    }

    fun setSpeed(value: Float) {
        speed = value.coerceIn(0.5f, 2f)
        player.rate = speed
    }

    fun shiftAudio(milliseconds: Long) {
        audioDelayMs = (audioDelayMs + milliseconds).coerceIn(-10_000, 10_000)
        player.setAudioDelay(audioDelayMs * 1000L) // microseconds
    }

    fun shiftSubtitles(milliseconds: Long) {
        subtitleDelayMs = (subtitleDelayMs + milliseconds).coerceIn(-10_000, 10_000)
        player.setSpuDelay(subtitleDelayMs * 1000L) // microseconds
    }

    fun audioTracks(): List<Pair<Int, String>> = runCatching {
        player.audioTracks?.map { it.id to it.name }.orEmpty()
    }.getOrDefault(emptyList())

    fun subtitleTracks(): List<Pair<Int, String>> = runCatching {
        player.spuTracks?.map { it.id to it.name }.orEmpty()
    }.getOrDefault(emptyList())

    fun selectAudio(id: Int) { player.audioTrack = id }
    fun selectSubtitle(id: Int) { player.spuTrack = id }

    fun loadExternalSubtitle(uri: Uri) = runCatching {
        player.addSlave(Media.Slave.Type.Subtitle, uri, true)
    }.isSuccess

    /** MediaPlayer.Equalizer ABI varies by LibVLC version; use reflection for optional presets. */
    fun equalizerPreset(index: Int): Boolean = runCatching {
        val equalizerClass = Class.forName("org.videolan.libvlc.MediaPlayer\$Equalizer")
        val method = equalizerClass.getMethod("createFromPreset", Int::class.javaPrimitiveType)
        val equalizer = method.invoke(null, index)
        val apply = player.javaClass.methods.firstOrNull { it.name == "setEqualizer" && it.parameterTypes.size == 1 }
            ?: error("Equalizer unavailable")
        apply.invoke(player, equalizer)
        true
    }.getOrDefault(false)

    fun close() {
        if (durationMs > 0L) onProgress(positionMs, durationMs)
        player.setEventListener(null)
        runCatching { player.stop() }
        runCatching { player.vlcVout.detachViews() }
        player.release()
        vlc.release()
    }
}
