package com.tikdown.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

object Downloader {

    /** Queues a download with the system DownloadManager (saves to the public Downloads/TikDown folder). */
    fun enqueue(context: Context, option: MediaOption, title: String): Long {
        val ext = when (option.type) {
            "audio" -> "mp3"
            "image" -> "jpg"
            else -> "mp4"
        }
        val safe = title
            .replace(Regex("[^\\p{L}\\p{N} _-]"), "")
            .trim()
            .take(40)
            .ifBlank { "tiktok" }
        val fileName = "${safe}_${System.currentTimeMillis()}.$ext"
        val mime = when (ext) {
            "mp3" -> "audio/mpeg"
            "jpg" -> "image/jpeg"
            else -> "video/mp4"
        }

        val request = DownloadManager.Request(Uri.parse(option.url))
            .setTitle(fileName)
            .setDescription("TikDown - ${option.label}")
            .setMimeType(mime)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "TikDown/$fileName")
            .addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }
}
