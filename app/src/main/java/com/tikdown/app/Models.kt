package com.tikdown.app

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DownloadItem(
    val id: String,
    val title: String,
    val uri: String, // content:// uri, or a file path on Android 9 and older
    val type: String, // video | audio | image
    val size: Long,
    val date: Long,
    val sourceUrl: String,
    val thumbPath: String?
)

data class HistoryItem(
    val url: String,
    val title: String,
    val thumb: String?,
    val date: Long
)

data class AppSettings(
    val autoDownload: Boolean = false,
    val defaultFormat: String = "video", // video | audio
    val saveToGallery: Boolean = true,
    val darkMode: Boolean = false,
    val notifications: Boolean = true
)

data class DlProgress(
    val title: String,
    val type: String,
    val fraction: Float?, // null = unknown size
    val remainingSec: Int?
)

val DownloadItem.formatLabel: String
    get() = when (type) {
        "audio" -> "MP3"
        "image" -> "JPG"
        else -> "MP4"
    }

val DownloadItem.mime: String
    get() = when (type) {
        "audio" -> "audio/mpeg"
        "image" -> "image/jpeg"
        else -> "video/mp4"
    }

fun DownloadItem.contentUri(context: Context): Uri =
    if (uri.startsWith("content://")) Uri.parse(uri)
    else FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(uri))

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val mb = bytes / 1024.0 / 1024.0
    return when {
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%d KB", (bytes / 1024).coerceAtLeast(1))
    }
}

fun formatDate(ms: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ms))
