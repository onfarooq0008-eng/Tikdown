package com.tikdown.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private var sharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFFFE2C55))) {
                Surface(Modifier.fillMaxSize()) {
                    TikDownScreen(vm, sharedText) { sharedText = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TikDownScreen(vm: MainViewModel, sharedText: String?, onSharedConsumed: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<MediaOption?>(null) }

    fun startDownload(option: MediaOption, title: String) {
        try {
            Downloader.enqueue(context, option, title)
            Toast.makeText(context, "Download started - check notifications", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val state = vm.state
        val opt = pending
        if (granted && opt != null && state is UiState.Success) startDownload(opt, state.info.title)
        else if (!granted) Toast.makeText(context, "Storage permission is required", Toast.LENGTH_SHORT).show()
        pending = null
    }

    fun requestDownload(option: MediaOption, title: String) {
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pending = option
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            startDownload(option, title)
        }
    }

    // Auto-load links shared from the TikTok app
    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            input = sharedText
            vm.load(sharedText)
            onSharedConsumed()
        }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("TikDown") }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("TikTok link") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        clipboard.getText()?.text?.let { input = it }
                    }) { Icon(Icons.Default.ContentPaste, contentDescription = "Paste") }
                }
            )

            Button(
                onClick = { vm.load(input) },
                enabled = input.isNotBlank() && vm.state !is UiState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Get download options")
            }

            when (val s = vm.state) {
                UiState.Idle -> {}
                UiState.Loading -> Box(Modifier.fillMaxWidth(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Failure -> Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error
                )
                is UiState.Success -> ResultCard(s.info) { option -> requestDownload(option, s.info.title) }
            }
        }
    }
}

@Composable
fun ResultCard(info: VideoInfo, onDownload: (MediaOption) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (info.thumbnail != null) {
                AsyncImage(
                    model = info.thumbnail,
                    contentDescription = "Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
            Text(
                info.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (info.author != null) {
                Text(info.author, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            Text("Download options", style = MaterialTheme.typography.labelLarge)
            info.media.forEach { option ->
                FilledTonalButton(
                    onClick = { onDownload(option) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        when (option.type) {
                            "audio" -> Icons.Default.MusicNote
                            "image" -> Icons.Default.Image
                            else -> Icons.Default.VideoFile
                        },
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(option.label)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.Download, contentDescription = "Download")
                }
            }
        }
    }
}
