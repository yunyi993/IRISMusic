package com.iris.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class PlaylistSong(
    val trackId: String,
    val name: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val artworkUrl: String = ""
)

data class ParsedPlaylist(
    val playlistId: String,
    val title: String,
    val songs: List<PlaylistSong>
)

object QishuiPlaylistParser {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    suspend fun parse(shareUrl: String): ParsedPlaylist? =
        withContext(Dispatchers.IO) {
            runCatching {
                val realUrl = expandShortUrl(shareUrl) ?: shareUrl
                val playlistId = extractPlaylistId(realUrl) ?: return@runCatching null
                val qpl = QishuiApi.playlistDetails(playlistId)
                    ?: return@runCatching null
                if (qpl.songs.isEmpty()) return@runCatching null

                ParsedPlaylist(
                    playlistId = qpl.id,
                    title = qpl.name,
                    songs = qpl.songs.map { s ->
                        PlaylistSong(
                            trackId = s.id,
                            name = s.title,
                            artist = s.artist,
                            album = s.album,
                            durationMs = s.durationMs,
                            artworkUrl = s.artworkUrl
                        )
                    }
                )
            }.getOrNull()
        }

    /** 展开 qishui.douyin.com/s/xxx 短链 */
    private fun expandShortUrl(shortUrl: String): String? = runCatching {
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

    /**
     * 从 URL 抽取 playlist_id。
     * 支持：
     *   https://music.douyin.com/qishui/share/playlist?playlist_id=xxx
     *   https://www.qishui.com/share/playlist?playlist_id=xxx
     */
    private fun extractPlaylistId(url: String): String? {
        val idx = url.indexOf("playlist_id=")
        if (idx < 0) return null
        var start = idx + "playlist_id=".length
        var end = url.indexOf('&', start)
        if (end < 0) end = url.length
        val id = url.substring(start, end).trim()
        return id.takeIf { it.isNotBlank() }
    }
}
