package com.iris.music.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PlaylistImporter {

    suspend fun importFromQishui(
        context: Context,
        shareUrl: String
    ): Result<ParsedPlaylist> = withContext(Dispatchers.IO) {
        val parsed = QishuiPlaylistParser.parse(shareUrl)
            ?: return@withContext Result.failure(Exception("解析失败，链接可能已失效"))

        if (parsed.songs.isEmpty()) {
            return@withContext Result.failure(Exception("未解析到曲目"))
        }

        // 存储到应用私有目录
        val dir = File(context.filesDir, "imported_playlists").apply { mkdirs() }
        val safeTitle = parsed.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val file = File(dir, "$safeTitle.txt")

        val lines = parsed.songs.map { "${it.name} - ${it.artist}" }
        file.writeText(
            "# ${parsed.title}\n" +
                    "# imported from: $shareUrl\n" +
                    lines.joinToString("\n")
        )

        Result.success(parsed)
    }

    fun listImported(context: Context): List<File> {
        val dir = File(context.filesDir, "imported_playlists")
        return dir.listFiles()?.toList() ?: emptyList()
    }
}
