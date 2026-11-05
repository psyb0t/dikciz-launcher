package org.fossify.home.dikciz

import android.os.Debug
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import org.json.JSONObject
import org.luaj.vm2.LuaError
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativePromise
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

/** Synchronous event handlers in a fresh interpreter scope, without Java access or a WebView. */
internal class DikcizJavaScriptRuntime(
    private val decodeResult: (String, DikcizLimits) -> DikcizLuaAutomationResult.Actions,
) {
    fun executeEvent(
        script: DikcizLuaScript,
        event: DikcizAutomationEvent,
        limits: DikcizLimits,
        widgets: JSONObject,
        accessibility: JSONObject?,
        androidAccess: JSONObject,
        executeCommand: ((String) -> String)?,
    ): DikcizLuaAutomationResult {
        if (!script.enabled) return DikcizLuaAutomationResult.Disabled
        val factory = ExecutionFactory()
        val cx = factory.enterContext()
        try {
            val scope = cx.initSafeStandardObjects()
            FORBIDDEN_GLOBALS.forEach { ScriptableObject.deleteProperty(scope, it) }
            val json = ScriptableObject.getProperty(scope, "JSON") as Scriptable
            val parse = ScriptableObject.getProperty(json, "parse") as Function
            val stringify = ScriptableObject.getProperty(json, "stringify") as Function
            val freeze = cx.evaluateString(scope, FREEZE_SOURCE, "script-context", 1, null) as Function
            fun fromJson(value: String): Any? = parse.call(cx, scope, json, arrayOf(value))
            fun toJson(value: Any?): String {
                val result = stringify.call(cx, scope, json, arrayOf(value))
                if (result === Undefined.instance) return "null"
                val serialized = Context.toString(result)
                require(serialized.toByteArray(StandardCharsets.UTF_8).size <= MAXIMUM_RESULT_BYTES)
                return serialized
            }
            fun command(raw: Boolean) = object : BaseFunction() {
                init {
                    parentScope = scope
                    prototype = ScriptableObject.getFunctionPrototype(scope)
                }
                override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                    require(args.size == 1)
                    val serialized = if (args[0] is CharSequence) args[0].toString() else toJson(args[0])
                    require(serialized.toByteArray(StandardCharsets.UTF_8).size <= DikcizLocalCommands.MAXIMUM_REQUEST_BYTES)
                    val host = executeCommand ?: throw IllegalArgumentException("Command host unavailable")
                    val response = factory.budget.host { host(serialized) }
                    return if (raw) response else fromJson(response)
                }
            }
            factory.budget = Budget(DikcizScriptExecutionLimits.SETUP_CPU_NANOS)
            val contextJson = JSONObject().put("scriptId", script.id).put("state", script.state)
                .put("widgets", widgets).put("androidAccess", androidAccess)
            accessibility?.let { contextJson.put("accessibility", it) }
            val context = fromJson(contextJson.toString()) as ScriptableObject
            context.defineProperty("request", command(false), READ_ONLY_PROPERTY)
            context.defineProperty("requestJson", command(true), READ_ONLY_PROPERTY)
            freeze.call(cx, scope, scope, arrayOf(context))
            scope.defineProperty("context", context, READ_ONLY_PROPERTY)
            val time = Instant.ofEpochMilli(event.timestampMilliseconds).atZone(ZoneId.systemDefault())
            val eventJson = JSONObject().put("type", event.name).put("source", event.source)
                .put("timestampMilliseconds", event.timestampMilliseconds).put("coalescingKey", event.coalescingKey)
                .put("payload", event.payload).put("time", JSONObject().put("hour24", time.hour).put("minute", time.minute))
            val eventValue = fromJson(eventJson.toString())
            freeze.call(cx, scope, scope, arrayOf(eventValue))
            cx.evaluateString(scope, script.source, script.id + ".js", 1, null)
            factory.budget.check()
            val handler = ScriptableObject.getProperty(scope, HANDLER_NAME)
            if (handler === Scriptable.NOT_FOUND || handler === Undefined.instance) return DikcizLuaAutomationResult.NoHandler
            if (handler !is Function) return DikcizLuaAutomationResult.Failure(DikcizLuaFailure.InvalidResult)
            factory.budget = Budget(DikcizScriptExecutionLimits.HANDLER_CPU_NANOS)
            val result = handler.call(cx, scope, scope, arrayOf(eventValue))
            factory.budget.check()
            if (result is NativePromise) return DikcizLuaAutomationResult.Failure(DikcizLuaFailure.InvalidResult)
            return decodeResult(toJson(result), limits).also { factory.budget.check() }
        } catch (failure: LimitFailure) {
            return DikcizLuaAutomationResult.Failure(failure.reason)
        } catch (exception: RhinoException) {
            factory.budget.failure?.let { return DikcizLuaAutomationResult.Failure(it.reason) }
            return DikcizLuaAutomationResult.Failure(DikcizLuaFailure.InvalidJavaScript,
                "JavaScript error at line " + exception.lineNumber())
        } catch (exception: LuaError) {
            return DikcizLuaAutomationResult.Failure(DikcizLuaFailure.InvalidResult)
        } catch (exception: IllegalArgumentException) {
            return DikcizLuaAutomationResult.Failure(DikcizLuaFailure.InvalidResult)
        } catch (exception: RuntimeException) {
            return DikcizLuaAutomationResult.Failure(DikcizLuaFailure.RuntimeUnavailable,
                "JavaScript runtime failed: " + exception.javaClass.simpleName)
        } catch (exception: LinkageError) {
            return DikcizLuaAutomationResult.Failure(DikcizLuaFailure.RuntimeUnavailable,
                "JavaScript runtime unavailable: " + exception.javaClass.simpleName)
        } finally {
            Context.exit()
        }
    }

    private class ExecutionFactory : ContextFactory() {
        var budget = Budget(DikcizScriptExecutionLimits.SETUP_CPU_NANOS)

        override fun makeContext(): Context = super.makeContext().apply {
            optimizationLevel = INTERPRETER_MODE
            languageVersion = Context.VERSION_ES6
            instructionObserverThreshold = OBSERVER_INTERVAL
            maximumInterpreterStackDepth = MAXIMUM_STACK_DEPTH
            setClassShutter { false }
        }

        override fun observeInstructionCount(cx: Context, instructionCount: Int) {
            budget.instructions += instructionCount
            budget.check()
        }
    }

    private class LimitFailure(val reason: DikcizLuaFailure) : Error(reason.persistedValue)

    private class Budget(private val maximumCpuNanos: Long) {
        private val startedCpu = Debug.threadCpuTimeNanos()
        private val startedMemory = memoryUsed()
        private var hostCpu = 0L
        private var hostMemory = 0L
        private var responseBytes = 0L
        var instructions = 0
        var failure: LimitFailure? = null
            private set

        fun check() {
            val reason = when {
                Thread.currentThread().isInterrupted -> DikcizLuaFailure.Cancelled
                instructions >= DikcizScriptExecutionLimits.INSTRUCTIONS -> DikcizLuaFailure.InstructionBudgetExceeded
                Debug.threadCpuTimeNanos() - startedCpu - hostCpu >= maximumCpuNanos -> DikcizLuaFailure.ExecutionDeadlineExceeded
                memoryUsed() - startedMemory - hostMemory + responseBytes >= DikcizScriptExecutionLimits.MEMORY_BYTES -> DikcizLuaFailure.MemoryBudgetExceeded
                else -> return
            }
            throw LimitFailure(reason).also { failure = it }
        }

        fun host(action: () -> String): String {
            check()
            val beforeCpu = Debug.threadCpuTimeNanos()
            val beforeMemory = memoryUsed()
            try {
                return action().also { response ->
                    responseBytes += response.toByteArray(StandardCharsets.UTF_8).size
                    if (responseBytes >= DikcizScriptExecutionLimits.MEMORY_BYTES) {
                        throw LimitFailure(DikcizLuaFailure.MemoryBudgetExceeded).also { failure = it }
                    }
                }
            } finally {
                hostCpu += (Debug.threadCpuTimeNanos() - beforeCpu).coerceAtLeast(0L)
                hostMemory += (memoryUsed() - beforeMemory).coerceAtLeast(0L)
            }
        }

        private fun memoryUsed(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
    }

    companion object {
        private const val HANDLER_NAME = "onEvent"
        private const val INTERPRETER_MODE = -1
        private const val OBSERVER_INTERVAL = 1000
        private const val MAXIMUM_STACK_DEPTH = 128
        private const val MAXIMUM_RESULT_BYTES = 1_048_576
        private const val READ_ONLY_PROPERTY = ScriptableObject.READONLY or ScriptableObject.PERMANENT
        private val FORBIDDEN_GLOBALS = listOf("Packages", "java", "javax", "org", "com", "edu", "net",
            "JavaAdapter", "JavaImporter", "importClass", "importPackage", "getClass")
        private const val FREEZE_SOURCE = """
            (function freeze(value) {
                if (value && typeof value === "object" && !Object.isFrozen(value)) {
                    Object.keys(value).forEach(function(key) { freeze(value[key]); });
                    Object.freeze(value);
                }
                return value;
            })
        """
    }
}

internal object DikcizScriptExecutionLimits {
    const val SETUP_CPU_NANOS = 250_000_000L
    const val HANDLER_CPU_NANOS = 50_000_000L
    const val MEMORY_BYTES = 2L * 1024L * 1024L
    const val INSTRUCTIONS = 50_000
}
