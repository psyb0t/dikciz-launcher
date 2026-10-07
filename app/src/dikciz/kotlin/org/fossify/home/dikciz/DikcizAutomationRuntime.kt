package org.fossify.home.dikciz

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import org.json.JSONObject

internal object DikcizAutomationEventBus {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, AUTOMATION_EVENT_THREAD_NAME)
    }
    private val configurationChangeListeners = ConcurrentHashMap<String, () -> Unit>()
    private val eventListeners = ConcurrentHashMap<String, (DikcizAutomationEvent) -> Unit>()
    private val htmlDomPatchListeners = ConcurrentHashMap<String, (DikcizLuaAutomationAction.PatchDom) -> String>()
    private val remoteEventListeners = ConcurrentHashMap<String, (DikcizAutomationEvent) -> Unit>()
    private val runtimes = ConcurrentHashMap<String, DikcizAutomationRuntime>()
    private val pendingSensorEvents = mutableMapOf<PendingSensorEventKey, DikcizAutomationEvent>()
    private val queuedSensorEvents = mutableSetOf<PendingSensorEventKey>()
    private val sensorQueueLock = Any()

    fun enqueue(context: Context, event: DikcizAutomationEvent) {
        val applicationContext = context.applicationContext
        if (event.type != DikcizAutomationEventType.Sensor) {
            executor.execute { dispatch(applicationContext, event) }
            return
        }
        enqueueSensorEvent(applicationContext, event)
    }

    fun enqueueManualTrigger(context: Context, scriptID: String) {
        enqueue(
            context,
            DikcizAutomationEvent(
                type = DikcizAutomationEventType.Manual,
                source = SOURCE_MANUAL_TRIGGER,
                timestampMilliseconds = System.currentTimeMillis(),
                coalescingKey = scriptID,
                payload = JSONObject(),
                targetScriptID = scriptID,
            ),
        )
    }

    fun enqueueWidgetChanged(
        context: Context,
        widgetAddress: String,
        checked: Boolean,
    ) {
        enqueue(
            context,
            DikcizAutomationEvent(
                type = DikcizAutomationEventType.WidgetChanged,
                source = SOURCE_WIDGET_CHANGED,
                timestampMilliseconds = System.currentTimeMillis(),
                coalescingKey = widgetAddress,
                payload = JSONObject()
                    .put(PAYLOAD_WIDGET_ADDRESS, widgetAddress)
                    .put(FIELD_CHECKED, checked),
            ),
        )
    }

    fun enqueueCustomEvent(
        context: Context,
        eventName: String,
        source: String,
        payload: JSONObject,
        coalescingKey: String?,
        customEventDepth: Int,
    ): Boolean {
        if (
            !DikcizAutomationEventNames.isValidCustomName(eventName) ||
            customEventDepth > MAXIMUM_CUSTOM_EVENT_DEPTH
        ) {
            return false
        }
        enqueue(
            context,
            DikcizAutomationEvent(
                type = DikcizAutomationEventType.Custom,
                source = source,
                timestampMilliseconds = System.currentTimeMillis(),
                coalescingKey = coalescingKey ?: eventName,
                payload = JSONObject(payload.toString()),
                customEventName = eventName,
                customEventDepth = customEventDepth,
            ),
        )
        return true
    }

    fun dispatchDirectActions(
        context: Context,
        sourceWidgetAddress: String,
        actions: List<DikcizLuaAutomationAction>,
    ): List<DikcizAutomationDirectActionResult> {
        return runtime(context.applicationContext).dispatchDirectActions(sourceWidgetAddress, actions)
    }

    fun acceptConfiguration(context: Context, configuration: HomeConfiguration) {
        runtime(context.applicationContext).acceptConfiguration(configuration)
    }

    fun lastAcceptedConfiguration(context: Context): HomeConfiguration? {
        return runtime(context.applicationContext).lastAcceptedConfiguration()
    }

    fun setConfigurationChangeListener(
        context: Context,
        listener: (() -> Unit)?,
    ) {
        val packageName = context.applicationContext.packageName
        if (listener == null) {
            configurationChangeListeners.remove(packageName)
            return
        }
        configurationChangeListeners[packageName] = listener
    }

    fun setEventListener(
        context: Context,
        listener: ((DikcizAutomationEvent) -> Unit)?,
    ) {
        val packageName = context.applicationContext.packageName
        if (listener == null) {
            eventListeners.remove(packageName)
            return
        }
        eventListeners[packageName] = listener
    }

    fun setHtmlDomPatchListener(
        context: Context,
        listener: ((DikcizLuaAutomationAction.PatchDom) -> String)?,
    ) {
        val packageName = context.applicationContext.packageName
        if (listener == null) {
            htmlDomPatchListeners.remove(packageName)
            return
        }
        htmlDomPatchListeners[packageName] = listener
    }

    fun patchRenderedHtmlDom(
        context: Context,
        action: DikcizLuaAutomationAction.PatchDom,
    ): String {
        return htmlDomPatchListeners[context.applicationContext.packageName]?.invoke(action)
            ?: DikcizHtmlDomPatchOutcome.TargetNotRendered
    }

    fun setRemoteEventListener(
        context: Context,
        listener: ((DikcizAutomationEvent) -> Unit)?,
    ) {
        val packageName = context.applicationContext.packageName
        if (listener == null) {
            remoteEventListeners.remove(packageName)
            return
        }
        remoteEventListeners[packageName] = listener
    }

    fun notifyConfigurationChanged(context: Context) {
        configurationChangeListeners[context.applicationContext.packageName]?.invoke()
    }

    private fun runtime(context: Context): DikcizAutomationRuntime {
        return runtimes.getOrPut(context.packageName) { DikcizAutomationRuntime(context) }
    }

    private fun enqueueSensorEvent(
        context: Context,
        event: DikcizAutomationEvent,
    ) {
        val key = PendingSensorEventKey(context.packageName, event.coalescingKey)
        synchronized(sensorQueueLock) {
            pendingSensorEvents[key] = event
            if (!queuedSensorEvents.add(key)) {
                return
            }
            executor.execute { dispatchPendingSensorEvent(context, key) }
        }
    }

    private fun dispatchPendingSensorEvent(
        context: Context,
        key: PendingSensorEventKey,
    ) {
        val event = synchronized(sensorQueueLock) {
            pendingSensorEvents.remove(key).also { pendingEvent ->
                if (pendingEvent == null) {
                    queuedSensorEvents.remove(key)
                }
            }
        } ?: return
        dispatch(context, event)
        synchronized(sensorQueueLock) {
            queuedSensorEvents.remove(key)
            if (pendingSensorEvents.containsKey(key) && queuedSensorEvents.add(key)) {
                executor.execute { dispatchPendingSensorEvent(context, key) }
            }
        }
    }

    private fun dispatch(
        context: Context,
        event: DikcizAutomationEvent,
    ) {
        runtime(context).dispatch(event)
        eventListeners[context.packageName]?.invoke(event)
        remoteEventListeners[context.packageName]?.invoke(event)
    }

    // Sensor callbacks can arrive faster than a script's state write. Keep the
    // newest sample per source so that work cannot starve location or controls.
    private data class PendingSensorEventKey(
        val packageName: String,
        val coalescingKey: String,
    )

    private const val AUTOMATION_EVENT_THREAD_NAME = "dikciz-automation-events"
    private const val SOURCE_MANUAL_TRIGGER = "manual_trigger"
    private const val SOURCE_WIDGET_CHANGED = "home_widget"
    private const val MAXIMUM_CUSTOM_EVENT_DEPTH = 16
    private const val PAYLOAD_WIDGET_ADDRESS = "widgetAddress"
    private const val FIELD_CHECKED = "checked"
}

internal data class DikcizAutomationDirectActionResult(
    val type: String,
    val outcome: String,
)

internal fun DikcizAutomationEvent.toDocument(): JSONObject {
    return JSONObject()
        .put(FIELD_COALESCING_KEY, coalescingKey)
        .put(FIELD_PAYLOAD, JSONObject(payload.toString()))
        .put(FIELD_SOURCE, source)
        .put(FIELD_TIMESTAMP_MILLISECONDS, timestampMilliseconds)
        .put(FIELD_TYPE, name)
}

private const val FIELD_COALESCING_KEY = "coalescingKey"
private const val FIELD_PAYLOAD = "payload"
private const val FIELD_SOURCE = "source"
private const val FIELD_TIMESTAMP_MILLISECONDS = "timestampMilliseconds"
private const val FIELD_TYPE = "type"

internal class DikcizAutomationRuntime(
    private val context: Context,
) {
    private val homeConfigStore = HomeConfigStore(context)
    private val logger = DikcizLogger(LOG_TAG, homeConfigStore.logDirectory)
    private val luaRuntime = DikcizLuaRuntime()
    private val privilegedShell = DikcizPrivilegedShell()
    private val appActionRunner = DikcizAppActionRunner(
        context = context,
        packageManager = context.packageManager,
        logger = logger,
        privilegedShell = privilegedShell,
        launchComponent = ::startLaunchableComponent,
        // A background script has no selected page. The action document rejects
        // addShortcut before it can reach this runner.
        addShortcut = { false },
    )
    private val deliverySchedules = mutableMapOf<AutomationSubscriptionKey, AutomationDeliverySchedule>()
    @Volatile
    private var lastAcceptedConfiguration: HomeConfiguration? = null

    fun dispatch(event: DikcizAutomationEvent) {
        var configuration = loadConfiguration() ?: return
        configuration.automation.scripts.forEach { automationScript ->
            configuration = dispatchToScript(configuration, automationScript, event)
        }
    }

    fun acceptConfiguration(configuration: HomeConfiguration) {
        lastAcceptedConfiguration = configuration
        logger.configure(configuration.logging)
        reconcileDeliverySchedules(configuration)
    }

    fun lastAcceptedConfiguration(): HomeConfiguration? = lastAcceptedConfiguration

    fun dispatchDirectActions(
        sourceWidgetAddress: String,
        actions: List<DikcizLuaAutomationAction>,
    ): List<DikcizAutomationDirectActionResult> {
        val configuration = loadConfiguration() ?: run {
            return actions.map { action ->
                DikcizAutomationDirectActionResult(
                    type = actionCapability(action).persistedValue,
                    outcome = REASON_CONFIGURATION_UNAVAILABLE,
                )
            }
        }
        val directEvent = DikcizAutomationEvent(
            type = DikcizAutomationEventType.Manual,
            source = sourceWidgetAddress,
            timestampMilliseconds = System.currentTimeMillis(),
            coalescingKey = sourceWidgetAddress,
            payload = JSONObject(),
        )
        var activeConfiguration = configuration
        return actions.map { action ->
            val execution = executeAction(
                configuration = activeConfiguration,
                scriptID = sourceWidgetAddress,
                policyID = null,
                event = directEvent,
                action = action,
            )
            activeConfiguration = execution.configuration
            DikcizAutomationDirectActionResult(
                type = actionCapability(action).persistedValue,
                outcome = execution.outcome,
            )
        }
    }

    private fun loadConfiguration(): HomeConfiguration? {
        if (!homeConfigStore.hasSharedStorageAccess()) {
            logger.warn(EVENT_DELIVERY_REJECTED, mapOf(FIELD_REASON to REASON_STORAGE_UNAVAILABLE))
            return null
        }
        val configuration = try {
            homeConfigStore.loadOrCreate()
        } catch (exception: HomeConfigException) {
            val fallbackConfiguration = lastAcceptedConfiguration
            logger.warn(
                EVENT_CONFIGURATION_REJECTED,
                mapOf(
                    FIELD_ERROR_CLASS to exception::class.java.simpleName,
                    FIELD_REASON to if (fallbackConfiguration == null) {
                        REASON_CONFIGURATION_UNAVAILABLE
                    } else {
                        REASON_LAST_ACCEPTED_CONFIGURATION
                    },
                ),
            )
            return fallbackConfiguration
        }
        acceptConfiguration(configuration)
        return configuration
    }

    private fun dispatchToScript(
        configuration: HomeConfiguration,
        automationScript: DikcizAutomationScript,
        event: DikcizAutomationEvent,
    ): HomeConfiguration {
        if (event.targetScriptID != null && event.targetScriptID != automationScript.scriptID) {
            return configuration
        }
        if (!automationScript.enabled) {
            return configuration
        }
        val script = configuration.scripts.firstOrNull { it.id == automationScript.scriptID }
        val policy = configuration.automation.policy(automationScript.policyID)
        if (script == null || !script.enabled) {
            return configuration
        }
        val matchingSubscriptions = automationScript.subscriptions.filter { subscription ->
            subscription.matches(event) && isDeliveryDue(automationScript.scriptID, subscription, event)
        }
        if (matchingSubscriptions.isEmpty()) {
            logUnmatchedLocationDelivery(automationScript, event)
            return configuration
        }
        var actionConfiguration = configuration
        val accessibilitySnapshot = accessibilitySnapshot(event)
        matchingSubscriptions.forEach { _ ->
            val result = luaRuntime.executeEvent(
                script,
                event,
                actionConfiguration.limits,
                actionConfiguration.scriptWidgetContext(),
                accessibilitySnapshot,
                DikcizAutomationStatus.androidAccessDocument(context),
            )
            when (result) {
                DikcizLuaAutomationResult.Disabled -> recordScriptRunStatus(
                    script,
                    event,
                    DikcizLuaScriptRunOutcome.Rejected,
                    REASON_SCRIPT_DISABLED,
                    actionConfiguration.limits,
                )

                DikcizLuaAutomationResult.NoHandler -> recordScriptRunStatus(
                    script,
                    event,
                    DikcizLuaScriptRunOutcome.Completed,
                    STATUS_NO_EVENT_HANDLER,
                    actionConfiguration.limits,
                )

                is DikcizLuaAutomationResult.Failure -> {
                    logDeliveryRejected(
                        script.id,
                        policy?.id,
                        event.name,
                        result.reason.persistedValue,
                        result.diagnostic,
                    )
                    DikcizScriptFailureNotifier.report(
                        context,
                        logger,
                        configuration.logging,
                        script.id,
                        DikcizScriptKind.Automation,
                        result.reason,
                    )
                    recordScriptRunStatus(
                        script,
                        event,
                        DikcizLuaScriptRunOutcome.Failed,
                        result.reason.persistedValue,
                        actionConfiguration.limits,
                    )
                }

                is DikcizLuaAutomationResult.Actions -> {
                    val actionOutcomes = mutableListOf<String>()
                    result.actions.forEach { action ->
                        val execution = executeScriptAction(
                            actionConfiguration,
                            script.id,
                            policy,
                            event,
                            action,
                        )
                        actionConfiguration = execution.configuration
                        actionOutcomes += execution.outcome
                    }
                    recordScriptRunStatus(
                        script,
                        event,
                        actionOutcomes.toScriptRunOutcome(),
                        if (actionOutcomes.toScriptRunOutcome() == DikcizLuaScriptRunOutcome.Completed) {
                            result.status ?: actionOutcomes.toDefaultScriptRunStatus()
                        } else {
                            actionOutcomes.toDefaultScriptRunStatus()
                        },
                        actionConfiguration.limits,
                    )
                }
            }
        }
        return actionConfiguration
    }

    private fun logUnmatchedLocationDelivery(
        automationScript: DikcizAutomationScript,
        event: DikcizAutomationEvent,
    ) {
        if (event.type != DikcizAutomationEventType.Location) {
            return
        }
        val locationSubscriptions = automationScript.subscriptions.filter { subscription ->
            subscription.event == DikcizAutomationEventType.Location
        }
        if (locationSubscriptions.isEmpty()) {
            return
        }
        val hasMatchingSubscription = locationSubscriptions.any { subscription ->
            subscription.matches(event)
        }
        logger.debug(
            EVENT_LOCATION_DELIVERY_SKIPPED,
            mapOf(
                FIELD_EVENT_LOCATION_PRECISION to event.locationPrecision?.persistedValue.orEmpty(),
                FIELD_REASON to if (hasMatchingSubscription) {
                    REASON_MINIMUM_INTERVAL
                } else {
                    REASON_SUBSCRIPTION_MISMATCH
                },
                FIELD_SCRIPT_ID to automationScript.scriptID,
                FIELD_SUBSCRIPTION_LOCATION_PRECISIONS to locationSubscriptions
                    .mapNotNull(DikcizAutomationSubscription::locationPrecision)
                    .joinToString(),
            ),
        )
    }

    private fun reconcileDeliverySchedules(configuration: HomeConfiguration) {
        val now = SystemClock.elapsedRealtime()
        val activeKeys = configuration.automation.scripts.asSequence()
            .filter(DikcizAutomationScript::enabled)
            .filter { automationScript ->
                configuration.scripts.any { script ->
                    script.id == automationScript.scriptID && script.enabled
                }
            }
            .filter { automationScript ->
                configuration.automation.policy(automationScript.policyID)?.enabled == true
            }
            .flatMap { automationScript ->
                automationScript.subscriptions.asSequence().map { subscription ->
                    AutomationSubscriptionKey(automationScript.scriptID, subscription)
                }
            }
            .toSet()
        synchronized(deliverySchedules) {
            deliverySchedules.keys.retainAll(activeKeys)
            activeKeys.forEach { key ->
                deliverySchedules.getOrPut(key) {
                    AutomationDeliverySchedule.from(key.subscription, now)
                }
            }
        }
    }

    private fun isDeliveryDue(
        scriptID: String,
        subscription: DikcizAutomationSubscription,
        event: DikcizAutomationEvent,
    ): Boolean {
        val now = eventDeliveryElapsedRealtime(event)
        val key = AutomationSubscriptionKey(scriptID, subscription)
        synchronized(deliverySchedules) {
            val schedule = deliverySchedules.getOrPut(key) {
                AutomationDeliverySchedule.from(subscription, now)
            }
            if (event.type == DikcizAutomationEventType.Alarm) {
                if (event.payload.optBoolean(PAYLOAD_ALARM_RECOVERY)) {
                    schedule.nextAlarmElapsedRealtime = now
                }
                val nextAlarmElapsedRealtime = schedule.nextAlarmElapsedRealtime ?: return false
                if (now < nextAlarmElapsedRealtime) {
                    return false
                }
            }
            val lastDeliveryElapsedRealtime = schedule.lastDeliveryElapsedRealtime
            if (
                lastDeliveryElapsedRealtime != null &&
                now - lastDeliveryElapsedRealtime < subscription.minimumIntervalMilliseconds
            ) {
                return false
            }
            schedule.lastDeliveryElapsedRealtime = now
            if (event.type == DikcizAutomationEventType.Alarm) {
                schedule.nextAlarmElapsedRealtime = now + requireNotNull(subscription.alarmIntervalMilliseconds)
            }
            return true
        }
    }

    private fun eventDeliveryElapsedRealtime(event: DikcizAutomationEvent): Long {
        if (event.type != DikcizAutomationEventType.Alarm) {
            return SystemClock.elapsedRealtime()
        }
        val elapsedRealtime = event.payload.optLong(PAYLOAD_ALARM_ELAPSED_REALTIME)
        return elapsedRealtime.takeIf { value -> value > NO_ELAPSED_REALTIME } ?: SystemClock.elapsedRealtime()
    }

    /**
     * Runs one action a script asked for, after its policy allows the
     * capability.
     *
     * A script returns whatever its Lua decides, so the policy is the only
     * place the owner limits it, and a missing policy grants nothing. Direct
     * widget actions do not pass through here, because the owner invokes those
     * on the device instead of a background subscription.
     */
    private fun executeScriptAction(
        configuration: HomeConfiguration,
        scriptID: String,
        policy: DikcizAutomationPolicy?,
        event: DikcizAutomationEvent,
        action: DikcizLuaAutomationAction,
    ): AutomationActionExecution {
        val capability = actionCapability(action)
        if (policy == null || capability !in policy.actions) {
            logAction(scriptID, policy?.id, event.name, capability, REASON_CAPABILITY_DENIED)
            return AutomationActionExecution(configuration, REASON_CAPABILITY_DENIED)
        }
        return executeAction(configuration, scriptID, policy.id, event, action)
    }

    private fun executeAction(
        configuration: HomeConfiguration,
        scriptID: String,
        policyID: String?,
        event: DikcizAutomationEvent,
        action: DikcizLuaAutomationAction,
    ): AutomationActionExecution {
        val capability = actionCapability(action)
        val execution = when (action) {
            is DikcizLuaAutomationAction.SelectPage -> selectPage(configuration, action.pageID)
            is DikcizLuaAutomationAction.PatchWidget -> patchWidget(
                configuration,
                action.widgetAddress,
                action.values,
            )
            is DikcizLuaAutomationAction.PatchDom -> patchDom(configuration, action)
            is DikcizLuaAutomationAction.PatchState -> patchState(configuration, scriptID, action.values)
            is DikcizLuaAutomationAction.LaunchApp -> launchApp(configuration, action.component)
            is DikcizLuaAutomationAction.AppAction -> appAction(configuration, action)
            is DikcizLuaAutomationAction.PostNotification -> postNotification(configuration, action.title, action.text)
            is DikcizLuaAutomationAction.SendSms -> sendSms(configuration, action)
            is DikcizLuaAutomationAction.ExplicitIntent -> dispatchExplicitIntent(configuration, action)
            is DikcizLuaAutomationAction.MediaControl -> mediaControl(configuration, action)
            is DikcizLuaAutomationAction.SetMediaVolume -> setMediaVolume(configuration, action)
            is DikcizLuaAutomationAction.NotificationControl -> notificationControl(configuration, action)
            is DikcizLuaAutomationAction.EmitEvent -> emitEvent(configuration, event, scriptID, action)
            is DikcizLuaAutomationAction.AccessibilityAction -> accessibilityAction(configuration, action)
            is DikcizLuaAutomationAction.AccessibilityGesture -> accessibilityGesture(configuration, action)
            is DikcizLuaAutomationAction.AccessibilityGlobalAction -> accessibilityGlobalAction(configuration, action)
            DikcizLuaAutomationAction.LockDevice -> lockDevice(configuration)
        }
        logAction(scriptID, policyID, event.name, capability, execution.outcome)
        if (policyID != null && execution.outcome != OUTCOME_EXECUTED) {
            DikcizScriptFailureNotifier.reportActionFailure(
                context = context,
                logger = logger,
                logging = configuration.logging,
                scriptID = scriptID,
                scriptKind = DikcizScriptKind.Automation,
                failureReason = execution.outcome,
            )
        }
        return execution
    }

    private fun actionCapability(action: DikcizLuaAutomationAction): DikcizAutomationActionCapability {
        return when (action) {
            is DikcizLuaAutomationAction.SelectPage -> DikcizAutomationActionCapability.SelectPage
            is DikcizLuaAutomationAction.PatchWidget -> DikcizAutomationActionCapability.PatchWidget
            is DikcizLuaAutomationAction.PatchDom -> DikcizAutomationActionCapability.PatchDom
            is DikcizLuaAutomationAction.PatchState -> DikcizAutomationActionCapability.PatchState
            is DikcizLuaAutomationAction.LaunchApp -> DikcizAutomationActionCapability.LaunchApp
            is DikcizLuaAutomationAction.AppAction -> DikcizAutomationActionCapability.AppAction
            is DikcizLuaAutomationAction.PostNotification -> DikcizAutomationActionCapability.PostNotification
            is DikcizLuaAutomationAction.SendSms -> DikcizAutomationActionCapability.SendSms
            is DikcizLuaAutomationAction.ExplicitIntent -> DikcizAutomationActionCapability.ExplicitIntent
            is DikcizLuaAutomationAction.MediaControl -> DikcizAutomationActionCapability.MediaControl
            is DikcizLuaAutomationAction.SetMediaVolume -> DikcizAutomationActionCapability.MediaVolume
            is DikcizLuaAutomationAction.NotificationControl -> DikcizAutomationActionCapability.NotificationControl
            is DikcizLuaAutomationAction.EmitEvent -> DikcizAutomationActionCapability.EmitEvent
            is DikcizLuaAutomationAction.AccessibilityAction -> {
                DikcizAutomationActionCapability.AccessibilityAction
            }
            is DikcizLuaAutomationAction.AccessibilityGesture -> {
                DikcizAutomationActionCapability.AccessibilityGesture
            }
            is DikcizLuaAutomationAction.AccessibilityGlobalAction -> {
                DikcizAutomationActionCapability.AccessibilityGlobalAction
            }
            DikcizLuaAutomationAction.LockDevice -> DikcizAutomationActionCapability.LockDevice
        }
    }

    private fun accessibilitySnapshot(event: DikcizAutomationEvent): JSONObject? {
        if (event.type != DikcizAutomationEventType.AccessibilityWindow) {
            return null
        }
        return try {
            DikcizAccessibilityAutomation.snapshot()
        } catch (exception: DikcizAccessibilityAutomationException) {
            logger.warn(EVENT_ACCESSIBILITY_SNAPSHOT_UNAVAILABLE, mapOf(FIELD_REASON to exception.code))
            null
        }
    }

    private fun accessibilityAction(
        configuration: HomeConfiguration,
        action: DikcizLuaAutomationAction.AccessibilityAction,
    ): AutomationActionExecution {
        return try {
            val result = DikcizAccessibilityAutomation.performAction(
                snapshotID = action.snapshotID,
                nodeID = action.nodeID,
                actionName = action.action,
                text = action.text,
            )
            AutomationActionExecution(configuration, result.getString(FIELD_OUTCOME))
        } catch (exception: DikcizAccessibilityAutomationException) {
            AutomationActionExecution(configuration, exception.code)
        }
    }

    private fun accessibilityGesture(
        configuration: HomeConfiguration,
        action: DikcizLuaAutomationAction.AccessibilityGesture,
    ): AutomationActionExecution {
        return try {
            val result = DikcizAccessibilityAutomation.performGesture(action.gesture)
            AutomationActionExecution(configuration, result.getString(FIELD_OUTCOME))
        } catch (exception: DikcizAccessibilityAutomationException) {
            AutomationActionExecution(configuration, exception.code)
        }
    }

    private fun accessibilityGlobalAction(
        configuration: HomeConfiguration,
        action: DikcizLuaAutomationAction.AccessibilityGlobalAction,
    ): AutomationActionExecution {
        return try {
            val result = DikcizAccessibilityAutomation.performGlobalAction(action.action.persistedValue)
            AutomationActionExecution(configuration, result.getString(FIELD_OUTCOME))
        } catch (exception: DikcizAccessibilityAutomationException) {
            AutomationActionExecution(configuration, exception.code)
        }
    }

    private fun emitEvent(
        configuration: HomeConfiguration,
        event: DikcizAutomationEvent,
        scriptID: String,
        action: DikcizLuaAutomationAction.EmitEvent,
    ): AutomationActionExecution {
        val accepted = DikcizAutomationEventBus.enqueueCustomEvent(
            context = context,
            eventName = action.eventName,
            source = scriptID,
            payload = action.payload,
            coalescingKey = action.coalescingKey,
            customEventDepth = event.customEventDepth + 1,
        )
        return AutomationActionExecution(
            configuration,
            if (accepted) OUTCOME_EXECUTED else REASON_EVENT_DEPTH_EXCEEDED,
        )
    }

    private fun selectPage(
        configuration: HomeConfiguration,
        pageID: String,
    ): AutomationActionExecution {
        if (configuration.pages.none { it.id == pageID }) {
            return AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
        }
        return save(configuration, configuration.copy(selectedPageID = pageID))
    }

    private fun patchWidget(
        configuration: HomeConfiguration,
        widgetAddress: String,
        values: JSONObject,
    ): AutomationActionExecution {
        val target = configuration.scriptWidgetTarget(widgetAddress)
            ?: return AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
        var patched = false
        val pages = configuration.pages.map { page ->
            if (page.id != target.pageID) {
                return@map page
            }
            page.copy(widgets = page.widgets.map { widget ->
                if (widget.id != target.widgetID) {
                    return@map widget
                }
                val updatedWidget = patchableWidget(widget, values)
                    ?: return@map widget
                patched = true
                updatedWidget
            })
        }
        if (!patched) {
            return AutomationActionExecution(configuration, REASON_TARGET_DENIED)
        }
        return save(configuration, configuration.copy(pages = pages))
    }

    private fun patchDom(
        configuration: HomeConfiguration,
        action: DikcizLuaAutomationAction.PatchDom,
    ): AutomationActionExecution {
        val target = configuration.scriptWidgetTarget(action.widgetAddress)
            ?: return AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
        val widget = configuration.pages
            .first { page -> page.id == target.pageID }
            .widgets
            .first { widget -> widget.id == target.widgetID }
        if (widget !is HtmlHomeWidget) {
            return AutomationActionExecution(configuration, DikcizHtmlDomPatchOutcome.TargetNotHtml)
        }
        return AutomationActionExecution(
            configuration,
            DikcizAutomationEventBus.patchRenderedHtmlDom(context, action),
        )
    }

    private fun patchableWidget(widget: HomeWidget, values: JSONObject): HomeWidget? {
        return when (widget) {
            is HtmlHomeWidget -> {
                if (!values.hasOnly(HTML_WIDGET_PATCH_FIELDS)) {
                    return null
                }
                val state = values.optJSONObject(FIELD_STATE)
                widget.copy(
                    title = values.optStringOrCurrent(FIELD_TITLE, widget.title),
                    html = values.optStringOrCurrent(FIELD_HTML, widget.html),
                    css = values.optStringOrCurrent(FIELD_CSS, widget.css),
                    javascript = values.optStringOrCurrent(FIELD_JAVASCRIPT, widget.javascript),
                    state = state?.let { JSONObject(it.toString()) } ?: widget.state,
                )
            }

            is AppHomeWidget,
            is AppGroupHomeWidget,
            is ProviderHomeWidget,
            is ScriptDashboardHomeWidget,
            -> null
        }
    }

    private fun JSONObject.hasOnly(allowedFields: Set<String>): Boolean {
        val keys = keys()
        while (keys.hasNext()) {
            if (keys.next() !in allowedFields) {
                return false
            }
        }
        return true
    }

    private fun JSONObject.optBooleanOrCurrent(key: String, current: Boolean): Boolean {
        return if (has(key)) getBoolean(key) else current
    }

    private fun JSONObject.optStringOrCurrent(key: String, current: String): String {
        return if (has(key)) getString(key) else current
    }

    private fun patchState(
        configuration: HomeConfiguration,
        scriptID: String,
        patch: JSONObject,
    ): AutomationActionExecution {
        var changed = false
        val scripts = configuration.scripts.map { script ->
            if (script.id != scriptID) {
                return@map script
            }
            val state = JSONObject(script.state.toString())
            val keys = patch.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                state.put(key, patch.get(key))
            }
            changed = true
            script.copy(state = state)
        }
        if (!changed) {
            return AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
        }
        return save(configuration, configuration.copy(scripts = scripts))
    }

    private fun save(
        previousConfiguration: HomeConfiguration,
        updatedConfiguration: HomeConfiguration,
    ): AutomationActionExecution {
        return try {
            homeConfigStore.save(updatedConfiguration)
            acceptConfiguration(updatedConfiguration)
            DikcizAutomationEventBus.notifyConfigurationChanged(context)
            AutomationActionExecution(updatedConfiguration, OUTCOME_EXECUTED)
        } catch (_: HomeConfigException) {
            AutomationActionExecution(previousConfiguration, REASON_CONFIGURATION_REJECTED)
        }
    }

    private fun appAction(
        configuration: HomeConfiguration,
        action: DikcizLuaAutomationAction.AppAction,
    ): AutomationActionExecution {
        val component = DikcizAppIdentifiers.parseComponent(
            action.component,
            configuration.limits.maxComponentCharacters,
        ) ?: return AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
        val application = DikcizAppCatalogue.find(
            packageManager = context.packageManager,
            launcherPackageName = context.packageName,
            component = component,
        ) ?: return AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
        val result = appActionRunner.run(action.action, application)
        if (result.isSuccessful) {
            return AutomationActionExecution(configuration, OUTCOME_EXECUTED)
        }
        return AutomationActionExecution(configuration, result.outcome)
    }

    private fun startLaunchableComponent(component: ComponentName): Boolean {
        if (context.packageManager.resolveActivity(
                Intent().setComponent(component),
                RESOLVE_ACTIVITY_FLAGS,
            ) == null
        ) {
            return false
        }
        return try {
            context.startActivity(Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun launchApp(
        configuration: HomeConfiguration,
        componentValue: String,
    ): AutomationActionExecution {
        val component = ComponentName.unflattenFromString(componentValue)
            ?: return AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
        if (context.packageManager.resolveActivity(Intent().setComponent(component), RESOLVE_ACTIVITY_FLAGS) == null) {
            return AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
        }
        return try {
            context.startActivity(Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            AutomationActionExecution(configuration, OUTCOME_EXECUTED)
        } catch (_: ActivityNotFoundException) {
            AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
        } catch (_: SecurityException) {
            AutomationActionExecution(configuration, REASON_ANDROID_REJECTED)
        }
    }

    private fun postNotification(
        configuration: HomeConfiguration,
        title: String,
        text: String,
    ): AutomationActionExecution {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return AutomationActionExecution(configuration, REASON_ANDROID_PERMISSION_DENIED)
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java)
            ?: return AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                AUTOMATION_NOTIFICATION_CHANNEL_ID,
                AUTOMATION_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        notificationManager.notify(
            title.hashCode(),
            NotificationCompat.Builder(context, AUTOMATION_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build(),
        )
        return AutomationActionExecution(configuration, OUTCOME_EXECUTED)
    }

    private fun sendSms(
        configuration: HomeConfiguration,
        action: DikcizLuaAutomationAction.SendSms,
    ): AutomationActionExecution {
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return AutomationActionExecution(configuration, REASON_ANDROID_PERMISSION_DENIED)
        }
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
        }
        val smsManager = context.getSystemService(SmsManager::class.java)
            ?: return AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
        return try {
            // Android splits anything past one segment, so a long reply from a
            // script still arrives as one message for the recipient.
            val parts = smsManager.divideMessage(action.body)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(action.recipient, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(action.recipient, null, action.body, null, null)
            }
            AutomationActionExecution(configuration, OUTCOME_EXECUTED)
        } catch (exception: IllegalArgumentException) {
            // Neither the recipient nor the body is logged. Both can carry
            // third-party content that reached the script from an inbound
            // message or an external service.
            logger.warn(
                EVENT_AUTOMATION_SMS_SEND_REJECTED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            AutomationActionExecution(configuration, REASON_ANDROID_REJECTED)
        } catch (exception: SecurityException) {
            logger.warn(
                EVENT_AUTOMATION_SMS_SEND_REJECTED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            AutomationActionExecution(configuration, REASON_ANDROID_PERMISSION_DENIED)
        }
    }

    private fun lockDevice(configuration: HomeConfiguration): AutomationActionExecution {
        return when (DikcizDeviceAdministration.lock(context)) {
            DikcizDeviceLockResult.Executed -> AutomationActionExecution(configuration, OUTCOME_EXECUTED)
            DikcizDeviceLockResult.AdminInactive -> {
                AutomationActionExecution(configuration, REASON_DEVICE_ADMIN_INACTIVE)
            }

            DikcizDeviceLockResult.TargetUnavailable -> {
                AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
            }

            DikcizDeviceLockResult.AndroidRejected -> {
                AutomationActionExecution(configuration, REASON_ANDROID_REJECTED)
            }
        }
    }

    private fun dispatchExplicitIntent(
        configuration: HomeConfiguration,
        action: DikcizLuaAutomationAction.ExplicitIntent,
    ): AutomationActionExecution {
        val component = ComponentName.unflattenFromString(action.component)
            ?: return AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
        val intent = Intent(action.action).setComponent(component)
        return try {
            when (action.intentType) {
                DikcizAutomationIntentType.Activity -> context.startActivity(
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )

                DikcizAutomationIntentType.Broadcast -> context.sendBroadcast(intent)
            }
            AutomationActionExecution(configuration, OUTCOME_EXECUTED)
        } catch (_: ActivityNotFoundException) {
            AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
        } catch (_: SecurityException) {
            AutomationActionExecution(configuration, REASON_ANDROID_REJECTED)
        }
    }

    private fun mediaControl(
        configuration: HomeConfiguration,
        action: DikcizLuaAutomationAction.MediaControl,
    ): AutomationActionExecution {
        if (!DikcizMediaSessionMonitor.hasAccess(context)) {
            return AutomationActionExecution(configuration, REASON_ANDROID_PERMISSION_DENIED)
        }
        val sessionManager = context.getSystemService(MediaSessionManager::class.java)
            ?: return AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
        val listenerComponent = ComponentName(context, DikcizNotificationListenerService::class.java)
        val controller = try {
            sessionManager.getActiveSessions(listenerComponent).firstOrNull { activeSession ->
                activeSession.packageName == action.packageName
            }
        } catch (_: SecurityException) {
            return AutomationActionExecution(configuration, REASON_ANDROID_REJECTED)
        } ?: return AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
        return try {
            dispatchMediaControl(controller, action)
            AutomationActionExecution(configuration, OUTCOME_EXECUTED)
        } catch (_: SecurityException) {
            AutomationActionExecution(configuration, REASON_ANDROID_REJECTED)
        }
    }

    private fun setMediaVolume(
        configuration: HomeConfiguration,
        action: DikcizLuaAutomationAction.SetMediaVolume,
    ): AutomationActionExecution {
        val audioManager = context.getSystemService(AudioManager::class.java)
            ?: return AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
        if (audioManager.isVolumeFixed) {
            return AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
        }
        val minimumIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            MINIMUM_MEDIA_VOLUME_INDEX
        }
        val maximumIndex = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maximumIndex < minimumIndex) {
            return AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
        }
        val requestedIndex = minimumIndex + (
            (maximumIndex - minimumIndex) * action.levelPercent + MEDIA_VOLUME_ROUNDING_OFFSET
            ) / MAXIMUM_MEDIA_VOLUME_PERCENT
        return try {
            setMediaStreamVolumeWithoutUi(audioManager, requestedIndex)
            if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != requestedIndex) {
                AutomationActionExecution(configuration, REASON_ANDROID_REJECTED)
            } else {
                AutomationActionExecution(configuration, OUTCOME_EXECUTED)
            }
        } catch (_: SecurityException) {
            AutomationActionExecution(configuration, REASON_ANDROID_REJECTED)
        }
    }

    @SuppressLint("WrongConstant")
    private fun setMediaStreamVolumeWithoutUi(audioManager: AudioManager, requestedIndex: Int) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, requestedIndex, NO_VOLUME_UI_FLAGS)
    }

    private fun notificationControl(
        configuration: HomeConfiguration,
        action: DikcizLuaAutomationAction.NotificationControl,
    ): AutomationActionExecution {
        if (!DikcizAutomationStatus.hasNotificationListenerAccess(context)) {
            return AutomationActionExecution(configuration, REASON_ANDROID_PERMISSION_DENIED)
        }
        return when (
            DikcizNotificationActionRegistry.consume(action.actionToken)
        ) {
            DikcizNotificationActionResult.Executed -> {
                AutomationActionExecution(configuration, OUTCOME_EXECUTED)
            }

            DikcizNotificationActionResult.TargetUnavailable -> {
                AutomationActionExecution(configuration, REASON_TARGET_UNAVAILABLE)
            }

            DikcizNotificationActionResult.AndroidRejected -> {
                AutomationActionExecution(configuration, REASON_ANDROID_REJECTED)
            }
        }
    }

    private fun dispatchMediaControl(
        controller: MediaController,
        action: DikcizLuaAutomationAction.MediaControl,
    ) {
        val controls = controller.transportControls
        when (action.command) {
            DikcizMediaControlCommand.Play -> controls.play()
            DikcizMediaControlCommand.Pause -> controls.pause()
            DikcizMediaControlCommand.PlayPause -> {
                if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    controls.pause()
                } else {
                    controls.play()
                }
            }

            DikcizMediaControlCommand.SkipNext -> controls.skipToNext()
            DikcizMediaControlCommand.SkipPrevious -> controls.skipToPrevious()
            DikcizMediaControlCommand.SeekTo -> controls.seekTo(requireNotNull(action.positionMilliseconds))
        }
    }

    private data class AutomationActionExecution(
        val configuration: HomeConfiguration,
        val outcome: String,
    )

    private data class AutomationSubscriptionKey(
        val scriptID: String,
        val subscription: DikcizAutomationSubscription,
    )

    private data class AutomationDeliverySchedule(
        var lastDeliveryElapsedRealtime: Long? = null,
        var nextAlarmElapsedRealtime: Long? = null,
    ) {
        companion object {
            fun from(
                subscription: DikcizAutomationSubscription,
                now: Long,
            ): AutomationDeliverySchedule {
                return AutomationDeliverySchedule(
                    nextAlarmElapsedRealtime = subscription.alarmIntervalMilliseconds?.let { interval ->
                        now + interval
                    },
                )
            }
        }
    }

    private fun logDeliveryRejected(
        scriptID: String,
        policyID: String?,
        eventType: String,
        reason: String,
        diagnostic: String?,
    ) {
        val fields = mutableMapOf<String, Any>(
            FIELD_EVENT_TYPE to eventType,
            FIELD_POLICY_ID to (policyID ?: POLICY_ID_UNRESTRICTED),
            FIELD_REASON to reason,
            FIELD_SCRIPT_ID to scriptID,
        )
        diagnostic?.let { value -> fields[FIELD_DIAGNOSTIC] = value }
        logger.warn(
            EVENT_DELIVERY_REJECTED,
            fields,
        )
    }

    private fun recordScriptRunStatus(
        script: DikcizLuaScript,
        event: DikcizAutomationEvent,
        outcome: DikcizLuaScriptRunOutcome,
        status: String,
        limits: DikcizLimits,
    ) {
        try {
            homeConfigStore.writeLuaScriptRunStatus(
                script.id,
                DikcizLuaScriptRunStatus(
                    lastRunAt = Instant.ofEpochMilli(event.timestampMilliseconds).toString(),
                    outcome = outcome,
                    status = status,
                ),
                limits,
            )
            DikcizAutomationEventBus.notifyConfigurationChanged(context)
        } catch (exception: HomeConfigException) {
            logger.warn(
                EVENT_SCRIPT_RUN_STATUS_REJECTED,
                mapOf(
                    FIELD_ERROR_CLASS to exception::class.java.simpleName,
                    FIELD_SCRIPT_ID to script.id,
                ),
            )
        }
    }

    private fun List<String>.toDefaultScriptRunStatus(): String {
        val rejectedOutcome = firstOrNull { outcome -> outcome != OUTCOME_EXECUTED }
        if (rejectedOutcome != null) {
            return "$STATUS_ACTION_REJECTED_PREFIX$rejectedOutcome"
        }
        return STATUS_COMPLETED
    }

    private fun List<String>.toScriptRunOutcome(): DikcizLuaScriptRunOutcome {
        return if (all { outcome -> outcome == OUTCOME_EXECUTED }) {
            DikcizLuaScriptRunOutcome.Completed
        } else {
            DikcizLuaScriptRunOutcome.Rejected
        }
    }

    private fun logAction(
        scriptID: String,
        policyID: String?,
        eventType: String,
        action: DikcizAutomationActionCapability,
        outcome: String,
    ) {
        val fields = mapOf(
            FIELD_ACTION_TYPE to action.persistedValue,
            FIELD_EVENT_TYPE to eventType,
            FIELD_OUTCOME to outcome,
            FIELD_POLICY_ID to (policyID ?: POLICY_ID_UNRESTRICTED),
            FIELD_SCRIPT_ID to scriptID,
        )
        if (outcome == OUTCOME_EXECUTED) {
            logger.info(EVENT_ACTION_COMPLETED, fields)
            return
        }
        // A rejection carries the machine-readable reason alongside the
        // outcome, so a reader can filter refusals without parsing outcomes.
        logger.warn(EVENT_ACTION_REJECTED, fields + mapOf(FIELD_REASON to outcome))
    }

    private companion object {
        const val AUTOMATION_NOTIFICATION_CHANNEL_ID = "dikciz-automation"
        const val AUTOMATION_NOTIFICATION_CHANNEL_NAME = "Dikciz automation"
        const val EVENT_ACTION_COMPLETED = "automation_action_completed"
        const val EVENT_ACTION_REJECTED = "automation_action_rejected"
        const val EVENT_ACCESSIBILITY_SNAPSHOT_UNAVAILABLE = "accessibility_snapshot_unavailable"
        const val EVENT_CONFIGURATION_REJECTED = "automation_configuration_rejected"
        const val EVENT_DELIVERY_REJECTED = "automation_delivery_rejected"
        const val EVENT_LOCATION_DELIVERY_SKIPPED = "automation_location_delivery_skipped"
        const val EVENT_SCRIPT_RUN_STATUS_REJECTED = "automation_script_run_status_rejected"
        const val EVENT_AUTOMATION_SMS_SEND_REJECTED = "automation_sms_send_rejected"
        const val FIELD_ACTION_TYPE = "action_type"
        const val FIELD_CSS = "css"
        const val FIELD_DIAGNOSTIC = "diagnostic"
        const val FIELD_ERROR_CLASS = "error_class"
        const val FIELD_EVENT_TYPE = "event_type"
        const val FIELD_EVENT_LOCATION_PRECISION = "event_location_precision"
        const val FIELD_OUTCOME = "outcome"
        const val FIELD_POLICY_ID = "policy_id"
        const val FIELD_REASON = "reason"
        const val FIELD_SCRIPT_ID = "script_id"
        const val FIELD_SUBSCRIPTION_LOCATION_PRECISIONS = "subscription_location_precisions"
        const val FIELD_HTML = "html"
        const val FIELD_JAVASCRIPT = "javascript"
        const val FIELD_STATE = "state"
        const val FIELD_TITLE = "title"
        const val INVALID_SENSOR_TYPE = -1
        const val LOG_TAG = "DikcizAutomation"
        const val MAXIMUM_MEDIA_VOLUME_PERCENT = 100
        const val MEDIA_VOLUME_ROUNDING_OFFSET = MAXIMUM_MEDIA_VOLUME_PERCENT / 2
        const val MINIMUM_MEDIA_VOLUME_INDEX = 0
        const val NO_VOLUME_UI_FLAGS = 0
        const val NO_ELAPSED_REALTIME = 0L
        const val OUTCOME_EXECUTED = "executed"
        const val POLICY_ID_UNRESTRICTED = "unrestricted"
        const val PAYLOAD_ALARM_ELAPSED_REALTIME = "alarmElapsedRealtime"
        const val PAYLOAD_ALARM_RECOVERY = "alarmRecovery"
        const val REASON_ANDROID_PERMISSION_DENIED = "android_permission_denied"
        const val REASON_ANDROID_REJECTED = "android_rejected"
        const val REASON_CAPABILITY_DENIED = "capability_denied"
        const val REASON_CONFIGURATION_REJECTED = "configuration_rejected"
        const val REASON_CONFIGURATION_UNAVAILABLE = "configuration_unavailable"
        const val REASON_MINIMUM_INTERVAL = "minimum_interval"
        const val REASON_DEVICE_ADMIN_INACTIVE = "device_admin_inactive"
        const val REASON_EVENT_DEPTH_EXCEEDED = "event_depth_exceeded"
        const val REASON_LAST_ACCEPTED_CONFIGURATION = "last_accepted_configuration"
        const val REASON_RATE_LIMITED = "rate_limited"
        const val REASON_STORAGE_UNAVAILABLE = "storage_unavailable"
        const val REASON_TARGET_DENIED = "target_denied"
        const val REASON_TARGET_UNAVAILABLE = "target_unavailable"
        const val RESOLVE_ACTIVITY_FLAGS = PackageManager.MATCH_DEFAULT_ONLY
        const val REASON_SCRIPT_DISABLED = "script_disabled"
        const val REASON_SUBSCRIPTION_MISMATCH = "subscription_mismatch"
        const val STATUS_ACTION_REJECTED_PREFIX = "Action rejected: "
        const val STATUS_COMPLETED = "Completed"
        const val STATUS_NO_EVENT_HANDLER = "No event handler"

        val HTML_WIDGET_PATCH_FIELDS = setOf(
            FIELD_TITLE,
            FIELD_HTML,
            FIELD_CSS,
            FIELD_JAVASCRIPT,
            FIELD_STATE,
        )
    }
}
