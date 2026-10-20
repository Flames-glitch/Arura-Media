package com.aurora.media.data

import android.content.Context
import org.json.JSONArray
import java.io.File

class LibraryStore(context: Context) {
    private val file = File(context.filesDir, "library-v1.json")

    fun read(): List<MediaEntry> = runCatching {
        val array = JSONArray(file.readText())
        (0 until array.length()).map { MediaEntry.fromJson(array.getJSONObject(it)) }
    }.getOrElse { emptyList() }

    @Synchronized
    fun write(items: List<MediaEntry>) {
        val temp = File(file.parentFile, "library-v1.json.tmp")
        temp.writeText(JSONArray().apply { items.forEach { put(it.toJson()) } }.toString())
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }
}
