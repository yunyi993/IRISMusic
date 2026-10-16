package com.iris.music.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iris.music.data.ParsedPlaylist
import com.iris.music.data.PlaylistSong
import com.iris.music.data.QishuiApi
import com.iris.music.data.Song
import com.iris.music.ui.theme.GlassLevel
import com.iris.music.ui.theme.IrisColors
import com.iris.music.ui.theme.IrisShape
import com.iris.music.ui.theme.irisSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun OnlinePlaylistDetailSheet(
    playlist: ParsedPlaylist,
    colors: IrisColors,
    onDismiss: () -> Unit,
    onPlay: (Song) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onSheet = if (colors.isDark) androidx.compose.ui.graphics.Color.White
        else androidx.compose.ui.graphics.Color.Black
    var loadingTrackId by remember { mutableStateOf<String?>(null) }
    val drag = rememberSheetDragState()

    IrisSheet(
        colors = colors,
        onDismiss = onDismiss,
        drag = drag,
        contentModifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp, bottom = 20.dp)
    ) {
        SheetDragHandle(drag, onDismiss, colors.card)
        Spacer(Modifier.height(12.dp))

        Text(
            playlist.title,
            color = onSheet,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "汽水音乐 · ${playlist.songs.size} 首",
            color = colors.subText,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(playlist.songs, key = { it.trackId }) { song ->
                OnlineSongRow(
                    song = song,
                    colors = colors,
                    loading = loadingTrackId == song.trackId,
                    onClick = {
                        if (loadingTrackId != null) return@OnlineSongRow
                        loadingTrackId = song.trackId
                        scope.launch {
                            val url = withContext(Dispatchers.IO) {
                                QishuiApi.resolveStream(song.trackId)
                            }
                            loadingTrackId = null
                            if (url.isNullOrBlank()) {
                                Toast.makeText(
                                    context,
                                    "该歌曲仅提供加密流，暂无法播放",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@launch
                            }
                            val networkSong = Song(
                                id = url.hashCode().toLong(),
                                title = song.name,
                                artist = song.artist,
                                album = song.album,
                                durationMs = song.durationMs,
                                uri = Uri.parse(url),
                                albumArtUri = null,
                                filePath = "",
                                folderPath = "online_${playlist.playlistId}",
                                folderName = "汽水 · ${playlist.title}"
                            )
                            onPlay(networkSong)
                            Toast.makeText(context, "正在播放：${song.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun OnlineSongRow(
    song: PlaylistSong,
    colors: IrisColors,
    loading: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .irisSurface(
                level = GlassLevel.ROW,
                colors = colors,
                shape = IrisShape.item
            )
            .clickable(enabled = !loading) { Haptics.tap(); onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                song.name,
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (song.artist.isNotBlank()) {
                Text(
                    song.artist,
                    color = colors.subText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (loading) {
            CircularProgressIndicator(
                color = colors.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Text(
                if (song.durationMs > 0) formatDuration(song.durationMs) else "",
                color = colors.subText,
                fontSize = 11.sp
            )
        }
    }
}
