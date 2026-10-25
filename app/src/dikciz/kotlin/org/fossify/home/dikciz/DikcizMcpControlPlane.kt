package org.fossify.home.dikciz

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class DikcizMcpControlPlane(
    private val mainHandler: Handler,
    private val target: DikcizAutomationTarget,
    private val logger: DikcizLogger,
    private val remoteAccessAuth: DikcizRemoteAccessAuth,
    private val executeHttpCommand: (JSONObject, String?, Boolean) -> String,
    private val resources: DikcizMcpResources,
) {
    /** The port of the running listener, or the port the next start would bind. */
    @Volatile
    var port: Int = CONTROL_PORT
        private set

    @Volatile
    var listenAddress: String = DikcizControlPorts.DEFAULT_LISTEN_ADDRESS
        private set

    private val shellStreams = DikcizShellStreams(logger)
    private val streamingSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val isRunning = AtomicBoolean()
    private val sessions = ConcurrentHashMap<String, McpSession>()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var workerPool: ExecutorService? = null

    @Synchronized
    fun start(endpoint: InetSocketAddress): Boolean {
        if (!isRunning.compareAndSet(false, true)) {
            return true
        }
        try {
            port = endpoint.port
            listenAddress = endpoint.address.hostAddress ?: DikcizControlPorts.DEFAULT_LISTEN_ADDRESS
            serverSocket = ServerSocket().apply { reuseAddress = true }
            serverSocket?.bind(endpoint)
            workerPool = Executors.newFixedThreadPool(MAXIMUM_CONNECTION_COUNT) { runnable ->
                Thread(runnable, THREAD_NAME_PREFIX + UUID.randomUUID()).apply { isDaemon = true }
            }
            workerPool?.execute(::acceptConnections)
            logger.info(
                EVENT_MCP_CONTROL_PLANE_STARTED,
                mapOf(FIELD_BIND_ADDRESS to listenAddress, FIELD_PORT to port),
            )
            return true
        } catch (exception: Exception) {
            isRunning.set(false)
            serverSocket?.close()
            serverSocket = null
            workerPool?.shutdownNow()
            workerPool = null
            logger.error(
                EVENT_MCP_CONTROL_PLANE_START_FAILED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName, FIELD_PORT to port),
            )
            return false
        }
    }

    @Synchronized
    fun stop() {
        if (!isRunning.compareAndSet(true, false)) {
            return
        }
        serverSocket?.close()
        serverSocket = null
        shellStreams.close()
        streamingSockets.forEach { it.close() }
        sessions.clear()
        workerPool?.shutdownNow()
        workerPool = null
        logger.info(EVENT_MCP_CONTROL_PLANE_STOPPED)
    }

    private fun acceptConnections() {
        while (isRunning.get()) {
            val socket = try {
                serverSocket?.accept() ?: return
            } catch (exception: Exception) {
                if (isRunning.get()) {
                    logger.warn(
                        EVENT_MCP_ACCEPT_FAILED,
                        mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
                    )
                }
                return
            }
            workerPool?.execute { handleConnection(socket) } ?: socket.close()
        }
    }

    private fun handleConnection(socket: Socket) {
        socket.use { connection ->
            connection.soTimeout = SOCKET_TIMEOUT_MILLISECONDS
            val input = BufferedInputStream(connection.getInputStream())
            val output = BufferedOutputStream(connection.getOutputStream())
            try {
                val request = readRequest(input)
                validateOrigin(request.headers)
                validateRemoteAuthorization(request.headers)
                handleRequest(request, output, connection)
            } catch (exception: McpHttpException) {
                logger.warn(
                    EVENT_MCP_REQUEST_REJECTED,
                    mapOf(FIELD_REASON to exception.reason, FIELD_STATUS_CODE to exception.statusCode),
                )
                writeError(
                    output,
                    exception.statusCode,
                    exception.message.orEmpty(),
                    exception.responseHeaders,
                )
            } catch (exception: Exception) {
                logger.error(
                    EVENT_MCP_REQUEST_FAILED,
                    mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
                )
                if (connection !in streamingSockets) writeError(output, HTTP_STATUS_INTERNAL_SERVER_ERROR, MESSAGE_INTERNAL_ERROR)
            } finally {
                streamingSockets.remove(connection)
            }
        }
    }

    private fun handleRequest(request: HttpRequest, output: BufferedOutputStream, connection: Socket) {
        if (request.path == HTTP_COMMAND_PATH) {
            if (request.method != HTTP_METHOD_POST) {
                writeResponse(output, HTTP_STATUS_METHOD_NOT_ALLOWED, null, mapOf(HEADER_ALLOW to HTTP_METHOD_POST))
                return
            }
            if (request.headers[HEADER_CONTENT_TYPE]?.substringBefore(';')?.trim() != MIME_TYPE_JSON) {
                throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_INVALID_JSON, MESSAGE_INVALID_JSON)
            }
            val command = try {
                JSONObject(String(request.body, StandardCharsets.UTF_8))
            } catch (exception: JSONException) {
                throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_INVALID_JSON, MESSAGE_INVALID_JSON)
            }
            if (command.optString(DikcizAutomationControlPlane.KEY_TYPE) in setOf(DikcizShellStreams.TYPE_START, DikcizShellStreams.TYPE_STOP)) {
                handleHttpStream(command, request, output, connection)
                return
            }
            val response = executeHttpCommand(command, request.headers[HEADER_AUTHORIZATION], false)
            val document = JSONObject(response)
            writeJson(output, document)
            if (document.optString(DikcizAutomationControlPlane.KEY_TYPE) == DikcizAutomationControlPlane.TYPE_RESULT) {
                target.responseSent(
                    command.getString(DikcizAutomationControlPlane.KEY_TYPE),
                    document.getJSONObject(DikcizAutomationControlPlane.KEY_RESULT),
                )
            }
            return
        }
        if (request.path != MCP_PATH) {
            throw McpHttpException(HTTP_STATUS_NOT_FOUND, REASON_UNKNOWN_PATH, MESSAGE_UNKNOWN_PATH)
        }
        when (request.method) {
            HTTP_METHOD_GET -> writeMethodNotAllowed(output)
            HTTP_METHOD_DELETE -> deleteSession(request, output)
            HTTP_METHOD_POST -> postMessage(request, output, connection)
            else -> writeMethodNotAllowed(output)
        }
    }

    private fun deleteSession(request: HttpRequest, output: BufferedOutputStream) {
        val sessionID = request.headers[HEADER_MCP_SESSION_ID]
            ?: throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_SESSION_REQUIRED, MESSAGE_SESSION_REQUIRED)
        if (sessions.remove(sessionID) == null) {
            throw McpHttpException(HTTP_STATUS_NOT_FOUND, REASON_UNKNOWN_SESSION, MESSAGE_UNKNOWN_SESSION)
        }
        shellStreams.closeOwner(MCP_STREAM_OWNER_PREFIX + sessionID)
        logger.info(EVENT_MCP_SESSION_DELETED)
        writeResponse(output, HTTP_STATUS_NO_CONTENT, null)
    }

    private fun postMessage(request: HttpRequest, output: BufferedOutputStream, connection: Socket) {
        validatePostHeaders(request.headers)
        val message = parseMessage(request.body)
        val method = message.requireMcpString(KEY_METHOD)
        if (method == METHOD_INITIALIZE) {
            initialize(message, request.headers, output)
            return
        }
        val stateless = !request.headers.containsKey(HEADER_MCP_SESSION_ID)
        val session = if (stateless) {
            if (!validateModernRequest(message, request.headers, output)) return
            McpSession(PROTOCOL_VERSION).apply { isInitialized.set(true) }
        } else {
            requireSession(request.headers).also { validateProtocolVersion(message, request.headers, it) }
        }
        session.touch()
        when (method) {
            METHOD_DISCOVER -> writeJson(output, jsonRpcSuccess(message.requireID(), discovery()))
            "ping" -> {
                requireInitialized(session)
                writeJson(output, jsonRpcSuccess(message.requireID(), JSONObject()))
            }
            METHOD_INITIALIZED -> {
                if (message.has(KEY_ID)) {
                    throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_INVALID_NOTIFICATION, MESSAGE_INVALID_NOTIFICATION)
                }
                session.isInitialized.set(true)
                logger.info(EVENT_MCP_SESSION_INITIALIZED)
                writeResponse(output, HTTP_STATUS_ACCEPTED, null)
            }

            METHOD_TOOLS_LIST -> {
                requireInitialized(session)
                writeJson(output, jsonRpcSuccess(message.requireID(), toolsListResult()))
            }

            DikcizMcpResources.METHOD_LIST,
            DikcizMcpResources.METHOD_READ,
            DikcizMcpResources.METHOD_TEMPLATES,
            -> {
                requireInitialized(session)
                writeJson(output, resourceResponse(message, method))
            }

            METHOD_TOOLS_CALL -> {
                requireInitialized(session)
                val toolName = message.requireObject(KEY_PARAMS).requireMcpString(KEY_NAME)
                if (toolName in setOf(TOOL_SHELL_STREAM, TOOL_SHELL_STREAM_STOP)) {
                    handleStreamingTool(message, request, session, output, connection)
                    return
                }
                val result = callTool(message, request.headers[HEADER_AUTHORIZATION])
                writeJson(output, jsonRpcSuccess(message.requireID(), result))
                if (!result.optBoolean(KEY_IS_ERROR)) {
                    val toolName = message.getJSONObject(KEY_PARAMS).getString(KEY_NAME)
                    target.responseSent(TOOL_COMMANDS.getValue(toolName), result.getJSONObject(KEY_STRUCTURED_CONTENT))
                }
            }
            else -> writeJson(
                output,
                jsonRpcError(
                    message.opt(KEY_ID) ?: JSONObject.NULL,
                    JSON_RPC_METHOD_NOT_FOUND,
                    MESSAGE_METHOD_NOT_FOUND,
                ),
                statusCode = if (stateless) HTTP_STATUS_NOT_FOUND else HTTP_STATUS_OK,
            )
        }
    }

    private fun handleHttpStream(rawCommand: JSONObject, request: HttpRequest, output: BufferedOutputStream, connection: Socket) {
        val requestID = rawCommand.optString(DikcizAutomationControlPlane.KEY_REQUEST_ID)
        try {
            val command = DikcizPermissionRequests.operation(rawCommand)
            if (requestID.length != 36 || runCatching { UUID.fromString(requestID).toString() }.getOrNull() != requestID) {
                throw DikcizAutomationRequestException(DikcizAutomationControlPlane.ERROR_VALIDATION_FAILED, DikcizAutomationControlPlane.MESSAGE_INVALID_REQUEST_ID)
            }
            val stop = command.getString(DikcizAutomationControlPlane.KEY_TYPE) == DikcizShellStreams.TYPE_STOP
            DikcizShellStreams.validate(command, stop, envelope = true)
            val owner = HTTP_STREAM_OWNER_PREFIX + (request.headers[HEADER_AUTHORIZATION] ?: HTTP_ANONYMOUS_OWNER)
            if (stop) {
                writeJson(output, streamEnvelope(requestID, shellStreams.stop(owner, command), false))
                return
            }
            dispatchCommand(DikcizPermissionRequests.command(DikcizShellStreams.TYPE_START, rawCommand), request.headers[HEADER_AUTHORIZATION])
            val stream = shellStreams.open(owner, command)
            val isAuthorized = remoteAccessAuth.authorizationMonitor(request.headers[HEADER_AUTHORIZATION])
            streamingSockets.add(connection)
            try {
                writeStreamHeaders(output)
                writeStreamMessage(output, streamEnvelope(requestID, stream.description(), false))
                val completed = stream.consume(
                    isAllowed = { isRunning.get() && isAuthorized() },
                    abortConnection = { connection.close() },
                ) { event -> writeStreamMessage(output, streamEnvelope(requestID, event, true)) }
                writeStreamMessage(output, streamEnvelope(requestID, completed, true))
            } finally {
                stream.cancel(DikcizShellStreams.STATE_CANCELLED)
            }
        } catch (exception: DikcizAutomationRequestException) {
            writeJson(output, JSONObject()
                .put(DikcizAutomationControlPlane.KEY_TYPE, DikcizAutomationControlPlane.TYPE_ERROR)
                .put(DikcizAutomationControlPlane.KEY_REQUEST_ID, requestID)
                .put(DikcizAutomationControlPlane.KEY_CODE, exception.code)
                .put(DikcizAutomationControlPlane.KEY_MESSAGE, exception.message)
                .put(DikcizPermissionRequests.KEY_RECOVERY, exception.permissionRequest))
        }
    }

    private fun handleStreamingTool(message: JSONObject, request: HttpRequest, session: McpSession, output: BufferedOutputStream, connection: Socket) {
        val requestID = message.requireID()
        val params = message.requireObject(KEY_PARAMS)
        val stop = params.getString(KEY_NAME) == TOOL_SHELL_STREAM_STOP
        val rawArguments = params.optJSONObject(KEY_ARGUMENTS) ?: JSONObject()
        val sessionID = request.headers[HEADER_MCP_SESSION_ID]
        val owner = if (sessionID != null) MCP_STREAM_OWNER_PREFIX + sessionID
            else HTTP_STREAM_OWNER_PREFIX + (request.headers[HEADER_AUTHORIZATION] ?: HTTP_ANONYMOUS_OWNER)
        try {
            val arguments = DikcizPermissionRequests.operation(rawArguments)
            DikcizShellStreams.validate(arguments, stop)
            if (stop) {
                writeJson(output, jsonRpcSuccess(requestID, toolSuccess(shellStreams.stop(owner, arguments))))
                return
            }
            val level = params.optJSONObject(KEY_META)?.optString(KEY_META_LOG_LEVEL)
            if (level !in STREAM_LOG_LEVELS) {
                throw DikcizAutomationRequestException(DikcizAutomationControlPlane.ERROR_VALIDATION_FAILED, MESSAGE_STREAM_LOG_OPT_IN)
            }
            dispatchCommand(DikcizPermissionRequests.command(DikcizShellStreams.TYPE_START, rawArguments), request.headers[HEADER_AUTHORIZATION])
            val stream = shellStreams.open(owner, arguments)
            val isAuthorized = remoteAccessAuth.authorizationMonitor(request.headers[HEADER_AUTHORIZATION])
            streamingSockets.add(connection)
            try {
                writeStreamHeaders(output)
                writeStreamMessage(output, streamNotification(stream.description()))
                val completed = stream.consume(
                    isAllowed = {
                        session.touch()
                        isRunning.get() && (sessionID == null || sessions[sessionID] === session) && isAuthorized()
                    },
                    abortConnection = { connection.close() },
                ) { event ->
                    if (event.getString(DikcizShellStreams.KEY_EVENT) == DikcizShellStreams.EVENT_HEARTBEAT) {
                        writeStreamHeartbeat(output)
                    } else {
                        writeStreamMessage(output, streamNotification(event))
                    }
                }
                val result = toolSuccess(completed)
                val state = completed.getString(DikcizShellStreams.KEY_STATE)
                val isError = state !in STREAM_SUCCESS_STATES
                result.put(KEY_IS_ERROR, isError)
                if (isError) {
                    result.getJSONArray(KEY_CONTENT).getJSONObject(0)
                        .put(KEY_TEXT, MESSAGE_STREAM_FAILED + completed.optString(DikcizAutomationControlPlane.KEY_CODE, state))
                }
                writeStreamMessage(output, jsonRpcSuccess(requestID, result))
            } finally {
                stream.cancel(DikcizShellStreams.STATE_CANCELLED)
            }
        } catch (exception: DikcizAutomationRequestException) {
            writeJson(output, jsonRpcSuccess(requestID, toolFailure(exception.message.orEmpty(), exception.code, exception.permissionRequest)))
        }
    }

    private fun streamEnvelope(requestID: String, event: JSONObject, isEvent: Boolean): JSONObject = JSONObject()
        .put(DikcizAutomationControlPlane.KEY_TYPE, if (isEvent) DikcizShellStreams.TYPE_EVENT else DikcizAutomationControlPlane.TYPE_RESULT)
        .put(DikcizAutomationControlPlane.KEY_REQUEST_ID, requestID)
        .put(DikcizAutomationControlPlane.KEY_RESULT, event)

    private fun streamNotification(event: JSONObject): JSONObject = JSONObject()
        .put(KEY_JSON_RPC, JSON_RPC_VERSION)
        .put(KEY_METHOD, METHOD_LOG_MESSAGE)
        .put(KEY_PARAMS, JSONObject().put(KEY_LEVEL, STREAM_LOG_LEVEL).put(KEY_LOGGER, STREAM_LOGGER).put(KEY_DATA, event))

    private fun writeStreamHeaders(output: BufferedOutputStream) {
        val headers = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream; charset=utf-8\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        output.write(headers.toByteArray(StandardCharsets.US_ASCII))
        output.flush()
    }

    private fun writeStreamMessage(output: BufferedOutputStream, document: JSONObject) {
        synchronized(output) {
            output.write(("event: message\ndata: " + document.toString() + "\n\n").toByteArray(StandardCharsets.UTF_8))
            output.flush()
        }
    }

    private fun writeStreamHeartbeat(output: BufferedOutputStream) {
        synchronized(output) {
            output.write(STREAM_HEARTBEAT.toByteArray(StandardCharsets.US_ASCII))
            output.flush()
        }
    }

    private fun initialize(message: JSONObject, headers: Map<String, String>, output: BufferedOutputStream) {
        if (headers.containsKey(HEADER_MCP_SESSION_ID)) {
            throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_UNEXPECTED_SESSION, MESSAGE_UNEXPECTED_SESSION)
        }
        val requestID = message.requireID()
        val params = message.requireObject(KEY_PARAMS)
        val protocolVersion = params.requireMcpString(KEY_PROTOCOL_VERSION)
        params.requireObject(KEY_CAPABILITIES)
        val clientInfo = params.requireObject(KEY_CLIENT_INFO)
        clientInfo.requireMcpString(KEY_NAME)
        clientInfo.requireMcpString(KEY_VERSION)
        val negotiatedVersion = if (protocolVersion == PROTOCOL_VERSION || protocolVersion in LEGACY_PROTOCOL_VERSIONS) protocolVersion
            else LEGACY_PROTOCOL_VERSIONS.first()
        cleanExpiredSessions()
        if (sessions.size >= MAXIMUM_SESSION_COUNT) {
            throw McpHttpException(HTTP_STATUS_TOO_MANY_REQUESTS, REASON_SESSION_LIMIT, MESSAGE_SESSION_LIMIT)
        }
        val sessionID = UUID.randomUUID().toString()
        sessions[sessionID] = McpSession(negotiatedVersion)
        logger.info(EVENT_MCP_SESSION_CREATED)
        val result = JSONObject()
            .put(KEY_PROTOCOL_VERSION, negotiatedVersion)
            .put(KEY_CAPABILITIES, JSONObject()
                .put(KEY_TOOLS, JSONObject().put(KEY_LIST_CHANGED, false))
                .put(DikcizMcpResources.KEY_RESOURCES, JSONObject()))
            .put(KEY_INSTRUCTIONS, DikcizMcpResources.INSTRUCTIONS)
            .put(KEY_SERVER_INFO, JSONObject().put(KEY_NAME, SERVER_NAME).put(KEY_VERSION, SERVER_VERSION))
            .put(KEY_RESULT_TYPE, RESULT_TYPE_COMPLETE)
        writeJson(
            output,
            jsonRpcSuccess(requestID, result),
            mapOf(HEADER_MCP_SESSION_ID_RESPONSE to sessionID),
        )
    }

    private fun resourceResponse(message: JSONObject, method: String): JSONObject {
        val requestID = message.requireID()
        val params = message.requireObject(KEY_PARAMS)
        return try {
            val result = when (method) {
                DikcizMcpResources.METHOD_LIST -> resources.list(params)
                DikcizMcpResources.METHOD_READ -> resources.read(params)
                else -> resources.templates(params)
            }
            logger.debug(EVENT_MCP_RESOURCE_COMPLETED, mapOf(FIELD_RESOURCE_METHOD to method))
            jsonRpcSuccess(requestID, result)
        } catch (exception: DikcizMcpResourceException) {
            logger.warn(EVENT_MCP_RESOURCE_REJECTED, mapOf(FIELD_RESOURCE_METHOD to method))
            jsonRpcError(requestID, DikcizMcpResources.JSON_RPC_INVALID_PARAMS, exception.message.orEmpty()).apply {
                exception.uri?.let { uri ->
                    getJSONObject(KEY_ERROR).put(KEY_DATA, JSONObject().put(DikcizMcpResources.KEY_URI, uri))
                }
            }
        } catch (exception: IOException) {
            resourceFailure(requestID, method, exception)
        } catch (exception: IllegalStateException) {
            resourceFailure(requestID, method, exception)
        }
    }

    private fun resourceFailure(requestID: Any, method: String, exception: Exception): JSONObject {
        logger.error(EVENT_MCP_RESOURCE_FAILED, mapOf(FIELD_RESOURCE_METHOD to method, FIELD_ERROR_CLASS to exception.javaClass.simpleName))
        return jsonRpcError(requestID, JSON_RPC_INTERNAL_ERROR, MESSAGE_RESOURCE_FAILED)
    }

    internal fun executeOwnerRequest(method: String, params: JSONObject, readOnly: Boolean): JSONObject {
        return when (method) {
            "tools/list" -> toolsListResult(readOnly)
            "tools/call" -> try {
                callTool(JSONObject().put("params", params), null, readOnly)
            } catch (exception: McpHttpException) {
                toolFailure(exception.message.orEmpty(), exception.reason)
            }
            "tools/responseRecorded" -> {
                if (readOnly) requireReadOnlyCommand(TOOL_COMMANDS[params.getString(KEY_NAME)].orEmpty(), JSONObject())
                val result = params.getJSONObject(KEY_RESULT)
                if (!result.optBoolean(KEY_IS_ERROR)) {
                    target.responseSent(TOOL_COMMANDS.getValue(params.getString(KEY_NAME)), result.getJSONObject(KEY_STRUCTURED_CONTENT))
                }
                JSONObject()
            }
            "resources/list" -> resources.list(params)
            "resources/read" -> resources.read(params)
            else -> throw IllegalArgumentException("Unsupported local MCP method")
        }
    }

    private fun toolsListResult(readOnly: Boolean = false): JSONObject {
        val tools = JSONArray()
        TOOL_DEFINITIONS.filter { !readOnly || it.readOnly }.forEach { definition -> tools.put(definition.toJson()) }
        DikcizNativeCommands.DEFINITIONS.values.filter { !readOnly || it.readOnly }.forEach { definition -> tools.put(definition.toolDocument()) }
        if (readOnly) {
            for (index in 0 until tools.length()) {
                tools.getJSONObject(index).getJSONObject(KEY_INPUT_SCHEMA).getJSONObject(KEY_PROPERTIES)
                    .remove(DikcizPermissionRequests.KEY_AUTO_REQUEST)
            }
        }
        return JSONObject().put(KEY_RESULT_TYPE, RESULT_TYPE_COMPLETE).put(KEY_TOOLS, tools)
    }

    private fun callTool(message: JSONObject, authorization: String?, readOnly: Boolean = false): JSONObject {
        val params = message.requireObject(KEY_PARAMS)
        val toolName = params.requireMcpString(KEY_NAME)
        val commandType = TOOL_COMMANDS[toolName]
            ?: throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_UNKNOWN_TOOL, MESSAGE_UNKNOWN_TOOL)
        val rawArguments = params.optJSONObject(KEY_ARGUMENTS) ?: JSONObject()
        try {
            if (rawArguments.has(DikcizAutomationControlPlane.KEY_TYPE) ||
                rawArguments.has(DikcizAutomationControlPlane.KEY_REQUEST_ID)
            ) {
                throw DikcizAutomationRequestException(
                    DikcizAutomationControlPlane.ERROR_VALIDATION_FAILED,
                    DikcizAutomationControlPlane.MESSAGE_UNEXPECTED_FIELD,
                )
            }
            logger.debug(EVENT_MCP_TOOL_RECEIVED, mapOf(FIELD_TOOL_NAME to toolName))
            val command = JSONObject(rawArguments.toString())
                .put(DikcizAutomationControlPlane.KEY_TYPE, commandType)
                .put(DikcizAutomationControlPlane.KEY_REQUEST_ID, UUID.randomUUID().toString())
            val response = JSONObject(executeHttpCommand(command, authorization, readOnly))
            if (response.optString(DikcizAutomationControlPlane.KEY_TYPE) == DikcizAutomationControlPlane.TYPE_ERROR) {
                return toolFailure(
                    response.getString(DikcizAutomationControlPlane.KEY_MESSAGE),
                    response.getString(DikcizAutomationControlPlane.KEY_CODE),
                    response.optJSONObject(DikcizPermissionRequests.KEY_RECOVERY),
                )
            }
            val result = response.getJSONObject(DikcizAutomationControlPlane.KEY_RESULT)
            logger.debug(EVENT_MCP_TOOL_COMPLETED, mapOf(FIELD_TOOL_NAME to toolName))
            return toolSuccess(result)
        } catch (exception: DikcizAutomationRequestException) {
            logger.warn(
                EVENT_MCP_TOOL_REJECTED,
                mapOf(FIELD_TOOL_NAME to toolName, FIELD_REASON to exception.code),
            )
            return toolFailure(exception.message.orEmpty(), exception.code, exception.permissionRequest)
        } catch (exception: Exception) {
            logger.error(
                EVENT_MCP_TOOL_FAILED,
                mapOf(FIELD_TOOL_NAME to toolName, FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            return toolFailure(MESSAGE_INTERNAL_ERROR)
        }
    }

    private fun toolSuccess(result: JSONObject): JSONObject {
        val content = JSONArray()
        val structuredContent = JSONObject(result.toString())
        val pngBase64 = structuredContent.optString(KEY_PNG_BASE_64)
        if (pngBase64.isNotBlank()) {
            content.put(
                JSONObject()
                    .put(KEY_TYPE, CONTENT_TYPE_IMAGE)
                    .put(KEY_DATA, pngBase64)
                    .put(KEY_MIME_TYPE, MIME_TYPE_PNG),
            )
        }
        val resultMessage = if (result.has(DikcizPermissionRequests.KEY_REQUIREMENT)) {
            result.optString(DikcizPermissionRequests.KEY_INSTRUCTIONS, MESSAGE_TOOL_COMPLETED)
        } else MESSAGE_TOOL_COMPLETED
        content.put(JSONObject().put(KEY_TYPE, CONTENT_TYPE_TEXT).put(KEY_TEXT, resultMessage))
        return JSONObject()
            .put(KEY_RESULT_TYPE, RESULT_TYPE_COMPLETE)
            .put(KEY_CONTENT, content)
            .put(KEY_STRUCTURED_CONTENT, structuredContent)
            .put(KEY_IS_ERROR, false)
    }

    /**
     * A failed tool result. The typed [code] is carried in structured content so an MCP
     * client sees the same finite failure value the WebSocket plane returns.
     */
    private fun toolFailure(
        message: String,
        code: String? = null,
        permissionRequest: JSONObject? = null,
    ): JSONObject {
        val instructions = permissionRequest?.optString(DikcizPermissionRequests.KEY_INSTRUCTIONS)
        val text = if (instructions == null) message else "$message $instructions"
        return JSONObject()
            .put(KEY_RESULT_TYPE, RESULT_TYPE_COMPLETE)
            .put(KEY_CONTENT, JSONArray().put(JSONObject().put(KEY_TYPE, CONTENT_TYPE_TEXT).put(KEY_TEXT, text)))
            .put(
                KEY_STRUCTURED_CONTENT,
                JSONObject()
                    .put(DikcizPermissionRequests.KEY_RECOVERY, permissionRequest)
                    .put(DikcizAutomationControlPlane.KEY_CODE, code ?: JSONObject.NULL)
                    .put(DikcizAutomationControlPlane.KEY_MESSAGE, message),
            )
            .put(KEY_IS_ERROR, true)
    }

    private fun dispatchCommand(
        command: DikcizAutomationCommand,
        authorization: String?,
    ): JSONObject {
        if (command.type in DikcizAutomationControlPlane.REMOTE_AUTH_COMMAND_TYPES) {
            try {
                return remoteAccessAuth.executeRemoteCommand(command.type, authorization)
            } catch (exception: DikcizRemoteAccessAuthException) {
                throw DikcizAutomationRequestException(exception.code, exception.message.orEmpty())
            }
        }
        if (command.type == DikcizAutomationControlPlane.TYPE_WAIT_FOR) {
            return waitForAutomationView(command)
        }
        val result = if (target.requiresMainThread(command)) {
            callOnMain { target.execute(command) }
        } else {
            target.execute(command)
        }
        return target.completeResult(result)
    }

    private fun waitForAutomationView(command: DikcizAutomationCommand): JSONObject {
        val semanticID = command.request.getString(DikcizAutomationControlPlane.KEY_SEMANTIC_ID)
        val timeoutMilliseconds = command.request.getInt(DikcizAutomationControlPlane.KEY_TIMEOUT_MILLISECONDS)
        val deadlineMilliseconds = SystemClock.elapsedRealtime() + timeoutMilliseconds
        var snapshot: JSONObject
        while (true) {
            snapshot = callOnMain { target.snapshot() }
            val node = snapshot.optJSONArray(DikcizAutomationControlPlane.KEY_NODES)
                ?.let { nodes ->
                    (0 until nodes.length())
                        .asSequence()
                        .mapNotNull(nodes::optJSONObject)
                        .firstOrNull { candidate ->
                            candidate.optString(DikcizAutomationControlPlane.KEY_SEMANTIC_ID) == semanticID
                        }
                }
            if (node != null) {
                return JSONObject()
                    .put(DikcizAutomationControlPlane.KEY_FOUND, true)
                    .put(DikcizAutomationControlPlane.KEY_NODE, node)
                    .put(DikcizAutomationControlPlane.KEY_SNAPSHOT, snapshot)
            }
            val remainingMilliseconds = deadlineMilliseconds - SystemClock.elapsedRealtime()
            if (remainingMilliseconds <= 0) {
                return JSONObject()
                    .put(DikcizAutomationControlPlane.KEY_FOUND, false)
                    .put(DikcizAutomationControlPlane.KEY_SEMANTIC_ID, semanticID)
                    .put(DikcizAutomationControlPlane.KEY_SNAPSHOT, snapshot)
            }
            try {
                Thread.sleep(
                    minOf(DikcizAutomationControlPlane.WAIT_POLL_INTERVAL_MILLISECONDS, remainingMilliseconds),
                )
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw DikcizAutomationRequestException(
                    DikcizAutomationControlPlane.ERROR_TIMEOUT,
                    DikcizAutomationControlPlane.MESSAGE_WAIT_INTERRUPTED,
                )
            }
        }
    }

    private fun validatePostHeaders(headers: Map<String, String>) {
        if (!headers[HEADER_CONTENT_TYPE].orEmpty().startsWith(MIME_TYPE_JSON)) {
            throw McpHttpException(HTTP_STATUS_UNSUPPORTED_MEDIA_TYPE, REASON_CONTENT_TYPE, MESSAGE_CONTENT_TYPE)
        }
        val accepts = headers[HEADER_ACCEPT].orEmpty()
        if (!accepts.contains(MIME_TYPE_JSON) || !accepts.contains(MIME_TYPE_EVENT_STREAM)) {
            throw McpHttpException(HTTP_STATUS_NOT_ACCEPTABLE, REASON_ACCEPT, MESSAGE_ACCEPT)
        }
    }

    private fun validateOrigin(headers: Map<String, String>) {
        val origin = headers[HEADER_ORIGIN] ?: return
        if (DikcizAutomationControlPlane.LOCAL_ORIGIN_PATTERN.matches(origin)) {
            return
        }
        throw McpHttpException(HTTP_STATUS_FORBIDDEN, REASON_ORIGIN, MESSAGE_ORIGIN)
    }

    private fun validateRemoteAuthorization(headers: Map<String, String>) {
        if (remoteAccessAuth.acceptsAuthorization(headers[HEADER_AUTHORIZATION])) {
            return
        }
        throw McpHttpException(
            HTTP_STATUS_UNAUTHORIZED,
            REASON_AUTHORIZATION,
            MESSAGE_REMOTE_AUTH_REQUIRED,
            REMOTE_AUTH_CHALLENGE_HEADER,
        )
    }

    private fun requireSession(headers: Map<String, String>): McpSession {
        cleanExpiredSessions()
        val sessionID = headers[HEADER_MCP_SESSION_ID]
            ?: throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_SESSION_REQUIRED, MESSAGE_SESSION_REQUIRED)
        return sessions[sessionID]
            ?: throw McpHttpException(HTTP_STATUS_NOT_FOUND, REASON_UNKNOWN_SESSION, MESSAGE_UNKNOWN_SESSION)
    }

    private fun requireInitialized(session: McpSession) {
        if (!session.isInitialized.get()) {
            throw McpHttpException(
                HTTP_STATUS_BAD_REQUEST,
                REASON_INITIALIZATION_REQUIRED,
                MESSAGE_INITIALIZATION_REQUIRED,
            )
        }
    }

    private fun validateProtocolVersion(
        message: JSONObject,
        headers: Map<String, String>,
        session: McpSession,
    ) {
        val headerVersion = headers[HEADER_MCP_PROTOCOL_VERSION]
        if (headerVersion != session.protocolVersion) {
            throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_PROTOCOL_MISMATCH, MESSAGE_PROTOCOL_MISMATCH)
        }
        if (!message.has(KEY_ID) || session.protocolVersion in LEGACY_PROTOCOL_VERSIONS) {
            return
        }
        val params = message.optJSONObject(KEY_PARAMS) ?: JSONObject()
        val meta = params.optJSONObject(KEY_META)
            ?: throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_PROTOCOL_MISMATCH, MESSAGE_PROTOCOL_MISMATCH)
        if (meta.optString(KEY_META_PROTOCOL_VERSION) != session.protocolVersion) {
            throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_PROTOCOL_MISMATCH, MESSAGE_PROTOCOL_MISMATCH)
        }
        meta.requireObject(KEY_META_CLIENT_CAPABILITIES)
    }

    private fun discovery(): JSONObject = JSONObject()
        .put("supportedVersions", JSONArray(listOf(PROTOCOL_VERSION) + LEGACY_PROTOCOL_VERSIONS))
        .put(KEY_CAPABILITIES, JSONObject().put(KEY_TOOLS, JSONObject()).put(DikcizMcpResources.KEY_RESOURCES, JSONObject()))
        .put(KEY_INSTRUCTIONS, DikcizMcpResources.INSTRUCTIONS)

    private fun validateModernRequest(message: JSONObject, headers: Map<String, String>, output: BufferedOutputStream): Boolean {
        val id = message.opt(KEY_ID) ?: JSONObject.NULL
        val params = message.optJSONObject(KEY_PARAMS) ?: JSONObject()
        val meta = params.optJSONObject(KEY_META) ?: JSONObject()
        val requested = meta.opt(KEY_META_PROTOCOL_VERSION)
        fun reject(code: Int, text: String, data: JSONObject? = null): Boolean {
            val response = jsonRpcError(id, code, text)
            data?.let { response.getJSONObject(KEY_ERROR).put(KEY_DATA, it) }
            writeJson(output, response, statusCode = HTTP_STATUS_BAD_REQUEST)
            return false
        }
        if (requested !is String || requested != headers[HEADER_MCP_PROTOCOL_VERSION]) {
            return reject(JSON_RPC_HEADER_MISMATCH, "Protocol header and request metadata must agree")
        }
        if (requested != PROTOCOL_VERSION) {
            return reject(JSON_RPC_UNSUPPORTED_VERSION, "Unsupported protocol version", JSONObject()
                .put("requested", requested).put("supported", JSONArray(listOf(PROTOCOL_VERSION) + LEGACY_PROTOCOL_VERSIONS)))
        }
        if (headers[HEADER_MCP_METHOD] != message.optString(KEY_METHOD)) {
            return reject(JSON_RPC_HEADER_MISMATCH, "Mcp-Method header must match the request method")
        }
        if (meta.opt(KEY_META_CLIENT_CAPABILITIES) !is JSONObject) {
            return reject(JSON_RPC_INVALID_REQUEST, "Request metadata must include clientCapabilities")
        }
        val nameKey = when (message.optString(KEY_METHOD)) {
            METHOD_TOOLS_CALL, "prompts/get" -> KEY_NAME
            DikcizMcpResources.METHOD_READ -> DikcizMcpResources.KEY_URI
            else -> null
        }
        if (nameKey != null) {
            val bodyName = params.opt(nameKey)
            val headerName = headers[HEADER_MCP_NAME]
            if (bodyName !is String || headerName == null) return reject(JSON_RPC_HEADER_MISMATCH, "Mcp-Name header is required")
            val decoded = if (headerName.startsWith(HEADER_BASE64_PREFIX) && headerName.endsWith(HEADER_BASE64_SUFFIX)) {
                try {
                    val bytes = Base64.getDecoder().decode(headerName.removePrefix(HEADER_BASE64_PREFIX).removeSuffix(HEADER_BASE64_SUFFIX))
                    StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                } catch (_: IllegalArgumentException) { return reject(JSON_RPC_HEADER_MISMATCH, "Invalid encoded Mcp-Name header") }
                  catch (_: java.nio.charset.CharacterCodingException) { return reject(JSON_RPC_HEADER_MISMATCH, "Invalid UTF-8 Mcp-Name header") }
            } else headerName
            if (decoded != bodyName) return reject(JSON_RPC_HEADER_MISMATCH, "Mcp-Name header must match the request body")
        }
        return true
    }

    private fun cleanExpiredSessions() {
        val now = System.currentTimeMillis()
        sessions.entries.forEach { entry ->
            if (now - entry.value.lastTouched.get() > SESSION_TIMEOUT_MILLISECONDS) {
                if (sessions.remove(entry.key, entry.value)) shellStreams.closeOwner(MCP_STREAM_OWNER_PREFIX + entry.key)
            }
        }
    }

    private fun readRequest(input: BufferedInputStream): HttpRequest {
        val headerBytes = readHeaders(input)
        val lines = String(headerBytes, StandardCharsets.US_ASCII)
            .removeSuffix(HTTP_HEADER_ENDING)
            .split(HTTP_LINE_SEPARATOR)
        val requestLine = lines.firstOrNull()?.split(SPACE, limit = HTTP_REQUEST_LINE_PARTS)
            ?: throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_REQUEST_LINE, MESSAGE_REQUEST_LINE)
        if (requestLine.size != HTTP_REQUEST_LINE_PARTS || requestLine[HTTP_VERSION_INDEX] != HTTP_VERSION) {
            throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_REQUEST_LINE, MESSAGE_REQUEST_LINE)
        }
        val headers = parseHeaders(lines.drop(FIRST_HEADER_LINE_INDEX))
        val contentLength = headers[HEADER_CONTENT_LENGTH]?.toIntOrNull() ?: ZERO_BYTES
        if (contentLength !in ZERO_BYTES..MAXIMUM_BODY_BYTES) {
            throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_BODY_SIZE, MESSAGE_BODY_SIZE)
        }
        val body = if (contentLength == ZERO_BYTES) ByteArray(ZERO_BYTES) else readExact(input, contentLength)
        return HttpRequest(requestLine[HTTP_METHOD_INDEX], requestLine[HTTP_PATH_INDEX], headers, body)
    }

    private fun readHeaders(input: BufferedInputStream): ByteArray {
        val bytes = ByteArrayOutputStream()
        var matchCount = ZERO_BYTES
        repeat(MAXIMUM_HEADER_BYTES) {
            val value = input.read()
            if (value < ZERO_BYTES) {
                throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_HEADERS, MESSAGE_HEADERS)
            }
            bytes.write(value)
            matchCount = when {
                matchCount == ZERO_BYTES && value == CARRIAGE_RETURN -> ONE_BYTE
                matchCount == ONE_BYTE && value == LINE_FEED -> TWO_BYTES
                matchCount == TWO_BYTES && value == CARRIAGE_RETURN -> THREE_BYTES
                matchCount == THREE_BYTES && value == LINE_FEED -> FOUR_BYTES
                value == CARRIAGE_RETURN -> ONE_BYTE
                else -> ZERO_BYTES
            }
            if (matchCount == HTTP_HEADER_ENDING_BYTES) {
                return bytes.toByteArray()
            }
        }
        throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_HEADERS, MESSAGE_HEADERS)
    }

    private fun parseHeaders(lines: List<String>): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        lines.forEach { line ->
            val separator = line.indexOf(COLON)
            if (separator <= ZERO_BYTES) {
                throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_HEADERS, MESSAGE_HEADERS)
            }
            val key = line.substring(ZERO_BYTES, separator).lowercase()
            val value = line.substring(separator + ONE_BYTE).trim()
            if (headers.put(key, value) != null) {
                if (key == HEADER_AUTHORIZATION) {
                    throw McpHttpException(
                        HTTP_STATUS_UNAUTHORIZED,
                        REASON_AUTHORIZATION,
                        MESSAGE_REMOTE_AUTH_REQUIRED,
                        REMOTE_AUTH_CHALLENGE_HEADER,
                    )
                }
                throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_HEADERS, MESSAGE_HEADERS)
            }
        }
        return headers
    }

    private fun readExact(input: BufferedInputStream, length: Int): ByteArray {
        val bytes = ByteArray(length)
        var offset = ZERO_BYTES
        while (offset < length) {
            val read = input.read(bytes, offset, length - offset)
            if (read < ZERO_BYTES) {
                throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_BODY_SIZE, MESSAGE_BODY_SIZE)
            }
            offset += read
        }
        return bytes
    }

    private fun parseMessage(body: ByteArray): JSONObject {
        if (body.isEmpty()) {
            throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_INVALID_JSON, MESSAGE_INVALID_JSON)
        }
        val message = try {
            JSONObject(String(body, StandardCharsets.UTF_8))
        } catch (exception: JSONException) {
            throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_INVALID_JSON, MESSAGE_INVALID_JSON)
        }
        if (message.optString(KEY_JSON_RPC) != JSON_RPC_VERSION) {
            throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_INVALID_JSON_RPC, MESSAGE_INVALID_JSON_RPC)
        }
        return message
    }

    private fun writeMethodNotAllowed(output: BufferedOutputStream) {
        writeResponse(output, HTTP_STATUS_METHOD_NOT_ALLOWED, null, mapOf(HEADER_ALLOW to ALLOWED_METHODS))
    }

    private fun writeError(
        output: BufferedOutputStream,
        statusCode: Int,
        message: String,
        headers: Map<String, String> = emptyMap(),
    ) {
        writeJson(output, jsonRpcError(JSONObject.NULL, JSON_RPC_INVALID_REQUEST, message), headers, statusCode)
    }

    private fun writeJson(
        output: BufferedOutputStream,
        body: JSONObject,
        headers: Map<String, String> = emptyMap(),
        statusCode: Int = HTTP_STATUS_OK,
    ) {
        writeResponse(output, statusCode, body.toString().toByteArray(StandardCharsets.UTF_8), headers)
    }

    private fun writeResponse(
        output: BufferedOutputStream,
        statusCode: Int,
        body: ByteArray?,
        headers: Map<String, String> = emptyMap(),
    ) {
        val responseHeaders = linkedMapOf(
            HEADER_CONNECTION_RESPONSE to HEADER_CONNECTION_CLOSE,
            HEADER_CACHE_CONTROL to CACHE_CONTROL_NO_STORE,
        )
        if (body != null) {
            responseHeaders[HEADER_CONTENT_TYPE_RESPONSE] = "$MIME_TYPE_JSON; charset=utf-8"
            responseHeaders[HEADER_CONTENT_LENGTH_RESPONSE] = body.size.toString()
        } else {
            responseHeaders[HEADER_CONTENT_LENGTH_RESPONSE] = ZERO_BYTES.toString()
        }
        responseHeaders.putAll(headers)
        val status = HTTP_STATUS_LINES[statusCode] ?: HTTP_STATUS_LINES.getValue(HTTP_STATUS_INTERNAL_SERVER_ERROR)
        val encodedHeaders = buildString {
            append(HTTP_VERSION).append(SPACE).append(status).append(HTTP_LINE_SEPARATOR)
            responseHeaders.forEach { (key, value) -> append(key).append(COLON_SPACE).append(value).append(HTTP_LINE_SEPARATOR) }
            append(HTTP_LINE_SEPARATOR)
        }
        output.write(encodedHeaders.toByteArray(StandardCharsets.US_ASCII))
        body?.let { output.write(it) }
        output.flush()
    }

    private fun JSONObject.requireObject(key: String): JSONObject {
        return optJSONObject(key) ?: throw McpHttpException(
            HTTP_STATUS_BAD_REQUEST,
            REASON_INVALID_JSON,
            MESSAGE_INVALID_JSON,
        )
    }

    private fun JSONObject.requireMcpString(key: String): String {
        val value = opt(key)
        if (value is String && value.isNotBlank()) {
            return value
        }
        throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_INVALID_JSON, MESSAGE_INVALID_JSON)
    }

    private fun JSONObject.requireID(): Any {
        val value = opt(KEY_ID)
        if (value is String || value is Number) {
            return value
        }
        throw McpHttpException(HTTP_STATUS_BAD_REQUEST, REASON_INVALID_JSON, MESSAGE_INVALID_JSON)
    }

    private fun jsonRpcSuccess(id: Any, result: JSONObject): JSONObject {
        result.put(KEY_RESULT_TYPE, RESULT_TYPE_COMPLETE)
        result.put(KEY_META, (result.optJSONObject(KEY_META) ?: JSONObject()).put(KEY_META_SERVER_INFO,
            JSONObject().put(KEY_NAME, SERVER_NAME).put(KEY_VERSION, SERVER_VERSION)))
        if (listOf("tools", "resources", "contents", "resourceTemplates", "supportedVersions").any(result::has)) {
            result.put("ttlMs", 0).put("cacheScope", "private")
        }
        return JSONObject().put(KEY_JSON_RPC, JSON_RPC_VERSION).put(KEY_ID, id).put(KEY_RESULT, result)
    }

    private fun jsonRpcError(id: Any, code: Int, message: String): JSONObject {
        return JSONObject()
            .put(KEY_JSON_RPC, JSON_RPC_VERSION)
            .put(KEY_ID, id)
            .put(KEY_ERROR, JSONObject().put(KEY_CODE, code).put(KEY_MESSAGE, message))
    }

    private fun <T> callOnMain(action: () -> T): T {
        if (Looper.myLooper() == mainHandler.looper) {
            return action()
        }
        val completion = CountDownLatch(ONE_BYTE)
        var result: T? = null
        var failure: Throwable? = null
        mainHandler.post {
            try {
                result = action()
            } catch (exception: Throwable) {
                failure = exception
            } finally {
                completion.countDown()
            }
        }
        if (!completion.await(DikcizAutomationControlPlane.UI_THREAD_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            throw DikcizAutomationRequestException(
                DikcizAutomationControlPlane.ERROR_TIMEOUT,
                DikcizAutomationControlPlane.MESSAGE_UI_THREAD_TIMEOUT,
            )
        }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private data class HttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: ByteArray,
    )

    private class McpSession(
        val protocolVersion: String,
    ) {
        val isInitialized = AtomicBoolean()
        val lastTouched = AtomicLong(System.currentTimeMillis())

        fun touch() {
            lastTouched.set(System.currentTimeMillis())
        }
    }

    private data class ToolDefinition(
        val name: String,
        val title: String,
        val description: String,
        val arguments: List<ToolArgument>,
        val readOnly: Boolean,
        val isDestructive: Boolean = false,
    ) {
        fun toJson(): JSONObject {
            val properties = JSONObject()
            val required = JSONArray()
            arguments.forEach { argument ->
                properties.put(argument.name, argument.toJson())
                if (argument.isRequired) {
                    required.put(argument.name)
                }
            }
            properties.put(
                DikcizPermissionRequests.KEY_AUTO_REQUEST,
                JSONObject()
                    .put(KEY_TYPE, "boolean")
                    .put(KEY_DESCRIPTION, "Show owner permission recovery when required. Defaults to false; explicit permission-request tools always ask. Never grants access or retries an operation.")
                    .put(KEY_SCHEMA_DEFAULT, false),
            )
            val schema = JSONObject()
                .put(KEY_TYPE, SCHEMA_TYPE_OBJECT)
                .put(KEY_PROPERTIES, properties)
                .put(KEY_ADDITIONAL_PROPERTIES, false)
            if (required.length() > ZERO_BYTES) {
                schema.put(KEY_REQUIRED, required)
            }
            return JSONObject()
                .put(KEY_NAME, name)
                .put(KEY_TITLE, title)
                .put(KEY_DESCRIPTION, description)
                .put(KEY_INPUT_SCHEMA, schema)
                .put(
                    KEY_ANNOTATIONS,
                    JSONObject()
                        .put(KEY_READ_ONLY_HINT, readOnly)
                        .put(KEY_DESTRUCTIVE_HINT, isDestructive)
                        .put(KEY_OPEN_WORLD_HINT, false),
                )
        }
    }

    private data class ToolArgument(
        val name: String,
        val description: String,
        val type: String,
        val isRequired: Boolean,
        val minimum: Int? = null,
        val maximum: Int? = null,
        val defaultValue: Int? = null,
        val maximumLength: Int? = null,
    ) {
        fun toJson(): JSONObject {
            val value = JSONObject().put(KEY_TYPE, type).put(KEY_DESCRIPTION, description)
            minimum?.let { value.put(KEY_MINIMUM, it) }
            maximum?.let { value.put(KEY_MAXIMUM, it) }
            defaultValue?.let { value.put(KEY_SCHEMA_DEFAULT, it) }
            maximumLength?.let { value.put(KEY_SCHEMA_MAX_LENGTH, it) }
            return value
        }
    }

    private class McpHttpException(
        val statusCode: Int,
        val reason: String,
        message: String,
        val responseHeaders: Map<String, String> = emptyMap(),
    ) : IllegalArgumentException(message)

    companion object {
        private const val KEY_SCHEMA_DEFAULT = "default"
        private const val KEY_SCHEMA_MAX_LENGTH = "maxLength"
        const val HTTP_COMMAND_PATH = "/v1/automation"
        const val ALLOWED_METHODS = "POST, DELETE"
        const val CACHE_CONTROL_NO_STORE = "no-store"
        const val CARRIAGE_RETURN = 13
        const val COLON = ':'
        const val COLON_SPACE = ": "
        const val CONTENT_TYPE_IMAGE = "image"
        const val CONTENT_TYPE_TEXT = "text"
        const val CONTROL_PORT = DikcizControlPorts.DEFAULT_MCP_PORT
        const val EVENT_MCP_ACCEPT_FAILED = "mcp_accept_failed"
        const val EVENT_MCP_CONTROL_PLANE_STARTED = "mcp_control_plane_started"
        const val EVENT_MCP_CONTROL_PLANE_START_FAILED = "mcp_control_plane_start_failed"
        const val EVENT_MCP_CONTROL_PLANE_STOPPED = "mcp_control_plane_stopped"
        const val EVENT_MCP_REQUEST_FAILED = "mcp_request_failed"
        const val EVENT_MCP_REQUEST_REJECTED = "mcp_request_rejected"
        const val EVENT_MCP_SESSION_CREATED = "mcp_session_created"
        const val EVENT_MCP_SESSION_DELETED = "mcp_session_deleted"
        const val EVENT_MCP_SESSION_INITIALIZED = "mcp_session_initialized"
        const val EVENT_MCP_TOOL_COMPLETED = "mcp_tool_completed"
        const val EVENT_MCP_TOOL_FAILED = "mcp_tool_failed"
        const val EVENT_MCP_TOOL_RECEIVED = "mcp_tool_received"
        const val EVENT_MCP_TOOL_REJECTED = "mcp_tool_rejected"
        const val FIELD_BIND_ADDRESS = "bind_address"
        const val FIELD_ERROR_CLASS = "error_class"
        const val FIELD_PORT = "port"
        const val FIELD_REASON = "reason"
        const val FIELD_STATUS_CODE = "status_code"
        const val FIELD_TOOL_NAME = "tool_name"
        const val FIRST_HEADER_LINE_INDEX = 1
        const val FOUR_BYTES = 4
        const val HEADER_ACCEPT = "accept"
        const val HEADER_ALLOW = "Allow"
        const val HEADER_AUTHORIZATION = "authorization"
        const val HEADER_CACHE_CONTROL = "Cache-Control"
        const val HEADER_CONNECTION = "connection"
        const val HEADER_CONNECTION_CLOSE = "close"
        const val HEADER_CONNECTION_RESPONSE = "Connection"
        const val HEADER_CONTENT_LENGTH = "content-length"
        const val HEADER_CONTENT_LENGTH_RESPONSE = "Content-Length"
        const val HEADER_CONTENT_TYPE = "content-type"
        const val HEADER_CONTENT_TYPE_RESPONSE = "Content-Type"
        const val HEADER_MCP_PROTOCOL_VERSION = "mcp-protocol-version"
        const val HEADER_MCP_SESSION_ID = "mcp-session-id"
        const val HEADER_MCP_SESSION_ID_RESPONSE = "MCP-Session-Id"
        const val HEADER_ORIGIN = "origin"
        const val HEADER_WWW_AUTHENTICATE = "WWW-Authenticate"
        const val HTTP_HEADER_ENDING = "\r\n\r\n"
        const val HTTP_HEADER_ENDING_BYTES = 4
        const val HTTP_LINE_SEPARATOR = "\r\n"
        const val HTTP_METHOD_DELETE = "DELETE"
        const val HTTP_METHOD_GET = "GET"
        const val HTTP_METHOD_INDEX = 0
        const val HTTP_METHOD_POST = "POST"
        const val HTTP_PATH_INDEX = 1
        const val HTTP_REQUEST_LINE_PARTS = 3
        const val HTTP_STATUS_ACCEPTED = 202
        const val HTTP_STATUS_BAD_REQUEST = 400
        const val HTTP_STATUS_FORBIDDEN = 403
        const val HTTP_STATUS_INTERNAL_SERVER_ERROR = 500
        const val HTTP_STATUS_METHOD_NOT_ALLOWED = 405
        const val HTTP_STATUS_NOT_ACCEPTABLE = 406
        const val HTTP_STATUS_NOT_FOUND = 404
        const val HTTP_STATUS_NO_CONTENT = 204
        const val HTTP_STATUS_OK = 200
        const val HTTP_STATUS_UNAUTHORIZED = 401
        const val HTTP_STATUS_TOO_MANY_REQUESTS = 429
        const val HTTP_STATUS_UNSUPPORTED_MEDIA_TYPE = 415
        val HTTP_STATUS_LINES = mapOf(
            HTTP_STATUS_OK to "200 OK",
            HTTP_STATUS_ACCEPTED to "202 Accepted",
            HTTP_STATUS_NO_CONTENT to "204 No Content",
            HTTP_STATUS_BAD_REQUEST to "400 Bad Request",
            HTTP_STATUS_UNAUTHORIZED to "401 Unauthorized",
            HTTP_STATUS_FORBIDDEN to "403 Forbidden",
            HTTP_STATUS_NOT_FOUND to "404 Not Found",
            HTTP_STATUS_METHOD_NOT_ALLOWED to "405 Method Not Allowed",
            HTTP_STATUS_NOT_ACCEPTABLE to "406 Not Acceptable",
            HTTP_STATUS_UNSUPPORTED_MEDIA_TYPE to "415 Unsupported Media Type",
            HTTP_STATUS_TOO_MANY_REQUESTS to "429 Too Many Requests",
            HTTP_STATUS_INTERNAL_SERVER_ERROR to "500 Internal Server Error",
        )
        const val HTTP_VERSION = "HTTP/1.1"
        const val HTTP_VERSION_INDEX = 2
        private const val JSON_RPC_INTERNAL_ERROR = -32603
        private const val KEY_INSTRUCTIONS = "instructions"
        private const val EVENT_MCP_RESOURCE_COMPLETED = "mcp_resource_completed"
        private const val EVENT_MCP_RESOURCE_REJECTED = "mcp_resource_rejected"
        private const val EVENT_MCP_RESOURCE_FAILED = "mcp_resource_failed"
        private const val FIELD_RESOURCE_METHOD = "resource_method"
        private const val MESSAGE_RESOURCE_FAILED = "Packaged documentation could not be loaded"
        const val JSON_RPC_INVALID_REQUEST = -32600
        const val JSON_RPC_METHOD_NOT_FOUND = -32601
        const val JSON_RPC_VERSION = "2.0"
        const val KEY_ACCEPT = "accept"
        const val KEY_ADDITIONAL_PROPERTIES = "additionalProperties"
        const val KEY_ANNOTATIONS = "annotations"
        const val KEY_ARGUMENTS = "arguments"
        const val KEY_CAPABILITIES = "capabilities"
        const val KEY_CLIENT_INFO = "clientInfo"
        const val KEY_CODE = "code"
        const val KEY_CONTENT = "content"
        const val KEY_DATA = "data"
        const val KEY_DESCRIPTION = "description"
        const val KEY_DESTRUCTIVE_HINT = "destructiveHint"
        const val KEY_ERROR = "error"
        const val KEY_ID = "id"
        const val KEY_INPUT_SCHEMA = "inputSchema"
        const val KEY_IS_ERROR = "isError"
        const val KEY_JSON_RPC = "jsonrpc"
        const val KEY_LIST_CHANGED = "listChanged"
        const val KEY_MESSAGE = "message"
        const val KEY_MAXIMUM = "maximum"
        const val KEY_META = "_meta"
        const val KEY_META_CLIENT_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities"
        const val KEY_META_PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion"
        const val KEY_METHOD = "method"
        const val KEY_MIME_TYPE = "mimeType"
        const val KEY_MINIMUM = "minimum"
        const val KEY_NAME = "name"
        const val KEY_OPEN_WORLD_HINT = "openWorldHint"
        const val KEY_PARAMS = "params"
        const val KEY_PNG_BASE_64 = "pngBase64"
        const val KEY_PROPERTIES = "properties"
        const val KEY_PROTOCOL_VERSION = "protocolVersion"
        const val KEY_READ_ONLY_HINT = "readOnlyHint"
        const val KEY_REQUIRED = "required"
        const val KEY_RESULT = "result"
        const val KEY_RESULT_TYPE = "resultType"
        const val KEY_SERVER_INFO = "serverInfo"
        const val KEY_STRUCTURED_CONTENT = "structuredContent"
        const val KEY_TEXT = "text"
        const val KEY_TITLE = "title"
        const val KEY_TOOLS = "tools"
        const val KEY_TYPE = "type"
        const val KEY_VERSION = "version"
        const val LINE_FEED = 10
        const val MAXIMUM_BODY_BYTES = HomeConfigStore.MAXIMUM_CONFIGURATION_DOCUMENT_BYTES
        private const val MAXIMUM_COMMAND_CONNECTION_COUNT = 4
        // Full stream capacity must leave workers available for stop and other commands.
        const val MAXIMUM_CONNECTION_COUNT = MAXIMUM_COMMAND_CONNECTION_COUNT + DikcizShellStreams.MAXIMUM_STREAM_COUNT
        const val MAXIMUM_HEADER_BYTES = 8_192
        const val MAXIMUM_SESSION_COUNT = 8
        const val MCP_PATH = "/mcp"
        const val MESSAGE_ACCEPT = "Accept must include application/json and text/event-stream"
        const val MESSAGE_BODY_SIZE = "request body exceeds its size limit"
        const val MESSAGE_CONTENT_TYPE = "Content-Type must be application/json"
        const val MESSAGE_HEADERS = "invalid HTTP headers"
        const val MESSAGE_INITIALIZATION_REQUIRED = "send notifications/initialized before calling tools or resources"
        const val MESSAGE_INTERNAL_ERROR = "request could not complete"
        const val MESSAGE_INVALID_JSON = "request body must be a JSON object"
        const val MESSAGE_INVALID_JSON_RPC = "request must use JSON-RPC 2.0"
        const val MESSAGE_INVALID_NOTIFICATION = "notification must not include an ID"
        const val MESSAGE_METHOD_NOT_FOUND = "method is not supported"
        const val MESSAGE_ORIGIN = "Origin is not allowed"
        const val MESSAGE_REMOTE_AUTH_REQUIRED = "a valid remote bearer credential is required"
        const val MESSAGE_PROTOCOL_MISMATCH = "unsupported MCP protocol version"
        const val MESSAGE_REQUEST_LINE = "invalid HTTP request line"
        const val MESSAGE_SESSION_LIMIT = "too many active MCP sessions"
        const val MESSAGE_SESSION_REQUIRED = "MCP-Session-Id is required"
        const val MESSAGE_TOOL_COMPLETED = "tool completed"
        const val MESSAGE_UNEXPECTED_SESSION = "initialization must not include MCP-Session-Id"
        const val MESSAGE_UNKNOWN_PATH = "unknown MCP endpoint"
        const val MESSAGE_UNKNOWN_SESSION = "MCP session was not found"
        const val MESSAGE_UNKNOWN_TOOL = "tool was not found"
        const val MIME_TYPE_EVENT_STREAM = "text/event-stream"
        const val MIME_TYPE_JSON = "application/json"
        const val MIME_TYPE_PNG = "image/png"
        const val METHOD_INITIALIZE = "initialize"
        const val METHOD_INITIALIZED = "notifications/initialized"
        const val METHOD_TOOLS_CALL = "tools/call"
        const val METHOD_TOOLS_LIST = "tools/list"
        const val ONE_BYTE = 1
        const val TOOL_SHELL_STREAM = "dikciz_shell_stream"
        const val TOOL_SHELL_STREAM_STOP = "dikciz_shell_stream_stop"
        private const val KEY_META_LOG_LEVEL = "io.modelcontextprotocol/logLevel"
        private const val KEY_LEVEL = "level"
        private const val KEY_LOGGER = "logger"
        private const val METHOD_LOG_MESSAGE = "notifications/message"
        private const val STREAM_LOG_LEVEL = "info"
        private const val STREAM_LOGGER = "dikciz.shell"
        private const val STREAM_HEARTBEAT = ": keepalive\n\n"
        private const val MESSAGE_STREAM_FAILED = "Shell stream stopped: "
        private const val MESSAGE_STREAM_LOG_OPT_IN = "shell streaming requires params._meta io.modelcontextprotocol/logLevel set to info or debug"
        private const val MCP_STREAM_OWNER_PREFIX = "mcp:"
        private const val HTTP_STREAM_OWNER_PREFIX = "http:"
        private const val HTTP_ANONYMOUS_OWNER = "anonymous"
        private val STREAM_LOG_LEVELS = setOf("info", "debug")
        private val STREAM_SUCCESS_STATES = setOf(DikcizShellStreams.STATE_EXITED, DikcizShellStreams.STATE_CANCELLED)
        private const val METHOD_DISCOVER = "server/discover"
        private const val HEADER_MCP_METHOD = "mcp-method"
        private const val HEADER_MCP_NAME = "mcp-name"
        private const val HEADER_BASE64_PREFIX = "=?base64?"
        private const val HEADER_BASE64_SUFFIX = "?="
        private const val KEY_META_SERVER_INFO = "io.modelcontextprotocol/serverInfo"
        private const val JSON_RPC_HEADER_MISMATCH = -32020
        private const val JSON_RPC_UNSUPPORTED_VERSION = -32022
        private val LEGACY_PROTOCOL_VERSIONS = listOf("2025-11-25", "2025-06-18", "2025-03-26")
        const val PROTOCOL_VERSION = "2026-07-28"
        const val REASON_ACCEPT = "accept"
        const val REASON_BODY_SIZE = "body_size"
        const val REASON_CONTENT_TYPE = "content_type"
        const val REASON_HEADERS = "headers"
        const val REASON_INITIALIZATION_REQUIRED = "initialization_required"
        const val REASON_INVALID_JSON = "invalid_json"
        const val REASON_INVALID_JSON_RPC = "invalid_json_rpc"
        const val REASON_INVALID_NOTIFICATION = "invalid_notification"
        const val REASON_ORIGIN = "origin"
        const val REASON_AUTHORIZATION = "authorization"
        const val REASON_PROTOCOL_MISMATCH = "protocol_mismatch"
        const val REASON_REQUEST_LINE = "request_line"
        const val REASON_SESSION_LIMIT = "session_limit"
        const val REASON_SESSION_REQUIRED = "session_required"
        const val REASON_UNEXPECTED_SESSION = "unexpected_session"
        const val REASON_UNKNOWN_PATH = "unknown_path"
        const val REASON_UNKNOWN_SESSION = "unknown_session"
        const val REASON_UNKNOWN_TOOL = "unknown_tool"
        const val RESULT_TYPE_COMPLETE = "complete"
        const val SCHEMA_TYPE_INTEGER = "integer"
        const val SCHEMA_TYPE_ARRAY = "array"
        const val SCHEMA_TYPE_BOOLEAN = "boolean"
        const val SCHEMA_TYPE_OBJECT = "object"
        const val SCHEMA_TYPE_STRING = "string"
        const val SERVER_NAME = "dikciz"
        const val SERVER_VERSION = "1.0.0"
        const val SESSION_TIMEOUT_MILLISECONDS = 10 * 60 * 1_000L
        const val SOCKET_TIMEOUT_MILLISECONDS = 10_000
        const val SPACE = " "
        const val THREE_BYTES = 3
        const val THREAD_NAME_PREFIX = "dikciz-mcp-"
        const val TWO_BYTES = 2
        const val ZERO_BYTES = 0
        val ACCESSIBILITY_ACTION_REQUIRED_ARGUMENT_KEYS = setOf(
            DikcizAutomationControlPlane.KEY_SNAPSHOT_ID,
            DikcizAutomationControlPlane.KEY_NODE_ID,
            DikcizAutomationControlPlane.KEY_ACTION,
        )
        val APP_ACTION_REQUIRED_ARGUMENT_KEYS = setOf(
            DikcizAutomationControlPlane.KEY_ACTION,
            DikcizAutomationControlPlane.KEY_COMPONENT,
        )
        val COMMAND_ARGUMENT_KEYS = mapOf(
            DikcizPermissionRequests.TYPE_REQUEST to setOf(DikcizPermissionRequests.KEY_REQUIREMENT),
            DikcizDeviceInspection.TYPE_DEVICE_INFO to emptySet(),
            DikcizDeviceInspection.TYPE_DISPLAY_SCREENSHOT to emptySet(),
            DikcizDeviceInspection.TYPE_PACKAGE_INFO to setOf(DikcizDeviceInspection.KEY_PACKAGE_NAME),
            DikcizDeviceInspection.TYPE_RESTART to emptySet(),
            DikcizDeviceInspection.TYPE_LAUNCHER_HANDOFF to setOf(DikcizDeviceInspection.KEY_PACKAGE_NAME),
            DikcizAutomationControlPlane.TYPE_ACCESSIBILITY_SNAPSHOT to emptySet(),
            DikcizAutomationControlPlane.TYPE_APP_CATALOGUE to setOf(
                DikcizAutomationControlPlane.KEY_QUERY,
            ),
            DikcizAutomationControlPlane.TYPE_APP_ACTION to APP_ACTION_REQUIRED_ARGUMENT_KEYS,
            DikcizAutomationControlPlane.TYPE_ACCESSIBILITY_ACTION to setOf(
                DikcizAutomationControlPlane.KEY_SNAPSHOT_ID,
                DikcizAutomationControlPlane.KEY_NODE_ID,
                DikcizAutomationControlPlane.KEY_ACTION,
                DikcizAutomationControlPlane.KEY_TEXT,
            ),
            DikcizAutomationControlPlane.TYPE_SNAPSHOT to emptySet(),
            DikcizAutomationControlPlane.TYPE_CONFIG_GET to emptySet(),
            DikcizAutomationControlPlane.TYPE_CONFIG_REPLACE to setOf(
                DikcizAutomationControlPlane.KEY_CONFIG,
            ),
            DikcizAutomationControlPlane.TYPE_CONFIG_SEED to setOf(
                DikcizAutomationControlPlane.KEY_CONFIG,
            ),
            DikcizAutomationControlPlane.TYPE_AUTOMATION_SERVICE_SYNC to emptySet(),
            DikcizAutomationControlPlane.TYPE_AUTOMATION_STATUS to emptySet(),
            DikcizAutomationControlPlane.TYPE_AUTOMATION_TRIGGER to setOf(
                DikcizAutomationControlPlane.KEY_SCRIPT_ID,
            ),
            DikcizAutomationControlPlane.TYPE_AUTOMATION_DISPATCH to setOf(
                DikcizAutomationControlPlane.KEY_ACTIONS,
                DikcizAutomationControlPlane.KEY_SOURCE_WIDGET_ADDRESS,
            ),
            DikcizAutomationControlPlane.TYPE_CONTROL_STATUS to emptySet(),
            DikcizAutomationControlPlane.TYPE_OPEN_AUTOMATION_SETUP to emptySet(),
            DikcizAutomationControlPlane.TYPE_REMOTE_AUTH_STATUS to emptySet(),
            DikcizAutomationControlPlane.TYPE_REMOTE_AUTH_ENABLE to emptySet(),
            DikcizAutomationControlPlane.TYPE_REMOTE_AUTH_DISABLE to emptySet(),
            DikcizAutomationControlPlane.TYPE_SCRIPT_LOGS to emptySet(),
            DikcizAutomationControlPlane.TYPE_RESET to emptySet(),
            DikcizAutomationControlPlane.TYPE_WAIT_FOR to setOf(
                DikcizAutomationControlPlane.KEY_SEMANTIC_ID,
                DikcizAutomationControlPlane.KEY_TIMEOUT_MILLISECONDS,
            ),
            DikcizAutomationControlPlane.TYPE_FIND to setOf(DikcizAutomationControlPlane.KEY_SEMANTIC_ID),
            DikcizAutomationControlPlane.TYPE_HOME_GET to emptySet(),
            DikcizAutomationControlPlane.TYPE_TAP to setOf(DikcizAutomationControlPlane.KEY_SEMANTIC_ID),
            DikcizAutomationControlPlane.TYPE_LONG_PRESS to setOf(DikcizAutomationControlPlane.KEY_SEMANTIC_ID),
            DikcizAutomationControlPlane.TYPE_SELECT_PAGE to setOf(DikcizAutomationControlPlane.KEY_PAGE_ID),
            DikcizAutomationControlPlane.TYPE_SCROLL_BY to setOf(DikcizAutomationControlPlane.KEY_DELTA_Y),
            DikcizAutomationControlPlane.TYPE_SCROLL_TO to setOf(DikcizAutomationControlPlane.KEY_SEMANTIC_ID),
            DikcizAutomationControlPlane.TYPE_SET_TEXT to setOf(
                DikcizAutomationControlPlane.KEY_SEMANTIC_ID,
                DikcizAutomationControlPlane.KEY_TEXT,
            ),
            DikcizAutomationControlPlane.TYPE_LAUNCH_APP to setOf(DikcizAutomationControlPlane.KEY_SEMANTIC_ID),
            DikcizAutomationControlPlane.TYPE_SHELL to setOf(
                DikcizAutomationControlPlane.KEY_COMMAND,
                DikcizAutomationControlPlane.KEY_ROOT,
                DikcizAutomationControlPlane.KEY_TIMEOUT_MILLISECONDS,
                DikcizPrivilegedShell.KEY_MAX_OUTPUT_BYTES,
            ),
            DikcizAutomationControlPlane.TYPE_INTENT to setOf(
                DikcizAutomationControlPlane.KEY_INTENT_TYPE,
                DikcizAutomationControlPlane.KEY_ACTION,
                DikcizAutomationControlPlane.KEY_INTENT_DATA,
            ),
            DikcizAutomationControlPlane.TYPE_SCREENSHOT to emptySet(),
            DikcizAutomationControlPlane.TYPE_UI_DUMP to emptySet(),
            DikcizAutomationControlPlane.TYPE_DIAGNOSTICS to emptySet(),
            DikcizAutomationControlPlane.TYPE_WIDGET_GET to setOf(
                DikcizAutomationControlPlane.KEY_WIDGET_ADDRESS,
            ),
            DikcizAutomationControlPlane.TYPE_ADD_WIDGET to setOf(
                DikcizAutomationControlPlane.KEY_WIDGET_TYPE,
            ),
            DikcizAutomationControlPlane.TYPE_GRID_SET to setOf(
                DikcizAutomationControlPlane.KEY_COLUMNS,
                DikcizAutomationControlPlane.KEY_ROWS,
                DikcizAutomationControlPlane.KEY_GAP_DP,
                DikcizAutomationControlPlane.KEY_OUTER_PADDING_DP,
            ),
            DikcizAutomationControlPlane.TYPE_WIDGET_MOVE to setOf(
                DikcizAutomationControlPlane.KEY_WIDGET_ADDRESS,
                DikcizAutomationControlPlane.KEY_CELL,
            ),
            DikcizAutomationControlPlane.TYPE_WIDGET_RESIZE to setOf(
                DikcizAutomationControlPlane.KEY_WIDGET_ADDRESS,
                DikcizAutomationControlPlane.KEY_CELL,
            ),
            DikcizAutomationControlPlane.TYPE_HTML_WIDGET_RENDERER_CRASH to setOf(
                DikcizAutomationControlPlane.KEY_WIDGET_ADDRESS,
            ),
        ) + DikcizFileCommands.FIELDS + DikcizNativeCommands.FIELDS
        val TOOL_COMMANDS = mapOf(
            DikcizPermissionRequests.TOOL_REQUEST to DikcizPermissionRequests.TYPE_REQUEST,
            "dikciz_device_info" to DikcizDeviceInspection.TYPE_DEVICE_INFO,
            "dikciz_display_screenshot" to DikcizDeviceInspection.TYPE_DISPLAY_SCREENSHOT,
            "dikciz_package_info" to DikcizDeviceInspection.TYPE_PACKAGE_INFO,
            "dikciz_restart" to DikcizDeviceInspection.TYPE_RESTART,
            "dikciz_launcher_handoff" to DikcizDeviceInspection.TYPE_LAUNCHER_HANDOFF,
            "dikciz_accessibility_snapshot" to DikcizAutomationControlPlane.TYPE_ACCESSIBILITY_SNAPSHOT,
            "dikciz_accessibility_action" to DikcizAutomationControlPlane.TYPE_ACCESSIBILITY_ACTION,
            "dikciz_snapshot" to DikcizAutomationControlPlane.TYPE_SNAPSHOT,
            "dikciz_config_get" to DikcizAutomationControlPlane.TYPE_CONFIG_GET,
            "dikciz_config_replace" to DikcizAutomationControlPlane.TYPE_CONFIG_REPLACE,
            "dikciz_config_seed" to DikcizAutomationControlPlane.TYPE_CONFIG_SEED,
            "dikciz_automation_service_sync" to DikcizAutomationControlPlane.TYPE_AUTOMATION_SERVICE_SYNC,
            "dikciz_automation_status" to DikcizAutomationControlPlane.TYPE_AUTOMATION_STATUS,
            "dikciz_automation_trigger" to DikcizAutomationControlPlane.TYPE_AUTOMATION_TRIGGER,
            "dikciz_automation_dispatch" to DikcizAutomationControlPlane.TYPE_AUTOMATION_DISPATCH,
            TOOL_CONTROL_STATUS to DikcizAutomationControlPlane.TYPE_CONTROL_STATUS,
            TOOL_REMOTE_AUTH_STATUS to DikcizAutomationControlPlane.TYPE_REMOTE_AUTH_STATUS,
            TOOL_REMOTE_AUTH_ENABLE to DikcizAutomationControlPlane.TYPE_REMOTE_AUTH_ENABLE,
            TOOL_REMOTE_AUTH_DISABLE to DikcizAutomationControlPlane.TYPE_REMOTE_AUTH_DISABLE,
            TOOL_SCRIPT_LOGS to DikcizAutomationControlPlane.TYPE_SCRIPT_LOGS,
            "dikciz_config_reset" to DikcizAutomationControlPlane.TYPE_RESET,
            "dikciz_wait_for" to DikcizAutomationControlPlane.TYPE_WAIT_FOR,
            "dikciz_find" to DikcizAutomationControlPlane.TYPE_FIND,
            "dikciz_home_get" to DikcizAutomationControlPlane.TYPE_HOME_GET,
            "dikciz_tap" to DikcizAutomationControlPlane.TYPE_TAP,
            "dikciz_long_press" to DikcizAutomationControlPlane.TYPE_LONG_PRESS,
            "dikciz_select_page" to DikcizAutomationControlPlane.TYPE_SELECT_PAGE,
            "dikciz_scroll_by" to DikcizAutomationControlPlane.TYPE_SCROLL_BY,
            "dikciz_scroll_to" to DikcizAutomationControlPlane.TYPE_SCROLL_TO,
            "dikciz_set_text" to DikcizAutomationControlPlane.TYPE_SET_TEXT,
            "dikciz_launch_app" to DikcizAutomationControlPlane.TYPE_LAUNCH_APP,
            TOOL_APP_CATALOGUE to DikcizAutomationControlPlane.TYPE_APP_CATALOGUE,
            TOOL_APP_ACTION to DikcizAutomationControlPlane.TYPE_APP_ACTION,
            TOOL_OPEN_AUTOMATION_SETUP to DikcizAutomationControlPlane.TYPE_OPEN_AUTOMATION_SETUP,
            TOOL_SHELL_STREAM to DikcizShellStreams.TYPE_START,
            TOOL_SHELL_STREAM_STOP to DikcizShellStreams.TYPE_STOP,
            "dikciz_shell" to DikcizAutomationControlPlane.TYPE_SHELL,
            "dikciz_intent" to DikcizAutomationControlPlane.TYPE_INTENT,
            "dikciz_screenshot" to DikcizAutomationControlPlane.TYPE_SCREENSHOT,
            "dikciz_ui_dump" to DikcizAutomationControlPlane.TYPE_UI_DUMP,
            "dikciz_diagnostics" to DikcizAutomationControlPlane.TYPE_DIAGNOSTICS,
            "dikciz_widget_get" to DikcizAutomationControlPlane.TYPE_WIDGET_GET,
            TOOL_ADD_WIDGET to DikcizAutomationControlPlane.TYPE_ADD_WIDGET,
            TOOL_GRID_SET to DikcizAutomationControlPlane.TYPE_GRID_SET,
            TOOL_WIDGET_MOVE to DikcizAutomationControlPlane.TYPE_WIDGET_MOVE,
            TOOL_WIDGET_RESIZE to DikcizAutomationControlPlane.TYPE_WIDGET_RESIZE,
            TOOL_HTML_WIDGET_RENDERER_CRASH to DikcizAutomationControlPlane.TYPE_HTML_WIDGET_RENDERER_CRASH,
        ) + DikcizFileCommands.TOOLS + DikcizNativeCommands.TOOLS
        private val FILE_FIELD_DESCRIPTIONS = mapOf(
            DikcizFileCommands.KEY_PATH to "Absolute filesystem path, using Android permissions granted to Dikciz.",
            DikcizFileCommands.KEY_OFFSET to "Nonnegative byte offset, or directory entry offset. Defaults to zero.",
            DikcizFileCommands.KEY_LIMIT to "Maximum directory entries (1 to 500) or file bytes (1 to 262144).",
            DikcizFileCommands.KEY_DATA_BASE64 to "Strict base64 file bytes, at most 262144 decoded bytes per chunk.",
            DikcizFileCommands.KEY_OVERWRITE to "Allow replacing an existing file at offset zero. Defaults to false.",
            DikcizFileCommands.KEY_PARENTS to "Create missing parent directories. Defaults to false.",
            DikcizFileCommands.KEY_SHA256 to "Optional expected SHA-256 of the complete APK, exactly 64 hex digits.",
            DikcizFileCommands.KEY_SESSION_ID to "Install session ID returned by dikciz_apk_install.",
        )
        private val FILE_TOOL_DESCRIPTIONS = mapOf(
            DikcizFileCommands.TYPE_FILE_LIST to "List one directory page in filesystem enumeration order. Follow hasMore with offset plus the returned item count; directory changes can alter enumeration order.",
            DikcizFileCommands.TYPE_FILE_READ to "Read a bounded binary file chunk as base64. Follow nextOffset until eof is true.",
            DikcizFileCommands.TYPE_FILE_WRITE to "Create or explicitly overwrite a file at offset zero, or append a chunk only when offset equals its current size. Never executes uploaded content.",
            DikcizFileCommands.TYPE_FILE_MKDIR to "Create a directory wherever Dikciz can write. Optionally create missing parents.",
            DikcizFileCommands.TYPE_APK_INSTALL to "Submit one readable standalone APK, up to 512 MiB, to Android PackageInstaller. Android may require unknown-source access and confirmation. Poll dikciz_apk_install_status for the actual outcome.",
            DikcizFileCommands.TYPE_APK_INSTALL_STATUS to "Read the actual Android outcome for one installation submitted by Dikciz. Submission or pending confirmation is not successful installation.",
        )

        private val TOOL_DEFINITIONS = listOf(
            ToolDefinition(DikcizPermissionRequests.TOOL_REQUEST, "Request Android access", "Ask the phone owner to grant one deviceAccess requirement from automationStatus. Posts a targeted notification and consent prompt. Never grants automatically or retries the blocked operation. Read state and notificationPosted before telling the user to check the phone.", listOf(ToolArgument(DikcizPermissionRequests.KEY_REQUIREMENT, "Exact requirement ID from automationStatus.deviceAccess.requirements.", SCHEMA_TYPE_STRING, true, maximumLength = 64)), false),
            ToolDefinition("dikciz_launcher_handoff", "Switch Dikciz builds", "Acknowledge, release listener ownership and launch an installed Dikciz release/debug package. Same-package requests do nothing. Verify the target after reconnecting; failed startup retries the original listener configuration.", listOf(packageNameArgument()), false),
            ToolDefinition("dikciz_display_screenshot", "Capture the Android display", "Capture the visible Android display, including other apps, through owner-enabled Accessibility on Android 11+. Secure windows and system rate limits still apply.", emptyList(), true),
            ToolDefinition("dikciz_device_info", "Inspect Android and Dikciz", "Read the running package, version, process and launcher instance, plus Android device information.", emptyList(), true),
            ToolDefinition("dikciz_package_info", "Inspect an installed package", "Read installed version and launch component through Android PackageManager without shell privileges.", listOf(packageNameArgument()), true),
            ToolDefinition("dikciz_restart", "Restart Dikciz", "Acknowledge, then recreate the launcher and listeners without killing the process or changing saved configuration. Reconnect and verify a new instanceId through dikciz_device_info.", emptyList(), false),
            ToolDefinition("dikciz_accessibility_snapshot", "Read current Android app", "Read a bounded snapshot of the active Android accessibility tree. The owner must first enable Dikciz cross-app automation in Android Accessibility settings.", emptyList(), true),
            ToolDefinition("dikciz_accessibility_action", "Act on current Android app", "Run one supported action on a node from an unexpired accessibility snapshot.", listOf(accessibilitySnapshotIDArgument(), accessibilityNodeIDArgument(), accessibilityActionArgument(), optionalAccessibilityTextArgument()), false),
            ToolDefinition("dikciz_snapshot", "Dikciz UI snapshot", "Read the current semantic launcher UI tree.", emptyList(), true),
            ToolDefinition("dikciz_config_get", "Read Dikciz configuration", "Read the complete validated Dikciz configuration document.", emptyList(), true),
            ToolDefinition("dikciz_config_replace", "Replace Dikciz configuration", "Validate, atomically save, and reload a complete Dikciz configuration document.", listOf(configArgument()), false),
            ToolDefinition("dikciz_config_seed", "Seed Dikciz configuration", "Validate, atomically save, and reload a complete Dikciz configuration document before scripted launcher use.", listOf(configArgument()), false),
            ToolDefinition("dikciz_automation_service_sync", "Sync Dikciz automation service", "Re-evaluate the current automation policy and ask the private Android service to reload its public configuration.", emptyList(), false),
            ToolDefinition("dikciz_automation_status", "Read Dikciz automation status", "Read typed automation capabilities, event requirements, available sensors, and relevant Android access state.", emptyList(), true),
            ToolDefinition("dikciz_automation_trigger", "Trigger Dikciz automation script", "Queue one fixed manual event for an enabled configured script with a matching policy and subscription.", listOf(scriptIDArgument()), false),
            ToolDefinition("dikciz_automation_dispatch", "Dispatch Dikciz actions", "Run the same typed action array used by an HTML widget or Lua script.", listOf(sourceWidgetAddressArgument(), actionsArgument()), false),
            ToolDefinition(TOOL_CONTROL_STATUS, "Read Dikciz control status", "Read the complete local command contract, limits, transports, and safe-mode state.", emptyList(), true),
            ToolDefinition(TOOL_REMOTE_AUTH_STATUS, "Read remote auth status", "Read remote WebSocket and MCP bearer-auth state without exposing its verifier.", emptyList(), true),
            ToolDefinition(TOOL_REMOTE_AUTH_ENABLE, "Enable remote bearer auth", "Require the existing phone-configured remote bearer credential on both remote control planes.", emptyList(), false),
            ToolDefinition(TOOL_REMOTE_AUTH_DISABLE, "Disable remote bearer auth", "Allow headerless local-tunnel control while retaining the existing phone-configured verifier.", emptyList(), false),
            ToolDefinition(TOOL_SCRIPT_LOGS, "Read Dikciz script logs", "Read the bounded, sanitized script-log snapshot.", emptyList(), true),
            ToolDefinition("dikciz_config_reset", "Reset Dikciz configuration", "Restore the bundled Dikciz configuration while retaining permitted unknown root namespaces.", emptyList(), false),
            ToolDefinition("dikciz_wait_for", "Wait for Dikciz node", "Wait up to a bounded interval for one semantic ID to appear in the launcher snapshot.", listOf(semanticIDArgument(), timeoutArgument()), true),
            ToolDefinition("dikciz_find", "Find Dikciz node", "Find one node by its stable semantic ID.", listOf(semanticIDArgument()), true),
            ToolDefinition("dikciz_home_get", "Read Dikciz page map", "Read pages keyed by home alias, stable page ID, and H/V address, with each page's widget map.", emptyList(), true),
            ToolDefinition("dikciz_tap", "Tap Dikciz node", "Tap one actionable semantic ID.", listOf(semanticIDArgument()), false),
            ToolDefinition("dikciz_long_press", "Long press Dikciz node", "Long press one semantic ID.", listOf(semanticIDArgument()), false),
            ToolDefinition("dikciz_select_page", "Select Dikciz page", "Select one page by its stable ID.", listOf(pageIDArgument()), false),
            ToolDefinition("dikciz_scroll_by", "Scroll Dikciz page", "Scroll the selected page by an exact pixel delta.", listOf(scrollDeltaArgument()), false),
            ToolDefinition("dikciz_scroll_to", "Scroll Dikciz node into view", "Scroll the selected page to a semantic ID.", listOf(semanticIDArgument()), false),
            ToolDefinition("dikciz_set_text", "Set Dikciz text input", "Set an editable text input or text widget by semantic ID.", listOf(semanticIDArgument(), textArgument()), false),
            ToolDefinition("dikciz_launch_app", "Launch Dikciz app widget", "Launch an explicit app widget by semantic ID.", listOf(semanticIDArgument()), false),
            ToolDefinition(TOOL_OPEN_AUTOMATION_SETUP, "Open Dikciz automation setup", "Open the native Dikciz Automation access sheet so the phone owner can grant the Android access a denied action needs.", emptyList(), false),
            ToolDefinition(TOOL_APP_CATALOGUE, "List launchable Android apps", "List the launcher app catalogue with stable semantic IDs, human labels, package names, and components, plus the supported app actions. An optional query filters on label and package name.", listOf(optionalAppQueryArgument()), true),
            ToolDefinition(TOOL_APP_ACTION, "Run a Dikciz app action", "Run one app action on a launchable component: launch, addShortcut, appInfo, uninstall, or forceStop. Force stop needs root and reports root_unavailable otherwise. Uninstall hands off to Android, which asks for its own confirmation.", listOf(appActionArgument(), appComponentArgument()), false, isDestructive = true),
            ToolDefinition(TOOL_SHELL_STREAM, "Stream shell output", "Stream command output through SSE log notifications. Opt in with params._meta io.modelcontextprotocol/logLevel=info. timeoutMilliseconds=0 runs until cancellation. Root defaults to true; set false for app permissions.", listOf(shellCommandArgument(), rootArgument(), streamTimeoutArgument(), streamChunkArgument()), false),
            ToolDefinition(TOOL_SHELL_STREAM_STOP, "Stop shell stream", "Stop a stream owned by the same MCP session.", listOf(ToolArgument(DikcizShellStreams.KEY_STREAM_ID, "Stream UUID from the started notification.", SCHEMA_TYPE_STRING, true, maximumLength = 36)), false),
            ToolDefinition("dikciz_shell", "Run Dikciz shell command", "Run a command through su by default, or as the launcher app user when root is false.", listOf(shellCommandArgument(), rootArgument(), shellTimeoutArgument(), shellOutputArgument()), false),
            ToolDefinition("dikciz_intent", "Dispatch Dikciz device intent", "Dispatch an Android activity or broadcast intent using the launcher app's permissions.", listOf(intentTypeArgument(), actionArgument(), intentDataArgument()), false),
            ToolDefinition("dikciz_screenshot", "Capture Dikciz screenshot", "Capture a bounded PNG of the launcher.", emptyList(), true),
            ToolDefinition("dikciz_ui_dump", "Dump Dikciz UI", "Read the current semantic launcher UI tree.", emptyList(), true),
            ToolDefinition("dikciz_diagnostics", "Read Dikciz diagnostics", "Read local control-plane diagnostics.", emptyList(), true),
            ToolDefinition("dikciz_widget_get", "Read Dikciz widget", "Read one widget by its stable H/V widget address.", listOf(widgetAddressArgument()), true),
            ToolDefinition(TOOL_GRID_SET, "Set Dikciz page grid", "Replace the page grid used by every page. Reflow widget rectangles to fit, relocate collisions and remove overflow items in saved order. Return the saved grid and evictedWidgets with pageId/id records. Evicting app shortcuts leaves their apps installed.", listOf(gridColumnsArgument(), gridRowsArgument(), gridGapArgument(), gridOuterPaddingArgument()), false),
            ToolDefinition(TOOL_ADD_WIDGET, "Add Dikciz widget", "Place one new top-level item in the first fitting free cells of the selected page grid. Rejects with page_full and writes nothing when no space fits. Use dikciz_widget_move and dikciz_widget_resize to place it afterwards.", listOf(widgetTypeArgument()), false),
            ToolDefinition(TOOL_WIDGET_MOVE, "Move Dikciz widget", "Move one widget to an explicit grid rectangle. Rejects with grid_collision or grid_bounds and writes nothing.", listOf(widgetAddressArgument(), cellArgument()), false),
            ToolDefinition(TOOL_WIDGET_RESIZE, "Resize Dikciz widget", "Resize one widget to an explicit grid rectangle. Rejects with grid_collision or grid_bounds and writes nothing.", listOf(widgetAddressArgument(), cellArgument()), false),
            ToolDefinition(TOOL_HTML_WIDGET_RENDERER_CRASH, "Test HTML renderer recovery", "Crash one rendered HTML widget through Android's documented WebView test URL, then report the normal renderer-loss recovery through local logs.", listOf(widgetAddressArgument()), false),
        ) + fileToolDefinitions()

        private fun fileToolDefinitions(): List<ToolDefinition> {
            return DikcizFileCommands.TOOLS.map { (tool, command) ->
                val arguments = DikcizFileCommands.FIELDS.getValue(command).sorted().map { field ->
                    val fieldType = when (field) {
                        DikcizFileCommands.KEY_OFFSET, DikcizFileCommands.KEY_LIMIT,
                        DikcizFileCommands.KEY_SESSION_ID -> SCHEMA_TYPE_INTEGER
                        DikcizFileCommands.KEY_OVERWRITE, DikcizFileCommands.KEY_PARENTS -> SCHEMA_TYPE_BOOLEAN
                        else -> SCHEMA_TYPE_STRING
                    }
                    ToolArgument(
                        field,
                        FILE_FIELD_DESCRIPTIONS.getValue(field),
                        fieldType,
                        field in DikcizFileCommands.REQUIRED_FIELDS.getValue(command),
                    )
                }
                ToolDefinition(
                    tool,
                    command,
                    FILE_TOOL_DESCRIPTIONS.getValue(command),
                    arguments,
                    command in DikcizFileCommands.READ_ONLY_TYPES,
                    command == DikcizFileCommands.TYPE_FILE_WRITE || command == DikcizFileCommands.TYPE_APK_INSTALL,
                )
            }
        }

        const val TOOL_ADD_WIDGET = "dikciz_add_widget"
        const val TOOL_GRID_SET = "dikciz_grid_set"
        const val TOOL_APP_ACTION = "dikciz_app_action"
        const val TOOL_OPEN_AUTOMATION_SETUP = "dikciz_open_automation_setup"
        const val TOOL_APP_CATALOGUE = "dikciz_app_catalogue"
        const val TOOL_CONTROL_STATUS = "dikciz_control_status"
        const val TOOL_HTML_WIDGET_RENDERER_CRASH = "dikciz_html_widget_renderer_crash"
        const val TOOL_REMOTE_AUTH_DISABLE = "dikciz_remote_auth_disable"
        const val TOOL_WIDGET_MOVE = "dikciz_widget_move"
        const val TOOL_WIDGET_RESIZE = "dikciz_widget_resize"
        const val TOOL_REMOTE_AUTH_ENABLE = "dikciz_remote_auth_enable"
        const val TOOL_REMOTE_AUTH_STATUS = "dikciz_remote_auth_status"
        const val TOOL_SCRIPT_LOGS = "dikciz_script_logs"

        val REMOTE_AUTH_CHALLENGE_HEADER = mapOf(
            HEADER_WWW_AUTHENTICATE to "Bearer realm=\"Dikciz\"",
        )

        const val ERROR_READ_ONLY_VIOLATION = "read_only_violation"

        internal fun requireReadOnlyCommand(commandType: String, request: JSONObject) {
            val allowed = TOOL_DEFINITIONS.any { it.readOnly && TOOL_COMMANDS[it.name] == commandType } ||
                commandType in DikcizNativeCommands.READ_ONLY_TYPES
            if (!allowed || request.optBoolean(DikcizPermissionRequests.KEY_AUTO_REQUEST)) {
                throw DikcizAutomationRequestException(
                    ERROR_READ_ONLY_VIOLATION,
                    "Read-only mode blocks this operation. The owner can change access in chat model settings.",
                )
            }
        }

        fun toolNameForCommand(commandType: String): String? {
            return TOOL_COMMANDS.entries.firstOrNull { it.value == commandType }?.key
        }

        private fun optionalAppQueryArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_QUERY,
            "Case-insensitive filter matched against the app label and its package name.",
            SCHEMA_TYPE_STRING,
            false,
            maximum = DikcizAutomationControlPlane.MAXIMUM_APP_QUERY_CHARACTERS,
        )

        private fun appActionArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_ACTION,
            "One of launch, addShortcut, appInfo, uninstall, forceStop.",
            SCHEMA_TYPE_STRING,
            true,
            maximum = DikcizAutomationControlPlane.MAXIMUM_APP_ACTION_CHARACTERS,
        )

        private fun appComponentArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_COMPONENT,
            "Flattened Android component from dikciz_app_catalogue, such as com.example/com.example.MainActivity.",
            SCHEMA_TYPE_STRING,
            true,
            maximum = DikcizAutomationControlPlane.MAXIMUM_COMPONENT_CHARACTERS,
        )

        private fun streamTimeoutArgument(): ToolArgument = ToolArgument(
            DikcizShellStreams.KEY_TIMEOUT,
            "Deadline in milliseconds. Zero, the default, keeps streaming until stopped or disconnected.",
            SCHEMA_TYPE_INTEGER,
            false,
            minimum = 0,
            maximum = Int.MAX_VALUE,
            defaultValue = 0,
        )

        private fun streamChunkArgument(): ToolArgument = ToolArgument(
            DikcizShellStreams.KEY_CHUNK_BYTES,
            "Maximum bytes per output chunk. Output is base64 encoded without a total output limit.",
            SCHEMA_TYPE_INTEGER,
            false,
            minimum = 1,
            maximum = DikcizShellStreams.MAXIMUM_CHUNK_BYTES,
            defaultValue = DikcizShellStreams.DEFAULT_CHUNK_BYTES,
        )

        private fun semanticIDArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_SEMANTIC_ID,
            "Stable Dikciz semantic ID from dikciz_snapshot.",
            SCHEMA_TYPE_STRING,
            true,
            maximum = DikcizAutomationControlPlane.MAXIMUM_SEMANTIC_ID_CHARACTERS,
        )

        private fun accessibilitySnapshotIDArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_SNAPSHOT_ID,
            "Opaque snapshot ID returned by dikciz_accessibility_snapshot.",
            SCHEMA_TYPE_STRING,
            true,
            maximum = DikcizAutomationControlPlane.MAXIMUM_ACCESSIBILITY_SNAPSHOT_ID_CHARACTERS,
        )

        private fun accessibilityNodeIDArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_NODE_ID,
            "Node ID from the same accessibility snapshot.",
            SCHEMA_TYPE_STRING,
            true,
            maximum = DikcizAutomationControlPlane.MAXIMUM_ACCESSIBILITY_NODE_ID_CHARACTERS,
        )

        private fun accessibilityActionArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_ACTION,
            "Supported node action: click, longClick, focus, clearFocus, scrollForward, scrollBackward, setText, or select.",
            SCHEMA_TYPE_STRING,
            true,
            maximum = DikcizAutomationControlPlane.MAXIMUM_ACCESSIBILITY_ACTION_CHARACTERS,
        )

        private fun optionalAccessibilityTextArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_TEXT,
            "Required only for setText. Omit for every other action.",
            SCHEMA_TYPE_STRING,
            false,
            maximum = DikcizAutomationControlPlane.MAXIMUM_TEXT_CHARACTERS,
        )

        private fun pageIDArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_PAGE_ID,
            "Stable Dikciz page ID from dikciz_snapshot.",
            SCHEMA_TYPE_STRING,
            true,
            maximum = DikcizAutomationControlPlane.MAXIMUM_SEMANTIC_ID_CHARACTERS,
        )

        private fun scriptIDArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_SCRIPT_ID,
            "Stable configured Dikciz script ID with a manual subscription.",
            SCHEMA_TYPE_STRING,
            true,
            maximum = DikcizAutomationControlPlane.MAXIMUM_SEMANTIC_ID_CHARACTERS,
        )

        private fun sourceWidgetAddressArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_SOURCE_WIDGET_ADDRESS,
            "Stable source HTML widget address in 1H2V-widget-id form.",
            SCHEMA_TYPE_STRING,
            true,
            maximum = DikcizAutomationControlPlane.MAXIMUM_SEMANTIC_ID_CHARACTERS,
        )

        private fun actionsArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_ACTIONS,
            "Non-empty typed action array shared by Lua and HTML widgets.",
            SCHEMA_TYPE_ARRAY,
            true,
        )

        private fun widgetAddressArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_WIDGET_ADDRESS,
            "Stable Dikciz widget address in 1H2V-widget-id form.",
            SCHEMA_TYPE_STRING,
            true,
            maximum = DikcizAutomationControlPlane.MAXIMUM_SEMANTIC_ID_CHARACTERS,
        )

        private fun gridColumnsArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_COLUMNS,
            "Grid columns for every page.",
            SCHEMA_TYPE_INTEGER,
            true,
            minimum = DikcizNativeGrid.MINIMUM_COLUMNS,
            maximum = DikcizNativeGrid.MAXIMUM_COLUMNS,
        )

        private fun gridRowsArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_ROWS,
            "Grid rows for every page.",
            SCHEMA_TYPE_INTEGER,
            true,
            minimum = DikcizNativeGrid.MINIMUM_ROWS,
            maximum = DikcizNativeGrid.MAXIMUM_ROWS,
        )

        private fun gridGapArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_GAP_DP,
            "Gap between cells in density pixels.",
            SCHEMA_TYPE_INTEGER,
            true,
            minimum = DikcizNativeGrid.MINIMUM_GAP_DP,
            maximum = DikcizNativeGrid.MAXIMUM_GAP_DP,
        )

        private fun gridOuterPaddingArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_OUTER_PADDING_DP,
            "Padding between the page edge and the grid in density pixels.",
            SCHEMA_TYPE_INTEGER,
            true,
            minimum = DikcizNativeGrid.MINIMUM_OUTER_PADDING_DP,
            maximum = DikcizNativeGrid.MAXIMUM_OUTER_PADDING_DP,
        )

        private fun widgetTypeArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_WIDGET_TYPE,
            "Item type to create: html, appGroup, or scriptDashboard.",
            SCHEMA_TYPE_STRING,
            true,
            maximum = DikcizAutomationControlPlane.MAXIMUM_SEMANTIC_ID_CHARACTERS,
        )

        private fun cellArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_CELL,
            "Grid rectangle with column, row, columnSpan, and rowSpan.",
            SCHEMA_TYPE_OBJECT,
            true,
        )

        private fun scrollDeltaArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_DELTA_Y,
            "Vertical pixel delta. Positive moves down.",
            SCHEMA_TYPE_INTEGER,
            true,
            DikcizAutomationControlPlane.MINIMUM_SCROLL_DELTA,
            DikcizAutomationControlPlane.MAXIMUM_SCROLL_DELTA,
        )

        private fun textArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_TEXT,
            "New editable input or text widget value.",
            SCHEMA_TYPE_STRING,
            true,
            maximum = DikcizAutomationControlPlane.MAXIMUM_TEXT_CHARACTERS,
        )

        private fun configArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_CONFIG,
            "Complete Dikciz configuration document.",
            SCHEMA_TYPE_OBJECT,
            true,
        )

        private fun timeoutArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_TIMEOUT_MILLISECONDS,
            "Maximum time to wait for the semantic ID, in milliseconds.",
            SCHEMA_TYPE_INTEGER,
            true,
            DikcizAutomationControlPlane.MINIMUM_WAIT_TIMEOUT_MILLISECONDS,
            DikcizAutomationControlPlane.MAXIMUM_WAIT_TIMEOUT_MILLISECONDS,
        )

        private fun packageNameArgument(): ToolArgument = ToolArgument(
            DikcizDeviceInspection.KEY_PACKAGE_NAME,
            "Installed Android package name.",
            SCHEMA_TYPE_STRING,
            true,
            maximumLength = DikcizDeviceInspection.MAXIMUM_PACKAGE_NAME_CHARACTERS,
        )

        private fun shellCommandArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_COMMAND,
            "Command run by Dikciz. Root is requested by default.",
            SCHEMA_TYPE_STRING,
            true,
            maximum = DikcizAutomationControlPlane.MAXIMUM_SHELL_COMMAND_CHARACTERS,
        )

        private fun shellTimeoutArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_TIMEOUT_MILLISECONDS,
            "Command timeout in milliseconds. Defaults to 30000.",
            SCHEMA_TYPE_INTEGER,
            false,
            1,
            DikcizPrivilegedShell.MAXIMUM_SHELL_TIMEOUT_MILLISECONDS,
            defaultValue = DikcizPrivilegedShell.DEFAULT_SHELL_TIMEOUT_MILLISECONDS.toInt(),
        )

        private fun shellOutputArgument(): ToolArgument = ToolArgument(
            DikcizPrivilegedShell.KEY_MAX_OUTPUT_BYTES,
            "Maximum combined stdout and stderr bytes. Defaults to 65536; maximum 1048576.",
            SCHEMA_TYPE_INTEGER,
            false,
            1,
            DikcizPrivilegedShell.MAXIMUM_SHELL_OUTPUT_BYTES,
            defaultValue = DikcizPrivilegedShell.DEFAULT_SHELL_OUTPUT_BYTES,
        )

        private fun rootArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_ROOT,
            "Run through su. Defaults to true. False runs as the launcher app user.",
            SCHEMA_TYPE_BOOLEAN,
            false,
        )

        private fun intentTypeArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_INTENT_TYPE,
            "Intent dispatch mode: activity or broadcast.",
            SCHEMA_TYPE_STRING,
            true,
            maximum = DikcizAutomationControlPlane.MAXIMUM_INTENT_TYPE_CHARACTERS,
        )

        private fun intentDataArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_INTENT_DATA,
            "Optional absolute Android intent data URI, such as an HTTPS browser destination.",
            SCHEMA_TYPE_STRING,
            false,
            maximum = DikcizAutomationControlPlane.MAXIMUM_INTENT_ACTION_CHARACTERS,
        )

        private fun actionArgument(): ToolArgument = ToolArgument(
            DikcizAutomationControlPlane.KEY_ACTION,
            "Android intent action.",
            SCHEMA_TYPE_STRING,
            true,
            maximum = DikcizAutomationControlPlane.MAXIMUM_INTENT_ACTION_CHARACTERS,
        )
    }
}
