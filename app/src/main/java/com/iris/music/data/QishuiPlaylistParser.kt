package com.iris.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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

    private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

    suspend fun parse(shareUrl: String): ParsedPlaylist? =
        withContext(Dispatchers.IO) {
            runCatching {
                val realUrl = expandShortUrl(shareUrl) ?: shareUrl
                val html = fetchHtml(realUrl) ?: return@runCatching null
                extractPlaylist(html)
            }.getOrNull()
        }

    /** 展开 qishui.douyin.com/s/xxx 短链 */
    private fun expandShortUrl(shortUrl: String): String? {
        return runCatching {
            val conn = URL(shortUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", UA)
            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (code in 300..399 && location != null) location else shortUrl
        }.getOrNull()
    }

    /** 拉取分享页 HTML */
    private fun fetchHtml(url: String): String? {
        return runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            html
        }.getOrNull()
    }

    /** 从 HTML 中提取歌单 JSON */
    private fun extractPlaylist(html: String): ParsedPlaylist? {
        val patterns = listOf(
            Regex("""window\._ROUTER_DATA\s*=\s*(\{.*?\})\s*;?\s*</script>""", RegexOption.DOT_MATCHES_ALL),
            Regex("""window\.__INITIAL_STATE__\s*=\s*(\{.*?\})\s*;?\s*</script>""", RegexOption.DOT_MATCHES_ALL),
            Regex("""<script[^>]+type="application/json"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        )

        for (pattern in patterns) {
            val match = pattern.find(html) ?: continue
            val json = match.groupValues[1].trim()
            runCatching {
                val root = JSONObject(json)
                val songs = parseSongList(root)
                if (songs.isNotEmpty()) {
                    return ParsedPlaylist(
                        title = findTitle(root) ?: "汽水歌单",
                        songs = songs
                    )
                }
            }
        }
        return null
    }

    private fun parseSongList(root: JSONObject): List<PlaylistSong> {
        val songs = mutableListOf<PlaylistSong>()
        walkJson(root) { obj ->
            val name = obj.optString("name", "").takeIf { it.isNotBlank() }
                ?: obj.optString("title", "").takeIf { it.isNotBlank() }
            val artist = obj.optString("artist", "").takeIf { it.isNotBlank() }
                ?: obj.optJSONArray("artists")?.let { arr ->
                    (0 until arr.length()).joinToString(", ") {
                        arr.optJSONObject(it)?.optString("name", "") ?: ""
                    }
                }?.takeIf { it.isNotBlank() }
            if (name != null && artist != null) {
                songs.add(
                    PlaylistSong(
                        name = name,
                        artist = artist,
                        album = obj.optString("album", "")
                    )
                )
            }
        }
        return songs.distinctBy { "${it.name}|${it.artist}" }
    }

    private fun findTitle(root: JSONObject): String? {
        var title: String? = null
        walkJson(root) { obj ->
            if (title == null) {
                title = obj.optString("playlistName", "").takeIf { it.isNotBlank() }
                    ?: obj.optString("playlist_name", "").takeIf { it.isNotBlank() }
                    ?: obj.optString("title", "").takeIf { it.isNotBlank() }
            }
        }
        return title
    }

    private fun walkJson(obj: JSONObject, action: (JSONObject) -> Unit) {
        action(obj)
        obj.keys().forEach { key ->
            when (val v = obj.opt(key)) {
                is JSONObject -> walkJson(v, action)
                is JSONArray -> for (i in 0 until v.length()) {
                    (v.opt(i) as? JSONObject)?.let { walkJson(it, action) }
                }
            }
        }
    }
}
