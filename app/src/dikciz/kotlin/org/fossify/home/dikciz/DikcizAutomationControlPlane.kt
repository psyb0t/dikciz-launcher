package org.fossify.home.dikciz

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONException
import org.json.JSONObject

internal interface DikcizAutomationTarget {
    fun snapshot(): JSONObject

    fun requiresMainThread(command: DikcizAutomationCommand): Boolean

    fun execute(command: DikcizAutomationCommand): JSONObject
}

internal data class DikcizAutomationCommand(
    val type: String,
    val request: JSONObject,
)

internal class DikcizAutomationRequestException(
    val code: String,
    message: String,
) : IllegalArgumentException(message)

internal class DikcizAutomationControlPlane(
    private val mainHandler: Handler,
    private val target: DikcizAutomationTarget,
    private val logger: DikcizLogger,
    private val remoteAccessAuth: DikcizRemoteAccessAuth,
) {
    private val clients = ConcurrentHashMap.newKeySet<ClientConnection>()
    private val revision = AtomicLong()
    private val isRunning = AtomicBoolean()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var workerPool: ExecutorService? = null

    @Synchronized
    fun start(): Boolean {
        if (!isRunning.compareAndSet(false, true)) {
            return true
        }
        try {
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName(LOOPBACK_ADDRESS), CONTROL_PORT))
            }
            workerPool = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, THREAD_NAME_PREFIX + UUID.randomUUID()).apply { isDaemon = true }
            }
            workerPool?.execute(::acceptClients)
            logger.info(
                EVENT_CONTROL_PLANE_STARTED,
                mapOf(FIELD_BIND_ADDRESS to LOOPBACK_ADDRESS, FIELD_PORT to CONTROL_PORT),
            )
            return true
        } catch (exception: Exception) {
            isRunning.set(false)
            serverSocket?.close()
            serverSocket = null
            workerPool?.shutdownNow()
            workerPool = null
            logger.error(
                EVENT_CONTROL_PLANE_START_FAILED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
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
        clients.forEach { client -> client.close() }
        clients.clear()
        workerPool?.shutdownNow()
        workerPool = null
        logger.info(EVENT_CONTROL_PLANE_STOPPED)
    }

    fun publish(event: String, fields: Map<String, Any> = emptyMap()) {
        if (clients.isEmpty()) {
            return
        }
        val message = JSONObject()
            .put(KEY_TYPE, TYPE_EVENT)
            .put(KEY_EVENT, event)
            .put(KEY_REVISION, revision.incrementAndGet())
            .put(KEY_FIELDS, JSONObject(fields))
            .toString()
        clients.forEach { client ->
            if (!client.sendText(message)) {
                logger.warn(EVENT_CLIENT_BACKPRESSURE, mapOf(FIELD_CLIENT_ID to client.id))
            }
        }
    }

    private fun acceptClients() {
        while (isRunning.get()) {
            val socket = try {
                serverSocket?.accept() ?: return
            } catch (exception: Exception) {
                if (isRunning.get()) {
                    logger.warn(
                        EVENT_ACCEPT_FAILED,
                        mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
                    )
                }
                return
            }
            if (clients.size >= MAXIMUM_CLIENT_COUNT) {
                socket.close()
                logger.warn(EVENT_CLIENT_REJECTED, mapOf(FIELD_REASON to REASON_CLIENT_LIMIT))
                continue
            }
            workerPool?.execute { handleClient(socket) } ?: socket.close()
        }
    }

    private fun handleClient(socket: Socket) {
        val client = ClientConnection(socket, remoteAccessAuth)
        try {
            client.performHandshake()
            clients.add(client)
            client.startWriter(workerPool ?: return)
            logger.info(EVENT_CLIENT_CONNECTED, mapOf(FIELD_CLIENT_ID to client.id))
            receiveClientMessages(client)
        } catch (exception: DikcizAutomationRequestException) {
            logger.warn(
                EVENT_CLIENT_REJECTED,
                mapOf(
                    FIELD_REASON to exception.code,
                    FIELD_CLIENT_ID to client.id,
                    FIELD_ERROR_MESSAGE to exception.message.orEmpty(),
                ),
            )
        } catch (exception: Exception) {
            if (isRunning.get()) {
                logger.warn(
                    EVENT_CLIENT_DISCONNECTED,
                    mapOf(
                        FIELD_CLIENT_ID to client.id,
                        FIELD_ERROR_CLASS to exception::class.java.simpleName,
                    ),
                )
            }
        } finally {
            clients.remove(client)
            client.close()
        }
    }

    private fun receiveClientMessages(client: ClientConnection) {
        while (isRunning.get() && client.isOpen()) {
            when (val frame = client.readFrame()) {
                null -> return
                is ClientConnection.IncomingFrame.Close -> return
                is ClientConnection.IncomingFrame.Ping -> {
                    client.sendControl(ClientConnection.OPCODE_PONG, frame.payload)
                }

                is ClientConnection.IncomingFrame.Text -> handleTextMessage(client, frame.value)
            }
        }
    }

    private fun handleTextMessage(client: ClientConnection, message: String) {
        val request = try {
            JSONObject(message)
        } catch (exception: JSONException) {
            client.sendText(errorResponse(null, ERROR_VALIDATION_FAILED, MESSAGE_INVALID_JSON))
            return
        }
        val requestID = request.opt(KEY_REQUEST_ID) as? String
        if (requestID == null || !isValidRequestID(requestID)) {
            client.sendText(errorResponse(null, ERROR_VALIDATION_FAILED, MESSAGE_INVALID_REQUEST_ID))
            return
        }
        if (!remoteAccessAuth.acceptsAuthorization(client.authorization)) {
            logger.warn(
                EVENT_COMMAND_REJECTED,
                mapOf(FIELD_CLIENT_ID to client.id, FIELD_REASON to ERROR_REMOTE_AUTH_REQUIRED),
            )
            client.sendText(errorResponse(requestID, ERROR_REMOTE_AUTH_REQUIRED, MESSAGE_REMOTE_AUTH_REQUIRED))
            return
        }
        val isEstablishedCommand = client.hasCompletedHello
        try {
            logger.debug(
                EVENT_COMMAND_RECEIVED,
                mapOf(FIELD_CLIENT_ID to client.id, FIELD_REQUEST_TYPE to request.optString(KEY_TYPE)),
            )
            val response = if (!client.hasCompletedHello) {
                handleHello(client, request, requestID)
            } else {
                handleCommand(request, requestID, client.authorization)
            }
            client.sendText(response)
            if (isEstablishedCommand) {
                publishCommandCompletion(requestID, request, OUTCOME_SUCCEEDED)
            }
            logger.debug(
                EVENT_COMMAND_COMPLETED,
                mapOf(FIELD_CLIENT_ID to client.id, FIELD_REQUEST_TYPE to request.optString(KEY_TYPE)),
            )
        } catch (exception: DikcizAutomationRequestException) {
            logger.warn(
                EVENT_COMMAND_REJECTED,
                mapOf(
                    FIELD_CLIENT_ID to client.id,
                    FIELD_REASON to exception.code,
                    FIELD_REQUEST_TYPE to request.optString(KEY_TYPE),
                ),
            )
            client.sendText(errorResponse(requestID, exception.code, exception.message.orEmpty()))
            if (isEstablishedCommand) {
                publishCommandCompletion(requestID, request, OUTCOME_REJECTED, exception.code)
            }
        } catch (exception: Exception) {
            logger.error(
                EVENT_COMMAND_FAILED,
                mapOf(
                    FIELD_ERROR_CLASS to exception::class.java.simpleName,
                    FIELD_REQUEST_TYPE to request.optString(KEY_TYPE),
                ),
            )
            client.sendText(errorResponse(requestID, ERROR_INTERNAL, MESSAGE_INTERNAL_ERROR))
            if (isEstablishedCommand) {
                publishCommandCompletion(requestID, request, OUTCOME_FAILED, ERROR_INTERNAL)
            }
        }
    }

    private fun publishCommandCompletion(
        requestID: String,
        request: JSONObject,
        outcome: String,
        code: String? = null,
    ) {
        val fields = mutableMapOf<String, Any>(
            KEY_REQUEST_ID to requestID,
            EVENT_FIELD_COMMAND_TYPE to publicCommandType(request),
            EVENT_FIELD_OUTCOME to outcome,
        )
        if (code != null) {
            fields[KEY_CODE] = code
        }
        publish(EVENT_COMMAND_COMPLETED, fields)
    }

    private fun publicCommandType(request: JSONObject): String {
        val commandType = request.opt(KEY_TYPE) as? String
        if (commandType == null || !COMMAND_KEYS.containsKey(commandType)) {
            return VALUE_UNKNOWN_COMMAND_TYPE
        }
        return commandType
    }

    private fun handleHello(
        client: ClientConnection,
        request: JSONObject,
        requestID: String,
    ): String {
        requireExactKeys(request, HELLO_KEYS)
        if (request.requireString(KEY_TYPE) != TYPE_HELLO) {
            throw DikcizAutomationRequestException(ERROR_HELLO_REQUIRED, MESSAGE_HELLO_REQUIRED)
        }
        if (request.requireInt(KEY_PROTOCOL_VERSION) != PROTOCOL_VERSION) {
            throw DikcizAutomationRequestException(ERROR_PROTOCOL_MISMATCH, MESSAGE_PROTOCOL_MISMATCH)
        }
        client.hasCompletedHello = true
        logger.info(EVENT_HELLO_COMPLETED, mapOf(FIELD_CLIENT_ID to client.id))
        return JSONObject()
            .put(KEY_TYPE, TYPE_HELLO)
            .put(KEY_REQUEST_ID, requestID)
            .put(KEY_PROTOCOL_VERSION, PROTOCOL_VERSION)
            .put(KEY_REVISION, revision.get())
            .put(KEY_SNAPSHOT, callOnMain { target.snapshot() })
            .toString()
    }

    internal fun executeLocalCommand(request: JSONObject): String {
        val requestID = request.opt(KEY_REQUEST_ID) as? String
        if (requestID == null || !isValidRequestID(requestID)) {
            return errorResponse(null, ERROR_VALIDATION_FAILED, MESSAGE_INVALID_REQUEST_ID)
        }
        val fields = mapOf(
            KEY_REQUEST_ID to requestID,
            FIELD_REQUEST_TYPE to publicCommandType(request),
        )
        logger.debug(EVENT_COMMAND_RECEIVED, fields)
        return try {
            val response = handleCommand(request, requestID)
            publishCommandCompletion(requestID, request, OUTCOME_SUCCEEDED)
            logger.info(EVENT_COMMAND_COMPLETED, fields)
            response
        } catch (exception: DikcizAutomationRequestException) {
            logger.warn(EVENT_COMMAND_REJECTED, fields + (FIELD_REASON to exception.code))
            publishCommandCompletion(requestID, request, OUTCOME_REJECTED, exception.code)
            errorResponse(requestID, exception.code, exception.message.orEmpty())
        } catch (exception: Exception) {
            logger.error(EVENT_COMMAND_FAILED, fields + (FIELD_ERROR_CLASS to exception.javaClass.simpleName))
            publishCommandCompletion(requestID, request, OUTCOME_FAILED, ERROR_INTERNAL)
            errorResponse(requestID, ERROR_INTERNAL, MESSAGE_INTERNAL_ERROR)
        }
    }

    private fun handleCommand(
        request: JSONObject,
        requestID: String,
        authorization: String? = null,
    ): String {
        val commandType = request.requireString(KEY_TYPE)
        val expectedKeys = COMMAND_KEYS[commandType]
            ?: throw DikcizAutomationRequestException(ERROR_UNKNOWN_COMMAND, MESSAGE_UNKNOWN_COMMAND)
        val requiredKeys = COMMAND_REQUIRED_KEYS[commandType]
        if (requiredKeys != null) {
            requireAllowedKeys(request, expectedKeys, requiredKeys)
        } else {
            requireExactKeys(request, expectedKeys)
        }
        validateCommand(request, commandType)
        val result = if (commandType in REMOTE_AUTH_COMMAND_TYPES) {
            try {
                remoteAccessAuth.executeRemoteCommand(commandType, authorization)
            } catch (exception: DikcizRemoteAccessAuthException) {
                throw DikcizAutomationRequestException(exception.code, exception.message.orEmpty())
            }
        } else {
            dispatchCommand(DikcizAutomationCommand(commandType, request))
        }
        return JSONObject()
            .put(KEY_TYPE, TYPE_RESULT)
            .put(KEY_REQUEST_ID, requestID)
            .put(KEY_REVISION, revision.get())
            .put(KEY_RESULT, result)
            .toString()
    }

    private fun dispatchCommand(command: DikcizAutomationCommand): JSONObject {
        if (command.type == TYPE_WAIT_FOR) {
            return waitForAutomationView(command)
        }
        if (!target.requiresMainThread(command)) {
            return target.execute(command)
        }
        return callOnMain { target.execute(command) }
    }

    private fun waitForAutomationView(command: DikcizAutomationCommand): JSONObject {
        val semanticID = command.request.getString(KEY_SEMANTIC_ID)
        val timeoutMilliseconds = command.request.getInt(KEY_TIMEOUT_MILLISECONDS)
        val deadlineMilliseconds = SystemClock.elapsedRealtime() + timeoutMilliseconds
        var snapshot: JSONObject
        while (true) {
            snapshot = callOnMain { target.snapshot() }
            val node = snapshot.optJSONArray(KEY_NODES)
                ?.let { nodes ->
                    (0 until nodes.length())
                        .asSequence()
                        .mapNotNull(nodes::optJSONObject)
                        .firstOrNull { candidate -> candidate.optString(KEY_SEMANTIC_ID) == semanticID }
                }
            if (node != null) {
                return JSONObject()
                    .put(KEY_FOUND, true)
                    .put(KEY_NODE, node)
                    .put(KEY_SNAPSHOT, snapshot)
            }
            val remainingMilliseconds = deadlineMilliseconds - SystemClock.elapsedRealtime()
            if (remainingMilliseconds <= 0) {
                return JSONObject()
                    .put(KEY_FOUND, false)
                    .put(KEY_SEMANTIC_ID, semanticID)
                    .put(KEY_SNAPSHOT, snapshot)
            }
            try {
                Thread.sleep(minOf(WAIT_POLL_INTERVAL_MILLISECONDS, remainingMilliseconds))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw DikcizAutomationRequestException(ERROR_TIMEOUT, MESSAGE_WAIT_INTERRUPTED)
            }
        }
    }

    private fun validateCommand(request: JSONObject, commandType: String) {
        when (commandType) {
            TYPE_FIND,
            TYPE_TAP,
            TYPE_LONG_PRESS,
            TYPE_LAUNCH_APP,
            -> request.requireBoundedString(KEY_SEMANTIC_ID, MAXIMUM_SEMANTIC_ID_CHARACTERS)

            TYPE_SELECT_PAGE -> request.requireBoundedString(KEY_PAGE_ID, MAXIMUM_SEMANTIC_ID_CHARACTERS)
            TYPE_ADD_WIDGET ->
                request.requireBoundedString(KEY_WIDGET_TYPE, MAXIMUM_SEMANTIC_ID_CHARACTERS)

            TYPE_GRID_SET -> {
                request.requireBoundedInt(
                    KEY_COLUMNS,
                    DikcizNativeGrid.MINIMUM_COLUMNS,
                    DikcizNativeGrid.MAXIMUM_COLUMNS,
                )
                request.requireBoundedInt(
                    KEY_ROWS,
                    DikcizNativeGrid.MINIMUM_ROWS,
                    DikcizNativeGrid.MAXIMUM_ROWS,
                )
                request.requireBoundedInt(
                    KEY_GAP_DP,
                    DikcizNativeGrid.MINIMUM_GAP_DP,
                    DikcizNativeGrid.MAXIMUM_GAP_DP,
                )
                request.requireBoundedInt(
                    KEY_OUTER_PADDING_DP,
                    DikcizNativeGrid.MINIMUM_OUTER_PADDING_DP,
                    DikcizNativeGrid.MAXIMUM_OUTER_PADDING_DP,
                )
            }

            TYPE_WIDGET_MOVE, TYPE_WIDGET_RESIZE -> {
                request.requireBoundedString(KEY_WIDGET_ADDRESS, MAXIMUM_SEMANTIC_ID_CHARACTERS)
                request.requireGridCell()
            }
            TYPE_SCROLL_BY -> request.requireBoundedInt(
                KEY_DELTA_Y,
                MINIMUM_SCROLL_DELTA,
                MAXIMUM_SCROLL_DELTA,
            )

            TYPE_SET_TEXT -> {
                request.requireBoundedString(KEY_SEMANTIC_ID, MAXIMUM_SEMANTIC_ID_CHARACTERS)
                request.requireBoundedString(KEY_TEXT, MAXIMUM_TEXT_CHARACTERS)
            }

            TYPE_CONFIG_REPLACE,
            TYPE_CONFIG_SEED,
            -> request.requireObject(KEY_CONFIG)

            TYPE_WAIT_FOR -> {
                request.requireBoundedString(KEY_SEMANTIC_ID, MAXIMUM_SEMANTIC_ID_CHARACTERS)
                request.requireBoundedInt(
                    KEY_TIMEOUT_MILLISECONDS,
                    MINIMUM_WAIT_TIMEOUT_MILLISECONDS,
                    MAXIMUM_WAIT_TIMEOUT_MILLISECONDS,
                )
            }

            TYPE_SHELL -> {
                request.requireBoundedString(KEY_COMMAND, MAXIMUM_SHELL_COMMAND_CHARACTERS)
                if (request.has(KEY_ROOT) && request.opt(KEY_ROOT) !is Boolean) {
                    throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_INVALID_ROOT_REQUEST)
                }
            }
            TYPE_AUTOMATION_TRIGGER -> {
                request.requireBoundedString(KEY_SCRIPT_ID, MAXIMUM_SEMANTIC_ID_CHARACTERS)
            }
            TYPE_AUTOMATION_DISPATCH -> {
                request.requireBoundedString(KEY_SOURCE_WIDGET_ADDRESS, MAXIMUM_SEMANTIC_ID_CHARACTERS)
                request.requireArray(KEY_ACTIONS)
            }
            TYPE_ACCESSIBILITY_ACTION -> {
                request.requireBoundedString(KEY_SNAPSHOT_ID, MAXIMUM_ACCESSIBILITY_SNAPSHOT_ID_CHARACTERS)
                request.requireBoundedString(KEY_NODE_ID, MAXIMUM_ACCESSIBILITY_NODE_ID_CHARACTERS)
                request.requireBoundedString(KEY_ACTION, MAXIMUM_ACCESSIBILITY_ACTION_CHARACTERS)
                if (request.has(KEY_TEXT)) {
                    request.requireBoundedString(KEY_TEXT, MAXIMUM_TEXT_CHARACTERS)
                }
            }
            TYPE_APP_CATALOGUE -> {
                if (request.has(KEY_QUERY)) {
                    request.requireBoundedString(KEY_QUERY, MAXIMUM_APP_QUERY_CHARACTERS)
                }
            }
            TYPE_APP_ACTION -> {
                val appAction = request.requireBoundedString(KEY_ACTION, MAXIMUM_APP_ACTION_CHARACTERS)
                if (appAction !in SUPPORTED_APP_ACTIONS) {
                    throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_INVALID_APP_ACTION)
                }
                request.requireBoundedString(KEY_COMPONENT, MAXIMUM_COMPONENT_CHARACTERS)
            }
            TYPE_HOME_GET -> Unit
            TYPE_WIDGET_GET -> request.requireBoundedString(
                KEY_WIDGET_ADDRESS,
                MAXIMUM_SEMANTIC_ID_CHARACTERS,
            )
            TYPE_HTML_WIDGET_RENDERER_CRASH -> request.requireBoundedString(
                KEY_WIDGET_ADDRESS,
                MAXIMUM_SEMANTIC_ID_CHARACTERS,
            )
            TYPE_INTENT -> {
                request.requireBoundedString(KEY_ACTION, MAXIMUM_INTENT_ACTION_CHARACTERS)
                val intentType = request.requireBoundedString(KEY_INTENT_TYPE, MAXIMUM_INTENT_TYPE_CHARACTERS)
                if (intentType !in SUPPORTED_INTENT_TYPES) {
                    throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_INVALID_INTENT_TYPE)
                }
            }
        }
    }

    private fun errorResponse(requestID: String?, code: String, message: String): String {
        return JSONObject()
            .put(KEY_TYPE, TYPE_ERROR)
            .put(KEY_REQUEST_ID, requestID ?: JSONObject.NULL)
            .put(KEY_CODE, code)
            .put(KEY_MESSAGE, message)
            .put(KEY_REVISION, revision.get())
            .toString()
    }

    private fun <T> callOnMain(action: () -> T): T {
        if (Looper.myLooper() == mainHandler.looper) {
            return action()
        }
        val completion = CountDownLatch(1)
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
        if (!completion.await(UI_THREAD_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            throw DikcizAutomationRequestException(ERROR_TIMEOUT, MESSAGE_UI_THREAD_TIMEOUT)
        }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun isValidRequestID(value: String): Boolean {
        return try {
            UUID.fromString(value).toString() == value.lowercase()
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun requireExactKeys(value: JSONObject, expectedKeys: Set<String>) {
        val iterator = value.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key !in expectedKeys) {
                throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_UNEXPECTED_FIELD)
            }
        }
        if (expectedKeys.any { key -> !value.has(key) }) {
            throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_MISSING_FIELD)
        }
    }

    private fun requireAllowedKeys(
        value: JSONObject,
        allowedKeys: Set<String>,
        requiredKeys: Set<String>,
    ) {
        val iterator = value.keys()
        while (iterator.hasNext()) {
            if (iterator.next() !in allowedKeys) {
                throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_UNEXPECTED_FIELD)
            }
        }
        if (requiredKeys.any { key -> !value.has(key) }) {
            throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_MISSING_FIELD)
        }
    }

    private class ClientConnection(
        private val socket: Socket,
        private val remoteAccessAuth: DikcizRemoteAccessAuth,
    ) {
        val id = UUID.randomUUID().toString()
        var hasCompletedHello = false
        var authorization: String? = null
            private set
        private val input = BufferedInputStream(socket.getInputStream())
        private val output = BufferedOutputStream(socket.getOutputStream())
        private val isClosed = AtomicBoolean()
        private val outgoingFrames = ArrayBlockingQueue<OutgoingFrame>(MAXIMUM_OUTGOING_FRAME_COUNT)

        fun isOpen(): Boolean = !isClosed.get() && !socket.isClosed

        fun performHandshake() {
            socket.soTimeout = HANDSHAKE_TIMEOUT_MILLISECONDS
            val request = readHttpRequest()
            val parsedHeaders = parseHeaders(request)
            val headers = parsedHeaders.values
            validateHandshake(request, headers)
            if (parsedHeaders.hasDuplicateAuthorization ||
                !remoteAccessAuth.acceptsAuthorization(headers[HEADER_AUTHORIZATION])
            ) {
                writeUnauthorizedResponse()
                throw DikcizAutomationRequestException(ERROR_REMOTE_AUTH_REQUIRED, MESSAGE_REMOTE_AUTH_REQUIRED)
            }
            authorization = headers[HEADER_AUTHORIZATION]
            val key = headers[HEADER_WEBSOCKET_KEY]
                ?: throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_MISSING_FIELD)
            val accept = createAcceptKey(key)
            val response = buildString {
                append(HTTP_SWITCHING_PROTOCOLS)
                append(HTTP_LINE_SEPARATOR)
                append(HEADER_CONNECTION_RESPONSE)
                append(HTTP_LINE_SEPARATOR)
                append(HEADER_UPGRADE_RESPONSE)
                append(HTTP_LINE_SEPARATOR)
                append(HEADER_WEBSOCKET_ACCEPT)
                append(COLON_SPACE)
                append(accept)
                append(HTTP_LINE_SEPARATOR)
                append(HTTP_LINE_SEPARATOR)
            }
            output.write(response.toByteArray(StandardCharsets.US_ASCII))
            output.flush()
            socket.soTimeout = 0
        }

        fun startWriter(workerPool: ExecutorService) {
            workerPool.execute writer@{
                while (isOpen()) {
                    val frame = try {
                        outgoingFrames.poll(OUTGOING_FRAME_WAIT_SECONDS, TimeUnit.SECONDS)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return@writer
                    } ?: continue
                    try {
                        writeFrame(frame.opcode, frame.payload)
                    } catch (_: Exception) {
                        close()
                    }
                }
            }
        }

        fun sendText(value: String): Boolean {
            return enqueue(OutgoingFrame(OPCODE_TEXT, value.toByteArray(StandardCharsets.UTF_8)))
        }

        fun sendControl(opcode: Int, payload: ByteArray): Boolean {
            if (payload.size > MAXIMUM_CONTROL_PAYLOAD_BYTES) {
                return false
            }
            return enqueue(OutgoingFrame(opcode, payload))
        }

        fun readFrame(): IncomingFrame? {
            val firstByte = input.read()
            if (firstByte < 0) {
                return null
            }
            val secondByte = input.read()
            if (secondByte < 0) {
                throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_INVALID_FRAME)
            }
            val isFinal = firstByte and FRAME_FINAL_BIT != 0
            val opcode = firstByte and FRAME_OPCODE_MASK
            val isMasked = secondByte and FRAME_MASK_BIT != 0
            if (!isFinal || !isMasked || opcode !in SUPPORTED_INCOMING_OPCODES) {
                throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_INVALID_FRAME)
            }
            val payloadLength = readPayloadLength(secondByte and FRAME_LENGTH_MASK)
            val maskingKey = readExact(MASKING_KEY_BYTES)
            val payload = readExact(payloadLength)
            payload.indices.forEach { index ->
                payload[index] = (payload[index].toInt() xor maskingKey[index % MASKING_KEY_BYTES].toInt()).toByte()
            }
            return when (opcode) {
                OPCODE_CLOSE -> IncomingFrame.Close
                OPCODE_PING -> IncomingFrame.Ping(payload)
                OPCODE_TEXT -> IncomingFrame.Text(String(payload, StandardCharsets.UTF_8))
                else -> throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_INVALID_FRAME)
            }
        }

        fun close() {
            if (!isClosed.compareAndSet(false, true)) {
                return
            }
            socket.close()
            outgoingFrames.clear()
            authorization = null
        }

        private fun enqueue(frame: OutgoingFrame): Boolean {
            if (!isOpen()) {
                return false
            }
            if (frame.payload.size > MAXIMUM_OUTGOING_PAYLOAD_BYTES || !outgoingFrames.offer(frame)) {
                close()
                return false
            }
            return true
        }

        private fun readHttpRequest(): String {
            val bytes = ByteArrayOutputStream()
            var matchingLineEndingBytes = 0
            repeat(MAXIMUM_HANDSHAKE_BYTES) {
                val value = input.read()
                if (value < 0) {
                    throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_INVALID_HANDSHAKE)
                }
                bytes.write(value)
                matchingLineEndingBytes = when {
                    matchingLineEndingBytes == 0 && value == CARRIAGE_RETURN -> 1
                    matchingLineEndingBytes == 1 && value == LINE_FEED -> 2
                    matchingLineEndingBytes == 2 && value == CARRIAGE_RETURN -> 3
                    matchingLineEndingBytes == 3 && value == LINE_FEED -> 4
                    value == CARRIAGE_RETURN -> 1
                    else -> 0
                }
                if (matchingLineEndingBytes == HTTP_HEADER_ENDING_BYTES) {
                    return String(bytes.toByteArray(), StandardCharsets.US_ASCII)
                }
            }
            throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_INVALID_HANDSHAKE)
        }

        private fun parseHeaders(request: String): ParsedHeaders {
            val lines = request.removeSuffix(HTTP_HEADER_ENDING).split(HTTP_LINE_SEPARATOR)
            if (lines.firstOrNull() != HTTP_REQUEST_LINE) {
                throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_INVALID_HANDSHAKE)
            }
            val headers = mutableMapOf<String, String>()
            var hasDuplicateAuthorization = false
            lines.drop(FIRST_HEADER_LINE_INDEX).forEach { line ->
                val separator = line.indexOf(COLON)
                if (separator <= 0) {
                    throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_INVALID_HANDSHAKE)
                }
                val name = line.substring(0, separator).lowercase()
                val value = line.substring(separator + 1).trim()
                if (headers.put(name, value) != null) {
                    if (name == HEADER_AUTHORIZATION) {
                        hasDuplicateAuthorization = true
                    } else {
                        throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_INVALID_HANDSHAKE)
                    }
                }
            }
            return ParsedHeaders(headers, hasDuplicateAuthorization)
        }

        private fun writeUnauthorizedResponse() {
            val response = buildString {
                append(HTTP_UNAUTHORIZED)
                append(HTTP_LINE_SEPARATOR)
                append(HEADER_WWW_AUTHENTICATE_RESPONSE)
                append(HTTP_LINE_SEPARATOR)
                append(HEADER_CONNECTION_CLOSE_RESPONSE)
                append(HTTP_LINE_SEPARATOR)
                append(HTTP_LINE_SEPARATOR)
            }
            output.write(response.toByteArray(StandardCharsets.US_ASCII))
            output.flush()
        }

        private fun validateHandshake(request: String, headers: Map<String, String>) {
            if (!request.startsWith("$HTTP_REQUEST_LINE$HTTP_LINE_SEPARATOR")) {
                throw DikcizAutomationRequestException(
                    ERROR_VALIDATION_FAILED,
                    MESSAGE_INVALID_HANDSHAKE_REQUEST_LINE,
                )
            }
            val key = headers[HEADER_WEBSOCKET_KEY]
            val version = headers[HEADER_WEBSOCKET_VERSION]
            val connection = headers[HEADER_CONNECTION]
            val upgrade = headers[HEADER_UPGRADE]
            if (key == null || !isValidWebSocketKey(key)) {
                throw DikcizAutomationRequestException(
                    ERROR_VALIDATION_FAILED,
                    MESSAGE_INVALID_HANDSHAKE_KEY,
                )
            }
            if (version != WEBSOCKET_VERSION) {
                throw DikcizAutomationRequestException(
                    ERROR_VALIDATION_FAILED,
                    MESSAGE_INVALID_HANDSHAKE_VERSION,
                )
            }
            if (!connection.orEmpty().contains(CONNECTION_UPGRADE_VALUE, ignoreCase = true)) {
                throw DikcizAutomationRequestException(
                    ERROR_VALIDATION_FAILED,
                    MESSAGE_INVALID_HANDSHAKE_CONNECTION,
                )
            }
            if (!upgrade.equals(WEBSOCKET_UPGRADE_VALUE, ignoreCase = true)) {
                throw DikcizAutomationRequestException(
                    ERROR_VALIDATION_FAILED,
                    MESSAGE_INVALID_HANDSHAKE_UPGRADE,
                )
            }
        }

        private fun isValidWebSocketKey(value: String): Boolean {
            return try {
                Base64.getDecoder().decode(value).size == WEBSOCKET_KEY_BYTES
            } catch (_: IllegalArgumentException) {
                false
            }
        }

        private fun createAcceptKey(value: String): String {
            val digest = MessageDigest.getInstance(SHA_1_ALGORITHM)
                .digest("$value$WEBSOCKET_GUID".toByteArray(StandardCharsets.US_ASCII))
            return Base64.getEncoder().encodeToString(digest)
        }

        private fun readPayloadLength(initialLength: Int): Int {
            return when (initialLength) {
                in 0..MAXIMUM_SHORT_PAYLOAD_LENGTH -> initialLength
                EXTENDED_SHORT_PAYLOAD_MARKER -> {
                    val bytes = readExact(EXTENDED_SHORT_PAYLOAD_BYTES)
                    ((bytes[0].toInt() and BYTE_MASK) shl BYTE_BITS) or (bytes[1].toInt() and BYTE_MASK)
                }
                EXTENDED_LONG_PAYLOAD_MARKER -> {
                    val bytes = readExact(EXTENDED_LONG_PAYLOAD_BYTES)
                    var length = 0L
                    bytes.forEach { byte ->
                        length = (length shl BYTE_BITS) or (byte.toLong() and BYTE_MASK.toLong())
                    }
                    if (length > MAXIMUM_INCOMING_PAYLOAD_BYTES) {
                        throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_FRAME_TOO_LARGE)
                    }
                    length.toInt()
                }
                else -> throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_INVALID_FRAME)
            }.also { length ->
                if (length > MAXIMUM_INCOMING_PAYLOAD_BYTES) {
                    throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_FRAME_TOO_LARGE)
                }
            }
        }

        private fun readExact(length: Int): ByteArray {
            val bytes = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val read = input.read(bytes, offset, length - offset)
                if (read < 0) {
                    throw DikcizAutomationRequestException(ERROR_VALIDATION_FAILED, MESSAGE_INVALID_FRAME)
                }
                offset += read
            }
            return bytes
        }

        private fun writeFrame(opcode: Int, payload: ByteArray) {
            output.write(FRAME_FINAL_BIT or opcode)
            when {
                payload.size <= MAXIMUM_SHORT_PAYLOAD_LENGTH -> output.write(payload.size)
                payload.size <= MAXIMUM_EXTENDED_SHORT_PAYLOAD_LENGTH -> {
                    output.write(EXTENDED_SHORT_PAYLOAD_MARKER)
                    output.write(payload.size shr BYTE_BITS)
                    output.write(payload.size and BYTE_MASK)
                }
                else -> {
                    output.write(EXTENDED_LONG_PAYLOAD_MARKER)
                    for (shift in LONG_PAYLOAD_SHIFTS) {
                        output.write(payload.size.toLong().shr(shift).toInt() and BYTE_MASK)
                    }
                }
            }
            output.write(payload)
            output.flush()
        }

        private data class OutgoingFrame(
            val opcode: Int,
            val payload: ByteArray,
        )

        private data class ParsedHeaders(
            val values: Map<String, String>,
            val hasDuplicateAuthorization: Boolean,
        )

        sealed interface IncomingFrame {
            data object Close : IncomingFrame

            data class Ping(
                val payload: ByteArray,
            ) : IncomingFrame

            data class Text(
                val value: String,
            ) : IncomingFrame
        }

        companion object {
            const val BYTE_BITS = 8
            const val BYTE_MASK = 0xFF
            const val CARRIAGE_RETURN = 13
            const val COLON = ':'
            const val COLON_SPACE = ": "
            const val CONNECTION_UPGRADE_VALUE = "upgrade"
            const val ERROR_VALIDATION_FAILED = "validation_failed"
            const val EXTENDED_LONG_PAYLOAD_BYTES = 8
            const val EXTENDED_LONG_PAYLOAD_MARKER = 127
            const val EXTENDED_SHORT_PAYLOAD_BYTES = 2
            const val EXTENDED_SHORT_PAYLOAD_MARKER = 126
            const val FIRST_HEADER_LINE_INDEX = 1
            const val FRAME_FINAL_BIT = 0x80
            const val FRAME_LENGTH_MASK = 0x7F
            const val FRAME_MASK_BIT = 0x80
            const val FRAME_OPCODE_MASK = 0x0F
            const val HANDSHAKE_TIMEOUT_MILLISECONDS = 10_000
            const val HEADER_CONNECTION = "connection"
            const val HEADER_CONNECTION_CLOSE_RESPONSE = "Connection: close"
            const val HEADER_CONNECTION_RESPONSE = "Connection: Upgrade"
            const val HEADER_AUTHORIZATION = "authorization"
            const val HEADER_UPGRADE = "upgrade"
            const val HEADER_UPGRADE_RESPONSE = "Upgrade: websocket"
            const val HEADER_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept"
            const val HEADER_WEBSOCKET_KEY = "sec-websocket-key"
            const val HEADER_WEBSOCKET_VERSION = "sec-websocket-version"
            const val HEADER_WWW_AUTHENTICATE_RESPONSE = "WWW-Authenticate: Bearer realm=\"Dikciz\""
            const val HTTP_HEADER_ENDING = "\r\n\r\n"
            const val HTTP_HEADER_ENDING_BYTES = 4
            const val HTTP_LINE_SEPARATOR = "\r\n"
            const val HTTP_REQUEST_LINE = "GET ${DikcizAutomationControlPlane.CONTROL_PATH} HTTP/1.1"
            const val HTTP_SWITCHING_PROTOCOLS = "HTTP/1.1 101 Switching Protocols"
            const val HTTP_UNAUTHORIZED = "HTTP/1.1 401 Unauthorized"
            const val LINE_FEED = 10
            const val LONG_PAYLOAD_SHIFT_BASE = 56
            const val MASKING_KEY_BYTES = 4
            const val MAXIMUM_CONTROL_PAYLOAD_BYTES = 125
            const val MAXIMUM_EXTENDED_SHORT_PAYLOAD_LENGTH = 65_535
            const val MAXIMUM_HANDSHAKE_BYTES = 8_192
            const val MAXIMUM_INCOMING_PAYLOAD_BYTES = HomeConfigStore.MAXIMUM_CONFIGURATION_DOCUMENT_BYTES
            const val MAXIMUM_OUTGOING_FRAME_COUNT = 64
            const val MAXIMUM_OUTGOING_PAYLOAD_BYTES = HomeConfigStore.MAXIMUM_CONFIGURATION_DOCUMENT_BYTES
            const val MAXIMUM_SHORT_PAYLOAD_LENGTH = 125
            const val MESSAGE_FRAME_TOO_LARGE = "frame exceeds its size limit"
            const val MESSAGE_INVALID_FRAME = "invalid WebSocket frame"
            const val MESSAGE_INVALID_HANDSHAKE = "invalid WebSocket handshake"
            const val MESSAGE_INVALID_HANDSHAKE_CONNECTION = "WebSocket Connection header is invalid"
            const val MESSAGE_INVALID_HANDSHAKE_KEY = "WebSocket key is invalid"
            const val MESSAGE_INVALID_HANDSHAKE_REQUEST_LINE = "WebSocket request line is invalid"
            const val MESSAGE_INVALID_HANDSHAKE_UPGRADE = "WebSocket Upgrade header is invalid"
            const val MESSAGE_INVALID_HANDSHAKE_VERSION = "WebSocket version is invalid"
            const val OPCODE_CLOSE = 0x8
            const val OPCODE_PING = 0x9
            const val OPCODE_PONG = 0xA
            const val OPCODE_TEXT = 0x1
            const val OUTGOING_FRAME_WAIT_SECONDS = 1L
            const val SHA_1_ALGORITHM = "SHA-1"
            const val WEBSOCKET_UPGRADE_VALUE = "websocket"
            const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
            const val WEBSOCKET_KEY_BYTES = 16
            const val WEBSOCKET_VERSION = "13"
            val LONG_PAYLOAD_SHIFTS = IntArray(EXTENDED_LONG_PAYLOAD_BYTES) { index ->
                LONG_PAYLOAD_SHIFT_BASE - index * BYTE_BITS
            }
            val SUPPORTED_INCOMING_OPCODES = setOf(OPCODE_CLOSE, OPCODE_PING, OPCODE_TEXT)
        }
    }

    companion object {
        const val CONTROL_PATH = "/v1/automation"
        const val CONTROL_PORT = 19_001
        const val ERROR_GRID_BOUNDS = "grid_bounds"
        const val ERROR_GRID_COLLISION = "grid_collision"
        const val ERROR_HELLO_REQUIRED = "hello_required"
        const val ERROR_PAGE_FULL = "page_full"
        const val ERROR_INTERNAL = "internal_error"
        const val ERROR_PROTOCOL_MISMATCH = "protocol_mismatch"
        const val ERROR_REMOTE_AUTH_REQUIRED = "remote_auth_required"
        const val ERROR_ROOT_UNAVAILABLE = "root_unavailable"
        const val ERROR_TIMEOUT = "timeout"
        const val ERROR_UNKNOWN_COMMAND = "unknown_command"
        const val ERROR_VALIDATION_FAILED = "validation_failed"
        const val EVENT_ACCEPT_FAILED = "automation_accept_failed"
        const val EVENT_AUTOMATION_EVENT = "automation_event"
        const val EVENT_CLIENT_BACKPRESSURE = "automation_client_backpressure"
        const val EVENT_CLIENT_CONNECTED = "automation_client_connected"
        const val EVENT_CLIENT_DISCONNECTED = "automation_client_disconnected"
        const val EVENT_CLIENT_REJECTED = "automation_client_rejected"
        const val EVENT_COMMAND_COMPLETED = "automation_command_completed"
        const val EVENT_COMMAND_FAILED = "automation_command_failed"
        const val EVENT_COMMAND_RECEIVED = "automation_command_received"
        const val EVENT_COMMAND_REJECTED = "automation_command_rejected"
        const val EVENT_CONTROL_PLANE_STARTED = "automation_control_plane_started"
        const val EVENT_CONTROL_PLANE_START_FAILED = "automation_control_plane_start_failed"
        const val EVENT_CONTROL_PLANE_STOPPED = "automation_control_plane_stopped"
        const val EVENT_HELLO_COMPLETED = "automation_hello_completed"
        const val EVENT_HTML_WIDGET_EVENT = "html_widget_event"
        const val EVENT_FIELD_COMMAND_TYPE = "commandType"
        const val EVENT_FIELD_EVENT = "event"
        const val EVENT_FIELD_OUTCOME = "outcome"
        const val EVENT_FIELD_REASON = "reason"
        const val EVENT_FIELD_SCREEN = "screen"
        const val EVENT_FIELD_SELECTED_PAGE_ID = "selectedPageId"
        const val EVENT_UI_RENDERED = "automation_ui_rendered"
        const val FIELD_BIND_ADDRESS = "bind_address"
        const val FIELD_CLIENT_ID = "client_id"
        const val FIELD_ERROR_CLASS = "error_class"
        const val FIELD_ERROR_MESSAGE = "error_message"
        const val FIELD_PORT = "port"
        const val FIELD_REASON = "reason"
        const val FIELD_REQUEST_TYPE = "request_type"
        const val KEY_ACTION = "action"
        const val KEY_ACTIONS = "actions"
        const val KEY_CODE = "code"
        const val KEY_COMMAND = "command"
        const val KEY_CONFIG = "config"
        const val KEY_DELTA_Y = "deltaY"
        const val KEY_EVENT = "event"
        const val KEY_FIELDS = "fields"
        const val KEY_INTENT_TYPE = "intentType"
        const val KEY_MESSAGE = "message"
        const val KEY_NODE_ID = "nodeId"
        const val KEY_COMPONENT = "component"
        const val KEY_PAGE_ID = "pageId"
        const val KEY_QUERY = "query"
        const val KEY_PROTOCOL_VERSION = "protocolVersion"
        const val KEY_REQUEST_ID = "requestId"
        const val KEY_RESULT = "result"
        const val KEY_REVISION = "revision"
        const val KEY_ROOT = "root"
        const val KEY_SEMANTIC_ID = "semanticId"
        const val KEY_SCRIPT_ID = "scriptId"
        const val KEY_SOURCE_WIDGET_ADDRESS = "sourceWidgetAddress"
        const val KEY_SNAPSHOT_ID = "snapshotId"
        const val KEY_SNAPSHOT = "snapshot"
        const val KEY_TEXT = "text"
        const val KEY_TIMEOUT_MILLISECONDS = "timeoutMilliseconds"
        const val KEY_TYPE = "type"
        const val KEY_CELL = "cell"
        const val KEY_COLUMN = "column"
        const val KEY_COLUMN_SPAN = "columnSpan"
        const val KEY_COLUMNS = "columns"
        const val KEY_GAP_DP = "gapDp"
        const val KEY_GRID = "grid"
        const val KEY_OUTER_PADDING_DP = "outerPaddingDp"
        const val KEY_ROWS = "rows"
        const val KEY_ROW = "row"
        const val KEY_ROW_SPAN = "rowSpan"
        const val KEY_WIDGET_ADDRESS = "widgetAddress"
        const val KEY_WIDGET_TYPE = "widgetType"
        const val KEY_NODES = "nodes"
        const val KEY_FOUND = "found"
        const val KEY_NODE = "node"
        const val LOOPBACK_ADDRESS = "127.0.0.1"
        const val MAXIMUM_CLIENT_COUNT = 4
        const val MAXIMUM_ACCESSIBILITY_ACTION_CHARACTERS = 32
        const val MAXIMUM_ACCESSIBILITY_NODE_ID_CHARACTERS = 128
        const val MAXIMUM_ACCESSIBILITY_SNAPSHOT_ID_CHARACTERS = 36
        const val MAXIMUM_INTENT_ACTION_CHARACTERS = 2_048
        const val MAXIMUM_INTENT_TYPE_CHARACTERS = 32
        const val MAXIMUM_APP_ACTION_CHARACTERS = 32
        const val MAXIMUM_APP_QUERY_CHARACTERS = 256
        const val MAXIMUM_COMPONENT_CHARACTERS = 512
        const val MAXIMUM_SEMANTIC_ID_CHARACTERS = 256
        const val MAXIMUM_SHELL_COMMAND_CHARACTERS = 8_192
        const val MAXIMUM_TEXT_CHARACTERS = 8_192
        const val MAXIMUM_SCROLL_DELTA = 10_000
        const val MAXIMUM_WAIT_TIMEOUT_MILLISECONDS = 5_000
        const val MESSAGE_HELLO_REQUIRED = "send hello before any command"
        const val MESSAGE_INTERNAL_ERROR = "command could not complete"
        const val MESSAGE_INVALID_JSON = "message must be a JSON object"
        const val MESSAGE_INVALID_APP_ACTION = "field action must name a supported app action"
        const val MESSAGE_INVALID_INTENT_TYPE = "intentType is not supported"
        const val MESSAGE_INVALID_REQUEST_ID = "requestId must be a UUID"
        const val MESSAGE_INVALID_ROOT_REQUEST = "root must be a boolean"
        const val MESSAGE_MISSING_FIELD = "required field is missing"
        const val MESSAGE_PROTOCOL_MISMATCH = "unsupported protocol version"
        const val MESSAGE_REMOTE_AUTH_REQUIRED = "a valid remote bearer credential is required"
        const val MESSAGE_UI_THREAD_TIMEOUT = "launcher UI did not respond in time"
        const val MESSAGE_UNEXPECTED_FIELD = "request contains an unsupported field"
        const val MESSAGE_UNKNOWN_COMMAND = "unknown command"
        const val MESSAGE_WAIT_INTERRUPTED = "wait was interrupted"
        const val MINIMUM_SCROLL_DELTA = -10_000
        const val MINIMUM_WAIT_TIMEOUT_MILLISECONDS = 1
        const val PROTOCOL_VERSION = 1
        const val REASON_CLIENT_LIMIT = "client_limit"
        const val THREAD_NAME_PREFIX = "dikciz-automation-"
        const val TYPE_DIAGNOSTICS = "diagnostics"
        const val TYPE_CONFIG_GET = "configGet"
        const val TYPE_CONFIG_REPLACE = "configReplace"
        const val TYPE_CONFIG_SEED = "configSeed"
        const val TYPE_AUTOMATION_SERVICE_SYNC = "automationServiceSync"
        const val TYPE_AUTOMATION_DISPATCH = "automationDispatch"
        const val TYPE_AUTOMATION_STATUS = "automationStatus"
        const val TYPE_AUTOMATION_TRIGGER = "automationTrigger"
        const val TYPE_ACCESSIBILITY_ACTION = "accessibilityAction"
        const val TYPE_ADD_WIDGET = "addWidget"
        const val TYPE_GRID_SET = "gridSet"
        const val TYPE_APP_ACTION = "appAction"
        const val TYPE_APP_CATALOGUE = "appCatalogue"
        const val TYPE_OPEN_AUTOMATION_SETUP = "openAutomationSetup"
        const val TYPE_ACCESSIBILITY_SNAPSHOT = "accessibilitySnapshot"
        const val TYPE_CONTROL_STATUS = "controlStatus"
        const val TYPE_ERROR = "error"
        const val TYPE_EVENT = "event"
        const val TYPE_FIND = "find"
        const val TYPE_HTML_WIDGET_RENDERER_CRASH = "htmlWidgetRendererCrash"
        const val TYPE_HOME_GET = "homeGet"
        const val TYPE_HELLO = "hello"
        const val TYPE_INTENT = "intent"
        const val TYPE_LAUNCH_APP = "launchApp"
        const val TYPE_LONG_PRESS = "longPress"
        const val TYPE_RESULT = "result"
        const val TYPE_RESET = "reset"
        const val TYPE_REMOTE_AUTH_DISABLE = "remoteAuthDisable"
        const val TYPE_REMOTE_AUTH_ENABLE = "remoteAuthEnable"
        const val TYPE_REMOTE_AUTH_STATUS = "remoteAuthStatus"
        const val TYPE_SCREENSHOT = "screenshot"
        const val TYPE_SCROLL_BY = "scrollBy"
        const val TYPE_SCROLL_TO = "scrollTo"
        const val TYPE_SELECT_PAGE = "selectPage"
        const val TYPE_SET_TEXT = "setText"
        const val TYPE_SHELL = "shell"
        const val TYPE_SCRIPT_LOGS = "scriptLogs"
        const val TYPE_SNAPSHOT = "snapshot"
        const val TYPE_TAP = "tap"
        const val TYPE_UI_DUMP = "uiDump"
        const val TYPE_WAIT_FOR = "waitFor"
        const val TYPE_WIDGET_GET = "widgetGet"
        const val TYPE_WIDGET_MOVE = "widgetMove"
        const val TYPE_WIDGET_RESIZE = "widgetResize"
        const val UI_THREAD_TIMEOUT_MILLISECONDS = 5_000L
        const val OUTCOME_FAILED = "failed"
        const val OUTCOME_REJECTED = "rejected"
        const val OUTCOME_SUCCEEDED = "succeeded"
        const val VALUE_UNKNOWN_COMMAND_TYPE = "unknown"
        const val WAIT_POLL_INTERVAL_MILLISECONDS = 50L
        val HELLO_KEYS = setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_PROTOCOL_VERSION)
        val ACCESSIBILITY_ACTION_REQUIRED_KEYS = setOf(
            KEY_REQUEST_ID,
            KEY_TYPE,
            KEY_SNAPSHOT_ID,
            KEY_NODE_ID,
            KEY_ACTION,
        )
        val APP_CATALOGUE_REQUIRED_KEYS = setOf(KEY_REQUEST_ID, KEY_TYPE)
        val COMMAND_REQUIRED_KEYS = mapOf(
            TYPE_ACCESSIBILITY_ACTION to ACCESSIBILITY_ACTION_REQUIRED_KEYS,
            TYPE_APP_CATALOGUE to APP_CATALOGUE_REQUIRED_KEYS,
        )
        val COMMAND_KEYS = mapOf(
            TYPE_ACCESSIBILITY_SNAPSHOT to setOf(KEY_REQUEST_ID, KEY_TYPE),
            TYPE_APP_CATALOGUE to APP_CATALOGUE_REQUIRED_KEYS + KEY_QUERY,
            TYPE_APP_ACTION to setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_ACTION, KEY_COMPONENT),
            TYPE_ACCESSIBILITY_ACTION to ACCESSIBILITY_ACTION_REQUIRED_KEYS + KEY_TEXT,
            TYPE_SNAPSHOT to setOf(KEY_REQUEST_ID, KEY_TYPE),
            TYPE_CONFIG_GET to setOf(KEY_REQUEST_ID, KEY_TYPE),
            TYPE_CONFIG_REPLACE to setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_CONFIG),
            TYPE_CONFIG_SEED to setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_CONFIG),
            TYPE_AUTOMATION_SERVICE_SYNC to setOf(KEY_REQUEST_ID, KEY_TYPE),
            TYPE_AUTOMATION_STATUS to setOf(KEY_REQUEST_ID, KEY_TYPE),
            TYPE_AUTOMATION_TRIGGER to setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_SCRIPT_ID),
            TYPE_AUTOMATION_DISPATCH to setOf(
                KEY_REQUEST_ID,
                KEY_TYPE,
                KEY_SOURCE_WIDGET_ADDRESS,
                KEY_ACTIONS,
            ),
            TYPE_CONTROL_STATUS to setOf(KEY_REQUEST_ID, KEY_TYPE),
            TYPE_OPEN_AUTOMATION_SETUP to setOf(KEY_REQUEST_ID, KEY_TYPE),
            TYPE_REMOTE_AUTH_STATUS to setOf(KEY_REQUEST_ID, KEY_TYPE),
            TYPE_REMOTE_AUTH_ENABLE to setOf(KEY_REQUEST_ID, KEY_TYPE),
            TYPE_REMOTE_AUTH_DISABLE to setOf(KEY_REQUEST_ID, KEY_TYPE),
            TYPE_SCRIPT_LOGS to setOf(KEY_REQUEST_ID, KEY_TYPE),
            TYPE_RESET to setOf(KEY_REQUEST_ID, KEY_TYPE),
            TYPE_FIND to setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_SEMANTIC_ID),
            TYPE_TAP to setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_SEMANTIC_ID),
            TYPE_LONG_PRESS to setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_SEMANTIC_ID),
            TYPE_SELECT_PAGE to setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_PAGE_ID),
            TYPE_SCROLL_BY to setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_DELTA_Y),
            TYPE_SCROLL_TO to setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_SEMANTIC_ID),
            TYPE_SET_TEXT to setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_SEMANTIC_ID, KEY_TEXT),
            TYPE_LAUNCH_APP to setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_SEMANTIC_ID),
            TYPE_SHELL to setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_COMMAND, KEY_ROOT),
            TYPE_INTENT to setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_INTENT_TYPE, KEY_ACTION),
            TYPE_SCREENSHOT to setOf(KEY_REQUEST_ID, KEY_TYPE),
            TYPE_UI_DUMP to setOf(KEY_REQUEST_ID, KEY_TYPE),
            TYPE_DIAGNOSTICS to setOf(KEY_REQUEST_ID, KEY_TYPE),
            TYPE_HOME_GET to setOf(KEY_REQUEST_ID, KEY_TYPE),
            TYPE_WIDGET_GET to setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_WIDGET_ADDRESS),
            TYPE_ADD_WIDGET to setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_WIDGET_TYPE),
            TYPE_GRID_SET to setOf(
                KEY_REQUEST_ID,
                KEY_TYPE,
                KEY_COLUMNS,
                KEY_ROWS,
                KEY_GAP_DP,
                KEY_OUTER_PADDING_DP,
            ),
            TYPE_WIDGET_MOVE to setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_WIDGET_ADDRESS, KEY_CELL),
            TYPE_WIDGET_RESIZE to setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_WIDGET_ADDRESS, KEY_CELL),
            TYPE_HTML_WIDGET_RENDERER_CRASH to setOf(
                KEY_REQUEST_ID,
                KEY_TYPE,
                KEY_WIDGET_ADDRESS,
            ),
            TYPE_WAIT_FOR to setOf(KEY_REQUEST_ID, KEY_TYPE, KEY_SEMANTIC_ID, KEY_TIMEOUT_MILLISECONDS),
        )
        val REMOTE_AUTH_COMMAND_TYPES = setOf(
            TYPE_REMOTE_AUTH_STATUS,
            TYPE_REMOTE_AUTH_ENABLE,
            TYPE_REMOTE_AUTH_DISABLE,
        )
        val SUPPORTED_APP_ACTIONS = DikcizAppActionType.PERSISTED_VALUES.toSet()
        val SUPPORTED_INTENT_TYPES = setOf(INTENT_TYPE_ACTIVITY, INTENT_TYPE_BROADCAST)
        const val INTENT_TYPE_ACTIVITY = "activity"
        const val INTENT_TYPE_BROADCAST = "broadcast"
    }
}

private fun JSONObject.requireString(key: String): String {
    val value = opt(key)
    if (value !is String || value.isBlank()) {
        throw DikcizAutomationRequestException("validation_failed", "field $key must be a string")
    }
    return value
}

private fun JSONObject.requireBoundedString(key: String, maximumLength: Int): String {
    val value = requireString(key)
    if (value.length > maximumLength) {
        throw DikcizAutomationRequestException("validation_failed", "field $key exceeds its limit")
    }
    return value
}

/**
 * Reads a placement rectangle. `cell` may be JSON null, which means "choose the first free
 * rectangle", so an absent anchor is a supported request and not a validation failure.
 */
internal fun JSONObject.requireGridCell(): JSONObject? {
    if (isNull(DikcizAutomationControlPlane.KEY_CELL)) {
        return null
    }
    val cell = requireObject(DikcizAutomationControlPlane.KEY_CELL)
    cell.requireBoundedInt(
        DikcizAutomationControlPlane.KEY_COLUMN,
        MINIMUM_GRID_INDEX,
        DikcizNativeGrid.MAXIMUM_COLUMNS,
    )
    cell.requireBoundedInt(
        DikcizAutomationControlPlane.KEY_ROW,
        MINIMUM_GRID_INDEX,
        DikcizNativeGrid.MAXIMUM_ROWS,
    )
    cell.requireBoundedInt(
        DikcizAutomationControlPlane.KEY_COLUMN_SPAN,
        DikcizGridRectangle.MINIMUM_SPAN,
        DikcizNativeGrid.MAXIMUM_COLUMNS,
    )
    cell.requireBoundedInt(
        DikcizAutomationControlPlane.KEY_ROW_SPAN,
        DikcizGridRectangle.MINIMUM_SPAN,
        DikcizNativeGrid.MAXIMUM_ROWS,
    )
    return cell
}

private const val MINIMUM_GRID_INDEX = 0

private fun JSONObject.requireObject(key: String): JSONObject {
    val value = opt(key)
    if (value !is JSONObject) {
        throw DikcizAutomationRequestException("validation_failed", "field $key must be an object")
    }
    return value
}

private fun JSONObject.requireArray(key: String): org.json.JSONArray {
    val value = opt(key)
    if (value !is org.json.JSONArray) {
        throw DikcizAutomationRequestException("validation_failed", "field $key must be an array")
    }
    return value
}

private fun JSONObject.requireInt(key: String): Int {
    val value = opt(key)
    if (value !is Number || value.toDouble() != value.toInt().toDouble()) {
        throw DikcizAutomationRequestException("validation_failed", "field $key must be an integer")
    }
    return value.toInt()
}

private fun JSONObject.requireBoundedInt(key: String, minimum: Int, maximum: Int): Int {
    val value = requireInt(key)
    if (value !in minimum..maximum) {
        throw DikcizAutomationRequestException("validation_failed", "field $key is out of range")
    }
    return value
}
