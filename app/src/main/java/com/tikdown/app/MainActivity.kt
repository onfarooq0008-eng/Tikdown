package com.tikdown.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            TikTheme(vm.settings.darkMode) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root(vm)
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
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.onShared(it) }
        }
    }
}

private data class NavTab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    NavTab("Home", Icons.Default.Home),
    NavTab("History", Icons.Default.History),
    NavTab("My Videos", Icons.Default.PlayCircle),
    NavTab("Settings", Icons.Default.Settings)
)

@Composable
fun Root(vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var splash by rememberSaveable { mutableStateOf(true) }
    var pendingOption by remember { mutableStateOf<MediaOption?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { delay(1400); splash = false }

    // Android 13+: ask for notification permission once
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(splash) {
        if (!splash && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Android 9 and older need a storage permission
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val opt = pendingOption
        pendingOption = null
        if (granted && opt != null) vm.startDownload(opt)
        else if (!granted) Toast.makeText(context, "Storage permission is required", Toast.LENGTH_SHORT).show()
    }

    fun requestDownload(option: MediaOption) {
        val needs = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needs) {
            pendingOption = option
            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else vm.startDownload(option)
    }

    // auto download
    LaunchedEffect(vm.autoOption) {
        vm.autoOption?.let { vm.autoOption = null; requestDownload(it) }
    }

    // toast-style messages
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    if (splash) {
        SplashScreen()
        return
    }

    val playing = vm.playing
    if (playing != null) {
        BackHandler { vm.playing = null }
        PlayerScreen(playing) { vm.playing = null }
        return
    }

    BackHandler(enabled = vm.tab != 0) { vm.tab = 0 }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = vm.tab == i,
                        onClick = { vm.tab = i; if (i == 3) vm.refreshCache() },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (vm.tab) {
                0 -> HomeScreen(vm) { requestDownload(it) }
                1 -> HistoryScreen(vm)
                2 -> MyVideosScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }

    vm.progress?.let { p -> ProgressDialog(p) { vm.cancelDownload() } }
    vm.menuItem?.let { ItemMenu(vm, it) }
}
