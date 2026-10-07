package org.fossify.home.dikciz

import kotlin.math.floor
import org.json.JSONArray
import org.json.JSONObject

internal object DikcizAutomationActionDocument {
    fun parse(
        actions: JSONArray,
        sourceWidgetAddress: String,
        limits: DikcizLimits,
    ): List<DikcizLuaAutomationAction> {
        require(DikcizScriptWidgetAddress.parse(sourceWidgetAddress) != null) {
            MESSAGE_INVALID_WIDGET_ADDRESS
        }
        require(actions.length() in MINIMUM_ACTION_COUNT..MAXIMUM_ACTION_COUNT) {
            MESSAGE_INVALID_ACTION_LIST
        }
        return List(actions.length()) { index ->
            val action = actions.optJSONObject(index) ?: throw IllegalArgumentException(MESSAGE_INVALID_ACTION)
            parseAction(action, sourceWidgetAddress, limits)
        }
    }

    private fun requireScriptAppAction(action: JSONObject): DikcizAppActionType {
        val value = requireString(action, FIELD_ACTION, MAXIMUM_APP_ACTION_CHARACTERS)
        val appAction = DikcizAppActionType.fromPersistedValue(value)
            ?: throw IllegalArgumentException(MESSAGE_INVALID_APP_ACTION)
        if (appAction in SCRIPT_DENIED_APP_ACTIONS) {
            throw IllegalArgumentException(MESSAGE_PAGE_SCOPED_APP_ACTION)
        }
        return appAction
    }

    private fun parseAction(
        action: JSONObject,
        sourceWidgetAddress: String,
        limits: DikcizLimits,
    ): DikcizLuaAutomationAction {
        return when (requireString(action, FIELD_TYPE, MAXIMUM_ACTION_TYPE_CHARACTERS)) {
            ACTION_SELECT_PAGE -> {
                requireExactKeys(action, SELECT_PAGE_KEYS)
                DikcizLuaAutomationAction.SelectPage(requireIdentifier(action, FIELD_PAGE_ID, limits))
            }

            ACTION_PATCH_WIDGET -> {
                requireExactKeys(action, PATCH_WIDGET_KEYS)
                DikcizLuaAutomationAction.PatchWidget(
                    widgetAddress = requireWidgetAddress(action, limits),
                    values = requireWidgetPatch(action, limits),
                )
            }

            ACTION_PATCH_STATE -> {
                requireExactKeys(action, PATCH_STATE_KEYS)
                DikcizLuaAutomationAction.PatchWidget(
                    widgetAddress = sourceWidgetAddress,
                    values = JSONObject().put(FIELD_STATE, requireStatePatch(action, limits)),
                )
            }

            ACTION_PATCH_DOM -> {
                requireExactKeys(action, PATCH_DOM_KEYS)
                DikcizLuaAutomationAction.PatchDom(
                    widgetAddress = requireWidgetAddress(action, limits),
                    selector = requireString(action, FIELD_SELECTOR, MAXIMUM_DOM_SELECTOR_CHARACTERS),
                    values = requireDomPatch(action, limits),
                )
            }

            ACTION_LAUNCH_APP -> {
                requireExactKeys(action, LAUNCH_APP_KEYS)
                DikcizLuaAutomationAction.LaunchApp(requireString(action, FIELD_COMPONENT, limits.maxComponentCharacters))
            }

            ACTION_APP_ACTION -> {
                requireExactKeys(action, APP_ACTION_KEYS)
                DikcizLuaAutomationAction.AppAction(
                    action = requireScriptAppAction(action),
                    component = requireString(action, FIELD_COMPONENT, limits.maxComponentCharacters),
                )
            }

            ACTION_POST_NOTIFICATION -> {
                requireExactKeys(action, POST_NOTIFICATION_KEYS)
                DikcizLuaAutomationAction.PostNotification(
                    title = requireString(action, FIELD_TITLE, limits.maxTitleCharacters),
                    text = requireString(action, FIELD_TEXT, limits.maxTextCharacters),
                )
            }

            ACTION_SEND_SMS -> {
                requireExactKeys(action, SEND_SMS_KEYS)
                DikcizLuaAutomationAction.SendSms(
                    recipient = requireDialableRecipient(action),
                    body = requireString(action, FIELD_BODY, limits.maxTextCharacters),
                )
            }

            ACTION_EXPLICIT_INTENT -> {
                requireAllowedKeys(action, EXPLICIT_INTENT_KEYS, EXPLICIT_INTENT_REQUIRED_KEYS)
                val intentType = DikcizAutomationIntentType.fromPersistedValue(
                    requireString(action, FIELD_INTENT_TYPE, MAXIMUM_INTENT_TYPE_CHARACTERS),
                ) ?: throw IllegalArgumentException(MESSAGE_INVALID_ACTION)
                DikcizLuaAutomationAction.ExplicitIntent(
                    intentType = intentType,
                    component = requireString(action, FIELD_COMPONENT, limits.maxComponentCharacters),
                    action = optionalString(action, FIELD_ACTION, MAXIMUM_INTENT_ACTION_CHARACTERS),
                )
            }

            ACTION_EMIT_EVENT -> {
                requireAllowedKeys(action, EMIT_EVENT_KEYS, EMIT_EVENT_REQUIRED_KEYS)
                DikcizLuaAutomationAction.EmitEvent(
                    eventName = requireCustomEventName(action),
                    payload = requireEventPayload(action, limits),
                    coalescingKey = optionalIdentifier(action, FIELD_COALESCING_KEY, limits),
                )
            }

            ACTION_MEDIA_CONTROL -> {
                requireAllowedKeys(action, MEDIA_CONTROL_KEYS, MEDIA_CONTROL_REQUIRED_KEYS)
                val packageName = requireString(action, FIELD_PACKAGE_NAME, MAXIMUM_PACKAGE_NAME_CHARACTERS)
                require(PACKAGE_NAME_PATTERN.matches(packageName)) { MESSAGE_INVALID_ACTION }
                val command = DikcizMediaControlCommand.fromPersistedValue(
                    requireString(action, FIELD_COMMAND, MAXIMUM_MEDIA_COMMAND_CHARACTERS),
                ) ?: throw IllegalArgumentException(MESSAGE_INVALID_ACTION)
                val positionMilliseconds = optionalPositionMilliseconds(action)
                require(command == DikcizMediaControlCommand.SeekTo || positionMilliseconds == null) {
                    MESSAGE_INVALID_ACTION
                }
                require(command != DikcizMediaControlCommand.SeekTo || positionMilliseconds != null) {
                    MESSAGE_INVALID_ACTION
                }
                DikcizLuaAutomationAction.MediaControl(packageName, command, positionMilliseconds)
            }

            ACTION_SET_MEDIA_VOLUME -> {
                requireExactKeys(action, SET_MEDIA_VOLUME_KEYS)
                DikcizLuaAutomationAction.SetMediaVolume(requireVolumePercent(action))
            }

            ACTION_NOTIFICATION_CONTROL -> {
                requireExactKeys(action, NOTIFICATION_CONTROL_KEYS)
                val actionToken = requireString(
                    action,
                    FIELD_ACTION_TOKEN,
                    MAXIMUM_NOTIFICATION_ACTION_TOKEN_CHARACTERS,
                )
                require(NOTIFICATION_ACTION_TOKEN_PATTERN.matches(actionToken)) { MESSAGE_INVALID_ACTION }
                DikcizLuaAutomationAction.NotificationControl(actionToken)
            }

            ACTION_LOCK_DEVICE -> {
                requireExactKeys(action, LOCK_DEVICE_KEYS)
                DikcizLuaAutomationAction.LockDevice
            }

            ACTION_ACCESSIBILITY_ACTION -> {
                requireAllowedKeys(
                    action,
                    ACCESSIBILITY_ACTION_KEYS,
                    ACCESSIBILITY_ACTION_REQUIRED_KEYS,
                )
                DikcizLuaAutomationAction.AccessibilityAction(
                    snapshotID = requireString(
                        action,
                        FIELD_SNAPSHOT_ID,
                        MAXIMUM_ACCESSIBILITY_SNAPSHOT_ID_CHARACTERS,
                    ),
                    nodeID = requireString(
                        action,
                        FIELD_NODE_ID,
                        MAXIMUM_ACCESSIBILITY_NODE_ID_CHARACTERS,
                    ),
                    action = requireString(
                        action,
                        FIELD_ACTION,
                        MAXIMUM_ACCESSIBILITY_ACTION_CHARACTERS,
                    ),
                    text = optionalString(action, FIELD_TEXT, limits.maxTextCharacters),
                )
            }

            ACTION_ACCESSIBILITY_GESTURE -> {
                val gestureKind = DikcizAccessibilityGestureKind.fromPersistedValue(
                    requireString(action, FIELD_GESTURE, MAXIMUM_ACCESSIBILITY_GESTURE_CHARACTERS),
                ) ?: throw IllegalArgumentException(MESSAGE_INVALID_ACTION)
                val snapshotID = requireString(
                    action,
                    FIELD_SNAPSHOT_ID,
                    MAXIMUM_ACCESSIBILITY_SNAPSHOT_ID_CHARACTERS,
                )
                val durationMilliseconds = requireGestureDuration(action)
                when (gestureKind) {
                    DikcizAccessibilityGestureKind.Tap -> {
                        requireExactKeys(action, ACCESSIBILITY_TAP_GESTURE_KEYS)
                        val x = requireGestureCoordinate(action, FIELD_X)
                        val y = requireGestureCoordinate(action, FIELD_Y)
                        DikcizLuaAutomationAction.AccessibilityGesture(
                            DikcizAccessibilityGesture(
                                snapshotID = snapshotID,
                                kind = gestureKind,
                                startX = x,
                                startY = y,
                                endX = x,
                                endY = y,
                                durationMilliseconds = durationMilliseconds,
                            ),
                        )
                    }

                    DikcizAccessibilityGestureKind.Swipe -> {
                        requireExactKeys(action, ACCESSIBILITY_SWIPE_GESTURE_KEYS)
                        DikcizLuaAutomationAction.AccessibilityGesture(
                            DikcizAccessibilityGesture(
                                snapshotID = snapshotID,
                                kind = gestureKind,
                                startX = requireGestureCoordinate(action, FIELD_START_X),
                                startY = requireGestureCoordinate(action, FIELD_START_Y),
                                endX = requireGestureCoordinate(action, FIELD_END_X),
                                endY = requireGestureCoordinate(action, FIELD_END_Y),
                                durationMilliseconds = durationMilliseconds,
                            ),
                        )
                    }
                }
            }

            ACTION_ACCESSIBILITY_GLOBAL_ACTION -> {
                requireExactKeys(action, ACCESSIBILITY_GLOBAL_ACTION_KEYS)
                val globalAction = DikcizAccessibilityGlobalAction.fromPersistedValue(
                    requireString(action, FIELD_ACTION, MAXIMUM_ACCESSIBILITY_ACTION_CHARACTERS),
                ) ?: throw IllegalArgumentException(MESSAGE_INVALID_ACTION)
                DikcizLuaAutomationAction.AccessibilityGlobalAction(globalAction)
            }

            else -> throw IllegalArgumentException(MESSAGE_INVALID_ACTION)
        }
    }

    private fun requireGestureCoordinate(action: JSONObject, field: String): Int {
        val coordinate = action.opt(field) as? Number ?: throw IllegalArgumentException(MESSAGE_INVALID_ACTION)
        val value = coordinate.toDouble()
        require(
            value.isFinite() &&
                value in MINIMUM_GESTURE_COORDINATE..DIKCIZ_ACCESSIBILITY_MAXIMUM_GESTURE_COORDINATE.toDouble() &&
                value == floor(value),
        ) { MESSAGE_INVALID_ACTION }
        return value.toInt()
    }

    private fun requireGestureDuration(action: JSONObject): Long {
        val duration = action.opt(FIELD_DURATION_MILLISECONDS) as? Number
            ?: throw IllegalArgumentException(MESSAGE_INVALID_ACTION)
        val value = duration.toDouble()
        require(
            value.isFinite() &&
                value in DIKCIZ_ACCESSIBILITY_MINIMUM_GESTURE_DURATION_MILLISECONDS.toDouble()..
                    DIKCIZ_ACCESSIBILITY_MAXIMUM_GESTURE_DURATION_MILLISECONDS.toDouble() &&
                value == floor(value),
        ) { MESSAGE_INVALID_ACTION }
        return value.toLong()
    }

    private fun requireWidgetAddress(action: JSONObject, limits: DikcizLimits): String {
        val address = requireString(
            action,
            FIELD_WIDGET_ADDRESS,
            limits.maxIdentifierCharacters + MAXIMUM_WIDGET_ADDRESS_OVERHEAD_CHARACTERS,
        )
        require(DikcizScriptWidgetAddress.parse(address) != null) { MESSAGE_INVALID_WIDGET_ADDRESS }
        return address
    }

    private fun requireWidgetPatch(action: JSONObject, limits: DikcizLimits): JSONObject {
        val values = action.optJSONObject(FIELD_VALUES) ?: throw IllegalArgumentException(MESSAGE_INVALID_WIDGET_PATCH)
        require(values.length() in MINIMUM_WIDGET_PATCH_FIELDS..MAXIMUM_WIDGET_PATCH_FIELDS) {
            MESSAGE_INVALID_WIDGET_PATCH
        }
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (key) {
                FIELD_TITLE,
                FIELD_TEXT,
                FIELD_HTML,
                FIELD_CSS,
                FIELD_JAVASCRIPT,
                -> requireString(values, key, limits.maxTextCharacters)

                FIELD_STATE -> validateStateValue(values.opt(key), limits, ROOT_STATE_DEPTH)
                else -> throw IllegalArgumentException(MESSAGE_INVALID_WIDGET_PATCH)
            }
        }
        return JSONObject(values.toString())
    }

    private fun requireStatePatch(action: JSONObject, limits: DikcizLimits): JSONObject {
        val values = action.optJSONObject(FIELD_VALUES) ?: throw IllegalArgumentException(MESSAGE_INVALID_STATE_PATCH)
        validateStateValue(values, limits, ROOT_STATE_DEPTH)
        return JSONObject(values.toString())
    }

    private fun requireDomPatch(action: JSONObject, limits: DikcizLimits): JSONObject {
        val values = action.optJSONObject(FIELD_VALUES) ?: throw IllegalArgumentException(MESSAGE_INVALID_DOM_PATCH)
        require(values.length() in MINIMUM_DOM_PATCH_FIELDS..MAXIMUM_DOM_PATCH_FIELDS) {
            MESSAGE_INVALID_DOM_PATCH
        }
        val keys = values.keys()
        while (keys.hasNext()) {
            when (val key = keys.next()) {
                FIELD_TEXT,
                FIELD_HTML,
                FIELD_VALUE,
                FIELD_CLASS_NAME,
                -> requireString(values, key, limits.maxTextCharacters)

                FIELD_ATTRIBUTES -> requireDomAttributes(values.optJSONObject(key), limits)
                else -> throw IllegalArgumentException(MESSAGE_INVALID_DOM_PATCH)
            }
        }
        return JSONObject(values.toString())
    }

    private fun requireDomAttributes(attributes: JSONObject?, limits: DikcizLimits) {
        require(attributes != null && attributes.length() <= MAXIMUM_DOM_ATTRIBUTE_COUNT) {
            MESSAGE_INVALID_DOM_PATCH
        }
        val keys = attributes.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            require(DOM_ATTRIBUTE_NAME_PATTERN.matches(key)) { MESSAGE_INVALID_DOM_PATCH }
            requireString(attributes, key, limits.maxTextCharacters)
        }
    }

    private fun requireEventPayload(action: JSONObject, limits: DikcizLimits): JSONObject {
        val payload = action.optJSONObject(FIELD_PAYLOAD)
            ?: throw IllegalArgumentException(MESSAGE_INVALID_EVENT_PAYLOAD)
        validateStateValue(payload, limits, ROOT_STATE_DEPTH)
        return JSONObject(payload.toString())
    }

    private fun requireCustomEventName(action: JSONObject): String {
        val name = requireString(
            action,
            FIELD_EVENT,
            DikcizAutomationEventNames.MAXIMUM_CUSTOM_EVENT_NAME_CHARACTERS,
        )
        require(DikcizAutomationEventNames.isValidCustomName(name)) { MESSAGE_INVALID_EVENT_NAME }
        return name
    }

    private fun validateStateValue(value: Any?, limits: DikcizLimits, depth: Int) {
        require(depth <= MAXIMUM_STATE_DEPTH) { MESSAGE_INVALID_STATE_PATCH }
        when (value) {
            is Boolean -> Unit
            is Number -> require(value.toDouble().isFinite()) { MESSAGE_INVALID_STATE_PATCH }
            is String -> require(value.length <= limits.maxTextCharacters && NUL_CHARACTER !in value) {
                MESSAGE_INVALID_STATE_PATCH
            }

            is JSONObject -> validateStateObject(value, limits, depth)
            is JSONArray -> validateStateArray(value, limits, depth)
            else -> throw IllegalArgumentException(MESSAGE_INVALID_STATE_PATCH)
        }
    }

    private fun validateStateObject(value: JSONObject, limits: DikcizLimits, depth: Int) {
        require(value.length() <= MAXIMUM_STATE_ENTRIES) { MESSAGE_INVALID_STATE_PATCH }
        val keys = value.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            require(IDENTIFIER_PATTERN.matches(key)) { MESSAGE_INVALID_STATE_PATCH }
            validateStateValue(value.opt(key), limits, depth + NEXT_STATE_DEPTH)
        }
    }

    private fun validateStateArray(value: JSONArray, limits: DikcizLimits, depth: Int) {
        require(value.length() <= MAXIMUM_STATE_ENTRIES) { MESSAGE_INVALID_STATE_PATCH }
        repeat(value.length()) { index -> validateStateValue(value.opt(index), limits, depth + NEXT_STATE_DEPTH) }
    }

    private fun optionalPositionMilliseconds(action: JSONObject): Long? {
        if (!action.has(FIELD_POSITION_MILLISECONDS)) {
            return null
        }
        val value = action.opt(FIELD_POSITION_MILLISECONDS) as? Number
            ?: throw IllegalArgumentException(MESSAGE_INVALID_ACTION)
        val position = value.toDouble()
        require(
            position.isFinite() &&
                position in MINIMUM_POSITION_MILLISECONDS..MAXIMUM_POSITION_MILLISECONDS &&
                position == floor(position),
        ) { MESSAGE_INVALID_ACTION }
        return position.toLong()
    }

    private fun requireVolumePercent(action: JSONObject): Int {
        val value = action.opt(FIELD_LEVEL_PERCENT) as? Number
            ?: throw IllegalArgumentException(MESSAGE_INVALID_ACTION)
        val percent = value.toDouble()
        require(
            percent.isFinite() &&
                percent in MINIMUM_VOLUME_PERCENT..MAXIMUM_VOLUME_PERCENT &&
                percent == floor(percent),
        ) { MESSAGE_INVALID_ACTION }
        return percent.toInt()
    }

    private fun requireIdentifier(action: JSONObject, field: String, limits: DikcizLimits): String {
        val value = requireString(action, field, limits.maxIdentifierCharacters)
        require(IDENTIFIER_PATTERN.matches(value)) { MESSAGE_INVALID_ACTION }
        return value
    }

    private fun optionalIdentifier(
        action: JSONObject,
        field: String,
        limits: DikcizLimits,
    ): String? {
        if (!action.has(field)) {
            return null
        }
        return requireIdentifier(action, field, limits)
    }

    private fun optionalString(action: JSONObject, field: String, maximumLength: Int): String? {
        if (!action.has(field)) {
            return null
        }
        return requireString(action, field, maximumLength)
    }

    private fun requireString(action: JSONObject, field: String, maximumLength: Int): String {
        val value = action.opt(field) as? String ?: throw IllegalArgumentException(MESSAGE_INVALID_ACTION)
        require(value.isNotEmpty() && value.length <= maximumLength && NUL_CHARACTER !in value) {
            MESSAGE_INVALID_ACTION
        }
        return value
    }

    /**
     * Reads a recipient that Android can dial.
     *
     * A script may build this from an inbound message or an external service,
     * so the shape is restricted to digits and dialling punctuation. That keeps
     * a URI, a component, or an intent out of the send path.
     */
    private fun requireDialableRecipient(action: JSONObject): String {
        val value = requireString(action, FIELD_RECIPIENT, MAXIMUM_RECIPIENT_CHARACTERS)
        require(RECIPIENT_PATTERN.matches(value)) { MESSAGE_INVALID_ACTION }
        return value
    }

    private fun requireExactKeys(action: JSONObject, expectedKeys: Set<String>) {
        require(action.length() == expectedKeys.size) { MESSAGE_INVALID_ACTION }
        val keys = action.keys()
        while (keys.hasNext()) {
            require(keys.next() in expectedKeys) { MESSAGE_INVALID_ACTION }
        }
    }

    private fun requireAllowedKeys(
        action: JSONObject,
        allowedKeys: Set<String>,
        requiredKeys: Set<String>,
    ) {
        val keys = action.keys()
        while (keys.hasNext()) {
            require(keys.next() in allowedKeys) { MESSAGE_INVALID_ACTION }
        }
        require(requiredKeys.all(action::has)) { MESSAGE_INVALID_ACTION }
    }

    private const val ACTION_ACCESSIBILITY_ACTION = "accessibilityAction"
    private const val ACTION_ACCESSIBILITY_GESTURE = "accessibilityGesture"
    private const val ACTION_ACCESSIBILITY_GLOBAL_ACTION = "accessibilityGlobalAction"
    private const val ACTION_EXPLICIT_INTENT = "explicitIntent"
    private const val ACTION_EMIT_EVENT = "emitEvent"
    private const val ACTION_APP_ACTION = "appAction"
    private const val ACTION_LAUNCH_APP = "launchApp"
    private const val ACTION_LOCK_DEVICE = "lockDevice"
    private const val ACTION_MEDIA_CONTROL = "mediaControl"
    private const val ACTION_NOTIFICATION_CONTROL = "notificationControl"
    private const val ACTION_PATCH_DOM = "patchDom"
    private const val ACTION_PATCH_STATE = "patchState"
    private const val ACTION_PATCH_WIDGET = "patchWidget"
    private const val ACTION_POST_NOTIFICATION = "postNotification"
    private const val ACTION_SELECT_PAGE = "selectPage"
    private const val ACTION_SEND_SMS = "sendSms"
    private const val ACTION_SET_MEDIA_VOLUME = "setMediaVolume"
    private const val FIELD_ACTION = "action"
    private const val FIELD_BODY = "body"
    private const val FIELD_ACTION_TOKEN = "actionToken"
    private const val FIELD_ATTRIBUTES = "attributes"
    private const val FIELD_CLASS_NAME = "className"
    private const val FIELD_COMMAND = "command"
    private const val FIELD_COALESCING_KEY = "coalescingKey"
    private const val FIELD_COMPONENT = "component"
    private const val FIELD_CSS = "css"
    private const val FIELD_EVENT = "event"
    private const val FIELD_END_X = "endX"
    private const val FIELD_END_Y = "endY"
    private const val FIELD_DURATION_MILLISECONDS = "durationMilliseconds"
    private const val FIELD_GESTURE = "gesture"
    private const val FIELD_HTML = "html"
    private const val FIELD_INTENT_TYPE = "intentType"
    private const val FIELD_JAVASCRIPT = "javascript"
    private const val FIELD_LEVEL_PERCENT = "levelPercent"
    private const val FIELD_NODE_ID = "nodeId"
    private const val FIELD_PACKAGE_NAME = "packageName"
    private const val FIELD_PAYLOAD = "payload"
    private const val FIELD_PAGE_ID = "pageId"
    private const val FIELD_POSITION_MILLISECONDS = "positionMilliseconds"
    private const val FIELD_STATE = "state"
    private const val FIELD_START_X = "startX"
    private const val FIELD_START_Y = "startY"
    private const val FIELD_SNAPSHOT_ID = "snapshotId"
    private const val FIELD_SELECTOR = "selector"
    private const val FIELD_RECIPIENT = "recipient"
    private const val FIELD_TEXT = "text"
    private const val FIELD_TITLE = "title"
    private const val FIELD_TYPE = "type"
    private const val FIELD_VALUE = "value"
    private const val FIELD_VALUES = "values"
    private const val FIELD_WIDGET_ADDRESS = "widgetAddress"
    private const val FIELD_X = "x"
    private const val FIELD_Y = "y"
    private const val MAXIMUM_ACTION_COUNT = 8
    private const val MAXIMUM_DOM_ATTRIBUTE_COUNT = 16
    private const val MAXIMUM_DOM_PATCH_FIELDS = 5
    private const val MAXIMUM_DOM_SELECTOR_CHARACTERS = 512
    private const val MAXIMUM_ACCESSIBILITY_ACTION_CHARACTERS = 32
    private const val MAXIMUM_ACCESSIBILITY_GESTURE_CHARACTERS = 16
    private const val MAXIMUM_ACCESSIBILITY_NODE_ID_CHARACTERS = 128
    private const val MAXIMUM_ACCESSIBILITY_SNAPSHOT_ID_CHARACTERS = 128
    private const val MAXIMUM_ACTION_TYPE_CHARACTERS = 32
    private const val MAXIMUM_APP_ACTION_CHARACTERS = 32
    private const val MESSAGE_INVALID_APP_ACTION = "app action is unsupported"
    private const val MESSAGE_PAGE_SCOPED_APP_ACTION =
        "addShortcut needs a selected page and is not available to a script action"
    private val SCRIPT_DENIED_APP_ACTIONS = setOf(DikcizAppActionType.AddShortcut)
    private const val MAXIMUM_INTENT_ACTION_CHARACTERS = 256
    private const val MAXIMUM_INTENT_TYPE_CHARACTERS = 16
    private const val MAXIMUM_MEDIA_COMMAND_CHARACTERS = 32
    private const val MAXIMUM_NOTIFICATION_ACTION_TOKEN_CHARACTERS = 36
    private const val MAXIMUM_PACKAGE_NAME_CHARACTERS = 255
    private const val MAXIMUM_POSITION_MILLISECONDS = 86_400_000.0
    private const val MAXIMUM_STATE_DEPTH = 8
    private const val MAXIMUM_STATE_ENTRIES = 128
    private const val MAXIMUM_WIDGET_ADDRESS_OVERHEAD_CHARACTERS = 16
    private const val MAXIMUM_WIDGET_PATCH_FIELDS = 5
    private const val MAXIMUM_VOLUME_PERCENT = 100.0
    private const val MAXIMUM_RECIPIENT_CHARACTERS = 20
    private val RECIPIENT_PATTERN = Regex("^\\+?[0-9][0-9 ()#*-]{1,19}$")
    private const val MESSAGE_INVALID_ACTION = "automation action is invalid"
    private const val MESSAGE_INVALID_ACTION_LIST = "automation actions must be a non-empty bounded array"
    private const val MESSAGE_INVALID_DOM_PATCH = "automation DOM patch is invalid"
    private const val MESSAGE_INVALID_STATE_PATCH = "automation state patch is invalid"
    private const val MESSAGE_INVALID_WIDGET_ADDRESS = "automation widget address is invalid"
    private const val MESSAGE_INVALID_WIDGET_PATCH = "automation widget patch is invalid"
    private const val MINIMUM_ACTION_COUNT = 1
    private const val MINIMUM_DOM_PATCH_FIELDS = 1
    private const val MINIMUM_GESTURE_COORDINATE = 0.0
    private const val MINIMUM_POSITION_MILLISECONDS = 0.0
    private const val MINIMUM_VOLUME_PERCENT = 0.0
    private const val MINIMUM_WIDGET_PATCH_FIELDS = 1
    private const val NEXT_STATE_DEPTH = 1
    private const val NUL_CHARACTER = '\u0000'
    private const val ROOT_STATE_DEPTH = 0
    private val ACCESSIBILITY_ACTION_KEYS = setOf(
        FIELD_ACTION,
        FIELD_NODE_ID,
        FIELD_SNAPSHOT_ID,
        FIELD_TEXT,
        FIELD_TYPE,
    )
    private val ACCESSIBILITY_ACTION_REQUIRED_KEYS = setOf(
        FIELD_ACTION,
        FIELD_NODE_ID,
        FIELD_SNAPSHOT_ID,
        FIELD_TYPE,
    )
    private val ACCESSIBILITY_GLOBAL_ACTION_KEYS = setOf(FIELD_ACTION, FIELD_TYPE)
    private val ACCESSIBILITY_TAP_GESTURE_KEYS = setOf(
        FIELD_DURATION_MILLISECONDS,
        FIELD_GESTURE,
        FIELD_SNAPSHOT_ID,
        FIELD_TYPE,
        FIELD_X,
        FIELD_Y,
    )
    private val ACCESSIBILITY_SWIPE_GESTURE_KEYS = setOf(
        FIELD_DURATION_MILLISECONDS,
        FIELD_END_X,
        FIELD_END_Y,
        FIELD_GESTURE,
        FIELD_SNAPSHOT_ID,
        FIELD_START_X,
        FIELD_START_Y,
        FIELD_TYPE,
    )
    private val EXPLICIT_INTENT_KEYS = setOf(FIELD_ACTION, FIELD_COMPONENT, FIELD_INTENT_TYPE, FIELD_TYPE)
    private val EXPLICIT_INTENT_REQUIRED_KEYS = setOf(FIELD_COMPONENT, FIELD_INTENT_TYPE, FIELD_TYPE)
    private val EMIT_EVENT_KEYS = setOf(FIELD_COALESCING_KEY, FIELD_EVENT, FIELD_PAYLOAD, FIELD_TYPE)
    private val EMIT_EVENT_REQUIRED_KEYS = setOf(FIELD_EVENT, FIELD_PAYLOAD, FIELD_TYPE)
    private val DOM_ATTRIBUTE_NAME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9:_-]{0,63}$")
    private val IDENTIFIER_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_.-]*$")
    private val LAUNCH_APP_KEYS = setOf(FIELD_COMPONENT, FIELD_TYPE)
    private val APP_ACTION_KEYS = setOf(FIELD_ACTION, FIELD_COMPONENT, FIELD_TYPE)
    private val LOCK_DEVICE_KEYS = setOf(FIELD_TYPE)
    private val MEDIA_CONTROL_KEYS = setOf(
        FIELD_COMMAND,
        FIELD_PACKAGE_NAME,
        FIELD_POSITION_MILLISECONDS,
        FIELD_TYPE,
    )
    private val MEDIA_CONTROL_REQUIRED_KEYS = setOf(FIELD_COMMAND, FIELD_PACKAGE_NAME, FIELD_TYPE)
    private val NOTIFICATION_ACTION_TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]{1,36}$")
    private val NOTIFICATION_CONTROL_KEYS = setOf(FIELD_ACTION_TOKEN, FIELD_TYPE)
    private val PACKAGE_NAME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private val PATCH_STATE_KEYS = setOf(FIELD_TYPE, FIELD_VALUES)
    private val PATCH_DOM_KEYS = setOf(FIELD_TYPE, FIELD_VALUES, FIELD_WIDGET_ADDRESS, FIELD_SELECTOR)
    private val PATCH_WIDGET_KEYS = setOf(FIELD_TYPE, FIELD_VALUES, FIELD_WIDGET_ADDRESS)
    private val POST_NOTIFICATION_KEYS = setOf(FIELD_TEXT, FIELD_TITLE, FIELD_TYPE)
    private val SEND_SMS_KEYS = setOf(FIELD_BODY, FIELD_RECIPIENT, FIELD_TYPE)
    private val SELECT_PAGE_KEYS = setOf(FIELD_PAGE_ID, FIELD_TYPE)
    private val SET_MEDIA_VOLUME_KEYS = setOf(FIELD_LEVEL_PERCENT, FIELD_TYPE)
}
    private const val MESSAGE_INVALID_EVENT_NAME = "automation event name is invalid"
    private const val MESSAGE_INVALID_EVENT_PAYLOAD = "automation event payload is invalid"
