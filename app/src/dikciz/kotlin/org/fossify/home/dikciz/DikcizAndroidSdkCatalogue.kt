package org.fossify.home.dikciz

import android.content.Context
import org.json.JSONObject
import java.util.zip.GZIPInputStream

internal class DikcizAndroidSdkCatalogue(context: Context) {
    private val assets = context.applicationContext.assets
    val index = JSONObject(assets.open(INDEX_PATH).bufferedReader().use { it.readText() })
    private val policy = index.getJSONObject("policy")
    private val classes = index.getJSONArray("classes").let { entries ->
        (0 until entries.length()).associate { position ->
            entries.getJSONObject(position).let { it.getString("name") to it }
        }
    }
    private val cache = object : LinkedHashMap<String, JSONObject>(CACHE_CLASSES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JSONObject>?) = size > CACHE_CLASSES
    }

    fun names(): List<String> = classes.keys.toList()
    fun metadata(name: String): JSONObject = classes[name]
        ?: DikcizNativeCommands.failure("android_api_class_not_found", "Class is not in the pinned public SDK catalogue")

    @Synchronized
    fun document(name: String): JSONObject {
        cache[name]?.let { return it }
        val entry = metadata(name)
        val document = GZIPInputStream(assets.open(DATA_PATH)).use { stream ->
            var remaining = entry.getLong("offset")
            while (remaining > 0) {
                val skipped = stream.skip(remaining)
                if (skipped <= 0) throw IllegalStateException("SDK catalogue ended before its index offset")
                remaining -= skipped
            }
            val bytes = ByteArray(entry.getInt("bytes"))
            var position = 0
            while (position < bytes.size) {
                val count = stream.read(bytes, position, bytes.size - position)
                if (count < 0) throw IllegalStateException("SDK catalogue entry is incomplete")
                position += count
            }
            JSONObject(String(bytes, Charsets.UTF_8))
        }
        cache[name] = document
        return document
    }

    fun members(name: String): List<JSONObject> {
        val found = linkedMapOf<String, JSONObject>()
        val pending = ArrayDeque<String>().apply { add(name) }
        val visited = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (!visited.add(current) || current !in classes) continue
            if (visited.size > MAXIMUM_INHERITANCE) throw IllegalStateException("SDK inheritance exceeds its limit")
            val doc = document(current)
            val members = doc.getJSONArray("members")
            for (position in 0 until members.length()) {
                val member = members.getJSONObject(position)
                if (!member.optBoolean("public")) continue
                if (current != name && member.getString("kind") == "constructor") continue
                val id = member.getString("id")
                if (id in found) continue
                found[id] = JSONObject(member.toString()).put("declaringClass", current)
            }
            val parents = doc.getJSONArray("parents")
            for (position in 0 until parents.length()) pending.addLast(parents.getString(position))
        }
        return found.values.sortedBy { it.getString("id") }
    }

    fun member(name: String, id: String): JSONObject = members(name).firstOrNull { it.getString("id") == id }
        ?: DikcizNativeCommands.failure("android_api_member_not_found", "Exact public SDK member was not found; discover the installed contract first")

    fun resolve(name: String): Class<*> {
        PRIMITIVES[name]?.let { return it }
        if (name.endsWith("[]")) return java.lang.reflect.Array.newInstance(resolve(name.removeSuffix("[]")), 0).javaClass
        val entry = metadata(name)
        return try {
            Class.forName(entry.getString("binaryName"), false, javaClass.classLoader)
        } catch (exception: ClassNotFoundException) {
            throw DikcizAutomationRequestException("android_api_unavailable", "Public SDK class is absent on this Android version").apply { initCause(exception) }
        } catch (exception: LinkageError) {
            throw DikcizAutomationRequestException("android_api_unavailable", "Public SDK class dependencies are unavailable").apply { initCause(exception) }
        }
    }

    fun permitted(name: String, member: JSONObject? = null): Boolean {
        if (name !in classes) return false
        val blocked = policy.getJSONArray("blockedClasses")
        if ((0 until blocked.length()).any { name == blocked.getString(it) }) return false
        if (BLOCKED_PREFIXES.any { name.startsWith(it) }) return false
        if (!name.startsWith("android.") && !SAFE_JAVA_PREFIXES.any { name.startsWith(it) } && name !in SAFE_JAVA_CLASSES) return false
        if (name == "android.os.Parcel" || name.startsWith("android.os.ParcelFileDescriptor")) return false
        if (member == null) return true
        val declaring = member.getString("declaringClass")
        if (declaring != name && !permitted(declaring)) return false
        val id = member.getString("id")
        if (declaring == "android.os.Debug" && !member.getBoolean("readOnly")) return false
        if (member.getString("name").contains("Serializable")) return false
        val excluded = policy.optJSONObject("blockedMembers")?.optJSONArray(declaring)
        if (excluded != null && (0 until excluded.length()).any { id == excluded.getString(it) }) return false
        if (declaring == "android.content.Context" || declaring == "android.content.ContextWrapper" || declaring == "android.app.Activity") {
            return member.getString("name") in SAFE_CONTEXT_MEMBERS
        }
        return true
    }

    fun summary(): JSONObject = JSONObject().put("sdkInt", index.getInt("sdkInt"))
        .put("classCount", index.getInt("classCount")).put("memberCount", index.getInt("memberCount"))
        .put("source", index.getJSONObject("source")).put("policySha256", index.getString("policySha256"))
        .put("defaultAccess", "write").put("catalogueIsPermissionGrant", false)

    companion object {
        private const val INDEX_PATH = "android-sdk/index.json"
        private const val DATA_PATH = "android-sdk/members.bin"
        private const val CACHE_CLASSES = 16
        private const val MAXIMUM_INHERITANCE = 256
        private val BLOCKED_PREFIXES = setOf(
            "android.system.", "android.os.storage.", "android.app.backup.", "android.content.res.Asset", "android.database.", "android.webkit.",
            "android.content.ContentResolver", "android.content.ContentProvider", "android.content.SharedPreferences",
            "android.app.Application", "android.app.Instrumentation", "android.app.LoadedApk",
        )
        private val SAFE_JAVA_PREFIXES = setOf("java.time.", "java.math.")
        private val SAFE_JAVA_CLASSES = setOf(
            "java.lang.Object", "java.lang.String", "java.lang.CharSequence", "java.lang.Math", "java.lang.StrictMath",
            "java.lang.Boolean", "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long",
            "java.lang.Float", "java.lang.Double", "java.lang.Character", "java.lang.Number", "java.lang.Enum",
            "java.lang.Runnable", "java.util.List", "java.util.Map", "java.util.Set", "java.util.Collection",
            "java.util.ArrayList", "java.util.HashMap", "java.util.HashSet", "java.util.UUID", "java.util.Locale",
            "java.util.Date", "java.util.Calendar", "java.util.concurrent.Executor", "java.util.function.Consumer",
        )
        private val SAFE_CONTEXT_MEMBERS = setOf(
            "getSystemService", "getPackageName", "getPackageManager", "getResources", "getApplicationContext",
            "getMainExecutor", "getApplicationInfo", "checkSelfPermission", "getSystemServiceName",
            "getString", "getText", "getColor", "getDrawable", "getMainLooper", "getContentResolver",
        )
        private val PRIMITIVES = mapOf(
            "void" to Void.TYPE, "boolean" to Boolean::class.javaPrimitiveType!!, "byte" to Byte::class.javaPrimitiveType!!,
            "short" to Short::class.javaPrimitiveType!!, "int" to Int::class.javaPrimitiveType!!,
            "long" to Long::class.javaPrimitiveType!!, "float" to Float::class.javaPrimitiveType!!,
            "double" to Double::class.javaPrimitiveType!!, "char" to Char::class.javaPrimitiveType!!,
        )
    }
}
