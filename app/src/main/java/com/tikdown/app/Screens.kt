package com.tikdown.app

import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.io.File

// ---------------------------------------------------------------- helpers

fun openExternal(context: Context, item: DownloadItem) {
    val uri = item.contentUri(context)
    val i = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, item.mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(i) }
}

fun shareItem(context: Context, item: DownloadItem) {
    val uri = item.contentUri(context)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = item.mime
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun openFolder(context: Context) {
    runCatching {
        context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// ---------------------------------------------------------------- splash

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(
            Modifier.fillMaxSize().padding(bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppLogo(120.dp)
            Spacer(Modifier.height(24.dp))
            Text("TikTok", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF111827))
            Text("Downloader", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Brand)
            Spacer(Modifier.height(10.dp))
            Text("Download • Watch • Enjoy", color = Color(0xFF111827), fontSize = 15.sp)
            Spacer(Modifier.height(36.dp))
            CircularProgressIndicator(color = BrandPink, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
        }
        Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(180.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(0f, h * 0.35f)
                cubicTo(w * 0.3f, h * 0.0f, w * 0.55f, h * 0.55f, w, h * 0.15f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(path, Brush.linearGradient(listOf(Color(0xFFFF2DA8), Color(0xFF4F46E5), Color(0xFF2F6BFF)),
                Offset(0f, 0f), Offset(w, h)))
        }
    }
}

// ---------------------------------------------------------------- home

@Composable
fun HomeScreen(vm: MainViewModel, onDownload: (MediaOption) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    fun scanQr() {
        GmsBarcodeScanning.getClient(context).startScan()
            .addOnSuccessListener { code ->
                val text = code.rawValue.orEmpty()
                if (vm.extractUrl(text) != null) { vm.input = text; vm.load(text) }
                else vm.message = "That QR code is not a TikTok link"
            }
            .addOnFailureListener { vm.message = "QR scanner unavailable on this device" }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        Text("TikTok", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text("Downloader", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Text("Fast • Simple • Free", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        AppLogo(110.dp)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = vm.input,
            onValueChange = { vm.input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Paste TikTok Link Here...") },
            leadingIcon = { Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                if (vm.input.isNotEmpty()) IconButton(onClick = { vm.input = "" }) {
                    Icon(Icons.Default.Close, "Clear")
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(14.dp))
        GradientButton(
            "Download",
            Icons.Default.Download,
            enabled = vm.input.isNotBlank() && vm.state !is UiState.Loading && vm.progress == null
        ) { vm.load(vm.input) }
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAction("Paste\nLink", Icons.Default.ContentPaste, Modifier.weight(1f)) {
                val t = clipboard.getText()?.text.orEmpty()
                if (t.isBlank()) vm.message = "Clipboard is empty"
                else { vm.input = t; if (vm.extractUrl(t) != null) vm.load(t) }
            }
            QuickAction("Scan\nQR", Icons.Default.QrCodeScanner, Modifier.weight(1f)) { scanQr() }
            QuickAction("Trending", Icons.Default.LocalFireDepartment, Modifier.weight(1f)) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/explore"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        when (val s = vm.state) {
            UiState.Idle -> {}
            UiState.Loading -> CircularProgressIndicator()
            is UiState.Failure -> Text(s.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            is UiState.Success -> ResultCard(s.info, onDownload)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = if (icon == Icons.Default.LocalFireDepartment) Color(0xFFFF6A00) else MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 13.sp, textAlign = TextAlign.Center, minLines = 2)
    }
}

@Composable
fun ResultCard(info: VideoInfo, onDownload: (MediaOption) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (info.thumbnail != null) {
                AsyncImage(
                    model = info.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp))
                )
            }
            Text(info.title, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (info.author != null) Text(info.author, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("Choose what to download", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            info.media.forEach { o ->
                GradientButton(
                    o.label,
                    when (o.type) { "audio" -> Icons.Default.MusicNote; "image" -> Icons.Default.Image; else -> Icons.Default.VideoFile }
                ) { onDownload(o) }
            }
        }
    }
}

// ---------------------------------------------------------------- history

@Composable
fun HistoryScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("History", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (vm.history.isNotEmpty()) TextButton(onClick = { vm.clearHistory() }) { Text("Clear all") }
        }
        Spacer(Modifier.height(10.dp))
        if (vm.history.isEmpty()) EmptyState(Icons.Default.History, "No history yet")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(vm.history, key = { it.url }) { h ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { vm.input = h.url; vm.tab = 0; vm.load(h.url) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Thumb(h.thumb, Modifier.size(64.dp, 80.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.title.ifBlank { h.url }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(formatDate(h.date), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.removeHistory(h) }) { Icon(Icons.Default.Close, "Remove") }
                }
            }
        }
    }
}

@Composable
fun Thumb(model: Any?, modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.PlayArrow) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) AsyncImage(model, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- my videos

@Composable
fun MyVideosScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf("all") }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val list = vm.downloads.filter {
        (filter == "all" || it.type == filter) && (query.isBlank() || it.title.contains(query, ignoreCase = true))
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (searching) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("Search downloads") },
                    shape = RoundedCornerShape(16.dp)
                )
                IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.Default.Close, "Close search") }
            } else {
                Text("My Downloads", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "Search") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("All", filter == "all") { filter = "all" }
            Pill("Videos", filter == "video") { filter = "video" }
            Pill("Audios", filter == "audio") { filter = "audio" }
        }
        Spacer(Modifier.height(14.dp))
        if (list.isEmpty()) EmptyState(Icons.Default.VideoLibrary, "Nothing here yet")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { item ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Thumb(
                        item.thumbPath?.let { File(it) },
                        Modifier.size(84.dp, 100.dp).clickable { vm.playing = item },
                        if (item.type == "audio") Icons.Default.MusicNote else Icons.Default.PlayArrow
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.menuItem = item }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.MoreVert, "More")
                            }
                        }
                        Text("${formatSize(item.size)} • ${item.formatLabel}", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.playing = item },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                Text("Play")
                            }
                            FilledTonalIconButton(
                                onClick = { openExternal(context, item) },
                                modifier = Modifier.size(36.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open with", modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemMenu(vm: MainViewModel, item: DownloadItem) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest = { vm.menuItem = null }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(item.thumbPath?.let { File(it) }, Modifier.size(56.dp),
                    if (item.type == "audio") Icons.Default.MusicNote else Icons.Default.PlayArrow)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("${formatSize(item.size)} • ${item.formatLabel}", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            MenuRow(Icons.Default.PlayArrow, "Play Video") { vm.menuItem = null; vm.playing = item }
            MenuRow(Icons.Default.FolderOpen, "Open in Folder") { vm.menuItem = null; openFolder(context) }
            MenuRow(Icons.Default.Share, "Share") { vm.menuItem = null; shareItem(context, item) }
            MenuRow(Icons.Default.Link, "Copy Link") {
                clipboard.setText(AnnotatedString(item.sourceUrl)); vm.menuItem = null; vm.message = "Link copied"
            }
            MenuRow(Icons.Default.Delete, "Delete") { vm.menuItem = null; vm.deleteDownload(item) }
            Spacer(Modifier.height(8.dp))
            SoftButton("Cancel") { vm.menuItem = null }
        }
    }
}

@Composable
fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(18.dp))
        Text(label, fontSize = 16.sp)
    }
}

// ---------------------------------------------------------------- progress dialog

@Composable
fun ProgressDialog(p: DlProgress, onCancel: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    if (p.fraction != null) {
                        CircularProgressIndicator(
                            progress = { p.fraction }, modifier = Modifier.size(120.dp),
                            strokeWidth = 8.dp, color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text("${(p.fraction * 100).toInt()}%", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(120.dp), strokeWidth = 8.dp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    when (p.type) { "audio" -> "Downloading Audio..."; "image" -> "Downloading Image..."; else -> "Downloading Video..." },
                    fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
                Text(
                    p.remainingSec?.let { "About $it sec remaining" } ?: "Starting...",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
                SoftButton("Cancel", onClick = onCancel)
            }
        }
    }
}

// ---------------------------------------------------------------- player

@Composable
fun PlayerScreen(item: DownloadItem, onBack: () -> Unit) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VideoView(ctx).apply {
                    val mc = MediaController(ctx)
                    mc.setAnchorView(this)
                    setMediaController(mc)
                    setVideoURI(item.contentUri(ctx))
                    setOnPreparedListener { it.isLooping = true; start() }
                }
            }
        )
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            IconButton(onClick = { shareItem(context, item) }) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
        }
    }
}

// ---------------------------------------------------------------- settings

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s = vm.settings
    var showAbout by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
        Spacer(Modifier.height(16.dp))
        SectionTitle("Download Settings")
        SettingsCard {
            SettingRow(Icons.Default.HighQuality, "Default Format",
                trailing = { Text(if (s.defaultFormat == "audio") "Audio (MP3)" else "Video (MP4)", color = MaterialTheme.colorScheme.primary) },
                onClick = { vm.updateSettings(s.copy(defaultFormat = if (s.defaultFormat == "audio") "video" else "audio")) })
            SettingRow(Icons.Default.FileDownload, "Auto Download",
                trailing = { Switch(s.autoDownload, { vm.updateSettings(s.copy(autoDownload = it)) }) })
            SettingRow(Icons.Default.PhotoLibrary, "Save to Gallery",
                trailing = { Switch(s.saveToGallery, { vm.updateSettings(s.copy(saveToGallery = it)) }) })
            SettingRow(Icons.Default.Folder, "Download Location",
                trailing = { Text(if (s.saveToGallery) "Movies/TikDown" else "Download/TikDown", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) })
        }
        Spacer(Modifier.height(20.dp))
        SectionTitle("App Settings")
        SettingsCard {
            SettingRow(Icons.Default.DarkMode, "Dark Mode",
                trailing = { Switch(s.darkMode, { vm.updateSettings(s.copy(darkMode = it)) }) })
            SettingRow(Icons.Default.Notifications, "Notifications",
                trailing = { Switch(s.notifications, { vm.updateSettings(s.copy(notifications = it)) }) })
            SettingRow(Icons.Default.Info, "About App",
                trailing = { Icon(Icons.Default.ChevronRight, null) }, onClick = { showAbout = true })
        }
        Spacer(Modifier.height(20.dp))
        SoftButton("Clear Cache (${formatSize(vm.cacheSize)})") { vm.clearCache() }
        Spacer(Modifier.height(24.dp))
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("OK") } },
            title = { Text("TikDown 1.0") },
            text = { Text("Download TikTok videos without watermark, or just the audio. Only download content you have the right to save.") }
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface),
        content = content
    )
}

@Composable
fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(title, modifier = Modifier.weight(1f))
        trailing()
    }
}
