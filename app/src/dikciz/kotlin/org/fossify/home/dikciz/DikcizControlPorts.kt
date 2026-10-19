package org.fossify.home.dikciz

import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import org.json.JSONException
import org.json.JSONObject

internal data class DikcizControlPortsStatus(
    val listenAddress: String,
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
 * Owns `control/ports.json`, the listen address and ports of the WebSocket and MCP
 * control planes. Settings and owner file edits configure the listeners. A
 * missing or invalid file falls back to loopback and the default ports.
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
                DEFAULT_LISTEN_ADDRESS,
                DEFAULT_WEBSOCKET_PORT,
                DEFAULT_MCP_PORT,
                custom = false,
                valid = true,
                state = STATE_DEFAULT,
                file = recordFile.absolutePath,
            )
            RecordState.Invalid -> DikcizControlPortsStatus(
                DEFAULT_LISTEN_ADDRESS,
                DEFAULT_WEBSOCKET_PORT,
                DEFAULT_MCP_PORT,
                custom = true,
                valid = false,
                state = STATE_INVALID,
                file = recordFile.absolutePath,
            )
            is RecordState.Valid -> DikcizControlPortsStatus(
                state.listenAddress,
                state.websocketPort,
                state.mcpPort,
                custom = true,
                valid = true,
                state = STATE_CUSTOM,
                file = recordFile.absolutePath,
            )
        }
    }

    fun websocketEndpoint(status: DikcizControlPortsStatus): InetSocketAddress {
        return InetSocketAddress(checkNotNull(parseListenAddress(status.listenAddress)), status.websocketPort)
    }

    fun mcpEndpoint(status: DikcizControlPortsStatus): InetSocketAddress {
        return InetSocketAddress(checkNotNull(parseListenAddress(status.listenAddress)), status.mcpPort)
    }

    @Synchronized
    fun setFromPhone(
        websocketPort: Int,
        mcpPort: Int,
        listenAddress: String = DEFAULT_LISTEN_ADDRESS,
    ) {
        if (!isValidListenAddress(listenAddress)) {
            throw DikcizControlPortsException(ERROR_ADDRESS_INVALID, MESSAGE_ADDRESS_INVALID)
        }
        if (!isValidPort(websocketPort) || !isValidPort(mcpPort)) {
            throw DikcizControlPortsException(ERROR_PORT_INVALID, MESSAGE_PORT_INVALID)
        }
        if (websocketPort == mcpPort) {
            throw DikcizControlPortsException(ERROR_PORTS_EQUAL, MESSAGE_PORTS_EQUAL)
        }
        writeRecord(websocketPort, mcpPort, listenAddress)
        logger.info(
            EVENT_CONTROL_PORTS_CHANGED,
            mapOf(
                FIELD_LISTEN_ADDRESS to listenAddress,
                FIELD_WEBSOCKET_PORT to websocketPort,
                FIELD_MCP_PORT to mcpPort,
            ),
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

    private fun isValidListenAddress(address: String): Boolean = parseListenAddress(address) != null

    private fun parseListenAddress(address: String): InetAddress? {
        if (address.isEmpty() || address.length > MAXIMUM_ADDRESS_CHARACTERS) return null
        val family = if (address.contains(':')) OsConstants.AF_INET6 else OsConstants.AF_INET
        return Os.inet_pton(family, address)
    }

    private fun isValidPort(port: Int): Boolean = port in MINIMUM_PORT..MAXIMUM_PORT

    private fun writeRecord(websocketPort: Int, mcpPort: Int, listenAddress: String) {
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
            .put(KEY_LISTEN_ADDRESS, listenAddress)
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
        if (!RECORD_KEYS.containsAll(document.keys().asSequence().toSet()) ||
            !document.has(KEY_WEBSOCKET_PORT) || !document.has(KEY_MCP_PORT) ||
            document.opt(KEY_VERSION) != RECORD_VERSION
        ) {
            return invalid(REASON_SHAPE)
        }
        val websocketPort = document.opt(KEY_WEBSOCKET_PORT) as? Int ?: return invalid(REASON_PORT)
        val mcpPort = document.opt(KEY_MCP_PORT) as? Int ?: return invalid(REASON_PORT)
        if (!isValidPort(websocketPort) || !isValidPort(mcpPort) || websocketPort == mcpPort) {
            return invalid(REASON_PORT)
        }
        val listenAddress = if (document.has(KEY_LISTEN_ADDRESS)) {
            document.opt(KEY_LISTEN_ADDRESS) as? String ?: return invalid(REASON_ADDRESS)
        } else {
            DEFAULT_LISTEN_ADDRESS
        }
        if (!isValidListenAddress(listenAddress)) return invalid(REASON_ADDRESS)
        return RecordState.Valid(listenAddress, websocketPort, mcpPort)
    }

    private fun invalid(reason: String): RecordState {
        logger.warn(EVENT_CONTROL_PORTS_INVALID, mapOf(FIELD_REASON to reason, FIELD_FILE to recordFile.absolutePath))
        return RecordState.Invalid
    }

    private sealed interface RecordState {
        data object Missing : RecordState

        data object Invalid : RecordState

        data class Valid(val listenAddress: String, val websocketPort: Int, val mcpPort: Int) : RecordState
    }

    internal companion object {
        const val DEFAULT_LISTEN_ADDRESS = "127.0.0.1"
        const val ERROR_ADDRESS_INVALID = "control_listen_address_invalid"
        const val MESSAGE_ADDRESS_INVALID = "use a numeric IPv4 or IPv6 listen address"
        const val FIELD_LISTEN_ADDRESS = "listen_address"
        const val KEY_LISTEN_ADDRESS = "listenAddress"
        const val REASON_ADDRESS = "listen_address"
        const val MAXIMUM_ADDRESS_CHARACTERS = 45
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
        val RECORD_KEYS = setOf(KEY_VERSION, KEY_WEBSOCKET_PORT, KEY_MCP_PORT, KEY_LISTEN_ADDRESS)
    }
}
