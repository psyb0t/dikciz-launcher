package org.fossify.home.dikciz

import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import org.json.JSONException
import org.json.JSONObject

internal data class DikcizControlPortsStatus(
    val websocketPort: Int,
    val mcpPort: Int,
    val custom: Boolean,
    val valid: Boolean,
    val state: String,
    val file: String,
)

internal class DikcizControlPortsException(
    val code: String,
    message: String,
) : IllegalArgumentException(message)

/**
 * Owns `control/ports.json`, the device-loopback ports of the WebSocket and MCP
 * control planes. Only the phone changes it, through Settings or by editing the
 * file; no control-plane command can move the listeners. A missing or invalid
 * file falls back to the default ports so a bad edit never locks the user out.
 */
internal class DikcizControlPorts(
    configurationDirectory: File,
    private val logger: DikcizLogger,
) {
    private val recordFile = File(
        configurationDirectory,
        "${DikcizRemoteAccessAuth.CONTROL_DIRECTORY_NAME}/$RECORD_FILE_NAME",
    )

    @Synchronized
    fun status(): DikcizControlPortsStatus {
        return when (val state = readState()) {
            RecordState.Missing -> DikcizControlPortsStatus(
                DEFAULT_WEBSOCKET_PORT,
                DEFAULT_MCP_PORT,
                custom = false,
                valid = true,
                state = STATE_DEFAULT,
                file = recordFile.absolutePath,
            )
            RecordState.Invalid -> DikcizControlPortsStatus(
                DEFAULT_WEBSOCKET_PORT,
                DEFAULT_MCP_PORT,
                custom = true,
                valid = false,
                state = STATE_INVALID,
                file = recordFile.absolutePath,
            )
            is RecordState.Valid -> DikcizControlPortsStatus(
                state.websocketPort,
                state.mcpPort,
                custom = true,
                valid = true,
                state = STATE_CUSTOM,
                file = recordFile.absolutePath,
            )
        }
    }

    fun websocketPort(): Int = status().websocketPort

    fun mcpPort(): Int = status().mcpPort

    @Synchronized
    fun setFromPhone(websocketPort: Int, mcpPort: Int) {
        if (!isValidPort(websocketPort) || !isValidPort(mcpPort)) {
            throw DikcizControlPortsException(ERROR_PORT_INVALID, MESSAGE_PORT_INVALID)
        }
        if (websocketPort == mcpPort) {
            throw DikcizControlPortsException(ERROR_PORTS_EQUAL, MESSAGE_PORTS_EQUAL)
        }
        writeRecord(websocketPort, mcpPort)
        logger.info(
            EVENT_CONTROL_PORTS_CHANGED,
            mapOf(FIELD_WEBSOCKET_PORT to websocketPort, FIELD_MCP_PORT to mcpPort),
        )
    }

    @Synchronized
    fun resetFromPhone() {
        if (!recordFile.exists()) {
            return
        }
        if (!recordFile.isFile || !recordFile.delete()) {
            throw DikcizControlPortsException(ERROR_RECORD_WRITE, MESSAGE_RESET_FAILED)
        }
        logger.info(
            EVENT_CONTROL_PORTS_CHANGED,
            mapOf(FIELD_WEBSOCKET_PORT to DEFAULT_WEBSOCKET_PORT, FIELD_MCP_PORT to DEFAULT_MCP_PORT),
        )
    }

    private fun isValidPort(port: Int): Boolean = port in MINIMUM_PORT..MAXIMUM_PORT

    private fun writeRecord(websocketPort: Int, mcpPort: Int) {
        val parent = recordFile.parentFile
            ?: throw DikcizControlPortsException(ERROR_RECORD_WRITE, MESSAGE_RECORD_WRITE_FAILED)
        if (!parent.exists() && !parent.mkdirs()) {
            throw DikcizControlPortsException(ERROR_RECORD_WRITE, MESSAGE_RECORD_WRITE_FAILED)
        }
        if (!parent.isDirectory) {
            throw DikcizControlPortsException(ERROR_RECORD_WRITE, MESSAGE_RECORD_WRITE_FAILED)
        }
        val serialized = JSONObject()
            .put(KEY_VERSION, RECORD_VERSION)
            .put(KEY_WEBSOCKET_PORT, websocketPort)
            .put(KEY_MCP_PORT, mcpPort)
            .toString(JSON_INDENTATION_SPACES)
        val atomicFile = AtomicFile(recordFile)
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(serialized.toByteArray(StandardCharsets.UTF_8))
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (exception: Exception) {
            output?.let(atomicFile::failWrite)
            throw DikcizControlPortsException(ERROR_RECORD_WRITE, MESSAGE_RECORD_WRITE_FAILED)
        }
    }

    private fun readState(): RecordState {
        if (!recordFile.exists()) {
            return RecordState.Missing
        }
        if (!recordFile.isFile || recordFile.length() !in MINIMUM_RECORD_BYTES..MAXIMUM_RECORD_BYTES) {
            return invalid(REASON_SIZE)
        }
        val document = try {
            JSONObject(recordFile.readText(StandardCharsets.UTF_8))
        } catch (_: JSONException) {
            return invalid(REASON_JSON)
        } catch (_: Exception) {
            return invalid(REASON_READ)
        }
        if (document.keys().asSequence().toSet() != RECORD_KEYS || document.opt(KEY_VERSION) != RECORD_VERSION) {
            return invalid(REASON_SHAPE)
        }
        val websocketPort = document.opt(KEY_WEBSOCKET_PORT) as? Int ?: return invalid(REASON_PORT)
        val mcpPort = document.opt(KEY_MCP_PORT) as? Int ?: return invalid(REASON_PORT)
        if (!isValidPort(websocketPort) || !isValidPort(mcpPort) || websocketPort == mcpPort) {
            return invalid(REASON_PORT)
        }
        return RecordState.Valid(websocketPort, mcpPort)
    }

    private fun invalid(reason: String): RecordState {
        logger.warn(EVENT_CONTROL_PORTS_INVALID, mapOf(FIELD_REASON to reason, FIELD_FILE to recordFile.absolutePath))
        return RecordState.Invalid
    }

    private sealed interface RecordState {
        data object Missing : RecordState

        data object Invalid : RecordState

        data class Valid(val websocketPort: Int, val mcpPort: Int) : RecordState
    }

    internal companion object {
        const val DEFAULT_MCP_PORT = 19_002
        const val DEFAULT_WEBSOCKET_PORT = 19_001
        const val ERROR_PORT_INVALID = "control_port_invalid"
        const val ERROR_PORTS_EQUAL = "control_ports_equal"
        const val ERROR_RECORD_WRITE = "control_ports_record_write_failed"
        const val EVENT_CONTROL_PORTS_CHANGED = "control_ports_changed"
        const val EVENT_CONTROL_PORTS_INVALID = "control_ports_invalid"
        const val FIELD_FILE = "file"
        const val FIELD_MCP_PORT = "mcp_port"
        const val FIELD_REASON = "reason"
        const val FIELD_WEBSOCKET_PORT = "websocket_port"
        const val JSON_INDENTATION_SPACES = 2
        const val KEY_MCP_PORT = "mcpPort"
        const val KEY_VERSION = "version"
        const val KEY_WEBSOCKET_PORT = "websocketPort"
        const val MAXIMUM_PORT = 65_535
        const val MAXIMUM_RECORD_BYTES = 256L
        const val MESSAGE_PORT_INVALID = "ports must be whole numbers from 1024 to 65535"
        const val MESSAGE_PORTS_EQUAL = "the WebSocket and MCP ports must differ"
        const val MESSAGE_RECORD_WRITE_FAILED = "could not save the control ports record"
        const val MESSAGE_RESET_FAILED = "could not reset the control ports record"
        const val MINIMUM_PORT = 1_024
        const val MINIMUM_RECORD_BYTES = 2L
        const val REASON_JSON = "json"
        const val REASON_PORT = "port"
        const val REASON_READ = "read"
        const val REASON_SHAPE = "shape"
        const val REASON_SIZE = "size"
        const val RECORD_FILE_NAME = "ports.json"
        const val RECORD_VERSION = 1
        const val STATE_CUSTOM = "custom"
        const val STATE_DEFAULT = "default"
        const val STATE_INVALID = "invalid"
        val RECORD_KEYS = setOf(KEY_VERSION, KEY_WEBSOCKET_PORT, KEY_MCP_PORT)
    }
}
