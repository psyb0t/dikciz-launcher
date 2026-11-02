package org.fossify.home.dikciz

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Array as JavaArray
import java.util.UUID

internal class DikcizAndroidSdkValues(private val catalogue: DikcizAndroidSdkCatalogue) {
    data class Handle(val value: Any, val className: String, val callback: Callback? = null)
    class Callback {
        val events = ArrayDeque<JSONObject>()
        var sequence = 0L
        var dropped = 0L
        var active = true
    }
    private val handles = linkedMapOf<String, Handle>()

    @Synchronized
    fun add(value: Any, preferred: String? = null, callback: Callback? = null): JSONObject {
        handles.entries.firstOrNull { it.value.value === value }?.let {
            return reference(it.key, it.value)
        }
        if (handles.size >= MAXIMUM_HANDLES) DikcizNativeCommands.failure("android_api_handle_limit", "Release unused SDK object handles before creating more")
        val name = facade(value.javaClass, preferred)
        val handle = Handle(value, name, callback)
        val id = UUID.randomUUID().toString()
        handles[id] = handle
        return reference(id, handle)
    }

    @Synchronized
    fun get(id: String): Handle = handles[id]
        ?: DikcizNativeCommands.failure("android_api_object_not_found", "SDK handle is absent or belongs to an earlier process")

    @Synchronized
    fun remove(id: String): Handle = handles.remove(id)
        ?: DikcizNativeCommands.failure("android_api_object_not_found", "SDK handle is absent or already released")

    @Synchronized
    fun list(): List<JSONObject> = handles.map { (id, handle) -> reference(id, handle) }

    fun read(id: String, request: JSONObject): JSONObject {
        val value = get(id).value
        val offset = request.optInt("offset", 0)
        val limit = request.optInt("limit", DikcizNativeCommands.DEFAULT_LIMIT)
        if (value is CharSequence) {
            val start = minOf(offset, value.length)
            val end = minOf(start + limit, value.length)
            return JSONObject().put("text", value.subSequence(start, end).toString())
                .put("offset", offset).put("limit", limit).put("hasMore", end < value.length)
                .put("total", value.length).put("unit", "utf16_code_units")
        }
        if (value is Map<*, *>) {
            val items = JSONArray()
            val selected = value.entries.asSequence().drop(offset).take(limit)
            selected.forEach { entry -> items.put(JSONObject().put("key", encode(entry.key)).put("value", encode(entry.value))) }
            return JSONObject().put("items", items).put("offset", offset).put("limit", limit)
                .put("total", value.size).put("hasMore", offset.toLong() + limit < value.size)
        }
        val size = when {
            value.javaClass.isArray -> JavaArray.getLength(value)
            value is List<*> -> value.size
            else -> DikcizNativeCommands.invalid("SDK object has no indexed contents; invoke its discovered public read members")
        }
        val items = JSONArray()
        for (index in minOf(offset, size) until minOf(offset.toLong() + limit, size.toLong()).toInt()) {
            val element = if (value is List<*>) value[index] else JavaArray.get(value, index)
            items.put(JSONObject().put("index", index).put("value", encode(element)))
        }
        return JSONObject().put("items", items).put("offset", offset).put("limit", limit)
            .put("total", size).put("hasMore", offset.toLong() + limit < size)
    }

    private fun reference(id: String, handle: Handle) = JSONObject().put("objectRef", id).put("className", handle.className)
        .put("callback", handle.callback != null).put("closeable", handle.value is AutoCloseable)

    private fun facade(type: Class<*>, preferred: String?): String {
        if (preferred != null && preferred != "void" && preferred != "java.lang.Object" && !preferred.endsWith("[]") && catalogue.names().contains(preferred)) return preferred
        var current: Class<*>? = type
        while (current != null) {
            val name = current.name.replace('$', '.')
            if (catalogue.names().contains(name)) return name
            current = current.superclass
        }
        return "java.lang.Object"
    }

    fun decode(value: Any?, expected: Class<*>, nullable: Boolean = true, depth: Int = 0): Any? {
        if (depth > MAXIMUM_DEPTH) DikcizNativeCommands.invalid("SDK arguments are nested too deeply")
        if (value == null || value === JSONObject.NULL) {
            if (expected.isPrimitive || !nullable) DikcizNativeCommands.invalid("SDK argument cannot be null")
            return null
        }
        if (value is JSONObject) {
            if (value.length() == 1 && value.has("objectRef")) {
                val handle = get(value.getString("objectRef"))
                if (!boxed(expected).isInstance(handle.value)) DikcizNativeCommands.invalid("SDK objectRef has an incompatible type")
                return handle.value
            }
            if (value.length() == 1 && value.has("className") && expected == Class::class.java) {
                val name = value.getString("className")
                if (!catalogue.permitted(name)) DikcizNativeCommands.invalid("SDK class token is unavailable")
                return catalogue.resolve(name)
            }
            if (value.length() == 1 && value.has("long") && boxed(expected) == java.lang.Long::class.java) {
                val text = value.getString("long")
                if (!Regex("-?(0|[1-9][0-9]{0,18})").matches(text)) DikcizNativeCommands.invalid("Invalid decimal long")
                return text.toLongOrNull() ?: DikcizNativeCommands.invalid("Decimal long overflows signed 64-bit range")
            }
            if (value.length() == 1 && value.has("bytesBase64") && expected == ByteArray::class.java) {
                val encoded = value.getString("bytesBase64")
                if (encoded.length > MAXIMUM_BYTES_BASE64 || !BASE64_PATTERN.matches(encoded)) DikcizNativeCommands.invalid("Invalid or oversized base64 bytes")
                return Base64.decode(encoded, Base64.NO_WRAP)
            }
            if (value.length() == 2 && value.has("arrayType") && value.has("values") && expected.isArray) {
                val component = catalogue.resolve(value.getString("arrayType"))
                if (component != expected.componentType) DikcizNativeCommands.invalid("arrayType must match the exact SDK component type")
                return decodeArray(value.getJSONArray("values"), expected, depth)
            }
            DikcizNativeCommands.invalid("Unsupported SDK argument object; use a typed objectRef or documented value tag")
        }
        if (value is JSONArray && expected.isArray) return decodeArray(value, expected, depth)
        if (expected.isEnum && value is String) {
            return expected.enumConstants.orEmpty().firstOrNull { (it as Enum<*>).name == value }
                ?: DikcizNativeCommands.invalid("Unknown SDK enum constant")
        }
        if (value is String && (expected == String::class.java || expected == CharSequence::class.java || expected == Any::class.java)) return value
        if (value is String && boxed(expected) == java.lang.Character::class.java && value.length == 1) return value[0]
        if (value is Boolean && (boxed(expected) == java.lang.Boolean::class.java || expected == Any::class.java)) return value
        if (value is Number) return number(value, expected)
        DikcizNativeCommands.invalid("SDK argument does not match its declared parameter type")
    }

    private fun decodeArray(values: JSONArray, expected: Class<*>, depth: Int): Any {
        if (values.length() > MAXIMUM_ARRAY) DikcizNativeCommands.invalid("SDK array exceeds 4096 elements")
        val array = JavaArray.newInstance(expected.componentType!!, values.length())
        for (index in 0 until values.length()) JavaArray.set(array, index, decode(values.get(index), expected.componentType!!, depth = depth + 1))
        return array
    }

    private fun number(value: Number, expected: Class<*>): Any {
        val boxed = boxed(expected)
        if (boxed == java.lang.Double::class.java || boxed == java.lang.Float::class.java) {
            if (!value.toDouble().isFinite()) DikcizNativeCommands.invalid("SDK numeric argument must be finite")
            if (boxed == java.lang.Float::class.java) {
                val result = value.toFloat()
                if (!result.isFinite()) DikcizNativeCommands.invalid("SDK float argument overflows")
                return result
            }
            return value.toDouble()
        }
        if (expected == Any::class.java) return value
        if (value !is Int && value !is Long) DikcizNativeCommands.invalid("SDK integral argument requires an integer")
        val number = value.toLong()
        return when (boxed) {
            java.lang.Byte::class.java -> if (number in Byte.MIN_VALUE..Byte.MAX_VALUE) number.toByte() else DikcizNativeCommands.invalid("SDK byte overflows")
            java.lang.Short::class.java -> if (number in Short.MIN_VALUE..Short.MAX_VALUE) number.toShort() else DikcizNativeCommands.invalid("SDK short overflows")
            java.lang.Integer::class.java -> if (number in Int.MIN_VALUE..Int.MAX_VALUE) number.toInt() else DikcizNativeCommands.invalid("SDK int overflows")
            java.lang.Long::class.java -> number
            else -> DikcizNativeCommands.invalid("SDK number has an incompatible type")
        }
    }

    fun encode(value: Any?, preferred: String? = null, depth: Int = 0): Any {
        if (value == null) return JSONObject.NULL
        if (value is Boolean || value is Int || value is Short || value is Byte) return value
        if (value is Long) return if (value in -MAXIMUM_JSON_INTEGER..MAXIMUM_JSON_INTEGER) value else JSONObject().put("long", value.toString())
        if (value is Float || value is Double) {
            return if ((value as Number).toDouble().isFinite()) value else JSONObject().put("nonFiniteNumber", value.toString())
        }
        if (value is Char) return value.toString()
        if (value is CharSequence) {
            val text = value.toString()
            if (text.length <= MAXIMUM_TEXT) return text
            return add(text, "java.lang.String").put("length", text.length).put("preview", text.take(MAXIMUM_TEXT)).put("previewTruncated", true)
        }
        if (value is Enum<*>) return value.name
        if (value is Class<*>) return JSONObject().put("className", value.name.replace('$', '.'))
        if (value is ByteArray && value.size <= MAXIMUM_INLINE_BYTES) return JSONObject().put("bytesBase64", Base64.encodeToString(value, Base64.NO_WRAP))
        if (value.javaClass.isArray && depth < MAXIMUM_DEPTH) {
            val length = JavaArray.getLength(value)
            val items = JSONArray()
            for (index in 0 until minOf(length, MAXIMUM_INLINE_ITEMS)) items.put(encode(JavaArray.get(value, index), depth = depth + 1))
            return add(value).put("items", items).put("total", length).put("itemsTruncated", length > MAXIMUM_INLINE_ITEMS)
        }
        if (value is List<*> && depth < MAXIMUM_DEPTH) {
            val items = JSONArray()
            for (index in 0 until minOf(value.size, MAXIMUM_INLINE_ITEMS)) items.put(encode(value[index], depth = depth + 1))
            return add(value, "java.util.List").put("items", items).put("total", value.size).put("itemsTruncated", value.size > MAXIMUM_INLINE_ITEMS)
        }
        return add(value, preferred)
    }

    private fun boxed(type: Class<*>): Class<*> = BOXES[type] ?: type

    companion object {
        const val MAXIMUM_HANDLES = 256
        const val MAXIMUM_EVENTS = 256
        const val MAXIMUM_TEXT = 4096
        private const val MAXIMUM_DEPTH = 8
        private const val MAXIMUM_ARRAY = 4096
        private const val MAXIMUM_BYTES_BASE64 = 349_528
        private const val MAXIMUM_INLINE_BYTES = 16_384
        private const val MAXIMUM_INLINE_ITEMS = 16
        private const val MAXIMUM_JSON_INTEGER = 9_007_199_254_740_991L
        private val BASE64_PATTERN = Regex("(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?")
        private val BOXES = mapOf(
            Boolean::class.javaPrimitiveType to java.lang.Boolean::class.java,
            Byte::class.javaPrimitiveType to java.lang.Byte::class.java,
            Short::class.javaPrimitiveType to java.lang.Short::class.java,
            Int::class.javaPrimitiveType to java.lang.Integer::class.java,
            Long::class.javaPrimitiveType to java.lang.Long::class.java,
            Float::class.javaPrimitiveType to java.lang.Float::class.java,
            Double::class.javaPrimitiveType to java.lang.Double::class.java,
            Char::class.javaPrimitiveType to java.lang.Character::class.java,
        )
    }
}
