package org.fossify.home.dikciz

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONException
import org.json.JSONObject

/** Owner bindings use the same executor while the launcher activity is serving commands. */
internal object DikcizLocalCommands {
    @Volatile
    private var endpoint: Endpoint? = null

    fun isAvailable(): Boolean = endpoint != null

    @Synchronized
    fun attach(
        ownerID: String,
        execute: (JSONObject, String) -> String,
        delivered: (String, JSONObject) -> Unit,
    ) {
        endpoint = Endpoint(ownerID, execute, delivered)
    }

    @Synchronized
    fun detach(ownerID: String) {
        if (endpoint?.ownerID == ownerID) {
            endpoint = null
        }
    }

    fun executeJson(sourceOwner: String, serialized: String): Reply {
        if (serialized.toByteArray(StandardCharsets.UTF_8).size > MAXIMUM_REQUEST_BYTES) {
            return failure(null, DikcizAutomationControlPlane.ERROR_VALIDATION_FAILED, MESSAGE_TOO_LARGE)
        }
        val request = try {
            JSONObject(serialized)
        } catch (_: JSONException) {
            return failure(null, DikcizAutomationControlPlane.ERROR_VALIDATION_FAILED, MESSAGE_INVALID_JSON)
        }
        return execute(sourceOwner, request)
    }

    fun execute(sourceOwner: String, request: JSONObject): Reply {
        val command = JSONObject(request.toString())
        if (!command.has(DikcizAutomationControlPlane.KEY_REQUEST_ID)) {
            command.put(DikcizAutomationControlPlane.KEY_REQUEST_ID, UUID.randomUUID().toString())
        }
        if (command.toString().toByteArray(StandardCharsets.UTF_8).size > MAXIMUM_REQUEST_BYTES) {
            return failure(
                command.opt(DikcizAutomationControlPlane.KEY_REQUEST_ID),
                DikcizAutomationControlPlane.ERROR_VALIDATION_FAILED,
                MESSAGE_TOO_LARGE,
            )
        }
        val serving = endpoint ?: return failure(
            command.opt(DikcizAutomationControlPlane.KEY_REQUEST_ID),
            ERROR_UNAVAILABLE,
            MESSAGE_UNAVAILABLE,
        )
        val response = JSONObject(serving.execute(command, sourceOwner))
        val commandType = command.optString(DikcizAutomationControlPlane.KEY_TYPE)
        return Reply(response, commandType) {
            if (endpoint === serving && response.optString(DikcizAutomationControlPlane.KEY_TYPE) ==
                DikcizAutomationControlPlane.TYPE_RESULT
            ) {
                serving.delivered(commandType, response.getJSONObject(DikcizAutomationControlPlane.KEY_RESULT))
            }
        }
    }

    fun failure(
        requestID: Any?,
        code: String,
        message: String,
    ): Reply {
        val response = JSONObject()
            .put(DikcizAutomationControlPlane.KEY_TYPE, DikcizAutomationControlPlane.TYPE_ERROR)
            .put(DikcizAutomationControlPlane.KEY_REQUEST_ID, requestID ?: JSONObject.NULL)
            .put(DikcizAutomationControlPlane.KEY_CODE, code)
            .put(DikcizAutomationControlPlane.KEY_MESSAGE, message)
        return Reply(response, EMPTY_COMMAND_TYPE) {}
    }

    internal class Reply(
        val response: JSONObject,
        commandType: String,
        private val afterDelivery: () -> Unit,
    ) {
        private val hasBeenDelivered = AtomicBoolean()
        val requiresAcknowledgement = commandType in DikcizDeviceInspection.RESPONSE_COMMAND_TYPES &&
            response.optString(DikcizAutomationControlPlane.KEY_TYPE) == DikcizAutomationControlPlane.TYPE_RESULT

        fun delivered() {
            if (hasBeenDelivered.compareAndSet(false, true)) {
                afterDelivery()
            }
        }
    }

    private data class Endpoint(
        val ownerID: String,
        val execute: (JSONObject, String) -> String,
        val delivered: (String, JSONObject) -> Unit,
    )

    private const val EMPTY_COMMAND_TYPE = ""
    const val MAXIMUM_REQUEST_BYTES = HomeConfigStore.MAXIMUM_CONFIGURATION_DOCUMENT_BYTES
    private const val ERROR_UNAVAILABLE = "control_unavailable"
    private const val MESSAGE_UNAVAILABLE = "Open Dikciz before sending owner commands."
    private const val MESSAGE_INVALID_JSON = "Command must be a JSON object"
    private const val MESSAGE_TOO_LARGE = "Command exceeds the control request size limit"
}
