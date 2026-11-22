package org.fossify.home.dikciz

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.lang.ref.WeakReference
import org.fossify.home.R
import org.json.JSONObject

internal class DikcizDeviceAccessActivity : Activity() {
    private val views = linkedMapOf<String, View>()
    private lateinit var scroll: ScrollView
    private lateinit var entries: LinearLayout
    private lateinit var progress: TextView
    private lateinit var nextButton: Button
    private lateinit var skipButton: Button
    private lateinit var logger: DikcizLogger
    private val deferred = linkedSetOf<String>()
    private val settingsOnly = linkedSetOf<String>()
    private var runtimeRequest: String? = null
    private var externalRequest: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dikciz_device_access)
        setTaskDescription(ActivityManager.TaskDescription(getString(R.string.dikciz_device_access_title)))
        logger = DikcizLogger(LOG_COMPONENT, HomeConfigStore(this).logDirectory)
        DikcizAutomationEventBus.lastAcceptedConfiguration(this)?.logging?.let(logger::configure)
        scroll = findViewById(R.id.dikciz_device_access_scroll)
        entries = findViewById(R.id.dikciz_device_access_entries)
        progress = findViewById(R.id.dikciz_device_access_progress)
        nextButton = findViewById(R.id.dikciz_device_access_next)
        skipButton = findViewById(R.id.dikciz_device_access_skip)
        deferred.addAll(savedInstanceState?.getStringArrayList(STATE_DEFERRED).orEmpty())
        settingsOnly.addAll(savedInstanceState?.getStringArrayList(STATE_SETTINGS_ONLY).orEmpty())
        runtimeRequest = savedInstanceState?.getString(STATE_RUNTIME_REQUEST)
        externalRequest = savedInstanceState?.getString(STATE_EXTERNAL_REQUEST)
        DikcizUiStyle.button(nextButton, primary = true)
        DikcizUiStyle.button(skipButton)
        findViewById<Button>(R.id.dikciz_device_access_close).apply {
            DikcizUiStyle.button(this)
            setOnClickListener { finishAndRemoveTask() }
        }
        renderAccess()
        if (!DikcizLocalCommands.isAvailable()) {
            logger.info(EVENT_HOST_RESTORE)
            startActivity(Intent(this, DikcizHomeActivity::class.java)
                .putExtra(EXTRA_OPEN_SETUP, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = WeakReference(this)
        externalRequest?.let { key ->
            val entry = DikcizDeviceAccess.entries(this).firstOrNull { it.requirement.persistedValue == key }
            if (entry?.state == DikcizDeviceAccessState.NeedsSetup) deferred.add(key)
        }
        externalRequest = null
        renderAccess()
    }

    override fun onSaveInstanceState(state: Bundle) {
        state.putStringArrayList(STATE_DEFERRED, ArrayList(deferred))
        state.putStringArrayList(STATE_SETTINGS_ONLY, ArrayList(settingsOnly))
        state.putString(STATE_RUNTIME_REQUEST, runtimeRequest)
        state.putString(STATE_EXTERNAL_REQUEST, externalRequest)
        super.onSaveInstanceState(state)
    }

    override fun onPause() {
        if (foreground?.get() === this) {
            foreground = null
            screenListener?.invoke()
        }
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != ACCESS_REQUEST_CODE) return
        val key = runtimeRequest ?: return
        runtimeRequest = null
        val denied = permissions.filterIndexed { index, _ ->
            grantResults.getOrNull(index) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (denied.isNotEmpty() || grantResults.isEmpty()) deferred.add(key)
        if (denied.any { !shouldShowRequestPermissionRationale(it) }) settingsOnly.add(key)
        renderAccess()
    }

    fun semanticViews(): Map<String, View> = views

    fun scrollState(): JSONObject = JSONObject()
        .put(KEY_Y, scroll.scrollY)
        .put(KEY_RANGE, entries.height + scroll.paddingTop + scroll.paddingBottom)
        .put(KEY_EXTENT, scroll.height)

    fun scrollBy(deltaY: Int) {
        scroll.scrollBy(0, deltaY)
    }

    fun scrollTo(view: View) {
        view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
    }

    private fun renderAccess() {
        val previousScroll = scroll.scrollY
        val access = DikcizDeviceAccess.entries(this)
        val actionable = access.filter { entry ->
            entry.state != DikcizDeviceAccessState.Unavailable && entry.state != DikcizDeviceAccessState.OnDemand
        }
        val granted = actionable.count { it.state == DikcizDeviceAccessState.Granted }
        actionable.filter { it.state == DikcizDeviceAccessState.Granted }.forEach {
            deferred.remove(it.requirement.persistedValue)
            settingsOnly.remove(it.requirement.persistedValue)
        }
        progress.text = getString(R.string.dikciz_device_access_progress, granted, actionable.size)
        entries.removeAllViews()
        views.clear()
        views[SEMANTIC_PROGRESS] = progress
        views[SEMANTIC_SCROLL] = scroll
        views[SEMANTIC_SETTINGS_DEVICE_ACCESS_CLOSE] = findViewById(R.id.dikciz_device_access_close)
        access.forEach { entry ->
            val title = getString(entry.requirement.titleResourceID)
            val key = entry.requirement.persistedValue
            val label = TextView(this).apply {
                val state = if (key in deferred && entry.state == DikcizDeviceAccessState.NeedsSetup) {
                    R.string.dikciz_device_access_state_deferred
                } else stateResourceID(entry.state)
                text = getString(R.string.dikciz_device_access_entry, title, getString(state))
                setTextColor(getColor(R.color.dikciz_foreground))
                setPadding(0, resources.getDimensionPixelSize(R.dimen.dikciz_page_chrome_inset), 0, 0)
            }
            entries.addView(label)
            views[SEMANTIC_ENTRY_PREFIX + entry.requirement.persistedValue] = label
            if (entry.state == DikcizDeviceAccessState.NeedsSetup) {
                val button = Button(this).apply {
                    DikcizUiStyle.button(this)
                    text = getString(when (key) {
                        in settingsOnly -> R.string.dikciz_device_access_open_settings
                        in deferred -> R.string.dikciz_device_access_retry
                        else -> R.string.dikciz_device_access_grant_next
                    }, title)
                    setOnClickListener {
                        if (key in settingsOnly) openApplicationSettings(entry.requirement) else requestAccess(entry.requirement)
                    }
                }
                entries.addView(button)
                views[SEMANTIC_SETUP_PREFIX + entry.requirement.persistedValue] = button
            }
        }
        val next = access.firstOrNull { it.state == DikcizDeviceAccessState.NeedsSetup && it.requirement.persistedValue !in deferred }?.requirement
        nextButton.visibility = if (next == null) View.GONE else View.VISIBLE
        skipButton.visibility = nextButton.visibility
        nextButton.setOnClickListener { next?.let(::requestAccess) }
        skipButton.setOnClickListener {
            next?.let { deferred.add(it.persistedValue) }
            renderAccess()
        }
        if (next == null) {
            entries.addView(TextView(this).apply {
                text = getString(if (granted == actionable.size) R.string.dikciz_device_access_complete
                    else R.string.dikciz_device_access_setup_reviewed)
                setTextColor(getColor(R.color.dikciz_foreground))
            })
        } else {
            nextButton.text = getString(R.string.dikciz_device_access_grant_next, getString(next.titleResourceID))
            views[SEMANTIC_SETTINGS_DEVICE_ACCESS_NEXT] = nextButton
            views[SEMANTIC_SETTINGS_DEVICE_ACCESS_PREFIX + "skip"] = skipButton
        }
        scroll.post {
            scroll.scrollTo(0, previousScroll)
            if (foreground?.get() === this) screenListener?.invoke()
        }
        logger.info(EVENT_REFRESHED, mapOf(FIELD_GRANTED to granted, FIELD_REQUIREMENTS to actionable.size))
    }

    private fun requestAccess(requirement: DikcizDeviceAccessRequirement) {
        if (DikcizDeviceAccess.state(this, requirement) != DikcizDeviceAccessState.NeedsSetup) {
            renderAccess()
            return
        }
        logger.info(EVENT_REQUESTED, mapOf(FIELD_REQUIREMENT to requirement.persistedValue))
        try {
            if (requirement == DikcizDeviceAccessRequirement.SharedStorage && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                requestRuntimeAccess(requirement, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                return
            }
            if (requirement == DikcizDeviceAccessRequirement.LocationBackground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                openApplicationSettings(requirement)
                return
            }
            when (requirement.kind) {
                DikcizDeviceAccessKind.RuntimePermission -> requestRuntimeAccess(
                    requirement,
                    DikcizDeviceAccess.runtimePermissions(requirement),
                )
                DikcizDeviceAccessKind.SystemSettings -> {
                    val settings = DikcizDeviceAccess.settingsIntent(this, requirement)
                    if (settings == null) {
                        unavailable(requirement)
                        return
                    }
                    externalRequest = requirement.persistedValue
                    startActivity(settings)
                }
                DikcizDeviceAccessKind.DeviceAdministration -> {
                    externalRequest = requirement.persistedValue
                    startActivityForResult(DikcizDeviceAdministration.activationIntent(this), ACCESS_REQUEST_CODE)
                }
                DikcizDeviceAccessKind.OnDemand -> unavailable(requirement)
            }
        } catch (exception: ActivityNotFoundException) {
            unavailable(requirement, exception)
        } catch (exception: SecurityException) {
            unavailable(requirement, exception)
        }
    }

    private fun requestRuntimeAccess(requirement: DikcizDeviceAccessRequirement, permissions: Array<String>) {
        if (runtimeRequest != null) return
        runtimeRequest = requirement.persistedValue
        requestPermissions(permissions, ACCESS_REQUEST_CODE)
    }

    private fun openApplicationSettings(requirement: DikcizDeviceAccessRequirement) {
        externalRequest = requirement.persistedValue
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (exception: ActivityNotFoundException) {
            unavailable(requirement, exception)
        } catch (exception: SecurityException) {
            unavailable(requirement, exception)
        }
    }

    private fun unavailable(requirement: DikcizDeviceAccessRequirement, exception: Exception? = null) {
        runtimeRequest = null
        externalRequest = null
        deferred.add(requirement.persistedValue)
        logger.warn(EVENT_UNAVAILABLE, buildMap {
            put(FIELD_REQUIREMENT, requirement.persistedValue)
            exception?.let { put(FIELD_ERROR_CLASS, it.javaClass.simpleName) }
        })
        Toast.makeText(this, R.string.dikciz_device_access_request_unavailable, Toast.LENGTH_LONG).show()
        renderAccess()
    }

    private fun stateResourceID(state: DikcizDeviceAccessState): Int = when (state) {
        DikcizDeviceAccessState.Granted -> R.string.dikciz_device_access_state_granted
        DikcizDeviceAccessState.NeedsSetup -> R.string.dikciz_device_access_state_needs_setup
        DikcizDeviceAccessState.Unavailable -> R.string.dikciz_device_access_state_unavailable
        DikcizDeviceAccessState.OnDemand -> R.string.dikciz_device_access_state_on_demand
    }

    companion object {
        const val SCREEN = "deviceAccess"
        const val EXTRA_OPEN_SETUP = "org.fossify.home.dikciz.OPEN_DEVICE_ACCESS_SETUP"
        private const val SEMANTIC_SETTINGS_DEVICE_ACCESS_PREFIX = "settings:device-access:"
        private const val SEMANTIC_SETTINGS_DEVICE_ACCESS_CLOSE =
            "${SEMANTIC_SETTINGS_DEVICE_ACCESS_PREFIX}close"
        private const val SEMANTIC_SETTINGS_DEVICE_ACCESS_NEXT =
            "${SEMANTIC_SETTINGS_DEVICE_ACCESS_PREFIX}next"
        private const val SEMANTIC_PROGRESS = SEMANTIC_SETTINGS_DEVICE_ACCESS_PREFIX + "progress"
        private const val SEMANTIC_SCROLL = SEMANTIC_SETTINGS_DEVICE_ACCESS_PREFIX + "scroll"
        private const val SEMANTIC_ENTRY_PREFIX = SEMANTIC_SETTINGS_DEVICE_ACCESS_PREFIX + "entry:"
        private const val SEMANTIC_SETUP_PREFIX = SEMANTIC_SETTINGS_DEVICE_ACCESS_PREFIX + "setup:"
        private const val ACCESS_REQUEST_CODE = 1
        private const val STATE_DEFERRED = "deferred"
        private const val STATE_SETTINGS_ONLY = "settingsOnly"
        private const val STATE_RUNTIME_REQUEST = "runtimeRequest"
        private const val STATE_EXTERNAL_REQUEST = "externalRequest"
        private const val LOG_COMPONENT = "DikcizDeviceAccess"
        private const val EVENT_HOST_RESTORE = "device_access_host_restore_requested"
        private const val EVENT_REFRESHED = "device_access_screen_refreshed"
        private const val EVENT_REQUESTED = "device_access_request_started"
        private const val EVENT_UNAVAILABLE = "device_access_request_unavailable"
        private const val FIELD_REQUIREMENT = "requirement"
        private const val FIELD_GRANTED = "granted_count"
        private const val FIELD_REQUIREMENTS = "requirement_count"
        private const val FIELD_ERROR_CLASS = "error_class"
        private const val KEY_Y = "y"
        private const val KEY_RANGE = "range"
        private const val KEY_EXTENT = "extent"
        private var foreground: WeakReference<DikcizDeviceAccessActivity>? = null
        private var screenListener: (() -> Unit)? = null
        private var listenerOwner: String? = null

        fun current(): DikcizDeviceAccessActivity? = foreground?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

        fun intent(context: Context): Intent = Intent(context, DikcizDeviceAccessActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )

        fun attachHost(owner: String, listener: () -> Unit) {
            listenerOwner = owner
            screenListener = listener
        }

        fun detachHost(owner: String) {
            if (listenerOwner == owner) {
                listenerOwner = null
                screenListener = null
            }
        }
    }
}
