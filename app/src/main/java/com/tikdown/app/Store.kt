package com.tikdown.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Tiny SharedPreferences-backed storage for settings, downloads and history. */
class Store(context: Context) {
    private val p = context.getSharedPreferences("tikdown", Context.MODE_PRIVATE)

    fun settings() = AppSettings(
        autoDownload = p.getBoolean("autoDownload", false),
        defaultFormat = p.getString("defaultFormat", "video") ?: "video",
        saveToGallery = p.getBoolean("saveToGallery", true),
        darkMode = p.getBoolean("darkMode", false),
        notifications = p.getBoolean("notifications", true)
    )

    fun saveSettings(s: AppSettings) {
        p.edit()
            .putBoolean("autoDownload", s.autoDownload)
            .putString("defaultFormat", s.defaultFormat)
            .putBoolean("saveToGallery", s.saveToGallery)
            .putBoolean("darkMode", s.darkMode)
            .putBoolean("notifications", s.notifications)
            .apply()
    }

    fun downloads(): List<DownloadItem> {
        val arr = JSONArray(p.getString("downloads", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            DownloadItem(
                id = o.getString("id"),
                title = o.getString("title"),
                uri = o.getString("uri"),
                type = o.getString("type"),
                size = o.optLong("size"),
                date = o.optLong("date"),
                sourceUrl = o.optString("sourceUrl"),
                thumbPath = o.optString("thumbPath").takeIf { s -> s.isNotBlank() }
            )
        }
    }

    fun saveDownloads(list: List<DownloadItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("title", it.title); put("uri", it.uri)
                put("type", it.type); put("size", it.size); put("date", it.date)
                put("sourceUrl", it.sourceUrl); put("thumbPath", it.thumbPath ?: "")
            })
        }
        p.edit().putString("downloads", arr.toString()).apply()
    }

    fun history(): List<HistoryItem> {
        val arr = JSONArray(p.getString("history", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            HistoryItem(
                url = o.getString("url"),
                title = o.optString("title"),
                thumb = o.optString("thumb").takeIf { s -> s.isNotBlank() },
                date = o.optLong("date")
            )
        }
    }

    fun saveHistory(list: List<HistoryItem>) {
        val arr = JSONArray()
        list.take(100).forEach {
            arr.put(JSONObject().apply {
                put("url", it.url); put("title", it.title)
                put("thumb", it.thumb ?: ""); put("date", it.date)
            })
        }
        p.edit().putString("history", arr.toString()).apply()
    }
}
