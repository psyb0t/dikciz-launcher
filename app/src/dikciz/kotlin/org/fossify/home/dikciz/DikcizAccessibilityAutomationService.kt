package org.fossify.home.dikciz

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

internal const val DIKCIZ_ACCESSIBILITY_MAXIMUM_GESTURE_COORDINATE = 10_000
internal const val DIKCIZ_ACCESSIBILITY_MAXIMUM_GESTURE_DURATION_MILLISECONDS = 10_000L
internal const val DIKCIZ_ACCESSIBILITY_MINIMUM_GESTURE_DURATION_MILLISECONDS = 1L

internal class DikcizAccessibilityAutomationService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        DikcizAccessibilityAutomation.connect(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        DikcizAccessibilityAutomation.recordWindowEvent(applicationContext, event)
    }

    override fun onInterrupt() {
        DikcizAccessibilityAutomation.interrupt(this)
    }

    override fun onDestroy() {
        DikcizAccessibilityAutomation.disconnect(this)
        super.onDestroy()
    }
}

internal class DikcizAccessibilityAutomationException(
    val code: String,
    message: String,
) : IllegalStateException(message)

internal object DikcizAccessibilityAutomation {
    private val snapshots = ConcurrentHashMap<String, AccessibilitySnapshotDescriptor>()

    @Volatile
    private var service: DikcizAccessibilityAutomationService? = null

    fun connect(service: DikcizAccessibilityAutomationService) {
        this.service = service
        snapshots.clear()
        recordLifecycleEvent(service.applicationContext, ACCESSIBILITY_STATE_CONNECTED)
    }

    fun disconnect(service: DikcizAccessibilityAutomationService) {
        if (this.service !== service) {
            return
        }
        this.service = null
        recordLifecycleEvent(service.applicationContext, ACCESSIBILITY_STATE_DISCONNECTED)
    }

    fun interrupt(service: DikcizAccessibilityAutomationService) {
        if (this.service !== service) {
            return
        }
        recordLifecycleEvent(service.applicationContext, ACCESSIBILITY_STATE_INTERRUPTED)
    }

    fun isEnabled(context: Context): Boolean {
        val component = ComponentName(context, DikcizAccessibilityAutomationService::class.java)
            .flattenToString()
        return Settings.Secure.getString(
            context.contentResolver,
            SECURE_SETTING_ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty().split(ACCESSIBILITY_SERVICE_SEPARATOR).any(component::equals)
    }

    @Synchronized
    fun snapshot(): JSONObject {
        val activeService = requireService()
        val root = activeService.rootInActiveWindow
            ?: throw DikcizAccessibilityAutomationException(
                ERROR_WINDOW_UNAVAILABLE,
                MESSAGE_WINDOW_UNAVAILABLE,
            )
        pruneExpiredSnapshots()
        if (snapshots.size >= MAXIMUM_SNAPSHOT_COUNT) {
            throw DikcizAccessibilityAutomationException(
                ERROR_SNAPSHOT_LIMIT,
                MESSAGE_SNAPSHOT_LIMIT,
            )
        }
        return captureSnapshot(root)
    }

    fun performAction(
        snapshotID: String,
        nodeID: String,
        actionName: String,
        text: String?,
    ): JSONObject {
        val activeService = requireService()
        val descriptor = snapshots[snapshotID]
            ?: throw DikcizAccessibilityAutomationException(
                ERROR_SNAPSHOT_UNAVAILABLE,
                MESSAGE_SNAPSHOT_UNAVAILABLE,
            )
        if (descriptor.expiresAtElapsedMilliseconds < SystemClock.elapsedRealtime()) {
            snapshots.remove(snapshotID)
            throw DikcizAccessibilityAutomationException(ERROR_SNAPSHOT_EXPIRED, MESSAGE_SNAPSHOT_EXPIRED)
        }
        val action = DikcizAccessibilityNodeAction.fromPersistedValue(actionName)
            ?: throw DikcizAccessibilityAutomationException(ERROR_ACTION_UNSUPPORTED, MESSAGE_ACTION_UNSUPPORTED)
        if (action.requiresText != (text != null)) {
            throw DikcizAccessibilityAutomationException(ERROR_ACTION_TEXT_INVALID, MESSAGE_ACTION_TEXT_INVALID)
        }
        if (text != null && text.length > MAXIMUM_SET_TEXT_CHARACTERS) {
            throw DikcizAccessibilityAutomationException(ERROR_ACTION_TEXT_INVALID, MESSAGE_ACTION_TEXT_INVALID)
        }
        val root = activeService.rootInActiveWindow
            ?: throw DikcizAccessibilityAutomationException(
                ERROR_WINDOW_UNAVAILABLE,
                MESSAGE_WINDOW_UNAVAILABLE,
            )
        if (!matchesDescriptor(descriptor, root)) {
            throw DikcizAccessibilityAutomationException(ERROR_SNAPSHOT_STALE, MESSAGE_SNAPSHOT_STALE)
        }
        val node = resolveNode(root, nodeID)
            ?: throw DikcizAccessibilityAutomationException(ERROR_NODE_UNAVAILABLE, MESSAGE_NODE_UNAVAILABLE)
        if (descriptor.nodeFingerprints[nodeID] != node.fingerprint()) {
            throw DikcizAccessibilityAutomationException(ERROR_SNAPSHOT_STALE, MESSAGE_SNAPSHOT_STALE)
        }
        val arguments = text?.let { value ->
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }
        }
        val accepted = node.performAction(action.androidActionID, arguments)
        return JSONObject()
            .put(KEY_ACTION, action.persistedValue)
            .put(KEY_NODE_ID, nodeID)
            .put(KEY_OUTCOME, if (accepted) OUTCOME_EXECUTED else OUTCOME_ANDROID_REJECTED)
            .put(KEY_SNAPSHOT_ID, snapshotID)
    }

    fun performGesture(gesture: DikcizAccessibilityGesture): JSONObject {
        val activeService = requireService()
        val descriptor = requireSnapshotDescriptor(gesture.snapshotID)
        val root = activeService.rootInActiveWindow
            ?: throw DikcizAccessibilityAutomationException(
                ERROR_WINDOW_UNAVAILABLE,
                MESSAGE_WINDOW_UNAVAILABLE,
            )
        if (!matchesDescriptor(descriptor, root)) {
            throw DikcizAccessibilityAutomationException(ERROR_SNAPSHOT_STALE, MESSAGE_SNAPSHOT_STALE)
        }
        if (!gesture.isWithin(activeService.resources.displayMetrics.widthPixels, activeService.resources.displayMetrics.heightPixels)) {
            throw DikcizAccessibilityAutomationException(ERROR_GESTURE_INVALID, MESSAGE_GESTURE_INVALID)
        }
        val completion = CountDownLatch(ONE_CALLBACK)
        var outcome = OUTCOME_GESTURE_CANCELLED
        val accepted = activeService.dispatchGesture(
            gesture.toPlatformGesture(),
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    outcome = OUTCOME_EXECUTED
                    completion.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    outcome = OUTCOME_GESTURE_CANCELLED
                    completion.countDown()
                }
            },
            Handler(Looper.getMainLooper()),
        )
        if (!accepted) {
            outcome = OUTCOME_ANDROID_REJECTED
        } else {
            try {
                if (!completion.await(GESTURE_RESULT_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)) {
                    outcome = OUTCOME_GESTURE_TIMEOUT
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw DikcizAccessibilityAutomationException(
                    ERROR_GESTURE_INTERRUPTED,
                    MESSAGE_GESTURE_INTERRUPTED,
                )
            }
        }
        return JSONObject()
            .put(KEY_GESTURE, gesture.kind.persistedValue)
            .put(KEY_OUTCOME, outcome)
            .put(KEY_SNAPSHOT_ID, gesture.snapshotID)
    }

    fun performGlobalAction(actionName: String): JSONObject {
        val action = DikcizAccessibilityGlobalAction.fromPersistedValue(actionName)
            ?: throw DikcizAccessibilityAutomationException(ERROR_GLOBAL_ACTION_UNSUPPORTED, MESSAGE_GLOBAL_ACTION_UNSUPPORTED)
        val accepted = requireService().performGlobalAction(action.androidActionID)
        return JSONObject()
            .put(KEY_ACTION, action.persistedValue)
            .put(KEY_OUTCOME, if (accepted) OUTCOME_EXECUTED else OUTCOME_ANDROID_REJECTED)
    }

    fun recordWindowEvent(context: Context, event: AccessibilityEvent) {
        if (event.eventType !in REPORTED_EVENT_TYPES) {
            return
        }
        val packageName = event.packageName?.toString()?.take(MAXIMUM_METADATA_CHARACTERS).orEmpty()
        val className = event.className?.toString()?.take(MAXIMUM_METADATA_CHARACTERS).orEmpty()
        DikcizAutomationEventBus.enqueue(
            context,
            DikcizAutomationEvent(
                type = DikcizAutomationEventType.AccessibilityWindow,
                source = SOURCE_ACCESSIBILITY_SERVICE,
                timestampMilliseconds = System.currentTimeMillis(),
                coalescingKey = "$packageName:${event.windowId}",
                payload = JSONObject()
                    .put(DikcizAutomationEventPayload.PACKAGE_NAME, packageName)
                    .put(KEY_CLASS_NAME, className)
                    .put(KEY_EVENT_TYPE, event.eventType)
                    .put(KEY_WINDOW_ID, event.windowId),
            ),
        )
    }

    private fun recordLifecycleEvent(context: Context, state: String) {
        DikcizAutomationEventBus.enqueue(
            context,
            DikcizAutomationEvent(
                type = DikcizAutomationEventType.AccessibilityServiceState,
                source = SOURCE_ACCESSIBILITY_SERVICE,
                timestampMilliseconds = System.currentTimeMillis(),
                coalescingKey = "$COALESCING_LIFECYCLE_PREFIX$state",
                payload = JSONObject()
                    .put(KEY_ENABLED, isEnabled(context))
                    .put(KEY_STATE, state),
            ),
        )
    }

    private fun captureSnapshot(root: AccessibilityNodeInfo): JSONObject {
        val nodes = JSONArray()
        val nodeFingerprints = linkedMapOf<String, String>()
        val queue = ArrayDeque<AccessibilityPendingNode>()
        queue.add(AccessibilityPendingNode(root, ROOT_NODE_ID, ROOT_DEPTH))
        var isTruncated = false
        while (queue.isNotEmpty()) {
            if (nodes.length() == MAXIMUM_NODE_COUNT) {
                isTruncated = true
                break
            }
            val pending = queue.removeFirst()
            nodes.put(pending.node.toDocument(pending.id))
            nodeFingerprints[pending.id] = pending.node.fingerprint()
            if (pending.depth == MAXIMUM_NODE_DEPTH) {
                isTruncated = isTruncated || pending.node.childCount > NO_CHILDREN
                continue
            }
            repeat(pending.node.childCount) { index ->
                val child = pending.node.getChild(index) ?: return@repeat
                queue.add(
                    AccessibilityPendingNode(
                        child,
                        "${pending.id}$NODE_ID_SEPARATOR$index",
                        pending.depth + NEXT_DEPTH,
                    ),
                )
            }
        }
        val snapshotID = UUID.randomUUID().toString()
        snapshots[snapshotID] = AccessibilitySnapshotDescriptor(
            expiresAtElapsedMilliseconds = SystemClock.elapsedRealtime() + SNAPSHOT_TTL_MILLISECONDS,
            nodeFingerprints = nodeFingerprints,
            packageName = root.packageName?.toString().orEmpty(),
            windowID = root.windowId,
        )
        return JSONObject()
            .put(KEY_NODE_COUNT, nodes.length())
            .put(KEY_NODES, nodes)
            .put(KEY_PACKAGE_NAME, root.packageName?.toString().orEmpty())
            .put(KEY_SNAPSHOT_ID, snapshotID)
            .put(KEY_TRUNCATED, isTruncated)
            .put(KEY_WINDOW_ID, root.windowId)
    }

    private fun resolveNode(root: AccessibilityNodeInfo, nodeID: String): AccessibilityNodeInfo? {
        if (nodeID.length > MAXIMUM_NODE_ID_CHARACTERS || !nodeID.startsWith(ROOT_NODE_ID)) {
            return null
        }
        if (nodeID == ROOT_NODE_ID) {
            return root
        }
        val path = nodeID.removePrefix(ROOT_NODE_ID + NODE_ID_SEPARATOR)
            .split(NODE_ID_SEPARATOR)
        if (path.isEmpty() || path.size > MAXIMUM_NODE_DEPTH) {
            return null
        }
        var current = root
        path.forEach { value ->
            val childIndex = value.toIntOrNull() ?: return null
            if (childIndex !in MINIMUM_CHILD_INDEX until current.childCount) {
                return null
            }
            current = current.getChild(childIndex) ?: return null
        }
        return current
    }

    private fun matchesDescriptor(
        descriptor: AccessibilitySnapshotDescriptor,
        root: AccessibilityNodeInfo,
    ): Boolean {
        if (root.windowId != descriptor.windowID) {
            return false
        }
        if (root.packageName?.toString().orEmpty() != descriptor.packageName) {
            return false
        }
        return snapshotNodeFingerprints(root) == descriptor.nodeFingerprints
    }

    private fun snapshotNodeFingerprints(root: AccessibilityNodeInfo): Map<String, String> {
        val nodeFingerprints = linkedMapOf<String, String>()
        val queue = ArrayDeque<AccessibilityPendingNode>()
        queue.add(AccessibilityPendingNode(root, ROOT_NODE_ID, ROOT_DEPTH))
        while (queue.isNotEmpty() && nodeFingerprints.size < MAXIMUM_NODE_COUNT) {
            val pending = queue.removeFirst()
            nodeFingerprints[pending.id] = pending.node.fingerprint()
            if (pending.depth == MAXIMUM_NODE_DEPTH) {
                continue
            }
            repeat(pending.node.childCount) { index ->
                val child = pending.node.getChild(index) ?: return@repeat
                queue.add(
                    AccessibilityPendingNode(
                        child,
                        "${pending.id}$NODE_ID_SEPARATOR$index",
                        pending.depth + NEXT_DEPTH,
                    ),
                )
            }
        }
        return nodeFingerprints
    }

    private fun requireService(): DikcizAccessibilityAutomationService {
        return service ?: throw DikcizAccessibilityAutomationException(
            ERROR_NOT_ENABLED,
            MESSAGE_NOT_ENABLED,
        )
    }

    private fun requireSnapshotDescriptor(snapshotID: String): AccessibilitySnapshotDescriptor {
        val descriptor = snapshots[snapshotID]
            ?: throw DikcizAccessibilityAutomationException(
                ERROR_SNAPSHOT_UNAVAILABLE,
                MESSAGE_SNAPSHOT_UNAVAILABLE,
            )
        if (descriptor.expiresAtElapsedMilliseconds < SystemClock.elapsedRealtime()) {
            snapshots.remove(snapshotID)
            throw DikcizAccessibilityAutomationException(ERROR_SNAPSHOT_EXPIRED, MESSAGE_SNAPSHOT_EXPIRED)
        }
        return descriptor
    }

    private fun pruneExpiredSnapshots() {
        val now = SystemClock.elapsedRealtime()
        snapshots.entries.removeIf { entry -> entry.value.expiresAtElapsedMilliseconds < now }
    }

    private fun AccessibilityNodeInfo.toDocument(nodeID: String): JSONObject {
        val bounds = Rect()
        getBoundsInScreen(bounds)
        return JSONObject()
            .put(KEY_ACTIONS, supportedActions())
            .put(KEY_BOUNDS, JSONObject()
                .put(KEY_BOTTOM, bounds.bottom)
                .put(KEY_LEFT, bounds.left)
                .put(KEY_RIGHT, bounds.right)
                .put(KEY_TOP, bounds.top))
            .put(KEY_CHECKABLE, isCheckable)
            .put(KEY_CHECKED, isChecked)
            .put(KEY_CLASS_NAME, boundedText(className))
            .put(KEY_CLICKABLE, isClickable)
            .put(KEY_CONTENT_DESCRIPTION, boundedText(contentDescription))
            .put(KEY_EDITABLE, isEditable)
            .put(KEY_ENABLED, isEnabled)
            .put(KEY_FOCUSABLE, isFocusable)
            .put(KEY_NODE_ID, nodeID)
            .put(KEY_PACKAGE_NAME, boundedText(packageName))
            .put(KEY_SCROLLABLE, isScrollable)
            .put(KEY_TEXT, boundedText(text))
            .put(KEY_VIEW_ID_RESOURCE_NAME, boundedText(viewIdResourceName))
            .put(KEY_VISIBLE_TO_USER, isVisibleToUser)
    }

    private fun AccessibilityNodeInfo.supportedActions(): JSONArray {
        val actionIDs = actionList.map { action -> action.id }.toSet()
        return JSONArray().apply {
            DikcizAccessibilityNodeAction.entries.forEach { action ->
                if (action.androidActionID in actionIDs) {
                    put(action.persistedValue)
                }
            }
        }
    }

    private fun AccessibilityNodeInfo.fingerprint(): String {
        return listOf(
            className?.toString().orEmpty(),
            contentDescription?.toString().orEmpty(),
            text?.toString().orEmpty(),
            viewIdResourceName.orEmpty(),
            childCount.toString(),
            isCheckable.toString(),
            isChecked.toString(),
            isEnabled.toString(),
        ).joinToString(FINGERPRINT_FIELD_SEPARATOR)
    }

    private fun boundedText(value: CharSequence?): Any {
        return value?.toString()?.take(MAXIMUM_METADATA_CHARACTERS) ?: JSONObject.NULL
    }

    private data class AccessibilityPendingNode(
        val node: AccessibilityNodeInfo,
        val id: String,
        val depth: Int,
    )

    private data class AccessibilitySnapshotDescriptor(
        val expiresAtElapsedMilliseconds: Long,
        val nodeFingerprints: Map<String, String>,
        val packageName: String,
        val windowID: Int,
    )

    private const val ACCESSIBILITY_SERVICE_SEPARATOR = ":"
    private const val ERROR_ACTION_TEXT_INVALID = "accessibility_action_text_invalid"
    private const val ERROR_ACTION_UNSUPPORTED = "accessibility_action_unsupported"
    private const val ERROR_GESTURE_INTERRUPTED = "accessibility_gesture_interrupted"
    private const val ERROR_GESTURE_INVALID = "accessibility_gesture_invalid"
    private const val ERROR_GLOBAL_ACTION_UNSUPPORTED = "accessibility_global_action_unsupported"
    private const val ERROR_NODE_UNAVAILABLE = "accessibility_node_unavailable"
    private const val ERROR_NOT_ENABLED = "accessibility_not_enabled"
    private const val ERROR_SNAPSHOT_EXPIRED = "accessibility_snapshot_expired"
    private const val ERROR_SNAPSHOT_LIMIT = "accessibility_snapshot_limit"
    private const val ERROR_SNAPSHOT_STALE = "accessibility_snapshot_stale"
    private const val ERROR_SNAPSHOT_UNAVAILABLE = "accessibility_snapshot_unavailable"
    private const val ERROR_WINDOW_UNAVAILABLE = "accessibility_window_unavailable"
    private const val FINGERPRINT_FIELD_SEPARATOR = "\u0000"
    private const val KEY_ACTION = "action"
    private const val KEY_ACTIONS = "actions"
    private const val KEY_BOTTOM = "bottom"
    private const val KEY_BOUNDS = "bounds"
    private const val KEY_CHECKABLE = "checkable"
    private const val KEY_CHECKED = "checked"
    private const val KEY_CLASS_NAME = "className"
    private const val KEY_CLICKABLE = "clickable"
    private const val KEY_CONTENT_DESCRIPTION = "contentDescription"
    private const val KEY_EDITABLE = "editable"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_EVENT_TYPE = "eventType"
    private const val KEY_FOCUSABLE = "focusable"
    private const val KEY_GESTURE = "gesture"
    private const val KEY_LEFT = "left"
    private const val KEY_NODE_COUNT = "nodeCount"
    private const val KEY_NODE_ID = "nodeId"
    private const val KEY_NODES = "nodes"
    private const val KEY_OUTCOME = "outcome"
    private const val KEY_PACKAGE_NAME = "packageName"
    private const val KEY_RIGHT = "right"
    private const val KEY_SCROLLABLE = "scrollable"
    private const val KEY_SNAPSHOT_ID = "snapshotId"
    private const val KEY_STATE = "state"
    private const val KEY_TEXT = "text"
    private const val KEY_TOP = "top"
    private const val KEY_TRUNCATED = "truncated"
    private const val KEY_VIEW_ID_RESOURCE_NAME = "viewIdResourceName"
    private const val KEY_VISIBLE_TO_USER = "visibleToUser"
    private const val KEY_WINDOW_ID = "windowId"
    private const val MAXIMUM_METADATA_CHARACTERS = 512
    private const val MAXIMUM_NODE_COUNT = 256
    private const val MAXIMUM_NODE_DEPTH = 16
    private const val MAXIMUM_NODE_ID_CHARACTERS = 128
    private const val MAXIMUM_SET_TEXT_CHARACTERS = 8_192
    private const val MAXIMUM_SNAPSHOT_COUNT = 32
    private const val MESSAGE_ACTION_TEXT_INVALID = "accessibility action text is invalid"
    private const val MESSAGE_ACTION_UNSUPPORTED = "accessibility action is not supported"
    private const val MESSAGE_GESTURE_INTERRUPTED = "accessibility gesture was interrupted"
    private const val MESSAGE_GESTURE_INVALID = "accessibility gesture is invalid"
    private const val MESSAGE_GLOBAL_ACTION_UNSUPPORTED = "accessibility global action is not supported"
    private const val MESSAGE_NODE_UNAVAILABLE = "accessibility node is unavailable"
    private const val MESSAGE_NOT_ENABLED = "Dikciz cross-app automation is not enabled in Android Accessibility settings"
    private const val MESSAGE_SNAPSHOT_EXPIRED = "accessibility snapshot expired"
    private const val MESSAGE_SNAPSHOT_LIMIT = "accessibility snapshot limit reached"
    private const val MESSAGE_SNAPSHOT_STALE = "accessibility snapshot no longer matches the active window"
    private const val MESSAGE_SNAPSHOT_UNAVAILABLE = "accessibility snapshot is unavailable"
    private const val MESSAGE_WINDOW_UNAVAILABLE = "active Android window is unavailable"
    private const val MINIMUM_CHILD_INDEX = 0
    private const val NEXT_DEPTH = 1
    private const val NODE_ID_SEPARATOR = "."
    private const val NO_CHILDREN = 0
    private const val ONE_CALLBACK = 1
    private const val OUTCOME_ANDROID_REJECTED = "android_rejected"
    private const val OUTCOME_EXECUTED = "executed"
    private const val OUTCOME_GESTURE_CANCELLED = "gesture_cancelled"
    private const val OUTCOME_GESTURE_TIMEOUT = "gesture_timeout"
    private const val ROOT_DEPTH = 0
    private const val ROOT_NODE_ID = "node"
    private const val SECURE_SETTING_ENABLED_ACCESSIBILITY_SERVICES = "enabled_accessibility_services"
    private const val SNAPSHOT_TTL_MILLISECONDS = 5_000L
    private const val GESTURE_RESULT_TIMEOUT_MILLISECONDS = 5_000L
    private const val SOURCE_ACCESSIBILITY_SERVICE = "accessibility_service"
    private const val ACCESSIBILITY_STATE_CONNECTED = "connected"
    private const val ACCESSIBILITY_STATE_DISCONNECTED = "disconnected"
    private const val ACCESSIBILITY_STATE_INTERRUPTED = "interrupted"
    private const val COALESCING_LIFECYCLE_PREFIX = "accessibility_service:"
    private val REPORTED_EVENT_TYPES = setOf(
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        AccessibilityEvent.TYPE_WINDOWS_CHANGED,
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
    )
}

internal data class DikcizAccessibilityGesture(
    val snapshotID: String,
    val kind: DikcizAccessibilityGestureKind,
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int,
    val durationMilliseconds: Long,
) {
    fun isWithin(widthPixels: Int, heightPixels: Int): Boolean {
        return widthPixels > 0 && heightPixels > 0 &&
            startX in 0 until widthPixels && startY in 0 until heightPixels &&
            endX in 0 until widthPixels && endY in 0 until heightPixels &&
            durationMilliseconds in DIKCIZ_ACCESSIBILITY_MINIMUM_GESTURE_DURATION_MILLISECONDS..
                DIKCIZ_ACCESSIBILITY_MAXIMUM_GESTURE_DURATION_MILLISECONDS &&
            (kind != DikcizAccessibilityGestureKind.Swipe || startX != endX || startY != endY)
    }

    fun toPlatformGesture(): GestureDescription {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            if (kind == DikcizAccessibilityGestureKind.Swipe) {
                lineTo(endX.toFloat(), endY.toFloat())
            }
        }
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, GESTURE_START_OFFSET_MILLISECONDS, durationMilliseconds))
            .build()
    }

    private companion object {
        const val GESTURE_START_OFFSET_MILLISECONDS = 0L
    }
}

internal enum class DikcizAccessibilityGestureKind(
    val persistedValue: String,
) {
    Tap("tap"),
    Swipe("swipe"),
    ;

    companion object {
        fun fromPersistedValue(value: String): DikcizAccessibilityGestureKind? {
            return entries.firstOrNull { gesture -> gesture.persistedValue == value }
        }
    }
}

internal enum class DikcizAccessibilityGlobalAction(
    val persistedValue: String,
    val androidActionID: Int,
) {
    Back("back", AccessibilityService.GLOBAL_ACTION_BACK),
    Home("home", AccessibilityService.GLOBAL_ACTION_HOME),
    Recents("recents", AccessibilityService.GLOBAL_ACTION_RECENTS),
    Notifications("notifications", AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS),
    QuickSettings("quickSettings", AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS),
    LockScreen("lockScreen", AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN),
    TakeScreenshot("takeScreenshot", AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT),
    ;

    companion object {
        fun fromPersistedValue(value: String): DikcizAccessibilityGlobalAction? {
            return entries.firstOrNull { action -> action.persistedValue == value }
        }
    }
}

internal enum class DikcizAccessibilityNodeAction(
    val persistedValue: String,
    val androidActionID: Int,
    val requiresText: Boolean = false,
) {
    Click("click", AccessibilityNodeInfo.ACTION_CLICK),
    LongClick("longClick", AccessibilityNodeInfo.ACTION_LONG_CLICK),
    Focus("focus", AccessibilityNodeInfo.ACTION_FOCUS),
    ClearFocus("clearFocus", AccessibilityNodeInfo.ACTION_CLEAR_FOCUS),
    ScrollForward("scrollForward", AccessibilityNodeInfo.ACTION_SCROLL_FORWARD),
    ScrollBackward("scrollBackward", AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD),
    SetText("setText", AccessibilityNodeInfo.ACTION_SET_TEXT, true),
    Select("select", AccessibilityNodeInfo.ACTION_SELECT),
    ;

    companion object {
        fun fromPersistedValue(value: String): DikcizAccessibilityNodeAction? {
            return entries.firstOrNull { action -> action.persistedValue == value }
        }
    }
}
