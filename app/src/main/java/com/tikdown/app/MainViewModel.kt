package com.tikdown.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Success(val info: VideoInfo, val url: String) : UiState
    data class Failure(val message: String) : UiState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    var settings by mutableStateOf(store.settings()); private set
    var downloads by mutableStateOf(store.downloads()); private set
    var history by mutableStateOf(store.history()); private set

    var input by mutableStateOf("")
    var state: UiState by mutableStateOf(UiState.Idle); private set
    var progress by mutableStateOf<DlProgress?>(null); private set
    var message by mutableStateOf<String?>(null)
    var tab by mutableStateOf(0)
    var playing by mutableStateOf<DownloadItem?>(null)
    var menuItem by mutableStateOf<DownloadItem?>(null)
    var autoOption by mutableStateOf<MediaOption?>(null)
    var cacheSize by mutableStateOf(0L); private set

    private var job: Job? = null

    init { refreshCache() }

    // ---- link loading ----

    fun onShared(text: String) {
        input = text
        tab = 0
        load(text)
    }

    fun load(rawInput: String) {
        val url = extractUrl(rawInput)
        if (url == null) {
            state = UiState.Failure("Please paste a valid TikTok link")
            return
        }
        state = UiState.Loading
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { TikApi.fetch(url) }
                state = UiState.Success(info, url)
                addHistory(HistoryItem(url, info.title, info.thumbnail, System.currentTimeMillis()))
                if (settings.autoDownload) {
                    autoOption = info.media.firstOrNull { it.type == settings.defaultFormat } ?: info.media.first()
                }
            } catch (e: Exception) {
                state = UiState.Failure(e.message ?: "Something went wrong")
            }
        }
    }

    fun extractUrl(text: String): String? =
        Regex("https?://\\S+").find(text)?.value?.trimEnd('.', ',', ')')
            ?.takeIf { it.contains("tiktok", ignoreCase = true) }

    // ---- downloading ----

    fun startDownload(option: MediaOption) {
        val s = state as? UiState.Success ?: return
        if (job?.isActive == true) return
        progress = DlProgress(s.info.title, option.type, null, null)
        job = viewModelScope.launch {
            try {
                val item = Downloader.download(
                    getApplication(), option, s.info, s.url, settings.saveToGallery
                ) { p -> progress = p }
                downloads = listOf(item) + downloads
                store.saveDownloads(downloads)
                message = "Saved - find it in My Videos"
                if (settings.notifications) Notifier.done(getApplication(), item.title)
            } catch (e: CancellationException) {
                message = "Download cancelled"
                throw e
            } catch (e: Exception) {
                message = "Download failed: ${e.message}"
            } finally {
                progress = null
            }
        }
    }

    fun cancelDownload() {
        job?.cancel()
    }

    fun deleteDownload(item: DownloadItem) {
        downloads = downloads.filter { it.id != item.id }
        store.saveDownloads(downloads)
        viewModelScope.launch(Dispatchers.IO) { Downloader.delete(getApplication(), item) }
        message = "Deleted"
    }

    // ---- history ----

    private fun addHistory(item: HistoryItem) {
        history = (listOf(item) + history.filter { it.url != item.url }).take(100)
        store.saveHistory(history)
    }

    fun removeHistory(item: HistoryItem) {
        history = history.filter { it.url != item.url }
        store.saveHistory(history)
    }

    fun clearHistory() {
        history = emptyList()
        store.saveHistory(history)
    }

    // ---- settings ----

    fun updateSettings(s: AppSettings) {
        settings = s
        store.saveSettings(s)
    }

    fun refreshCache() {
        val dir = getApplication<Application>().cacheDir
        cacheSize = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            getApplication<Application>().cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            withContext(Dispatchers.Main) {
                refreshCache()
                message = "Cache cleared"
            }
        }
    }
}
