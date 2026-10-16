package com.iris.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class PlaylistSong(
    val name: String,
    val artist: String,
    val album: String = ""
)

data class ParsedPlaylist(
    val title: String,
    val songs: List<PlaylistSong>
)

object QishuiPlaylistParser {

    private const val API = "https://api.suyanw.cn/api/qishuimusic.php"

    suspend fun parse(shareUrl: String): ParsedPlaylist? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$API?msg=${URLEncoder.encode(shareUrl, "UTF-8")}"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "IRISMusic/1.0")
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                parseResponse(text)
            }.getOrNull()
        }

    private fun parseResponse(text: String): ParsedPlaylist? {
        return runCatching {
            val root = JSONObject(text)
            val dataArr = root.optJSONArray("data")
                ?: root.optJSONArray("songs")
                ?: root.optJSONArray("list")
                ?: return null

            val songs = mutableListOf<PlaylistSong>()
            for (i in 0 until dataArr.length()) {
                val o = dataArr.optJSONObject(i) ?: continue
                val name = o.optString("name", "").takeIf { it.isNotBlank() }
                    ?: o.optString("title", "").takeIf { it.isNotBlank() }
                    ?: continue
                val artist = o.optString("artist", "").takeIf { it.isNotBlank() }
                    ?: o.optString("singer", "").takeIf { it.isNotBlank() }
                    ?: "未知"
                songs.add(
                    PlaylistSong(
                        name = name,
                        artist = artist,
                        album = o.optString("album", "")
                    )
                )
            }

            if (songs.isEmpty()) null
            else ParsedPlaylist("汽水歌单", songs)
        }.getOrNull()
    }
}
