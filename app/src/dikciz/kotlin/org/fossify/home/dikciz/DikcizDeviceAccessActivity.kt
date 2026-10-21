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
    private lateinit var logger: DikcizLogger

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
        findViewById<Button>(R.id.dikciz_device_access_close).setOnClickListener {
            finishAndRemoveTask()
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
        renderAccess()
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
        if (requestCode == ACCESS_REQUEST_CODE && foreground?.get() === this) {
            renderAccess()
        }
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
        progress.text = getString(R.string.dikciz_device_access_progress, granted, actionable.size)
        entries.removeAllViews()
        views.clear()
        views[SEMANTIC_PROGRESS] = progress
        views[SEMANTIC_SCROLL] = scroll
        views[SEMANTIC_SETTINGS_DEVICE_ACCESS_CLOSE] = findViewById(R.id.dikciz_device_access_close)
        access.forEach { entry ->
            val title = getString(entry.requirement.titleResourceID)
            val label = TextView(this).apply {
                text = getString(R.string.dikciz_device_access_entry, title, getString(stateResourceID(entry.state)))
                setTextColor(getColor(R.color.dikciz_foreground))
                setPadding(0, resources.getDimensionPixelSize(R.dimen.dikciz_page_chrome_inset), 0, 0)
            }
            entries.addView(label)
            views[SEMANTIC_ENTRY_PREFIX + entry.requirement.persistedValue] = label
            if (entry.state == DikcizDeviceAccessState.NeedsSetup) {
                val button = Button(this).apply {
                    text = getString(R.string.dikciz_device_access_grant_next, title)
                    setOnClickListener { requestAccess(entry.requirement) }
                }
                entries.addView(button)
                views[SEMANTIC_SETUP_PREFIX + entry.requirement.persistedValue] = button
            }
        }
        val next = access.firstOrNull { it.state == DikcizDeviceAccessState.NeedsSetup }?.requirement
        nextButton.visibility = if (next == null) View.GONE else View.VISIBLE
        nextButton.setOnClickListener { next?.let(::requestAccess) }
        if (next == null) {
            entries.addView(TextView(this).apply {
                text = getString(R.string.dikciz_device_access_complete)
                setTextColor(getColor(R.color.dikciz_foreground))
            })
        } else {
            nextButton.text = getString(R.string.dikciz_device_access_grant_next, getString(next.titleResourceID))
            views[SEMANTIC_SETTINGS_DEVICE_ACCESS_NEXT] = nextButton
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
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), ACCESS_REQUEST_CODE)
                return
            }
            if (requirement == DikcizDeviceAccessRequirement.LocationBackground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                return
            }
            when (requirement.kind) {
                DikcizDeviceAccessKind.RuntimePermission -> requestPermissions(
                    DikcizDeviceAccess.runtimePermissions(requirement),
                    ACCESS_REQUEST_CODE,
                )
                DikcizDeviceAccessKind.SystemSettings -> {
                    val settings = DikcizDeviceAccess.settingsIntent(this, requirement)
                    if (settings == null) {
                        unavailable(requirement)
                        return
                    }
                    startActivity(settings)
                }
                DikcizDeviceAccessKind.DeviceAdministration -> startActivityForResult(
                    DikcizDeviceAdministration.activationIntent(this),
                    ACCESS_REQUEST_CODE,
                )
                DikcizDeviceAccessKind.OnDemand -> unavailable(requirement)
            }
        } catch (exception: ActivityNotFoundException) {
            unavailable(requirement, exception)
        } catch (exception: SecurityException) {
            unavailable(requirement, exception)
        }
    }

    private fun unavailable(requirement: DikcizDeviceAccessRequirement, exception: Exception? = null) {
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
