package com.iris.music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iris.music.data.SourceImport
import com.iris.music.ui.theme.IrisColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceImportSheet(
    colors: IrisColors,
    onDismiss: () -> Unit,
    onImported: (Int, String) -> Unit,
    onImport: suspend (String) -> SourceImport.Result
) {
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
            Text("导入音源", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("粘贴音源地址或 JSON 文本", color = colors.subText, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = input,
                onValueChange = { input = it; message = null },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                placeholder = { Text("https://... 或 {\"lines\": [...]}", color = colors.subText) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.subText.copy(alpha = 0.3f)
                ),
                maxLines = 8
            )

            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = if (isError) colors.subText else colors.primary, fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (input.isBlank()) {
                        message = "请粘贴音源地址或 JSON"; isError = true; return@Button
                    }
                    loading = true; message = null
                    scope.launch {
                        val result = onImport(input.trim())
                        loading = false
                        when (result) {
                            is SourceImport.Result.Success -> {
                                val count = result.bundle.sources.size
                                val names = result.bundle.sources.joinToString("、") { it.name }
                                onImported(count, names)
                                onDismiss()
                            }
                            is SourceImport.Result.Error -> {
                                message = result.message; isError = true
                            }
                        }
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
                    Text("确认导入", color = colors.surface, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "支持 QingMusic 格式音源 · 按 id 自动去重",
                color = colors.subText.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
