package com.tikdown.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Success(val info: VideoInfo) : UiState
    data class Failure(val message: String) : UiState
}

class MainViewModel : ViewModel() {
    var state: UiState by mutableStateOf(UiState.Idle)
        private set

    fun load(rawInput: String) {
        val url = extractUrl(rawInput)
        if (url == null) {
            state = UiState.Failure("Please paste a valid TikTok link")
            return
        }
        state = UiState.Loading
        viewModelScope.launch {
            state = try {
                val info = withContext(Dispatchers.IO) { TikApi.fetch(url) }
                UiState.Success(info)
            } catch (e: Exception) {
                UiState.Failure(e.message ?: "Something went wrong")
            }
        }
    }

    fun reset() {
        state = UiState.Idle
    }

    /** Pulls the first http(s) link out of shared text like "Check this out https://vt.tiktok.com/xyz/". */
    fun extractUrl(text: String): String? =
        Regex("https?://\\S+").find(text)?.value?.trimEnd('.', ',', ')')
            ?.takeIf { it.contains("tiktok", ignoreCase = true) }
}
