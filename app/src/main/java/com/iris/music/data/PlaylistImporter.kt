package com.iris.music.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object PlaylistImporter {

    suspend fun importFromQishui(
        context: Context,
        shareUrl: String
    ): Result<ParsedPlaylist> = withContext(Dispatchers.IO) {
        val parsed = QishuiPlaylistParser.parse(shareUrl)
            ?: return@withContext Result.failure(Exception("解析失败，检查链接是否有效"))

        if (parsed.songs.isEmpty()) {
            return@withContext Result.failure(Exception("未解析到曲目"))
        }

        // 按 playlistId 覆盖式存储：同一歌单重复导入不翻倍
        val existing = loadAll(context).toMutableList()
        existing.removeAll { it.playlistId == parsed.playlistId }
        existing.add(parsed)
        persist(context, existing)

        Result.success(parsed)
    }

    /** 读取所有已导入歌单 */
    fun loadAll(context: Context): List<ParsedPlaylist> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                fromJson(arr.optJSONObject(i))
            }
        }.getOrDefault(emptyList())
    }

    /** 读取单曲列表（用于构造网络 Song） */
    fun loadSongs(context: Context, playlistId: String): List<PlaylistSong> =
        loadAll(context).firstOrNull { it.playlistId == playlistId }?.songs ?: emptyList()

    private fun persist(context: Context, list: List<ParsedPlaylist>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    private fun toJson(p: ParsedPlaylist): JSONObject {
        val songsArr = JSONArray()
        p.songs.forEach { s ->
            songsArr.put(JSONObject().apply {
                put("trackId", s.trackId)
                put("name", s.name)
                put("artist", s.artist)
                put("album", s.album)
                put("durationMs", s.durationMs)
                put("artworkUrl", s.artworkUrl)
            })
        }
        return JSONObject().apply {
            put("playlistId", p.playlistId)
            put("title", p.title)
            put("songs", songsArr)
        }
    }

    private fun fromJson(o: JSONObject?): ParsedPlaylist? {
        o ?: return null
        val pid = o.optString("playlistId", "").takeIf { it.isNotBlank() } ?: return null
        val title = o.optString("title", "汽水歌单")
        val arr = o.optJSONArray("songs") ?: return null
        val songs = (0 until arr.length()).mapNotNull { i ->
            val s = arr.optJSONObject(i) ?: return@mapNotNull null
            PlaylistSong(
                trackId = s.optString("trackId", ""),
                name = s.optString("name", ""),
                artist = s.optString("artist", ""),
                album = s.optString("album", ""),
                durationMs = s.optLong("durationMs", 0L),
                artworkUrl = s.optString("artworkUrl", "")
            )
        }
        return ParsedPlaylist(pid, title, songs)
    }

    private const val PREFS = "iris_imported_playlists"
    private const val KEY = "playlists"
}
