package com.iris.music.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object QishuiApi {

    private const val UA_ANDROID =
        "com.luna.music/100198030 (Linux; U; Android 15; zh_CN_#Hans; ABR-AL80; Build/V417IR;tt-ok/3.12.13.19)"
    private const val UA_PC = "LunaPC/3.3.0(359450208)"
    private const val UA_WEB =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
    private const val BASE = "https://api.qishui.com/luna"
    private const val AID = "386088"

    private const val IMG_BASE = "https://p3-luna.douyinpic.com/img/"

    // ==================== HTTP ====================

    private fun httpGet(url: String, ua: String, extraHeaders: Map<String, String> = emptyMap()): String? =
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", ua)
            extraHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            conn.disconnect()
            if (code in 200..299) text else null
        }.getOrNull()

    private fun httpGetJson(url: String, ua: String, extraHeaders: Map<String, String> = emptyMap()): JSONObject? =
        httpGet(url, ua, extraHeaders)?.let { runCatching { JSONObject(it) }.getOrNull() }

    // ==================== 参数构造 ====================

    private fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun formOf(map: Map<String, String>): String =
        map.entries.joinToString("&") { "${urlEncode(it.key)}=${urlEncode(it.value)}" }

    private fun androidSearchParams(extra: Map<String, String> = emptyMap()): String {
        val params = mutableMapOf(
            "device_platform" to "android", "os" to "android", "ssmix" to "a",
            "cdid" to "46556f98-1720-4248-83da-62b74b60b46a",
            "channel" to "xiaomi_8478_64",
            "aid" to "8478", "app_name" to "luna",
            "version_code" to "100198030", "version_name" to "19.8.0",
            "manifest_version_code" to "100198030", "update_version_code" to "100198030",
            "resolution" to "1080*1920", "dpi" to "480",
            "device_type" to "ABR-AL80", "device_brand" to "HUAWEI",
            "language" to "zh", "os_api" to "35", "os_version" to "15", "ac" to "wifi",
            "device_model" to "ABR-AL80", "package" to "com.luna.music",
            "iid" to "2204957404569386", "device_id" to "2204957404565290",
            "_rticket" to System.currentTimeMillis().toString()
        )
        extra.forEach { (k, v) -> params[k] = v }
        return formOf(params)
    }

    private fun pcParams(extra: Map<String, String> = emptyMap()): String {
        val now = System.currentTimeMillis()
        val params = mutableMapOf(
            "aid" to AID, "app_name" to "luna_pc",
            "region" to "cn", "geo_region" to "cn", "os_region" to "cn",
            "device_id" to now.toString(), "iid" to (now + 1).toString(),
            "version_name" to "3.3.0", "version_code" to "30030000",
            "channel" to "official", "ac" to "wifi", "tz_name" to "Asia/Shanghai",
            "device_platform" to "windows", "device_type" to "Windows",
            "os_version" to "Windows 11", "fp" to now.toString()
        )
        extra.forEach { (k, v) -> params[k] = v }
        return formOf(params)
    }

    private fun pcHeaders(): Map<String, String> = mapOf(
        "x-luna-background-type" to "foreground",
        "x-luna-is-background-req" to "0",
        "x-luna-is-local-user" to "1"
    )

    // ==================== 数据模型 ====================

    data class QSong(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val artworkUrl: String
    )

    data class QPlaylist(
        val id: String,
        val name: String,
        val description: String,
        val artworkUrl: String,
        val songs: List<QSong>
    )

    // ==================== 搜索 ====================

    /**
     * 按关键词搜索汽水曲库
     */
    fun searchSongs(keyword: String, limit: Int = 20, page: Int = 1): List<QSong> {
        if (keyword.isBlank()) return emptyList()
        val cursor = (page - 1) * limit
        val url = "$BASE/search/track?" + androidSearchParams(
            mapOf(
                "q" to keyword,
                "cursor" to cursor.toString(),
                "count" to limit.toString(),
                "aid" to AID
            )
        )
        val body = httpGetJson(url, UA_ANDROID) ?: return emptyList()
        return parseTracks(body)
    }

    private fun parseTracks(body: JSONObject): List<QSong> {
        val out = mutableListOf<QSong>()
        val seen = mutableSetOf<String>()
        val groups = body.optJSONArray("result_groups") ?: return out
        for (i in 0 until groups.length()) {
            val group = groups.optJSONObject(i) ?: continue
            val data = group.optJSONArray("data") ?: continue
            for (j in 0 until data.length()) {
                val entry = data.optJSONObject(j) ?: continue
                val track = entry.optJSONObject("entity")
                    ?.optJSONObject("track") ?: continue
                val song = songFromTrack(track) ?: continue
                if (seen.add(song.id)) out.add(song)
            }
        }
        return out
    }

    // ==================== 歌单详情（拉全） ====================

    /**
     * 拉取完整歌单。内部自动分页，最多 20 页。
     */
    fun playlistDetails(playlistId: String): QPlaylist? {
        val songs = mutableListOf<QSong>()
        val seen = mutableSetOf<String>()
        var meta: QPlaylist? = null
        var cursor = ""

        var pageCount = 0
        while (pageCount < 20) {
            pageCount++
            val url = "$BASE/pc/playlist/detail?" + pcParams(
                mapOf(
                    "playlist_id" to playlistId,
                    "cursor" to cursor,
                    "count" to "100"
                )
            )
            val body = httpGetJson(url, UA_PC, pcHeaders()) ?: break

            if (meta == null) {
                meta = playlistFromItem(body.optJSONObject("playlist"))
                    ?: QPlaylist(playlistId, playlistId, "", "", emptyList())
            }

            val media = body.optJSONArray("media_resources") ?: break
            for (i in 0 until media.length()) {
                val item = media.optJSONObject(i) ?: continue
                if (item.optString("type", "track") != "track") continue
                val entity = item.optJSONObject("entity")
                val track = entity?.optJSONObject("track_wrapper")?.optJSONObject("track")
                    ?: entity?.optJSONObject("track")
                    ?: continue
                val song = songFromTrack(track) ?: continue
                if (seen.add(song.id)) songs.add(song)
            }

            val next = body.optString("next_cursor", "")
            if (next.isBlank() || next == cursor) break
            cursor = next
        }

        return meta?.copy(songs = songs)
    }

    // ==================== 单曲详情 ====================

    /**
     * 拿单曲详情，含播放信息
     */
    fun trackV2(trackId: String): JSONObject? {
        val url = "$BASE/pc/track_v2?" + pcParams(
            mapOf(
                "track_id" to trackId,
                "media_type" to "track",
                "device_platform" to "web",
                "channel" to "pc_web"
            )
        )
        return httpGetJson(url, UA_WEB)
    }

    /**
     * 解析播放地址。明文流返回 URL，加密流返回 null。
     */
    fun resolveStream(trackId: String): String? {
        val body = trackV2(trackId) ?: return null
        val player = body.optJSONObject("track_player")
            ?: body.optJSONObject("TrackPlayer")
            ?: return null

        // 先从 video_model 里找明文 URL
        val videoRaw = player.opt("video_model") ?: player.opt("VideoModel")
        val videoUrls = mutableListOf<String>()
        if (videoRaw != null) {
            val parsed = when (videoRaw) {
                is String -> runCatching { JSONObject(videoRaw) }.getOrNull()
                is JSONObject -> videoRaw
                else -> null
            }
            if (parsed != null) collectVideoUrls(parsed, videoUrls)
        }
        if (videoUrls.isNotEmpty()) return videoUrls[0]

        // 再从 url_player_info 找
        val infoUrl = player.optString("url_player_info", "")
            .takeIf { it.isNotBlank() }
            ?: player.optString("URLPlayerInfo", "").takeIf { it.isNotBlank() }
            ?: return null

        val infoBody = httpGetJson(infoUrl, UA_WEB) ?: return null
        return pickPlainStream(infoBody)
    }

    private fun pickPlainStream(body: JSONObject): String? {
        val list = body.optJSONObject("Result")
            ?.optJSONObject("Data")
            ?.optJSONArray("PlayInfoList")
            ?: return null
        var bestUrl: String? = null
        var bestSize = 0L
        for (i in 0 until list.length()) {
            val info = list.optJSONObject(i) ?: continue
            val url = info.optString("MainPlayURL", "")
                .takeIf { it.isNotBlank() }
                ?: info.optString("main_play_url", "").takeIf { it.isNotBlank() }
                ?: continue
            val auth = info.optString("PlayAuth", "")
                .takeIf { it.isNotBlank() }
                ?: info.optString("play_auth", "")
            if (auth.isNotBlank()) continue
            val size = info.optLong("Size", 0L)
            if (size > bestSize) {
                bestSize = size
                bestUrl = url
            } else if (bestUrl == null) {
                bestUrl = url
            }
        }
        return bestUrl?.let { secureUrl(it) }
    }

    private fun collectVideoUrls(value: Any?, out: MutableList<String>) {
        when (value) {
            null -> return
            is JSONArray -> for (i in 0 until value.length()) collectVideoUrls(value.opt(i), out)
            is JSONObject -> {
                val url = value.optString("MainPlayURL", "")
                    .takeIf { it.isNotBlank() }
                    ?: value.optString("main_play_url", "").takeIf { it.isNotBlank() }
                    ?: value.optString("main_url", "").takeIf { it.isNotBlank() }
                val auth = value.optString("PlayAuth", "")
                    .takeIf { it.isNotBlank() }
                    ?: value.optString("play_auth", "")
                if (url != null && auth.isNullOrBlank()) {
                    out.add(secureUrl(url))
                }
                value.keys().forEach { collectVideoUrls(value.opt(it), out) }
            }
        }
    }

    // ==================== 辅助 ====================

    private fun songFromTrack(track: JSONObject): QSong? {
        val id = track.optString("id", "").takeIf { it.isNotBlank() } ?: return null
        val title = track.optString("name", "").takeIf { it.isNotBlank() } ?: return null
        val artists = track.optJSONArray("artists")
        val artist = if (artists != null) {
            (0 until artists.length()).mapNotNull {
                artists.optJSONObject(it)?.optString("name", "")?.takeIf { s -> s.isNotBlank() }
            }.joinToString(", ")
        } else ""

        val album = track.optJSONObject("album")
        val albumName = album?.optString("name", "") ?: ""
        val cover = imageUrl(album?.optJSONObject("url_cover"))

        val durationRaw = track.optLong("duration", 0L)
        val durationMs = if (durationRaw in 1..10000) durationRaw * 1000 else durationRaw

        return QSong(id, title, artist, albumName, durationMs, cover)
    }

    private fun playlistFromItem(item: JSONObject?): QPlaylist? {
        item ?: return null
        val id = item.optString("id", "").takeIf { it.isNotBlank() } ?: return null
        val name = item.optString("title", "")
            .takeIf { it.isNotBlank() }
            ?: item.optString("name", "").takeIf { it.isNotBlank() }
            ?: return null
        val desc = item.optString("desc", "")
        val cover = imageUrl(item.optJSONObject("url_cover"))
        return QPlaylist(id, name, desc, cover, emptyList())
    }

    private fun imageUrl(img: JSONObject?): String {
        img ?: return ""
        val urls = img.optJSONArray("urls") ?: return ""
        if (urls.length() == 0) return ""
        val cover = urls.optString(0, "")
        val uri = img.optString("uri", "")
        val prefix = img.optString("template_prefix", "")
        if (uri.isNotBlank() && prefix.isNotBlank()) {
            return "$IMG_BASE$uri~$prefix-resize:960:960.png"
        }
        val built = if (uri.isNotBlank() && !cover.contains(uri)) cover + uri else cover
        return secureUrl(built)
    }

    private fun secureUrl(value: String): String {
        if (value.isBlank()) return ""
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://") -> "https://${value.substring(7)}"
            else -> value
        }
    }
}
