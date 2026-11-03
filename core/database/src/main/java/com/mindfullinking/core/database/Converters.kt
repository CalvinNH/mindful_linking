package com.mindfullinking.core.database

import androidx.room.TypeConverter
import org.json.JSONArray
import java.time.Instant

/**
 * Room TypeConverters for Instant and JSON array lists.
 */
class Converters {

    @TypeConverter
    fun fromEpochMs(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun toEpochMs(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }

    @TypeConverter
    fun fromStringListJson(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val jsonArray = JSONArray(json)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun toStringListJson(list: List<String>?): String {
        if (list.isNullOrEmpty()) return "[]"
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }
}
