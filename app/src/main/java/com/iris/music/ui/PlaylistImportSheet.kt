package com.iris.music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iris.music.data.ParsedPlaylist
import com.iris.music.data.PlaylistImporter
import com.iris.music.ui.theme.IrisColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistImportSheet(
    colors: IrisColors,
    onDismiss: () -> Unit,
    onImported: (ParsedPlaylist) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text("导入歌单", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("粘贴汽水音乐分享链接", color = colors.subText, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = input,
                onValueChange = { input = it; message = null },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://qishui.douyin.com/s/...", color = colors.subText) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.subText.copy(alpha = 0.3f)
                ),
                maxLines = 4
            )

            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = if (isError) colors.error else colors.primary, fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (input.isBlank()) {
                        message = "请粘贴链接"; isError = true; return@Button
                    }
                    loading = true; message = null
                    scope.launch {
                        val result = PlaylistImporter.importFromQishui(context, input.trim())
                        loading = false
                        result.fold(
                            onSuccess = { playlist -> onImported(playlist); onDismiss() },
                            onFailure = { e -> message = e.message ?: "解析失败"; isError = true }
                        )
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                if (loading) {
                    CircularProgressIndicator(color = colors.surface, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text("解析并导入", color = colors.surface, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "支持汽水音乐单曲 / 歌单分享链接",
                color = colors.subText.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
