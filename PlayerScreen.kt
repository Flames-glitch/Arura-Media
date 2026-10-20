package com.aurora.media.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aurora.media.data.FtpSettings
import com.aurora.media.data.MediaEntry
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

private fun formatted(ms: Long): String {
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0L))
    return "%02d:%02d:%02d".format(seconds / 3600, (seconds / 60) % 60, seconds % 60)
}

@Composable
fun PlayerScreen(entry: MediaEntry, settings: FtpSettings, onBack: () -> Unit, onPositionChange: (Long, Long) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val session = remember(entry.id, settings.host) { VlcSession(context, entry, settings, onPositionChange) }
    var controlsVisible by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf("") }
    var orientation by remember { mutableStateOf("Landscape") }
    var seekPreview by remember { mutableStateOf<Float?>(null) }
    var notice by remember { mutableStateOf("") }
    val subtitleChooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val permitted = runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            notice = if (session.loadExternalSubtitle(uri)) "External subtitle added" else "Unable to open subtitle"
        }
    }

    DisposableEffect(session) {
        val original = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            session.close()
            activity?.requestedOrientation = original
        }
    }
    LaunchedEffect(orientation) {
        activity?.requestedOrientation = when (orientation) {
            "Auto" -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            "Landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "Portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "Locked" -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) { delay(6_000L); controlsVisible = false }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> SurfaceView(ctx).also(session::attach) }
        )
        // Transparent touch target; tapping once reveals the playback controls.
        Box(Modifier.fillMaxSize().clickable { controlsVisible = !controlsVisible })
        if (session.isBuffering && session.error.isBlank()) CircularProgressIndicator(Modifier.align(Alignment.Center))
        if (session.error.isNotBlank()) {
            Text(session.error, color = Color.White,
                modifier = Modifier.align(Alignment.Center).background(Color(0xC9000000), RoundedCornerShape(12.dp)).padding(18.dp))
        }
        if (controlsVisible) {
            Column(Modifier.fillMaxSize().background(Color(0x55000000))) {
                Row(Modifier.fillMaxWidth().background(Color(0x99000000)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                    Text(entry.displayTitle, modifier = Modifier.weight(1f), color = Color.White, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { orientation = if (orientation == "Locked") "Auto" else "Locked" }) {
                        Icon(if (orientation == "Locked") Icons.Default.ScreenLockRotation else Icons.Default.ScreenRotation,
                            "Toggle rotation lock", tint = Color.White)
                    }
                    IconButton(onClick = { dialog = "settings" }) {
                        Icon(Icons.Default.Tune, "Player settings", tint = Color.White)
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { session.skip(-10_000); controlsVisible = true }, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Default.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(42.dp))
                    }
                    Spacer(Modifier.width(30.dp))
                    IconButton(onClick = { session.togglePause(); controlsVisible = true }, modifier = Modifier.size(70.dp)) {
                        Icon(if (session.isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            "Play / pause", tint = Color.White, modifier = Modifier.size(66.dp))
                    }
                    Spacer(Modifier.width(30.dp))
                    IconButton(onClick = { session.skip(10_000); controlsVisible = true }, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Default.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(42.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(Modifier.background(Color(0xB0000000)).fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                    val length = session.durationMs.coerceAtLeast(1L)
                    Slider(value = seekPreview ?: (session.positionMs.toFloat() / length).coerceIn(0f, 1f),
                        onValueChange = { seekPreview = it },
                        onValueChangeFinished = {
                            seekPreview?.let { session.seek((it * length).toLong()) }
                            seekPreview = null
                        }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatted(session.positionMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { dialog = "audio" }) { Icon(Icons.Default.GraphicEq, "Audio tracks", tint = Color.White) }
                        IconButton(onClick = { dialog = "subtitles" }) { Icon(Icons.Default.Subtitles, "Subtitles", tint = Color.White) }
                        IconButton(onClick = { dialog = "aspect" }) { Icon(Icons.Default.AspectRatio, "Aspect ratio", tint = Color.White) }
                        Spacer(Modifier.width(8.dp))
                        Text(formatted(session.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (notice.isNotBlank()) {
            Text(notice, modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp)
                .background(Color.DarkGray, RoundedCornerShape(9.dp)).padding(12.dp), color = Color.White)
            LaunchedEffect(notice) { delay(2400); notice = "" }
        }
    }

    when (dialog) {
        "aspect" -> OptionDialog("Aspect ratio", listOf("Fit", "Fill", "16:9", "4:3", "21:9"),
            onDismiss = { dialog = "" }, onSelect = { session.changeAspect(it); dialog = "" })
        "audio" -> TrackDialog("Audio track", session.audioTracks(), onDismiss = { dialog = "" },
            onSelect = { session.selectAudio(it); dialog = "" })
        "subtitles" -> AlertDialog(onDismissRequest = { dialog = "" },
            title = { Text("Subtitles") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    session.subtitleTracks().forEach { (id, name) ->
                        TextButton(onClick = { session.selectSubtitle(id); dialog = "" }) { Text(name) }
                    }
                    HorizontalDivider()
                    TextButton(onClick = { dialog = ""; subtitleChooser.launch(arrayOf("application/x-subrip", "text/plain", "text/vtt", "application/octet-stream")) }) {
                        Text("Import subtitle file (.srt / .vtt)")
                    }
                    TextButton(onClick = { dialog = "sync" }) { Text("Audio & subtitle delay") }
                }
            }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text("Close") } })
        "sync" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Track synchronization") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Subtitle delay: ${session.subtitleDelayMs} ms")
                Row { TextButton(onClick = { session.shiftSubtitles(-250) }) { Text("−250 ms") }; TextButton(onClick = { session.shiftSubtitles(250) }) { Text("+250 ms") } }
                HorizontalDivider()
                Text("Audio delay: ${session.audioDelayMs} ms")
                Row { TextButton(onClick = { session.shiftAudio(-250) }) { Text("−250 ms") }; TextButton(onClick = { session.shiftAudio(250) }) { Text("+250 ms") } }
            } }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text("Done") } })
        "settings" -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Player settings") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Volume / audio boost: ${session.volume}%")
                Slider(value = session.volume.toFloat(), onValueChange = { session.setVolumePercent(it.toInt()) }, valueRange = 0f..125f)
                Text("Playback speed: ${"%.2f".format(session.speed)}×")
                Slider(value = session.speed, onValueChange = session::setSpeed, valueRange = 0.5f..2f)
                TextButton(onClick = { dialog = "orientation" }) { Text("Orientation: $orientation") }
                TextButton(onClick = { dialog = "equalizer" }) { Text("Equalizer presets") }
                TextButton(onClick = { dialog = "sync" }) { Text("Audio and subtitle synchronization") }
            } }, confirmButton = { TextButton(onClick = { dialog = "" }) { Text("Close") } })
        "orientation" -> OptionDialog("Screen orientation", listOf("Auto", "Landscape", "Portrait", "Locked"),
            onDismiss = { dialog = "" }, onSelect = { orientation = it; dialog = "" })
        "equalizer" -> OptionDialog("Audio equalizer (device dependent)",
            listOf("Flat", "Classical", "Club", "Dance", "Full bass", "Full treble", "Headphones", "Large Hall", "Live", "Party", "Pop", "Reggae", "Rock", "Ska", "Soft", "Techno"),
            onDismiss = { dialog = "" }, onSelect = {
                val index = listOf("Flat", "Classical", "Club", "Dance", "Full bass", "Full treble", "Headphones", "Large Hall", "Live", "Party", "Pop", "Reggae", "Rock", "Ska", "Soft", "Techno").indexOf(it)
                notice = if (session.equalizerPreset(index)) "$it equalizer enabled" else "Equalizer not supported in this player build"
                dialog = ""
            })
    }
}

@Composable
private fun OptionDialog(title: String, options: List<String>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            options.forEach { option -> TextButton(onClick = { onSelect(option) }, modifier = Modifier.fillMaxWidth()) { Text(option) } }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun TrackDialog(title: String, tracks: List<Pair<Int, String>>, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            if (tracks.isEmpty()) Text("No selectable tracks detected")
            tracks.forEach { (id, name) -> TextButton(onClick = { onSelect(id) }, modifier = Modifier.fillMaxWidth()) { Text(name) } }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
