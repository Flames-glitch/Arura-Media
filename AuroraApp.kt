package com.aurora.media.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aurora.media.data.FtpSettings
import com.aurora.media.data.MediaEntry
import com.aurora.media.player.PlayerScreen
import java.util.Locale

private val auroraColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFA9B9FF),
    background = androidx.compose.ui.graphics.Color(0xFF0C101A),
    surface = androidx.compose.ui.graphics.Color(0xFF171D2B),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF242C3A)
)

@Composable
fun AuroraApp(viewModel: AuroraViewModel) {
    val state = viewModel.state
    MaterialTheme(colorScheme = auroraColors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val screen = state.screen) {
                Screen.Setup -> SetupScreen(
                    initial = state.settings, busy = state.busy, message = state.message,
                    canExit = state.settings.ready,
                    onBack = { viewModel.navigate(Screen.Library) },
                    onConnect = viewModel::saveSetup
                )
                Screen.Library -> LibraryScreen(
                    entries = state.entries, message = state.message,
                    loading = state.isScanning, onRescan = viewModel::refresh,
                    onSettings = { viewModel.navigate(Screen.Setup) },
                    onSelect = viewModel::select
                )
                is Screen.Details -> {
                    BackHandler { viewModel.navigate(Screen.Library) }
                    val entry = state.entries.firstOrNull { it.id == screen.id }
                    if (entry == null) LaunchedEffect(screen.id) { viewModel.navigate(Screen.Library) }
                    else DetailsScreen(entry, onBack = { viewModel.navigate(Screen.Library) }, onPlay = { viewModel.play(entry.id) })
                }
                is Screen.Player -> {
                    BackHandler { viewModel.exitPlayer() }
                    val entry = state.entries.firstOrNull { it.id == screen.id }
                    if (entry == null) LaunchedEffect(screen.id) { viewModel.navigate(Screen.Library) }
                    else PlayerScreen(entry = entry, settings = state.settings,
                        onBack = viewModel::exitPlayer,
                        onPositionChange = { time, duration -> viewModel.updateProgress(entry.id, time, duration) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen(
    initial: FtpSettings, busy: Boolean, message: String, canExit: Boolean,
    onBack: () -> Unit, onConnect: (FtpSettings) -> Unit
) {
    var host by remember(initial) { mutableStateOf(initial.host) }
    var port by remember(initial) { mutableStateOf(initial.port.toString()) }
    var username by remember(initial) { mutableStateOf(initial.username) }
    var password by remember(initial) { mutableStateOf(initial.password) }
    var directory by remember(initial) { mutableStateOf(initial.directory) }
    var token by remember(initial) { mutableStateOf(initial.tmdbToken) }

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (canExit) "FTP Settings" else "Connect your media library") },
            navigationIcon = { if (canExit) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
    }) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().imePadding()
                .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.size(55.dp), tint = MaterialTheme.colorScheme.primary)
            Text("One-time FTP configuration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Enter your FTP server details. They are saved on this device, and the app reconnects automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(host, { host = it }, label = { Text("FTP server IP or hostname") },
                placeholder = { Text("192.168.1.254") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("FTP port") },
                modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(username, { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(directory, { directory = it }, label = { Text("Media folder") },
                placeholder = { Text("/Movies") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            HorizontalDivider()
            Text("Movie posters and information", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(token, { token = it }, label = { Text("TMDb API Read Access Token (optional)") },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("For automatic posters, add your TMDb API Read Access Token from themoviedb.org. Without it, videos remain browsable by filename. This product uses the TMDb API but is not endorsed or certified by TMDb.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
            Button(onClick = {
                onConnect(FtpSettings(host.trim(), port.toIntOrNull() ?: 21, username.trim(), password, directory.trim(), token.trim()))
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Cable, null)
                Spacer(Modifier.width(8.dp))
                Text(if (canExit) "Save and reconnect" else "Connect and build library")
            }
            Text("FTP is unencrypted. Use only on a trusted local network or through a private VPN.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(
    entries: List<MediaEntry>, message: String, loading: Boolean,
    onRescan: () -> Unit, onSettings: () -> Unit, onSelect: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    val types = listOf("All", "Movies", "TV Shows", "Continue")
    val filtered = remember(entries, query, category) {
        entries.filter { item ->
            (category == "All" || (category == "Movies" && !item.isEpisode) ||
                (category == "TV Shows" && item.isEpisode) ||
                (category == "Continue" && item.lastPositionMs > 5_000)) &&
                (query.isBlank() || item.displayTitle.contains(query, ignoreCase = true) || item.fileName.contains(query, ignoreCase = true))
        }.sortedWith(compareBy<MediaEntry> { it.displayTitle.lowercase(Locale.ROOT) }.thenBy { it.episodeCode.orEmpty() })
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("AURORA MEDIA", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp) }, actions = {
            IconButton(onClick = onRescan, enabled = !loading) { Icon(Icons.Default.Refresh, "Rescan FTP") }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "FTP and metadata settings") }
        }) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                types.forEach { item ->
                    NavigationBarItem(selected = category == item, onClick = { category = item },
                        icon = { Icon(when (item) {
                            "Movies" -> Icons.Default.Movie
                            "TV Shows" -> Icons.Default.Tv
                            "Continue" -> Icons.Default.PlayCircle
                            else -> Icons.Default.Home
                        }, null) }, label = { Text(item) })
                }
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(value = query, onValueChange = { query = it },
                label = { Text("Search your collection") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 7.dp))
            if (message.isNotBlank()) Text(message, modifier = Modifier.padding(horizontal = 18.dp, vertical = 3.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyVerticalGrid(columns = GridCells.Adaptive(138.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(horizontal = 4.dp, vertical = 5.dp)) {
                        Text(when (category) {
                            "Movies" -> "Your movies"; "TV Shows" -> "TV episodes"
                            "Continue" -> "Continue watching"; else -> "Your media library"
                        }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${filtered.size} video${if (filtered.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(filtered, key = { it.id }) { item -> PosterTile(item, onClick = { onSelect(item.id) }) }
                if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.size(60.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(if (loading) "Scanning server…" else "No videos found")
                        Text("Check the FTP folder, tap refresh, or change your search.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterTile(item: MediaEntry, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            if (item.poster != null) AsyncImage(model = item.poster, contentDescription = "${item.title} poster",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.Movie, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
            if (item.lastPositionMs > 0L && item.durationMs > 0L) {
                LinearProgressIndicator(progress = { (item.lastPositionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.displayTitle, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        if (item.year != null) Text(item.year.toString(), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsScreen(item: MediaEntry, onBack: () -> Unit, onPlay: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            if (item.backdrop != null) AsyncImage(item.backdrop, contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxWidth().height(125.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center) { Icon(Icons.Default.Movie, null, modifier = Modifier.size(60.dp)) }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text(item.displayTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(item.year?.toString(), item.episodeCode,
                    item.bytes.takeIf { it > 0 }?.let { "%.1f GB".format(it.toDouble() / 1_073_741_824) }).joinToString(" • "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onPlay, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp))
                    Text(if (item.lastPositionMs > 0) "Resume playback" else "Play video")
                }
                if (!item.description.isNullOrBlank()) Text(item.description)
                HorizontalDivider()
                Text("FTP file", style = MaterialTheme.typography.titleSmall)
                Text(item.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
