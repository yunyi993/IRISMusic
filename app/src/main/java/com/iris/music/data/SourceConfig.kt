package com.iris.music.data

import org.json.JSONArray
import org.json.JSONObject

data class MusicSource(
    val id: String,
    val name: String,
    val searchApi: String,
    val detailApi: String,
    val levels: List<String>,
    val enabled: Boolean = true
)

data class SourceBundle(
    val sources: List<MusicSource>,
    val importedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        val arr = JSONArray()
        sources.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("searchApi", s.searchApi)
                put("detailApi", s.detailApi)
                put("levels", JSONArray(s.levels))
                put("enabled", s.enabled)
            })
        }
        return JSONObject().put("lines", arr).toString()
    }

    companion object {
        fun fromJson(text: String): SourceBundle? = runCatching {
            val root = JSONObject(text)
            val arr = root.getJSONArray("lines")
            val list = mutableListOf<MusicSource>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    MusicSource(
                        id = o.getString("id"),
                        name = o.optString("name", o.getString("id")),
                        searchApi = o.getString("searchApi"),
                        detailApi = o.getString("detailApi"),
                        levels = o.optJSONArray("levels")?.let { la ->
                            (0 until la.length()).map { la.getString(it) }
                        } ?: emptyList()
                    )
                )
            }
            if (list.isEmpty()) null else SourceBundle(list)
        }.getOrNull()
    }
}
