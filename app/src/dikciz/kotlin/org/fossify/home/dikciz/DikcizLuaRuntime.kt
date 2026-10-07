package org.fossify.home.dikciz

import android.os.Debug
import org.json.JSONArray
import org.json.JSONObject
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaThread
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.BaseLib
import org.luaj.vm2.lib.Bit32Lib
import org.luaj.vm2.lib.DebugLib
import org.luaj.vm2.lib.PackageLib
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.ZeroArgFunction
import org.luaj.vm2.lib.jse.JseMathLib
import org.luaj.vm2.LoadState
import java.time.Instant
import java.time.ZoneId

internal class DikcizLuaRuntime {
    fun executeEvent(
        script: DikcizLuaScript,
        event: DikcizAutomationEvent,
        limits: DikcizLimits,
        widgets: JSONObject,
        accessibility: JSONObject?,
        androidAccess: JSONObject,
    ): DikcizLuaAutomationResult {
        if (!script.enabled) {
            return DikcizLuaAutomationResult.Disabled
        }
        return try {
            val setupBudget = LuaExecutionBudget(MAXIMUM_SETUP_DURATION_NANOS)
            val executionGlobals = createExecutionGlobals(script, widgets, accessibility, androidAccess)
            setupBudget.throwIfExceeded()
            val setHook = takeInstructionHook(executionGlobals)
            val chunk = createCompilerGlobals().load(
                script.source,
                scriptChunkName(script.id),
                executionGlobals,
            )
            setupBudget.throwIfExceeded()
            resumeWithLimits(executionGlobals, setHook, chunk, LuaValue.NIL, setupBudget)
            setupBudget.throwIfExceeded()
            val handler = executionGlobals.get(EVENT_HANDLER_GLOBAL_NAME)
            if (handler.isnil()) {
                return DikcizLuaAutomationResult.NoHandler
            }
            if (!handler.isfunction()) {
                return DikcizLuaAutomationResult.Failure(DikcizLuaFailure.InvalidResult)
            }
            val executionBudget = LuaExecutionBudget(MAXIMUM_EXECUTION_DURATION_NANOS)
            val result = resumeWithLimits(
                executionGlobals,
                setHook,
                handler,
                eventToLuaTable(event),
                executionBudget,
            )
            executionBudget.throwIfExceeded()
            val parsedResult = parseEventResult(result, limits)
            DikcizLuaAutomationResult.Actions(parsedResult.actions, parsedResult.status)
        } catch (_: DikcizLuaExecutionCancelled) {
            DikcizLuaAutomationResult.Failure(DikcizLuaFailure.Cancelled)
        } catch (_: DikcizLuaMemoryBudgetExceeded) {
            DikcizLuaAutomationResult.Failure(DikcizLuaFailure.MemoryBudgetExceeded)
        } catch (_: DikcizLuaExecutionDeadlineExceeded) {
            DikcizLuaAutomationResult.Failure(DikcizLuaFailure.ExecutionDeadlineExceeded)
        } catch (_: DikcizLuaInstructionLimitExceeded) {
            DikcizLuaAutomationResult.Failure(DikcizLuaFailure.InstructionBudgetExceeded)
        } catch (exception: LuaError) {
            DikcizLuaAutomationResult.Failure(
                DikcizLuaFailure.InvalidLua,
                luaErrorDiagnostic(exception),
            )
        } catch (_: IllegalArgumentException) {
            DikcizLuaAutomationResult.Failure(DikcizLuaFailure.InvalidResult)
        }
    }

    private fun createExecutionGlobals(
        script: DikcizLuaScript,
        widgets: JSONObject = JSONObject(),
        accessibility: JSONObject? = null,
        androidAccess: JSONObject = JSONObject(),
    ): Globals {
        val globals = createBaseGlobals()
        globals.set(CONTEXT_GLOBAL_NAME, createExecutionContext(script, widgets, accessibility, androidAccess))
        globals.load(DebugLib())
        globals.get(DEBUG_GLOBAL_NAME).get(DEBUG_SET_HOOK_FUNCTION_NAME).also { hook ->
            globals.set(PRIVATE_SET_HOOK_GLOBAL_NAME, hook)
        }
        globals.set(DEBUG_GLOBAL_NAME, LuaValue.NIL)
        FORBIDDEN_GLOBALS.forEach { globalName ->
            globals.set(globalName, LuaValue.NIL)
        }
        return globals
    }

    private fun createExecutionContext(
        script: DikcizLuaScript,
        widgets: JSONObject,
        accessibility: JSONObject?,
        androidAccess: JSONObject,
    ): LuaTable {
        return ReadOnlyLuaTable().apply {
            putInitial(CONTEXT_SCRIPT_ID_FIELD, LuaValue.valueOf(script.id))
            putInitial(CONTEXT_STATE_FIELD, scriptStateToLuaTable(script.state))
            putInitial(CONTEXT_WIDGETS_FIELD, scriptStateToLuaTable(widgets))
            putInitial(CONTEXT_ANDROID_ACCESS_FIELD, scriptStateToLuaTable(androidAccess))
            if (accessibility != null) {
                putInitial(CONTEXT_ACCESSIBILITY_FIELD, scriptStateToLuaTable(accessibility))
            }
        }
    }

    private fun eventToLuaTable(event: DikcizAutomationEvent): LuaTable {
        return ReadOnlyLuaTable().apply {
            putInitial(EVENT_COALESCING_KEY_FIELD, LuaValue.valueOf(event.coalescingKey))
            putInitial(EVENT_PAYLOAD_FIELD, scriptStateToLuaTable(event.payload))
            putInitial(EVENT_SOURCE_FIELD, LuaValue.valueOf(event.source))
            putInitial(EVENT_TIMESTAMP_MILLISECONDS_FIELD, LuaValue.valueOf(event.timestampMilliseconds.toDouble()))
            putInitial(EVENT_TIME_FIELD, eventTimeToLuaTable(event.timestampMilliseconds))
            putInitial(EVENT_TYPE_FIELD, LuaValue.valueOf(event.name))
        }
    }

    private fun eventTimeToLuaTable(timestampMilliseconds: Long): LuaTable {
        val time = Instant.ofEpochMilli(timestampMilliseconds).atZone(ZoneId.systemDefault())
        return ReadOnlyLuaTable().apply {
            putInitial(EVENT_TIME_HOUR_24_FIELD, LuaValue.valueOf(time.hour))
            putInitial(EVENT_TIME_MINUTE_FIELD, LuaValue.valueOf(time.minute))
        }
    }

    private fun scriptStateToLuaTable(state: JSONObject): LuaTable {
        return ReadOnlyLuaTable().apply {
            val keys = state.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                putInitial(key, scriptStateValueToLuaValue(state.get(key)))
            }
        }
    }

    private fun scriptStateArrayToLuaTable(state: JSONArray): LuaTable {
        return ReadOnlyLuaTable().apply {
            repeat(state.length()) { index ->
                putInitial(index + FIRST_LUA_ARRAY_INDEX, scriptStateValueToLuaValue(state.get(index)))
            }
        }
    }

    private fun scriptStateValueToLuaValue(value: Any): LuaValue {
        if (value === JSONObject.NULL) {
            return LuaValue.NIL
        }
        return when (value) {
            is Boolean -> LuaValue.valueOf(value)
            is Number -> LuaValue.valueOf(value.toDouble())
            is String -> LuaValue.valueOf(value)
            is JSONArray -> scriptStateArrayToLuaTable(value)
            is JSONObject -> scriptStateToLuaTable(value)
            else -> throw IllegalArgumentException(UNSUPPORTED_SCRIPT_STATE_VALUE_MESSAGE)
        }
    }

    private fun createCompilerGlobals(): Globals = Globals().apply {
        LoadState.install(this)
        LuaC.install(this)
    }

    private fun createBaseGlobals(): Globals = Globals().apply {
        load(BaseLib())
        load(PackageLib())
        load(Bit32Lib())
        load(TableLib())
        load(StringLib())
        load(JseMathLib())
        removeUnboundedLibraryFunctions(this)
    }

    private fun removeUnboundedLibraryFunctions(globals: Globals) {
        removeLibraryFunctions(globals, STRING_LIBRARY_NAME, UNBOUNDED_STRING_FUNCTIONS)
        removeLibraryFunctions(globals, TABLE_LIBRARY_NAME, UNBOUNDED_TABLE_FUNCTIONS)
    }

    private fun removeLibraryFunctions(
        globals: Globals,
        libraryName: String,
        functionNames: Set<String>,
    ) {
        val library = globals.get(libraryName).checktable()
        functionNames.forEach { functionName ->
            library.set(functionName, LuaValue.NIL)
        }
    }

    private fun resumeWithLimits(
        globals: Globals,
        setHook: LuaValue,
        chunk: LuaValue,
        argument: LuaValue,
        executionBudget: LuaExecutionBudget,
    ): LuaValue {
        val thread = LuaThread(globals, chunk)
        setHook.invoke(
            LuaValue.varargsOf(
                arrayOf(
                    thread,
                    InstructionLimitHook(executionBudget),
                    LuaValue.EMPTYSTRING,
                    LuaValue.valueOf(INSTRUCTION_HOOK_INTERVAL),
                ),
            ),
        )
        val result = thread.resume(argument)
        if (!result.arg1().toboolean()) {
            throw LuaError(result.arg(SECOND_RESULT_INDEX).tojstring())
        }
        return result.arg(SECOND_RESULT_INDEX)
    }

    private fun luaErrorDiagnostic(exception: LuaError): String {
        return exception.message.orEmpty()
            .filter { character -> character >= MINIMUM_DIAGNOSTIC_CHARACTER && character != DELETE_CHARACTER }
            .take(MAXIMUM_LUA_DIAGNOSTIC_CHARACTERS)
            .ifBlank { SCRIPT_EXECUTION_FAILED_MESSAGE }
    }

    private fun takeInstructionHook(globals: Globals): LuaValue {
        val setHook = globals.get(PRIVATE_SET_HOOK_GLOBAL_NAME)
        globals.set(PRIVATE_SET_HOOK_GLOBAL_NAME, LuaValue.NIL)
        return setHook
    }

    private fun parseEventResult(
        result: LuaValue,
        limits: DikcizLimits,
    ): DikcizLuaEventResult {
        if (result.isnil()) {
            return DikcizLuaEventResult(emptyList(), null)
        }
        val table = result.checktable()
        if (table.get(EVENT_ACTIONS_FIELD).istable() || table.get(EVENT_STATUS_FIELD).isstring()) {
            requireExactKeys(table, EVENT_RESULT_KEYS)
            val status = optionalBoundedString(table, EVENT_STATUS_FIELD, limits.maxTitleCharacters)
            val actions = if (table.get(EVENT_ACTIONS_FIELD).isnil()) {
                emptyList()
            } else {
                parseEventActions(table.get(EVENT_ACTIONS_FIELD), limits)
            }
            return DikcizLuaEventResult(actions, status)
        }
        return DikcizLuaEventResult(parseEventActions(result, limits), null)
    }

    private fun parseEventActions(
        result: LuaValue,
        limits: DikcizLimits,
    ): List<DikcizLuaAutomationAction> {
        if (result.isnil()) {
            return emptyList()
        }
        val table = result.checktable()
        if (table.get(TYPE_FIELD).isstring()) {
            return listOf(parseEventAction(table, limits))
        }
        val actions = mutableListOf<DikcizLuaAutomationAction>()
        var index = FIRST_LUA_ARRAY_INDEX
        while (!table.get(index).isnil()) {
            if (actions.size >= MAXIMUM_EVENT_ACTIONS) {
                throw IllegalArgumentException(TOO_MANY_EVENT_ACTIONS_MESSAGE)
            }
            actions += parseEventAction(table.get(index).checktable(), limits)
            index += FIRST_LUA_ARRAY_INDEX
        }
        var key = LuaValue.NIL
        while (true) {
            val entry = table.next(key)
            key = entry.arg1()
            if (key.isnil()) {
                return actions
            }
            if (!key.isinttype() || key.toint() !in FIRST_LUA_ARRAY_INDEX until index) {
                throw IllegalArgumentException(INVALID_EVENT_ACTION_LIST_MESSAGE)
            }
        }
    }

    private fun parseEventAction(
        action: LuaTable,
        limits: DikcizLimits,
    ): DikcizLuaAutomationAction {
        return when (action.get(TYPE_FIELD).checkjstring()) {
            SELECT_PAGE_ACTION_TYPE -> {
                requireExactKeys(action, SELECT_PAGE_ACTION_KEYS)
                DikcizLuaAutomationAction.SelectPage(requireBoundedIdentifier(action, PAGE_ID_FIELD, limits))
            }

            PATCH_WIDGET_ACTION_TYPE -> {
                requireExactKeys(action, PATCH_WIDGET_ACTION_KEYS)
                DikcizLuaAutomationAction.PatchWidget(
                    widgetAddress = requireWidgetAddress(action, limits),
                    values = parseWidgetPatch(action.get(VALUES_FIELD).checktable(), limits),
                )
            }

            PATCH_STATE_ACTION_TYPE -> {
                requireExactKeys(action, PATCH_STATE_ACTION_KEYS)
                DikcizLuaAutomationAction.PatchState(
                    luaTableToJson(action.get(VALUES_FIELD).checktable(), limits, ROOT_STATE_PATCH_DEPTH),
                )
            }

            PATCH_DOM_ACTION_TYPE -> {
                requireExactKeys(action, PATCH_DOM_ACTION_KEYS)
                DikcizLuaAutomationAction.PatchDom(
                    widgetAddress = requireWidgetAddress(action, limits),
                    selector = requireBoundedString(
                        action,
                        SELECTOR_FIELD,
                        MAXIMUM_DOM_SELECTOR_CHARACTERS,
                    ),
                    values = parseDomPatch(action.get(VALUES_FIELD).checktable(), limits),
                )
            }

            LAUNCH_APP_ACTION_TYPE -> {
                requireExactKeys(action, LAUNCH_APP_ACTION_KEYS)
                DikcizLuaAutomationAction.LaunchApp(
                    requireBoundedString(action, COMPONENT_FIELD, limits.maxComponentCharacters),
                )
            }

            APP_ACTION_ACTION_TYPE -> {
                requireExactKeys(action, APP_ACTION_ACTION_KEYS)
                DikcizLuaAutomationAction.AppAction(
                    action = requireScriptAppAction(action),
                    component = requireBoundedString(action, COMPONENT_FIELD, limits.maxComponentCharacters),
                )
            }

            POST_NOTIFICATION_ACTION_TYPE -> {
                requireExactKeys(action, POST_NOTIFICATION_ACTION_KEYS)
                DikcizLuaAutomationAction.PostNotification(
                    requireBoundedString(action, TITLE_FIELD, limits.maxTitleCharacters),
                    requireBoundedString(action, TEXT_FIELD, limits.maxTextCharacters),
                )
            }

            SEND_SMS_ACTION_TYPE -> {
                requireExactKeys(action, SEND_SMS_ACTION_KEYS)
                DikcizLuaAutomationAction.SendSms(
                    requireDialableRecipient(action),
                    requireBoundedString(action, BODY_FIELD, limits.maxTextCharacters),
                )
            }

            EXPLICIT_INTENT_ACTION_TYPE -> {
                requireExactKeys(action, EXPLICIT_INTENT_ACTION_KEYS)
                val intentType = DikcizAutomationIntentType.fromPersistedValue(
                    requireBoundedString(action, INTENT_TYPE_FIELD, MAXIMUM_INTENT_TYPE_CHARACTERS),
                ) ?: throw IllegalArgumentException(INVALID_ACTION_TYPE_MESSAGE)
                DikcizLuaAutomationAction.ExplicitIntent(
                    intentType = intentType,
                    component = requireBoundedString(action, COMPONENT_FIELD, limits.maxComponentCharacters),
                    action = optionalBoundedString(action, ACTION_NAME_FIELD, MAXIMUM_INTENT_ACTION_CHARACTERS),
                )
            }

            EMIT_EVENT_ACTION_TYPE -> {
                requireAllowedKeys(action, EMIT_EVENT_ACTION_KEYS)
                val eventName = requireBoundedString(
                    action,
                    EVENT_NAME_FIELD,
                    DikcizAutomationEventNames.MAXIMUM_CUSTOM_EVENT_NAME_CHARACTERS,
                )
                if (!DikcizAutomationEventNames.isValidCustomName(eventName)) {
                    throw IllegalArgumentException(INVALID_CUSTOM_EVENT_NAME_MESSAGE)
                }
                DikcizLuaAutomationAction.EmitEvent(
                    eventName = eventName,
                    payload = luaTableToJson(
                        action.get(EVENT_PAYLOAD_FIELD).checktable(),
                        limits,
                        ROOT_STATE_PATCH_DEPTH,
                    ),
                    coalescingKey = optionalBoundedIdentifier(action, EVENT_COALESCING_KEY_FIELD, limits),
                )
            }

            MEDIA_CONTROL_ACTION_TYPE -> {
                requireExactKeys(action, MEDIA_CONTROL_ACTION_KEYS)
                val packageName = requireBoundedString(
                    action,
                    PACKAGE_NAME_FIELD,
                    MAXIMUM_MEDIA_PACKAGE_NAME_CHARACTERS,
                )
                if (!ANDROID_PACKAGE_NAME_PATTERN.matches(packageName)) {
                    throw IllegalArgumentException(INVALID_MEDIA_PACKAGE_NAME_MESSAGE)
                }
                val command = DikcizMediaControlCommand.fromPersistedValue(
                    requireBoundedString(action, COMMAND_FIELD, MAXIMUM_MEDIA_COMMAND_CHARACTERS),
                ) ?: throw IllegalArgumentException(INVALID_ACTION_TYPE_MESSAGE)
                val positionMilliseconds = optionalMediaSeekPosition(action)
                if (command == DikcizMediaControlCommand.SeekTo && positionMilliseconds == null) {
                    throw IllegalArgumentException(MISSING_MEDIA_SEEK_POSITION_MESSAGE)
                }
                if (command != DikcizMediaControlCommand.SeekTo && positionMilliseconds != null) {
                    throw IllegalArgumentException(UNEXPECTED_MEDIA_SEEK_POSITION_MESSAGE)
                }
                DikcizLuaAutomationAction.MediaControl(packageName, command, positionMilliseconds)
            }

            SET_MEDIA_VOLUME_ACTION_TYPE -> {
                requireExactKeys(action, SET_MEDIA_VOLUME_ACTION_KEYS)
                DikcizLuaAutomationAction.SetMediaVolume(requireMediaVolumePercent(action))
            }

            NOTIFICATION_CONTROL_ACTION_TYPE -> {
                requireExactKeys(action, NOTIFICATION_CONTROL_ACTION_KEYS)
                val actionToken = requireBoundedString(
                    action,
                    ACTION_TOKEN_FIELD,
                    MAXIMUM_NOTIFICATION_ACTION_TOKEN_CHARACTERS,
                )
                if (!NOTIFICATION_ACTION_TOKEN_PATTERN.matches(actionToken)) {
                    throw IllegalArgumentException(INVALID_NOTIFICATION_ACTION_TOKEN_MESSAGE)
                }
                DikcizLuaAutomationAction.NotificationControl(actionToken)
            }

            LOCK_DEVICE_ACTION_TYPE -> {
                requireExactKeys(action, LOCK_DEVICE_ACTION_KEYS)
                DikcizLuaAutomationAction.LockDevice
            }

            ACCESSIBILITY_ACTION_TYPE -> {
                requireAllowedKeys(action, ACCESSIBILITY_ACTION_KEYS)
                DikcizLuaAutomationAction.AccessibilityAction(
                    snapshotID = requireBoundedString(
                        action,
                        SNAPSHOT_ID_FIELD,
                        MAXIMUM_ACCESSIBILITY_SNAPSHOT_ID_CHARACTERS,
                    ),
                    nodeID = requireBoundedString(
                        action,
                        NODE_ID_FIELD,
                        MAXIMUM_ACCESSIBILITY_NODE_ID_CHARACTERS,
                    ),
                    action = requireBoundedString(
                        action,
                        ACTION_NAME_FIELD,
                        MAXIMUM_ACCESSIBILITY_ACTION_CHARACTERS,
                    ),
                    text = optionalBoundedString(action, TEXT_FIELD, limits.maxTextCharacters),
                )
            }

            ACCESSIBILITY_GESTURE_ACTION_TYPE -> {
                val gestureKind = DikcizAccessibilityGestureKind.fromPersistedValue(
                    requireBoundedString(
                        action,
                        GESTURE_FIELD,
                        MAXIMUM_ACCESSIBILITY_GESTURE_CHARACTERS,
                    ),
                ) ?: throw IllegalArgumentException(INVALID_ACTION_TYPE_MESSAGE)
                when (gestureKind) {
                    DikcizAccessibilityGestureKind.Tap -> {
                        requireExactKeys(action, ACCESSIBILITY_TAP_GESTURE_KEYS)
                        val x = requireGestureCoordinate(action, X_FIELD)
                        val y = requireGestureCoordinate(action, Y_FIELD)
                        DikcizLuaAutomationAction.AccessibilityGesture(
                            DikcizAccessibilityGesture(
                                snapshotID = requireBoundedString(
                                    action,
                                    SNAPSHOT_ID_FIELD,
                                    MAXIMUM_ACCESSIBILITY_SNAPSHOT_ID_CHARACTERS,
                                ),
                                kind = gestureKind,
                                startX = x,
                                startY = y,
                                endX = x,
                                endY = y,
                                durationMilliseconds = requireGestureDuration(action),
                            ),
                        )
                    }

                    DikcizAccessibilityGestureKind.Swipe -> {
                        requireExactKeys(action, ACCESSIBILITY_SWIPE_GESTURE_KEYS)
                        DikcizLuaAutomationAction.AccessibilityGesture(
                            DikcizAccessibilityGesture(
                                snapshotID = requireBoundedString(
                                    action,
                                    SNAPSHOT_ID_FIELD,
                                    MAXIMUM_ACCESSIBILITY_SNAPSHOT_ID_CHARACTERS,
                                ),
                                kind = gestureKind,
                                startX = requireGestureCoordinate(action, START_X_FIELD),
                                startY = requireGestureCoordinate(action, START_Y_FIELD),
                                endX = requireGestureCoordinate(action, END_X_FIELD),
                                endY = requireGestureCoordinate(action, END_Y_FIELD),
                                durationMilliseconds = requireGestureDuration(action),
                            ),
                        )
                    }
                }
            }

            ACCESSIBILITY_GLOBAL_ACTION_TYPE -> {
                requireExactKeys(action, ACCESSIBILITY_GLOBAL_ACTION_KEYS)
                val globalAction = DikcizAccessibilityGlobalAction.fromPersistedValue(
                    requireBoundedString(
                        action,
                        ACTION_NAME_FIELD,
                        MAXIMUM_ACCESSIBILITY_ACTION_CHARACTERS,
                    ),
                ) ?: throw IllegalArgumentException(INVALID_ACTION_TYPE_MESSAGE)
                DikcizLuaAutomationAction.AccessibilityGlobalAction(globalAction)
            }

            else -> throw IllegalArgumentException(INVALID_ACTION_TYPE_MESSAGE)
        }
    }

    private fun requireWidgetAddress(action: LuaTable, limits: DikcizLimits): String {
        val address = requireBoundedString(
            action,
            WIDGET_ADDRESS_FIELD,
            limits.maxIdentifierCharacters + MAXIMUM_WIDGET_ADDRESS_OVERHEAD_CHARACTERS,
        )
        if (DikcizScriptWidgetAddress.parse(address) == null) {
            throw IllegalArgumentException(INVALID_WIDGET_ADDRESS_MESSAGE)
        }
        return address
    }

    private fun parseWidgetPatch(table: LuaTable, limits: DikcizLimits): JSONObject {
        val values = luaTableToJson(table, limits, ROOT_WIDGET_PATCH_DEPTH)
        if (values.length() !in MINIMUM_WIDGET_PATCH_FIELDS..MAXIMUM_WIDGET_PATCH_FIELDS) {
            throw IllegalArgumentException(INVALID_WIDGET_PATCH_MESSAGE)
        }
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (key) {
                TITLE_FIELD,
                TEXT_FIELD,
                HTML_FIELD,
                CSS_FIELD,
                JAVASCRIPT_FIELD,
                -> if (values.opt(key) !is String) {
                    throw IllegalArgumentException(INVALID_WIDGET_PATCH_MESSAGE)
                }

                CONTEXT_STATE_FIELD -> if (values.opt(key) !is JSONObject) {
                    throw IllegalArgumentException(INVALID_WIDGET_PATCH_MESSAGE)
                }

                else -> throw IllegalArgumentException(INVALID_WIDGET_PATCH_MESSAGE)
            }
        }
        return values
    }

    private fun parseDomPatch(table: LuaTable, limits: DikcizLimits): JSONObject {
        val values = JSONObject()
        var fieldCount = NO_FIELDS_SEEN
        var key = LuaValue.NIL
        while (true) {
            val entry = table.next(key)
            key = entry.arg1()
            if (key.isnil()) {
                break
            }
            fieldCount += ONE_FIELD
            if (fieldCount > MAXIMUM_DOM_PATCH_FIELDS || !key.isstring()) {
                throw IllegalArgumentException(INVALID_DOM_PATCH_MESSAGE)
            }
            val name = key.tojstring()
            when (name) {
                TEXT_FIELD,
                HTML_FIELD,
                VALUE_FIELD,
                CLASS_NAME_FIELD,
                -> values.put(name, requireBoundedStringValue(entry.arg(SECOND_RESULT_INDEX), limits))

                ATTRIBUTES_FIELD -> values.put(
                    name,
                    parseDomAttributes(entry.arg(SECOND_RESULT_INDEX).checktable(), limits),
                )

                else -> throw IllegalArgumentException(INVALID_DOM_PATCH_MESSAGE)
            }
        }
        if (fieldCount !in MINIMUM_DOM_PATCH_FIELDS..MAXIMUM_DOM_PATCH_FIELDS) {
            throw IllegalArgumentException(INVALID_DOM_PATCH_MESSAGE)
        }
        return values
    }

    private fun parseDomAttributes(table: LuaTable, limits: DikcizLimits): JSONObject {
        val attributes = JSONObject()
        var fieldCount = NO_FIELDS_SEEN
        var key = LuaValue.NIL
        while (true) {
            val entry = table.next(key)
            key = entry.arg1()
            if (key.isnil()) {
                return attributes
            }
            fieldCount += ONE_FIELD
            if (
                fieldCount > MAXIMUM_DOM_ATTRIBUTE_COUNT ||
                !key.isstring() ||
                !DOM_ATTRIBUTE_NAME_PATTERN.matches(key.tojstring())
            ) {
                throw IllegalArgumentException(INVALID_DOM_PATCH_MESSAGE)
            }
            attributes.put(key.tojstring(), requireBoundedStringValue(entry.arg(SECOND_RESULT_INDEX), limits))
        }
    }

    private fun requireBoundedStringValue(value: LuaValue, limits: DikcizLimits): String {
        val text = value.checkjstring()
        if (text.isEmpty() || text.length > limits.maxTextCharacters || NUL_CHARACTER in text) {
            throw IllegalArgumentException(INVALID_DOM_PATCH_MESSAGE)
        }
        return text
    }

    private fun optionalMediaSeekPosition(action: LuaTable): Long? {
        val value = action.get(POSITION_MILLISECONDS_FIELD)
        if (value.isnil()) {
            return null
        }
        if (!value.isnumber()) {
            throw IllegalArgumentException(INVALID_MEDIA_SEEK_POSITION_MESSAGE)
        }
        val position = value.checkdouble()
        if (
            !position.isFinite() ||
            position < MINIMUM_MEDIA_SEEK_MILLISECONDS ||
            position > MAXIMUM_MEDIA_SEEK_MILLISECONDS ||
            position != kotlin.math.floor(position)
        ) {
            throw IllegalArgumentException(INVALID_MEDIA_SEEK_POSITION_MESSAGE)
        }
        return position.toLong()
    }

    private fun requireMediaVolumePercent(action: LuaTable): Int {
        val value = action.get(LEVEL_PERCENT_FIELD)
        if (!value.isnumber()) {
            throw IllegalArgumentException(INVALID_MEDIA_VOLUME_PERCENT_MESSAGE)
        }
        val percent = value.checkdouble()
        if (
            !percent.isFinite() ||
            percent < MINIMUM_MEDIA_VOLUME_PERCENT ||
            percent > MAXIMUM_MEDIA_VOLUME_PERCENT ||
            percent != kotlin.math.floor(percent)
        ) {
            throw IllegalArgumentException(INVALID_MEDIA_VOLUME_PERCENT_MESSAGE)
        }
        return percent.toInt()
    }

    private fun requireGestureCoordinate(action: LuaTable, field: String): Int {
        val coordinate = action.get(field).checkdouble()
        if (
            !coordinate.isFinite() ||
            coordinate < MINIMUM_GESTURE_COORDINATE ||
            coordinate > DIKCIZ_ACCESSIBILITY_MAXIMUM_GESTURE_COORDINATE.toDouble() ||
            coordinate != kotlin.math.floor(coordinate)
        ) {
            throw IllegalArgumentException(INVALID_ACTION_TYPE_MESSAGE)
        }
        return coordinate.toInt()
    }

    private fun requireGestureDuration(action: LuaTable): Long {
        val duration = action.get(DURATION_MILLISECONDS_FIELD).checkdouble()
        if (
            !duration.isFinite() ||
            duration < DIKCIZ_ACCESSIBILITY_MINIMUM_GESTURE_DURATION_MILLISECONDS.toDouble() ||
            duration > DIKCIZ_ACCESSIBILITY_MAXIMUM_GESTURE_DURATION_MILLISECONDS.toDouble() ||
            duration != kotlin.math.floor(duration)
        ) {
            throw IllegalArgumentException(INVALID_ACTION_TYPE_MESSAGE)
        }
        return duration.toLong()
    }

    private fun optionalBoundedString(table: LuaTable, field: String, maximum: Int): String? {
        val value = table.get(field)
        if (value.isnil()) {
            return null
        }
        return requireBoundedString(table, field, maximum)
    }

    private fun optionalBoundedIdentifier(
        table: LuaTable,
        field: String,
        limits: DikcizLimits,
    ): String? {
        if (table.get(field).isnil()) {
            return null
        }
        return requireBoundedIdentifier(table, field, limits)
    }

    private fun luaTableToJson(
        table: LuaTable,
        limits: DikcizLimits,
        depth: Int,
    ): JSONObject {
        if (depth > MAXIMUM_STATE_PATCH_DEPTH) {
            throw IllegalArgumentException(STATE_PATCH_DEPTH_MESSAGE)
        }
        val result = JSONObject()
        var fieldsSeen = NO_FIELDS_SEEN
        var key = LuaValue.NIL
        while (true) {
            val entry = table.next(key)
            key = entry.arg1()
            if (key.isnil()) {
                return result
            }
            fieldsSeen += ONE_FIELD
            if (fieldsSeen > MAXIMUM_STATE_PATCH_ENTRIES || !key.isstring()) {
                throw IllegalArgumentException(INVALID_STATE_PATCH_MESSAGE)
            }
            val name = key.tojstring()
            if (!IDENTIFIER_PATTERN.matches(name)) {
                throw IllegalArgumentException(INVALID_STATE_PATCH_MESSAGE)
            }
            result.put(name, luaValueToJson(entry.arg(SECOND_RESULT_INDEX), limits, depth + ONE_STATE_LEVEL))
        }
    }

    private fun luaArrayToJson(
        table: LuaTable,
        limits: DikcizLimits,
        depth: Int,
    ): JSONArray {
        if (depth > MAXIMUM_STATE_PATCH_DEPTH) {
            throw IllegalArgumentException(STATE_PATCH_DEPTH_MESSAGE)
        }
        val result = JSONArray()
        var index = FIRST_LUA_ARRAY_INDEX
        while (!table.get(index).isnil()) {
            if (result.length() >= MAXIMUM_STATE_PATCH_ENTRIES) {
                throw IllegalArgumentException(INVALID_STATE_PATCH_MESSAGE)
            }
            result.put(luaValueToJson(table.get(index), limits, depth + ONE_STATE_LEVEL))
            index += FIRST_LUA_ARRAY_INDEX
        }
        var key = LuaValue.NIL
        while (true) {
            val entry = table.next(key)
            key = entry.arg1()
            if (key.isnil()) {
                return result
            }
            if (!key.isinttype() || key.toint() !in FIRST_LUA_ARRAY_INDEX until index) {
                throw IllegalArgumentException(INVALID_STATE_PATCH_MESSAGE)
            }
        }
    }

    private fun luaValueToJson(value: LuaValue, limits: DikcizLimits, depth: Int): Any {
        return when {
            value.isboolean() -> value.toboolean()
            value.type() == LuaValue.TSTRING -> value.tojstring().also { text ->
                if (text.length > limits.maxTextCharacters || NUL_CHARACTER in text) {
                    throw IllegalArgumentException(INVALID_STATE_PATCH_MESSAGE)
                }
            }

            value.isnumber() -> value.todouble().also { number ->
                if (!number.isFinite()) {
                    throw IllegalArgumentException(INVALID_STATE_PATCH_MESSAGE)
                }
            }

            value.istable() -> {
                val table = value.checktable()
                if (table.get(FIRST_LUA_ARRAY_INDEX).isnil()) {
                    luaTableToJson(table, limits, depth)
                } else {
                    luaArrayToJson(table, limits, depth)
                }
            }

            else -> throw IllegalArgumentException(INVALID_STATE_PATCH_MESSAGE)
        }
    }

    private fun requireBoundedIdentifier(
        table: LuaTable,
        field: String,
        limits: DikcizLimits,
    ): String {
        val value = requireBoundedString(table, field, limits.maxIdentifierCharacters)
        require(IDENTIFIER_PATTERN.matches(value))
        return value
    }

    private fun requireScriptAppAction(action: LuaTable): DikcizAppActionType {
        val value = requireBoundedString(action, ACTION_NAME_FIELD, APP_ACTION_MAXIMUM_CHARACTERS)
        val appAction = DikcizAppActionType.fromPersistedValue(value)
            ?: throw IllegalArgumentException(INVALID_APP_ACTION_MESSAGE)
        if (appAction in SCRIPT_DENIED_APP_ACTIONS) {
            throw IllegalArgumentException(PAGE_SCOPED_APP_ACTION_MESSAGE)
        }
        return appAction
    }

    /**
     * Reads a recipient that Android can dial.
     *
     * A script may build this from an inbound message or an external service,
     * so the shape is restricted to digits and dialling punctuation. That keeps
     * a URI, a component, or an intent out of the send path.
     */
    private fun requireDialableRecipient(action: LuaTable): String {
        val value = requireBoundedString(action, RECIPIENT_FIELD, MAXIMUM_RECIPIENT_CHARACTERS)
        require(RECIPIENT_PATTERN.matches(value))
        return value
    }

    private fun requireBoundedString(table: LuaTable, field: String, maximum: Int): String {
        val value = table.get(field).checkjstring()
        require(value.isNotEmpty())
        require(value.length <= maximum)
        require(NUL_CHARACTER !in value)
        return value
    }

    private fun requireExactKeys(table: LuaTable, expectedKeys: Set<String>) {
        var key = LuaValue.NIL
        var fieldsSeen = NO_FIELDS_SEEN
        while (true) {
            val entry = table.next(key)
            key = entry.arg1()
            if (key.isnil()) {
                return
            }
            fieldsSeen += ONE_FIELD
            require(fieldsSeen <= MAXIMUM_TABLE_FIELDS)
            require(key.isstring())
            require(key.tojstring() in expectedKeys)
        }
    }

    private fun requireAllowedKeys(table: LuaTable, allowedKeys: Set<String>) {
        var key = LuaValue.NIL
        var fieldsSeen = NO_FIELDS_SEEN
        while (true) {
            val entry = table.next(key)
            key = entry.arg1()
            if (key.isnil()) {
                return
            }
            fieldsSeen += ONE_FIELD
            require(fieldsSeen <= MAXIMUM_TABLE_FIELDS)
            require(key.isstring())
            require(key.tojstring() in allowedKeys)
        }
    }

    private fun scriptChunkName(scriptID: String): String = "@$SCRIPT_DIRECTORY_NAME/$scriptID/$SCRIPT_SOURCE_FILE_NAME"

    private class InstructionLimitHook(
        private val executionBudget: LuaExecutionBudget,
    ) : ZeroArgFunction() {
        private var executedInstructionCount = NO_EXECUTED_INSTRUCTIONS

        override fun call(): LuaValue {
            executedInstructionCount += INSTRUCTION_HOOK_INTERVAL
            executionBudget.throwIfExceeded()
            if (executedInstructionCount >= MAXIMUM_INSTRUCTION_COUNT) {
                throw DikcizLuaInstructionLimitExceeded()
            }
            return LuaValue.NIL
        }
    }

    private class LuaExecutionBudget(
        private val maximumDurationNanos: Long,
    ) {
        private val startedAtThreadCpuNanos = Debug.threadCpuTimeNanos()
        private val initialUsedMemoryBytes = usedMemoryBytes()

        fun throwIfExceeded() {
            if (Thread.currentThread().isInterrupted) {
                throw DikcizLuaExecutionCancelled()
            }
            if (Debug.threadCpuTimeNanos() - startedAtThreadCpuNanos >= maximumDurationNanos) {
                throw DikcizLuaExecutionDeadlineExceeded()
            }
            if (usedMemoryBytes() - initialUsedMemoryBytes >= MAXIMUM_EXECUTION_MEMORY_DELTA_BYTES) {
                throw DikcizLuaMemoryBudgetExceeded()
            }
        }

        private fun usedMemoryBytes(): Long {
            val runtime = Runtime.getRuntime()
            return runtime.totalMemory() - runtime.freeMemory()
        }
    }

    private class DikcizLuaExecutionCancelled : Error()
    private class DikcizLuaExecutionDeadlineExceeded : Error()

    private class DikcizLuaInstructionLimitExceeded : Error()

    private class DikcizLuaMemoryBudgetExceeded : Error()

    private class ReadOnlyLuaTable : LuaTable() {
        fun putInitial(key: String, value: LuaValue) {
            super.rawset(LuaValue.valueOf(key), value)
        }

        fun putInitial(key: Int, value: LuaValue) {
            super.rawset(key, value)
        }

        override fun insert(pos: Int, value: LuaValue) {
            rejectMutation()
        }

        override fun rawset(key: Int, value: LuaValue) {
            rejectMutation()
        }

        override fun rawset(key: LuaValue, value: LuaValue) {
            rejectMutation()
        }

        override fun remove(pos: Int): LuaValue = rejectMutation()

        override fun set(key: Int, value: LuaValue) {
            rejectMutation()
        }

        override fun set(key: LuaValue, value: LuaValue) {
            rejectMutation()
        }

        override fun setmetatable(metatable: LuaValue): LuaValue = rejectMutation()

        private fun rejectMutation(): Nothing = throw LuaError(READ_ONLY_LUA_STATE_MESSAGE)
    }

    private companion object {
        const val ACCESSIBILITY_ACTION_TYPE = "accessibilityAction"
        const val ACCESSIBILITY_GESTURE_ACTION_TYPE = "accessibilityGesture"
        const val ACCESSIBILITY_GLOBAL_ACTION_TYPE = "accessibilityGlobalAction"
        const val ACTION_NAME_FIELD = "action"
        const val ACTION_TOKEN_FIELD = "actionToken"
        const val ATTRIBUTES_FIELD = "attributes"
        const val CLASS_NAME_FIELD = "className"
        const val COMMAND_FIELD = "command"
        const val COMPONENT_FIELD = "component"
        const val CONTEXT_GLOBAL_NAME = "context"
        const val CONTEXT_ACCESSIBILITY_FIELD = "accessibility"
        const val CONTEXT_ANDROID_ACCESS_FIELD = "androidAccess"
        const val CONTEXT_SCRIPT_ID_FIELD = "scriptId"
        const val CONTEXT_STATE_FIELD = "state"
        const val CONTEXT_WIDGETS_FIELD = "widgets"
        const val CSS_FIELD = "css"
        const val DELETE_CHARACTER = '\u007f'
        const val DURATION_MILLISECONDS_FIELD = "durationMilliseconds"
        const val END_X_FIELD = "endX"
        const val END_Y_FIELD = "endY"
        const val DEBUG_GLOBAL_NAME = "debug"
        const val DEBUG_SET_HOOK_FUNCTION_NAME = "sethook"
        const val EVENT_COALESCING_KEY_FIELD = "coalescingKey"
        const val EVENT_ACTIONS_FIELD = "actions"
        const val EVENT_NAME_FIELD = "event"
        const val EVENT_HANDLER_GLOBAL_NAME = "on_event"
        const val EVENT_PAYLOAD_FIELD = "payload"
        const val EVENT_SOURCE_FIELD = "source"
        const val EVENT_STATUS_FIELD = "status"
        const val EVENT_TIMESTAMP_MILLISECONDS_FIELD = "timestampMilliseconds"
        const val EVENT_TIME_FIELD = "time"
        const val EVENT_TIME_HOUR_24_FIELD = "hour24"
        const val EVENT_TIME_MINUTE_FIELD = "minute"
        const val EVENT_TYPE_FIELD = "type"
        const val EXPLICIT_INTENT_ACTION_TYPE = "explicitIntent"
        const val EMIT_EVENT_ACTION_TYPE = "emitEvent"
        const val GESTURE_FIELD = "gesture"
        const val HTML_FIELD = "html"
        const val FIRST_LUA_ARRAY_INDEX = 1
        const val INSTRUCTION_HOOK_INTERVAL = 1_000
        const val INTENT_TYPE_FIELD = "intentType"
        const val JAVASCRIPT_FIELD = "javascript"
        const val LEVEL_PERCENT_FIELD = "levelPercent"
        const val INVALID_ACTION_TYPE_MESSAGE = "Lua action type is unsupported"
        const val MAXIMUM_EXECUTION_DURATION_NANOS = 50_000_000L
        const val MAXIMUM_SETUP_DURATION_NANOS = 250_000_000L
        const val MAXIMUM_EXECUTION_MEMORY_DELTA_BYTES = 2L * 1024L * 1024L
        const val MAXIMUM_INSTRUCTION_COUNT = 50_000
        const val MAXIMUM_EVENT_ACTIONS = 8
        const val MAXIMUM_ACCESSIBILITY_ACTION_CHARACTERS = 32
        const val MAXIMUM_ACCESSIBILITY_GESTURE_CHARACTERS = 16
        const val MAXIMUM_ACCESSIBILITY_NODE_ID_CHARACTERS = 128
        const val MAXIMUM_ACCESSIBILITY_SNAPSHOT_ID_CHARACTERS = 128
        const val MAXIMUM_DOM_ATTRIBUTE_COUNT = 16
        const val MAXIMUM_DOM_PATCH_FIELDS = 5
        const val MAXIMUM_DOM_SELECTOR_CHARACTERS = 512
        const val MAXIMUM_INTENT_ACTION_CHARACTERS = 256
        const val MAXIMUM_INTENT_TYPE_CHARACTERS = 16
        const val MAXIMUM_MEDIA_COMMAND_CHARACTERS = 32
        const val MAXIMUM_MEDIA_PACKAGE_NAME_CHARACTERS = 255
        const val MAXIMUM_MEDIA_SEEK_MILLISECONDS = 86_400_000.0
        const val MAXIMUM_MEDIA_VOLUME_PERCENT = 100.0
        const val MAXIMUM_LUA_DIAGNOSTIC_CHARACTERS = 256
        const val MAXIMUM_NOTIFICATION_ACTION_TOKEN_CHARACTERS = 36
        const val MAXIMUM_TABLE_FIELDS = 8
        const val MAXIMUM_WIDGET_ADDRESS_OVERHEAD_CHARACTERS = 16
        const val MAXIMUM_WIDGET_PATCH_FIELDS = 5
        const val MINIMUM_DIAGNOSTIC_CHARACTER = ' '
        const val NUL_CHARACTER = '\u0000'
        const val NODE_ID_FIELD = "nodeId"
        const val NO_EXECUTED_INSTRUCTIONS = 0
        const val NO_FIELDS_SEEN = 0
        const val ONE_FIELD = 1
        const val ONE_STATE_LEVEL = 1
        const val PAGE_ID_FIELD = "pageId"
        const val PACKAGE_NAME_FIELD = "packageName"
        const val POSITION_MILLISECONDS_FIELD = "positionMilliseconds"
        const val PRIVATE_SET_HOOK_GLOBAL_NAME = "__dikciz_set_hook"
        const val SCRIPT_DIRECTORY_NAME = "scripts"
        const val SCRIPT_EXECUTION_FAILED_MESSAGE = "Lua script execution failed"
        const val SCRIPT_SOURCE_FILE_NAME = "main.lua"
        const val SECOND_RESULT_INDEX = 2
        const val SELECT_PAGE_ACTION_TYPE = "selectPage"
        const val SNAPSHOT_ID_FIELD = "snapshotId"
        const val SELECTOR_FIELD = "selector"
        const val START_X_FIELD = "startX"
        const val START_Y_FIELD = "startY"
        const val STRING_LIBRARY_NAME = "string"
        const val TABLE_LIBRARY_NAME = "table"
        const val READ_ONLY_LUA_STATE_MESSAGE = "Lua context is read-only"
        const val ROOT_STATE_PATCH_DEPTH = 0
        const val ROOT_WIDGET_PATCH_DEPTH = 0
        const val MINIMUM_GESTURE_COORDINATE = 0.0
        const val MINIMUM_MEDIA_SEEK_MILLISECONDS = 0.0
        const val MINIMUM_MEDIA_VOLUME_PERCENT = 0.0
        const val MINIMUM_DOM_PATCH_FIELDS = 1
        const val MINIMUM_WIDGET_PATCH_FIELDS = 1
        const val BODY_FIELD = "body"
        const val RECIPIENT_FIELD = "recipient"
        const val MAXIMUM_RECIPIENT_CHARACTERS = 20
        val RECIPIENT_PATTERN = Regex("^\\+?[0-9][0-9 ()#*-]{1,19}$")
        const val TEXT_FIELD = "text"
        const val TITLE_FIELD = "title"
        const val TYPE_FIELD = "type"
        const val UNSUPPORTED_SCRIPT_STATE_VALUE_MESSAGE = "Lua state value is unsupported"
        const val VALUE_FIELD = "value"
        const val VALUES_FIELD = "values"
        const val WIDGET_ADDRESS_FIELD = "widgetAddress"
        const val X_FIELD = "x"
        const val Y_FIELD = "y"

        const val INVALID_EVENT_ACTION_LIST_MESSAGE = "Lua event actions must be an array"
        const val INVALID_CUSTOM_EVENT_NAME_MESSAGE = "Lua custom event name is invalid"
        const val INVALID_DOM_PATCH_MESSAGE = "Lua DOM patch is unsupported"
        const val INVALID_MEDIA_PACKAGE_NAME_MESSAGE = "media control package name is invalid"
        const val INVALID_MEDIA_SEEK_POSITION_MESSAGE = "media control seek position is invalid"
        const val INVALID_MEDIA_VOLUME_PERCENT_MESSAGE = "media volume percent is invalid"
        const val INVALID_NOTIFICATION_ACTION_TOKEN_MESSAGE = "notification action token is invalid"
        const val INVALID_STATE_PATCH_MESSAGE = "Lua state patch is unsupported"
        const val INVALID_WIDGET_ADDRESS_MESSAGE = "Lua widget address is invalid"
        const val INVALID_WIDGET_PATCH_MESSAGE = "Lua widget patch is unsupported"
        const val MISSING_MEDIA_SEEK_POSITION_MESSAGE = "media control seek needs a position"
        const val STATE_PATCH_DEPTH_MESSAGE = "Lua state patch is too deep"
        const val TOO_MANY_EVENT_ACTIONS_MESSAGE = "Lua returned too many event actions"
        const val UNEXPECTED_MEDIA_SEEK_POSITION_MESSAGE = "media control position only applies to seek"
        const val APP_ACTION_ACTION_TYPE = "appAction"
        const val APP_ACTION_MAXIMUM_CHARACTERS = 32
        const val INVALID_APP_ACTION_MESSAGE = "Lua app action is unsupported"
        const val PAGE_SCOPED_APP_ACTION_MESSAGE =
            "Lua addShortcut needs a selected page and is not available to a script action"
        const val LAUNCH_APP_ACTION_TYPE = "launchApp"
        const val LOCK_DEVICE_ACTION_TYPE = "lockDevice"
        const val PATCH_DOM_ACTION_TYPE = "patchDom"
        const val PATCH_STATE_ACTION_TYPE = "patchState"
        const val PATCH_WIDGET_ACTION_TYPE = "patchWidget"
        const val POST_NOTIFICATION_ACTION_TYPE = "postNotification"
        const val SEND_SMS_ACTION_TYPE = "sendSms"
        const val MEDIA_CONTROL_ACTION_TYPE = "mediaControl"
        const val SET_MEDIA_VOLUME_ACTION_TYPE = "setMediaVolume"
        const val NOTIFICATION_CONTROL_ACTION_TYPE = "notificationControl"

        const val MAXIMUM_STATE_PATCH_DEPTH = 8
        const val MAXIMUM_STATE_PATCH_ENTRIES = 128

        val ACCESSIBILITY_ACTION_KEYS = setOf(
            ACTION_NAME_FIELD,
            NODE_ID_FIELD,
            SNAPSHOT_ID_FIELD,
            TEXT_FIELD,
            TYPE_FIELD,
        )
        val ACCESSIBILITY_GLOBAL_ACTION_KEYS = setOf(ACTION_NAME_FIELD, TYPE_FIELD)
        val ACCESSIBILITY_TAP_GESTURE_KEYS = setOf(
            DURATION_MILLISECONDS_FIELD,
            GESTURE_FIELD,
            SNAPSHOT_ID_FIELD,
            TYPE_FIELD,
            X_FIELD,
            Y_FIELD,
        )
        val ACCESSIBILITY_SWIPE_GESTURE_KEYS = setOf(
            DURATION_MILLISECONDS_FIELD,
            END_X_FIELD,
            END_Y_FIELD,
            GESTURE_FIELD,
            SNAPSHOT_ID_FIELD,
            START_X_FIELD,
            START_Y_FIELD,
            TYPE_FIELD,
        )
        val EXPLICIT_INTENT_ACTION_KEYS = setOf(
            ACTION_NAME_FIELD,
            COMPONENT_FIELD,
            INTENT_TYPE_FIELD,
            TYPE_FIELD,
        )
        val EMIT_EVENT_ACTION_KEYS = setOf(
            EVENT_COALESCING_KEY_FIELD,
            EVENT_NAME_FIELD,
            EVENT_PAYLOAD_FIELD,
            TYPE_FIELD,
        )
        val DOM_ATTRIBUTE_NAME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9:_-]{0,63}$")
        val MEDIA_CONTROL_ACTION_KEYS = setOf(
            COMMAND_FIELD,
            PACKAGE_NAME_FIELD,
            POSITION_MILLISECONDS_FIELD,
            TYPE_FIELD,
        )
        val SET_MEDIA_VOLUME_ACTION_KEYS = setOf(LEVEL_PERCENT_FIELD, TYPE_FIELD)
        val NOTIFICATION_CONTROL_ACTION_KEYS = setOf(ACTION_TOKEN_FIELD, TYPE_FIELD)
        val FORBIDDEN_GLOBALS = setOf(
            "collectgarbage",
            "dofile",
            "getmetatable",
            "load",
            "loadfile",
            "module",
            "package",
            "print",
            "rawget",
            "rawset",
            "require",
            "setfenv",
            "setmetatable",
            "getfenv",
        )
        val IDENTIFIER_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_.-]*$")
        val ANDROID_PACKAGE_NAME_PATTERN = Regex(
            "^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$",
        )
        val NOTIFICATION_ACTION_TOKEN_PATTERN = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        )
        val SELECT_PAGE_ACTION_KEYS = setOf(PAGE_ID_FIELD, TYPE_FIELD)
        val LAUNCH_APP_ACTION_KEYS = setOf(COMPONENT_FIELD, TYPE_FIELD)
        val APP_ACTION_ACTION_KEYS = setOf(ACTION_NAME_FIELD, COMPONENT_FIELD, TYPE_FIELD)
        val SCRIPT_DENIED_APP_ACTIONS = setOf(DikcizAppActionType.AddShortcut)
        val LOCK_DEVICE_ACTION_KEYS = setOf(TYPE_FIELD)
        val PATCH_STATE_ACTION_KEYS = setOf(TYPE_FIELD, VALUES_FIELD)
        val PATCH_DOM_ACTION_KEYS = setOf(TYPE_FIELD, VALUES_FIELD, WIDGET_ADDRESS_FIELD, SELECTOR_FIELD)
        val PATCH_WIDGET_ACTION_KEYS = setOf(TYPE_FIELD, VALUES_FIELD, WIDGET_ADDRESS_FIELD)
        val POST_NOTIFICATION_ACTION_KEYS = setOf(TEXT_FIELD, TITLE_FIELD, TYPE_FIELD)
        val SEND_SMS_ACTION_KEYS = setOf(BODY_FIELD, RECIPIENT_FIELD, TYPE_FIELD)
        val EVENT_RESULT_KEYS = setOf(EVENT_ACTIONS_FIELD, EVENT_STATUS_FIELD)
        val UNBOUNDED_STRING_FUNCTIONS = setOf("format", "gsub", "rep")
        val UNBOUNDED_TABLE_FUNCTIONS = setOf("concat", "move", "unpack")
    }
}

internal sealed interface DikcizLuaAutomationResult {
    data class Actions(
        val actions: List<DikcizLuaAutomationAction>,
        val status: String?,
    ) : DikcizLuaAutomationResult

    data class Failure(
        val reason: DikcizLuaFailure,
        val diagnostic: String? = null,
    ) : DikcizLuaAutomationResult

    object Disabled : DikcizLuaAutomationResult

    object NoHandler : DikcizLuaAutomationResult
}

private data class DikcizLuaEventResult(
    val actions: List<DikcizLuaAutomationAction>,
    val status: String?,
)

internal enum class DikcizLuaFailure(
    val persistedValue: String,
) {
    Cancelled("cancelled"),
    ExecutionDeadlineExceeded("execution_deadline_exceeded"),
    InstructionBudgetExceeded("instruction_budget_exceeded"),
    InvalidLua("invalid_lua"),
    InvalidResult("invalid_result"),
    MemoryBudgetExceeded("memory_budget_exceeded"),
    ;

}
