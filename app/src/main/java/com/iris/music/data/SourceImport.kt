package com.iris.music.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object SourceImport {

    sealed interface Result {
        data class Success(val bundle: SourceBundle) : Result
        data class Error(val message: String) : Result
    }

    suspend fun fromUrl(context: Context, url: String): Result =
        withContext(Dispatchers.IO) {
            if (!url.startsWith("http")) {
                return@withContext Result.Error("地址必须以 http:// 或 https:// 开头")
            }
            runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "IRISMusic/1.0")
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                SourceBundle.fromJson(text)
            }.fold(
                onSuccess = { bundle ->
                    if (bundle == null) {
                        Result.Error("JSON 解析失败：缺少 lines 数组或为空")
                    } else {
                        SourceStore.save(context, bundle)
                        Result.Success(bundle)
                    }
                },
                onFailure = { Result.Error("拉取失败：${it.message ?: "网络错误"}") }
            )
        }

    suspend fun fromText(context: Context, text: String): Result =
        withContext(Dispatchers.IO) {
            val bundle = SourceBundle.fromJson(text.trim())
            if (bundle == null) {
                Result.Error("JSON 解析失败：缺少 lines 数组或为空")
            } else {
                SourceStore.save(context, bundle)
                Result.Success(bundle)
            }
        }
}
