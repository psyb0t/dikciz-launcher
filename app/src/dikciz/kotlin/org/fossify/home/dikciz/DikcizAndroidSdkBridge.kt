package org.fossify.home.dikciz

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.android.dx.stock.ProxyBuilder
import java.lang.reflect.InvocationHandler
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class DikcizAndroidSdkBridge private constructor(
    private val context: Application,
    private val logger: DikcizLogger,
) {
    private val catalogue by lazy { DikcizAndroidSdkCatalogue(context) }
    private val values by lazy { DikcizAndroidSdkValues(catalogue) }
    private val access = DikcizAndroidSdkAccess(context)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var activity = java.lang.ref.WeakReference<Activity>(null)
    private data class Invocation(val digest: String, val task: FutureTask<JSONObject>)
    private val invocations = linkedMapOf<String, Invocation>()
    private val invocationLock = Any()

    fun execute(command: DikcizAutomationCommand): JSONObject {
        if (command.readOnly && command.type !in READ_TYPES) readOnlyFailure()
        return when (command.type) {
            DikcizAndroidSdkCommands.DISCOVER -> discover(command.request, command.readOnly)
            DikcizAndroidSdkCommands.READ -> call(command.request, true)
            DikcizAndroidSdkCommands.INVOKE -> call(command.request, false)
            DikcizAndroidSdkCommands.OBJECTS -> objects(command.request)
            DikcizAndroidSdkCommands.RELEASE -> release(command.request)
            DikcizAndroidSdkCommands.CALLBACK -> callback(command.request)
            else -> DikcizNativeCommands.invalid()
        }
    }

    private fun discover(request: JSONObject, readOnly: Boolean): JSONObject {
        val query = request.optString("query").lowercase()
        if (!request.has("className")) {
            val classes = catalogue.names().filter { name ->
                name.lowercase().contains(query) && (!readOnly ||
                    (catalogue.permitted(name) && catalogue.metadata(name).getInt("readOnlyCount") > 0))
            }
            return DikcizNativeCommands.page(classes, request) { name ->
                JSONObject(catalogue.metadata(name).toString()).apply { remove("offset"); remove("bytes") }
                    .put("bridgePermitted", catalogue.permitted(name)).put("runtimeAvailable", available(name))
            }.put("catalogue", catalogue.summary()).put("readOnly", readOnly)
        }
        val name = request.getString("className")
        val members = catalogue.members(name).filter { member ->
            member.getString("id").lowercase().contains(query) &&
                (!readOnly || (member.getBoolean("readOnly") && catalogue.permitted(name, member)))
        }
        return DikcizNativeCommands.page(members, request) { member ->
            member.put("bridgePermitted", catalogue.permitted(name, member))
                .put("runtimeAvailable", memberAvailable(name, member))
        }.put("className", name).put("runtimeAvailable", available(name)).put("readOnly", readOnly)
            .put("catalogue", catalogue.summary())
    }

    private fun available(name: String): Boolean = try {
        catalogue.resolve(name)
        true
    } catch (exception: DikcizAutomationRequestException) {
        if (exception.code != "android_api_unavailable") throw exception
        false
    }

    private fun memberAvailable(name: String, member: JSONObject): Boolean = try {
        val type = catalogue.resolve(name)
        when (member.getString("kind")) {
            "constructor" -> type.getConstructor(*parameterTypes(member))
            "field" -> type.getField(member.getString("name"))
            else -> type.getMethod(member.getString("name"), *parameterTypes(member))
        }
        true
    } catch (exception: NoSuchMethodException) {
        false
    } catch (exception: NoSuchFieldException) {
        false
    } catch (exception: DikcizAutomationRequestException) {
        if (exception.code !in setOf("android_api_unavailable", "android_api_class_not_found")) throw exception
        false
    } catch (exception: LinkageError) {
        false
    }

    private fun call(request: JSONObject, readOnly: Boolean): JSONObject {
        val name = request.getString("className")
        val id = request.getString("member")
        // Authorize before resolving targets, handles, permissions or initializing Android classes.
        if (readOnly && id.startsWith(SET_PREFIX)) readOnlyFailure()
        val member = catalogue.member(name, if (id.startsWith(SET_PREFIX)) "field:" + id.removePrefix(SET_PREFIX) else id)
        if (readOnly && !member.getBoolean("readOnly")) readOnlyFailure()
        if (!catalogue.permitted(name, member)) {
            DikcizNativeCommands.failure("android_api_bridge_restricted", "Use the dedicated protected adapter for raw storage, reflection or private launcher internals")
        }
        val task: FutureTask<JSONObject>
        val replay: Boolean
        if (readOnly) {
            task = FutureTask { invoke(request, name, member) }
            replay = false
            submit(task, request, member)
        } else {
            val key = request.getString("idempotencyKey")
            val digest = digest(request)
            synchronized(invocationLock) {
                val existing = invocations[key]
                if (existing != null) {
                    if (existing.digest != digest) DikcizNativeCommands.failure("idempotency_conflict", "SDK idempotencyKey was used with different arguments")
                    task = existing.task
                    replay = true
                } else {
                    if (invocations.size >= MAXIMUM_INVOCATIONS) DikcizNativeCommands.failure("android_api_invocation_limit", "Process invocation history is full")
                    task = FutureTask { invoke(request, name, member) }
                    replay = false
                    submit(task, request, member)
                    invocations[key] = Invocation(digest, task)
                }
            }
        }
        return try {
            JSONObject(task.get(request.optLong("timeoutMilliseconds", DEFAULT_TIMEOUT), TimeUnit.MILLISECONDS).toString()).put("replayed", replay)
        } catch (exception: TimeoutException) {
            // Retain running writes so an identical retry observes the first call instead of repeating it.
            if (readOnly) task.cancel(false)
            throw DikcizAutomationRequestException("android_api_timeout", "SDK wait timeout; completion is unknown. Retry the identical write key to observe it without repeating the operation.").apply { initCause(exception) }
        } catch (exception: ExecutionException) {
            val cause = exception.cause
            if (cause is DikcizAutomationRequestException) {
                if (!readOnly && (cause.code in PRE_EXECUTION_ERRORS || cause.code.startsWith("native_permission_"))) {
                    synchronized(invocationLock) {
                        val key = request.getString("idempotencyKey")
                        if (invocations[key]?.task === task) invocations.remove(key)
                    }
                }
                throw cause
            }
            throw DikcizAutomationRequestException("android_api_failed", "SDK invocation failed; no success is confirmed").apply { initCause(cause ?: exception) }
        }
    }

    private fun submit(task: FutureTask<JSONObject>, request: JSONObject, member: JSONObject) {
        val onMain = when (request.optString("thread", "auto")) {
            "main" -> true
            "background" -> false
            else -> member.getBoolean("mainThread")
        }
        if (onMain) {
            if (Looper.myLooper() == Looper.getMainLooper()) task.run() else if (!main.post(task)) {
                DikcizNativeCommands.failure("android_api_busy", "Android main thread is unavailable")
            }
            return
        }
        try {
            EXECUTOR.execute(task)
        } catch (exception: RejectedExecutionException) {
            throw DikcizAutomationRequestException("android_api_busy", "SDK workers are busy; no new invocation was submitted").apply { initCause(exception) }
        }
    }

    private fun invoke(request: JSONObject, name: String, member: JSONObject): JSONObject {
        val type = catalogue.resolve(name)
        val setting = request.getString("member").startsWith(SET_PREFIX)
        val kind = member.getString("kind")
        val types = if (kind == "field") {
            if (setting) arrayOf(catalogue.resolve(member.getString("type"))) else emptyArray()
        } else parameterTypes(member)
        val arguments = request.getJSONArray("arguments")
        if (arguments.length() != types.size) DikcizNativeCommands.invalid("SDK arguments must match the exact positional parameter count")
        if (setting && member.getBoolean("final")) DikcizNativeCommands.invalid("A final SDK field cannot be set")
        val parameters = member.optJSONArray("parameters")
        val decoded = Array<Any?>(types.size) { index ->
            values.decode(arguments.get(index), types[index], parameters?.getJSONObject(index)?.opt("nullable") != false)
        }
        decoded.forEach { access.validate(it, member.getBoolean("readOnly") && !setting) }
        val target = if (kind == "constructor" || member.getBoolean("static")) {
            if (request.has("target")) DikcizNativeCommands.invalid("Static SDK members and constructors do not accept a target")
            null
        } else target(request.optString("target"), type)
        requirePermissions(member)
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            val result = when (kind) {
                "constructor" -> type.getConstructor(*types).newInstance(*decoded)
                "field" -> type.getField(member.getString("name")).let { field ->
                    if (setting) {
                        field.set(target, decoded[0])
                        null
                    } else field.get(target)
                }
                else -> type.getMethod(member.getString("name"), *types).invoke(target, *decoded)
            }
            logger.debug("android_sdk_invocation_completed", mapOf("class_name" to name, "member_id" to member.getString("id")))
            return JSONObject().put("state", "completed").put("className", name).put("member", request.getString("member"))
                .put("result", values.encode(result, if (kind == "field") member.getString("type") else member.getString("returns")))
                .put("durationMilliseconds", android.os.SystemClock.elapsedRealtime() - started)
        } catch (exception: InvocationTargetException) {
            val cause = exception.targetException
            val code = when (cause) {
                is SecurityException -> "android_api_access_denied"
                is UnsupportedOperationException -> "android_api_unsupported"
                is IllegalArgumentException -> "android_api_argument_rejected"
                else -> "android_api_failed"
            }
            logger.warn("android_sdk_invocation_rejected", mapOf("class_name" to name, "member_id" to member.getString("id"), "error_class" to cause.javaClass.simpleName))
            throw DikcizAutomationRequestException(code, "Android rejected the SDK invocation; inspect permissions, roles, hardware and exact arguments. No success is confirmed.").apply { initCause(cause) }
        } catch (exception: ReflectiveOperationException) {
            throw DikcizAutomationRequestException("android_api_unavailable", "The exact SDK overload is unavailable or inaccessible on this Android version").apply { initCause(exception) }
        }
    }

    private fun target(name: String, expected: Class<*>): Any {
        val value = when {
            name == "context" -> context.applicationContext
            name == "activity" -> activity.get()?.takeUnless { it.isDestroyed || it.isFinishing } ?: DikcizNativeCommands.failure("android_api_activity_unavailable", "A foreground launcher activity is required")
            name.startsWith("service:") -> context.getSystemService(name.removePrefix("service:"))
                ?: DikcizNativeCommands.failure("android_api_service_unavailable", "Android system service is absent on this device")
            name.isNotBlank() -> values.get(name).value
            else -> DikcizNativeCommands.invalid("An instance SDK member requires a target")
        }
        if (!expected.isInstance(value)) DikcizNativeCommands.invalid("SDK target is not assignable to className")
        return value
    }

    private fun parameterTypes(member: JSONObject): Array<Class<*>> {
        val parameters = member.getJSONArray("parameters")
        return Array(parameters.length()) { catalogue.resolve(parameters.getJSONObject(it).getString("type")) }
    }

    private fun requirePermissions(member: JSONObject) {
        val groups = member.getJSONArray("permissions")
        for (index in 0 until groups.length()) {
            val group = groups.getJSONObject(index)
            if (group.getBoolean("conditional") || group.optJSONArray("unresolvedConstants")?.length() != 0 ||
                group.optJSONArray("unresolvedRequirements")?.length() != 0 || group.has("apis")) continue
            val permissions = group.getJSONArray("permissions").let { array -> (0 until array.length()).map(array::getString) }
            val missing = permissions.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isEmpty() || (group.getString("mode") == "any" && missing.size < permissions.size)) continue
            val requirement = DikcizDeviceAccessRequirement.entries.firstOrNull { candidate ->
                missing.first() in DikcizDeviceAccess.runtimePermissions(candidate)
            }
            val recovery = JSONObject().put("state", "permission_required").put("permissions", JSONArray(missing))
                .put("autoRequested", false).put("operationExecuted", false)
                .put("instructions", "Review declared Android permissions and device roles. Signature permissions cannot be granted by a runtime prompt.")
            throw DikcizAutomationRequestException(
                if (requirement == null) "android_api_permission_required" else "native_permission_" + requirement.persistedValue,
                "Android SDK permission is required before this operation can run", recovery,
            )
        }
    }

    private fun objects(request: JSONObject): JSONObject {
        if (!request.has("objectRef")) return DikcizNativeCommands.page(values.list(), request)
        val handle = values.get(request.getString("objectRef"))
        val callback = handle.callback ?: return values.read(request.getString("objectRef"), request)
        synchronized(callback) {
            val after = request.optLong("afterSequence", 0)
            if (after > callback.sequence) DikcizNativeCommands.invalid("Callback cursor is ahead of its sequence")
            return DikcizNativeCommands.page(callback.events.filter { it.getLong("sequence") > after }, request)
                .put("lastSequence", callback.sequence).put("droppedEvents", callback.dropped)
                .put("gap", callback.events.firstOrNull()?.getLong("sequence")?.let { after < it - 1 } ?: false)
        }
    }

    private fun release(request: JSONObject): JSONObject {
        val id = request.getString("objectRef")
        val handle = values.get(id)
        if (request.optBoolean("close")) {
            val resource = handle.value as? AutoCloseable ?: DikcizNativeCommands.invalid("SDK object is not AutoCloseable")
            resource.close()
        }
        handle.callback?.let { synchronized(it) { it.active = false } }
        values.remove(id)
        return JSONObject().put("state", "released").put("objectRef", id)
    }

    private fun callback(request: JSONObject): JSONObject {
        val name = request.getString("className")
        if (!catalogue.permitted(name)) DikcizNativeCommands.invalid("SDK callback class is restricted")
        val type = catalogue.resolve(name)
        if (Modifier.isFinal(type.modifiers)) DikcizNativeCommands.invalid("A final SDK class cannot be a callback")
        val returns = request.optJSONObject("returns") ?: JSONObject()
        val methods = type.methods.filter { it.declaringClass != Any::class.java && !Modifier.isStatic(it.modifiers) && !Modifier.isFinal(it.modifiers) }
        val keys = methods.map(::methodId).toSet()
        if (returns.keys().asSequence().any { it !in keys }) DikcizNativeCommands.invalid("Unknown callback return overload")
        val decoded = methods.associate { method ->
            val id = methodId(method)
            if (method.returnType != Void.TYPE && !returns.has(id)) DikcizNativeCommands.invalid("Every non-void callback needs an explicit constant return value")
            id to if (method.returnType == Void.TYPE) null else values.decode(returns.get(id), method.returnType).also { access.validate(it, false) }
        }
        val state = DikcizAndroidSdkValues.Callback()
        val callbackId = java.util.concurrent.atomic.AtomicReference<String>()
        val handler = InvocationHandler { instance, method, args ->
            when {
                method.declaringClass == Any::class.java && method.name == "hashCode" -> System.identityHashCode(instance)
                method.declaringClass == Any::class.java && method.name == "equals" -> instance === args?.get(0)
                method.declaringClass == Any::class.java && method.name == "toString" -> "Dikciz SDK callback"
                else -> {
                    synchronized(state) {
                        if (state.active) {
                            val event = JSONObject().put("sequence", ++state.sequence).put("member", methodId(method))
                            try {
                                event.put("arguments", JSONArray(args.orEmpty().map { values.encode(it) }))
                            } catch (exception: DikcizAutomationRequestException) {
                                logger.warn("android_sdk_callback_arguments_omitted", mapOf("error_code" to exception.code))
                                event.put("argumentsOmitted", true).put("errorCode", exception.code)
                            }
                            callbackId.get()?.let { id ->
                                DikcizAutomationEventBus.enqueue(context, DikcizAutomationEvent(
                                    type = DikcizAutomationEventType.Custom,
                                    source = "android_sdk", timestampMilliseconds = System.currentTimeMillis(),
                                    coalescingKey = id, customEventName = "android_api_callback",
                                    payload = JSONObject(event.toString()).put("objectRef", id),
                                ))
                            }
                            if (state.events.size >= DikcizAndroidSdkValues.MAXIMUM_EVENTS) {
                                state.events.removeFirst()
                                state.dropped++
                            }
                            state.events.addLast(event)
                        }
                    }
                    decoded[methodId(method)]
                }
            }
        }
        val proxy = if (type.isInterface) {
            if (request.has("constructor") || request.optJSONArray("arguments")?.length()?.let { it != 0 } == true) {
                DikcizNativeCommands.invalid("Interface callbacks do not have constructor arguments")
            }
            Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler)
        } else {
            val constructorId = request.optString("constructor", "new()")
            val constructor = catalogue.document(name).getJSONArray("members").let { entries ->
                (0 until entries.length()).map(entries::getJSONObject).firstOrNull {
                    it.getString("kind") == "constructor" && it.getString("id") == constructorId
                }
            } ?: DikcizNativeCommands.invalid("Callback needs an exact public or protected SDK constructor")
            val types = parameterTypes(constructor)
            val supplied = request.optJSONArray("arguments") ?: JSONArray()
            if (supplied.length() != types.size) DikcizNativeCommands.invalid("Callback constructor argument count does not match")
            val arguments = Array<Any?>(types.size) { values.decode(supplied.get(it), types[it]) }
            arguments.forEach { access.validate(it, false) }
            requirePermissions(constructor)
            ProxyBuilder.forClass(type).dexCache(context.applicationContext.getDir("sdk-callbacks", Context.MODE_PRIVATE))
                .onlyMethods(methods.toTypedArray()).constructorArgTypes(*types).constructorArgValues(*arguments)
                .handler(handler).build()
        }
        return values.add(proxy, name, state).also { callbackId.set(it.getString("objectRef")) }.put("state", "created")
    }

    private fun methodId(method: Method): String = method.name + "(" + method.parameterTypes.joinToString(",") { canonical(it) } + ")"
    private fun canonical(type: Class<*>): String = if (type.isArray) canonical(type.componentType!!) + "[]" else type.name.replace('$', '.')
    private fun digest(request: JSONObject): String {
        val cleaned = JSONObject(request.toString()).apply { remove("type"); remove("requestId"); remove("timeoutMilliseconds") }
        val bytes = canonicalJson(cleaned).toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
    private fun canonicalJson(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonicalJson(value.get(it)) }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonicalJson(value.get(it)) }
        else -> JSONArray().put(value).toString().removePrefix("[").removeSuffix("]")
    }
    private fun readOnlyFailure(): Nothing = DikcizNativeCommands.failure(
        DikcizMcpControlPlane.ERROR_READ_ONLY_VIOLATION,
        "Read-only mode blocks this SDK operation. Use an explicitly approved read member; only the owner can enable write access.",
    )

    companion object {
        private var instance: DikcizAndroidSdkBridge? = null
        @Synchronized
        fun get(context: Context, logger: DikcizLogger): DikcizAndroidSdkBridge {
            val bridge = instance ?: DikcizAndroidSdkBridge(context.applicationContext as Application, logger).also { instance = it }
            if (context is Activity) bridge.activity = java.lang.ref.WeakReference(context)
            return bridge
        }
        private const val DEFAULT_TIMEOUT = 30_000L
        private const val MAXIMUM_INVOCATIONS = 1024
        private const val SET_PREFIX = "set:"
        private val PRE_EXECUTION_ERRORS = setOf(
            "validation_failed", "android_api_object_not_found", "file_access_denied", "android_api_protected_path", "android_api_service_unavailable",
            "android_api_activity_unavailable", "android_api_permission_required", "android_api_class_not_found",
            "android_api_member_not_found", "android_api_unavailable",
        )
        private val READ_TYPES = setOf(DikcizAndroidSdkCommands.DISCOVER, DikcizAndroidSdkCommands.READ, DikcizAndroidSdkCommands.OBJECTS)
        private val EXECUTOR = ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(16),
            { runnable -> Thread(runnable, "dikciz-android-sdk") },
        )
    }
}
