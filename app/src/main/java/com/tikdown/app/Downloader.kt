package com.tikdown.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

object Downloader {

    private const val FOLDER = "TikDown"

    private class Target(
        val stream: OutputStream,
        val uri: String,
        val finish: () -> Unit,
        val discard: () -> Unit
    )

    /** Downloads one media option with live progress. Cancelling the calling coroutine removes the partial file. */
    suspend fun download(
        context: Context,
        option: MediaOption,
        info: VideoInfo,
        sourceUrl: String,
        saveToGallery: Boolean,
        onProgress: (DlProgress) -> Unit
    ): DownloadItem = withContext(Dispatchers.IO) {
        val ext = when (option.type) { "audio" -> "mp3"; "image" -> "jpg"; else -> "mp4" }
        val mime = when (ext) { "mp3" -> "audio/mpeg"; "jpg" -> "image/jpeg"; else -> "video/mp4" }
        val safe = info.title
            .replace(Regex("[^\\p{L}\\p{N} _-]"), "")
            .trim()
            .take(40)
            .ifBlank { "tiktok" }
        val now = System.currentTimeMillis()
        val fileName = "${safe}_$now.$ext"

        val target = openTarget(context, fileName, mime, option.type, saveToGallery)
        var ok = false
        var written = 0L
        try {
            val conn = (URL(option.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
            }
            try {
                if (conn.responseCode !in 200..299) error("Server returned ${conn.responseCode}")
                val total = conn.contentLengthLong
                val started = System.currentTimeMillis()
                var lastReport = 0L
                conn.inputStream.use { input ->
                    target.stream.use { out ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            written += n
                            val t = System.currentTimeMillis()
                            if (t - lastReport > 150) {
                                lastReport = t
                                val secs = ((t - started) / 1000.0).coerceAtLeast(0.2)
                                val speed = written / secs
                                val fraction = if (total > 0) (written.toFloat() / total).coerceIn(0f, 1f) else null
                                val remaining = if (total > 0 && speed > 0) ((total - written) / speed).toInt() else null
                                onProgress(DlProgress(info.title, option.type, fraction, remaining))
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            target.finish()
            ok = true
            val id = now.toString()
            DownloadItem(
                id = id,
                title = info.title,
                uri = target.uri,
                type = option.type,
                size = written,
                date = now,
                sourceUrl = sourceUrl,
                thumbPath = saveThumb(context, info.thumbnail, id)
            )
        } finally {
            if (!ok) target.discard()
        }
    }

    private fun openTarget(
        context: Context,
        name: String,
        mime: String,
        type: String,
        gallery: Boolean
    ): Target {
        val dirName = when {
            !gallery -> Environment.DIRECTORY_DOWNLOADS
            type == "audio" -> Environment.DIRECTORY_MUSIC
            type == "image" -> Environment.DIRECTORY_PICTURES
            else -> Environment.DIRECTORY_MOVIES
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = when {
                !gallery -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
                type == "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                type == "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$dirName/$FOLDER")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: error("Could not create file")
            val stream = resolver.openOutputStream(uri) ?: error("Could not open file")
            return Target(
                stream = stream,
                uri = uri.toString(),
                finish = {
                    val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                },
                discard = { runCatching { resolver.delete(uri, null, null) } }
            )
        }

        // Android 9 and older: write straight to the public folder
        val dir = File(Environment.getExternalStoragePublicDirectory(dirName), FOLDER).apply { mkdirs() }
        val file = File(dir, name)
        return Target(
            stream = FileOutputStream(file),
            uri = file.absolutePath,
            finish = { MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null) },
            discard = { runCatching { file.delete() } }
        )
    }

    private fun saveThumb(context: Context, url: String?, id: String): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val f = File(context.filesDir, "thumbs/$id.jpg").apply { parentFile?.mkdirs() }
            URL(url).openStream().use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            f.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun delete(context: Context, item: DownloadItem) {
        runCatching {
            if (item.uri.startsWith("content://")) {
                context.contentResolver.delete(Uri.parse(item.uri), null, null)
            } else {
                File(item.uri).delete()
            }
        }
        item.thumbPath?.let { runCatching { File(it).delete() } }
    }
}

object Notifier {
    fun done(context: Context, title: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel("downloads", "Downloads", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val n = NotificationCompat.Builder(context, "downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(title)
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), n)
    }
}
