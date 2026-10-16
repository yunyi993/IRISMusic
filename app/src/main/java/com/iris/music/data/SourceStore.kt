package com.iris.music.data

import android.content.Context
import org.json.JSONArray

object SourceStore {
    private const val PREFS = "iris_sources"
    private const val KEY = "bundles"

    fun getAll(context: Context): List<SourceBundle> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                SourceBundle.fromJson(arr.getString(i))
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, bundle: SourceBundle) {
        val current = getAll(context).toMutableList()
        current.removeAll { it.sources.any { s -> bundle.sources.any { n -> n.id == s.id } } }
        current.add(bundle)
        persist(context, current)
    }

    fun remove(context: Context, bundle: SourceBundle) {
        val current = getAll(context).toMutableList()
        current.removeAll { it.importedAt == bundle.importedAt }
        persist(context, current)
    }

    private fun persist(context: Context, list: List<SourceBundle>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
