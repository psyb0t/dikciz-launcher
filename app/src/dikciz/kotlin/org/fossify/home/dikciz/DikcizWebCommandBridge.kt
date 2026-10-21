package org.fossify.home.dikciz

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.nio.charset.StandardCharsets
import org.json.JSONException
import org.json.JSONObject

internal class DikcizWebCommandBridge(
    private val widgetID: String,
    private val execute: (JSONObject) -> DikcizLocalCommands.Reply,
    private val logger: DikcizLogger,
    private val reply: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val awaitingDelivery = ConcurrentHashMap<String, DikcizLocalCommands.Reply>()
    private val worker = ThreadPoolExecutor(
        WORKER_COUNT,
        WORKER_COUNT,
        IDLE_SECONDS,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(QUEUE_CAPACITY),
        { runnable -> Thread(runnable, THREAD_NAME) },
    )

    @Volatile
    private var closed = false

    fun receive(payload: String) {
        if (closed) return
        val request = try {
            if (payload.toByteArray(StandardCharsets.UTF_8).size > DikcizLocalCommands.MAXIMUM_REQUEST_BYTES) {
                reject(null, ERROR_VALIDATION, MESSAGE_TOO_LARGE)
                return
            }
            JSONObject(payload)
        } catch (exception: JSONException) {
            reject(null, ERROR_VALIDATION, MESSAGE_INVALID_JSON)
            return
        }
        if (request.optString(DikcizAutomationControlPlane.KEY_TYPE) == TYPE_RESPONSE_RECEIVED) {
            val requestID = request.opt(DikcizAutomationControlPlane.KEY_REQUEST_ID) as? String
            if (request.length() == ACKNOWLEDGEMENT_FIELD_COUNT && requestID != null) {
                awaitingDelivery.remove(requestID)?.delivered()
            }
            return
        }
        try {
            worker.execute {
                if (closed) return@execute
                val result = execute(request)
                mainHandler.post {
                    if (closed) return@post
                    val requestID = result.response.optString(DikcizAutomationControlPlane.KEY_REQUEST_ID)
                    if (result.requiresAcknowledgement) {
                        if (awaitingDelivery.size >= QUEUE_CAPACITY) {
                            reject(requestID, ERROR_BUSY, MESSAGE_BUSY)
                            return@post
                        }
                        awaitingDelivery[requestID] = result
                    }
                    reply(result.response.toString())
                    if (!result.requiresAcknowledgement) {
                        result.delivered()
                    }
                }
            }
        } catch (exception: RejectedExecutionException) {
            reject(request.optString(DikcizAutomationControlPlane.KEY_REQUEST_ID), ERROR_BUSY, MESSAGE_BUSY)
        }
    }

    fun close() {
        closed = true
        worker.queue.clear()
        awaitingDelivery.clear()
        // Already dispatched commands retain their normal completion and audit trail.
        worker.shutdown()
    }

    private fun reject(requestID: String?, code: String, message: String) {
        logger.warn(
            EVENT_REJECTED,
            mapOf(
                FIELD_CODE to code,
                FIELD_REASON to code,
                FIELD_SCRIPT_ID to widgetID,
                FIELD_SCRIPT_KIND to SCRIPT_KIND_HTML,
                FIELD_WIDGET_ID to widgetID,
            ),
        )
        reply(
            JSONObject()
                .put(DikcizAutomationControlPlane.KEY_TYPE, DikcizAutomationControlPlane.TYPE_ERROR)
                .put(DikcizAutomationControlPlane.KEY_REQUEST_ID, requestID ?: JSONObject.NULL)
                .put(DikcizAutomationControlPlane.KEY_CODE, code)
                .put(DikcizAutomationControlPlane.KEY_MESSAGE, message)
                .toString(),
        )
    }

    private companion object {
        const val WORKER_COUNT = 1
        const val TYPE_RESPONSE_RECEIVED = "commandResponseReceived"
        const val ACKNOWLEDGEMENT_FIELD_COUNT = 2
        const val QUEUE_CAPACITY = 32
        const val IDLE_SECONDS = 0L
        const val THREAD_NAME = "dikciz-web-commands"
        const val ERROR_VALIDATION = "validation_failed"
        const val ERROR_BUSY = "busy"
        const val MESSAGE_TOO_LARGE = "HTML command exceeds the control request size limit"
        const val MESSAGE_INVALID_JSON = "HTML command must be a JSON object"
        const val MESSAGE_BUSY = "HTML command queue is full"
        const val EVENT_REJECTED = "web_command_rejected"
        const val FIELD_CODE = "code"
        const val FIELD_REASON = "reason"
        const val FIELD_SCRIPT_ID = "script_id"
        const val FIELD_SCRIPT_KIND = "script_kind"
        const val FIELD_WIDGET_ID = "widget_id"
        const val SCRIPT_KIND_HTML = "html"
    }
}
