package com.tikdown.app

import com.tikdown.app.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class MediaOption(
    val label: String,
    val type: String, // "video" | "audio" | "image"
    val url: String
)

data class VideoInfo(
    val title: String,
    val thumbnail: String?,
    val author: String?,
    val media: List<MediaOption>
)

object TikApi {

    /** Calls the API and returns everything it gives back. Must run off the main thread. */
    fun fetch(tiktokUrl: String): VideoInfo {
        val endpoint = BuildConfig.API_URL + "?url=" + URLEncoder.encode(tiktokUrl, "UTF-8")
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Server error ($code)")
            return parse(body)
        } finally {
            conn.disconnect()
        }
    }

    internal fun parse(body: String): VideoInfo {
        val root = JSONObject(body)
        if (!root.optBoolean("success", true)) {
            error(root.optString("error", "The API could not process this link"))
        }
        val result = root.optJSONObject("result") ?: error("Empty response from API")
        val normalized = root.optJSONObject("normalized")

        val title = normalized?.optString("title").takeUnless { it.isNullOrBlank() }
            ?: result.optString("title", "TikTok")
        val thumb = (normalized?.optString("thumbnail") ?: result.optString("thumbnail"))
            .takeUnless { it.isNullOrBlank() || it == "null" }
        val author = normalized?.optString("author").takeUnless { it.isNullOrBlank() || it == "null" }

        val options = mutableListOf<MediaOption>()

        // Preferred: the API's normalized list (already labelled)
        normalized?.optJSONArray("media")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val url = o.optString("url")
                if (url.isNotBlank()) {
                    options += MediaOption(
                        label = o.optString("label", "Download"),
                        type = o.optString("type", "video"),
                        url = url
                    )
                }
            }
        }

        // Fallback: raw result arrays
        if (options.isEmpty()) {
            result.optJSONArray("video")?.let { arr ->
                for (i in 0 until arr.length()) {
                    options += MediaOption("Video ${i + 1}", "video", arr.getString(i))
                }
            }
            result.optJSONArray("audio")?.let { arr ->
                for (i in 0 until arr.length()) {
                    options += MediaOption("Audio (MP3) ${i + 1}", "audio", arr.getString(i))
                }
            }
            result.optJSONArray("images")?.let { arr ->
                for (i in 0 until arr.length()) {
                    options += MediaOption("Image ${i + 1}", "image", arr.getString(i))
                }
            }
        }

        if (options.isEmpty()) error("No downloadable media found")
        return VideoInfo(title.trim(), thumb, author, options)
    }
}
