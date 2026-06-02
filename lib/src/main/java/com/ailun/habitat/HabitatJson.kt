package com.ailun.habitat

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

object HabitatJson {

    private val gson = GsonBuilder()
        .registerTypeAdapter(
            object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type,
            MapAnyTypeAdapter()
        )
        .create()

    /**
     * Deserializes JSON objects as [Map<String, Any?>] and JSON arrays as [List<Any?>],
     * converting numbers to the narrowest Kotlin type (Int → Long → Double) so that
     * handler code receiving `node.params?.get("millis")` always gets a sensible number.
     */
    private class MapAnyTypeAdapter : TypeAdapter<Map<String, Any?>>() {
        override fun write(out: JsonWriter, value: Map<String, Any?>?) {
            if (value == null) { out.nullValue(); return }
            out.beginObject()
            for ((k, v) in value) {
                out.name(k)
                writeValue(out, v)
            }
            out.endObject()
        }

        private fun writeValue(out: JsonWriter, v: Any?) {
            when (v) {
                null -> out.nullValue()
                is String -> out.value(v)
                is Boolean -> out.value(v)
                is Int -> out.value(v.toLong())
                is Long -> out.value(v)
                is Double -> out.value(v)
                is Float -> out.value(v.toDouble())
                is Number -> out.value(v.toDouble())
                is Map<*, *> -> {
                    out.beginObject()
                    for ((mk, mv) in v) {
                        out.name(mk.toString())
                        writeValue(out, mv)
                    }
                    out.endObject()
                }
                is List<*> -> {
                    out.beginArray()
                    for (item in v) writeValue(out, item)
                    out.endArray()
                }
                else -> out.value(v.toString())
            }
        }

        override fun read(reader: JsonReader): Map<String, Any?> {
            return readObject(reader)
        }

        private fun readObject(reader: JsonReader): Map<String, Any?> {
            val map = linkedMapOf<String, Any?>()
            reader.beginObject()
            while (reader.hasNext()) {
                map[reader.nextName()] = readValue(reader)
            }
            reader.endObject()
            return map
        }

        private fun readArray(reader: JsonReader): List<Any?> {
            val list = mutableListOf<Any?>()
            reader.beginArray()
            while (reader.hasNext()) {
                list.add(readValue(reader))
            }
            reader.endArray()
            return list
        }

        private fun readValue(reader: JsonReader): Any? {
            return when (reader.peek()) {
                com.google.gson.stream.JsonToken.STRING -> reader.nextString()
                com.google.gson.stream.JsonToken.NUMBER -> {
                    val num = reader.nextString()
                    narrowNumber(num)
                }
                com.google.gson.stream.JsonToken.BOOLEAN -> reader.nextBoolean()
                com.google.gson.stream.JsonToken.NULL -> { reader.nextNull(); null }
                com.google.gson.stream.JsonToken.BEGIN_OBJECT -> readObject(reader)
                com.google.gson.stream.JsonToken.BEGIN_ARRAY -> readArray(reader)
                else -> { reader.skipValue(); null }
            }
        }

        private fun narrowNumber(raw: String): Number {
            return raw.toDoubleOrNull()?.let { d ->
                if (d == d.toLong().toDouble() && !raw.contains('.')) {
                    val longVal = d.toLong()
                    if (longVal in Int.MIN_VALUE..Int.MAX_VALUE) longVal.toInt()
                    else longVal
                } else d
            } ?: 0.0
        }
    }

    fun fromJson(json: String): WorkflowGraph {
        val trimmed = json.trim()
        if (!trimmed.startsWith("{")) {
            throw IllegalArgumentException("JSON 必须是一个对象（以 { 开头），当前以 '${trimmed.take(10)}' 开头")
        }

        // Try preprocessing to strip non-object entries from nodes (comments/separators)
        val cleaned = try {
            val tree = com.google.gson.JsonParser.parseString(trimmed).asJsonObject
            val nodesObj = tree.getAsJsonObject("nodes")
            if (nodesObj != null) {
                val toRemove = nodesObj.entrySet()
                    .filter { !it.value.isJsonObject }
                    .map { it.key }
                    .toList()
                toRemove.forEach { nodesObj.remove(it) }
            }
            tree.toString()
        } catch (e: com.google.gson.JsonSyntaxException) {
            throw IllegalArgumentException(
                "JSON 语法错误: ${e.message}",
                e,
            )
        } catch (e: Exception) {
            // If preprocessing fails, try raw string as fallback
            trimmed
        }

        val graph = try {
            gson.fromJson(cleaned, WorkflowGraph::class.java)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "JSON 解析失败: ${e.message}",
                e,
            )
        }
        graph.validate()
        return graph
    }
}
