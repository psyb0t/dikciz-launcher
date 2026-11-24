package org.fossify.home.dikciz

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
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
import java.util.concurrent.atomic.AtomicReference
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
    /** One touch "person" per process, created on the first humanized gesture. */
    private var touchModel: DikcizTouchModel? = null
    private val snapshots = ConcurrentHashMap<String, AccessibilitySnapshotDescriptor>()
    private class ContentEventWindow(
        var lastPublished: Long,
        var pending: Runnable? = null,
        var latest: DikcizAutomationEvent? = null,
    )
    private val contentEvents = linkedMapOf<Pair<String, Int>, ContentEventWindow>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var service: DikcizAccessibilityAutomationService? = null

    fun connect(service: DikcizAccessibilityAutomationService) {
        this.service = service
        snapshots.clear()
        clearContentEvents()
        recordLifecycleEvent(service.applicationContext, ACCESSIBILITY_STATE_CONNECTED)
    }

    fun disconnect(service: DikcizAccessibilityAutomationService) {
        if (this.service !== service) {
            return
        }
        this.service = null
        clearContentEvents()
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

    fun foregroundPackage(): String? {
        val root = requireService().rootInActiveWindow ?: return null
        return try {
            if (root.refresh() && root.isVisibleToUser) root.packageName?.toString() else null
        } finally {
            root.recycle()
        }
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

    fun screenshot(): Bitmap {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw DikcizAccessibilityAutomationException(
                "unsupported_android_version",
                "display screenshots require Android 11 or newer",
            )
        }
        val activeService = requireService()
        val completion = CountDownLatch(1)
        val result = AtomicReference<Bitmap?>()
        val failure = AtomicReference<Int?>()
        val guard = Any()
        var expired = false
        activeService.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            activeService.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val bitmap = try {
                        screenshot.hardwareBuffer.use { buffer ->
                            val hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            try {
                                hardware?.copy(Bitmap.Config.ARGB_8888, false)
                            } finally {
                                hardware?.recycle()
                            }
                        }
                    } catch (_: RuntimeException) {
                        failure.set(AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR)
                        null
                    }
                    synchronized(guard) {
                        if (expired) bitmap?.recycle() else result.set(bitmap)
                        completion.countDown()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    failure.set(errorCode)
                    completion.countDown()
                }
            },
        )
        val completed = try {
            completion.await(GESTURE_RESULT_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        synchronized(guard) {
            if (!completed) {
                expired = true
                result.getAndSet(null)?.recycle()
                throw DikcizAccessibilityAutomationException("timeout", "display screenshot did not complete in time")
            }
            return result.get() ?: throw DikcizAccessibilityAutomationException(
                "display_screenshot_failed",
                "Android rejected display screenshot with status ${failure.get()}",
            )
        }
    }

    fun performAction(
        snapshotID: String,
        nodeID: String,
        actionName: String,
        text: String?,
        allowDikcizControl: Boolean = true,
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
        if (!allowDikcizControl && root.packageName?.toString().let {
                it == activeService.packageName || it == "eu.psyb0t.dikciz.launcher" || it == "eu.psyb0t.dikciz.launcher.debug"
            }) {
            throw DikcizAutomationRequestException(DikcizRemotePolicy.ERROR_DENIED, "Scoped UI control cannot change Dikciz")
        }
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
        val accepted = (!action.usesClipboard || node.allowsClipboardAction(action)) &&
            node.performAction(action.androidActionID, arguments)
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
        val metrics = activeService.resources.displayMetrics
        if (!gesture.isWithin(metrics.widthPixels, metrics.heightPixels)) {
            throw DikcizAccessibilityAutomationException(ERROR_GESTURE_INVALID, MESSAGE_GESTURE_INVALID)
        }
        val (outcome, duration) = if (gesture.humanize) {
            val plan = synchronized(this) { gesture.plan(touchModel(metrics.xdpi)) }
            val slices = if (plan.continuous) {
                continuousSlices(plan, metrics.widthPixels, metrics.heightPixels)
            } else {
                listOf(discreteGesture(plan, metrics.widthPixels, metrics.heightPixels))
            }
            var result = OUTCOME_EXECUTED
            for ((index, slice) in slices.withIndex()) {
                // The whole gesture's length bounds any one slice's completion wait.
                result = dispatchAndWait(activeService, slice, plan.totalDurationMilliseconds)
                if (result != OUTCOME_EXECUTED) {
                    android.util.Log.w(LOG_TAG, "humanized ${gesture.kind.persistedValue} slice ${index + 1}/${slices.size}: $result")
                    break
                }
            }
            result to plan.totalDurationMilliseconds
        } else {
            dispatchAndWait(activeService, gesture.toPlatformGesture(), gesture.totalDurationMilliseconds) to
                gesture.totalDurationMilliseconds
        }
        return JSONObject()
            .put(KEY_GESTURE, gesture.kind.persistedValue)
            .put(KEY_OUTCOME, outcome)
            .put(KEY_SNAPSHOT_ID, gesture.snapshotID)
            .put(KEY_HUMANIZED, gesture.humanize)
            .put(KEY_DURATION_MILLISECONDS, duration)
    }

    private fun touchModel(xdpi: Float): DikcizTouchModel =
        touchModel ?: DikcizTouchModel(
            random = kotlin.random.Random(java.security.SecureRandom().nextLong()),
            pixelsPerMillimetre = (if (xdpi > 0f) xdpi else FALLBACK_DPI) / MILLIMETRES_PER_INCH,
        ).also { touchModel = it }

    private fun dispatchAndWait(
        service: DikcizAccessibilityAutomationService,
        description: GestureDescription,
        durationMilliseconds: Long,
    ): String {
        val completion = CountDownLatch(ONE_CALLBACK)
        val callbackOutcome = AtomicReference(OUTCOME_GESTURE_CANCELLED)
        val accepted = service.dispatchGesture(
            description,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    callbackOutcome.set(OUTCOME_EXECUTED)
                    completion.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    callbackOutcome.set(OUTCOME_GESTURE_CANCELLED)
                    completion.countDown()
                }
            },
            Handler(Looper.getMainLooper()),
        )
        if (!accepted) return OUTCOME_ANDROID_REJECTED
        return try {
            if (completion.await(durationMilliseconds + GESTURE_RESULT_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)) {
                callbackOutcome.get()
            } else {
                OUTCOME_GESTURE_TIMEOUT
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw DikcizAccessibilityAutomationException(ERROR_GESTURE_INTERRUPTED, MESSAGE_GESTURE_INTERRUPTED)
        }
    }

    /**
     * Taps, holds and double taps: one stroke per press along its drift path. A
     * stroke moves at constant speed, which is fine for a few pixels of roll.
     */
    private fun discreteGesture(plan: DikcizTouchPlan, width: Int, height: Int): GestureDescription {
        val builder = GestureDescription.Builder()
        plan.pointers.forEach { pointer ->
            val path = Path()
            pointer.samples.forEachIndexed { index, sample ->
                val x = clampCoordinate(sample.x, width)
                val y = clampCoordinate(sample.y, height)
                if (index == 0) path.moveTo(x, y) else if (pointer.moves) path.lineTo(x, y)
            }
            val duration = pointer.samples.last().timeMilliseconds.coerceAtLeast(1L)
            builder.addStroke(GestureDescription.StrokeDescription(path, pointer.startMilliseconds, duration))
        }
        return builder.build()
    }

    /**
     * Swipes: one GestureDescription per CONTINUOUS_SLICE_MILLISECONDS, each finger
     * continuing its stroke, so the speed changes along the path the way the plan
     * says instead of Android's constant speed for a single stroke.
     */
    private fun continuousSlices(plan: DikcizTouchPlan, width: Int, height: Int): List<GestureDescription> {
        val total = plan.totalDurationMilliseconds
        val boundaries = sliceBoundaries(plan, width, height)
        val previous = arrayOfNulls<GestureDescription.StrokeDescription>(plan.pointers.size)
        val slices = ArrayList<GestureDescription>(boundaries.size)
        for ((sliceStart, sliceEnd) in boundaries.zipWithNext()) {
            val builder = GestureDescription.Builder()
            var strokes = 0
            plan.pointers.forEachIndexed { index, pointer ->
                val segmentStart = maxOf(sliceStart, pointer.startMilliseconds)
                val segmentEnd = minOf(sliceEnd, pointer.endMilliseconds)
                if (segmentEnd <= segmentStart) return@forEachIndexed
                val path = pathBetween(
                    pointer,
                    segmentStart - pointer.startMilliseconds,
                    segmentEnd - pointer.startMilliseconds,
                    width,
                    height,
                )
                val continues = pointer.endMilliseconds > sliceEnd
                val stroke = previous[index]?.continueStroke(path, segmentStart - sliceStart, segmentEnd - segmentStart, continues)
                    ?: GestureDescription.StrokeDescription(path, segmentStart - sliceStart, segmentEnd - segmentStart, continues)
                builder.addStroke(stroke)
                previous[index] = stroke
                strokes++
            }
            if (strokes > 0) slices.add(builder.build())
        }
        return slices
    }

    /**
     * The path between two slice edges. Android rounds a stroke's first and last
     * points to whole pixels and continues a stroke only when the new first point
     * equals the old last point, so both edges sit on whole pixels.
     */
    /**
     * Slice edges about CONTINUOUS_SLICE_MILLISECONDS apart. Android rounds sampled
     * points to whole pixels and fails a continued gesture that produces no motion
     * event, so a slice is extended until some finger moves at least one pixel. The
     * last slice always ends the gesture.
     */
    private fun sliceBoundaries(plan: DikcizTouchPlan, width: Int, height: Int): List<Long> {
        val total = plan.totalDurationMilliseconds
        val boundaries = mutableListOf(0L)
        var start = 0L
        while (start < total) {
            var end = minOf(total, start + CONTINUOUS_SLICE_MILLISECONDS)
            while (end < total && plan.pointers.none { movesWholePixel(it, start, end, width, height) }) {
                end = minOf(total, end + SLICE_EXTENSION_MILLISECONDS)
            }
            boundaries.add(end)
            start = end
        }
        return boundaries
    }

    private fun movesWholePixel(pointer: DikcizPlannedPointer, from: Long, to: Long, width: Int, height: Int): Boolean {
        if (pointer.endMilliseconds <= from || pointer.startMilliseconds > to) return false
        // Going down or lifting inside the slice is itself a motion event.
        if (pointer.startMilliseconds >= from || pointer.endMilliseconds <= to) return true
        val (startX, startY) = pointAt(pointer, from - pointer.startMilliseconds)
        val (endX, endY) = pointAt(pointer, to - pointer.startMilliseconds)
        return snapCoordinate(startX, width) != snapCoordinate(endX, width) ||
            snapCoordinate(startY, height) != snapCoordinate(endY, height)
    }

    private fun pathBetween(pointer: DikcizPlannedPointer, from: Long, to: Long, width: Int, height: Int): Path {
        val path = Path()
        val (startX, startY) = pointAt(pointer, from)
        path.moveTo(snapCoordinate(startX, width), snapCoordinate(startY, height))
        pointer.samples.filter { it.timeMilliseconds in (from + 1) until to }.forEach {
            path.lineTo(clampCoordinate(it.x, width), clampCoordinate(it.y, height))
        }
        val (endX, endY) = pointAt(pointer, to)
        path.lineTo(snapCoordinate(endX, width), snapCoordinate(endY, height))
        return path
    }

    private fun snapCoordinate(value: Float, size: Int): Float = kotlin.math.round(clampCoordinate(value, size))

    /** The planned position at `time` ms after the finger went down. */
    private fun pointAt(pointer: DikcizPlannedPointer, time: Long): Pair<Float, Float> {
        val samples = pointer.samples
        if (time <= samples.first().timeMilliseconds) return samples.first().x to samples.first().y
        if (time >= samples.last().timeMilliseconds) return samples.last().x to samples.last().y
        val after = samples.indexOfFirst { it.timeMilliseconds >= time }
        val a = samples[after - 1]
        val b = samples[after]
        val share = (time - a.timeMilliseconds).toFloat() / (b.timeMilliseconds - a.timeMilliseconds)
        return a.x + (b.x - a.x) * share to a.y + (b.y - a.y) * share
    }

    private fun clampCoordinate(value: Float, size: Int): Float = value.coerceIn(0f, (size - 1).toFloat())

    fun performGlobalAction(actionName: String): JSONObject {
        val action = DikcizAccessibilityGlobalAction.fromPersistedValue(actionName)
            ?: throw DikcizAccessibilityAutomationException(ERROR_GLOBAL_ACTION_UNSUPPORTED, MESSAGE_GLOBAL_ACTION_UNSUPPORTED)
        val accepted = requireService().performGlobalAction(action.androidActionID)
        return JSONObject()
            .put(KEY_ACTION, action.persistedValue)
            .put(KEY_OUTCOME, if (accepted) OUTCOME_EXECUTED else OUTCOME_ANDROID_REJECTED)
    }

    fun recordWindowEvent(context: Context, event: AccessibilityEvent) {
        if (event.eventType !in REPORTED_EVENT_TYPES) return
        val packageName = event.packageName?.toString()?.take(MAXIMUM_METADATA_CHARACTERS).orEmpty()
        val className = event.className?.toString()?.take(MAXIMUM_METADATA_CHARACTERS).orEmpty()
        val window = packageName to event.windowId
        val document = DikcizAutomationEvent(
            type = DikcizAutomationEventType.AccessibilityWindow,
            source = SOURCE_ACCESSIBILITY_SERVICE,
            timestampMilliseconds = System.currentTimeMillis(),
            coalescingKey = "$packageName:${event.windowId}",
            payload = JSONObject()
                .put(DikcizAutomationEventPayload.PACKAGE_NAME, packageName)
                .put(KEY_CLASS_NAME, className)
                .put(KEY_EVENT_TYPE, event.eventType)
                .put(KEY_WINDOW_ID, event.windowId),
        )
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            contentEvents.remove(window)?.pending?.let(mainHandler::removeCallbacks)
            DikcizAutomationEventBus.enqueue(context, document)
            return
        }
        val now = SystemClock.uptimeMillis()
        val previous = contentEvents[window]
        if (previous == null) {
            if (contentEvents.size >= MAXIMUM_CONTENT_WINDOWS) {
                contentEvents.remove(contentEvents.keys.first())?.pending?.let(mainHandler::removeCallbacks)
            }
            contentEvents[window] = ContentEventWindow(now)
            DikcizAutomationEventBus.enqueue(context, document)
            return
        }
        previous.latest = document
        if (previous.pending != null) return
        val publish = Runnable {
            previous.pending = null
            previous.lastPublished = SystemClock.uptimeMillis()
            previous.latest?.let { DikcizAutomationEventBus.enqueue(context, it) }
            previous.latest = null
        }
        val delay = CONTENT_EVENT_INTERVAL_MILLISECONDS - (now - previous.lastPublished)
        if (delay <= 0L) {
            publish.run()
        } else {
            previous.pending = publish
            mainHandler.postDelayed(publish, delay)
        }
    }

    private fun clearContentEvents() {
        contentEvents.values.forEach { it.pending?.let(mainHandler::removeCallbacks) }
        contentEvents.clear()
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
            .put(KEY_TEXT, if (isPassword) JSONObject.NULL else boundedText(text))
            .put(KEY_VIEW_ID_RESOURCE_NAME, boundedText(viewIdResourceName))
            .put(KEY_VISIBLE_TO_USER, isVisibleToUser)
    }

    private fun AccessibilityNodeInfo.supportedActions(): JSONArray {
        val actionIDs = actionList.map { action -> action.id }.toSet()
        return JSONArray().apply {
            DikcizAccessibilityNodeAction.entries.forEach { action ->
                if (action.androidActionID in actionIDs && (!action.usesClipboard || allowsClipboardAction(action))) {
                    put(action.persistedValue)
                }
            }
        }
    }

    private fun AccessibilityNodeInfo.allowsClipboardAction(action: DikcizAccessibilityNodeAction): Boolean =
        isEnabled && isFocused && isVisibleToUser && !isPassword &&
            actionList.any { it.id == action.androidActionID }

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
    private const val KEY_HUMANIZED = "humanized"
    private const val KEY_DURATION_MILLISECONDS = "durationMilliseconds"
    // Each slice is one dispatch round trip; longer slices mean fewer handoffs while
    // still letting the speed change several times along a swipe.
    private const val CONTINUOUS_SLICE_MILLISECONDS = 48L
    private const val SLICE_EXTENSION_MILLISECONDS = 8L
    private const val LOG_TAG = "DikcizAccessibility"
    private const val MILLIMETRES_PER_INCH = 25.4f
    private const val FALLBACK_DPI = 420f
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
    private const val CONTENT_EVENT_INTERVAL_MILLISECONDS = 1_000L
    private const val MAXIMUM_CONTENT_WINDOWS = 32
    private val REPORTED_EVENT_TYPES = setOf(
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        AccessibilityEvent.TYPE_WINDOWS_CHANGED,
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
    )
}

private data class DikcizGestureStroke(
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int,
) {
    val moves: Boolean get() = startX != endX || startY != endY

    fun isWithin(width: Int, height: Int): Boolean =
        startX in 0 until width && startY in 0 until height &&
            endX in 0 until width && endY in 0 until height

    fun toPath(): Path = Path().apply {
        moveTo(startX.toFloat(), startY.toFloat())
        if (moves) lineTo(endX.toFloat(), endY.toFloat())
    }

    fun toArray(): IntArray = intArrayOf(startX, startY, endX, endY)

    fun writeTo(document: JSONObject, keys: List<String>) {
        keys.zip(listOf(startX, startY, endX, endY)).forEach { (key, value) ->
            document.put(key, value)
        }
    }
}

/**
 * A requested screen gesture. Humanized gestures (the default) are planned by
 * DikcizTouchModel; `humanize: false` dispatches the exact straight, fixed-time
 * gesture that was asked for.
 */
internal class DikcizAccessibilityGesture private constructor(
    val snapshotID: String,
    val kind: DikcizAccessibilityGestureKind,
    private val requestedDurationMilliseconds: Long?,
    val humanize: Boolean,
    private val targetWidth: Int?,
    private val targetHeight: Int?,
    private val first: DikcizGestureStroke,
    private val second: DikcizGestureStroke?,
) {
    val durationMilliseconds: Long get() = requestedDurationMilliseconds ?: kind.defaultDurationMilliseconds

    private val secondTapOffset: Long get() = durationMilliseconds + DOUBLE_TAP_GAP_MILLISECONDS

    /** The exact gesture's length; a humanized plan reports its own. */
    val totalDurationMilliseconds: Long get() = when (kind) {
        DikcizAccessibilityGestureKind.DoubleTap -> secondTapOffset + durationMilliseconds
        else -> durationMilliseconds
    }

    fun isWithin(width: Int, height: Int): Boolean {
        if (!first.isWithin(width, height) || second?.isWithin(width, height) == false) return false
        return when (kind) {
            DikcizAccessibilityGestureKind.Swipe -> first.moves
            DikcizAccessibilityGestureKind.DoubleTap -> durationMilliseconds <= DOUBLE_TAP_MAX_PRESS_MILLISECONDS
            DikcizAccessibilityGestureKind.TwoFingerSwipe -> second != null &&
                (first.moves || second.moves) &&
                (first.startX != second.startX || first.startY != second.startY) &&
                (first.endX != second.endX || first.endY != second.endY)
            else -> true
        }
    }

    fun toPlatformGesture(): GestureDescription {
        val builder = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(first.toPath(), 0L, durationMilliseconds))
        if (kind == DikcizAccessibilityGestureKind.DoubleTap) {
            builder.addStroke(GestureDescription.StrokeDescription(first.toPath(), secondTapOffset, durationMilliseconds))
        }
        second?.let {
            builder.addStroke(GestureDescription.StrokeDescription(it.toPath(), 0L, durationMilliseconds))
        }
        return builder.build()
    }

    fun plan(model: DikcizTouchModel): DikcizTouchPlan {
        val target = DikcizTouchTarget(first.startX, first.startY, targetWidth, targetHeight)
        return when (kind) {
            DikcizAccessibilityGestureKind.Tap -> model.tap(target, requestedDurationMilliseconds)
            DikcizAccessibilityGestureKind.Hold -> model.hold(target, requestedDurationMilliseconds)
            DikcizAccessibilityGestureKind.DoubleTap -> model.doubleTap(target, requestedDurationMilliseconds)
            DikcizAccessibilityGestureKind.Swipe ->
                model.swipe(first.startX, first.startY, first.endX, first.endY, requestedDurationMilliseconds)
            DikcizAccessibilityGestureKind.TwoFingerSwipe ->
                model.twoFingerSwipe(first.toArray(), requireNotNull(second).toArray(), requestedDurationMilliseconds)
        }
    }

    fun writeFields(document: JSONObject) {
        document.put("snapshotId", snapshotID).put("gesture", kind.persistedValue)
        requestedDurationMilliseconds?.let { document.put(DikcizAccessibilityGestureKind.DURATION_FIELD, it) }
        if (!humanize) document.put(DikcizAccessibilityGestureKind.HUMANIZE_FIELD, false)
        targetWidth?.let { document.put(DikcizAccessibilityGestureKind.TARGET_WIDTH_FIELD, it) }
        targetHeight?.let { document.put(DikcizAccessibilityGestureKind.TARGET_HEIGHT_FIELD, it) }
        first.writeTo(document, kind.coordinateKeys)
        second?.writeTo(document, DikcizAccessibilityGestureKind.secondStrokeKeys)
    }

    companion object {
        private const val DOUBLE_TAP_GAP_MILLISECONDS = 60L
        private const val DOUBLE_TAP_MAX_PRESS_MILLISECONDS = 100L

        fun fromFields(
            snapshotID: String,
            kind: DikcizAccessibilityGestureKind,
            durationMilliseconds: Long?,
            humanize: Boolean,
            targetWidth: Int?,
            targetHeight: Int?,
            coordinate: (String) -> Int,
        ): DikcizAccessibilityGesture {
            val firstCoordinates = kind.coordinateKeys.map(coordinate)
            val first = DikcizGestureStroke(
                firstCoordinates[0],
                firstCoordinates[1],
                firstCoordinates.getOrElse(2) { firstCoordinates[0] },
                firstCoordinates.getOrElse(3) { firstCoordinates[1] },
            )
            val second = if (kind == DikcizAccessibilityGestureKind.TwoFingerSwipe) {
                val (startX, startY, endX, endY) = DikcizAccessibilityGestureKind.secondStrokeKeys.map(coordinate)
                DikcizGestureStroke(startX, startY, endX, endY)
            } else {
                null
            }
            return DikcizAccessibilityGesture(
                snapshotID,
                kind,
                durationMilliseconds,
                humanize,
                targetWidth.takeIf { kind.acceptsTargetSize },
                targetHeight.takeIf { kind.acceptsTargetSize },
                first,
                second,
            )
        }
    }
}

private val gesturePointKeys = listOf("x", "y")
private val gestureSwipeKeys = listOf("startX", "startY", "endX", "endY")

internal enum class DikcizAccessibilityGestureKind(
    val persistedValue: String,
    val coordinateKeys: List<String>,
    val defaultDurationMilliseconds: Long,
) {
    Tap("tap", gesturePointKeys, 100L),
    Hold("hold", gesturePointKeys, 650L),
    DoubleTap("doubleTap", gesturePointKeys, 60L),
    Swipe("swipe", gestureSwipeKeys, 520L),
    TwoFingerSwipe("twoFingerSwipe", gestureSwipeKeys, 520L),
    ;

    /** Point gestures may name the touched element's size so landings stay inside it. */
    val acceptsTargetSize: Boolean get() = coordinateKeys == gesturePointKeys

    /** Coordinate fields every request of this kind must carry. */
    val fields: Set<String> get() = if (this == TwoFingerSwipe) {
        (coordinateKeys + secondStrokeKeys).toSet()
    } else {
        coordinateKeys.toSet()
    }

    /** Fields a request of this kind may add. */
    val optionalFields: Set<String> get() = if (acceptsTargetSize) {
        setOf(DURATION_FIELD, HUMANIZE_FIELD, TARGET_WIDTH_FIELD, TARGET_HEIGHT_FIELD)
    } else {
        setOf(DURATION_FIELD, HUMANIZE_FIELD)
    }

    companion object {
        const val DURATION_FIELD = "durationMilliseconds"
        const val HUMANIZE_FIELD = "humanize"
        const val TARGET_WIDTH_FIELD = "targetWidth"
        const val TARGET_HEIGHT_FIELD = "targetHeight"
        val secondStrokeKeys = listOf("secondStartX", "secondStartY", "secondEndX", "secondEndY")

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
    Copy("copy", AccessibilityNodeInfo.ACTION_COPY),
    Paste("paste", AccessibilityNodeInfo.ACTION_PASTE),
    ;

    val usesClipboard: Boolean
        get() = this == Copy || this == Paste

    companion object {
        fun fromPersistedValue(value: String): DikcizAccessibilityNodeAction? {
            return entries.firstOrNull { action -> action.persistedValue == value }
        }
    }
}
