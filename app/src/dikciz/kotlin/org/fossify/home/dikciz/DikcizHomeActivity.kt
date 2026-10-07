package org.fossify.home.dikciz

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.health.connect.client.PermissionController
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import org.fossify.home.R
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class DikcizHomeActivity : Activity() {
    private data class ScriptDashboardRunStatusTextTag(
        val scriptID: String,
        val field: ScriptDashboardRunStatusField,
    )

    private enum class ScriptDashboardRunStatusField {
        Status,
        LastRun,
    }

    private lateinit var appDrawer: DikcizAppDrawerView
    private lateinit var horizontalPageIndicator: DikcizPageIndicatorView
    private lateinit var launcherContent: LinearLayout
    private lateinit var launcherControl: DikcizLauncherControlView
    private lateinit var launcherWallpaper: ImageView
    private lateinit var pageCanvasHost: FrameLayout
    private lateinit var pageScrollView: DikcizPageScrollView
    private lateinit var safeModeControls: LinearLayout
    private lateinit var safeModeExitButton: Button
    private lateinit var safeModeResetButton: Button
    private lateinit var startupBrandMark: ImageView
    private lateinit var startupOverlay: FrameLayout
    private lateinit var widgetContainer: DikcizPageGridLayout
    private lateinit var verticalPageIndicator: DikcizPageIndicatorView

    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var widgetPickerCatalogue: DikcizWidgetPickerCatalogue
    private lateinit var homeConfigStore: HomeConfigStore
    private lateinit var dikcizThemeStore: DikcizThemeStore
    private lateinit var automationControlPlane: DikcizAutomationControlPlane
    private lateinit var mcpControlPlane: DikcizMcpControlPlane
    private lateinit var dikcizLogger: DikcizLogger
    private lateinit var remoteAccessAuth: DikcizRemoteAccessAuth
    private lateinit var dikcizStyleRenderer: DikcizStyleRenderer
    private lateinit var appActionRunner: DikcizAppActionRunner
    private lateinit var homeGestureDetector: DikcizHomeGestureDetector
    private val privilegedShell = DikcizPrivilegedShell()
    private val automationViews = mutableMapOf<String, View>()
    private val cachedPageAutomationViews = mutableMapOf<String, Map<String, View>>()
    private val cachedPageCanvases = mutableMapOf<String, DikcizPageGridLayout>()
    private val widgetEditorAutomationSemanticIDs = mutableSetOf<String>()
    private val providerAutomationViews = mutableMapOf<String, ProviderAutomationView>()
    private val pendingHtmlContentFits = mutableSetOf<String>()
    private val recoveringHtmlWidgetIDs = mutableSetOf<String>()
    private var providerAutomationRenderTokenSequence = INITIAL_PROVIDER_AUTOMATION_RENDER_TOKEN
    private var activeTheme: DikcizTheme? = null
    private var homeConfiguration: HomeConfiguration? = null
    private val homeConfigFileObservers = mutableListOf<HomeConfigFileObserver>()
    private var pendingProviderWidget: PendingProviderWidget? = null
    private var pendingProviderReconfiguration: ProviderHomeWidget? = null
    private var activeWidgetEdit: ActiveWidgetEdit? = null
    private var activeWidgetResize: ActiveWidgetResize? = null
    private var activeWidgetResizeGesture: WidgetResizeGesture? = null
    private var activeWidgetMove: ActiveWidgetMove? = null
    private var armedWidgetMoveID: String? = null
    private var activeDialog: AlertDialog? = null
    private var pendingWidgetMove: PendingWidgetMove? = null
    private var pageAutomationViewCapture: MutableMap<String, View>? = null
    private var pageBeingBuilt: HomePage? = null
    private var shouldResumeFullDeviceAccessSetup = false
    private var controlPlanesStarted = false
    private var controlPlaneStartAttemptsRemaining = CONTROL_PLANE_START_ATTEMPTS
    private var isControlPlaneStartRetryScheduled = false
    private var isConfigurationErrorVisible = false
    private var isStartupBrandVisible = false
    private var hasPendingDefaultHomePageSelection = false
    private var hasPendingRenderingAssetChange = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val startupBrandInterpolator = DecelerateInterpolator()
    private val hideStartupBrandRunnable = Runnable(::hideStartupBrand)
    private val reloadConfigurationRunnable = Runnable { reloadConfiguration(false) }
    private val pollConfigurationTreeRunnable = Runnable(::pollConfigurationTree)
    private var configurationTreeFingerprints: Map<String, String>? = null
    // Stat work for the poll never runs on the main thread. onStartCommand for
    // the automation service runs there too, and Android kills the process when
    // it cannot reach startForeground within five seconds of a foreground start.
    private val configurationPollExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()
    private val retryControlPlaneStartRunnable = Runnable {
        isControlPlaneStartRetryScheduled = false
        startControlPlanesIfNeeded()
    }
    private val healthConnectPermissionContract = PermissionController.createRequestPermissionResultContract()

    private val isSafeMode: Boolean
        get() = intent.getBooleanExtra(EXTRA_SAFE_MODE, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dikciz_home)
        bindViews()
        prepareStartupBrand(savedInstanceState)
        homeConfigStore = HomeConfigStore(this)
        dikcizThemeStore = DikcizThemeStore(this, homeConfigStore.themeDirectory)
        dikcizLogger = DikcizLogger(LOG_TAG, homeConfigStore.logDirectory)
        val configurationDirectory = checkNotNull(homeConfigStore.configurationFile.parentFile)
        remoteAccessAuth = DikcizRemoteAccessAuth(
            configurationDirectory,
            dikcizLogger,
        )
        dikcizStyleRenderer = DikcizStyleRenderer(
            context = this,
            fontDirectory = File(
                homeConfigStore.configurationFile.parentFile,
                DikcizStyleCodec.FONT_DIRECTORY_NAME,
            ),
            onFontFallback = { font ->
                dikcizLogger.warn(
                    EVENT_FONT_FALLBACK,
                    mapOf(
                        FIELD_FONT_ID to font.id,
                        FIELD_FONT_SOURCE to font.source.persistedValue,
                    ),
                )
            },
        )
        appWidgetManager = AppWidgetManager.getInstance(this)
        widgetPickerCatalogue = DikcizWidgetPickerCatalogue(
            context = this,
            appWidgetManager = appWidgetManager,
            logger = dikcizLogger,
            loadLaunchableApps = ::launchableApps,
        )
        appActionRunner = DikcizAppActionRunner(
            context = this,
            packageManager = packageManager,
            logger = dikcizLogger,
            privilegedShell = privilegedShell,
            launchComponent = ::launchExplicitComponent,
            addShortcut = ::addAppShortcutToSelectedPage,
        )
        homeGestureDetector = DikcizHomeGestureDetector(
            context = this,
            activationBounds = ::homeGestureActivationBounds,
            isGestureEnabled = ::isHomeGestureEnabled,
            onDrawerRequested = { openAppDrawer(DRAWER_SOURCE_GESTURE) },
        )
        appWidgetHost = object : AppWidgetHost(this, APP_WIDGET_HOST_ID) {
            override fun onCreateView(
                context: Context,
                appWidgetID: Int,
                appWidget: AppWidgetProviderInfo,
            ): AppWidgetHostView {
                return DikcizProviderWidgetHostView(
                    context = context,
                    nextAutomationRenderToken = ::nextProviderAutomationRenderToken,
                ).apply {
                    setAppWidget(appWidgetID, appWidget)
                }
            }
        }
        val automationTarget = object : DikcizAutomationTarget {
            override fun snapshot(): JSONObject = automationSnapshot()

            override fun requiresMainThread(command: DikcizAutomationCommand): Boolean {
                return command.type !in AUTOMATION_BACKGROUND_COMMAND_TYPES
            }

            override fun execute(command: DikcizAutomationCommand): JSONObject {
                return executeAutomationCommand(command)
            }
        }
        automationControlPlane = DikcizAutomationControlPlane(
            mainHandler,
            automationTarget,
            dikcizLogger,
            remoteAccessAuth,
        )
        mcpControlPlane = DikcizMcpControlPlane(mainHandler, automationTarget, dikcizLogger, remoteAccessAuth)
        DikcizAutomationEventBus.setHtmlDomPatchListener(applicationContext, ::patchRenderedHtmlDom)
        dikcizLogger.info(EVENT_ACTIVITY_CREATED)
    }

    override fun onStart() {
        super.onStart()
        dikcizLogger.info(
            EVENT_ACTIVITY_STARTED,
            mapOf(FIELD_SAFE_MODE to isSafeMode),
        )
        if (!homeConfigStore.hasSharedStorageAccess()) {
            dikcizLogger.warn(EVENT_STORAGE_ACCESS_UNAVAILABLE)
            showStorageAccessRequirement()
            startControlPlanesIfNeeded()
            return
        }
        if (homeConfiguration == null && isLauncherHomeIntent()) {
            hasPendingDefaultHomePageSelection = true
        }
        reloadConfiguration(true)
        startLauncherServicesIfReady()
        resumeFullDeviceAccessSetupIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        hasPendingDefaultHomePageSelection = isLauncherHomeIntent()
        dikcizLogger.info(
            EVENT_SAFE_MODE_LAUNCH_REQUESTED,
            mapOf(FIELD_SAFE_MODE to isSafeMode),
        )
        if (!homeConfigStore.hasSharedStorageAccess()) {
            showStorageAccessRequirement()
            startControlPlanesIfNeeded()
            return
        }
        reloadConfiguration(true)
        startLauncherServicesIfReady()
    }

    override fun onStop() {
        dismissStartupBrand()
        DikcizClipboardMonitor.stop()
        appWidgetHost.stopListening()
        DikcizAutomationEventBus.setConfigurationChangeListener(applicationContext, null)
        DikcizAutomationEventBus.setEventListener(applicationContext, null)
        stopConfigurationObserver()
        dikcizLogger.info(EVENT_ACTIVITY_STOPPED)
        super.onStop()
    }

    override fun onDestroy() {
        dismissStartupBrand()
        configurationPollExecutor.shutdownNow()
        mainHandler.removeCallbacks(retryControlPlaneStartRunnable)
        DikcizClipboardMonitor.stop()
        DikcizAutomationEventBus.setHtmlDomPatchListener(applicationContext, null)
        DikcizAutomationEventBus.setRemoteEventListener(applicationContext, null)
        mcpControlPlane.stop()
        automationControlPlane.stop()
        controlPlanesStarted = false
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LEGACY_STORAGE_PERMISSION_REQUEST_CODE) {
            reloadConfiguration(true)
            startLauncherServicesIfReady()
            return
        }
        if (requestCode in AUTOMATION_PERMISSION_REQUEST_CODES) {
            reloadConfiguration(false)
            homeConfiguration?.let(::syncAutomationService)
        }
        if (requestCode == FULL_DEVICE_ACCESS_PERMISSION_REQUEST_CODE) {
            reloadConfiguration(false)
            homeConfiguration?.let(::syncAutomationService)
            resumeFullDeviceAccessSetupIfNeeded()
        }
    }

    @Deprecated("Android platform callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            APP_WIDGET_BIND_REQUEST_CODE -> handleAppWidgetBindResult(resultCode)
            APP_WIDGET_CONFIGURATION_REQUEST_CODE -> handleAppWidgetConfigurationResult(resultCode)
            APP_WIDGET_RECONFIGURATION_REQUEST_CODE -> handleAppWidgetReconfigurationResult(resultCode)
            AUTOMATION_DEVICE_ADMINISTRATION_REQUEST_CODE -> reloadConfiguration(false)
            AUTOMATION_HEALTH_CONNECT_PERMISSION_REQUEST_CODE -> {
                healthConnectPermissionContract.parseResult(resultCode, data)
                reloadConfiguration(false)
            }
        }
        if (requestCode == FULL_DEVICE_ACCESS_SMS_ROLE_REQUEST_CODE ||
            requestCode == AUTOMATION_DEVICE_ADMINISTRATION_REQUEST_CODE
        ) {
            resumeFullDeviceAccessSetupIfNeeded()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (dismissStartupBrandOnTouch(event)) {
            return true
        }
        if (routeArmedWidgetMoveTouch(event)) {
            return true
        }
        if (homeGestureDetector.onTouchEvent(event)) {
            cancelPendingTouchDelivery(event)
            return true
        }
        if (routeWidgetResizeTouch(event)) {
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN && finishWidgetResizeWhenTouchedOutside(event)) {
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            dismissWidgetEditWhenTouchedOutside(event)
        }
        if (routeWidgetMoveTouch(event)) {
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    @Deprecated("Launcher back handling predates the predictive-back callback contract.")
    override fun onBackPressed() {
        if (isStartupBrandVisible) {
            dismissStartupBrand()
            return
        }
        if (appDrawer.isOpen) {
            closeAppDrawer()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun bindViews() {
        horizontalPageIndicator = findViewById(R.id.dikciz_horizontal_page_indicator)
        launcherContent = findViewById(R.id.dikciz_launcher_content)
        launcherWallpaper = findViewById(R.id.dikciz_launcher_wallpaper)
        pageCanvasHost = findViewById(R.id.dikciz_page_canvas_host)
        pageScrollView = findViewById(R.id.dikciz_page_scroll)
        safeModeControls = findViewById(R.id.dikciz_safe_mode_controls)
        safeModeExitButton = findViewById(R.id.dikciz_safe_mode_exit)
        safeModeResetButton = findViewById(R.id.dikciz_safe_mode_reset)
        startupBrandMark = findViewById(R.id.dikciz_startup_brand_mark)
        startupOverlay = findViewById(R.id.dikciz_startup_overlay)
        widgetContainer = findViewById(R.id.dikciz_widget_container)
        verticalPageIndicator = findViewById(R.id.dikciz_vertical_page_indicator)
        appDrawer = findViewById(R.id.dikciz_app_drawer)
        launcherControl = findViewById(R.id.dikciz_launcher_control)
        launcherControl.onControlsRequested = ::showLauncherControlSheet
        launcherControl.onPageSearchRequested = ::showPageSearchSheet
        bindAppDrawer()
        pageScrollView.setOnTouchListener { view, event ->
            if (isSafeMode) {
                return@setOnTouchListener true
            }
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                view.performClick()
            }
            false
        }
    }

    private fun prepareStartupBrand(savedInstanceState: Bundle?) {
        if (savedInstanceState != null || hasShownStartupBrandInCurrentProcess) {
            startupOverlay.visibility = View.GONE
            return
        }
        hasShownStartupBrandInCurrentProcess = true
        isStartupBrandVisible = true
        startupOverlay.alpha = ENABLED_WIDGET_ALPHA
        startupOverlay.visibility = View.VISIBLE
        startupOverlay.setOnClickListener { dismissStartupBrand() }
        registerAutomationView(SEMANTIC_STARTUP_DISMISS, startupOverlay)
        startupBrandMark.alpha = TRANSPARENT_OPACITY
        startupBrandMark.scaleX = STARTUP_BRAND_INITIAL_SCALE
        startupBrandMark.scaleY = STARTUP_BRAND_INITIAL_SCALE
        startupOverlay.post {
            if (!isStartupBrandVisible) {
                return@post
            }
            startupBrandMark.animate()
                .alpha(ENABLED_WIDGET_ALPHA)
                .scaleX(ENABLED_WIDGET_ALPHA)
                .scaleY(ENABLED_WIDGET_ALPHA)
                .setDuration(STARTUP_BRAND_ENTER_DURATION_MILLISECONDS)
                .setInterpolator(startupBrandInterpolator)
                .start()
            mainHandler.postDelayed(
                hideStartupBrandRunnable,
                STARTUP_BRAND_VISIBLE_DURATION_MILLISECONDS,
            )
        }
    }

    private fun hideStartupBrand() {
        if (!isStartupBrandVisible) {
            return
        }
        isStartupBrandVisible = false
        startupOverlay.animate()
            .alpha(TRANSPARENT_OPACITY)
            .setDuration(STARTUP_BRAND_EXIT_DURATION_MILLISECONDS)
            .setInterpolator(startupBrandInterpolator)
            .withEndAction {
                startupOverlay.alpha = ENABLED_WIDGET_ALPHA
                startupOverlay.visibility = View.GONE
            }
            .start()
    }

    private fun dismissStartupBrand() {
        mainHandler.removeCallbacks(hideStartupBrandRunnable)
        isStartupBrandVisible = false
        startupOverlay.animate().cancel()
        startupBrandMark.animate().cancel()
        startupOverlay.alpha = ENABLED_WIDGET_ALPHA
        startupOverlay.visibility = View.GONE
    }

    private fun configurePageIndicators(
        configuration: HomeConfiguration,
        selectedPage: HomePage,
    ) {
        configurePageIndicator(
            indicator = horizontalPageIndicator,
            axis = PageNavigationAxis.Horizontal,
            pageTitles = configuration.pagesByColumn().map { pages -> pages.first().title },
            selectedIndex = configuration.columnIndex(selectedPage),
            semanticID = SEMANTIC_ID_HORIZONTAL_PAGE_INDICATOR,
        ) { columnIndex ->
            configuration.pagesByColumn().getOrNull(columnIndex)
                ?.minByOrNull { page -> abs(page.position.row - selectedPage.position.row) }
                ?.let { page -> selectPage(page.id) }
        }
        configurePageIndicator(
            indicator = verticalPageIndicator,
            axis = PageNavigationAxis.Vertical,
            pageTitles = configuration.pagesInColumn(selectedPage.position.column).map(HomePage::title),
            selectedIndex = configuration.rowIndex(selectedPage),
            semanticID = SEMANTIC_ID_VERTICAL_PAGE_INDICATOR,
        ) { rowIndex ->
            configuration.pagesInColumn(selectedPage.position.column).getOrNull(rowIndex)
                ?.let { page -> selectPage(page.id) }
        }
    }

    private fun configurePageIndicator(
        indicator: DikcizPageIndicatorView,
        axis: PageNavigationAxis,
        pageTitles: List<String>,
        selectedIndex: Int,
        semanticID: String,
        onPageSelectionRequested: (Int) -> Unit,
    ) {
        indicator.axis = axis.indicatorAxis
        indicator.setPages(pageTitles)
        indicator.setSelectedPageIndex(selectedIndex)
        indicator.onPageSelectionRequested = onPageSelectionRequested
        indicator.onPageEdgeLongPressed = { side -> createPageAtRailExtremity(axis, side) }
        indicator.onInteraction = { interaction ->
            dikcizLogger.debug(
                EVENT_PAGE_INDICATOR_INTERACTION,
                mapOf(
                    FIELD_ACTION to interaction.action.persistedValue,
                    FIELD_PAGE_AXIS to axis.persistedValue,
                    FIELD_FROM_PAGE_INDEX to interaction.fromPageIndex,
                    FIELD_TO_PAGE_INDEX to interaction.toPageIndex,
                ),
            )
        }
        registerAutomationView(semanticID, indicator)
        PageInsertionSide.entries.forEach { side ->
            registerAutomationView(pageIndicatorEdgeSemanticID(axis, side), indicator)
        }
    }

    private fun startLauncherServicesIfReady() {
        startControlPlanesIfNeeded()
        if (homeConfiguration == null) return
        appWidgetHost.startListening()
        startConfigurationObserver()
        DikcizAutomationEventBus.setConfigurationChangeListener(
            applicationContext,
            ::scheduleConfigurationReload,
        )
        DikcizAutomationEventBus.setEventListener(
            applicationContext,
            ::deliverAutomationEventToHtmlWidgets,
        )
    }

    private fun startControlPlanesIfNeeded() {
        if (controlPlanesStarted || isControlPlaneStartRetryScheduled || isFinishing || isDestroyed) {
            return
        }
        val automationStarted = automationControlPlane.start()
        val mcpStarted = mcpControlPlane.start()
        if (!automationStarted || !mcpStarted) {
            automationControlPlane.stop()
            mcpControlPlane.stop()
            scheduleControlPlaneStartRetry()
            return
        }
        DikcizAutomationEventBus.setRemoteEventListener(
            applicationContext,
            ::publishAutomationEvent,
        )
        controlPlanesStarted = true
        controlPlaneStartAttemptsRemaining = CONTROL_PLANE_START_ATTEMPTS
    }

    private fun scheduleControlPlaneStartRetry() {
        if (controlPlaneStartAttemptsRemaining == NO_CONTROL_PLANE_START_ATTEMPTS) {
            return
        }
        controlPlaneStartAttemptsRemaining -= ONE_CONTROL_PLANE_START_ATTEMPT
        isControlPlaneStartRetryScheduled = true
        mainHandler.postDelayed(
            retryControlPlaneStartRunnable,
            CONTROL_PLANE_START_RETRY_DELAY_MILLISECONDS,
        )
    }

    private fun startConfigurationObserver() {
        if (homeConfigFileObservers.isNotEmpty()) {
            dikcizLogger.debug(EVENT_CONFIGURATION_OBSERVER_ALREADY_RUNNING)
            return
        }
        replaceConfigurationObservers(homeConfiguration ?: return)
        dikcizLogger.info(EVENT_CONFIGURATION_OBSERVER_STARTED)
    }

    private fun startConfigurationErrorObserver() {
        if (homeConfigFileObservers.isNotEmpty()) return
        val configurationDirectory = homeConfigStore.configurationFile.parentFile ?: return
        homeConfigFileObservers += HomeConfigFileObserver(
            configurationDirectory,
            { scheduleConfigurationReload() },
            setOf(DikcizRemoteAccessAuth.CONTROL_DIRECTORY_NAME),
        ).also(HomeConfigFileObserver::startWatching)
        startConfigurationTreePolling(null)
        dikcizLogger.info(EVENT_CONFIGURATION_OBSERVER_STARTED)
    }

    private fun stopConfigurationObserver() {
        homeConfigFileObservers.forEach(HomeConfigFileObserver::stopWatching)
        homeConfigFileObservers.clear()
        mainHandler.removeCallbacks(reloadConfigurationRunnable)
        mainHandler.removeCallbacks(pollConfigurationTreeRunnable)
        configurationTreeFingerprints = null
        dikcizLogger.info(EVENT_CONFIGURATION_OBSERVER_STOPPED)
    }

    private fun replaceConfigurationObservers(configuration: HomeConfiguration) {
        homeConfigFileObservers.forEach(HomeConfigFileObserver::stopWatching)
        homeConfigFileObservers.clear()
        val configurationDirectory = homeConfigStore.configurationFile.parentFile?.absolutePath
        val renderingAssetDirectories = setOf(
            homeConfigStore.themeDirectory.absolutePath,
            homeConfigStore.wallpaperDirectory.absolutePath,
            homeConfigStore.fontDirectory.absolutePath,
        )
        homeConfigStore.configurationWatchDirectories(configuration).forEach { directory ->
            val ignoredPaths = if (directory.absolutePath == configurationDirectory) {
                setOf(DikcizRemoteAccessAuth.CONTROL_DIRECTORY_NAME)
            } else {
                emptySet()
            }
            homeConfigFileObservers += HomeConfigFileObserver(
                directory,
                {
                    scheduleConfigurationReload(
                        isRenderingAssetChange = directory.absolutePath in renderingAssetDirectories,
                    )
                },
                ignoredPaths,
            )
                .also(HomeConfigFileObserver::startWatching)
        }
        startConfigurationTreePolling(configuration)
    }

    /**
     * Backs the file observers up with a cheap stat poll.
     *
     * A FileObserver watches the FUSE view of shared storage. Writes that reach
     * the files another way, an adb push from a computer or this process saving
     * its own tree, never surface on that view, so an edit made outside the
     * launcher can sit on disk unnoticed. The public tree is documented to
     * reload automatically, so the observers carry the instant case and this
     * carries the rest.
     */
    private fun startConfigurationTreePolling(configuration: HomeConfiguration?) {
        mainHandler.removeCallbacks(pollConfigurationTreeRunnable)
        scanConfigurationTree(configuration) { fingerprints ->
            configurationTreeFingerprints = fingerprints
        }
    }

    private fun pollConfigurationTree() {
        val previous = configurationTreeFingerprints ?: return
        scanConfigurationTree(homeConfiguration) { current ->
            if (current == previous) return@scanConfigurationTree
            configurationTreeFingerprints = current
            val renderingAssetDirectories = setOf(
                homeConfigStore.themeDirectory.absolutePath,
                homeConfigStore.wallpaperDirectory.absolutePath,
                homeConfigStore.fontDirectory.absolutePath,
            )
            val changedDirectories = (previous.keys + current.keys).filter { directory ->
                previous[directory] != current[directory]
            }
            scheduleConfigurationReload(
                isRenderingAssetChange = changedDirectories.any { directory ->
                    directory in renderingAssetDirectories
                },
            )
        }
    }

    /**
     * Reads the watched tree on a worker thread and hands the result back on the
     * main thread, then schedules the next poll.
     *
     * The result is dropped when the observers have been stopped in the
     * meantime, so a scan already in flight cannot restart polling behind a
     * stopped activity.
     */
    private fun scanConfigurationTree(
        configuration: HomeConfiguration?,
        onScanned: (Map<String, String>) -> Unit,
    ) {
        configurationPollExecutor.execute {
            val fingerprints = currentConfigurationFingerprints(configuration)
            mainHandler.post {
                if (homeConfigFileObservers.isEmpty()) return@post
                onScanned(fingerprints)
                mainHandler.removeCallbacks(pollConfigurationTreeRunnable)
                mainHandler.postDelayed(
                    pollConfigurationTreeRunnable,
                    CONFIGURATION_POLL_INTERVAL_MS,
                )
            }
        }
    }

    private fun currentConfigurationFingerprints(
        configuration: HomeConfiguration?,
    ): Map<String, String> {
        val configurationDirectory = homeConfigStore.configurationFile.parentFile?.absolutePath
        val directories = configuration
            ?.let(homeConfigStore::configurationWatchDirectories)
            ?: listOfNotNull(homeConfigStore.configurationFile.parentFile)
        return directories.associate { directory ->
            val ignoredNames = if (directory.absolutePath == configurationDirectory) {
                setOf(
                    DikcizRemoteAccessAuth.CONTROL_DIRECTORY_NAME,
                    homeConfigStore.logDirectory.name,
                )
            } else {
                emptySet()
            }
            directory.absolutePath to directoryFingerprint(directory, ignoredNames)
        }
    }

    private fun directoryFingerprint(directory: File, ignoredNames: Set<String>): String {
        return directory.listFiles().orEmpty()
            .filterNot { file -> file.name in ignoredNames }
            .sortedBy(File::getName)
            .joinToString(FINGERPRINT_ENTRY_SEPARATOR) { file ->
                "${file.name}:${file.length()}:${file.lastModified()}"
            }
    }

    private fun scheduleConfigurationReload(isRenderingAssetChange: Boolean = false) {
        mainHandler.post {
            hasPendingRenderingAssetChange = hasPendingRenderingAssetChange || isRenderingAssetChange
            dikcizLogger.debug(EVENT_CONFIGURATION_FILE_CHANGED)
            mainHandler.removeCallbacks(reloadConfigurationRunnable)
            mainHandler.postDelayed(reloadConfigurationRunnable, CONFIGURATION_RELOAD_DEBOUNCE_MS)
        }
    }

    private fun reloadConfiguration(showFatalError: Boolean) {
        dikcizLogger.debug(
            EVENT_CONFIGURATION_RELOAD_STARTED,
            mapOf(FIELD_FATAL_ON_FAILURE to showFatalError),
        )
        try {
            val shouldRefreshRenderingAssets = hasPendingRenderingAssetChange
            hasPendingRenderingAssetChange = false
            val configuration = configurationForCurrentMode()
            isConfigurationErrorVisible = false
            val previousConfiguration = homeConfiguration
            val hasSameConfiguration = previousConfiguration != null &&
                homeConfigStore.hasSameSerializedConfiguration(configuration, previousConfiguration)
            if (hasSameConfiguration) {
                if (shouldRefreshRenderingAssets) {
                    refreshRenderingAssets()
                    return
                }
                dikcizLogger.debug(EVENT_CONFIGURATION_RELOAD_UNCHANGED)
                refreshRenderedScriptDashboardStatuses(configuration)
                return
            }
            val hasSameStaticConfiguration = previousConfiguration != null &&
                homeConfigStore.hasSameStaticConfiguration(configuration, previousConfiguration)
            dikcizLogger.configure(configuration.logging)
            if (hasSameStaticConfiguration) {
                val previous = checkNotNull(previousConfiguration)
                homeConfiguration = configuration
                deliverHtmlWidgetStateUpdates(previous, configuration)
                refreshRenderedScriptDashboardStatuses(configuration)
                if (shouldRefreshRenderingAssets) {
                    refreshRenderingAssets()
                    return
                }
                dikcizLogger.debug(EVENT_CONFIGURATION_RUNTIME_STATE_APPLIED)
                return
            }
            previousConfiguration?.let { previous ->
                cleanupRemovedProviderWidgets(previous, configuration)
            }
            homeConfiguration = configuration
            syncAutomationService(configuration)
            if (homeConfigFileObservers.isNotEmpty()) {
                replaceConfigurationObservers(configuration)
            }
            dikcizLogger.info(
                EVENT_CONFIGURATION_RELOAD_COMPLETED,
                mapOf(
                    FIELD_PAGE_COUNT to configuration.pages.size,
                    FIELD_SELECTED_PAGE_INDEX to configuration.pages.indexOfFirst {
                        it.id == configuration.selectedPageID
                    },
                ),
            )
            renderConfiguration()
        } catch (exception: HomeConfigException) {
            dikcizLogger.warn(
                EVENT_CONFIGURATION_RELOAD_REJECTED,
                mapOf(
                    FIELD_ERROR_CLASS to exception::class.java.simpleName,
                    FIELD_ERROR_SUMMARY to exception.message.orEmpty(),
                    FIELD_FATAL_ON_FAILURE to showFatalError,
                ),
            )
            if (homeConfiguration == null || showFatalError) {
                showConfigurationError(exception)
                return
            }
            Toast.makeText(this, exception.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshRenderingAssets() {
        val configuration = homeConfiguration ?: return
        renderConfiguration()
        dikcizLogger.info(
            EVENT_CONFIGURATION_ASSETS_RELOADED,
            mapOf(
                FIELD_SELECTED_PAGE_ID to configuration.selectedPageID,
                FIELD_THEME_ID to configuration.selectedThemeID.orEmpty(),
            ),
        )
    }

    private fun configurationForCurrentMode(): HomeConfiguration {
        val configuration = if (isSafeMode) {
            safeModeConfiguration(homeConfigStore.loadBundledConfiguration())
        } else {
            homeConfigStore.loadOrCreate()
        }
        if (!hasPendingDefaultHomePageSelection) {
            return configuration
        }
        hasPendingDefaultHomePageSelection = false
        if (configuration.selectedPageID == configuration.homePageID) {
            return configuration
        }
        val homeSelection = configuration.copy(selectedPageID = configuration.homePageID)
        persistDefaultHomePageSelection(homeSelection)
        return homeSelection
    }

    /**
     * Records the Home-intent page selection in the public manifest.
     *
     * The selection would otherwise live only in memory, so the next process
     * start would reload the previously selected page and reopen there. Safe
     * mode never writes, and a failed write still leaves the home page
     * rendered for this start.
     */
    private fun persistDefaultHomePageSelection(configuration: HomeConfiguration) {
        if (isSafeMode) {
            return
        }
        try {
            homeConfigStore.saveSelectedPage(configuration)
        } catch (exception: HomeConfigException) {
            dikcizLogger.warn(
                EVENT_CONFIGURATION_SAVE_FAILED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
        }
    }

    private fun isLauncherHomeIntent(): Boolean {
        return intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)
    }

    private fun safeModeConfiguration(configuration: HomeConfiguration): HomeConfiguration {
        val homePage = configuration.pages.first { page -> page.id == configuration.homePageID }
        return configuration.copy(selectedPageID = homePage.id, pages = listOf(homePage))
    }

    private fun renderConfiguration() {
        val configuration = homeConfiguration ?: return
        val selectedPage = configuration.selectedPage() ?: return
        dikcizStyleRenderer.clearTypefaceCache()
        activeTheme = resolveActiveTheme(configuration)
        activeDialog?.dismiss()
        activeDialog = null
        dismissWidgetEditActions()
        pendingHtmlContentFits.clear()
        activeWidgetResize = null
        activeWidgetResizeGesture = null
        activeWidgetMove = null
        widgetContainer.isGridGuideVisible = false
        clearPendingWidgetMove()
        clearCachedPageCanvases()
        val selectedPageIndex = configuration.pages.indexOf(selectedPage)
        dikcizLogger.debug(
            EVENT_HOME_RENDERED,
            mapOf(
                FIELD_PAGE_COUNT to configuration.pages.size,
                FIELD_SELECTED_PAGE_INDEX to selectedPageIndex,
                FIELD_WIDGET_COUNT to selectedPage.widgets.size,
            ),
        )
        automationViews.clear()
        providerAutomationViews.clear()
        configureSafeModeControls()
        configurePageIndicators(configuration, selectedPage)
        applyLauncherBackground(
            configuration.launcherBackground
                ?: activeTheme?.launcherBackground
                ?: DikcizLauncherBackground(),
        )
        registerAutomationView(SEMANTIC_ID_HORIZONTAL_PAGE_INDICATOR, horizontalPageIndicator)
        registerAutomationView(SEMANTIC_ID_VERTICAL_PAGE_INDICATOR, verticalPageIndicator)
        registerAutomationView(SEMANTIC_ID_PAGE_SCROLL, pageScrollView)
        showCachedPage(configuration, selectedPage)
        scheduleNeighborPageCache(configuration, selectedPage)
    }

    private fun showCachedPage(
        configuration: HomeConfiguration,
        selectedPage: HomePage,
    ) {
        activeDialog?.dismiss()
        activeDialog = null
        dismissWidgetEditActions()
        pendingHtmlContentFits.clear()
        activeWidgetResize = null
        activeWidgetResizeGesture = null
        activeWidgetMove = null
        widgetContainer.isGridGuideVisible = false
        clearPendingWidgetMove()
        val pageCanvas = cachedPageCanvases[selectedPage.id]
            ?: createCachedPageCanvas(configuration, selectedPage)
        cachedPageCanvases.values.forEach { canvas ->
            canvas.visibility = if (canvas === pageCanvas) View.VISIBLE else View.INVISIBLE
        }
        widgetContainer = pageCanvas
        providerAutomationViews.clear()
        cachedPageAutomationViews.values.forEach { views ->
            views.keys.forEach(automationViews::remove)
        }
        cachedPageAutomationViews[selectedPage.id]?.let(automationViews::putAll)
        refreshCachedHtmlWidgets(selectedPage, pageCanvas)
        configurePageIndicators(configuration, selectedPage)
        registerAutomationView(SEMANTIC_ID_PAGE_CANVAS, widgetContainer)
        setEditingControlsEnabled(!isSafeMode)
        pageScrollView.scrollTo(SCROLL_X_ORIGIN, SCROLL_Y_ORIGIN)
        configureLauncherControl()
        publishAutomationUIRendered()
    }

    private fun createCachedPageCanvas(
        configuration: HomeConfiguration,
        page: HomePage,
    ): DikcizPageGridLayout {
        val pageCanvas = DikcizPageGridLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            grid = configuration.nativeGrid
            visibility = View.INVISIBLE
        }
        configurePageCanvas(pageCanvas)
        val pageViews = mutableMapOf<String, View>()
        pageAutomationViewCapture = pageViews
        pageBeingBuilt = page
        try {
            if (page.widgets.isEmpty()) {
                pageCanvas.addView(createEmptyPageView(page))
            } else {
                page.widgetsInPageOrder().forEach { widget ->
                    pageCanvas.addView(
                        createWidgetView(
                            widget = widget,
                            isPageLocked = page.locked,
                        ),
                    )
                }
            }
            pageCanvas.setOnLongClickListener {
                if (page.locked || isSafeMode) {
                    return@setOnLongClickListener false
                }
                showPageMenu(page)
                true
            }
        } finally {
            pageBeingBuilt = null
            pageAutomationViewCapture = null
        }
        pageCanvasHost.addView(pageCanvas)
        cachedPageCanvases[page.id] = pageCanvas
        cachedPageAutomationViews[page.id] = pageViews
        return pageCanvas
    }

    private fun clearCachedPageCanvases() {
        pageCanvasHost.removeAllViews()
        cachedPageCanvases.clear()
        cachedPageAutomationViews.clear()
    }

    private fun scheduleNeighborPageCache(
        configuration: HomeConfiguration,
        selectedPage: HomePage,
    ) {
        mainHandler.post {
            val currentConfiguration = homeConfiguration ?: return@post
            if (currentConfiguration.selectedPageID != selectedPage.id) {
                return@post
            }
            val neighbors = pageSelectionNeighbors(currentConfiguration, selectedPage)
            val protectedPageIDs = (neighbors.map(HomePage::id) + selectedPage.id).toSet()
            neighbors.forEach { page ->
                if (cachedPageCanvases.containsKey(page.id)) {
                    return@forEach
                }
                trimCachedPageCanvases(protectedPageIDs)
                createCachedPageCanvas(currentConfiguration, page)
            }
        }
    }

    private fun trimCachedPageCanvases(protectedPageIDs: Set<String>) {
        while (cachedPageCanvases.size >= PAGE_CACHE_MAXIMUM_COUNT) {
            val pageID = cachedPageCanvases.keys.firstOrNull { pageID ->
                pageID !in protectedPageIDs
            } ?: return
            cachedPageAutomationViews.remove(pageID)
            cachedPageCanvases.remove(pageID)?.let(pageCanvasHost::removeView)
        }
    }

    private fun pageSelectionNeighbors(
        configuration: HomeConfiguration,
        selectedPage: HomePage,
    ): List<HomePage> {
        val columns = configuration.pagesByColumn()
        val selectedColumnIndex = configuration.columnIndex(selectedPage)
        val rows = configuration.pagesInColumn(selectedPage.position.column)
        val selectedRowIndex = configuration.rowIndex(selectedPage)
        return listOfNotNull(
            columns.getOrNull(selectedColumnIndex - PAGE_CACHE_NEIGHBOR_OFFSET)
                ?.minByOrNull { page -> abs(page.position.row - selectedPage.position.row) },
            columns.getOrNull(selectedColumnIndex + PAGE_CACHE_NEIGHBOR_OFFSET)
                ?.minByOrNull { page -> abs(page.position.row - selectedPage.position.row) },
            rows.getOrNull(selectedRowIndex - PAGE_CACHE_NEIGHBOR_OFFSET),
            rows.getOrNull(selectedRowIndex + PAGE_CACHE_NEIGHBOR_OFFSET),
        )
            .filter { page -> page.id != selectedPage.id }
            .distinctBy(HomePage::id)
    }

    private fun refreshCachedHtmlWidgets(
        page: HomePage,
        pageCanvas: DikcizPageGridLayout,
    ) {
        val widgets = page.widgets
            .filterIsInstance<HtmlHomeWidget>()
            .associateBy(HtmlHomeWidget::id)
        pageCanvas.collectHtmlWidgetViews().forEach { widgetView ->
            val widget = widgets[widgetView.widgetID()] ?: return@forEach
            widgetView.deliverState(widget.state)
            if (widget.heightMode == HtmlWidgetHeightMode.Content) {
                widgetView.requestContentHeight()
            }
        }
    }

    private fun applyLauncherBackground(background: DikcizLauncherBackground) {
        configurePageCanvas(widgetContainer)
        launcherContent.setBackgroundColor(Color.parseColor(background.color))
        val wallpaper = background.wallpaper ?: run {
            launcherWallpaper.setImageDrawable(null)
            launcherWallpaper.visibility = View.GONE
            return
        }
        val wallpaperFile = File(homeConfigStore.wallpaperDirectory, wallpaper)
        val bitmap = loadLauncherWallpaper(wallpaperFile)
        if (bitmap == null) {
            launcherWallpaper.setImageDrawable(null)
            launcherWallpaper.visibility = View.GONE
            dikcizLogger.warn(
                EVENT_LAUNCHER_WALLPAPER_FALLBACK,
                mapOf(FIELD_REASON to REASON_WALLPAPER_UNAVAILABLE),
            )
            return
        }
        launcherContent.setBackgroundColor(Color.TRANSPARENT)
        launcherWallpaper.setImageBitmap(bitmap)
        launcherWallpaper.visibility = View.VISIBLE
        dikcizLogger.debug(EVENT_LAUNCHER_BACKGROUND_RENDERED)
    }

    private fun configurePageCanvas(pageCanvas: DikcizPageGridLayout) {
        pageCanvas.background = null
        val pageEdgeInsetPixels = densityPixels(PAGE_CONTENT_EDGE_INSET_DP)
        pageCanvas.setPadding(
            pageEdgeInsetPixels,
            pageEdgeInsetPixels,
            pageEdgeInsetPixels,
            pageEdgeInsetPixels,
        )
        val pageLayoutParameters = pageCanvas.layoutParams as ViewGroup.MarginLayoutParams
        pageLayoutParameters.setMargins(NO_PADDING, NO_PADDING, NO_PADDING, NO_PADDING)
        pageCanvas.layoutParams = pageLayoutParameters
    }

    private fun loadLauncherWallpaper(file: File): Bitmap? {
        if (!file.isFile) {
            return null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (
            bounds.outWidth <= NO_DIMENSION ||
            bounds.outHeight <= NO_DIMENSION ||
            maxOf(bounds.outWidth, bounds.outHeight) > MAXIMUM_WALLPAPER_DIMENSION_PIXELS
        ) {
            return null
        }
        val sampleSize = generateWallpaperSampleSize(bounds.outWidth, bounds.outHeight)
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        )
    }

    private fun generateWallpaperSampleSize(width: Int, height: Int): Int {
        var sampleSize = INITIAL_WALLPAPER_SAMPLE_SIZE
        while (maxOf(width / sampleSize, height / sampleSize) > MAXIMUM_WALLPAPER_DECODE_DIMENSION_PIXELS) {
            sampleSize *= WALLPAPER_SAMPLE_SIZE_MULTIPLIER
        }
        return sampleSize
    }

    private fun configureSafeModeControls() {
        if (!isSafeMode) {
            safeModeControls.visibility = View.GONE
            return
        }
        safeModeControls.visibility = View.VISIBLE
        safeModeResetButton.setOnClickListener { showResetConfirmation() }
        safeModeExitButton.setOnClickListener { restartInMode(false) }
        registerAutomationView(SEMANTIC_SAFE_MODE_RESET, safeModeResetButton)
        registerAutomationView(SEMANTIC_SAFE_MODE_EXIT, safeModeExitButton)
        dikcizLogger.info(EVENT_SAFE_MODE_RENDERED)
    }

    private fun createEmptyPageView(page: HomePage): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = DikcizPageGridLayout.LayoutParams(fullPageCell())
            gravity = Gravity.CENTER
            minimumHeight = densityPixels(EMPTY_PAGE_MINIMUM_HEIGHT_DP)
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), standardPadding(), standardPadding(), standardPadding())
            contentDescription = getString(R.string.dikciz_empty_page)
            isClickable = true
            setOnLongClickListener {
                if (page.locked || isSafeMode) {
                    return@setOnLongClickListener false
                }
                showPageMenu(page)
                true
            }
            addView(TextView(this@DikcizHomeActivity).apply {
                text = getString(R.string.dikciz_empty_page)
                setTextColor(getColor(R.color.dikciz_muted_foreground))
                gravity = Gravity.CENTER
            })
        }
    }

    private fun createWidgetView(
        widget: HomeWidget,
        isPageLocked: Boolean,
    ): FrameLayout {
        val isEnabled = widget.enabled
        val isEditingLocked = isSafeMode || isPageLocked || widget.locked
        val hasMoveGestureZone = !isEditingLocked
        val isContentOwnedSurface = widget is HtmlHomeWidget ||
            widget is AppHomeWidget ||
            widget is AppGroupHomeWidget ||
            widget is ProviderHomeWidget
        val style = selectedWidgetStyle(widget)
        val frameStyle = dikcizStyleRenderer.resolveWidgetFrameStyle(
            style = style,
            isEnabled = isEnabled,
            defaultCornerRadiusPixels = densityPixels(WIDGET_CORNER_RADIUS_DP).toFloat(),
        )
        return DikcizWidgetFrameLayout(this).apply {
            val widgetFrame = this
            layoutParams = widgetLayoutParams(widget).apply {
                if (!isContentOwnedSurface) {
                    dikcizStyleRenderer.applyMargins(this, frameStyle)
                }
            }
            alpha = if (isEnabled) ENABLED_WIDGET_ALPHA else DISABLED_WIDGET_ALPHA
            background = if (isContentOwnedSurface) null else dikcizStyleRenderer.createBackground(frameStyle)
            contentDescription = widget.title
            resizeTouchCapture = { event ->
                !isEditingLocked && shouldCaptureWidgetResizeTouch(this, event)
            }
            setOnLongClickListener {
                if (isEditingLocked) {
                    return@setOnLongClickListener false
                }
                showWidgetEditActions(widget, this)
                true
            }
            configureWidgetTapAction(widget, isEditingLocked)
            setOnTouchListener { view, event ->
                val hadResizeGesture = activeWidgetResizeGesture != null
                val handled = handleWidgetResizeTouch(this, event)
                if (
                    handled &&
                    event.actionMasked == MotionEvent.ACTION_UP &&
                    !hadResizeGesture
                ) {
                    view.performClick()
                }
                handled
            }
            val content = LinearLayout(this@DikcizHomeActivity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    if (isContentOwnedSurface) {
                        FrameLayout.LayoutParams.MATCH_PARENT
                    } else {
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    },
                )
                orientation = LinearLayout.VERTICAL
                if (widget is AppHomeWidget || widget is AppGroupHomeWidget) {
                    gravity = Gravity.CENTER
                }
                setPadding(
                    if (isContentOwnedSurface) NO_PADDING else frameStyle.padding.left,
                    if (isContentOwnedSurface) NO_PADDING else frameStyle.padding.top,
                    if (isContentOwnedSurface) NO_PADDING else frameStyle.padding.right,
                    if (isContentOwnedSurface) NO_PADDING else frameStyle.padding.bottom,
                )
                when (widget) {
                    is HtmlHomeWidget -> {
                        if (isEnabled) {
                            addHtmlWidgetContent(this, widget, widgetFrame)
                        }
                    }
                    is AppHomeWidget -> addAppWidgetContent(this, widget, style, isEnabled)
                    is AppGroupHomeWidget -> addAppGroupWidgetContent(this, widget, style, isEnabled)
                    is ProviderHomeWidget -> addProviderWidgetContent(this, widget, style, isEnabled)
                    is ScriptDashboardHomeWidget -> addScriptDashboardWidgetContent(this, widget, style)
                }
                if (!isEnabled) {
                    addView(createDisabledWidgetView())
                }
            }
            addView(content)
            if (hasMoveGestureZone) {
                // An icon tile has no live content, so the whole tile drags and a
                // still press opens its menu. A widget that owns its content keeps
                // the narrow top edge, because its body needs the touches itself.
                val iconTileTap: (() -> Unit)? = iconTileTapAction(widget, isEnabled)
                addView(
                    createWidgetMoveGestureZone(
                        widget = widget,
                        widgetView = this,
                        isFullTile = iconTileTap != null,
                        onTap = iconTileTap,
                    ),
                )
                addView(createWidgetWidthFillButton(widget, this))
                addView(createWidgetHeightFillButton(widget, this))
            }
        }.also { view -> registerAutomationView(widgetSemanticID(widget.id), view) }
    }

    private fun selectedWidgetStyle(widget: HomeWidget): DikcizStyle {
        val configuration = homeConfiguration ?: return widget.style ?: DikcizStyle()
        return (activeTheme?.widgetStyle ?: DikcizStyle())
            .mergedWith(configuration.styleDefaults.widget)
            .mergedWith(widget.style)
    }

    private fun resolveActiveTheme(configuration: HomeConfiguration): DikcizTheme? {
        val themeID = configuration.selectedThemeID ?: return null
        return try {
            dikcizThemeStore.loadCatalog().themes.firstOrNull { theme -> theme.id == themeID }
        } catch (exception: HomeConfigException) {
            dikcizLogger.warn(
                EVENT_THEME_CATALOGUE_LOAD_FAILED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            null
        }
    }

    private fun View.configureWidgetTapAction(
        widget: HomeWidget,
        isEditingLocked: Boolean,
    ) {
        if (isEditingLocked) {
            return
        }
        when (widget) {
            is HtmlHomeWidget -> Unit
            is ScriptDashboardHomeWidget -> Unit
            is AppHomeWidget,
            is AppGroupHomeWidget,
            is ProviderHomeWidget,
            -> Unit
        }
    }

    /**
     * The drag target that starts a move.
     *
     * An icon tile has no live content of its own, so the whole tile is the target and a
     * drag can begin anywhere on it. A widget that owns its content, an HTML document, a
     * provider view, or the script dashboard, keeps the narrow top edge, because covering
     * its body would swallow the touches that body exists to receive.
     *
     * [onTap] runs for a press that neither dragged nor became a long press, which is how
     * a full-tile zone still launches the app underneath it.
     */
    /**
     * What a tap on an icon tile does, or null when the tile is not an icon tile.
     *
     * A non-null action also makes the whole tile the drag zone, so the two stay
     * in step: every tile that can be dragged from anywhere can also be tapped
     * from anywhere. The action runs the launch itself rather than forwarding a
     * click to the view underneath, which the covering zone would swallow.
     */
    private fun iconTileTapAction(widget: HomeWidget, isEnabled: Boolean): (() -> Unit)? {
        if (!isEnabled) {
            return null
        }
        return when (widget) {
            is AppHomeWidget -> {
                val component = ComponentName.unflattenFromString(widget.component)
                    ?: return null
                if (!resolvesActivity(component)) {
                    return null
                }
                { launchExplicitComponent(component) }
            }

            is AppGroupHomeWidget -> {
                { showAppGroupContents(widget) }
            }

            else -> null
        }
    }

    private fun createWidgetMoveGestureZone(
        widget: HomeWidget,
        widgetView: View,
        isFullTile: Boolean,
        onTap: (() -> Unit)?,
    ): View {
        return View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                if (isFullTile) {
                    FrameLayout.LayoutParams.MATCH_PARENT
                } else {
                    densityPixels(WIDGET_TOP_EDGE_MOVE_GESTURE_HEIGHT_DP)
                },
                Gravity.TOP or Gravity.START,
            )
            contentDescription = getString(
                R.string.dikciz_widget_move_handle_description,
                widget.title,
            )
            isClickable = true
            isLongClickable = true
            setOnLongClickListener {
                showWidgetEditActions(widget, widgetView)
                true
            }
            if (onTap != null) {
                setOnClickListener { onTap() }
            }
            setOnTouchListener { _, event ->
                handleWidgetMoveTouch(widget, widgetView, event, isTopLevel = true, onTap = onTap)
            }
            registerAutomationView(moveHandleSemanticID(widget.id), this)
        }
    }

    private fun createWidgetWidthFillButton(widget: HomeWidget, widgetView: View): Button {
        return Button(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                densityPixels(WIDGET_RESIZE_FILL_CONTROL_SIZE_DP),
                densityPixels(WIDGET_RESIZE_FILL_CONTROL_SIZE_DP),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply {
                bottomMargin = densityPixels(WIDGET_RESIZE_FILL_CONTROL_MARGIN_DP)
            }
            contentDescription = getString(R.string.dikciz_widget_resize_fill_width, widget.title)
            minHeight = NO_PADDING
            minWidth = NO_PADDING
            setPadding(NO_PADDING, NO_PADDING, NO_PADDING, NO_PADDING)
            tag = WIDGET_RESIZE_WIDTH_FILL_TAG
            text = getString(R.string.dikciz_widget_resize_fill_width_symbol)
            visibility = View.GONE
            setOnClickListener { fillWidgetResize(widget.id, widgetView, WidgetResizeFillAxis.Width) }
        }
    }

    private fun createWidgetHeightFillButton(widget: HomeWidget, widgetView: View): Button {
        return Button(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                densityPixels(WIDGET_RESIZE_FILL_CONTROL_SIZE_DP),
                densityPixels(WIDGET_RESIZE_FILL_CONTROL_SIZE_DP),
                Gravity.CENTER_VERTICAL or Gravity.END,
            ).apply {
                rightMargin = densityPixels(WIDGET_RESIZE_HEIGHT_FILL_CONTROL_MARGIN_DP)
            }
            contentDescription = getString(R.string.dikciz_widget_resize_fill_height, widget.title)
            minHeight = NO_PADDING
            minWidth = NO_PADDING
            setPadding(NO_PADDING, NO_PADDING, NO_PADDING, NO_PADDING)
            tag = WIDGET_RESIZE_HEIGHT_FILL_TAG
            text = getString(R.string.dikciz_widget_resize_fill_height_symbol)
            visibility = View.GONE
            setOnClickListener { fillWidgetResize(widget.id, widgetView, WidgetResizeFillAxis.Height) }
        }
    }

    private fun widgetLayoutParams(widget: HomeWidget): ViewGroup.MarginLayoutParams {
        return DikcizPageGridLayout.LayoutParams(widget.cell)
    }

    private fun showWidgetEditActions(widget: HomeWidget, widgetView: View) {
        if (selectedPageOrWidgetIsLocked(widget.id)) {
            return
        }
        activeWidgetResize?.let(::finishWidgetResize)
        clearPendingWidgetMove()
        dismissWidgetEditActions()
        val actionsView = LinearLayout(this).apply {
            gravity = Gravity.END
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), standardPadding(), standardPadding(), standardPadding())
            background = createWidgetBackground(isEnabled = true)
            addView(createWidgetEditButtons(widget, widgetView))
        }
        val actionsPopup = PopupWindow(
            actionsView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
        }
        actionsPopup.setOnDismissListener {
            clearDismissedWidgetEdit(actionsPopup, widget.id)
        }
        activeWidgetEdit = ActiveWidgetEdit(widget.id, widgetView, actionsView, actionsPopup)
        val popupPlacement = showWidgetEditActionsPopup(actionsPopup, actionsView, widgetView)
        dikcizLogger.debug(
            EVENT_WIDGET_EDIT_ACTIONS_SHOWN,
            mapOf(
                FIELD_POPUP_PLACEMENT to popupPlacement,
                FIELD_WIDGET_ID to widget.id,
            ),
        )
        publishAutomationUIRendered()
    }

    private fun showWidgetEditActionsPopup(
        actionsPopup: PopupWindow,
        actionsView: View,
        widgetView: View,
    ): String {
        actionsView.measure(
            View.MeasureSpec.makeMeasureSpec(pageScrollView.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(pageScrollView.height, View.MeasureSpec.AT_MOST),
        )
        val pageBounds = Rect()
        val widgetBounds = Rect()
        pageScrollView.getGlobalVisibleRect(pageBounds)
        widgetView.getGlobalVisibleRect(widgetBounds)
        if (widgetBounds.bottom + actionsView.measuredHeight <= pageBounds.bottom) {
            actionsPopup.showAsDropDown(widgetView)
            return POPUP_PLACEMENT_BELOW
        }
        actionsPopup.showAsDropDown(
            widgetView,
            NO_PADDING,
            -widgetView.height - actionsView.measuredHeight,
        )
        return POPUP_PLACEMENT_ABOVE
    }

    private fun createWidgetEditButtons(widget: HomeWidget, widgetView: View): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val appearanceAction = Button(this@DikcizHomeActivity).apply {
                isAllCaps = false
                text = getString(R.string.dikciz_widget_appearance)
                contentDescription = text
                setOnClickListener {
                    dismissWidgetEditActions()
                    showWidgetAppearanceDialog(widget)
                }
                registerAutomationView(appearanceActionSemanticID(widget.id), this)
            }
            addView(createWidgetActionRow(listOf(appearanceAction)))
            val resizeAction = Button(this@DikcizHomeActivity).apply {
                isAllCaps = false
                text = getString(R.string.dikciz_widget_resize)
                contentDescription = getString(R.string.dikciz_widget_resize)
                setOnClickListener { beginWidgetResize(widget, widgetView) }
                registerAutomationView(resizeSemanticID(widget.id), this)
            }
            val editAction = when (widget) {
                is HtmlHomeWidget -> createWidgetEditButton(widget) {
                    homeConfiguration?.selectedPage()?.let { page ->
                        showEditHtmlWidgetDialog(page.id, widget)
                    }
                }
                is AppHomeWidget -> createWidgetEditButton(widget) { showEditAppWidgetDialog(widget) }
                is AppGroupHomeWidget -> createWidgetEditButton(widget) { showEditAppGroupDialog(widget) }
                is ProviderHomeWidget -> createWidgetEditButton(widget) { showEditProviderWidgetDialog(widget) }
                is ScriptDashboardHomeWidget -> null
            }
            addView(createWidgetActionRow(listOfNotNull(editAction, resizeAction)))
            if (widget is HtmlHomeWidget) {
                val fitContentAction = Button(this@DikcizHomeActivity).apply {
                    isAllCaps = false
                    text = getString(R.string.dikciz_html_widget_fit_content)
                    contentDescription = getString(
                        R.string.dikciz_html_widget_fit_content_description,
                        widget.title,
                    )
                    setOnClickListener { requestHtmlWidgetContentFit(widget, widgetView) }
                    registerAutomationView(htmlWidgetFitContentSemanticID(widget.id), this)
                }
                // A block is widget-local authoring. It is reachable only from the widget
                // that will receive it, never from the page-level add flow.
                val addBlockAction = Button(this@DikcizHomeActivity).apply {
                    isAllCaps = false
                    text = getString(R.string.dikciz_html_widget_add_block)
                    contentDescription = getString(
                        R.string.dikciz_html_widget_add_block_description,
                        widget.title,
                    )
                    setOnClickListener {
                        dismissWidgetEditActions()
                        showHtmlBlockPicker(widget)
                    }
                    registerAutomationView(htmlWidgetAddBlockSemanticID(widget.id), this)
                }
                addView(createWidgetActionRow(listOf(fitContentAction, addBlockAction)))
            }
            val moveAction = Button(this@DikcizHomeActivity).apply {
                isAllCaps = false
                text = getString(R.string.dikciz_widget_move)
                contentDescription = getString(R.string.dikciz_widget_move_description, widget.title)
                setOnClickListener { armWidgetMove(widget, widgetView) }
                registerAutomationView(moveSemanticID(widget.id), this)
            }
            addView(createWidgetActionRow(listOf(moveAction)))
            val secondaryActions = listOf(
                Button(this@DikcizHomeActivity).apply {
                    isAllCaps = false
                    text = getString(
                        if (widget.locked) {
                            R.string.dikciz_widget_unlock
                        } else {
                            R.string.dikciz_widget_lock
                        },
                    )
                    setOnClickListener {
                        dismissWidgetEditActions()
                        setWidgetLocked(widget.id, !widget.locked)
                    }
                    registerAutomationView(lockSemanticID(widget.id), this)
                },
                Button(this@DikcizHomeActivity).apply {
                    isAllCaps = false
                    text = getString(R.string.dikciz_widget_delete)
                    contentDescription = getString(R.string.dikciz_widget_delete)
                    setOnClickListener {
                        dismissWidgetEditActions()
                        deleteTopLevelWidget(widget.id)
                    }
                    registerAutomationView(deleteSemanticID(widget.id), this)
                },
            )
            addView(createWidgetActionRow(secondaryActions))
        }
    }

    private fun createWidgetActionRow(actionViews: List<View>): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            gravity = Gravity.CENTER_HORIZONTAL
            orientation = LinearLayout.HORIZONTAL
            actionViews.forEach { actionView ->
                addView(
                    actionView,
                    LinearLayout.LayoutParams(
                        NO_PADDING,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        WIDGET_EDIT_ACTION_WEIGHT,
                    ),
                )
            }
        }
    }

    private fun createWidgetEditButton(widget: HomeWidget, onEdit: () -> Unit): Button {
        return Button(this).apply {
            isAllCaps = false
            text = getString(R.string.dikciz_widget_edit)
            contentDescription = getString(R.string.dikciz_widget_edit)
            setOnClickListener {
                dismissWidgetEditActions()
                onEdit()
            }
            registerAutomationView(editSemanticID(widget.id), this)
        }
    }

    private fun dismissWidgetEditWhenTouchedOutside(event: MotionEvent) {
        val activeEdit = activeWidgetEdit ?: return
        if (isTouchInsideView(event, activeEdit.widgetView)) {
            return
        }
        dismissWidgetEditActions()
    }

    private fun dismissWidgetEditActions() {
        val activeEdit = activeWidgetEdit ?: return
        activeWidgetEdit = null
        clearWidgetEditAutomationViews(activeEdit.widgetID)
        activeEdit.actionsPopup.dismiss()
        publishAutomationUIRendered()
    }

    private fun clearDismissedWidgetEdit(actionsPopup: PopupWindow, widgetID: String) {
        if (activeWidgetEdit?.actionsPopup !== actionsPopup) {
            return
        }
        activeWidgetEdit = null
        clearWidgetEditAutomationViews(widgetID)
        publishAutomationUIRendered()
    }

    /**
     * Forgets every control the widget action sheet registered.
     *
     * A control left behind keeps answering a snapshot after its sheet is gone,
     * so a remote client sees a button that is no longer on screen. The two
     * dismissal paths share this list because keeping two copies is what let
     * `move` and `addBlock` be forgotten from one of them.
     */
    private fun clearWidgetEditAutomationViews(widgetID: String) {
        listOf(
            moveSemanticID(widgetID),
            resizeSemanticID(widgetID),
            htmlWidgetFitContentSemanticID(widgetID),
            htmlWidgetAddBlockSemanticID(widgetID),
            appearanceActionSemanticID(widgetID),
            editSemanticID(widgetID),
            deleteSemanticID(widgetID),
            lockSemanticID(widgetID),
        ).forEach(automationViews::remove)
    }

    private fun beginWidgetResize(
        widget: HomeWidget,
        widgetView: View,
        resizeHandles: Set<WidgetResizeHandle> = WidgetResizeHandle.entries.toSet(),
    ) {
        if (selectedPageOrWidgetIsLocked(widget.id)) {
            return
        }
        activeWidgetResize?.let { activeResize ->
            if (activeResize.widgetID != widget.id) {
                finishWidgetResize(activeResize)
            }
        }
        if (activeWidgetResize?.widgetID == widget.id) {
            return
        }
        dismissWidgetEditActions()
        activeWidgetResize = ActiveWidgetResize(
            widgetID = widget.id,
            widgetView = widgetView,
            isWidgetEnabled = widget.enabled,
            resizeHandles = resizeHandles,
            initialCell = widget.cell,
            targetCell = widget.cell,
        )
        widgetView.elevation = densityPixels(WIDGET_RESIZE_ELEVATION_DP).toFloat()
        widgetContainer.isGridGuideVisible = true
        setWidgetMoveGestureZoneInteractive(widget.id, false)
        setWidgetResizeFillControlsVisible(widget.id, widgetView, true)
        widgetView.background = createWidgetBackground(
            isEnabled = widget.enabled,
            style = selectedWidgetStyle(widget),
        )
        (widgetView as? DikcizWidgetFrameLayout)?.resizeOverlay = createWidgetResizeOverlay(
            widget = widget,
            resizeHandles = resizeHandles,
        )
        dikcizLogger.debug(
            EVENT_WIDGET_RESIZE_STARTED,
            mapOf(FIELD_WIDGET_ID to widget.id),
        )
        publishAutomationUIRendered()
    }

    private fun handleWidgetResizeTouch(widgetView: View, event: MotionEvent): Boolean {
        val activeResize = activeWidgetResize ?: return false
        if (activeResize.widgetView !== widgetView) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val handle = resizeHandleAt(
                    widgetView,
                    event.x,
                    event.y,
                    activeResize.resizeHandles,
                ) ?: return false
                setWidgetTouchInterceptionBlocked(widgetView, true)
                beginWidgetResizeGesture(handle, event)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeWidgetResizeGesture == null) {
                    return false
                }
                updateWidgetResize(event)
                return true
            }

            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP,
            -> {
                val hadActiveGesture = activeWidgetResizeGesture != null
                activeWidgetResizeGesture = null
                setWidgetTouchInterceptionBlocked(widgetView, false)
                return hadActiveGesture
            }
        }
        return false
    }

    private fun shouldCaptureWidgetResizeTouch(widgetView: View, event: MotionEvent): Boolean {
        val activeResize = activeWidgetResize ?: return false
        if (activeResize.widgetView !== widgetView) {
            return false
        }
        if (activeWidgetResizeGesture != null) {
            return true
        }
        if (isTouchInsideWidgetResizeFillControl(widgetView, event)) {
            return false
        }
        return event.actionMasked == MotionEvent.ACTION_DOWN &&
            resizeHandleAt(widgetView, event.x, event.y, activeResize.resizeHandles) != null
    }

    private fun routeWidgetResizeTouch(event: MotionEvent): Boolean {
        val activeResize = activeWidgetResize ?: return false
        if (activeWidgetResizeGesture != null) {
            return handleWidgetResizeTouch(activeResize.widgetView, event)
        }
        if (event.actionMasked != MotionEvent.ACTION_DOWN) {
            return false
        }
        if (!isTouchInsideView(event, activeResize.widgetView)) {
            return false
        }
        if (isTouchInsideWidgetResizeFillControl(event, activeResize.widgetView)) {
            return false
        }
        val handle = resizeHandleAtScreen(
            activeResize.widgetView,
            event,
            activeResize.resizeHandles,
        ) ?: return false
        setWidgetTouchInterceptionBlocked(activeResize.widgetView, true)
        beginWidgetResizeGesture(handle, event)
        return true
    }

    private fun isTouchInsideWidgetResizeFillControl(event: MotionEvent, widgetView: View): Boolean {
        return WidgetResizeFillAxis.entries.any { axis ->
            val control = widgetView.findViewWithTag<Button>(axis.viewTag) ?: return@any false
            control.visibility == View.VISIBLE && isTouchInsideView(event, control)
        }
    }

    private fun isTouchInsideWidgetResizeFillControl(widgetView: View, event: MotionEvent): Boolean {
        return WidgetResizeFillAxis.entries.any { axis ->
            val control = widgetView.findViewWithTag<Button>(axis.viewTag) ?: return@any false
            control.visibility == View.VISIBLE &&
                event.x >= control.left && event.x <= control.right &&
                event.y >= control.top && event.y <= control.bottom
        }
    }

    private fun resizeHandleAtScreen(
        widgetView: View,
        event: MotionEvent,
        resizeHandles: Set<WidgetResizeHandle>,
    ): WidgetResizeHandle? {
        val viewLocation = IntArray(2)
        widgetView.getLocationOnScreen(viewLocation)
        return resizeHandleAt(
            widgetView,
            event.rawX - viewLocation[0],
            event.rawY - viewLocation[1],
            resizeHandles,
        )
    }

    private fun handleWidgetMoveTouch(
        widget: HomeWidget,
        widgetView: View,
        event: MotionEvent,
        isTopLevel: Boolean,
        onTap: (() -> Unit)? = null,
    ): Boolean {
        if (!isTopLevel || activeWidgetEdit != null || activeWidgetResize != null) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                setWidgetTouchInterceptionBlocked(widgetView, true)
                beginPendingWidgetMove(widget, widgetView, event, onTap)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                activeWidgetMove?.let { activeMove ->
                    if (activeMove.widgetView === widgetView) {
                        updateWidgetMove(activeMove, event)
                        return true
                    }
                }
                val pendingMove = pendingWidgetMove ?: return false
                if (pendingMove.widgetView !== widgetView) {
                    return false
                }
                val deltaX = event.rawX - pendingMove.initialRawX
                val deltaY = event.rawY - pendingMove.initialRawY
                val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
                if ((deltaX * deltaX) + (deltaY * deltaY) < touchSlop * touchSlop) {
                    return true
                }
                val initialRawX = pendingMove.initialRawX
                val initialRawY = pendingMove.initialRawY
                clearPendingWidgetMove(widgetView)
                widgetView.cancelLongPress()
                if (!startWidgetMove(widget, widgetView, initialRawX, initialRawY)) {
                    return false
                }
                activeWidgetMove?.let { updateWidgetMove(it, event) }
                return true
            }

            MotionEvent.ACTION_UP -> {
                setWidgetTouchInterceptionBlocked(widgetView, false)
                activeWidgetMove?.let { activeMove ->
                    if (activeMove.widgetView === widgetView) {
                        finishWidgetMove(activeMove)
                        return true
                    }
                }
                val pendingTap = pendingWidgetMove
                if (pendingTap?.widgetView === widgetView) {
                    clearPendingWidgetMove(widgetView)
                    pendingTap.onTap?.invoke()
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                setWidgetTouchInterceptionBlocked(widgetView, false)
                activeWidgetMove?.let { activeMove ->
                    if (activeMove.widgetView === widgetView) {
                        cancelWidgetMove(activeMove)
                        return true
                    }
                }
                if (pendingWidgetMove?.widgetView === widgetView) {
                    clearPendingWidgetMove(widgetView)
                    return true
                }
            }
        }
        return false
    }

    private fun routeWidgetMoveTouch(event: MotionEvent): Boolean {
        val activeMove = activeWidgetMove
        val pendingMove = pendingWidgetMove
        if (activeMove == null && pendingMove == null) {
            return false
        }
        val widgetID = activeMove?.widgetID ?: pendingMove?.widget?.id ?: return false
        val widget = homeConfiguration?.selectedPage()?.widgets?.firstOrNull { it.id == widgetID }
            ?: return false
        val widgetView = activeMove?.widgetView ?: pendingMove?.widgetView ?: return false
        return handleWidgetMoveTouch(widget, widgetView, event, true)
    }

    private fun beginPendingWidgetMove(
        widget: HomeWidget,
        widgetView: View,
        event: MotionEvent,
        onTap: (() -> Unit)?,
    ) {
        clearPendingWidgetMove()
        val longPressRunnable = Runnable {
            val pendingMove = pendingWidgetMove ?: return@Runnable
            if (pendingMove.widgetView !== widgetView) {
                return@Runnable
            }
            pendingWidgetMove = null
            setWidgetTouchInterceptionBlocked(widgetView, false)
            showWidgetEditActions(widget, widgetView)
        }
        pendingWidgetMove = PendingWidgetMove(
            widget = widget,
            widgetView = widgetView,
            initialRawX = event.rawX,
            initialRawY = event.rawY,
            longPressRunnable = longPressRunnable,
            onTap = onTap,
        )
        mainHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
    }

    private fun clearPendingWidgetMove(widgetView: View? = null) {
        val pendingMove = pendingWidgetMove ?: return
        if (widgetView != null && pendingMove.widgetView !== widgetView) {
            return
        }
        mainHandler.removeCallbacks(pendingMove.longPressRunnable)
        pendingWidgetMove = null
    }

    private fun setWidgetTouchInterceptionBlocked(widgetView: View, isBlocked: Boolean) {
        widgetView.parent?.requestDisallowInterceptTouchEvent(isBlocked)
    }

    private fun resizeHandleAt(
        widgetView: View,
        x: Float,
        y: Float,
        resizeHandles: Set<WidgetResizeHandle>,
    ): WidgetResizeHandle? {
        val touchRadius = densityPixels(WIDGET_RESIZE_TOUCH_RADIUS_DP).toFloat()
        val pointRadius = densityPixels(WIDGET_RESIZE_POINT_RADIUS_DP).toFloat()
        return resizeHandles.minByOrNull { handle ->
            val horizontalDistance = x - handle.horizontalPosition.coordinate(
                widgetView.width,
                pointRadius,
            )
            val verticalDistance = y - handle.verticalPosition.coordinate(
                widgetView.height,
                pointRadius,
            )
            (horizontalDistance * horizontalDistance) + (verticalDistance * verticalDistance)
        }?.takeIf { handle ->
            val horizontalDistance = x - handle.horizontalPosition.coordinate(
                widgetView.width,
                pointRadius,
            )
            val verticalDistance = y - handle.verticalPosition.coordinate(
                widgetView.height,
                pointRadius,
            )
            (horizontalDistance * horizontalDistance) + (verticalDistance * verticalDistance) <= touchRadius * touchRadius
        }
    }

    private fun beginWidgetResizeGesture(handle: WidgetResizeHandle, event: MotionEvent) {
        val activeResize = activeWidgetResize ?: return
        activeWidgetResizeGesture = WidgetResizeGesture(
            handle = handle,
            initialRawX = event.rawX,
            initialRawY = event.rawY,
        )
        dikcizLogger.debug(
            EVENT_WIDGET_RESIZE_HANDLE_SELECTED,
            mapOf(
                FIELD_RESIZE_HANDLE to handle.semanticSuffix,
                FIELD_WIDGET_ID to activeResize.widgetID,
            ),
        )
    }

    /**
     * Snaps the resize to whole cells. The pointer chooses a cell edge, the candidate is
     * validated against the page, and an illegal candidate leaves the widget where it is
     * and turns the outline to the rejection colour.
     */
    private fun updateWidgetResize(event: MotionEvent) {
        val activeResize = activeWidgetResize ?: return
        val gesture = activeWidgetResizeGesture ?: return
        val candidate = resizeCandidateCell(activeResize, gesture, event) ?: return
        if (candidate == activeResize.targetCell) {
            return
        }
        val page = homeConfiguration?.selectedPage() ?: return
        val placement = DikcizGridLayoutEngine.resizeCandidate(
            grid = nativeGrid(),
            occupied = page.occupiedCells(),
            current = activeResize.initialCell,
            target = candidate,
        )
        if (placement is DikcizGridPlacement.Rejected) {
            setWidgetResizeRejected(activeResize, true)
            dikcizLogger.debug(
                EVENT_WIDGET_RESIZE_REJECTED,
                mapOf(
                    FIELD_REASON to placement.failure.persistedValue,
                    FIELD_WIDGET_ID to activeResize.widgetID,
                ),
            )
            return
        }
        setWidgetResizeRejected(activeResize, false)
        activeResize.targetCell = candidate
        activeResize.hasChanged = candidate != activeResize.initialCell
        applyWidgetCell(activeResize.widgetView, candidate)
        dikcizLogger.debug(
            EVENT_WIDGET_RESIZE_POINTER_UPDATED,
            mapOf(
                FIELD_COLUMN_SPAN to candidate.columnSpan,
                FIELD_RESIZE_HANDLE to gesture.handle.semanticSuffix,
                FIELD_ROW_SPAN to candidate.rowSpan,
                FIELD_WIDGET_ID to activeResize.widgetID,
            ),
        )
    }

    private fun resizeCandidateCell(
        activeResize: ActiveWidgetResize,
        gesture: WidgetResizeGesture,
        event: MotionEvent,
    ): DikcizGridRectangle? {
        val containerBounds = Rect()
        if (!widgetContainer.getGlobalVisibleRect(containerBounds)) {
            return null
        }
        val (pointerColumn, pointerRow) = widgetContainer.cellAt(
            (event.rawX - containerBounds.left).roundToInt(),
            (event.rawY - containerBounds.top).roundToInt(),
        )
        val current = activeResize.targetCell
        var column = current.column
        var endColumn = current.endColumn
        when (gesture.handle.horizontalDirection) {
            ResizeDirection.Negative -> column = pointerColumn.coerceIn(
                FIRST_CELL_INDEX,
                endColumn - DikcizGridRectangle.MINIMUM_SPAN,
            )

            ResizeDirection.Positive -> endColumn = (pointerColumn + NEXT_CELL_EDGE).coerceIn(
                column + DikcizGridRectangle.MINIMUM_SPAN,
                nativeGrid().columns,
            )

            ResizeDirection.None -> Unit
        }
        var row = current.row
        var endRow = current.endRow
        when (gesture.handle.verticalDirection) {
            ResizeDirection.Negative -> row = pointerRow.coerceIn(
                FIRST_CELL_INDEX,
                endRow - DikcizGridRectangle.MINIMUM_SPAN,
            )

            ResizeDirection.Positive -> endRow = (pointerRow + NEXT_CELL_EDGE).coerceIn(
                row + DikcizGridRectangle.MINIMUM_SPAN,
                nativeGrid().rows,
            )

            ResizeDirection.None -> Unit
        }
        return DikcizGridRectangle(
            column = column,
            row = row,
            columnSpan = endColumn - column,
            rowSpan = endRow - row,
        )
    }

    private fun setWidgetResizeRejected(activeResize: ActiveWidgetResize, isRejected: Boolean) {
        val overlay = (activeResize.widgetView as? DikcizWidgetFrameLayout)?.resizeOverlay
        (overlay as? WidgetResizeSelectionDrawable)?.isRejected = isRejected
    }

    /** Re-lays the widget onto a cell without persisting anything. */
    private fun applyWidgetCell(widgetView: View, cell: DikcizGridRectangle) {
        val layoutParams = widgetView.layoutParams as? DikcizPageGridLayout.LayoutParams ?: return
        layoutParams.cell = cell
        widgetView.layoutParams = layoutParams
        updateProviderWidgetHostHeight(
            widgetView = widgetView,
            heightPixels = widgetContainer.cellBounds(cell).height,
        )
    }

    private fun nativeGrid(): DikcizNativeGrid {
        return homeConfiguration?.nativeGrid ?: DikcizNativeGrid.BUNDLED_DEFAULT
    }

    private fun finishWidgetResizeWhenTouchedOutside(event: MotionEvent): Boolean {
        val activeResize = activeWidgetResize ?: return false
        if (isTouchInsideView(event, activeResize.widgetView)) {
            return false
        }
        finishWidgetResize(activeResize)
        return true
    }

    private fun isTouchInsideView(event: MotionEvent, view: View): Boolean {
        val bounds = Rect()
        if (!view.getGlobalVisibleRect(bounds)) {
            return false
        }
        return event.rawX >= bounds.left && event.rawX < bounds.right &&
            event.rawY >= bounds.top && event.rawY < bounds.bottom
    }

    private fun finishWidgetResize(activeResize: ActiveWidgetResize) {
        if (activeWidgetResize?.widgetID != activeResize.widgetID) {
            return
        }
        setWidgetTouchInterceptionBlocked(activeResize.widgetView, false)
        activeResize.widgetView.elevation = NO_ELEVATION
        (activeResize.widgetView as? DikcizWidgetFrameLayout)?.resizeOverlay = null
        activeWidgetResize = null
        activeWidgetResizeGesture = null
        widgetContainer.isGridGuideVisible = false
        setWidgetMoveGestureZoneInteractive(activeResize.widgetID, true)
        setWidgetResizeFillControlsVisible(activeResize.widgetID, activeResize.widgetView, false)
        if (!activeResize.hasChanged) {
            activeResize.widgetView.background = createWidgetBackground(
                isEnabled = activeResize.isWidgetEnabled,
                style = selectedWidgetStyleByID(activeResize.widgetID),
            )
            dikcizLogger.debug(
                EVENT_WIDGET_RESIZE_CANCELLED,
                mapOf(FIELD_WIDGET_ID to activeResize.widgetID),
            )
            publishAutomationUIRendered()
            return
        }
        val resizedCell = activeResize.targetCell
        if (!updateSelectedPage { page ->
                page.replaceWidget(activeResize.widgetID) { currentWidget ->
                    currentWidget.withCell(resizedCell)
                }
            }
        ) {
            activeResize.widgetView.background = createWidgetBackground(
                isEnabled = activeResize.isWidgetEnabled,
                style = selectedWidgetStyleByID(activeResize.widgetID),
            )
            dikcizLogger.warn(
                EVENT_WIDGET_RESIZE_SAVE_REJECTED,
                mapOf(FIELD_WIDGET_ID to activeResize.widgetID),
            )
            publishAutomationUIRendered()
            return
        }
        dikcizLogger.info(
            EVENT_WIDGET_RESIZED,
            mapOf(
                FIELD_COLUMN_SPAN to resizedCell.columnSpan,
                FIELD_ROW_SPAN to resizedCell.rowSpan,
                FIELD_WIDGET_ID to activeResize.widgetID,
            ),
        )
        publishAutomationUIRendered()
    }

    private fun setWidgetResizeFillControlsVisible(
        widgetID: String,
        widgetView: View,
        isVisible: Boolean,
    ) {
        WidgetResizeFillAxis.entries.forEach { axis ->
            val control = widgetView.findViewWithTag<Button>(axis.viewTag) ?: return@forEach
            control.visibility = if (isVisible) View.VISIBLE else View.GONE
            val semanticID = resizeFillSemanticID(widgetID, axis)
            if (isVisible) {
                registerAutomationView(semanticID, control)
            } else {
                automationViews.remove(semanticID)
            }
        }
    }

    /** Grows the widget to the full grid extent on one axis, when that stays legal. */
    private fun fillWidgetResize(
        widgetID: String,
        widgetView: View,
        axis: WidgetResizeFillAxis,
    ) {
        val activeResize = activeWidgetResize
        if (
            activeResize?.widgetID != widgetID ||
            activeResize.widgetView !== widgetView
        ) {
            return
        }
        val grid = nativeGrid()
        val current = activeResize.targetCell
        val filled = when (axis) {
            WidgetResizeFillAxis.Width -> current.copy(
                column = FIRST_CELL_INDEX,
                columnSpan = grid.columns,
            )

            WidgetResizeFillAxis.Height -> current.copy(
                row = FIRST_CELL_INDEX,
                rowSpan = grid.rows,
            )
        }
        val page = homeConfiguration?.selectedPage() ?: return
        val placement = DikcizGridLayoutEngine.resizeCandidate(
            grid = grid,
            occupied = page.occupiedCells(),
            current = activeResize.initialCell,
            target = filled,
        )
        if (placement is DikcizGridPlacement.Rejected) {
            setWidgetResizeRejected(activeResize, true)
            dikcizLogger.warn(
                EVENT_WIDGET_RESIZE_REJECTED,
                mapOf(
                    FIELD_REASON to placement.failure.persistedValue,
                    FIELD_RESIZE_FILL_AXIS to axis.persistedValue,
                    FIELD_WIDGET_ID to widgetID,
                ),
            )
            showGridFailureMessage(placement.failure)
            return
        }
        setWidgetResizeRejected(activeResize, false)
        activeResize.targetCell = filled
        activeResize.hasChanged = true
        applyWidgetCell(widgetView, filled)
        dikcizLogger.info(
            EVENT_WIDGET_RESIZE_FILLED,
            mapOf(
                FIELD_RESIZE_FILL_AXIS to axis.persistedValue,
                FIELD_WIDGET_ID to widgetID,
            ),
        )
    }

    private fun pageCanvasWidthPixels(): Int {
        return (widgetContainer.width - widgetContainer.paddingLeft - widgetContainer.paddingRight)
            .coerceAtLeast(NO_PADDING)
    }

    private fun pageCanvasHeightPixels(): Int {
        return (pageScrollView.height - pageScrollView.paddingTop - pageScrollView.paddingBottom)
            .coerceAtLeast(NO_PADDING)
    }

    /**
     * The span a new item asks for, bounded by the current grid. Insertion resolves the
     * real anchor, so the returned column and row are placeholders only.
     */
    private fun requestedCell(span: DikcizGridSpan): DikcizGridRectangle {
        return span.boundedBy(nativeGrid()).atOrigin()
    }

    private fun fullPageCell(): DikcizGridRectangle {
        val grid = nativeGrid()
        return DikcizGridRectangle(
            column = FIRST_CELL_INDEX,
            row = FIRST_CELL_INDEX,
            columnSpan = grid.columns,
            rowSpan = grid.rows,
        )
    }

    private fun addHtmlWidgetContent(
        container: LinearLayout,
        widget: HtmlHomeWidget,
        widgetFrame: View,
    ) {
        val configuration = homeConfiguration ?: return
        val page = pageBeingBuilt ?: configuration.selectedPage() ?: return
        val widgetAddress = page.scriptWidgetAddress(widget).persistedValue
        val widgetContext = DikcizWebWidgetContext.selfWidget(configuration, page, widget)
        container.addView(
            DikcizHtmlWidgetView(
                this,
                widget,
                widgetContext,
                dikcizLogger,
                { request -> executeHtmlWidgetCommand(widgetAddress, request) },
                { contentHeightPixels ->
                    onHtmlWidgetContentHeightChanged(
                        page.id,
                        widget,
                        widgetFrame,
                        contentHeightPixels,
                    )
                },
                widgetFrame::performLongClick,
                { recoverHtmlWidgetRenderer(widget.id, container, widgetFrame) },
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun executeHtmlWidgetCommand(
        sourceWidgetAddress: String,
        request: JSONObject,
    ): String {
        if (request.optString(DikcizAutomationControlPlane.KEY_TYPE) ==
            DikcizAutomationControlPlane.TYPE_AUTOMATION_DISPATCH
        ) {
            request.put(DikcizAutomationControlPlane.KEY_SOURCE_WIDGET_ADDRESS, sourceWidgetAddress)
        }
        return automationControlPlane.executeLocalCommand(request)
    }

    private fun recoverHtmlWidgetRenderer(
        widgetID: String,
        container: LinearLayout,
        widgetFrame: View,
    ) {
        if (!recoveringHtmlWidgetIDs.add(widgetID)) {
            return
        }
        mainHandler.post {
            try {
                val currentWidget = homeConfiguration
                    ?.selectedPage()
                    ?.widgets
                    ?.filterIsInstance<HtmlHomeWidget>()
                    ?.firstOrNull { widget -> widget.id == widgetID }
                if (
                    isFinishing ||
                    currentWidget == null ||
                    !widgetFrame.isAttachedToWindow ||
                    container.parent !== widgetFrame
                ) {
                    return@post
                }
                container.removeAllViews()
                if (currentWidget.enabled) {
                    addHtmlWidgetContent(container, currentWidget, widgetFrame)
                } else {
                    container.addView(createDisabledWidgetView())
                }
                dikcizLogger.info(
                    EVENT_HTML_WIDGET_RENDERER_RECOVERED,
                    mapOf(
                        FIELD_SCRIPT_ID to widgetID,
                        FIELD_SCRIPT_KIND to SCRIPT_KIND_HTML,
                        FIELD_WIDGET_ID to widgetID,
                    ),
                )
            } finally {
                recoveringHtmlWidgetIDs.remove(widgetID)
            }
        }
    }

    private fun deliverAutomationEventToHtmlWidgets(event: DikcizAutomationEvent) {
        mainHandler.post {
            if (isFinishing || homeConfiguration == null) {
                return@post
            }
            val eventDocument = event.toDocument()
            widgetContainer.collectHtmlWidgetViews().forEach { widgetView ->
                if (widgetView.acceptsAutomationEvent(event)) {
                    widgetView.deliverAutomationEvent(eventDocument)
                }
            }
            automationControlPlane.publish(
                DikcizAutomationControlPlane.EVENT_HTML_WIDGET_EVENT,
                mapOf(DikcizAutomationControlPlane.EVENT_FIELD_EVENT to eventDocument),
            )
        }
    }

    private fun publishAutomationEvent(event: DikcizAutomationEvent) {
        automationControlPlane.publish(
            DikcizAutomationControlPlane.EVENT_AUTOMATION_EVENT,
            mapOf(DikcizAutomationControlPlane.EVENT_FIELD_EVENT to event.toDocument()),
        )
    }

    private fun deliverHtmlWidgetStateUpdates(
        previousConfiguration: HomeConfiguration,
        currentConfiguration: HomeConfiguration,
    ) {
        val previousWidgets = previousConfiguration.selectedPage()
            ?.widgets
            ?.filterIsInstance<HtmlHomeWidget>()
            ?.associateBy(HtmlHomeWidget::id)
            .orEmpty()
        val currentWidgets = currentConfiguration.selectedPage()
            ?.widgets
            ?.filterIsInstance<HtmlHomeWidget>()
            ?.associateBy(HtmlHomeWidget::id)
            .orEmpty()
        widgetContainer.collectHtmlWidgetViews().forEach { widgetView ->
            val currentWidget = currentWidgets[widgetView.widgetID()] ?: return@forEach
            val previousWidget = previousWidgets[currentWidget.id]
            if (previousWidget?.serializedState == currentWidget.serializedState) {
                return@forEach
            }
            widgetView.deliverState(currentWidget.state)
        }
    }

    private fun patchRenderedHtmlDom(action: DikcizLuaAutomationAction.PatchDom): String {
        if (Looper.myLooper() == mainHandler.looper) {
            return DikcizHtmlDomPatchOutcome.OperationFailed
        }
        val completion = CountDownLatch(1)
        var outcome = DikcizHtmlDomPatchOutcome.TargetNotRendered
        if (!mainHandler.post {
                val configuration = homeConfiguration
                val target = configuration?.scriptWidgetTarget(action.widgetAddress)
                if (isFinishing || configuration == null || target == null ||
                    configuration.selectedPageID != target.pageID
                ) {
                    completion.countDown()
                    return@post
                }
                val widget = configuration.selectedPage()
                    ?.widgets
                    ?.firstOrNull { candidate -> candidate.id == target.widgetID }
                if (widget !is HtmlHomeWidget) {
                    outcome = DikcizHtmlDomPatchOutcome.TargetNotHtml
                    completion.countDown()
                    return@post
                }
                val widgetView = widgetContainer.collectHtmlWidgetViews()
                    .firstOrNull { candidate -> candidate.widgetID() == widget.id }
                if (widgetView == null) {
                    completion.countDown()
                    return@post
                }
                widgetView.patchDom(action.selector, action.values) { result ->
                    outcome = result
                    completion.countDown()
                }
            }
        ) {
            return DikcizHtmlDomPatchOutcome.TargetNotRendered
        }
        return try {
            if (completion.await(HTML_DOM_PATCH_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)) {
                outcome
            } else {
                DikcizHtmlDomPatchOutcome.OperationFailed
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            DikcizHtmlDomPatchOutcome.OperationFailed
        }
    }

    private fun requestHtmlWidgetContentFit(widget: HtmlHomeWidget, widgetView: View) {
        val htmlWidgetView = widgetView.findHtmlWidgetView()
        if (htmlWidgetView == null) {
            return
        }
        pendingHtmlContentFits.add(widget.id)
        if (htmlWidgetView.requestContentHeight()) {
            dikcizLogger.info(EVENT_HTML_WIDGET_CONTENT_FIT_REQUESTED, mapOf(FIELD_WIDGET_ID to widget.id))
            return
        }
        pendingHtmlContentFits.remove(widget.id)
        Toast.makeText(this, R.string.dikciz_html_widget_fit_content_unavailable, Toast.LENGTH_SHORT).show()
    }

    /**
     * Fits the HTML document inside its assigned row span.
     *
     * Content height is a render concern now. It never resizes the widget's cell, never
     * grows the page, and therefore never writes to the saved document.
     */
    private fun onHtmlWidgetContentHeightChanged(
        pageID: String,
        widget: HtmlHomeWidget,
        widgetView: View,
        contentHeightPixels: Int,
    ) {
        if (homeConfiguration?.selectedPageID != pageID) {
            return
        }
        val isManualFit = pendingHtmlContentFits.remove(widget.id)
        if (widget.heightMode != HtmlWidgetHeightMode.Content && !isManualFit) {
            return
        }
        val cellHeightPixels = widgetContainer.cellBounds(widget.cell).height
        applyHtmlWidgetContentHeight(
            widgetView,
            contentHeightPixels.coerceIn(NO_PADDING, cellHeightPixels),
        )
        if (!isManualFit) {
            return
        }
        dikcizLogger.info(
            EVENT_HTML_WIDGET_CONTENT_FIT_APPLIED,
            mapOf(FIELD_WIDGET_ID to widget.id),
        )
    }

    private fun applyHtmlWidgetContentHeight(widgetView: View, heightPixels: Int) {
        val layoutParams = widgetView.layoutParams ?: return
        if (layoutParams.height == heightPixels) {
            return
        }
        layoutParams.height = heightPixels
        widgetView.layoutParams = layoutParams
        widgetContainer.requestLayout()
        pageScrollView.requestLayout()
    }

    private fun View.findHtmlWidgetView(): DikcizHtmlWidgetView? {
        if (this is DikcizHtmlWidgetView) {
            return this
        }
        val group = this as? ViewGroup ?: return null
        for (index in 0 until group.childCount) {
            group.getChildAt(index).findHtmlWidgetView()?.let { return it }
        }
        return null
    }

    private fun View.collectHtmlWidgetViews(): List<DikcizHtmlWidgetView> {
        if (this is DikcizHtmlWidgetView) {
            return listOf(this)
        }
        val group = this as? ViewGroup ?: return emptyList()
        return List(group.childCount) { index -> group.getChildAt(index) }
            .flatMap { child -> child.collectHtmlWidgetViews() }
    }

    private fun View.descendantViews(): List<View> {
        val group = this as? ViewGroup ?: return listOf(this)
        return listOf(this) + List(group.childCount) { index -> group.getChildAt(index) }
            .flatMap { child -> child.descendantViews() }
    }

    private fun addScriptDashboardWidgetContent(
        container: LinearLayout,
        widget: ScriptDashboardHomeWidget,
        style: DikcizStyle,
    ) {
        container.addView(createWidgetTitle(widget.title, style))
        val configuration = homeConfiguration ?: return
        if (configuration.scripts.isEmpty()) {
            container.addView(createScriptDashboardTextView(
                getString(R.string.dikciz_script_dashboard_empty),
                style,
            ))
            return
        }
        configuration.scripts.forEach { script ->
            container.addView(
                createScriptDashboardEntry(
                    widgetID = widget.id,
                    script = script,
                    limits = configuration.limits,
                    style = style,
                ),
            )
        }
    }

    private fun createScriptDashboardEntry(
        widgetID: String,
        script: DikcizLuaScript,
        limits: DikcizLimits,
        style: DikcizStyle,
    ): LinearLayout {
        val runStatus = homeConfigStore.readLuaScriptRunStatus(script.id, limits)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(createWidgetTitle(script.title, style))
            addView(
                createScriptDashboardTextView(
                    getString(R.string.dikciz_script_dashboard_status, runStatus.status),
                    style,
                ).also { view ->
                    view.tag = ScriptDashboardRunStatusTextTag(
                        script.id,
                        ScriptDashboardRunStatusField.Status,
                    )
                },
            )
            addView(
                createScriptDashboardTextView(
                    getString(
                        R.string.dikciz_script_dashboard_last_run,
                        runStatus.lastRunAt ?: getString(R.string.dikciz_script_dashboard_never_run),
                    ),
                    style,
                ).also { view ->
                    view.tag = ScriptDashboardRunStatusTextTag(
                        script.id,
                        ScriptDashboardRunStatusField.LastRun,
                    )
                },
            )
            addView(LinearLayout(this@DikcizHomeActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(createScriptDashboardButton(
                    R.string.dikciz_script_dashboard_logs,
                    isEnabled = true,
                    semanticID = scriptDashboardActionSemanticID(
                        widgetID,
                        script.id,
                        SEMANTIC_SCRIPT_ACTION_LOGS,
                    ),
                ) {
                    showScriptLogViewer(script.id)
                })
                addView(createScriptDashboardButton(
                    if (script.enabled) {
                        R.string.dikciz_script_dashboard_disable
                    } else {
                        R.string.dikciz_script_dashboard_enable
                    },
                    isEnabled = !isSafeMode,
                    semanticID = scriptDashboardActionSemanticID(
                        widgetID,
                        script.id,
                        SEMANTIC_SCRIPT_ACTION_TOGGLE_ENABLED,
                    ),
                ) {
                    setLuaScriptEnabled(script.id, !script.enabled)
                })
                addView(createScriptDashboardButton(
                    R.string.dikciz_script_edit,
                    isEnabled = !isSafeMode,
                    semanticID = scriptDashboardActionSemanticID(
                        widgetID,
                        script.id,
                        SEMANTIC_SCRIPT_ACTION_EDIT,
                    ),
                ) {
                    showLuaScriptEditor(script)
                })
            })
        }
    }

    private fun refreshRenderedScriptDashboardStatuses(configuration: HomeConfiguration) {
        val scriptsByID = configuration.scripts.associateBy(DikcizLuaScript::id)
        widgetContainer.descendantViews().forEach { view ->
            val tag = view.tag as? ScriptDashboardRunStatusTextTag ?: return@forEach
            val script = scriptsByID[tag.scriptID] ?: return@forEach
            val status = homeConfigStore.readLuaScriptRunStatus(script.id, configuration.limits)
            val textView = view as? TextView ?: return@forEach
            textView.text = when (tag.field) {
                ScriptDashboardRunStatusField.Status -> {
                    getString(R.string.dikciz_script_dashboard_status, status.status)
                }

                ScriptDashboardRunStatusField.LastRun -> {
                    getString(
                        R.string.dikciz_script_dashboard_last_run,
                        status.lastRunAt ?: getString(R.string.dikciz_script_dashboard_never_run),
                    )
                }
            }
        }
    }

    private fun createScriptDashboardButton(
        labelResource: Int,
        isEnabled: Boolean,
        semanticID: String,
        onClick: () -> Unit,
    ): Button {
        return Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                NO_LAYOUT_SIZE,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                FULL_LAYOUT_WEIGHT,
            )
            contentDescription = getString(labelResource)
            isAllCaps = false
            this.isEnabled = isEnabled
            text = contentDescription
            setOnClickListener { onClick() }
            registerAutomationView(semanticID, this)
        }
    }

    private fun setLuaScriptEnabled(scriptID: String, isEnabled: Boolean) {
        updateConfiguration { configuration ->
            configuration.copy(scripts = configuration.scripts.map { script ->
                if (script.id == scriptID) script.copy(enabled = isEnabled) else script
            })
        }
    }

    private fun createScriptDashboardTextView(text: String, style: DikcizStyle): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(getColor(R.color.dikciz_foreground))
            textSize = SCRIPT_DASHBOARD_TEXT_SIZE_SP
            dikcizStyleRenderer.applyTextStyle(this, style)
        }
    }

    private fun addAppWidgetContent(
        container: LinearLayout,
        widget: AppHomeWidget,
        style: DikcizStyle,
        isEnabled: Boolean,
    ) {
        val component = ComponentName.unflattenFromString(widget.component)
        val isAvailable = component != null && resolvesActivity(component)
        val isLaunchEnabled = isEnabled && isAvailable
        val icon = if (isAvailable) appShortcutIcon(component, widget.id) else null
        val appShortcutView = when (widget.displayStyle) {
            AppWidgetDisplayStyle.IconWithLabel -> createIconWithLabelAppShortcut(
                widget,
                component,
                icon,
                style,
                isLaunchEnabled,
            )

            AppWidgetDisplayStyle.TextButton -> createAppShortcutButton(
                widget,
                component,
                icon = null,
                style = style,
                isLaunchEnabled = isLaunchEnabled,
            )

            AppWidgetDisplayStyle.IconAndLabelButton -> createAppShortcutButton(
                widget,
                component,
                icon,
                style,
                isLaunchEnabled,
            )
        }
        container.addView(appShortcutView.also { view ->
            view.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                if (isAvailable) {
                    LinearLayout.LayoutParams.MATCH_PARENT
                } else {
                    LinearLayout.LayoutParams.WRAP_CONTENT
                },
            )
            registerAutomationView(appSemanticID(widget.id), view)
        })
        if (!isAvailable) {
            container.addView(TextView(this).apply {
                text = getString(R.string.dikciz_unavailable_app)
                setTextColor(getColor(R.color.dikciz_muted_foreground))
                dikcizStyleRenderer.applyTextStyle(this, style)
            })
        }
    }

    private fun addAppGroupWidgetContent(
        container: LinearLayout,
        widget: AppGroupHomeWidget,
        style: DikcizStyle,
        isEnabled: Boolean,
    ) {
        val applications = appGroupApplications(widget)
        val folder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            contentDescription = getString(
                R.string.dikciz_app_group_content_description,
                widget.title,
                applications.size,
            )
            createAppGroupPreview(applications, widget).let(::addView)
            addView(TextView(this@DikcizHomeActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = densityPixels(APP_SHORTCUT_ICON_LABEL_GAP_DP)
                }
                gravity = Gravity.CENTER_HORIZONTAL
                text = widget.title
                setTextColor(getColor(R.color.dikciz_foreground))
                dikcizStyleRenderer.applyTextStyle(this, style)
            })
            this.isEnabled = isEnabled
            if (isEnabled) {
                isClickable = true
                isFocusable = true
                setOnClickListener { showAppGroupContents(widget) }
            }
        }
        container.addView(folder.also { view ->
            registerAutomationView(appGroupSemanticID(widget.id), view)
        })
        if (applications.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.dikciz_app_group_unavailable)
                setTextColor(getColor(R.color.dikciz_muted_foreground))
                dikcizStyleRenderer.applyTextStyle(this, style)
            })
        }
    }

    private fun createAppGroupPreview(
        applications: List<DikcizLaunchableApp>,
        widget: AppGroupHomeWidget,
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            applications.take(AppGroupLimits.PREVIEW_COMPONENTS).chunked(APP_GROUP_PREVIEW_COLUMNS)
                .forEach { row ->
                    addView(LinearLayout(this@DikcizHomeActivity).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        row.forEach { application ->
                            addView(ImageView(this@DikcizHomeActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    densityPixels(APP_GROUP_ICON_SIZE_DP),
                                    densityPixels(APP_GROUP_ICON_SIZE_DP),
                                )
                                contentDescription = getString(
                                    R.string.dikciz_app_widget_icon_content_description,
                                    application.label,
                                )
                                setImageDrawable(appShortcutIcon(application.component, widget.id))
                            })
                        }
                    })
                }
        }
    }

    /**
     * The installed apps a group names, in the order the group stores them.
     *
     * A component is matched in both its short and its full flattened form,
     * because the public files accept either and a member written by hand would
     * otherwise vanish from the group without saying why.
     */
    private fun appGroupApplications(widget: AppGroupHomeWidget): List<DikcizLaunchableApp> {
        val applicationsByComponent = mutableMapOf<String, DikcizLaunchableApp>()
        launchableApps().forEach { application ->
            applicationsByComponent[application.component.flattenToShortString()] = application
            applicationsByComponent[application.component.flattenToString()] = application
        }
        return widget.components.mapNotNull(applicationsByComponent::get)
    }

    /**
     * Opens a group as a grid of its member icons.
     *
     * Tapping a member launches it. Long pressing one offers Remove, which is
     * how every other item in Dikciz is removed, and removing the second to
     * last member collapses the group back into a plain app shortcut.
     */
    private fun showAppGroupContents(widget: AppGroupHomeWidget) {
        val applications = appGroupApplications(widget)
        lateinit var dialog: AlertDialog
        val contents = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(standardPadding(), standardPadding(), standardPadding(), standardPadding())
            if (applications.isEmpty()) {
                addView(createPanelSubtitle(getString(R.string.dikciz_app_group_unavailable)))
                return@apply
            }
            applications.chunked(APP_GROUP_MEMBER_COLUMNS).forEach { row ->
                addView(LinearLayout(this@DikcizHomeActivity).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    row.forEach { application ->
                        addView(
                            createAppGroupMember(widget, application) { dialog.dismiss() },
                        )
                    }
                })
            }
        }
        dialog = panelDialogBuilder()
            .setTitle(widget.title)
            .setView(ScrollView(this).apply { addView(contents) })
            .setNegativeButton(R.string.dikciz_dialog_close, null)
            .setPositiveButton(R.string.dikciz_widget_edit) { _, _ -> showEditAppGroupDialog(widget) }
            .create()
        showActiveDialog(
            dialog,
            onShown = {
                registerAutomationView(appGroupSemanticID(widget.id), contents)
                publishAutomationUIRendered()
            },
            onDismiss = {
                automationViews.keys.removeAll { key ->
                    key.startsWith(appGroupSemanticID(widget.id))
                }
                publishAutomationUIRendered()
            },
        )
    }

    private fun createAppGroupMember(
        widget: AppGroupHomeWidget,
        application: DikcizLaunchableApp,
        onSelected: () -> Unit,
    ): View {
        val component = application.component.flattenToShortString()
        return DikcizAppShortcutTileView(
            this,
            densityPixels(APP_SHORTCUT_ICON_LABEL_GAP_DP),
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                densityPixels(APP_GROUP_MEMBER_SIZE_DP),
                densityPixels(APP_GROUP_MEMBER_SIZE_DP),
            ).apply {
                marginStart = densityPixels(APP_GROUP_MEMBER_GAP_DP)
                marginEnd = densityPixels(APP_GROUP_MEMBER_GAP_DP)
                topMargin = densityPixels(APP_GROUP_MEMBER_GAP_DP)
                bottomMargin = densityPixels(APP_GROUP_MEMBER_GAP_DP)
            }
            contentDescription = application.label
            addView(ImageView(this@DikcizHomeActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(application.icon)
            })
            addView(TextView(this@DikcizHomeActivity).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                maxLines = APP_GROUP_MEMBER_LABEL_LINES
                text = application.label
                setTextColor(getColor(R.color.dikciz_foreground))
            })
            isClickable = true
            isLongClickable = true
            setOnClickListener {
                onSelected()
                launchExplicitComponent(application.component)
            }
            setOnLongClickListener {
                onSelected()
                confirmAppGroupMemberRemoval(widget, application.label, component)
                true
            }
            registerAutomationView(appGroupMemberSemanticID(widget.id, component), this)
        }
    }

    private fun confirmAppGroupMemberRemoval(
        widget: AppGroupHomeWidget,
        label: String,
        component: String,
    ) {
        showActiveDialog(
            panelDialogBuilder()
                .setTitle(getString(R.string.dikciz_app_group_remove_title, label))
                .setMessage(getString(R.string.dikciz_app_group_remove_message, widget.title))
                .setNegativeButton(R.string.dikciz_dialog_cancel, null)
                .setPositiveButton(R.string.dikciz_dialog_remove) { _, _ ->
                    removeAppGroupMember(widget, component)
                }
                .create(),
        )
    }

    private fun removeAppGroupMember(widget: AppGroupHomeWidget, component: String) {
        val remaining = widget.components.filterNot { candidate -> candidate == component }
        val willCollapse = remaining.size <= SINGLE_APP_GROUP_MEMBER
        val didRemove = updateSelectedPage { page ->
            page.withoutAppGroupMember(widget.id, component)
        }
        if (!didRemove) {
            return
        }
        dikcizLogger.info(
            if (willCollapse) EVENT_APP_GROUP_COLLAPSED else EVENT_APP_GROUP_MEMBER_REMOVED,
            mapOf(
                FIELD_WIDGET_ID to widget.id,
                FIELD_APP_GROUP_MEMBER_COUNT to remaining.size,
            ),
        )
    }

    private fun createIconWithLabelAppShortcut(
        widget: AppHomeWidget,
        component: ComponentName?,
        icon: Drawable?,
        style: DikcizStyle,
        isLaunchEnabled: Boolean,
    ): View {
        return DikcizAppShortcutTileView(
            this,
            densityPixels(APP_SHORTCUT_ICON_LABEL_GAP_DP),
        ).apply {
            contentDescription = appShortcutContentDescription(widget)
            icon?.let { drawable ->
                addView(ImageView(this@DikcizHomeActivity).apply {
                    // The tile hands the icon a square sized to the narrower side of the
                    // cell, and FIT_CENTER keeps the drawable's own proportions inside it.
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = getString(
                        R.string.dikciz_app_widget_icon_content_description,
                        widget.title,
                    )
                    setImageDrawable(drawable)
                })
            }
            addView(TextView(this@DikcizHomeActivity).apply {
                text = widget.title
                setTextColor(getColor(R.color.dikciz_foreground))
                gravity = Gravity.CENTER_HORIZONTAL
                dikcizStyleRenderer.applyTextStyle(this, style)
            })
            configureAppShortcutAction(this, component, isLaunchEnabled)
        }
    }

    private fun createAppShortcutButton(
        widget: AppHomeWidget,
        component: ComponentName?,
        icon: Drawable?,
        style: DikcizStyle,
        isLaunchEnabled: Boolean,
    ): Button {
        return Button(this).apply {
            text = widget.title
            isAllCaps = false
            dikcizStyleRenderer.applyTextStyle(this, style)
            contentDescription = appShortcutContentDescription(widget)
            if (icon != null) {
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
                compoundDrawablePadding = densityPixels(APP_SHORTCUT_BUTTON_ICON_GAP_DP)
            }
            configureAppShortcutAction(this, component, isLaunchEnabled)
        }
    }

    private fun configureAppShortcutAction(
        view: View,
        component: ComponentName?,
        isLaunchEnabled: Boolean,
    ) {
        view.isEnabled = isLaunchEnabled
        if (!isLaunchEnabled || component == null) {
            return
        }
        view.isClickable = true
        view.isFocusable = true
        view.setOnClickListener { launchExplicitComponent(component) }
    }

    private fun appShortcutIcon(component: ComponentName?, widgetID: String): Drawable? {
        if (component == null) {
            return null
        }
        return try {
            packageManager.getActivityIcon(component)
        } catch (exception: android.content.pm.PackageManager.NameNotFoundException) {
            dikcizLogger.warn(
                EVENT_APP_SHORTCUT_ICON_UNAVAILABLE,
                mapOf(
                    FIELD_ERROR_CLASS to exception::class.java.simpleName,
                    FIELD_WIDGET_ID to widgetID,
                ),
            )
            null
        }
    }

    private fun appShortcutContentDescription(widget: AppHomeWidget): String {
        return getString(
            R.string.dikciz_app_widget_content_description,
            widget.title,
            appWidgetDisplayStyleLabel(widget.displayStyle),
        )
    }

    private fun appWidgetDisplayStyleLabel(displayStyle: AppWidgetDisplayStyle): String {
        return getString(
            when (displayStyle) {
                AppWidgetDisplayStyle.IconWithLabel -> {
                    R.string.dikciz_app_widget_display_style_icon_label
                }

                AppWidgetDisplayStyle.TextButton -> {
                    R.string.dikciz_app_widget_display_style_button
                }

                AppWidgetDisplayStyle.IconAndLabelButton -> {
                    R.string.dikciz_app_widget_display_style_icon_label_button
                }
            },
        )
    }

    private fun addProviderWidgetContent(
        container: LinearLayout,
        widget: ProviderHomeWidget,
        style: DikcizStyle,
        isEnabled: Boolean,
    ) {
        val providerInfo = appWidgetManager.getAppWidgetInfo(widget.appWidgetID)
        if (providerInfo == null || providerInfo.provider.flattenToString() != widget.provider) {
            dikcizLogger.warn(
                EVENT_PROVIDER_WIDGET_UNAVAILABLE,
                mapOf(FIELD_WIDGET_ID to widget.id),
            )
            container.addView(TextView(this).apply {
                text = getString(R.string.dikciz_unavailable_widget)
                setTextColor(getColor(R.color.dikciz_muted_foreground))
                dikcizStyleRenderer.applyTextStyle(this, style)
            })
            return
        }
        val hostView = appWidgetHost.createView(this, widget.appWidgetID, providerInfo).apply {
            this.isEnabled = isEnabled
            alpha = if (isEnabled) ENABLED_WIDGET_ALPHA else DISABLED_WIDGET_ALPHA
        }
        val initialHeightDP = providerWidgetInitialHeightDP(widget, providerInfo)
        registerProviderWidgetSizeReporter(hostView, widget.id)
        container.addView(
            hostView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        dikcizLogger.debug(
            EVENT_PROVIDER_WIDGET_HOST_CREATED,
            mapOf(
                FIELD_HEIGHT_DP to initialHeightDP,
                FIELD_WIDGET_ID to widget.id,
            ),
        )
    }

    private fun providerWidgetInitialHeightDP(
        widget: ProviderHomeWidget,
        providerInfo: AppWidgetProviderInfo,
    ): Int {
        val cellHeightPixels = widgetContainer.cellBounds(widget.cell).height
        if (cellHeightPixels <= NO_PADDING) {
            return providerInfo.minHeight
        }
        return pixelsToDP(cellHeightPixels)
    }

    private fun registerProviderWidgetSizeReporter(
        hostView: AppWidgetHostView,
        widgetID: String,
    ) {
        var lastReportedSize: Pair<Int, Int>? = null
        hostView.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            if (view.width <= NO_PADDING || view.height <= NO_PADDING) {
                return@addOnLayoutChangeListener
            }
            val reportedSize = pixelsToDP(view.width) to pixelsToDP(view.height)
            if (lastReportedSize == reportedSize) {
                return@addOnLayoutChangeListener
            }
            lastReportedSize = reportedSize
            hostView.updateAppWidgetSize(
                null,
                reportedSize.first,
                reportedSize.second,
                reportedSize.first,
                reportedSize.second,
            )
            dikcizLogger.debug(
                EVENT_PROVIDER_WIDGET_SIZE_REPORTED,
                mapOf(
                    FIELD_HEIGHT_DP to reportedSize.second,
                    FIELD_WIDGET_ID to widgetID,
                    FIELD_WIDTH_DP to reportedSize.first,
                ),
            )
        }
    }

    private fun updateProviderWidgetHostHeight(
        widgetView: View,
        heightPixels: Int,
    ) {
        val hostView = providerWidgetHostView(widgetView) ?: return
        val layoutParams = hostView.layoutParams
        if (layoutParams.height == heightPixels) {
            return
        }
        layoutParams.height = heightPixels
        hostView.layoutParams = layoutParams
    }

    private fun providerWidgetHostView(widgetView: View): AppWidgetHostView? {
        if (widgetView is AppWidgetHostView) {
            return widgetView
        }
        if (widgetView !is ViewGroup) {
            return null
        }
        for (childIndex in FIRST_WIDGET_INDEX until widgetView.childCount) {
            val hostView = providerWidgetHostView(widgetView.getChildAt(childIndex))
            if (hostView != null) {
                return hostView
            }
        }
        return null
    }

    private fun createWidgetTitle(
        title: String,
        style: DikcizStyle = DikcizStyle(),
        onClick: (() -> Unit)? = null,
    ): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(getColor(R.color.dikciz_foreground))
            textSize = WIDGET_TITLE_TEXT_SIZE_SP
            dikcizStyleRenderer.applyTextStyle(this, style)
            if (onClick != null) {
                isClickable = true
                setOnClickListener { onClick.invoke() }
            }
        }
    }

    private fun createDisabledWidgetView(): TextView {
        return TextView(this).apply {
            text = getString(R.string.dikciz_disabled_widget)
            setTextColor(getColor(R.color.dikciz_muted_foreground))
            textSize = DISABLED_WIDGET_TEXT_SIZE_SP
        }
    }

    private fun createWidgetBackground(
        isEnabled: Boolean,
        style: DikcizStyle = DikcizStyle(),
    ): Drawable {
        val frameStyle = dikcizStyleRenderer.resolveWidgetFrameStyle(
            style = style,
            isEnabled = isEnabled,
            defaultCornerRadiusPixels = densityPixels(WIDGET_CORNER_RADIUS_DP).toFloat(),
        )
        return dikcizStyleRenderer.createBackground(frameStyle)
    }

    private fun createWidgetResizeOverlay(
        widget: HomeWidget,
        resizeHandles: Set<WidgetResizeHandle>,
    ): Drawable {
        val frameStyle = dikcizStyleRenderer.resolveWidgetFrameStyle(
            style = selectedWidgetStyle(widget),
            isEnabled = widget.enabled,
            defaultCornerRadiusPixels = densityPixels(WIDGET_CORNER_RADIUS_DP).toFloat(),
        )
        return WidgetResizeSelectionDrawable(
            cornerRadiusPixels = frameStyle.cornerRadiusPixels,
            outlineWidthPixels = densityPixels(WIDGET_RESIZE_OUTLINE_WIDTH_DP).toFloat(),
            pointRadiusPixels = densityPixels(WIDGET_RESIZE_POINT_RADIUS_DP).toFloat(),
            pointColor = getColor(R.color.dikciz_resize_point),
            rejectedColor = getColor(R.color.dikciz_resize_rejected),
            resizeHandles = resizeHandles,
        )
    }

    private fun selectedWidgetStyleByID(widgetID: String): DikcizStyle {
        val widget = homeConfiguration?.selectedPage()?.findWidget(widgetID)
        return widget?.let(::selectedWidgetStyle) ?: DikcizStyle()
    }

    private fun automationSnapshot(): JSONObject {
        if (isConfigurationErrorVisible) {
            return configurationErrorAutomationSnapshot()
        }
        val configuration = homeConfiguration ?: return storageAccessAutomationSnapshot()
        val selectedPage = configuration.selectedPage()
            ?: throw automationFailure(MESSAGE_SELECTED_PAGE_UNAVAILABLE)
        return JSONObject()
            .put(KEY_SCREEN, VALUE_HOME_SCREEN)
            .put(KEY_SELECTED_PAGE_ID, selectedPage.id)
            .put(KEY_PAGES, automationPages(configuration))
            .put(KEY_WIDGET_REFERENCES, automationWidgetReferences(configuration))
            .put(DikcizAutomationControlPlane.KEY_GRID, automationGrid(configuration.nativeGrid))
            .put(KEY_SCROLL, automationScrollState())
            .put(KEY_NODES, automationNodes())
    }

    private fun storageAccessAutomationSnapshot(): JSONObject {
        return JSONObject()
            .put(KEY_SCREEN, VALUE_STORAGE_ACCESS_SCREEN)
            .put(KEY_SELECTED_PAGE_ID, JSONObject.NULL)
            .put(KEY_PAGES, JSONArray())
            .put(KEY_WIDGET_REFERENCES, JSONArray())
            .put(KEY_SCROLL, automationScrollState())
            .put(KEY_NODES, automationNodes())
    }

    private fun configurationErrorAutomationSnapshot(): JSONObject {
        return JSONObject()
            .put(KEY_SCREEN, VALUE_CONFIGURATION_ERROR_SCREEN)
            .put(KEY_SELECTED_PAGE_ID, JSONObject.NULL)
            .put(KEY_PAGES, JSONArray())
            .put(KEY_WIDGET_REFERENCES, JSONArray())
            .put(KEY_SCROLL, automationScrollState())
            .put(KEY_NODES, automationNodes())
    }

    private fun automationPages(configuration: HomeConfiguration): JSONArray {
        return JSONArray().apply {
            configuration.pages.forEach { page ->
                put(
                    JSONObject()
                        .put(KEY_ID, page.id)
                        .put(KEY_PAGE_LOCATION, page.referenceLocation())
                        .put(KEY_TITLE, boundedAutomationText(page.title))
                        .put(KEY_SELECTED, page.id == configuration.selectedPageID),
                )
            }
        }
    }

    private fun automationWidgetReferences(configuration: HomeConfiguration): JSONArray {
        return JSONArray().apply {
            configuration.pages.forEach { page ->
                page.widgets.forEach { widget ->
                    put(
                        JSONObject()
                            .put(KEY_ID, widget.id)
                            .put(KEY_PAGE_ID, page.id)
                            .put(KEY_PAGE_LOCATION, page.referenceLocation())
                            .put(KEY_TITLE, boundedAutomationText(widget.title))
                            .put(KEY_TYPE, widget.typeReferenceValue())
                            .put(DikcizAutomationControlPlane.KEY_CELL, automationCell(widget.cell))
                            .put(KEY_BOUNDS, renderedWidgetBounds(widget.id)),
                    )
                }
            }
        }
    }

    /** The logical grid every page is laid out on. */
    private fun automationGrid(grid: DikcizNativeGrid): JSONObject {
        return JSONObject()
            .put(DikcizAutomationControlPlane.KEY_COLUMNS, grid.columns)
            .put(DikcizAutomationControlPlane.KEY_ROWS, grid.rows)
            .put(DikcizAutomationControlPlane.KEY_GAP_DP, grid.gapDP)
            .put(DikcizAutomationControlPlane.KEY_OUTER_PADDING_DP, grid.outerPaddingDP)
    }

    private fun automationCell(cell: DikcizGridRectangle): JSONObject {
        return JSONObject()
            .put(DikcizAutomationControlPlane.KEY_COLUMN, cell.column)
            .put(DikcizAutomationControlPlane.KEY_ROW, cell.row)
            .put(DikcizAutomationControlPlane.KEY_COLUMN_SPAN, cell.columnSpan)
            .put(DikcizAutomationControlPlane.KEY_ROW_SPAN, cell.rowSpan)
    }

    /**
     * The rendered screen rectangle for a widget, or null when it is not on the selected
     * page. Clients pair this with the logical cell; only the cell is persistent.
     */
    private fun renderedWidgetBounds(widgetID: String): Any {
        val view = automationViews[widgetSemanticID(widgetID)] ?: return JSONObject.NULL
        val bounds = Rect()
        if (!view.getGlobalVisibleRect(bounds)) {
            return JSONObject.NULL
        }
        return JSONObject()
            .put(KEY_LEFT, bounds.left)
            .put(KEY_TOP, bounds.top)
            .put(KEY_RIGHT, bounds.right)
            .put(KEY_BOTTOM, bounds.bottom)
    }

    private fun automationScrollState(): JSONObject {
        return JSONObject()
            .put(KEY_Y, pageScrollView.scrollY)
            .put(KEY_RANGE, pageScrollView.verticalScrollRange())
            .put(KEY_EXTENT, pageScrollView.height)
    }

    private fun automationNodes(): JSONArray {
        refreshProviderAutomationViews()
        return JSONArray().apply {
            automationViews
                .plus(providerAutomationViews.mapValues { (_, providerView) -> providerView.view })
                .toSortedMap()
                .forEach { (semanticID, view) ->
                    put(describeAutomationView(semanticID, view))
                }
        }
    }

    private fun refreshProviderAutomationViews() {
        providerAutomationViews.clear()
        if (isConfigurationErrorVisible) return
        val selectedPage = homeConfiguration?.selectedPage() ?: return
        selectedPage.widgets.filterIsInstance<ProviderHomeWidget>().forEach { widget ->
            if (providerAutomationViews.size >= MAXIMUM_PROVIDER_AUTOMATION_NODE_COUNT) {
                return@forEach
            }
            val widgetView = automationViews[widgetSemanticID(widget.id)] ?: return@forEach
            val hostView = providerWidgetHostView(widgetView) ?: return@forEach
            registerProviderAutomationView(providerWidgetHostSemanticID(widget.id), hostView, hostView)
            registerProviderAutomationDescendants(
                widget.id,
                hostView,
                providerAutomationRenderToken(hostView),
                hostView,
                EMPTY_PROVIDER_AUTOMATION_PATH,
                0,
            )
        }
    }

    private fun registerProviderAutomationDescendants(
        widgetID: String,
        hostView: AppWidgetHostView,
        renderToken: Long,
        parent: ViewGroup,
        parentPath: String,
        depth: Int,
    ) {
        if (depth >= MAXIMUM_PROVIDER_AUTOMATION_DEPTH ||
            providerAutomationViews.size >= MAXIMUM_PROVIDER_AUTOMATION_NODE_COUNT
        ) {
            return
        }
        for (childIndex in 0 until parent.childCount) {
            if (providerAutomationViews.size >= MAXIMUM_PROVIDER_AUTOMATION_NODE_COUNT) {
                return
            }
            val child = parent.getChildAt(childIndex)
            val path = providerAutomationPath(parentPath, childIndex)
            if (child.isClickable || child.isLongClickable) {
                registerProviderAutomationView(
                    providerWidgetDescendantSemanticID(widgetID, renderToken, path),
                    child,
                    hostView,
                )
            }
            if (child is ViewGroup) {
                registerProviderAutomationDescendants(
                    widgetID,
                    hostView,
                    renderToken,
                    child,
                    path,
                    depth + 1,
                )
            }
        }
    }

    private fun providerAutomationRenderToken(hostView: AppWidgetHostView): Long {
        return (hostView as? DikcizProviderWidgetHostView)?.automationRenderToken
            ?: throw IllegalStateException("provider automation host view was not created by Dikciz")
    }

    private fun nextProviderAutomationRenderToken(): Long {
        check(providerAutomationRenderTokenSequence < MAXIMUM_PROVIDER_AUTOMATION_RENDER_TOKEN) {
            "provider automation render token limit reached"
        }
        providerAutomationRenderTokenSequence += PROVIDER_AUTOMATION_RENDER_TOKEN_INCREMENT
        return providerAutomationRenderTokenSequence
    }

    private fun registerProviderAutomationView(
        semanticID: String,
        view: View,
        hostView: AppWidgetHostView,
    ) {
        providerAutomationViews[semanticID] = ProviderAutomationView(view, hostView)
    }

    private fun describeAutomationView(semanticID: String, view: View): JSONObject {
        val bounds = Rect()
        val isVisible = view.getGlobalVisibleRect(bounds)
        return JSONObject()
            .put(KEY_SEMANTIC_ID, semanticID)
            .put(KEY_RESOURCE_ID, automationResourceID(view))
            .put(KEY_ROLE, automationRole(view))
            .put(KEY_CLASS_NAME, view.javaClass.name)
            .put(KEY_ENABLED, view.isEnabled)
            .put(KEY_CHECKED, (view as? CompoundButton)?.isChecked ?: JSONObject.NULL)
            .put(KEY_CLICKABLE, view.isClickable)
            .put(KEY_VISIBLE, isVisible)
            .put(KEY_CONTENT_DESCRIPTION, boundedAutomationText(view.contentDescription?.toString()))
            .put(
                KEY_TEXT,
                boundedAutomationText((view as? TextView)?.text?.toString()),
            )
            .put(
                KEY_BOUNDS,
                JSONObject()
                    .put(KEY_LEFT, bounds.left)
                    .put(KEY_TOP, bounds.top)
                    .put(KEY_RIGHT, bounds.right)
                    .put(KEY_BOTTOM, bounds.bottom),
            )
    }

    private fun automationResourceID(view: View): Any {
        val viewID = view.id
        if (viewID == View.NO_ID) {
            return JSONObject.NULL
        }
        return try {
            view.resources.getResourceName(viewID)
        } catch (_: Resources.NotFoundException) {
            JSONObject.NULL
        }
    }

    private fun automationRole(view: View): String {
        return when (view) {
            is DikcizPageScrollView -> AUTOMATION_ROLE_SCROLL_CONTAINER
            is DikcizPageIndicatorView -> AUTOMATION_ROLE_PAGE_INDICATOR
            is DikcizWidgetFrameLayout -> AUTOMATION_ROLE_WIDGET
            is AppWidgetHostView -> AUTOMATION_ROLE_APP_WIDGET
            is Switch -> AUTOMATION_ROLE_SWITCH
            is EditText -> AUTOMATION_ROLE_TEXT_INPUT
            is RadioButton -> AUTOMATION_ROLE_RADIO_BUTTON
            is Button -> AUTOMATION_ROLE_BUTTON
            is ImageView -> if (view.isClickable) {
                AUTOMATION_ROLE_IMAGE_BUTTON
            } else {
                AUTOMATION_ROLE_IMAGE
            }

            is TextView -> if (view.isClickable) {
                AUTOMATION_ROLE_BUTTON
            } else {
                AUTOMATION_ROLE_STATIC_TEXT
            }

            else -> AUTOMATION_ROLE_VIEW
        }
    }

    private fun executeAutomationCommand(command: DikcizAutomationCommand): JSONObject {
        if (isSafeMode && command.type !in SAFE_MODE_READ_ONLY_COMMAND_TYPES &&
            command.type != DikcizAutomationControlPlane.TYPE_TAP
        ) {
            dikcizLogger.warn(
                EVENT_SAFE_MODE_MUTATION_REJECTED,
                mapOf(FIELD_COMMAND_TYPE to command.type),
            )
            throw automationFailure(MESSAGE_SAFE_MODE_READ_ONLY)
        }
        if (
            (homeConfiguration == null || isConfigurationErrorVisible) &&
            command.type !in STORAGE_ACCESS_COMMAND_TYPES
        ) {
            throw automationFailure(MESSAGE_CONFIGURATION_UNAVAILABLE)
        }
        return when (command.type) {
            DikcizAutomationControlPlane.TYPE_ACCESSIBILITY_SNAPSHOT -> accessibilitySnapshot()
            DikcizAutomationControlPlane.TYPE_ACCESSIBILITY_ACTION -> accessibilityAction(command)
            DikcizAutomationControlPlane.TYPE_SNAPSHOT,
            DikcizAutomationControlPlane.TYPE_UI_DUMP,
            -> automationSnapshot()

            DikcizAutomationControlPlane.TYPE_CONFIG_GET -> automationConfigurationDocument()
            DikcizAutomationControlPlane.TYPE_SCRIPT_LOGS -> automationScriptLogs()
            DikcizAutomationControlPlane.TYPE_AUTOMATION_STATUS -> {
                DikcizAutomationStatus.document(applicationContext)
            }
            DikcizAutomationControlPlane.TYPE_AUTOMATION_TRIGGER -> triggerAutomationScript(command)
            DikcizAutomationControlPlane.TYPE_AUTOMATION_DISPATCH -> dispatchAutomationActions(command)
            DikcizAutomationControlPlane.TYPE_CONTROL_STATUS -> DikcizControlStatus.document(
                isSafeMode,
                SAFE_MODE_READ_ONLY_COMMAND_TYPES,
                SAFE_MODE_ACTION_SEMANTIC_IDS,
            )
            DikcizAutomationControlPlane.TYPE_CONFIG_REPLACE -> replaceAutomationConfiguration(command)
            DikcizAutomationControlPlane.TYPE_CONFIG_SEED -> replaceAutomationConfiguration(command)
            DikcizAutomationControlPlane.TYPE_AUTOMATION_SERVICE_SYNC -> syncAutomationServiceThroughAutomation()
            DikcizAutomationControlPlane.TYPE_RESET -> resetAutomationConfiguration()
            DikcizAutomationControlPlane.TYPE_FIND -> findAutomationView(command)
            DikcizAutomationControlPlane.TYPE_HOME_GET -> automationHomeMap()
            DikcizAutomationControlPlane.TYPE_HTML_WIDGET_RENDERER_CRASH -> {
                requestHtmlWidgetRendererCrash(command)
            }
            DikcizAutomationControlPlane.TYPE_TAP -> tapAutomationView(command)
            DikcizAutomationControlPlane.TYPE_LONG_PRESS -> longPressAutomationView(command)
            DikcizAutomationControlPlane.TYPE_SELECT_PAGE -> selectAutomationPage(command)
            DikcizAutomationControlPlane.TYPE_SCROLL_BY -> scrollAutomationPage(command)
            DikcizAutomationControlPlane.TYPE_SCROLL_TO -> scrollToAutomationView(command)
            DikcizAutomationControlPlane.TYPE_SET_TEXT -> setAutomationText(command)
            DikcizAutomationControlPlane.TYPE_LAUNCH_APP -> launchAutomationApp(command)
            DikcizAutomationControlPlane.TYPE_APP_CATALOGUE -> appCatalogueDocument(command)
            DikcizAutomationControlPlane.TYPE_APP_ACTION -> runAppActionCommand(command)
            DikcizAutomationControlPlane.TYPE_OPEN_AUTOMATION_SETUP -> openAutomationSetupCommand()
            DikcizAutomationControlPlane.TYPE_SHELL -> runAutomationShell(command)
            DikcizAutomationControlPlane.TYPE_INTENT -> dispatchAutomationIntent(command)
            DikcizAutomationControlPlane.TYPE_SCREENSHOT -> captureAutomationScreenshot()
            DikcizAutomationControlPlane.TYPE_DIAGNOSTICS -> automationDiagnostics()
            DikcizAutomationControlPlane.TYPE_WIDGET_GET -> automationWidget(command)
            DikcizAutomationControlPlane.TYPE_ADD_WIDGET -> addAutomationWidget(command)
            DikcizAutomationControlPlane.TYPE_GRID_SET -> setAutomationGrid(command)
            DikcizAutomationControlPlane.TYPE_WIDGET_MOVE -> moveAutomationWidget(command)
            DikcizAutomationControlPlane.TYPE_WIDGET_RESIZE -> resizeAutomationWidget(command)
            else -> throw automationFailure(MESSAGE_UNSUPPORTED_AUTOMATION_COMMAND)
        }
    }

    private fun accessibilitySnapshot(): JSONObject {
        return try {
            DikcizAccessibilityAutomation.snapshot()
        } catch (exception: DikcizAccessibilityAutomationException) {
            dikcizLogger.warn(
                EVENT_ACCESSIBILITY_SNAPSHOT_REJECTED,
                mapOf(FIELD_REASON to exception.code),
            )
            throw DikcizAutomationRequestException(exception.code, exception.message.orEmpty())
        }
    }

    private fun accessibilityAction(command: DikcizAutomationCommand): JSONObject {
        val action = command.request.getString(DikcizAutomationControlPlane.KEY_ACTION)
        return try {
            DikcizAccessibilityAutomation.performAction(
                snapshotID = command.request.getString(DikcizAutomationControlPlane.KEY_SNAPSHOT_ID),
                nodeID = command.request.getString(DikcizAutomationControlPlane.KEY_NODE_ID),
                actionName = action,
                text = if (command.request.has(DikcizAutomationControlPlane.KEY_TEXT)) {
                    command.request.getString(DikcizAutomationControlPlane.KEY_TEXT)
                } else {
                    null
                },
            ).also { result ->
                dikcizLogger.info(
                    EVENT_ACCESSIBILITY_ACTION_COMPLETED,
                    mapOf(
                        FIELD_COMMAND_TYPE to command.type,
                        FIELD_OUTCOME to result.getString(KEY_OUTCOME),
                        FIELD_REASON to action,
                    ),
                )
            }
        } catch (exception: DikcizAccessibilityAutomationException) {
            dikcizLogger.warn(
                EVENT_ACCESSIBILITY_ACTION_REJECTED,
                mapOf(
                    FIELD_COMMAND_TYPE to command.type,
                    FIELD_REASON to exception.code,
                ),
            )
            throw DikcizAutomationRequestException(exception.code, exception.message.orEmpty())
        }
    }

    private fun dispatchAutomationActions(command: DikcizAutomationCommand): JSONObject {
        val configuration = homeConfiguration ?: throw automationFailure(MESSAGE_CONFIGURATION_UNAVAILABLE)
        val sourceWidgetAddress = command.request.getString(
            DikcizAutomationControlPlane.KEY_SOURCE_WIDGET_ADDRESS,
        )
        val actions = try {
            DikcizAutomationActionDocument.parse(
                command.request.getJSONArray(DikcizAutomationControlPlane.KEY_ACTIONS),
                sourceWidgetAddress,
                configuration.limits,
            )
        } catch (exception: IllegalArgumentException) {
            throw automationFailure(exception.message ?: MESSAGE_INVALID_AUTOMATION_ACTIONS)
        }
        val results = DikcizAutomationEventBus.dispatchDirectActions(
            applicationContext,
            sourceWidgetAddress,
            actions,
        )
        return JSONObject().put(
            DikcizAutomationControlPlane.KEY_ACTIONS,
            JSONArray().apply {
                results.forEach { result ->
                    put(
                        JSONObject()
                            .put(KEY_ACTION_TYPE, result.type)
                            .put(KEY_OUTCOME, result.outcome),
                    )
                }
            },
        )
    }

    private fun automationHomeMap(): JSONObject {
        val configuration = homeConfiguration ?: throw automationFailure(MESSAGE_CONFIGURATION_UNAVAILABLE)
        return JSONObject().put(KEY_PAGES, DikcizWebWidgetContext.pageMap(configuration))
    }

    private fun automationWidget(command: DikcizAutomationCommand): JSONObject {
        val configuration = homeConfiguration ?: throw automationFailure(MESSAGE_CONFIGURATION_UNAVAILABLE)
        val address = command.request.getString(DikcizAutomationControlPlane.KEY_WIDGET_ADDRESS)
        val target = configuration.scriptWidgetTarget(address) ?: throw automationFailure(MESSAGE_WIDGET_NOT_FOUND)
        val page = configuration.pages.first { candidate -> candidate.id == target.pageID }
        val widget = page.widgets.first { candidate -> candidate.id == target.widgetID }
        return DikcizWebWidgetContext.widgetDocument(page, widget)
    }

    /**
     * Creates one top-level item on the selected page through the control plane.
     *
     * The item always lands in the first fitting free cells of the selected page, so the
     * caller never chooses an anchor. Use `widgetMove` and `widgetResize` afterwards.
     * Types that need extra Android interaction, app shortcuts and Android provider
     * widgets, keep their existing routes: `appAction` with `addShortcut` and the native
     * picker.
     */
    private fun addAutomationWidget(command: DikcizAutomationCommand): JSONObject {
        val configuration = homeConfiguration ?: throw automationFailure(MESSAGE_CONFIGURATION_UNAVAILABLE)
        val page = configuration.selectedPage() ?: throw automationFailure(MESSAGE_SELECTED_PAGE_UNAVAILABLE)
        val widgetType = command.request.getString(DikcizAutomationControlPlane.KEY_WIDGET_TYPE)
        val placement = DikcizGridLayoutEngine.placePreferredSpan(
            grid = configuration.nativeGrid,
            occupied = page.occupiedCells(),
            preferred = automationWidgetSpan(widgetType),
        )
        if (placement is DikcizGridPlacement.Rejected) {
            dikcizLogger.warn(
                EVENT_WIDGET_INSERTION_REJECTED,
                mapOf(
                    FIELD_PAGE_ID to page.id,
                    FIELD_REASON to placement.failure.persistedValue,
                    FIELD_WIDGET_TYPE to widgetType,
                ),
            )
            throw gridFailure(placement.failure)
        }
        val cell = (placement as DikcizGridPlacement.Placed).rectangle
        val widget = buildAutomationWidget(widgetType, cell)
        if (!insertAutomationWidget(page.id, widget)) {
            throw automationFailure(MESSAGE_WIDGET_INSERTION_FAILED)
        }
        val savedPage = homeConfiguration?.pages?.first { candidate -> candidate.id == page.id }
            ?: throw automationFailure(MESSAGE_CONFIGURATION_UNAVAILABLE)
        val savedWidget = savedPage.widgets.first { candidate -> candidate.id == widget.id }
        return DikcizWebWidgetContext.widgetDocument(savedPage, savedWidget)
    }

    /**
     * Replaces the page grid for every page at once.
     *
     * Validation covers all placed items before anything is written. A single item that no
     * longer fits rejects the whole change and names the offending page and widget, so the
     * saved document is never left half converted.
     */
    private fun setAutomationGrid(command: DikcizAutomationCommand): JSONObject {
        val configuration = homeConfiguration ?: throw automationFailure(MESSAGE_CONFIGURATION_UNAVAILABLE)
        val grid = DikcizNativeGrid(
            columns = command.request.getInt(DikcizAutomationControlPlane.KEY_COLUMNS),
            rows = command.request.getInt(DikcizAutomationControlPlane.KEY_ROWS),
            gapDP = command.request.getInt(DikcizAutomationControlPlane.KEY_GAP_DP),
            outerPaddingDP = command.request.getInt(
                DikcizAutomationControlPlane.KEY_OUTER_PADDING_DP,
            ),
        )
        nativeGridConflict(configuration, grid)?.let { (page, conflict) ->
            dikcizLogger.warn(
                EVENT_PAGE_GRID_REJECTED,
                mapOf(
                    FIELD_PAGE_ID to page.id,
                    FIELD_REASON to conflict.failure.persistedValue,
                    FIELD_WIDGET_ID to conflict.itemID,
                ),
            )
            throw DikcizAutomationRequestException(
                gridFailureCode(conflict.failure),
                nativeGridConflictMessage(page, conflict, grid),
            )
        }
        if (grid != configuration.nativeGrid &&
            !updateConfiguration { current -> current.copy(nativeGrid = grid) }
        ) {
            throw automationFailure(MESSAGE_CONFIGURATION_SAVE_FAILED)
        }
        dikcizLogger.info(
            EVENT_PAGE_GRID_SAVED,
            mapOf(
                FIELD_COLUMN_SPAN to grid.columns,
                FIELD_ROW_SPAN to grid.rows,
            ),
        )
        return automationGrid(grid)
    }

    private fun moveAutomationWidget(command: DikcizAutomationCommand): JSONObject {
        return relocateAutomationWidget(command, isResize = false)
    }

    private fun resizeAutomationWidget(command: DikcizAutomationCommand): JSONObject {
        return relocateAutomationWidget(command, isResize = true)
    }

    /**
     * Moves or resizes one item to an explicit rectangle. A rejected request writes nothing
     * and reports the same typed failure the native gesture would.
     */
    private fun relocateAutomationWidget(
        command: DikcizAutomationCommand,
        isResize: Boolean,
    ): JSONObject {
        val configuration = homeConfiguration ?: throw automationFailure(MESSAGE_CONFIGURATION_UNAVAILABLE)
        val address = command.request.getString(DikcizAutomationControlPlane.KEY_WIDGET_ADDRESS)
        val target = configuration.scriptWidgetTarget(address) ?: throw automationFailure(MESSAGE_WIDGET_NOT_FOUND)
        val page = configuration.pages.first { candidate -> candidate.id == target.pageID }
        val widget = page.widgets.first { candidate -> candidate.id == target.widgetID }
        val requested = requestedAutomationCell(command)
            ?: throw automationFailure(MESSAGE_GRID_CELL_REQUIRED)
        val placement = DikcizGridLayoutEngine.moveCandidate(
            grid = configuration.nativeGrid,
            occupied = page.occupiedCells(),
            current = widget.cell,
            target = requested,
        )
        if (placement is DikcizGridPlacement.Rejected) {
            dikcizLogger.warn(
                if (isResize) EVENT_WIDGET_RESIZE_REJECTED else EVENT_WIDGET_MOVE_REJECTED,
                mapOf(
                    FIELD_PAGE_ID to page.id,
                    FIELD_REASON to placement.failure.persistedValue,
                    FIELD_WIDGET_ID to widget.id,
                ),
            )
            throw gridFailure(placement.failure)
        }
        val cell = (placement as DikcizGridPlacement.Placed).rectangle
        if (!updateConfiguration { current ->
            current.replacePage(page.id) { currentPage ->
                currentPage.replaceWidget(widget.id) { currentWidget -> currentWidget.withCell(cell) }
            }
        }) {
            throw automationFailure(MESSAGE_CONFIGURATION_SAVE_FAILED)
        }
        dikcizLogger.info(
            if (isResize) EVENT_WIDGET_RESIZED else EVENT_WIDGET_MOVED,
            mapOf(
                FIELD_COLUMN to cell.column,
                FIELD_COLUMN_SPAN to cell.columnSpan,
                FIELD_ROW to cell.row,
                FIELD_ROW_SPAN to cell.rowSpan,
                FIELD_WIDGET_ID to widget.id,
            ),
        )
        val savedPage = homeConfiguration?.pages?.first { candidate -> candidate.id == page.id }
            ?: throw automationFailure(MESSAGE_CONFIGURATION_UNAVAILABLE)
        val savedWidget = savedPage.widgets.first { candidate -> candidate.id == widget.id }
        return DikcizWebWidgetContext.widgetDocument(savedPage, savedWidget)
    }

    private fun requestedAutomationCell(command: DikcizAutomationCommand): DikcizGridRectangle? {
        val cell = command.request.optJSONObject(DikcizAutomationControlPlane.KEY_CELL) ?: return null
        return DikcizGridRectangle(
            column = cell.getInt(DikcizAutomationControlPlane.KEY_COLUMN),
            row = cell.getInt(DikcizAutomationControlPlane.KEY_ROW),
            columnSpan = cell.getInt(DikcizAutomationControlPlane.KEY_COLUMN_SPAN),
            rowSpan = cell.getInt(DikcizAutomationControlPlane.KEY_ROW_SPAN),
        )
    }

    private fun automationWidgetSpan(widgetType: String): DikcizGridSpan {
        return when (widgetType) {
            AUTOMATION_WIDGET_TYPE_HTML -> DikcizWidgetSpans.HTML
            AUTOMATION_WIDGET_TYPE_APP_GROUP -> DikcizWidgetSpans.APP_GROUP
            AUTOMATION_WIDGET_TYPE_SCRIPT_DASHBOARD -> DikcizWidgetSpans.SCRIPT_DASHBOARD
            else -> throw automationFailure(MESSAGE_UNSUPPORTED_WIDGET_TYPE)
        }
    }

    private fun buildAutomationWidget(
        widgetType: String,
        cell: DikcizGridRectangle,
    ): HomeWidget {
        val title = automationWidgetTitle(widgetType)
        val widgetID = newWidgetID(title) ?: throw automationFailure(MESSAGE_WIDGET_ID_UNAVAILABLE)
        return when (widgetType) {
            AUTOMATION_WIDGET_TYPE_HTML -> HtmlHomeWidget(
                id = widgetID,
                title = title,
                html = getString(R.string.dikciz_html_widget_blank_document),
                enabled = true,
                cell = cell,
            )

            AUTOMATION_WIDGET_TYPE_APP_GROUP -> AppGroupHomeWidget(
                id = widgetID,
                title = title,
                components = emptyList(),
                enabled = true,
                cell = cell,
            )

            AUTOMATION_WIDGET_TYPE_SCRIPT_DASHBOARD -> ScriptDashboardHomeWidget(
                id = widgetID,
                title = title,
                enabled = true,
                cell = cell,
            )

            else -> throw automationFailure(MESSAGE_UNSUPPORTED_WIDGET_TYPE)
        }
    }

    private fun automationWidgetTitle(widgetType: String): String {
        return when (widgetType) {
            AUTOMATION_WIDGET_TYPE_HTML -> getString(R.string.dikciz_html_widget_default_title)
            AUTOMATION_WIDGET_TYPE_APP_GROUP -> getString(R.string.dikciz_app_group_default_title)
            AUTOMATION_WIDGET_TYPE_SCRIPT_DASHBOARD ->
                getString(R.string.dikciz_script_dashboard_widget_title)

            else -> throw automationFailure(MESSAGE_UNSUPPORTED_WIDGET_TYPE)
        }
    }

    /** Inserts an already-placed widget without re-running placement. */
    private fun insertAutomationWidget(pageID: String, widget: HomeWidget): Boolean {
        return updateConfiguration { current ->
            current
                .replacePage(pageID) { page -> page.copy(widgets = page.widgets + widget) }
                .copy(selectedPageID = pageID)
        }
    }

    private fun requestHtmlWidgetRendererCrash(command: DikcizAutomationCommand): JSONObject {
        val configuration = homeConfiguration ?: throw automationFailure(MESSAGE_CONFIGURATION_UNAVAILABLE)
        val address = command.request.getString(DikcizAutomationControlPlane.KEY_WIDGET_ADDRESS)
        val target = configuration.scriptWidgetTarget(address) ?: throw automationFailure(MESSAGE_WIDGET_NOT_FOUND)
        if (configuration.selectedPageID != target.pageID) {
            return htmlWidgetRendererCrashResult(address, DikcizHtmlDomPatchOutcome.TargetNotRendered)
        }
        val widget = configuration.selectedPage()?.findWidget(target.widgetID)
        if (widget !is HtmlHomeWidget) {
            throw automationFailure(MESSAGE_HTML_WIDGET_REQUIRED)
        }
        val widgetView = widgetContainer.collectHtmlWidgetViews()
            .firstOrNull { candidate -> candidate.widgetID() == widget.id }
            ?: return htmlWidgetRendererCrashResult(address, DikcizHtmlDomPatchOutcome.TargetNotRendered)
        if (!widgetView.requestRendererCrash()) {
            return htmlWidgetRendererCrashResult(address, DikcizHtmlDomPatchOutcome.TargetNotRendered)
        }
        return htmlWidgetRendererCrashResult(address, OUTCOME_HTML_RENDERER_CRASH_REQUESTED)
    }

    private fun htmlWidgetRendererCrashResult(address: String, outcome: String): JSONObject {
        return JSONObject()
            .put(DikcizAutomationControlPlane.KEY_WIDGET_ADDRESS, address)
            .put(KEY_OUTCOME, outcome)
    }

    private fun triggerAutomationScript(command: DikcizAutomationCommand): JSONObject {
        val scriptID = command.request.getString(DikcizAutomationControlPlane.KEY_SCRIPT_ID)
        val configuration = homeConfiguration ?: throw automationFailure(MESSAGE_CONFIGURATION_UNAVAILABLE)
        val script = configuration.scripts.firstOrNull { candidate -> candidate.id == scriptID }
            ?: throw automationFailure(MESSAGE_MANUAL_TRIGGER_UNAVAILABLE)
        val automationScript = configuration.automation.scripts.firstOrNull { candidate ->
            candidate.scriptID == scriptID
        } ?: throw automationFailure(MESSAGE_MANUAL_TRIGGER_UNAVAILABLE)
        val hasManualSubscription = automationScript.subscriptions.any { subscription ->
            subscription.event == DikcizAutomationEventType.Manual
        }
        if (!script.enabled || !automationScript.enabled || !hasManualSubscription) {
            throw automationFailure(MESSAGE_MANUAL_TRIGGER_UNAVAILABLE)
        }
        configuration.automation.policy(automationScript.policyID)
            ?: throw automationFailure(MESSAGE_MANUAL_TRIGGER_UNAVAILABLE)
        DikcizAutomationEventBus.enqueueManualTrigger(applicationContext, scriptID)
        return JSONObject().put(DikcizAutomationControlPlane.KEY_SCRIPT_ID, scriptID)
    }

    private fun automationScriptLogs(): JSONObject {
        val snapshot = dikcizLogger.recentScriptLogSnapshot(DIKCIZ_MAXIMUM_SCRIPT_LOG_RECORDS)
        val records = JSONArray().apply {
            snapshot.records.forEach { record ->
                put(
                    JSONObject()
                        .put(KEY_SCRIPT_LOG_TIMESTAMP, record.timestamp)
                        .put(KEY_SCRIPT_LOG_LEVEL, record.level)
                        .put(KEY_SCRIPT_LOG_EVENT, record.event)
                        .put(KEY_SCRIPT_LOG_SCRIPT_ID, record.scriptID)
                        .put(KEY_SCRIPT_LOG_SCRIPT_KIND, record.scriptKind ?: JSONObject.NULL)
                        .put(KEY_SCRIPT_LOG_POLICY_ID, record.policyID ?: JSONObject.NULL)
                        .put(KEY_SCRIPT_LOG_EVENT_TYPE, record.eventType ?: JSONObject.NULL)
                        .put(KEY_SCRIPT_LOG_ACTION_TYPE, record.actionType ?: JSONObject.NULL)
                        .put(KEY_SCRIPT_LOG_OUTCOME, record.outcome ?: JSONObject.NULL)
                        .put(KEY_SCRIPT_LOG_REASON, record.reason ?: JSONObject.NULL)
                        .put(KEY_SCRIPT_LOG_DIAGNOSTIC, record.diagnostic ?: JSONObject.NULL),
                )
            }
        }
        return JSONObject()
            .put(KEY_SCRIPT_LOG_RECORDS, records)
            .put(KEY_SCRIPT_LOGS_TRUNCATED, snapshot.isTruncated)
    }

    private fun findAutomationView(command: DikcizAutomationCommand): JSONObject {
        val semanticID = command.request.getString(DikcizAutomationControlPlane.KEY_SEMANTIC_ID)
        val view = automationViewForSemanticID(semanticID, refreshProviderViews = true)
        if (view == null) {
            return JSONObject().put(KEY_FOUND, false).put(KEY_SEMANTIC_ID, semanticID)
        }
        return JSONObject().put(KEY_FOUND, true).put(KEY_NODE, describeAutomationView(semanticID, view))
    }

    private fun tapAutomationView(command: DikcizAutomationCommand): JSONObject {
        val semanticID = command.request.getString(DikcizAutomationControlPlane.KEY_SEMANTIC_ID)
        if (isSafeMode && semanticID !in SAFE_MODE_ACTION_SEMANTIC_IDS) {
            dikcizLogger.warn(
                EVENT_SAFE_MODE_MUTATION_REJECTED,
                mapOf(
                    FIELD_COMMAND_TYPE to command.type,
                    FIELD_SEMANTIC_ID to semanticID,
                ),
            )
            throw automationFailure(MESSAGE_SAFE_MODE_READ_ONLY)
        }
        val view = requireAutomationView(semanticID)
        val wasHandled = tapAutomationWidget(semanticID) || tapAutomationControl(view)
        if (!wasHandled) {
            throw automationFailure(MESSAGE_VIEW_NOT_ACTIONABLE)
        }
        dikcizLogger.debug(
            EVENT_AUTOMATION_TAPPED,
            mapOf(FIELD_SEMANTIC_ID to loggableSemanticID(semanticID)),
        )
        return JSONObject().put(KEY_NODE, describeAutomationView(semanticID, view))
    }

    private fun tapAutomationControl(view: View): Boolean {
        return when (view) {
            is RadioButton -> {
                if (!view.isEnabled) {
                    false
                } else {
                    (view.parent as? RadioGroup)?.check(view.id) ?: run { view.isChecked = true }
                    true
                }
            }

            is Switch -> {
                if (!view.isEnabled) {
                    false
                } else {
                    view.isChecked = !view.isChecked
                    true
                }
            }

            else -> view.performClick()
        }
    }

    private fun tapAutomationWidget(semanticID: String): Boolean {
        val widget = currentWidgetForSemanticID(semanticID) ?: return false
        if (!widget.enabled) {
            throw automationFailure(MESSAGE_WIDGET_DISABLED)
        }
        return when (widget) {
            is HtmlHomeWidget -> false
            is AppHomeWidget -> {
                if (semanticID != appSemanticID(widget.id)) {
                    false
                } else {
                    launchExplicitComponent(ComponentName.unflattenFromString(widget.component)
                        ?: throw automationFailure(MESSAGE_INVALID_APP_COMPONENT))
                }
            }

            is AppGroupHomeWidget -> {
                if (semanticID != appGroupSemanticID(widget.id)) {
                    false
                } else {
                    showAppGroupContents(widget)
                    true
                }
            }

            is ProviderHomeWidget,
            is ScriptDashboardHomeWidget,
            -> false
        }
    }

    private fun longPressAutomationView(command: DikcizAutomationCommand): JSONObject {
        val semanticID = command.request.getString(DikcizAutomationControlPlane.KEY_SEMANTIC_ID)
        val view = requireAutomationView(semanticID)
        val pageInsertionTarget = pageInsertionTargetForSemanticID(semanticID)
        if (pageInsertionTarget != null) {
            createPageAtRailExtremity(pageInsertionTarget.axis, pageInsertionTarget.side)
            dikcizLogger.debug(
                EVENT_AUTOMATION_LONG_PRESSED,
                mapOf(FIELD_SEMANTIC_ID to loggableSemanticID(semanticID)),
            )
            return JSONObject().put(KEY_NODE, describeAutomationView(semanticID, view))
        }
        if (!view.performLongClick()) {
            throw automationFailure(MESSAGE_VIEW_NOT_ACTIONABLE)
        }
        dikcizLogger.debug(
            EVENT_AUTOMATION_LONG_PRESSED,
            mapOf(FIELD_SEMANTIC_ID to loggableSemanticID(semanticID)),
        )
        return JSONObject().put(KEY_NODE, describeAutomationView(semanticID, view))
    }

    private fun selectAutomationPage(command: DikcizAutomationCommand): JSONObject {
        val pageID = command.request.getString(DikcizAutomationControlPlane.KEY_PAGE_ID)
        if (!selectPage(pageID)) {
            throw automationFailure(MESSAGE_PAGE_NOT_FOUND)
        }
        return automationSnapshot()
    }

    private fun scrollAutomationPage(command: DikcizAutomationCommand): JSONObject {
        val deltaY = command.request.getInt(DikcizAutomationControlPlane.KEY_DELTA_Y)
        pageScrollView.scrollBy(SCROLL_X_ORIGIN, deltaY)
        dikcizLogger.debug(EVENT_AUTOMATION_SCROLLED, mapOf(FIELD_DELTA_Y to deltaY))
        return automationScrollState()
    }

    private fun scrollToAutomationView(command: DikcizAutomationCommand): JSONObject {
        val semanticID = command.request.getString(DikcizAutomationControlPlane.KEY_SEMANTIC_ID)
        val view = requireAutomationView(semanticID)
        pageScrollTargetY(view)?.let { scrollY ->
            pageScrollView.scrollTo(SCROLL_X_ORIGIN, scrollY)
        }
        dikcizLogger.debug(
            EVENT_AUTOMATION_SCROLLED_TO,
            mapOf(FIELD_SEMANTIC_ID to loggableSemanticID(semanticID)),
        )
        return automationScrollState()
    }

    private fun setAutomationText(command: DikcizAutomationCommand): JSONObject {
        val semanticID = command.request.getString(DikcizAutomationControlPlane.KEY_SEMANTIC_ID)
        val text = command.request.getString(DikcizAutomationControlPlane.KEY_TEXT)
        val input = automationViewForSemanticID(semanticID) as? EditText
        if (input != null) {
            input.setText(text)
            dikcizLogger.debug(
                EVENT_AUTOMATION_TEXT_SET,
                mapOf(FIELD_SEMANTIC_ID to loggableSemanticID(semanticID)),
            )
            return JSONObject()
                .put(KEY_SEMANTIC_ID, semanticID)
                .put(KEY_TEXT, boundedAutomationText(text))
        }
        val widget = currentWidgetForSemanticID(semanticID) as? HtmlHomeWidget
            ?: throw automationFailure(MESSAGE_HTML_WIDGET_REQUIRED)
        if (!updateSelectedPage { page ->
                page.replaceWidget(widget.id) { currentWidget ->
                    val currentHtmlWidget = currentWidget as HtmlHomeWidget
                    currentHtmlWidget.copy(
                        state = JSONObject(currentHtmlWidget.state.toString()).put(FIELD_TEXT, text),
                    )
                }
            }
        ) {
            throw automationFailure(MESSAGE_CONFIGURATION_SAVE_FAILED)
        }
        dikcizLogger.debug(
            EVENT_AUTOMATION_TEXT_SET,
            mapOf(FIELD_SEMANTIC_ID to loggableSemanticID(semanticID)),
        )
        return JSONObject()
            .put(KEY_SEMANTIC_ID, semanticID)
            .put(KEY_TEXT, boundedAutomationText(text))
    }

    private fun automationConfigurationDocument(): JSONObject {
        val configuration = try {
            homeConfigStore.loadOrCreate()
        } catch (exception: HomeConfigException) {
            val fallbackConfiguration = DikcizAutomationEventBus.lastAcceptedConfiguration(applicationContext)
                ?: homeConfiguration
            if (fallbackConfiguration != null) {
                fallbackConfiguration
            } else {
                dikcizLogger.warn(
                    EVENT_AUTOMATION_CONFIGURATION_READ_REJECTED,
                    mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
                )
                throw automationFailure(exception.message.orEmpty())
            }
        }
        return JSONObject().put(KEY_CONFIG, homeConfigStore.configurationDocument(configuration))
    }

    private fun replaceAutomationConfiguration(command: DikcizAutomationCommand): JSONObject {
        val document = command.request.getJSONObject(DikcizAutomationControlPlane.KEY_CONFIG)
        val configuration = try {
            homeConfigStore.replaceConfigurationDocument(document)
        } catch (exception: HomeConfigException) {
            dikcizLogger.warn(
                EVENT_AUTOMATION_CONFIGURATION_REPLACE_REJECTED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            throw automationFailure(exception.message.orEmpty())
        }
        dikcizLogger.configure(configuration.logging)
        homeConfiguration?.let { previousConfiguration ->
            cleanupRemovedProviderWidgets(previousConfiguration, configuration)
        }
        homeConfiguration = configuration
        syncAutomationService(configuration)
        renderConfiguration()
        dikcizLogger.info(
            EVENT_AUTOMATION_CONFIGURATION_REPLACED,
            mapOf(FIELD_PAGE_COUNT to configuration.pages.size),
        )
        return JSONObject()
            .put(KEY_CONFIG, homeConfigStore.configurationDocument(configuration))
            .put(DikcizAutomationControlPlane.KEY_SNAPSHOT, automationSnapshot())
    }

    private fun resetAutomationConfiguration(): JSONObject {
        val configuration = try {
            resetToBundledConfiguration()
        } catch (exception: HomeConfigException) {
            throw automationFailure(exception.message.orEmpty())
        }
        return JSONObject()
            .put(KEY_CONFIG, homeConfigStore.configurationDocument(configuration))
            .put(DikcizAutomationControlPlane.KEY_SNAPSHOT, automationSnapshot())
    }

    private fun runAutomationShell(command: DikcizAutomationCommand): JSONObject {
        val shellCommand = command.request.getString(DikcizAutomationControlPlane.KEY_COMMAND)
        val useRoot = command.request.opt(DikcizAutomationControlPlane.KEY_ROOT) as? Boolean ?: true
        val startedAtMilliseconds = SystemClock.elapsedRealtime()
        dikcizLogger.debug(
            EVENT_AUTOMATION_SHELL_STARTED,
            mapOf(
                FIELD_COMMAND_LENGTH to shellCommand.length,
                FIELD_ROOT_REQUESTED to useRoot,
            ),
        )
        val executionScope: String
        val result: DikcizShellResult
        try {
            executionScope = if (useRoot) {
                if (!privilegedShell.isRootAvailable()) {
                    throw privilegedShell.rootUnavailable()
                }
                DikcizPrivilegedShell.VALUE_ROOT
            } else {
                DikcizPrivilegedShell.VALUE_APP_SANDBOX
            }
            result = privilegedShell.run(shellCommand, useRoot)
        } catch (exception: DikcizAutomationRequestException) {
            dikcizLogger.warn(
                EVENT_AUTOMATION_SHELL_REJECTED,
                mapOf(FIELD_REASON to exception.code),
            )
            throw exception
        } catch (exception: Exception) {
            dikcizLogger.error(
                EVENT_AUTOMATION_SHELL_FAILED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            throw automationFailure(MESSAGE_SHELL_FAILED)
        }
        val durationMilliseconds = SystemClock.elapsedRealtime() - startedAtMilliseconds
        dikcizLogger.info(
            EVENT_AUTOMATION_SHELL_COMPLETED,
            mapOf(
                FIELD_DURATION_MILLISECONDS to durationMilliseconds,
                FIELD_EXIT_CODE to result.exitCode,
                FIELD_OUTPUT_TRUNCATED to result.isOutputTruncated,
                FIELD_ROOT_REQUESTED to useRoot,
            ),
        )
        return JSONObject()
            .put(KEY_EXECUTION_SCOPE, executionScope)
            .put(KEY_EXIT_CODE, result.exitCode)
            .put(KEY_OUTPUT, result.output)
            .put(KEY_OUTPUT_TRUNCATED, result.isOutputTruncated)
    }

    private fun dispatchAutomationIntent(command: DikcizAutomationCommand): JSONObject {
        val intentType = command.request.getString(DikcizAutomationControlPlane.KEY_INTENT_TYPE)
        val action = command.request.getString(DikcizAutomationControlPlane.KEY_ACTION)
        try {
            when (intentType) {
                DikcizAutomationControlPlane.INTENT_TYPE_ACTIVITY -> startActivity(Intent(action))
                DikcizAutomationControlPlane.INTENT_TYPE_BROADCAST -> sendBroadcast(Intent(action))
                else -> throw automationFailure(MESSAGE_UNSUPPORTED_INTENT_TYPE)
            }
        } catch (exception: ActivityNotFoundException) {
            dikcizLogger.warn(
                EVENT_AUTOMATION_INTENT_REJECTED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            throw automationFailure(MESSAGE_INTENT_UNAVAILABLE)
        } catch (exception: SecurityException) {
            dikcizLogger.warn(
                EVENT_AUTOMATION_INTENT_REJECTED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            throw automationFailure(MESSAGE_INTENT_REJECTED)
        }
        dikcizLogger.info(EVENT_AUTOMATION_INTENT_DISPATCHED, mapOf(FIELD_INTENT_TYPE to intentType))
        return JSONObject()
            .put(DikcizAutomationControlPlane.KEY_ACTION, action)
            .put(KEY_DISPATCHED, true)
            .put(DikcizAutomationControlPlane.KEY_INTENT_TYPE, intentType)
    }

    private fun launchAutomationApp(command: DikcizAutomationCommand): JSONObject {
        val semanticID = command.request.getString(DikcizAutomationControlPlane.KEY_SEMANTIC_ID)
        val widget = currentWidgetForSemanticID(semanticID) as? AppHomeWidget
            ?: throw automationFailure(MESSAGE_APP_WIDGET_REQUIRED)
        val component = ComponentName.unflattenFromString(widget.component)
            ?: throw automationFailure(MESSAGE_INVALID_APP_COMPONENT)
        if (!launchExplicitComponent(component)) {
            throw automationFailure(MESSAGE_APP_UNAVAILABLE)
        }
        dikcizLogger.info(
            EVENT_AUTOMATION_APP_LAUNCHED,
            mapOf(FIELD_SEMANTIC_ID to loggableSemanticID(semanticID)),
        )
        return JSONObject().put(KEY_LAUNCHED, true).put(KEY_SEMANTIC_ID, semanticID)
    }

    private fun openAutomationSetupCommand(): JSONObject {
        showAutomationSettings()
        dikcizLogger.info(EVENT_AUTOMATION_SETUP_OPENED)
        return JSONObject().put(KEY_OPENED, true)
    }

    private fun appCatalogueDocument(command: DikcizAutomationCommand): JSONObject {
        val query = if (command.request.has(DikcizAutomationControlPlane.KEY_QUERY)) {
            command.request.getString(DikcizAutomationControlPlane.KEY_QUERY)
        } else {
            EMPTY_QUERY
        }
        val applications = launchableApps().filter { application ->
            DikcizAppCatalogue.matchesQuery(
                query,
                application.label,
                application.component.packageName,
            )
        }
        val entries = JSONArray()
        applications.take(MAXIMUM_APP_CATALOGUE_ENTRIES).forEach { application ->
            entries.put(
                JSONObject()
                    .put(KEY_SEMANTIC_ID, drawerAppSemanticID(application))
                    .put(KEY_LABEL, application.label.take(MAXIMUM_APP_LABEL_CHARACTERS))
                    .put(KEY_PACKAGE_NAME, application.component.packageName)
                    .put(KEY_COMPONENT, application.component.flattenToString()),
            )
        }
        dikcizLogger.debug(
            EVENT_APP_CATALOGUE_PROJECTED,
            mapOf(FIELD_RESULT_COUNT to entries.length()),
        )
        return JSONObject()
            .put(KEY_APPS, entries)
            .put(KEY_ENTRY_COUNT, applications.size)
            .put(KEY_TRUNCATED, applications.size > MAXIMUM_APP_CATALOGUE_ENTRIES)
            .put(DikcizAutomationControlPlane.KEY_ACTIONS, appActionDescriptors())
    }

    private fun appActionDescriptors(): JSONArray {
        val descriptors = JSONArray()
        DikcizAppActionType.entries.forEach { action ->
            descriptors.put(
                JSONObject()
                    .put(KEY_ACTION_ID, action.persistedValue)
                    .put(KEY_LABEL, getString(action.labelResourceID))
                    .put(KEY_SEMANTIC_ID, appActionSemanticID(action))
                    .put(KEY_DESTRUCTIVE, action.isDestructive)
                    .put(KEY_ROOT_REQUIRED, action == DikcizAppActionType.ForceStop),
            )
        }
        return descriptors
    }

    private fun runAppActionCommand(command: DikcizAutomationCommand): JSONObject {
        val actionValue = command.request.getString(DikcizAutomationControlPlane.KEY_ACTION)
        val action = DikcizAppActionType.fromPersistedValue(actionValue)
            ?: throw automationFailure(MESSAGE_UNSUPPORTED_APP_ACTION)
        val configuration = homeConfiguration
            ?: throw automationFailure(MESSAGE_CONFIGURATION_UNAVAILABLE)
        val component = DikcizAppIdentifiers.parseComponent(
            command.request.getString(DikcizAutomationControlPlane.KEY_COMPONENT),
            configuration.limits.maxComponentCharacters,
        ) ?: throw automationFailure(MESSAGE_INVALID_APP_COMPONENT)
        val application = launchableApps().firstOrNull { it.component == component }
            ?: throw automationFailure(MESSAGE_APP_UNAVAILABLE)
        val result = appActionRunner.run(action, application)
        val document = JSONObject()
            .put(DikcizAutomationControlPlane.KEY_ACTION, action.persistedValue)
            .put(KEY_COMPONENT, component.flattenToString())
            .put(KEY_PACKAGE_NAME, component.packageName)
            .put(KEY_LABEL, application.label.take(MAXIMUM_APP_LABEL_CHARACTERS))
            .put(KEY_OUTCOME, result.outcome)
            .put(KEY_SEMANTIC_ID, drawerAppSemanticID(application))
        result.exitCode?.let { exitCode -> document.put(KEY_EXIT_CODE, exitCode) }
        return document
    }

    private fun captureAutomationScreenshot(): JSONObject {
        val root = window.decorView
        if (root.width <= SCROLL_X_ORIGIN || root.height <= SCROLL_Y_ORIGIN) {
            throw automationFailure(MESSAGE_SCREEN_UNAVAILABLE)
        }
        val source = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(source))
        val screenshot = scaleAutomationScreenshot(source)
        if (screenshot !== source) {
            source.recycle()
        }
        val screenshotWidth = screenshot.width
        val screenshotHeight = screenshot.height
        val output = ByteArrayOutputStream()
        val didCompress = screenshot.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)
        screenshot.recycle()
        if (!didCompress) {
            throw automationFailure(MESSAGE_SCREENSHOT_ENCODING_FAILED)
        }
        val bytes = output.toByteArray()
        if (bytes.size > MAXIMUM_AUTOMATION_SCREENSHOT_BYTES) {
            throw automationFailure(MESSAGE_SCREENSHOT_TOO_LARGE)
        }
        return JSONObject()
            .put(KEY_PNG_BASE64, Base64.encodeToString(bytes, Base64.NO_WRAP))
            .put(KEY_WIDTH, screenshotWidth)
            .put(KEY_HEIGHT, screenshotHeight)
    }

    private fun scaleAutomationScreenshot(source: Bitmap): Bitmap {
        val longestEdge = maxOf(source.width, source.height)
        if (longestEdge <= MAXIMUM_AUTOMATION_SCREENSHOT_EDGE) {
            return source
        }
        val scale = MAXIMUM_AUTOMATION_SCREENSHOT_EDGE.toFloat() / longestEdge
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt(),
            (source.height * scale).toInt(),
            SCALE_SCREENSHOT_FILTER,
        )
    }

    private fun automationDiagnostics(): JSONObject {
        return JSONObject()
            .put(KEY_CONFIG_PATH, homeConfigStore.configurationFile.absolutePath)
            .put(KEY_LOG_DIRECTORY, homeConfigStore.logDirectory.absolutePath)
            .put(KEY_THEME_DIRECTORY, homeConfigStore.themeDirectory.absolutePath)
            .put(KEY_WALLPAPER_DIRECTORY, homeConfigStore.wallpaperDirectory.absolutePath)
            .put(KEY_CONTROL_PORT, DikcizAutomationControlPlane.CONTROL_PORT)
            .put(KEY_MCP_CONTROL_PORT, DikcizMcpControlPlane.CONTROL_PORT)
            .put(KEY_SCREEN, VALUE_HOME_SCREEN)
            .put(KEY_HTML_WIDGET_RESOURCES, htmlWidgetResourceDiagnostics())
    }

    private fun htmlWidgetResourceDiagnostics(): JSONObject {
        val activeRenderers = widgetContainer.collectHtmlWidgetViews()
        val limits = homeConfiguration?.limits
        return JSONObject()
            .put(KEY_HTML_WIDGET_ACTIVE_RENDERER_COUNT, activeRenderers.size)
            .put(
                KEY_HTML_WIDGET_DOCUMENT_BYTES,
                activeRenderers.sumOf(DikcizHtmlWidgetView::documentByteCount),
            )
            .put(
                KEY_HTML_WIDGET_MAX_DOCUMENT_BYTES,
                limits?.maxHtmlWidgetDocumentBytes ?: EMPTY_HTML_WIDGET_RESOURCE_VALUE,
            )
            .put(
                KEY_HTML_WIDGET_MAX_RENDERERS_PER_PAGE,
                limits?.maxHtmlWidgetsPerPage ?: EMPTY_HTML_WIDGET_RESOURCE_VALUE,
            )
    }

    private fun requireAutomationView(semanticID: String): View {
        return automationViewForSemanticID(semanticID) ?: throw automationFailure(MESSAGE_SEMANTIC_ID_NOT_FOUND)
    }

    private fun automationViewForSemanticID(
        semanticID: String,
        refreshProviderViews: Boolean = false,
    ): View? {
        if (refreshProviderViews) {
            refreshProviderAutomationViews()
        }
        automationViews[semanticID]?.let { view -> return view }
        val providerView = providerAutomationViews[semanticID] ?: return null
        if (providerView.isCurrent()) {
            return providerView.view
        }
        providerAutomationViews.remove(semanticID)
        return null
    }

    private fun currentWidgetForSemanticID(semanticID: String): HomeWidget? {
        if (!semanticID.startsWith(SEMANTIC_WIDGET_PREFIX)) {
            return null
        }
        val widgetID = semanticID
            .removePrefix(SEMANTIC_WIDGET_PREFIX)
            .substringBefore(SEMANTIC_WIDGET_ACTION_SEPARATOR)
        return homeConfiguration?.selectedPage()?.findWidget(widgetID)
    }

    private fun loggableSemanticID(semanticID: String): String {
        val appEntryPrefix = "$SEMANTIC_COMMAND_ENTRY_PREFIX$SEMANTIC_COMMAND_APP:"
        if (!semanticID.startsWith(appEntryPrefix)) {
            return semanticID
        }
        return when {
            semanticID.endsWith("$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_COMMAND_ADD_SHORTCUT") -> {
                "$SEMANTIC_COMMAND_ENTRY_PREFIX$SEMANTIC_COMMAND_APP:$SEMANTIC_COMMAND_ADD_SHORTCUT"
            }

            semanticID.endsWith("$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_COMMAND_LAUNCH") -> {
                "$SEMANTIC_COMMAND_ENTRY_PREFIX$SEMANTIC_COMMAND_APP:$SEMANTIC_COMMAND_LAUNCH"
            }

            else -> "$SEMANTIC_COMMAND_ENTRY_PREFIX$SEMANTIC_COMMAND_APP"
        }
    }

    private fun registerAutomationView(semanticID: String, view: View) {
        pageAutomationViewCapture?.let { capture ->
            capture[semanticID] = view
            return
        }
        automationViews[semanticID] = view
    }

    private fun clearCommandSheetAutomationViews() {
        automationViews.keys
            .filter { semanticID -> semanticID.startsWith(SEMANTIC_COMMAND_PREFIX) }
            .forEach(automationViews::remove)
    }

    private fun clearCommandSheetEntryAutomationViews() {
        automationViews.keys
            .filter { semanticID -> semanticID.startsWith(SEMANTIC_COMMAND_ENTRY_PREFIX) }
            .forEach(automationViews::remove)
    }

    private fun clearPageMenuAutomationViews() {
        automationViews.keys
            .filter { semanticID -> semanticID.startsWith(SEMANTIC_PAGE_MENU_PREFIX) }
            .forEach(automationViews::remove)
    }

    private fun clearPageRenameAutomationViews() {
        automationViews.keys
            .filter { semanticID -> semanticID.startsWith(SEMANTIC_PAGE_RENAME_PREFIX) }
            .forEach(automationViews::remove)
    }

    private fun clearWidgetLocksAutomationViews() {
        automationViews.keys
            .filter { semanticID -> semanticID.startsWith(SEMANTIC_WIDGET_LOCKS_PREFIX) }
            .forEach(automationViews::remove)
    }

    private fun clearWidgetEditorAutomationViews() {
        widgetEditorAutomationSemanticIDs.forEach(automationViews::remove)
        widgetEditorAutomationSemanticIDs.clear()
    }

    private fun clearPrimaryWidgetPickerAutomationViews() {
        automationViews.keys
            .filter { semanticID ->
                (
                    semanticID.startsWith(SEMANTIC_PICKER_WIDGET_PREFIX) &&
                        !semanticID.contains(SEMANTIC_PICKER_HTML_PACKAGE_SEGMENT)
                ) ||
                    semanticID.startsWith(SEMANTIC_PICKER_RETARGET_PREFIX)
            }
            .forEach(automationViews::remove)
    }

    private fun clearHtmlWidgetPackageEntryAutomationViews() {
        clearWidgetPickerAutomationViews(SEMANTIC_PICKER_HTML_PACKAGE_ENTRY_SEGMENT)
    }

    private fun clearHtmlWidgetPackagePreviewAutomationViews() {
        clearWidgetPickerAutomationViews(SEMANTIC_PICKER_HTML_PACKAGE_LOAD_SEGMENT)
    }

    private fun clearWidgetPickerAutomationViews(semanticIDSegment: String) {
        automationViews.keys
            .filter { semanticID -> semanticID.contains(semanticIDSegment) }
            .forEach(automationViews::remove)
    }

    private fun registerWidgetEditorAutomationView(semanticID: String, view: View) {
        widgetEditorAutomationSemanticIDs.add(semanticID)
        registerAutomationView(semanticID, view)
    }

    private fun registerWidgetPickerAutomationView(semanticID: String, view: View) {
        registerAutomationView(semanticID, view)
    }

    private fun clearSettingsAutomationViews() {
        automationViews.keys
            .filter { semanticID -> semanticID.startsWith(SEMANTIC_SETTINGS_PREFIX) }
            .forEach(automationViews::remove)
    }

    private fun clearAutomationSettingsAccessViews() {
        automationViews.keys
            .filter { semanticID -> semanticID.startsWith(SEMANTIC_SETTINGS_AUTOMATION_ACCESS_PREFIX) }
            .forEach(automationViews::remove)
    }

    private fun clearRemoteAuthAutomationViews() {
        automationViews.keys
            .filter { semanticID -> semanticID.startsWith(SEMANTIC_REMOTE_AUTH_PREFIX) }
            .forEach(automationViews::remove)
    }

    private fun clearDeviceAccessAutomationViews() {
        automationViews.keys
            .filter { semanticID -> semanticID.startsWith(SEMANTIC_SETTINGS_DEVICE_ACCESS_PREFIX) }
            .forEach(automationViews::remove)
    }

    private fun clearThemeAutomationViews() {
        automationViews.keys
            .filter { semanticID -> semanticID.startsWith(SEMANTIC_THEME_PREFIX) }
            .forEach(automationViews::remove)
    }

    private fun clearLuaScriptAutomationViews() {
        automationViews.keys
            .filter { semanticID -> semanticID.startsWith(SEMANTIC_LUA_SCRIPT_PREFIX) }
            .forEach(automationViews::remove)
    }

    private fun commandSheetSearchSemanticID(): String =
        "$SEMANTIC_COMMAND_PREFIX$SEMANTIC_COMMAND_SEARCH"

    private fun commandSheetEntrySemanticID(entry: CommandSheetEntry): String {
        return when (entry) {
            is ManagePageCommand -> "$SEMANTIC_COMMAND_ENTRY_PREFIX$SEMANTIC_COMMAND_MANAGE_PAGE:${entry.page.id}"
            SettingsCommand -> "$SEMANTIC_COMMAND_ENTRY_PREFIX$SEMANTIC_COMMAND_SETTINGS"
            is SelectPageCommand -> "$SEMANTIC_COMMAND_ENTRY_PREFIX$SEMANTIC_COMMAND_SELECT_PAGE:${entry.page.id}"
            is LaunchableAppCommand -> "$SEMANTIC_COMMAND_ENTRY_PREFIX$SEMANTIC_COMMAND_APP:${entry.application.component.flattenToString()}"
        }
    }

    private fun pageMenuActionSemanticID(pageID: String, action: PageMenuAction): String {
        return "$SEMANTIC_PAGE_MENU_PREFIX$pageID$SEMANTIC_WIDGET_ACTION_SEPARATOR${action.semanticAction}"
    }

    private fun pageRenameControlSemanticID(pageID: String, control: String): String {
        return "$SEMANTIC_PAGE_RENAME_PREFIX$pageID$SEMANTIC_WIDGET_ACTION_SEPARATOR$control"
    }

    private fun widgetLocksInputSemanticID(pageID: String, widgetID: String): String {
        return "$SEMANTIC_WIDGET_LOCKS_PREFIX$pageID$SEMANTIC_WIDGET_ACTION_SEPARATOR" +
            "$SEMANTIC_WIDGET_LOCKS_WIDGET$SEMANTIC_WIDGET_ACTION_SEPARATOR$widgetID"
    }

    private fun widgetLocksSaveSemanticID(pageID: String): String {
        return "$SEMANTIC_WIDGET_LOCKS_PREFIX$pageID$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_WIDGET_LOCKS_SAVE"
    }

    private fun commandAppLaunchSemanticID(application: DikcizLaunchableApp): String =
        "$SEMANTIC_COMMAND_ENTRY_PREFIX$SEMANTIC_COMMAND_APP:${application.component.flattenToString()}:$SEMANTIC_COMMAND_LAUNCH"

    private fun commandAppAddShortcutSemanticID(application: DikcizLaunchableApp): String =
        "$SEMANTIC_COMMAND_ENTRY_PREFIX$SEMANTIC_COMMAND_APP:${application.component.flattenToString()}:$SEMANTIC_COMMAND_ADD_SHORTCUT"

    private fun publishAutomationUIRendered() {
        val selectedPage = homeConfiguration?.selectedPage() ?: return
        automationControlPlane.publish(
            EVENT_AUTOMATION_UI_RENDERED,
            mapOf(
                DikcizAutomationControlPlane.EVENT_FIELD_SCREEN to VALUE_HOME_SCREEN,
                DikcizAutomationControlPlane.EVENT_FIELD_SELECTED_PAGE_ID to selectedPage.id,
            ),
        )
    }

    private fun widgetSemanticID(widgetID: String): String = "$SEMANTIC_WIDGET_PREFIX$widgetID"

    private fun providerWidgetHostSemanticID(widgetID: String): String =
        "${widgetSemanticID(widgetID)}$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_ACTION_PROVIDER"

    private fun providerWidgetDescendantSemanticID(
        widgetID: String,
        renderToken: Long,
        path: String,
    ): String =
        "${providerWidgetHostSemanticID(widgetID)}$SEMANTIC_WIDGET_ACTION_SEPARATOR" +
            "$SEMANTIC_PROVIDER_RENDER_TOKEN_PREFIX$renderToken" +
            "$SEMANTIC_WIDGET_ACTION_SEPARATOR$path"

    private fun providerAutomationPath(parentPath: String, childIndex: Int): String {
        return if (parentPath.isEmpty()) {
            childIndex.toString()
        } else {
            "$parentPath$SEMANTIC_PROVIDER_PATH_SEPARATOR$childIndex"
        }
    }

    private fun scriptDashboardActionSemanticID(
        widgetID: String,
        scriptID: String,
        action: String,
    ): String = "${widgetSemanticID(widgetID)}$SEMANTIC_WIDGET_ACTION_SEPARATOR" +
        "$SEMANTIC_ACTION_SCRIPT$SEMANTIC_WIDGET_ACTION_SEPARATOR$scriptID" +
        "$SEMANTIC_WIDGET_ACTION_SEPARATOR$action"

    private fun appSemanticID(widgetID: String): String =
        "${widgetSemanticID(widgetID)}$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_ACTION_APP"

    private fun appGroupSemanticID(widgetID: String): String =
        "${widgetSemanticID(widgetID)}$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_ACTION_APP_GROUP"

    private fun resizeSemanticID(widgetID: String): String =
        "${widgetSemanticID(widgetID)}$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_ACTION_RESIZE"

    private fun htmlWidgetFitContentSemanticID(widgetID: String): String =
        "${widgetSemanticID(widgetID)}$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_ACTION_FIT_CONTENT"

    private fun htmlWidgetAddBlockSemanticID(widgetID: String): String =
        "${widgetSemanticID(widgetID)}$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_ACTION_ADD_BLOCK"

    private fun htmlBlockEntrySemanticID(widgetID: String, blockID: String): String =
        "${htmlWidgetAddBlockSemanticID(widgetID)}$SEMANTIC_WIDGET_ACTION_SEPARATOR$blockID"

    private fun editSemanticID(widgetID: String): String =
        "${widgetSemanticID(widgetID)}$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_ACTION_EDIT"

    private fun widgetEditorSemanticID(widgetID: String, control: String): String =
        "${widgetSemanticID(widgetID)}$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_ACTION_EDITOR" +
            "$SEMANTIC_WIDGET_ACTION_SEPARATOR$control"

    private fun widgetEditorDisplayStyleSemanticID(
        widgetID: String,
        displayStyle: AppWidgetDisplayStyle,
    ): String = widgetEditorSemanticID(
        widgetID,
        "$SEMANTIC_EDITOR_DISPLAY_STYLE$SEMANTIC_WIDGET_ACTION_SEPARATOR${displayStyle.persistedValue}",
    )

    private fun widgetPickerSemanticID(pageID: String): String =
        "$SEMANTIC_PICKER_WIDGET_PREFIX$pageID"

    private fun htmlWidgetPackagePickerSemanticID(pageID: String): String =
        "${widgetPickerSemanticID(pageID)}$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_PICKER_HTML_PACKAGE"

    private fun htmlWidgetPackageEntrySemanticID(pickerID: String, packageID: String): String =
        "$pickerID$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_PICKER_ENTRY" +
            "$SEMANTIC_WIDGET_ACTION_SEPARATOR$packageID"

    private fun htmlWidgetPackageLoadSemanticID(pickerID: String, packageID: String): String =
        "$pickerID$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_PICKER_LOAD" +
            "$SEMANTIC_WIDGET_ACTION_SEPARATOR$packageID"

    private fun appRetargetPickerSemanticID(widgetID: String): String =
        "$SEMANTIC_PICKER_RETARGET_PREFIX$widgetID"

    private fun pickerSearchSemanticID(pickerID: String): String =
        "$pickerID$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_PICKER_SEARCH"

    private fun pickerPageFullSemanticID(pickerID: String): String =
        "$pickerID$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_PICKER_PAGE_FULL"

    private fun pickerCategorySemanticID(pickerID: String, categoryID: String): String =
        "$pickerID$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_PICKER_CATEGORY" +
            "$SEMANTIC_WIDGET_ACTION_SEPARATOR$categoryID"

    private fun pickerApplicationSemanticID(pickerID: String, packageName: String): String =
        "$pickerID$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_PICKER_APPLICATION" +
            "$SEMANTIC_WIDGET_ACTION_SEPARATOR$packageName"

    private fun pickerEntrySemanticID(pickerID: String, entry: WidgetPickerEntry): String =
        "$pickerID$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_PICKER_ENTRY" +
            "$SEMANTIC_WIDGET_ACTION_SEPARATOR${entry.automationID}"

    private fun appearanceActionSemanticID(widgetID: String): String =
        "${widgetSemanticID(widgetID)}$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_ACTION_APPEARANCE"

    private fun appearanceControlSemanticID(
        target: AppearanceEditorTarget,
        control: DikcizStyleEditorControl,
    ): String = "$SEMANTIC_APPEARANCE_PREFIX${target.semanticID}:${control.persistedValue}"

    private fun appearanceDialogActionSemanticID(
        target: AppearanceEditorTarget,
        action: String,
    ): String = "$SEMANTIC_APPEARANCE_PREFIX${target.semanticID}:$action"

    private fun resizeFillSemanticID(
        widgetID: String,
        axis: WidgetResizeFillAxis,
    ): String = "${widgetSemanticID(widgetID)}$SEMANTIC_WIDGET_ACTION_SEPARATOR${axis.semanticAction}"

    private fun deleteSemanticID(widgetID: String): String =
        "${widgetSemanticID(widgetID)}$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_ACTION_DELETE"

    private fun lockSemanticID(widgetID: String): String =
        "${widgetSemanticID(widgetID)}$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_ACTION_LOCK"

    private fun moveHandleSemanticID(widgetID: String): String =
        "${widgetSemanticID(widgetID)}$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_ACTION_MOVE_HANDLE"

    private fun settingsGridSemanticID(setting: DikcizGridSetting): String =
        "$SEMANTIC_SETTINGS_GRID_PREFIX${setting.persistedValue}"

    private fun settingsPageTitleSemanticID(pageID: String): String =
        "$SEMANTIC_SETTINGS_PAGE_PREFIX$pageID$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_SETTINGS_PAGE_TITLE"

    private fun settingsAppearanceSemanticID(scope: AppearanceDefaultScope): String =
        "$SEMANTIC_SETTINGS_APPEARANCE_PREFIX${scope.semanticID}"

    private fun themeSemanticID(themeID: String): String = "$SEMANTIC_THEME_PREFIX$themeID"

    private fun luaScriptSemanticID(scriptID: String): String = "$SEMANTIC_LUA_SCRIPT_PREFIX$scriptID"

    private fun luaScriptArchiveSemanticID(archive: File): String =
        "$SEMANTIC_LUA_SCRIPT_ARCHIVE_PREFIX${archive.name}"

    private fun setWidgetMoveGestureZoneInteractive(widgetID: String, isInteractive: Boolean) {
        automationViews[moveHandleSemanticID(widgetID)]?.apply {
            isEnabled = isInteractive
            isClickable = isInteractive
            isLongClickable = isInteractive
        }
    }

    private fun boundedAutomationText(value: String?): String? {
        return value?.take(MAXIMUM_AUTOMATION_NODE_TEXT_CHARACTERS)
    }

    private fun automationFailure(message: String): DikcizAutomationRequestException {
        return DikcizAutomationRequestException(
            DikcizAutomationControlPlane.ERROR_VALIDATION_FAILED,
            message,
        )
    }

    /**
     * A placement rejection as a typed control-plane failure. The code is the same finite
     * value the native UI and the diagnostics use, so both planes report one taxonomy.
     */
    private fun gridFailure(failure: DikcizGridFailure): DikcizAutomationRequestException {
        return DikcizAutomationRequestException(
            gridFailureCode(failure),
            getString(gridFailureMessageResourceID(failure)),
        )
    }

    private fun gridFailureCode(failure: DikcizGridFailure): String {
        return when (failure) {
            DikcizGridFailure.PageFull -> DikcizAutomationControlPlane.ERROR_PAGE_FULL
            DikcizGridFailure.GridCollision -> DikcizAutomationControlPlane.ERROR_GRID_COLLISION
            DikcizGridFailure.GridBounds -> DikcizAutomationControlPlane.ERROR_GRID_BOUNDS
        }
    }

    private fun selectPage(pageID: String): Boolean {
        val configuration = homeConfiguration ?: return false
        val selectedPage = configuration.selectedPage() ?: return false
        val selectedPageIndex = configuration.pages.indexOf(selectedPage)
        val destinationPage = configuration.pages.firstOrNull { it.id == pageID }
        if (destinationPage == null) {
            dikcizLogger.warn(
                EVENT_PAGE_SELECTION_REJECTED,
                mapOf(FIELD_REASON to REASON_PAGE_NOT_FOUND),
            )
            return false
        }
        val destinationPageIndex = configuration.pages.indexOf(destinationPage)
        if (destinationPageIndex == selectedPageIndex) {
            return true
        }
        val didUpdate = updatePageSelection { currentConfiguration ->
            dikcizLogger.info(
                EVENT_PAGE_SELECTION_STARTED,
                mapOf(
                    FIELD_FROM_PAGE_INDEX to selectedPageIndex,
                    FIELD_TO_PAGE_INDEX to destinationPageIndex,
                ),
            )
            currentConfiguration.copy(selectedPageID = pageID)
        }
        if (didUpdate) {
            dikcizLogger.info(
                EVENT_PAGE_SELECTED,
                mapOf(
                    FIELD_FROM_PAGE_INDEX to selectedPageIndex,
                    FIELD_TO_PAGE_INDEX to destinationPageIndex,
                ),
            )
            return true
        }
        return false
    }

    private fun updatePageSelection(
        transform: (HomeConfiguration) -> HomeConfiguration,
    ): Boolean {
        if (isSafeMode) {
            dikcizLogger.warn(EVENT_SAFE_MODE_MUTATION_REJECTED)
            Toast.makeText(this, R.string.dikciz_safe_mode_message, Toast.LENGTH_LONG).show()
            return false
        }
        val currentConfiguration = homeConfiguration ?: return false
        val updatedConfiguration = transform(currentConfiguration)
        dikcizLogger.debug(
            EVENT_CONFIGURATION_SAVE_STARTED,
            mapOf(FIELD_PAGE_COUNT to updatedConfiguration.pages.size),
        )
        try {
            homeConfigStore.saveSelectedPage(updatedConfiguration)
        } catch (exception: HomeConfigException) {
            dikcizLogger.error(
                EVENT_CONFIGURATION_SAVE_FAILED,
                mapOf(
                    FIELD_ERROR_CLASS to exception::class.java.simpleName,
                    FIELD_ERROR_SUMMARY to exception.message.orEmpty(),
                ),
            )
            Toast.makeText(this, exception.message, Toast.LENGTH_LONG).show()
            return false
        }
        homeConfiguration = updatedConfiguration
        dikcizLogger.debug(EVENT_CONFIGURATION_SAVE_COMPLETED)
        val selectedPage = updatedConfiguration.selectedPage() ?: return false
        showCachedPage(updatedConfiguration, selectedPage)
        scheduleNeighborPageCache(updatedConfiguration, selectedPage)
        return true
    }

    private fun setHomePage(pageID: String) {
        if (homeConfiguration?.homePageID == pageID) {
            dikcizLogger.debug(
                EVENT_HOME_PAGE_SET_SKIPPED,
                mapOf(FIELD_HOME_PAGE_ID to pageID),
            )
            return
        }
        if (!updateConfiguration { configuration ->
                if (configuration.pages.none { page -> page.id == pageID }) {
                    return@updateConfiguration configuration
                }
                configuration.copy(homePageID = pageID)
            }
        ) {
            return
        }
        dikcizLogger.info(
            EVENT_HOME_PAGE_CHANGED,
            mapOf(FIELD_HOME_PAGE_ID to pageID),
        )
    }

    private fun showPageMenu(page: HomePage) {
        dikcizLogger.debug(
            EVENT_PAGE_MENU_OPENED,
            mapOf(
                FIELD_SELECTED_PAGE_ID to page.id,
                FIELD_WIDGET_COUNT to page.widgets.size,
            ),
        )
        val actions = pageMenuActions(page)
        lateinit var dialog: AlertDialog
        val actionButtons = mutableMapOf<PageMenuAction, Button>()
        val actionList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            PageMenuSection.entries.forEach { section ->
                val sectionActions = actions.filter { action -> action.section == section }
                if (sectionActions.isEmpty()) {
                    return@forEach
                }
                addView(createWidgetTitle(getString(section.labelResourceID)))
                sectionActions.forEach { action ->
                    val button = Button(this@DikcizHomeActivity).apply {
                        text = getString(action.labelResourceID)
                        contentDescription = text
                        isAllCaps = false
                        isEnabled = pageMenuActionEnabled(page, action)
                        setOnClickListener {
                            if (!pageMenuActionEnabled(page, action)) {
                                return@setOnClickListener
                            }
                            dialog.dismiss()
                            performPageMenuAction(page, action)
                        }
                    }
                    actionButtons[action] = button
                    addView(
                        button,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                }
            }
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dikciz_page_menu_title, page.title, page.scriptPageAddress()))
            .setView(ScrollView(this).apply { addView(actionList) })
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .create()
        showActiveDialog(
            dialog,
            onShown = {
                actionButtons.forEach { (action, button) ->
                    registerAutomationView(pageMenuActionSemanticID(page.id, action), button)
                }
            },
            onDismiss = ::clearPageMenuAutomationViews,
        )
    }

    private fun performPageMenuAction(page: HomePage, action: PageMenuAction) {
        if (!pageMenuActionEnabled(page, action)) {
            return
        }
        when (action) {
            PageMenuAction.Widgets -> showWidgetPicker(page, page.widgets.size)
            PageMenuAction.HtmlWidgets -> showHtmlWidgetManager(page)
            PageMenuAction.SetHome -> setHomePage(page.id)
            PageMenuAction.Rename -> showRenamePageDialog(page)
            PageMenuAction.Commands -> showCommandSheet(page)
            PageMenuAction.Settings -> showSettingsDialog()
            PageMenuAction.Delete -> mainHandler.post { deletePage(page) }
            PageMenuAction.Lock -> setPageLocked(page.id, true)
            PageMenuAction.Unlock -> setPageLocked(page.id, false)
            PageMenuAction.WidgetLocks -> showWidgetLocksDialog(page)
            PageMenuAction.MoveLeft,
            PageMenuAction.MoveRight,
            PageMenuAction.MoveUp,
            PageMenuAction.MoveDown,
            -> movePage(page, checkNotNull(action.moveDirection))
        }
    }

    private fun pageMenuActions(page: HomePage): List<PageMenuAction> {
        return buildList {
            if (!page.locked) {
                add(PageMenuAction.Widgets)
                if (page.widgets.any { widget -> widget is HtmlHomeWidget }) {
                    add(PageMenuAction.HtmlWidgets)
                }
            }
            add(PageMenuAction.SetHome)
            add(if (page.locked) PageMenuAction.Unlock else PageMenuAction.Lock)
            add(PageMenuAction.WidgetLocks)
            add(PageMenuAction.Commands)
            if (page.locked) {
                return@buildList
            }
            add(PageMenuAction.Rename)
            add(PageMenuAction.Settings)
            if (page.widgets.isEmpty()) {
                add(PageMenuAction.Delete)
            }
            PageMenuAction.entries.filter { candidate -> candidate.moveDirection != null }
                .forEach(::add)
        }
    }

    private fun pageMenuActionEnabled(page: HomePage, action: PageMenuAction): Boolean {
        val direction = action.moveDirection
        if (direction != null) {
            return !page.locked && homeConfiguration?.adjacentPage(page, direction) != null
        }
        return action != PageMenuAction.SetHome || page.id != homeConfiguration?.homePageID
    }

    private fun showWidgetAppearanceDialog(widget: HomeWidget) {
        val target = AppearanceEditorTarget(
            semanticID = "widget:${widget.id}",
            scope = APPEARANCE_SCOPE_WIDGET,
            widgetID = widget.id,
        )
        showAppearanceDialog(
            target = target,
            title = getString(R.string.dikciz_appearance_widget_title, widget.title),
            initialStyle = widget.style ?: DikcizStyle(),
            onReset = {
                updateSelectedPage { page ->
                    page.replaceWidget(widget.id) { current -> current.withStyle(null) }
                }
            },
            onSave = { style ->
                updateSelectedPage { page ->
                    page.replaceWidget(widget.id) { current -> current.withStyle(style) }
                }
            },
        )
    }

    private fun showDefaultAppearanceDialog(scope: AppearanceDefaultScope) {
        val configuration = homeConfiguration ?: return
        val target = AppearanceEditorTarget(
            semanticID = "defaults:${scope.semanticID}",
            scope = "default_${scope.semanticID}",
        )
        showAppearanceDialog(
            target = target,
            title = getString(scope.titleResourceID),
            initialStyle = configuration.styleDefaults.styleFor(scope),
            onReset = {
                updateConfiguration { current ->
                    current.copy(
                        styleDefaults = current.styleDefaults.withStyle(
                            scope,
                            DikcizStyle(),
                        ),
                    )
                }
            },
            onSave = { style ->
                updateConfiguration { current ->
                    current.copy(styleDefaults = current.styleDefaults.withStyle(scope, style))
                }
            },
        )
    }

    private fun showAppearanceDialog(
        target: AppearanceEditorTarget,
        title: String,
        initialStyle: DikcizStyle,
        onReset: () -> Boolean,
        onSave: (DikcizStyle) -> Boolean,
    ) {
        val editor = DikcizStyleEditor(
            context = this,
            fontDirectory = File(
                homeConfigStore.configurationFile.parentFile,
                DikcizStyleCodec.FONT_DIRECTORY_NAME,
            ),
            typefaceForPreview = dikcizStyleRenderer::typefaceForPreview,
            style = initialStyle,
            standardPaddingPixels = standardPadding(),
        )
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(editor.view)
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .setNeutralButton(R.string.dikciz_appearance_reset, null)
            .setPositiveButton(R.string.dikciz_dialog_save, null)
            .create()
        dialog.setOnShowListener {
            editor.registerControls { control, view ->
                registerAutomationView(appearanceControlSemanticID(target, control), view)
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).apply {
                registerAutomationView(
                    appearanceDialogActionSemanticID(target, SEMANTIC_APPEARANCE_RESET),
                    this,
                )
                setOnClickListener {
                    if (!onReset()) {
                        return@setOnClickListener
                    }
                    dikcizLogger.info(EVENT_APPEARANCE_RESET, target.logFields())
                    dialog.dismiss()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                registerAutomationView(
                    appearanceDialogActionSemanticID(target, SEMANTIC_APPEARANCE_SAVE),
                    this,
                )
                setOnClickListener {
                    val style = try {
                        editor.readStyle()
                    } catch (exception: DikcizStyleEditorInputException) {
                        dikcizLogger.warn(
                            EVENT_APPEARANCE_SAVE_REJECTED,
                            target.logFields() + (FIELD_ERROR_CLASS to exception::class.java.simpleName),
                        )
                        return@setOnClickListener
                    }
                    if (!onSave(style)) {
                        return@setOnClickListener
                    }
                    dikcizLogger.info(EVENT_APPEARANCE_SAVED, target.logFields())
                    dialog.dismiss()
                }
            }
            publishAutomationUIRendered()
        }
        dikcizLogger.info(EVENT_APPEARANCE_EDITOR_OPENED, target.logFields())
        showActiveDialog(dialog) {
            clearAppearanceAutomationViews()
            publishAutomationUIRendered()
        }
    }

    private fun clearAppearanceAutomationViews() {
        automationViews.keys
            .filter { semanticID -> semanticID.startsWith(SEMANTIC_APPEARANCE_PREFIX) }
            .forEach(automationViews::remove)
    }

    private fun DikcizStyleDefaults.styleFor(scope: AppearanceDefaultScope): DikcizStyle {
        return when (scope) {
            AppearanceDefaultScope.Widget -> widget
        }
    }

    private fun DikcizStyleDefaults.withStyle(
        scope: AppearanceDefaultScope,
        style: DikcizStyle,
    ): DikcizStyleDefaults {
        return when (scope) {
            AppearanceDefaultScope.Widget -> copy(widget = style)
        }
    }

    private fun showWidgetLocksDialog(page: HomePage) {
        val widgetLockInputs = linkedMapOf<String, Switch>()
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), NO_PADDING)
        }
        page.widgets.forEach { widget ->
            val input = Switch(this).apply {
                isChecked = widget.locked
                text = widgetReferenceLabel(page, widget)
                contentDescription = this@DikcizHomeActivity.getString(
                    R.string.dikciz_widget_lock_input_description,
                    widget.title,
                )
            }
            widgetLockInputs[widget.id] = input
            list.addView(input)
        }
        val scroll = ScrollView(this).apply { addView(list) }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_widget_locks_title)
            .setView(scroll)
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .setPositiveButton(R.string.dikciz_dialog_save, null)
            .create()
        showActiveDialog(
            dialog,
            onShown = {
                widgetLockInputs.forEach { (widgetID, input) ->
                    registerAutomationView(widgetLocksInputSemanticID(page.id, widgetID), input)
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                    registerAutomationView(widgetLocksSaveSemanticID(page.id), this)
                    setOnClickListener {
                        saveWidgetLocks(
                            page.id,
                            widgetLockInputs.mapValues { (_, input) -> input.isChecked },
                        )
                        dialog.dismiss()
                    }
                }
            },
            onDismiss = ::clearWidgetLocksAutomationViews,
        )
    }

    private fun showCommandSheet(page: HomePage) {
        val searchInput = EditText(this).apply {
            hint = getString(R.string.dikciz_command_sheet_search)
            contentDescription = getString(R.string.dikciz_command_sheet_search)
        }
        registerAutomationView(commandSheetSearchSemanticID(), searchInput)
        val entryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), NO_PADDING)
            addView(searchInput)
            addView(ScrollView(this@DikcizHomeActivity).apply {
                addView(entryContainer)
            })
        }
        val entries = commandSheetEntries(page)
        lateinit var dialog: AlertDialog
        fun selectEntry(entry: CommandSheetEntry) {
            dialog.dismiss()
            when (entry) {
                is ManagePageCommand -> showPageMenu(entry.page)
                SettingsCommand -> showSettingsDialog()
                is SelectPageCommand -> selectPage(entry.page.id)
                is LaunchableAppCommand -> showLaunchableAppActions(page, entry.application)
            }
        }
        fun renderEntries(query: String) {
            val matchingEntries = entries.filter { it.matches(query) }
            clearCommandSheetEntryAutomationViews()
            entryContainer.removeAllViews()
            if (matchingEntries.isEmpty()) {
                entryContainer.addView(TextView(this).apply {
                    text = getString(R.string.dikciz_command_sheet_no_results)
                    setTextColor(getColor(R.color.dikciz_muted_foreground))
                })
            } else {
                matchingEntries.forEach { entry ->
                    val label = entry.label(this@DikcizHomeActivity)
                    entryContainer.addView(Button(this).apply {
                        text = label
                        contentDescription = label
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        isAllCaps = false
                        entry.icon?.let { icon ->
                            setCompoundDrawablesRelative(
                                boundedCommandSheetIcon(icon),
                                null,
                                null,
                                null,
                            )
                            compoundDrawablePadding = densityPixels(COMMAND_SHEET_APP_ICON_GAP_DP)
                        }
                        setOnClickListener { selectEntry(entry) }
                        registerAutomationView(commandSheetEntrySemanticID(entry), this)
                    })
                }
            }
            dikcizLogger.debug(
                EVENT_COMMAND_SHEET_FILTERED,
                mapOf(FIELD_RESULT_COUNT to matchingEntries.size),
            )
            publishAutomationUIRendered()
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_command_sheet_title)
            .setView(form)
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .create()
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                renderEntries(value?.toString().orEmpty())
            }

            override fun afterTextChanged(value: Editable?) = Unit
        })
        renderEntries(EMPTY_QUERY)
        dikcizLogger.info(
            EVENT_COMMAND_SHEET_OPENED,
            mapOf(FIELD_ENTRY_COUNT to entries.size),
        )
        showActiveDialog(dialog) {
            clearCommandSheetAutomationViews()
            publishAutomationUIRendered()
        }
    }

    private fun boundedCommandSheetIcon(icon: Drawable): Drawable {
        val boundedIcon = icon.constantState?.newDrawable(resources)?.mutate() ?: icon.mutate()
        val iconSizePixels = densityPixels(COMMAND_SHEET_APP_ICON_SIZE_DP)
        boundedIcon.setBounds(NO_PADDING, NO_PADDING, iconSizePixels, iconSizePixels)
        return boundedIcon
    }

    private fun commandSheetEntries(page: HomePage): List<CommandSheetEntry> {
        val configuration = homeConfiguration ?: return emptyList()
        val appEntries = launchableApps().map(::LaunchableAppCommand)
        val pageEntries = configuration.pages.map(::SelectPageCommand)
        return listOf(SettingsCommand, ManagePageCommand(page)) + pageEntries + appEntries
    }

    private fun launchableApps(): List<DikcizLaunchableApp> {
        return try {
            DikcizAppCatalogue.load(packageManager, packageName).also { applications ->
                dikcizLogger.debug(
                    EVENT_APP_CATALOGUE_LOADED,
                    mapOf(FIELD_ENTRY_COUNT to applications.size),
                )
            }
        } catch (exception: SecurityException) {
            dikcizLogger.warn(
                EVENT_APP_CATALOGUE_LOAD_REJECTED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            emptyList()
        }
    }

    private fun showLaunchableAppActions(
        page: HomePage,
        application: DikcizLaunchableApp,
    ) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(application.label)
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .setNeutralButton(R.string.dikciz_app_action_launch) { _, _ ->
                launchExplicitComponent(application.component)
            }
            .setPositiveButton(R.string.dikciz_app_action_add_shortcut) { _, _ ->
                addAppShortcut(page.id, application)
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.let { launchButton ->
                registerAutomationView(commandAppLaunchSemanticID(application), launchButton)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { addButton ->
                registerAutomationView(commandAppAddShortcutSemanticID(application), addButton)
            }
            publishAutomationUIRendered()
        }
        showActiveDialog(dialog) {
            clearCommandSheetAutomationViews()
            publishAutomationUIRendered()
        }
    }

    private fun addAppShortcut(pageID: String, application: DikcizLaunchableApp): Boolean {
        return addAppShortcut(
            target = WidgetInsertionTarget(pageID, pageWidgets(pageID).size),
            application = application,
        )
    }

    private fun addAppShortcutToSelectedPage(application: DikcizLaunchableApp): Boolean {
        val pageID = homeConfiguration?.selectedPage()?.id ?: return false
        return addAppShortcut(pageID, application)
    }

    private fun addAppShortcut(
        target: WidgetInsertionTarget,
        application: DikcizLaunchableApp,
    ): Boolean {
        val configuration = homeConfiguration ?: return false
        val component = appShortcutComponent(application) ?: return false
        val title = application.label.take(configuration.limits.maxTitleCharacters).trim()
        if (title.isEmpty()) {
            dikcizLogger.warn(
                EVENT_APP_SHORTCUT_REJECTED,
                mapOf(FIELD_REASON to REASON_EMPTY_APP_LABEL),
            )
            Toast.makeText(this, R.string.dikciz_app_shortcut_rejected, Toast.LENGTH_LONG).show()
            return false
        }
        val widgetID = newWidgetIDOrShowError(title) ?: return false
        val shortcut = AppHomeWidget(
            id = widgetID,
            title = title,
            component = component,
            displayStyle = AppWidgetDisplayStyle.IconWithLabel,
            enabled = true,
            cell = requestedCell(DikcizWidgetSpans.APP),
        )
        if (!insertWidget(target, shortcut)) {
            return false
        }
        dikcizLogger.info(
            EVENT_APP_SHORTCUT_CREATED,
            mapOf(
                FIELD_COMPONENT_PACKAGE to application.component.packageName,
                FIELD_DISPLAY_STYLE to shortcut.displayStyle.persistedValue,
            ),
        )
        return true
    }

    private fun appShortcutComponent(application: DikcizLaunchableApp): String? {
        val configuration = homeConfiguration ?: return null
        val component = application.component.flattenToShortString()
        if (component.length <= configuration.limits.maxComponentCharacters) {
            return component
        }
        dikcizLogger.warn(
            EVENT_APP_SHORTCUT_REJECTED,
            mapOf(FIELD_REASON to REASON_COMPONENT_TOO_LONG),
        )
        Toast.makeText(this, R.string.dikciz_app_shortcut_rejected, Toast.LENGTH_LONG).show()
        return null
    }

    private fun registerAppDrawerAutomationViews() {
        registerAutomationView(SEMANTIC_DRAWER, appDrawer)
        registerAutomationView(SEMANTIC_DRAWER_SEARCH, appDrawer.searchInput)
        registerAutomationView(SEMANTIC_DRAWER_EMPTY, appDrawer.emptyStateView)
        registerAutomationView(SEMANTIC_DRAWER_CLOSE, appDrawer.closeButton)
    }

    private fun bindAppDrawer() {
        appDrawer.onCloseRequested = ::closeAppDrawer
        appDrawer.onLaunchRequested = { application ->
            runAppActionFromUI(application, DikcizAppActionType.Launch)
        }
        appDrawer.onActionsRequested = ::showAppDrawerActions
        appDrawer.onRowBound = { application, row, actionsButton ->
            registerAutomationView(drawerAppSemanticID(application), row)
            registerAutomationView(drawerAppActionsSemanticID(application), actionsButton)
        }
        appDrawer.onResultsRendered = { resultCount ->
            // The list recycles, so only rows it has actually bound may claim a semantic
            // node. Drop the previous result's rows here and let the adapter re-register
            // whatever the new result binds.
            clearAppDrawerRowAutomationViews()
            dikcizLogger.debug(
                EVENT_APP_DRAWER_FILTERED,
                mapOf(FIELD_RESULT_COUNT to resultCount),
            )
            publishAutomationUIRendered()
        }
    }

    private fun configureLauncherControl() {
        launcherControl.setControlsVisible(!isSafeMode)
        registerAutomationView(SEMANTIC_LAUNCHER_CONTROL, launcherControl.controlButton)
        registerAutomationView(SEMANTIC_PAGE_SEARCH, launcherControl.pageSearchButton)
        if (appDrawer.isOpen) {
            registerAppDrawerAutomationViews()
        }
    }

    private fun homeGestureActivationBounds(): List<Rect> {
        // Only the horizontal page rail starts the drawer gesture. The page
        // viewport keeps its upward drag for scrolling page content.
        val bounds = mutableListOf<Rect>()
        Rect().takeIf(horizontalPageIndicator::getGlobalVisibleRect)?.let(bounds::add)
        return bounds
    }

    private fun isHomeGestureEnabled(): Boolean {
        if (isSafeMode || isStartupBrandVisible || isConfigurationErrorVisible) {
            return false
        }
        if (homeConfiguration == null || appDrawer.isOpen) {
            return false
        }
        return activeDialog == null &&
            activeWidgetEdit == null &&
            activeWidgetResize == null &&
            activeWidgetMove == null &&
            armedWidgetMoveID == null
    }

    /**
     * The page child already received the down event before the gesture threshold was
     * reached, so it needs an explicit cancel or it stays visually pressed.
     */
    private fun cancelPendingTouchDelivery(event: MotionEvent) {
        val cancellation = MotionEvent.obtain(event)
        cancellation.action = MotionEvent.ACTION_CANCEL
        super.dispatchTouchEvent(cancellation)
        cancellation.recycle()
    }

    private fun dismissStartupBrandOnTouch(event: MotionEvent): Boolean {
        if (!isStartupBrandVisible || event.actionMasked != MotionEvent.ACTION_DOWN) {
            return false
        }
        dismissStartupBrand()
        dikcizLogger.info(
            EVENT_STARTUP_BRAND_DISMISSED,
            mapOf(FIELD_SOURCE to STARTUP_DISMISS_SOURCE_TOUCH),
        )
        return true
    }

    /**
     * Builds a platform dialog on the Dikciz dark panel theme. Without an explicit theme
     * these sheets resolve the generic platform alert surface, which reads as unfinished
     * plumbing next to the launcher's own chrome.
     */
    private fun panelDialogBuilder(): AlertDialog.Builder {
        return AlertDialog.Builder(this, R.style.DikcizPanelDialog)
    }

    private fun createPanelColumn(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), standardPadding())
        }
    }

    private fun createPanelSubtitle(value: String): TextView {
        return TextView(this).apply {
            contentDescription = value
            setPadding(
                NO_PADDING,
                NO_PADDING,
                NO_PADDING,
                densityPixels(PANEL_SUBTITLE_GAP_DP),
            )
            setTextColor(getColor(R.color.dikciz_muted_foreground))
            text = value
        }
    }

    private fun createPanelRouteButton(
        label: String,
        iconResourceID: Int,
        isDestructive: Boolean,
        onSelected: () -> Unit,
    ): Button {
        val textColorResourceID = if (isDestructive) {
            R.color.dikciz_destructive_foreground
        } else {
            R.color.dikciz_foreground
        }
        return Button(this).apply {
            background = getDrawable(R.drawable.dikciz_panel_route)
            compoundDrawablePadding = densityPixels(PANEL_ROUTE_ICON_GAP_DP)
            contentDescription = label
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            isAllCaps = false
            minHeight = densityPixels(PANEL_ROUTE_HEIGHT_DP)
            minimumHeight = densityPixels(PANEL_ROUTE_HEIGHT_DP)
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                iconResourceID,
                NO_DRAWABLE,
                NO_DRAWABLE,
                NO_DRAWABLE,
            )
            setPadding(
                densityPixels(PANEL_ROUTE_HORIZONTAL_PADDING_DP),
                NO_PADDING,
                densityPixels(PANEL_ROUTE_HORIZONTAL_PADDING_DP),
                NO_PADDING,
            )
            setTextColor(getColor(textColorResourceID))
            text = label
            textSize = PANEL_ROUTE_TEXT_SIZE_SP
            setOnClickListener { onSelected() }
        }
    }

    private fun panelRouteLayoutParameters(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = densityPixels(PANEL_ROUTE_GAP_DP) }
    }

    private fun showLauncherControlSheet() {
        val page = homeConfiguration?.selectedPage() ?: return
        lateinit var dialog: AlertDialog
        val actionButtons = mutableMapOf<DikcizLauncherControlAction, Button>()
        val actionList = createPanelColumn().apply {
            // The sheet is where the selected page gets named. The home screen itself
            // carries page position through the indicator rails, not through a label.
            addView(
                createPanelSubtitle(
                    getString(R.string.dikciz_page_menu_title, page.title, page.scriptPageAddress()),
                ),
            )
            DikcizLauncherControlAction.entries.forEach { action ->
                val button = createPanelRouteButton(
                    label = getString(action.labelResourceID),
                    iconResourceID = action.iconResourceID,
                    isDestructive = false,
                ) {
                    dialog.dismiss()
                    performLauncherControlAction(page, action)
                }
                actionButtons[action] = button
                addView(button, panelRouteLayoutParameters())
            }
        }
        dialog = panelDialogBuilder()
            .setTitle(R.string.dikciz_launcher_control_title)
            .setView(ScrollView(this).apply { addView(actionList) })
            .setNegativeButton(R.string.dikciz_dialog_close, null)
            .create()
        dikcizLogger.info(EVENT_LAUNCHER_CONTROL_OPENED, mapOf(FIELD_SELECTED_PAGE_ID to page.id))
        showActiveDialog(
            dialog,
            onShown = {
                actionButtons.forEach { (action, button) ->
                    registerAutomationView(launcherControlActionSemanticID(action), button)
                }
                publishAutomationUIRendered()
            },
            onDismiss = {
                clearLauncherControlAutomationViews()
                publishAutomationUIRendered()
            },
        )
    }

    /**
     * Opens the page jump list. Pages are addressed by their `1H1V` coordinate everywhere
     * else in the product, so the name a page carries needs one place where it is the way
     * in. Matching accepts either half of the printed entry, the name or the address.
     */
    private fun showPageSearchSheet() {
        val configuration = homeConfiguration ?: return
        val emptyNotice = createPanelSubtitle(getString(R.string.dikciz_page_search_empty))
        val queryInput = EditText(this).apply {
            hint = getString(R.string.dikciz_page_search_query)
            contentDescription = getString(R.string.dikciz_page_search_query)
        }
        val resultContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), NO_PADDING)
            addView(queryInput)
            addView(ScrollView(this@DikcizHomeActivity).apply { addView(resultContainer) })
        }
        lateinit var dialog: AlertDialog
        fun renderResults(query: String) {
            clearPageSearchAutomationViews()
            registerAutomationView(SEMANTIC_PAGE_SEARCH_QUERY, queryInput)
            resultContainer.removeAllViews()
            val matchingPages = homeConfiguration?.pages.orEmpty().filter { page ->
                pageMatchesSearch(page, query)
            }
            if (matchingPages.isEmpty()) {
                resultContainer.addView(emptyNotice)
                registerAutomationView(SEMANTIC_PAGE_SEARCH_EMPTY, emptyNotice)
                return
            }
            matchingPages.forEach { page ->
                val button = createPanelRouteButton(
                    label = getString(
                        R.string.dikciz_page_search_entry,
                        page.title,
                        page.scriptPageAddress(),
                    ),
                    iconResourceID = R.drawable.dikciz_icon_route_page,
                    isDestructive = false,
                ) {
                    dialog.dismiss()
                    selectPage(page.id)
                }
                resultContainer.addView(button, panelRouteLayoutParameters())
                registerAutomationView(pageSearchEntrySemanticID(page.id), button)
            }
        }
        dialog = panelDialogBuilder()
            .setTitle(R.string.dikciz_page_search_title)
            .setView(form)
            .setNegativeButton(R.string.dikciz_dialog_close, null)
            .create()
        queryInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                renderResults(value?.toString().orEmpty())
                publishAutomationUIRendered()
            }

            override fun afterTextChanged(value: Editable?) = Unit
        })
        dikcizLogger.info(
            EVENT_PAGE_SEARCH_OPENED,
            mapOf(FIELD_PAGE_COUNT to configuration.pages.size),
        )
        showActiveDialog(
            dialog,
            onShown = { renderResults(EMPTY_QUERY) },
            onDismiss = {
                clearPageSearchAutomationViews()
                publishAutomationUIRendered()
            },
        )
    }

    private fun pageMatchesSearch(page: HomePage, query: String): Boolean {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return true
        }
        return page.title.contains(trimmedQuery, ignoreCase = true) ||
            page.scriptPageAddress().contains(trimmedQuery, ignoreCase = true)
    }

    private fun pageSearchEntrySemanticID(pageID: String): String =
        "$SEMANTIC_PAGE_SEARCH_PAGE_PREFIX$pageID"

    private fun clearPageSearchAutomationViews() {
        automationViews.keys.removeAll { key -> key.startsWith(SEMANTIC_PAGE_SEARCH_PREFIX) }
    }

    private fun performLauncherControlAction(
        page: HomePage,
        action: DikcizLauncherControlAction,
    ) {
        dikcizLogger.info(
            EVENT_LAUNCHER_CONTROL_ACTION,
            mapOf(FIELD_ACTION to action.semanticAction),
        )
        when (action) {
            DikcizLauncherControlAction.AppDrawer -> openAppDrawer(DRAWER_SOURCE_CONTROL)
            DikcizLauncherControlAction.AddToPage -> showWidgetPicker(page, page.widgets.size)
            DikcizLauncherControlAction.ManagePage -> showPageMenu(page)
            DikcizLauncherControlAction.DikcizSettings -> showSettingsDialog()
        }
    }

    private fun openAppDrawer(source: String) {
        if (isSafeMode || appDrawer.isOpen) {
            return
        }
        val applications = launchableApps()
        appDrawer.open(applications)
        registerAppDrawerAutomationViews()
        dikcizLogger.info(
            EVENT_APP_DRAWER_OPENED,
            mapOf(
                FIELD_ENTRY_COUNT to applications.size,
                FIELD_SOURCE to source,
            ),
        )
        publishAutomationUIRendered()
    }

    private fun closeAppDrawer() {
        if (!appDrawer.isOpen) {
            return
        }
        appDrawer.close()
        clearAppDrawerAutomationViews()
        dikcizLogger.info(EVENT_APP_DRAWER_CLOSED)
        publishAutomationUIRendered()
    }

    private fun showAppDrawerActions(application: DikcizLaunchableApp) {
        lateinit var dialog: AlertDialog
        val actionButtons = mutableMapOf<DikcizAppActionType, Button>()
        val actionList = createPanelColumn().apply {
            addView(
                createPanelSubtitle(
                    getString(
                        R.string.dikciz_app_action_subtitle,
                        application.component.packageName,
                    ),
                ),
            )
            DikcizAppActionType.entries.forEach { action ->
                val button = createPanelRouteButton(
                    label = getString(action.labelResourceID),
                    iconResourceID = NO_DRAWABLE,
                    isDestructive = action.isDestructive,
                ) {
                    dialog.dismiss()
                    beginAppAction(application, action)
                }
                actionButtons[action] = button
                addView(button, panelRouteLayoutParameters())
            }
        }
        dialog = panelDialogBuilder()
            .setTitle(getString(R.string.dikciz_app_action_title, application.label))
            .setView(ScrollView(this).apply { addView(actionList) })
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .create()
        showActiveDialog(
            dialog,
            onShown = {
                actionButtons.forEach { (action, button) ->
                    registerAutomationView(appActionSemanticID(action), button)
                }
                publishAutomationUIRendered()
            },
            onDismiss = {
                clearAppActionAutomationViews()
                publishAutomationUIRendered()
            },
        )
    }

    private fun beginAppAction(
        application: DikcizLaunchableApp,
        action: DikcizAppActionType,
    ) {
        if (action == DikcizAppActionType.ForceStop && !appActionRunner.isRootAvailable()) {
            showForceStopRootRequirement(application)
            return
        }
        if (!action.isDestructive) {
            runAppActionFromUI(application, action)
            return
        }
        confirmDestructiveAppAction(application, action)
    }

    /**
     * Android cannot stop a third-party app for an unprivileged launcher. Offer the real
     * App info route rather than a request that would silently do nothing.
     */
    private fun showForceStopRootRequirement(application: DikcizLaunchableApp) {
        dikcizLogger.warn(
            EVENT_APP_ACTION_ROOT_REQUIRED,
            mapOf(FIELD_COMPONENT_PACKAGE to application.component.packageName),
        )
        val dialog = panelDialogBuilder()
            .setTitle(getString(R.string.dikciz_app_action_title, application.label))
            .setMessage(R.string.dikciz_app_action_force_stop_root_required)
            .setNegativeButton(R.string.dikciz_dialog_close, null)
            .setPositiveButton(R.string.dikciz_app_action_force_stop_open_app_info) { _, _ ->
                runAppActionFromUI(application, DikcizAppActionType.AppInfo)
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { appInfoButton ->
                registerAutomationView(SEMANTIC_APP_ACTION_ROOT_APP_INFO, appInfoButton)
            }
            publishAutomationUIRendered()
        }
        showActiveDialog(dialog) {
            clearAppActionAutomationViews()
            publishAutomationUIRendered()
        }
    }

    private fun confirmDestructiveAppAction(
        application: DikcizLaunchableApp,
        action: DikcizAppActionType,
    ) {
        val messageResourceID = when (action) {
            DikcizAppActionType.Uninstall -> R.string.dikciz_app_action_confirm_uninstall
            else -> R.string.dikciz_app_action_confirm_force_stop
        }
        val dialog = panelDialogBuilder()
            .setTitle(getString(action.labelResourceID))
            .setMessage(
                getString(
                    messageResourceID,
                    application.label,
                    application.component.packageName,
                ),
            )
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .setPositiveButton(R.string.dikciz_app_action_confirm) { _, _ ->
                runAppActionFromUI(application, action)
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { confirmButton ->
                registerAutomationView(SEMANTIC_APP_ACTION_CONFIRM, confirmButton)
            }
            publishAutomationUIRendered()
        }
        showActiveDialog(dialog) {
            clearAppActionAutomationViews()
            publishAutomationUIRendered()
        }
    }

    private fun runAppActionFromUI(
        application: DikcizLaunchableApp,
        action: DikcizAppActionType,
    ) {
        val result = appActionRunner.run(action, application)
        Toast.makeText(this, appActionOutcomeMessage(result, application), Toast.LENGTH_LONG).show()
        if (action == DikcizAppActionType.Launch && result.isSuccessful) {
            closeAppDrawer()
        }
        if (action == DikcizAppActionType.AddShortcut && result.isSuccessful) {
            closeAppDrawer()
        }
        publishAutomationUIRendered()
    }

    private fun appActionOutcomeMessage(
        result: DikcizAppActionResult,
        application: DikcizLaunchableApp,
    ): String {
        val messageResourceID = when (result.outcome) {
            DikcizAppActionOutcome.LAUNCHED -> R.string.dikciz_app_action_outcome_launched
            DikcizAppActionOutcome.SHORTCUT_ADDED -> R.string.dikciz_app_action_outcome_shortcut_added
            DikcizAppActionOutcome.SHORTCUT_REJECTED -> R.string.dikciz_app_action_outcome_shortcut_rejected
            DikcizAppActionOutcome.APP_INFO_OPENED -> R.string.dikciz_app_action_outcome_app_info_opened
            DikcizAppActionOutcome.UNINSTALL_REQUESTED -> R.string.dikciz_app_action_outcome_uninstall_requested
            DikcizAppActionOutcome.UNINSTALL_NOT_PERMITTED ->
                R.string.dikciz_app_action_outcome_uninstall_not_permitted

            DikcizAppActionOutcome.FORCE_STOPPED -> R.string.dikciz_app_action_outcome_force_stopped
            DikcizAppActionOutcome.FORCE_STOP_FAILED -> R.string.dikciz_app_action_outcome_force_stop_failed
            DikcizAppActionOutcome.ROOT_UNAVAILABLE -> R.string.dikciz_app_action_force_stop_root_required
            DikcizAppActionOutcome.TARGET_UNAVAILABLE -> R.string.dikciz_app_action_outcome_target_unavailable
            else -> R.string.dikciz_app_action_outcome_android_rejected
        }
        if (messageResourceID == R.string.dikciz_app_action_force_stop_root_required) {
            return getString(messageResourceID)
        }
        return getString(messageResourceID, application.label)
    }

    private fun armWidgetMove(widget: HomeWidget, widgetView: View) {
        if (selectedPageOrWidgetIsLocked(widget.id)) {
            return
        }
        dismissWidgetEditActions()
        clearPendingWidgetMove()
        armedWidgetMoveID = widget.id
        widgetContainer.isGridGuideVisible = true
        (widgetView as? DikcizWidgetFrameLayout)?.resizeOverlay = createWidgetResizeOverlay(
            widget = widget,
            resizeHandles = emptySet(),
        )
        Toast.makeText(
            this,
            getString(R.string.dikciz_widget_move_armed, widget.title),
            Toast.LENGTH_LONG,
        ).show()
        dikcizLogger.info(EVENT_WIDGET_MOVE_ARMED, mapOf(FIELD_WIDGET_ID to widget.id))
        publishAutomationUIRendered()
    }

    /**
     * Once Move is armed the whole widget is the drag target, so a pointer that lands on it
     * starts the same move the narrow top edge starts. A touch anywhere else disarms.
     */
    private fun routeArmedWidgetMoveTouch(event: MotionEvent): Boolean {
        val widgetID = armedWidgetMoveID ?: return false
        if (event.actionMasked != MotionEvent.ACTION_DOWN) {
            return false
        }
        val widgetView = automationViews[widgetSemanticID(widgetID)]
        val widget = homeConfiguration?.selectedPage()?.widgets?.firstOrNull { it.id == widgetID }
        if (widgetView == null || widget == null || !isTouchInsideView(event, widgetView)) {
            clearArmedWidgetMove()
            return false
        }
        clearArmedWidgetMove()
        if (!startWidgetMove(widget, widgetView, event.rawX, event.rawY)) {
            return false
        }
        setWidgetTouchInterceptionBlocked(widgetView, true)
        return true
    }

    private fun clearArmedWidgetMove() {
        val widgetID = armedWidgetMoveID ?: return
        armedWidgetMoveID = null
        if (activeWidgetMove == null) {
            widgetContainer.isGridGuideVisible = false
        }
        (automationViews[widgetSemanticID(widgetID)] as? DikcizWidgetFrameLayout)?.resizeOverlay = null
    }

    private fun launcherControlActionSemanticID(action: DikcizLauncherControlAction): String =
        "$SEMANTIC_LAUNCHER_CONTROL_PREFIX${action.semanticAction}"

    private fun appGroupMemberSemanticID(widgetID: String, component: String): String =
        "${appGroupSemanticID(widgetID)}$SEMANTIC_APP_GROUP_MEMBER_SEPARATOR$component"

    private fun appActionSemanticID(action: DikcizAppActionType): String =
        "$SEMANTIC_APP_ACTION_PREFIX${action.semanticAction}"

    private fun drawerAppSemanticID(application: DikcizLaunchableApp): String =
        "$SEMANTIC_DRAWER_APP_PREFIX${application.component.flattenToString()}"

    private fun drawerAppActionsSemanticID(application: DikcizLaunchableApp): String =
        "$SEMANTIC_DRAWER_APP_ACTIONS_PREFIX${application.component.flattenToString()}"

    private fun moveSemanticID(widgetID: String): String =
        "$SEMANTIC_WIDGET_PREFIX$widgetID$SEMANTIC_WIDGET_ACTION_SEPARATOR$SEMANTIC_ACTION_MOVE"

    private fun clearLauncherControlAutomationViews() {
        automationViews.keys.removeAll { key -> key.startsWith(SEMANTIC_LAUNCHER_CONTROL_PREFIX) }
    }

    private fun clearAppActionAutomationViews() {
        automationViews.keys.removeAll { key -> key.startsWith(SEMANTIC_APP_ACTION_PREFIX) }
    }

    private fun clearAppDrawerAutomationViews() {
        automationViews.keys.removeAll { key -> key.startsWith(SEMANTIC_DRAWER) }
    }

    private fun clearAppDrawerRowAutomationViews() {
        automationViews.keys.removeAll { key ->
            key.startsWith(SEMANTIC_DRAWER_APP_PREFIX) ||
                key.startsWith(SEMANTIC_DRAWER_APP_ACTIONS_PREFIX)
        }
    }

    private fun pageWidgets(pageID: String): List<HomeWidget> {
        return homeConfiguration?.pages?.firstOrNull { it.id == pageID }?.widgets.orEmpty()
    }

    private fun pageIndicatorEdgeSemanticID(
        axis: PageNavigationAxis,
        side: PageInsertionSide,
    ): String {
        val indicatorSemanticID = when (axis) {
            PageNavigationAxis.Horizontal -> SEMANTIC_ID_HORIZONTAL_PAGE_INDICATOR
            PageNavigationAxis.Vertical -> SEMANTIC_ID_VERTICAL_PAGE_INDICATOR
        }
        return "$indicatorSemanticID$SEMANTIC_PAGE_INDICATOR_EDGE_PREFIX${side.semanticID}"
    }

    private fun pageInsertionTargetForSemanticID(semanticID: String): PageInsertionTarget? {
        return PageNavigationAxis.entries.firstNotNullOfOrNull { axis ->
            PageInsertionSide.entries.firstOrNull { side ->
                semanticID == pageIndicatorEdgeSemanticID(axis, side)
            }?.let { side -> PageInsertionTarget(axis, side) }
        }
    }

    private fun showRenamePageDialog(page: HomePage) {
        val titleInput = EditText(this).apply {
            hint = getString(R.string.dikciz_dialog_title_hint)
            setText(page.title)
            setSelectAllOnFocus(true)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_dialog_rename)
            .setView(titleInput)
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .setPositiveButton(R.string.dikciz_dialog_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = titleInput.text.toString()
                if (updateConfiguration { configuration ->
                        configuration.replacePage(page.id) { currentPage ->
                            currentPage.copy(title = title)
                        }
                    }
                ) {
                    dikcizLogger.info(EVENT_PAGE_RENAMED)
                }
                dialog.dismiss()
            }
        }
        showActiveDialog(
            dialog,
            onShown = {
                clearPageRenameAutomationViews()
                registerAutomationView(
                    pageRenameControlSemanticID(page.id, SEMANTIC_PAGE_RENAME_TITLE),
                    titleInput,
                )
                registerAutomationView(
                    pageRenameControlSemanticID(page.id, SEMANTIC_PAGE_RENAME_SAVE),
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                )
            },
            onDismiss = ::clearPageRenameAutomationViews,
        )
    }

    private fun showSettingsDialog() {
        val configuration = homeConfiguration ?: return
        if (isSafeMode) {
            showResetConfirmation()
            return
        }
        val titleInputs = linkedMapOf<String, EditText>()
        val gridInputs = linkedMapOf<DikcizGridSetting, EditText>()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), NO_PADDING)
            addView(createWidgetTitle(getString(R.string.dikciz_settings_appearance)))
            AppearanceDefaultScope.entries.forEach { scope ->
                addView(
                    Button(this@DikcizHomeActivity).apply {
                        text = getString(scope.titleResourceID)
                        contentDescription = text
                        setOnClickListener { showDefaultAppearanceDialog(scope) }
                        registerAutomationView(settingsAppearanceSemanticID(scope), this)
                    },
                )
            }
            addView(createWidgetTitle(getString(R.string.dikciz_settings_themes)))
            addView(
                Button(this@DikcizHomeActivity).apply {
                    text = getString(
                        R.string.dikciz_settings_themes_button,
                        themeSelectionDescription(configuration),
                    )
                    contentDescription = text
                    setOnClickListener { showThemePicker() }
                    registerAutomationView(SEMANTIC_SETTINGS_THEMES, this)
                },
            )
            addView(createWidgetTitle(getString(R.string.dikciz_settings_scripts)))
            addView(
                Button(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_settings_scripts_button)
                    contentDescription = text
                    setOnClickListener { showLuaScriptManager() }
                    registerAutomationView(SEMANTIC_SETTINGS_SCRIPTS, this)
                },
            )
            addView(
                Button(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_settings_script_logs_button)
                    contentDescription = text
                    setOnClickListener { showScriptLogViewer() }
                    registerAutomationView(SEMANTIC_SETTINGS_SCRIPT_LOGS, this)
                },
            )
            addView(createWidgetTitle(getString(R.string.dikciz_settings_automation)))
            addView(
                Button(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_settings_automation_button)
                    contentDescription = text
                    setOnClickListener { showAutomationSettings() }
                    registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION, this)
                },
            )
            addView(createWidgetTitle(getString(R.string.dikciz_settings_page_grid)))
            addView(
                TextView(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_settings_page_grid_hint)
                    contentDescription = text
                    setTextColor(getColor(R.color.dikciz_muted_foreground))
                },
            )
            DikcizGridSetting.entries.forEach { setting ->
                val input = EditText(this@DikcizHomeActivity).apply {
                    contentDescription = getString(setting.labelResourceID)
                    hint = getString(setting.labelResourceID)
                    inputType = InputType.TYPE_CLASS_NUMBER
                    setText(setting.read(configuration.nativeGrid).toString())
                    setSelectAllOnFocus(true)
                }
                gridInputs[setting] = input
                addView(
                    LinearLayout(this@DikcizHomeActivity).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        orientation = LinearLayout.HORIZONTAL
                        addView(
                            TextView(this@DikcizHomeActivity).apply {
                                text = getString(setting.labelResourceID)
                                setTextColor(getColor(R.color.dikciz_foreground))
                            },
                            LinearLayout.LayoutParams(
                                NO_PADDING,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                SETTINGS_TITLE_WEIGHT,
                            ),
                        )
                        addView(
                            input,
                            LinearLayout.LayoutParams(
                                NO_PADDING,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                SETTINGS_TITLE_WEIGHT,
                            ),
                        )
                    },
                )
                registerAutomationView(settingsGridSemanticID(setting), input)
            }
            addView(
                Button(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_settings_save_page_grid)
                    contentDescription = text
                    setOnClickListener { saveSettingsGrid(gridInputs) }
                    registerAutomationView(SEMANTIC_SETTINGS_SAVE_PAGE_GRID, this)
                },
            )
            addView(createWidgetTitle(getString(R.string.dikciz_settings_pages)))
        }
        configuration.pages.forEach { page ->
            val input = EditText(this).apply {
                contentDescription = getString(R.string.dikciz_settings_page_name)
                hint = getString(R.string.dikciz_settings_page_name)
                setText(page.title)
                setSelectAllOnFocus(true)
            }
            titleInputs[page.id] = input
            form.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                addView(
                    input,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, SETTINGS_TITLE_WEIGHT),
                )
            })
            registerAutomationView(settingsPageTitleSemanticID(page.id), input)
        }
        lateinit var dialog: AlertDialog
        form.addView(Button(this).apply {
            text = getString(R.string.dikciz_settings_save_pages)
            setOnClickListener {
                if (saveSettingsPages(titleInputs)) {
                    dialog.dismiss()
                }
            }
            registerAutomationView(SEMANTIC_SETTINGS_SAVE_PAGES, this)
        })
        form.addView(Button(this).apply {
            text = getString(R.string.dikciz_settings_reset)
            setOnClickListener { showResetConfirmation() }
            registerAutomationView(SEMANTIC_SETTINGS_RESET, this)
        })
        form.addView(Button(this).apply {
            text = getString(R.string.dikciz_settings_safe_mode)
            setOnClickListener { restartInMode(true) }
            registerAutomationView(SEMANTIC_SETTINGS_SAFE_MODE, this)
        })
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_settings_title)
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton(R.string.dikciz_dialog_close, null)
            .create()
        dikcizLogger.info(
            EVENT_SETTINGS_OPENED,
            mapOf(FIELD_PAGE_COUNT to configuration.pages.size),
        )
        showActiveDialog(dialog) {
            if (activeDialog !== dialog) {
                return@showActiveDialog
            }
            clearSettingsAutomationViews()
            publishAutomationUIRendered()
        }
    }

    private fun showAutomationSettings() {
        val configuration = homeConfiguration ?: return
        val capabilities = configuration.automation.policies
            .filter(DikcizAutomationPolicy::enabled)
            .flatMap(DikcizAutomationPolicy::capabilities)
            .toSet()
        val actions = configuration.automation.policies
            .filter(DikcizAutomationPolicy::enabled)
            .flatMap(DikcizAutomationPolicy::actions)
            .toSet()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), NO_PADDING)
            addView(TextView(this@DikcizHomeActivity).apply {
                text = getString(R.string.dikciz_automation_message)
                contentDescription = text
            })
            addView(Button(this@DikcizHomeActivity).apply {
                text = getString(R.string.dikciz_automation_full_access_setup)
                contentDescription = text
                setOnClickListener { showFullDeviceAccessSetup() }
                registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_FULL_ACCESS, this)
            })
            addView(Button(this@DikcizHomeActivity).apply {
                text = getString(R.string.dikciz_remote_auth_open)
                contentDescription = text
                setOnClickListener { showRemoteAccessAuthSettings() }
                registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_REMOTE_AUTH, this)
            })
            if (
                DikcizAutomationCapability.ClipboardMetadata in capabilities ||
                DikcizAutomationCapability.ClipboardContent in capabilities
            ) {
                addView(TextView(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_automation_clipboard_message)
                    contentDescription = text
                })
            }
            if (
                DikcizAutomationCapability.LocationApproximate in capabilities ||
                DikcizAutomationCapability.LocationPrecise in capabilities
            ) {
                addView(Button(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_automation_grant_location)
                    setOnClickListener {
                        requestPermissions(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ),
                            AUTOMATION_LOCATION_PERMISSION_REQUEST_CODE,
                        )
                    }
                    registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_GRANT_LOCATION, this)
                })
            }
            if (
                DikcizAutomationCapability.BluetoothState in capabilities &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
                addView(Button(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_automation_grant_bluetooth)
                    setOnClickListener {
                        requestPermissions(
                            arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                            AUTOMATION_BLUETOOTH_PERMISSION_REQUEST_CODE,
                        )
                    }
                    registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_GRANT_BLUETOOTH, this)
                })
            }
            if (
                DikcizAutomationCapability.CalendarEventsMetadata in capabilities ||
                DikcizAutomationCapability.CalendarEventsContent in capabilities
            ) {
                addView(Button(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_automation_grant_calendar)
                    setOnClickListener {
                        requestPermissions(
                            arrayOf(Manifest.permission.READ_CALENDAR),
                            AUTOMATION_CALENDAR_PERMISSION_REQUEST_CODE,
                        )
                    }
                    registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_GRANT_CALENDAR, this)
                })
            }
            if (DikcizAutomationCapability.ContactsMetadata in capabilities) {
                addView(Button(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_automation_grant_contacts)
                    setOnClickListener {
                        requestPermissions(
                            arrayOf(Manifest.permission.READ_CONTACTS),
                            AUTOMATION_CONTACTS_PERMISSION_REQUEST_CODE,
                        )
                    }
                    registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_GRANT_CONTACTS, this)
                })
            }
            if (DikcizAutomationCapability.PhoneState in capabilities) {
                addView(Button(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_automation_grant_phone_state)
                    setOnClickListener {
                        requestPermissions(
                            arrayOf(Manifest.permission.READ_PHONE_STATE),
                            AUTOMATION_PHONE_STATE_PERMISSION_REQUEST_CODE,
                        )
                    }
                    registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_GRANT_PHONE_STATE, this)
                })
            }
            if (
                DikcizAutomationCapability.SmsMetadata in capabilities ||
                DikcizAutomationCapability.SmsContent in capabilities
            ) {
                addView(TextView(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_automation_sms_restricted_permission_message)
                    contentDescription = text
                })
                addView(Button(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_automation_grant_sms)
                    setOnClickListener {
                        requestPermissions(
                            arrayOf(Manifest.permission.RECEIVE_SMS),
                            AUTOMATION_SMS_PERMISSION_REQUEST_CODE,
                        )
                    }
                    registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_GRANT_SMS, this)
                })
            }
            if (configuration.automation.requiresActivityRecognitionSensorAccess() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ) {
                addView(Button(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_automation_grant_step_sensor)
                    setOnClickListener {
                        requestPermissions(
                            arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                            AUTOMATION_SENSOR_PERMISSION_REQUEST_CODE,
                        )
                    }
                    registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_GRANT_STEP_SENSOR, this)
                })
            }
            if (configuration.automation.requiresHeartRateSensorAccess()) {
                if (DikcizAutomationSensorPermission.usesGranularHealthPermissions()) {
                    addView(Button(this@DikcizHomeActivity).apply {
                        text = getString(R.string.dikciz_automation_grant_heart_rate_sensor)
                        setOnClickListener { requestHealthConnectHeartRatePermission() }
                        registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_GRANT_HEART_RATE_SENSOR, this)
                    })
                    addView(Button(this@DikcizHomeActivity).apply {
                        text = getString(R.string.dikciz_automation_grant_heart_rate_background)
                        setOnClickListener { requestHealthConnectBackgroundPermission() }
                        registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_GRANT_HEART_RATE_BACKGROUND, this)
                    })
                } else {
                    addView(Button(this@DikcizHomeActivity).apply {
                        text = getString(R.string.dikciz_automation_grant_heart_rate_sensor)
                        setOnClickListener {
                            requestPermissions(
                                arrayOf(Manifest.permission.BODY_SENSORS),
                                AUTOMATION_SENSOR_PERMISSION_REQUEST_CODE,
                            )
                        }
                        registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_GRANT_HEART_RATE_SENSOR, this)
                    })
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        addView(TextView(this@DikcizHomeActivity).apply {
                            text = getString(
                                R.string.dikciz_automation_body_sensors_background_restricted_permission_message,
                            )
                            contentDescription = text
                        })
                        addView(Button(this@DikcizHomeActivity).apply {
                            text = getString(R.string.dikciz_automation_grant_heart_rate_background)
                            setOnClickListener {
                                requestPermissions(
                                    arrayOf(Manifest.permission.BODY_SENSORS_BACKGROUND),
                                    AUTOMATION_SENSOR_PERMISSION_REQUEST_CODE,
                                )
                            }
                            registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_GRANT_HEART_RATE_BACKGROUND, this)
                        })
                    }
                }
            }
            if (DikcizAutomationCapability.HealthSteps in capabilities) {
                addView(Button(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_automation_grant_health_steps)
                    setOnClickListener { requestHealthConnectStepsPermission() }
                    registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_GRANT_HEALTH_STEPS, this)
                })
                addView(Button(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_automation_grant_health_background)
                    setOnClickListener { requestHealthConnectBackgroundPermission() }
                    registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_GRANT_HEALTH_BACKGROUND, this)
                })
            }
            if (
                DikcizAutomationCapability.DeviceAdministration in capabilities ||
                DikcizAutomationActionCapability.LockDevice in actions
            ) {
                addView(Button(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_automation_grant_device_administration)
                    setOnClickListener { requestDeviceAdministration() }
                    registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_OPEN_DEVICE_ADMINISTRATION, this)
                })
            }
            if (
                (
                    configuration.logging.notifyOnScriptError ||
                        DikcizAutomationActionCapability.PostNotification in actions
                    ) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ) {
                addView(Button(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_automation_grant_notifications)
                    setOnClickListener {
                        requestPermissions(
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            AUTOMATION_NOTIFICATION_PERMISSION_REQUEST_CODE,
                        )
                    }
                    registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_GRANT_NOTIFICATIONS, this)
                })
            }
            if (
                DikcizAutomationCapability.NotificationsMetadata in capabilities ||
                DikcizAutomationCapability.NotificationsContent in capabilities ||
                DikcizAutomationCapability.MediaSessionsMetadata in capabilities ||
                DikcizAutomationCapability.MediaSessionsContent in capabilities ||
                DikcizAutomationActionCapability.MediaControl in actions
            ) {
                addView(Button(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_automation_open_listener)
                    setOnClickListener { openAutomationSystemSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
                    registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_OPEN_NOTIFICATION_LISTENER, this)
                })
            }
            if (DikcizAutomationCapability.InterruptionFilterState in capabilities) {
                addView(Button(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_automation_open_notification_policy_access)
                    setOnClickListener {
                        openAutomationSystemSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    }
                    registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_OPEN_NOTIFICATION_POLICY, this)
                })
            }
            addView(Button(this@DikcizHomeActivity).apply {
                text = getString(R.string.dikciz_automation_open_app_settings)
                setOnClickListener { openAutomationAppSettings() }
                registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_OPEN_APP_SETTINGS, this)
            })
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_automation_title)
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton(R.string.dikciz_dialog_close, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { closeButton ->
                registerAutomationView(SEMANTIC_SETTINGS_AUTOMATION_CLOSE, closeButton)
            }
            publishAutomationUIRendered()
        }
        showActiveDialog(dialog) {
            clearAutomationSettingsAccessViews()
            publishAutomationUIRendered()
        }
    }

    private fun showRemoteAccessAuthSettings() {
        val initialStatus = remoteAccessAuth.status()
        val summary = TextView(this).apply {
            text = remoteAccessAuthSummary(initialStatus)
            contentDescription = text
        }
        val passwordInput = EditText(this).apply {
            hint = getString(R.string.dikciz_remote_auth_password)
            contentDescription = hint
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val confirmationInput = EditText(this).apply {
            hint = getString(R.string.dikciz_remote_auth_password_confirmation)
            contentDescription = hint
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val authEnabled = Switch(this).apply {
            text = getString(R.string.dikciz_remote_auth_enabled)
            contentDescription = text
            isEnabled = initialStatus.configured && initialStatus.valid
            isChecked = initialStatus.enabled
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), NO_PADDING)
            addView(TextView(this@DikcizHomeActivity).apply {
                text = getString(R.string.dikciz_remote_auth_message)
                contentDescription = text
            })
            addView(summary)
            addView(passwordInput)
            addView(confirmationInput)
            addView(authEnabled)
        }
        lateinit var dialog: AlertDialog
        lateinit var clearButton: Button
        val saveButton = Button(this).apply {
            text = getString(R.string.dikciz_remote_auth_set_password)
            contentDescription = text
            setOnClickListener {
                val password = passwordInput.text.toString().toCharArray()
                val confirmation = confirmationInput.text.toString().toCharArray()
                try {
                    if (!remoteAccessAuthPasswordsMatch(password, confirmation)) {
                        confirmationInput.error = getString(R.string.dikciz_remote_auth_password_mismatch)
                        return@setOnClickListener
                    }
                    remoteAccessAuth.setPasswordFromPhone(password)
                    passwordInput.text.clear()
                    confirmationInput.text.clear()
                    val updatedStatus = remoteAccessAuth.status()
                    summary.text = remoteAccessAuthSummary(updatedStatus)
                    summary.contentDescription = summary.text
                    authEnabled.isEnabled = updatedStatus.configured && updatedStatus.valid
                    authEnabled.isChecked = updatedStatus.enabled
                    clearButton.isEnabled = updatedStatus.configured
                    Toast.makeText(
                        this@DikcizHomeActivity,
                        R.string.dikciz_remote_auth_password_saved,
                        Toast.LENGTH_SHORT,
                    ).show()
                } catch (_: DikcizRemoteAccessAuthException) {
                    passwordInput.error = getString(R.string.dikciz_remote_auth_password_invalid)
                } finally {
                    password.fill(REMOTE_AUTH_PASSWORD_CLEAR_CHARACTER)
                    confirmation.fill(REMOTE_AUTH_PASSWORD_CLEAR_CHARACTER)
                }
            }
        }
        clearButton = Button(this).apply {
            text = getString(R.string.dikciz_remote_auth_clear_password)
            contentDescription = text
            isEnabled = initialStatus.configured
            setOnClickListener {
                try {
                    remoteAccessAuth.clearPasswordFromPhone()
                    val updatedStatus = remoteAccessAuth.status()
                    summary.text = remoteAccessAuthSummary(updatedStatus)
                    summary.contentDescription = summary.text
                    authEnabled.isEnabled = updatedStatus.configured && updatedStatus.valid
                    authEnabled.isChecked = updatedStatus.enabled
                    clearButton.isEnabled = updatedStatus.configured
                    Toast.makeText(
                        this@DikcizHomeActivity,
                        R.string.dikciz_remote_auth_password_cleared,
                        Toast.LENGTH_SHORT,
                    ).show()
                } catch (_: DikcizRemoteAccessAuthException) {
                    Toast.makeText(
                        this@DikcizHomeActivity,
                        R.string.dikciz_remote_auth_record_error,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
        form.addView(saveButton)
        form.addView(clearButton)
        authEnabled.setOnCheckedChangeListener { _, enabled ->
            if (!authEnabled.isPressed) {
                return@setOnCheckedChangeListener
            }
            try {
                remoteAccessAuth.setEnabledFromPhone(enabled)
                val updatedStatus = remoteAccessAuth.status()
                summary.text = remoteAccessAuthSummary(updatedStatus)
                summary.contentDescription = summary.text
            } catch (_: DikcizRemoteAccessAuthException) {
                authEnabled.isChecked = !enabled
                Toast.makeText(
                    this@DikcizHomeActivity,
                    R.string.dikciz_remote_auth_record_error,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_remote_auth_title)
            .setView(form)
            .setNegativeButton(R.string.dikciz_dialog_close, null)
            .create()
        showActiveDialog(
            dialog,
            onShown = {
                clearRemoteAuthAutomationViews()
                registerAutomationView(SEMANTIC_REMOTE_AUTH_PASSWORD, passwordInput)
                registerAutomationView(SEMANTIC_REMOTE_AUTH_PASSWORD_CONFIRMATION, confirmationInput)
                registerAutomationView(SEMANTIC_REMOTE_AUTH_ENABLED, authEnabled)
                registerAutomationView(SEMANTIC_REMOTE_AUTH_SET_PASSWORD, saveButton)
                registerAutomationView(SEMANTIC_REMOTE_AUTH_CLEAR_PASSWORD, clearButton)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { closeButton ->
                    registerAutomationView(SEMANTIC_REMOTE_AUTH_CLOSE, closeButton)
                }
            },
            onDismiss = {
                if (activeDialog === dialog) {
                    clearRemoteAuthAutomationViews()
                }
            },
        )
    }

    private fun remoteAccessAuthSummary(status: DikcizRemoteAccessAuthStatus): String {
        return when {
            !status.valid -> getString(R.string.dikciz_remote_auth_status_invalid)
            !status.configured -> getString(R.string.dikciz_remote_auth_status_unconfigured)
            status.enabled -> getString(R.string.dikciz_remote_auth_status_enabled)
            else -> getString(R.string.dikciz_remote_auth_status_disabled)
        }
    }

    private fun remoteAccessAuthPasswordsMatch(first: CharArray, second: CharArray): Boolean {
        val firstBytes = String(first).toByteArray(StandardCharsets.UTF_8)
        val secondBytes = String(second).toByteArray(StandardCharsets.UTF_8)
        return try {
            MessageDigest.isEqual(firstBytes, secondBytes)
        } finally {
            firstBytes.fill(REMOTE_AUTH_PASSWORD_CLEAR_BYTE)
            secondBytes.fill(REMOTE_AUTH_PASSWORD_CLEAR_BYTE)
        }
    }

    private fun showFullDeviceAccessSetup() {
        shouldResumeFullDeviceAccessSetup = false
        val entries = DikcizDeviceAccess.entries(this)
        val actionableEntries = entries.filter { entry ->
            entry.state != DikcizDeviceAccessState.Unavailable &&
                entry.state != DikcizDeviceAccessState.OnDemand
        }
        val grantedCount = actionableEntries.count { entry ->
            entry.state == DikcizDeviceAccessState.Granted
        }
        val nextRequirement = DikcizDeviceAccess.nextSetupRequirement(this)
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), NO_PADDING)
            addView(TextView(this@DikcizHomeActivity).apply {
                text = getString(R.string.dikciz_device_access_message)
                contentDescription = text
            })
            addView(TextView(this@DikcizHomeActivity).apply {
                text = getString(
                    R.string.dikciz_device_access_progress,
                    grantedCount,
                    actionableEntries.size,
                )
                contentDescription = text
            })
            entries.forEach { entry ->
                addView(TextView(this@DikcizHomeActivity).apply {
                    text = getString(
                        R.string.dikciz_device_access_entry,
                        getString(entry.requirement.titleResourceID),
                        getString(deviceAccessStateResourceID(entry.state)),
                    )
                    contentDescription = text
                })
            }
            if (nextRequirement == null) {
                addView(TextView(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_device_access_complete)
                    contentDescription = text
                })
            } else {
                addView(Button(this@DikcizHomeActivity).apply {
                    text = getString(
                        R.string.dikciz_device_access_grant_next,
                        getString(nextRequirement.titleResourceID),
                    )
                    contentDescription = text
                    setOnClickListener { requestFullDeviceAccess(nextRequirement) }
                    registerAutomationView(SEMANTIC_SETTINGS_DEVICE_ACCESS_NEXT, this)
                })
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_device_access_title)
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton(R.string.dikciz_dialog_close, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { closeButton ->
                registerAutomationView(SEMANTIC_SETTINGS_DEVICE_ACCESS_CLOSE, closeButton)
            }
            publishAutomationUIRendered()
        }
        dikcizLogger.info(
            EVENT_DEVICE_ACCESS_SETUP_OPENED,
            mapOf(
                FIELD_DEVICE_ACCESS_GRANTED_COUNT to grantedCount,
                FIELD_DEVICE_ACCESS_REQUIREMENT_COUNT to actionableEntries.size,
            ),
        )
        showActiveDialog(dialog, onDismiss = ::clearDeviceAccessAutomationViews)
    }

    private fun requestFullDeviceAccess(requirement: DikcizDeviceAccessRequirement) {
        shouldResumeFullDeviceAccessSetup = true
        activeDialog?.dismiss()
        dikcizLogger.info(
            EVENT_DEVICE_ACCESS_REQUEST_STARTED,
            mapOf(FIELD_DEVICE_ACCESS_REQUIREMENT to requirement.persistedValue),
        )
        when (requirement.kind) {
            DikcizDeviceAccessKind.RuntimePermission -> {
                requestPermissions(
                    DikcizDeviceAccess.runtimePermissions(requirement),
                    FULL_DEVICE_ACCESS_PERMISSION_REQUEST_CODE,
                )
            }

            DikcizDeviceAccessKind.SystemSettings -> {
                val settingsIntent = DikcizDeviceAccess.settingsIntent(this, requirement)
                if (settingsIntent == null) {
                    showFullDeviceAccessRequestUnavailable(requirement)
                    return
                }
                try {
                    startActivity(settingsIntent)
                } catch (exception: ActivityNotFoundException) {
                    showFullDeviceAccessRequestUnavailable(requirement, exception)
                }
            }

            DikcizDeviceAccessKind.Role -> {
                val roleIntent = DikcizDeviceAccess.smsRoleIntent(this)
                if (roleIntent == null) {
                    showFullDeviceAccessRequestUnavailable(requirement)
                    return
                }
                try {
                    startActivityForResult(roleIntent, FULL_DEVICE_ACCESS_SMS_ROLE_REQUEST_CODE)
                } catch (exception: ActivityNotFoundException) {
                    showFullDeviceAccessRequestUnavailable(requirement, exception)
                }
            }

            DikcizDeviceAccessKind.DeviceAdministration -> requestDeviceAdministration()
            DikcizDeviceAccessKind.OnDemand -> showFullDeviceAccessRequestUnavailable(requirement)
        }
    }

    private fun showFullDeviceAccessRequestUnavailable(
        requirement: DikcizDeviceAccessRequirement,
        exception: ActivityNotFoundException? = null,
    ) {
        shouldResumeFullDeviceAccessSetup = false
        dikcizLogger.warn(
            EVENT_DEVICE_ACCESS_REQUEST_UNAVAILABLE,
            buildMap {
                put(FIELD_DEVICE_ACCESS_REQUIREMENT, requirement.persistedValue)
                exception?.let { value -> put(FIELD_ERROR_CLASS, value::class.java.simpleName) }
            },
        )
        Toast.makeText(this, R.string.dikciz_device_access_request_unavailable, Toast.LENGTH_LONG).show()
        showFullDeviceAccessSetup()
    }

    private fun resumeFullDeviceAccessSetupIfNeeded() {
        if (!shouldResumeFullDeviceAccessSetup || isFinishing) {
            return
        }
        shouldResumeFullDeviceAccessSetup = false
        mainHandler.post(::showFullDeviceAccessSetup)
    }

    private fun deviceAccessStateResourceID(state: DikcizDeviceAccessState): Int {
        return when (state) {
            DikcizDeviceAccessState.Granted -> R.string.dikciz_device_access_state_granted
            DikcizDeviceAccessState.NeedsSetup -> R.string.dikciz_device_access_state_needs_setup
            DikcizDeviceAccessState.Unavailable -> R.string.dikciz_device_access_state_unavailable
            DikcizDeviceAccessState.OnDemand -> R.string.dikciz_device_access_state_on_demand
        }
    }

    private fun requestHealthConnectStepsPermission() {
        requestHealthConnectPermissions(DikcizHealthConnectAutomation.requestedStepsPermissions())
    }

    private fun requestHealthConnectHeartRatePermission() {
        requestHealthConnectPermissions(DikcizHealthConnectAutomation.requestedHeartRatePermissions())
    }

    private fun requestHealthConnectBackgroundPermission() {
        requestHealthConnectPermissions(DikcizHealthConnectAutomation.requestedBackgroundPermissions())
    }

    private fun requestHealthConnectPermissions(permissions: Set<String>) {
        if (!DikcizHealthConnectAutomation.isAvailable(this)) {
            Toast.makeText(this, R.string.dikciz_automation_health_connect_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivityForResult(
                healthConnectPermissionContract.createIntent(
                    this,
                    permissions,
                ),
                AUTOMATION_HEALTH_CONNECT_PERMISSION_REQUEST_CODE,
            )
        } catch (exception: ActivityNotFoundException) {
            dikcizLogger.warn(
                EVENT_AUTOMATION_HEALTH_CONNECT_PERMISSION_REJECTED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            Toast.makeText(this, R.string.dikciz_automation_health_connect_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestDeviceAdministration() {
        try {
            startActivityForResult(
                DikcizDeviceAdministration.activationIntent(this),
                AUTOMATION_DEVICE_ADMINISTRATION_REQUEST_CODE,
            )
        } catch (exception: ActivityNotFoundException) {
            dikcizLogger.warn(
                EVENT_AUTOMATION_DEVICE_ADMINISTRATION_REQUEST_REJECTED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            Toast.makeText(
                this,
                R.string.dikciz_automation_device_administration_unavailable,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun showScriptLogViewer(scriptID: String? = null) {
        val allScriptsSnapshot = dikcizLogger.recentScriptLogSnapshot(DIKCIZ_MAXIMUM_SCRIPT_LOG_RECORDS)
        val snapshot = if (scriptID == null) {
            allScriptsSnapshot
        } else {
            allScriptsSnapshot.copy(
                records = allScriptsSnapshot.records.filter { record -> record.scriptID == scriptID },
            )
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), NO_PADDING)
            if (snapshot.isTruncated) {
                addView(TextView(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_script_logs_truncated)
                    contentDescription = text
                })
            }
            if (snapshot.records.isEmpty()) {
                addView(TextView(this@DikcizHomeActivity).apply {
                    text = getString(R.string.dikciz_script_logs_empty)
                    contentDescription = text
                })
            } else {
                snapshot.records.forEach { record ->
                    addView(TextView(this@DikcizHomeActivity).apply {
                        text = scriptLogRecordText(record)
                        contentDescription = text
                        setTextIsSelectable(true)
                    })
                }
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_script_logs_title)
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton(R.string.dikciz_dialog_close, null)
            .setPositiveButton(R.string.dikciz_script_logs_refresh) { _, _ -> showScriptLogViewer(scriptID) }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { refreshButton ->
                registerAutomationView(SEMANTIC_SETTINGS_SCRIPT_LOGS_REFRESH, refreshButton)
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { closeButton ->
                registerAutomationView(SEMANTIC_SETTINGS_SCRIPT_LOGS_CLOSE, closeButton)
            }
            publishAutomationUIRendered()
        }
        dikcizLogger.info(
            EVENT_SCRIPT_LOG_VIEWER_OPENED,
            buildMap {
                put(FIELD_SCRIPT_LOG_RECORD_COUNT, snapshot.records.size)
                scriptID?.let { value -> put(FIELD_SCRIPT_ID, value) }
            },
        )
        showActiveDialog(dialog) {
            if (activeDialog !== dialog) {
                return@showActiveDialog
            }
            automationViews.remove(SEMANTIC_SETTINGS_SCRIPT_LOGS_REFRESH)
            automationViews.remove(SEMANTIC_SETTINGS_SCRIPT_LOGS_CLOSE)
            publishAutomationUIRendered()
        }
    }

    private fun scriptLogRecordText(record: DikcizScriptLogRecord): String {
        return buildList {
            add(record.timestamp)
            add("${record.level}: ${record.event}")
            add("$SCRIPT_LOG_FIELD_SCRIPT_ID=${record.scriptID}")
            record.scriptKind?.let { scriptKind -> add("$SCRIPT_LOG_FIELD_SCRIPT_KIND=$scriptKind") }
            record.policyID?.let { policyID -> add("$SCRIPT_LOG_FIELD_POLICY_ID=$policyID") }
            record.eventType?.let { eventType -> add("$SCRIPT_LOG_FIELD_EVENT_TYPE=$eventType") }
            record.actionType?.let { actionType -> add("$SCRIPT_LOG_FIELD_ACTION_TYPE=$actionType") }
            record.outcome?.let { outcome -> add("$SCRIPT_LOG_FIELD_OUTCOME=$outcome") }
            record.reason?.let { reason -> add("$SCRIPT_LOG_FIELD_REASON=$reason") }
            record.diagnostic?.let { diagnostic -> add("$SCRIPT_LOG_FIELD_DIAGNOSTIC=$diagnostic") }
        }.joinToString(SCRIPT_LOG_RECORD_LINE_SEPARATOR)
    }

    private fun openAutomationSystemSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (exception: ActivityNotFoundException) {
            Toast.makeText(this, exception.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun openAutomationAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun showThemePicker() {
        val configuration = homeConfiguration ?: return
        val catalog = try {
            dikcizThemeStore.loadCatalog()
        } catch (exception: HomeConfigException) {
            dikcizLogger.error(
                EVENT_THEME_CATALOGUE_LOAD_FAILED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            Toast.makeText(this, R.string.dikciz_theme_catalogue_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        val themeState = configuration.themeState(catalog)
        dikcizLogger.info(
            EVENT_THEME_CATALOGUE_LOADED,
            mapOf(
                FIELD_THEME_COUNT to catalog.themes.size,
                FIELD_REJECTED_THEME_COUNT to catalog.rejectedThemeCount,
                FIELD_THEME_STATE to themeState.persistedValue,
            ),
        )
        if (catalog.rejectedThemeCount > NO_REJECTED_THEMES) {
            dikcizLogger.warn(
                EVENT_THEME_CATALOGUE_REJECTED,
                mapOf(FIELD_REJECTED_THEME_COUNT to catalog.rejectedThemeCount),
            )
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), NO_PADDING)
            addView(createWidgetTitle(getString(R.string.dikciz_theme_current_title)))
            addView(TextView(this@DikcizHomeActivity).apply {
                text = themeSelectionDescription(configuration, catalog)
                contentDescription = text
            })
            addView(createWidgetTitle(getString(R.string.dikciz_theme_available_title)))
        }
        if (catalog.themes.isEmpty()) {
            form.addView(TextView(this).apply {
                text = getString(R.string.dikciz_theme_none_available)
                contentDescription = text
            })
        }
        lateinit var dialog: AlertDialog
        catalog.themes.forEach { theme ->
            form.addView(Button(this).apply {
                text = theme.title
                contentDescription = getString(R.string.dikciz_theme_apply_description, theme.title)
                setOnClickListener {
                    val didApply = updateConfiguration { current -> current.applyTheme(theme) }
                    if (!didApply) {
                        dikcizLogger.warn(
                            EVENT_THEME_APPLY_REJECTED,
                            mapOf(FIELD_THEME_ID to theme.id),
                        )
                        return@setOnClickListener
                    }
                    dikcizLogger.info(
                        EVENT_THEME_APPLIED,
                        mapOf(
                            FIELD_THEME_ID to theme.id,
                            FIELD_PAGE_COUNT to configuration.pages.size,
                            FIELD_WIDGET_COUNT to configuration.themeWidgetCount(),
                        ),
                    )
                    dialog.dismiss()
                }
                registerAutomationView(themeSemanticID(theme.id), this)
            })
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_theme_picker_title)
            .setView(form)
            .setNegativeButton(R.string.dikciz_dialog_close, null)
            .create()
        showActiveDialog(dialog) {
            clearThemeAutomationViews()
            publishAutomationUIRendered()
        }
    }

    private fun showLuaScriptManager() {
        val configuration = homeConfiguration ?: return
        val scriptButtons = mutableMapOf<String, Button>()
        lateinit var createButton: Button
        lateinit var importButton: Button
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), NO_PADDING)
        }
        if (configuration.scripts.isEmpty()) {
            form.addView(TextView(this).apply {
                text = getString(R.string.dikciz_script_none)
                contentDescription = text
            })
        } else {
            configuration.scripts.forEach { script ->
                form.addView(Button(this).apply {
                    text = getString(R.string.dikciz_script_manager_entry, script.title, script.id)
                    contentDescription = text
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    isAllCaps = false
                    setOnClickListener { showLuaScriptEditor(script) }
                    scriptButtons[script.id] = this
                })
            }
        }
        form.addView(Button(this).apply {
            text = getString(R.string.dikciz_script_import)
            contentDescription = text
            isAllCaps = false
            setOnClickListener { showLuaScriptImportPicker() }
            importButton = this
        })
        form.addView(Button(this).apply {
            text = getString(R.string.dikciz_script_create)
            contentDescription = text
            isAllCaps = false
            setOnClickListener { showLuaScriptEditor(null) }
            createButton = this
        })
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_script_manager_title)
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton(R.string.dikciz_dialog_close, null)
            .create()
        dialog.setOnShowListener {
            scriptButtons.forEach { (scriptID, scriptButton) ->
                registerAutomationView(luaScriptSemanticID(scriptID), scriptButton)
            }
            registerAutomationView(SEMANTIC_LUA_SCRIPT_CREATE, createButton)
            registerAutomationView(SEMANTIC_LUA_SCRIPT_IMPORT, importButton)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { closeButton ->
                registerAutomationView(SEMANTIC_LUA_SCRIPT_CLOSE, closeButton)
            }
            publishAutomationUIRendered()
        }
        dikcizLogger.info(
            EVENT_LUA_SCRIPT_MANAGER_OPENED,
            mapOf(FIELD_SCRIPT_COUNT to configuration.scripts.size),
        )
        showActiveDialog(dialog) {
            if (activeDialog !== dialog) {
                return@showActiveDialog
            }
            clearLuaScriptAutomationViews()
            publishAutomationUIRendered()
        }
    }

    private fun showLuaScriptImportPicker() {
        val archives = try {
            homeConfigStore.availableLuaScriptArchives()
        } catch (exception: HomeConfigException) {
            logLuaScriptArchiveFailure(EVENT_LUA_SCRIPT_IMPORT_REJECTED, null, exception)
            Toast.makeText(this, R.string.dikciz_script_import_failed, Toast.LENGTH_LONG).show()
            return
        }
        if (archives.isEmpty()) {
            Toast.makeText(
                this,
                getString(R.string.dikciz_script_import_none, homeConfigStore.scriptImportDirectory.absolutePath),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val archiveButtons = mutableMapOf<File, Button>()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), NO_PADDING)
            archives.forEach { archive ->
                addView(Button(this@DikcizHomeActivity).apply {
                    text = archive.name
                    contentDescription = text
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    isAllCaps = false
                    setOnClickListener { showLuaScriptImportConfirmation(archive) }
                    archiveButtons[archive] = this
                })
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_script_import_choose)
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .create()
        dialog.setOnShowListener {
            archiveButtons.forEach { (archive, button) ->
                registerAutomationView(luaScriptArchiveSemanticID(archive), button)
            }
            publishAutomationUIRendered()
        }
        showActiveDialog(dialog) {
            if (activeDialog !== dialog) {
                return@showActiveDialog
            }
            clearLuaScriptAutomationViews()
            publishAutomationUIRendered()
        }
    }

    private fun showLuaScriptImportConfirmation(archive: File) {
        val importedScript = try {
            val configuration = homeConfiguration ?: return
            homeConfigStore.readLuaScriptArchive(archive, configuration.limits)
        } catch (exception: HomeConfigException) {
            logLuaScriptArchiveFailure(EVENT_LUA_SCRIPT_IMPORT_REJECTED, archive, exception)
            Toast.makeText(this, R.string.dikciz_script_import_failed, Toast.LENGTH_LONG).show()
            return
        }
        val existingScript = homeConfiguration?.scripts?.firstOrNull { script ->
            script.id.equals(importedScript.id, ignoreCase = true)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_script_import)
            .setMessage(
                getString(
                    if (existingScript == null) {
                        R.string.dikciz_script_import_message
                    } else {
                        R.string.dikciz_script_import_conflict_message
                    },
                    importedScript.title,
                    importedScript.id,
                ),
            )
            .setNegativeButton(
                if (existingScript == null) {
                    R.string.dikciz_dialog_cancel
                } else {
                    R.string.dikciz_script_import_keep
                },
            ) { _, _ ->
                if (existingScript != null) {
                    dikcizLogger.info(
                        EVENT_LUA_SCRIPT_IMPORT_SKIPPED,
                        mapOf(
                            FIELD_ARCHIVE_NAME to archive.name,
                            FIELD_SCRIPT_ID to importedScript.id,
                        ),
                    )
                    showLuaScriptManager()
                }
            }
            .setPositiveButton(
                if (existingScript == null) {
                    R.string.dikciz_script_import_confirm
                } else {
                    R.string.dikciz_script_import_replace
                },
            ) { _, _ ->
                installLuaScriptArchive(importedScript, archive, existingScript != null)
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { importButton ->
                registerAutomationView(
                    if (existingScript == null) {
                        SEMANTIC_LUA_SCRIPT_IMPORT_CONFIRM
                    } else {
                        SEMANTIC_LUA_SCRIPT_IMPORT_REPLACE
                    },
                    importButton,
                )
            }
            if (existingScript != null) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { keepButton ->
                    registerAutomationView(SEMANTIC_LUA_SCRIPT_IMPORT_KEEP, keepButton)
                }
            }
            publishAutomationUIRendered()
        }
        showActiveDialog(dialog) {
            if (activeDialog !== dialog) {
                return@showActiveDialog
            }
            clearLuaScriptAutomationViews()
            publishAutomationUIRendered()
        }
    }

    private fun installLuaScriptArchive(
        importedScript: DikcizLuaScript,
        archive: File,
        shouldReplaceExisting: Boolean,
    ) {
        var didInstall = false
        val didSave = updateConfiguration { configuration ->
            val existingScript = configuration.scripts.firstOrNull { script ->
                script.id.equals(importedScript.id, ignoreCase = true)
            }
            if (existingScript == null) {
                didInstall = true
                return@updateConfiguration configuration.copy(
                    scripts = configuration.scripts + importedScript,
                )
            }
            if (!shouldReplaceExisting) {
                return@updateConfiguration configuration
            }
            didInstall = true
            configuration.copy(
                scripts = configuration.scripts.map { script ->
                    if (script.id.equals(importedScript.id, ignoreCase = true)) {
                        importedScript
                    } else {
                        script
                    }
                },
            )
        }
        if (!didSave || !didInstall) {
            if (didSave) {
                dikcizLogger.warn(
                    EVENT_LUA_SCRIPT_IMPORT_REJECTED,
                    mapOf(
                        FIELD_ARCHIVE_NAME to archive.name,
                        FIELD_REASON to REASON_SCRIPT_ID_CONFLICT,
                        FIELD_SCRIPT_ID to importedScript.id,
                    ),
                )
                Toast.makeText(this, R.string.dikciz_script_import_conflict, Toast.LENGTH_LONG).show()
            }
            return
        }
        dikcizLogger.info(
            EVENT_LUA_SCRIPT_IMPORTED,
            mapOf(
                FIELD_ARCHIVE_NAME to archive.name,
                FIELD_REPLACED to shouldReplaceExisting,
                FIELD_SCRIPT_ID to importedScript.id,
            ),
        )
        Toast.makeText(this, R.string.dikciz_script_imported, Toast.LENGTH_LONG).show()
        showLuaScriptManager()
    }

    private fun showLuaScriptEditor(script: DikcizLuaScript?) {
        val titleInput = EditText(this).apply {
            hint = getString(R.string.dikciz_dialog_title_hint)
            contentDescription = getString(R.string.dikciz_script_title_input)
            setText(script?.title.orEmpty())
            setSelectAllOnFocus(true)
        }
        val enabledInput = Switch(this).apply {
            text = getString(R.string.dikciz_script_enabled)
            contentDescription = text
            isChecked = script?.enabled ?: true
        }
        val sourceInput = EditText(this).apply {
            hint = getString(R.string.dikciz_script_source_input)
            contentDescription = getString(R.string.dikciz_script_source_input)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = SCRIPT_EDITOR_MINIMUM_LINES
            setText(script?.source ?: DEFAULT_LUA_SCRIPT_SOURCE)
        }
        val stateInput = EditText(this).apply {
            hint = getString(R.string.dikciz_script_state_input)
            contentDescription = getString(R.string.dikciz_script_state_input)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = SCRIPT_STATE_EDITOR_MINIMUM_LINES
            setText(script?.state?.toString(SCRIPT_STATE_JSON_INDENTATION_SPACES) ?: DEFAULT_LUA_SCRIPT_STATE)
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), NO_PADDING)
            addView(titleInput)
            addView(enabledInput)
            addView(createWidgetTitle(getString(R.string.dikciz_script_source_label)))
            addView(sourceInput)
            addView(createWidgetTitle(getString(R.string.dikciz_script_state_label)))
            addView(stateInput)
        }
        val exportButton = script?.let { savedScript ->
            Button(this).apply {
                text = getString(R.string.dikciz_script_export)
                contentDescription = text
                isAllCaps = false
                setOnClickListener { exportLuaScriptArchive(savedScript) }
            }.also(form::addView)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (script == null) R.string.dikciz_script_create else R.string.dikciz_script_edit)
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .setPositiveButton(R.string.dikciz_dialog_save, null)
            .create()
        if (script != null) {
            dialog.setButton(
                AlertDialog.BUTTON_NEUTRAL,
                getString(R.string.dikciz_script_delete),
                DialogInterface.OnClickListener { _, _ -> Unit },
            )
        }
        fun registerLuaScriptEditorAutomationViews() {
            if (script != null) {
                val deleteButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                deleteButton.setOnClickListener {
                    showLuaScriptDeleteConfirmation(
                        script,
                        dialog,
                        ::registerLuaScriptEditorAutomationViews,
                    )
                }
                registerAutomationView(SEMANTIC_LUA_SCRIPT_DELETE, deleteButton)
            }
            registerAutomationView(SEMANTIC_LUA_SCRIPT_TITLE, titleInput)
            registerAutomationView(SEMANTIC_LUA_SCRIPT_ENABLED, enabledInput)
            registerAutomationView(SEMANTIC_LUA_SCRIPT_SOURCE, sourceInput)
            registerAutomationView(SEMANTIC_LUA_SCRIPT_STATE, stateInput)
            registerAutomationView(SEMANTIC_LUA_SCRIPT_SAVE, dialog.getButton(AlertDialog.BUTTON_POSITIVE))
            exportButton?.let { button ->
                registerAutomationView(SEMANTIC_LUA_SCRIPT_EXPORT, button)
            }
            registerDialogCloseAutomationView(dialog)
            publishAutomationUIRendered()
        }
        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val state = parseLuaScriptEditorState(stateInput) ?: return@setOnClickListener
                val updatedScript = DikcizLuaScript(
                    id = script?.id ?: newScriptIDOrShowError(titleInput.text.toString())
                        ?: return@setOnClickListener,
                    title = titleInput.text.toString(),
                    enabled = enabledInput.isChecked,
                    source = sourceInput.text.toString(),
                    state = state,
                )
                val didSave = updateConfiguration { current ->
                    if (script == null) {
                        current.copy(scripts = current.scripts + updatedScript)
                    } else {
                        current.copy(
                            scripts = current.scripts.map { currentScript ->
                                if (currentScript.id == script.id) updatedScript else currentScript
                            },
                        )
                    }
                }
                if (!didSave) {
                    return@setOnClickListener
                }
                dikcizLogger.info(
                    if (script == null) EVENT_LUA_SCRIPT_CREATED else EVENT_LUA_SCRIPT_EDITED,
                    mapOf(FIELD_SCRIPT_ID to updatedScript.id),
                )
                dialog.dismiss()
                showLuaScriptManager()
            }
            registerLuaScriptEditorAutomationViews()
        }
        showActiveDialog(dialog) {
            if (activeDialog !== dialog) {
                return@showActiveDialog
            }
            clearLuaScriptAutomationViews()
            publishAutomationUIRendered()
        }
    }

    private fun exportLuaScriptArchive(script: DikcizLuaScript) {
        val archive = try {
            val configuration = homeConfiguration ?: return
            homeConfigStore.exportLuaScriptArchive(script, configuration.limits)
        } catch (exception: HomeConfigException) {
            logLuaScriptArchiveFailure(EVENT_LUA_SCRIPT_EXPORT_FAILED, null, exception)
            Toast.makeText(this, R.string.dikciz_script_export_failed, Toast.LENGTH_LONG).show()
            return
        }
        dikcizLogger.info(
            EVENT_LUA_SCRIPT_EXPORTED,
            mapOf(
                FIELD_ARCHIVE_NAME to archive.name,
                FIELD_SCRIPT_ID to script.id,
            ),
        )
        Toast.makeText(
            this,
            getString(R.string.dikciz_script_exported, archive.absolutePath),
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun logLuaScriptArchiveFailure(
        event: String,
        archive: File?,
        exception: HomeConfigException,
    ) {
        dikcizLogger.warn(
            event,
            buildMap {
                archive?.let { file -> put(FIELD_ARCHIVE_NAME, file.name) }
                put(FIELD_ERROR_CLASS, exception::class.java.simpleName)
                put(FIELD_ERROR_SUMMARY, exception.message.orEmpty())
            },
        )
    }

    private fun parseLuaScriptEditorState(stateInput: EditText): JSONObject? {
        return try {
            JSONObject(stateInput.text.toString())
        } catch (_: JSONException) {
            Toast.makeText(this, R.string.dikciz_script_state_invalid, Toast.LENGTH_LONG).show()
            null
        }
    }

    private fun showLuaScriptDeleteConfirmation(
        script: DikcizLuaScript,
        editorDialog: AlertDialog,
        restoreEditorAutomationViews: () -> Unit,
    ) {
        editorDialog.hide()
        clearLuaScriptAutomationViews()
        automationViews.remove(SEMANTIC_DIALOG_CLOSE)
        var didDelete = false
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_script_delete)
            .setMessage(getString(R.string.dikciz_script_delete_message, script.title))
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .setPositiveButton(R.string.dikciz_script_delete, null)
            .create()
        dialog.setOnShowListener {
            val confirmButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            confirmButton.setOnClickListener {
                if (!updateConfiguration { current ->
                        current.copy(
                            scripts = current.scripts.filterNot { currentScript ->
                                currentScript.id == script.id
                            },
                        )
                    }
                ) {
                    return@setOnClickListener
                }
                didDelete = true
                dikcizLogger.info(EVENT_LUA_SCRIPT_DELETED, mapOf(FIELD_SCRIPT_ID to script.id))
                dialog.dismiss()
                editorDialog.dismiss()
                showLuaScriptManager()
            }
            registerAutomationView(SEMANTIC_LUA_SCRIPT_DELETE_CONFIRM, confirmButton)
            registerDialogCloseAutomationView(dialog)
            publishAutomationUIRendered()
        }
        dialog.setOnDismissListener {
            automationViews.remove(SEMANTIC_LUA_SCRIPT_DELETE_CONFIRM)
            automationViews.remove(SEMANTIC_DIALOG_CLOSE)
            if (didDelete) {
                publishAutomationUIRendered()
                return@setOnDismissListener
            }
            editorDialog.show()
            restoreEditorAutomationViews()
        }
        dialog.show()
    }

    private fun themeSelectionDescription(configuration: HomeConfiguration): String {
        val catalog = try {
            dikcizThemeStore.loadCatalog()
        } catch (_: HomeConfigException) {
            return getString(R.string.dikciz_theme_status_unavailable)
        }
        return themeSelectionDescription(configuration, catalog)
    }

    private fun themeSelectionDescription(
        configuration: HomeConfiguration,
        catalog: DikcizThemeCatalog,
    ): String {
        val state = configuration.themeState(catalog)
        if (state == DikcizThemeState.None) {
            return getString(R.string.dikciz_theme_status_none)
        }
        val themeName = catalog.themes
            .firstOrNull { theme -> theme.id == configuration.selectedThemeID }
            ?.title
            ?: configuration.selectedThemeID.orEmpty()
        return getString(
            R.string.dikciz_theme_status_named,
            themeName,
            getString(state.labelResourceID),
        )
    }

    private val DikcizThemeState.labelResourceID: Int
        get() = when (this) {
            DikcizThemeState.None -> R.string.dikciz_theme_state_none
            DikcizThemeState.Clean -> R.string.dikciz_theme_state_clean
            DikcizThemeState.Missing -> R.string.dikciz_theme_state_missing
            DikcizThemeState.Modified -> R.string.dikciz_theme_state_modified
        }

    private fun saveSettingsPages(
        titleInputs: Map<String, EditText>,
    ): Boolean {
        val titles = titleInputs.mapValues { (_, input) -> input.text.toString() }
        val didSave = updateConfiguration { currentConfiguration ->
            currentConfiguration.copy(
                pages = currentConfiguration.pages.map { page ->
                    page.copy(title = titles[page.id].orEmpty())
                },
            )
        }
        if (didSave) {
            dikcizLogger.info(
                EVENT_SETTINGS_PAGES_SAVED,
                mapOf(FIELD_PAGE_COUNT to titleInputs.size),
            )
        }
        return didSave
    }

    private fun saveSettingsGrid(gridInputs: Map<DikcizGridSetting, EditText>) {
        val configuration = homeConfiguration ?: return
        var candidate = configuration.nativeGrid
        gridInputs.forEach { (setting, input) ->
            val value = input.text.toString().trim().toIntOrNull()
            if (value == null || value < setting.minimum || value > setting.maximum) {
                dikcizLogger.warn(
                    EVENT_PAGE_GRID_REJECTED,
                    mapOf(
                        FIELD_REASON to REASON_GRID_VALUE_OUT_OF_RANGE,
                        FIELD_SETTING to setting.persistedValue,
                    ),
                )
                Toast.makeText(
                    this,
                    getString(
                        R.string.dikciz_settings_grid_value_invalid,
                        getString(setting.labelResourceID),
                        setting.minimum,
                        setting.maximum,
                    ),
                    Toast.LENGTH_LONG,
                ).show()
                return
            }
            candidate = setting.write(candidate, value)
        }
        applyNativeGrid(candidate)
    }

    /**
     * Applies a page grid for every page at once.
     *
     * The change is all or nothing: if a single placed item would leave the grid or collide
     * under the new geometry, nothing is written and the offending page and widget are
     * named. Users resize or move that item first, then change the grid again.
     */
    private fun applyNativeGrid(grid: DikcizNativeGrid): Boolean {
        val configuration = homeConfiguration ?: return false
        if (grid == configuration.nativeGrid) {
            return true
        }
        nativeGridConflict(configuration, grid)?.let { (page, conflict) ->
            dikcizLogger.warn(
                EVENT_PAGE_GRID_REJECTED,
                mapOf(
                    FIELD_PAGE_ID to page.id,
                    FIELD_REASON to conflict.failure.persistedValue,
                    FIELD_WIDGET_ID to conflict.itemID,
                ),
            )
            Toast.makeText(
                this,
                nativeGridConflictMessage(page, conflict, grid),
                Toast.LENGTH_LONG,
            ).show()
            return false
        }
        if (!updateConfiguration { current -> current.copy(nativeGrid = grid) }) {
            return false
        }
        dikcizLogger.info(
            EVENT_PAGE_GRID_SAVED,
            mapOf(
                FIELD_COLUMN_SPAN to grid.columns,
                FIELD_ROW_SPAN to grid.rows,
            ),
        )
        Toast.makeText(this, R.string.dikciz_settings_grid_saved, Toast.LENGTH_SHORT).show()
        return true
    }

    private fun nativeGridConflictMessage(
        page: HomePage,
        conflict: DikcizGridConflict,
        grid: DikcizNativeGrid,
    ): String {
        return getString(
            R.string.dikciz_settings_grid_conflict,
            conflict.itemID,
            page.title,
            grid.columns,
            grid.rows,
        )
    }

    /** The first page and widget a candidate grid cannot hold, or null when every item fits. */
    private fun nativeGridConflict(
        configuration: HomeConfiguration,
        grid: DikcizNativeGrid,
    ): Pair<HomePage, DikcizGridConflict>? {
        configuration.pages.forEach { page ->
            val conflict = DikcizGridLayoutEngine.firstConflict(
                grid = grid,
                items = page.widgets.map { widget -> DikcizGridItem(widget.id, widget.cell) },
            )
            if (conflict != null) {
                return page to conflict
            }
        }
        return null
    }

    private fun showResetConfirmation() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_reset_title)
            .setMessage(R.string.dikciz_reset_message)
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .setPositiveButton(R.string.dikciz_reset_confirm) { _, _ -> resetDikcizHome() }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { resetButton ->
                registerAutomationView(SEMANTIC_SETTINGS_RESET_CONFIRM, resetButton)
                publishAutomationUIRendered()
            }
        }
        showActiveDialog(dialog) {
            automationViews.remove(SEMANTIC_SETTINGS_RESET_CONFIRM)
            publishAutomationUIRendered()
        }
    }

    private fun resetDikcizHome() {
        try {
            resetToBundledConfiguration()
        } catch (exception: HomeConfigException) {
            Toast.makeText(this, exception.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun resetToBundledConfiguration(): HomeConfiguration {
        val previousConfiguration = try {
            homeConfigStore.loadOrCreate()
        } catch (exception: HomeConfigException) {
            dikcizLogger.warn(
                EVENT_CONFIGURATION_RESET_SOURCE_UNAVAILABLE,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            null
        }
        dikcizLogger.info(EVENT_CONFIGURATION_RESET_STARTED)
        val resetConfiguration = try {
            homeConfigStore.resetToBundledConfiguration(previousConfiguration)
        } catch (exception: HomeConfigException) {
            dikcizLogger.error(
                EVENT_CONFIGURATION_RESET_FAILED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            throw exception
        }
        previousConfiguration?.let { previous ->
            cleanupRemovedProviderWidgets(previous, resetConfiguration)
        }
        homeConfiguration = safeModeConfiguration(resetConfiguration)
        dikcizLogger.configure(resetConfiguration.logging)
        dikcizLogger.info(EVENT_CONFIGURATION_RESET_COMPLETED)
        renderConfiguration()
        return resetConfiguration
    }

    private fun restartInMode(shouldUseSafeMode: Boolean) {
        dikcizLogger.info(
            EVENT_SAFE_MODE_LAUNCH_REQUESTED,
            mapOf(FIELD_SAFE_MODE to shouldUseSafeMode),
        )
        startActivity(
            Intent(this, DikcizHomeActivity::class.java)
                .putExtra(EXTRA_SAFE_MODE, shouldUseSafeMode)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    private fun showEditAppWidgetDialog(widget: AppHomeWidget) {
        val titleInput = EditText(this).apply {
            hint = getString(R.string.dikciz_dialog_title_hint)
            contentDescription = getString(R.string.dikciz_dialog_app_widget_title_input)
            setText(widget.title)
            setSelectAllOnFocus(true)
        }
        val displayStyleButtons = AppWidgetDisplayStyle.entries.associateWith { displayStyle ->
            RadioButton(this).apply {
                id = View.generateViewId()
                text = appWidgetDisplayStyleLabel(displayStyle)
                contentDescription = getString(
                    R.string.dikciz_dialog_app_widget_display_style_option,
                    appWidgetDisplayStyleLabel(displayStyle),
                )
                isChecked = widget.displayStyle == displayStyle
            }
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), NO_PADDING)
            addView(titleInput)
            addView(createWidgetTitle(getString(R.string.dikciz_dialog_app_widget_display_style)))
            addView(RadioGroup(this@DikcizHomeActivity).apply {
                orientation = RadioGroup.VERTICAL
                contentDescription = getString(R.string.dikciz_dialog_app_widget_display_style)
                displayStyleButtons.values.forEach(::addView)
            })
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_dialog_edit_app)
            .setView(form)
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .setNeutralButton(R.string.dikciz_dialog_retarget_app, null)
            .setPositiveButton(R.string.dikciz_dialog_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                showAppShortcutRetargetPicker(widget)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val displayStyle = displayStyleButtons.entries
                    .firstOrNull { (_, button) -> button.isChecked }
                    ?.key
                    ?: widget.displayStyle
                if (!updateSelectedPage { page ->
                        page.replaceWidget(widget.id) { currentWidget ->
                            (currentWidget as AppHomeWidget).copy(
                                title = titleInput.text.toString(),
                                displayStyle = displayStyle,
                            )
                        }
                    }
                ) {
                    return@setOnClickListener
                }
                dikcizLogger.info(
                    EVENT_APP_WIDGET_EDITED,
                    mapOf(
                        FIELD_DISPLAY_STYLE to displayStyle.persistedValue,
                        FIELD_WIDGET_ID to widget.id,
                    ),
                )
                dialog.dismiss()
            }
        }
        showActiveDialog(
            dialog,
            onShown = {
                clearWidgetEditorAutomationViews()
                registerWidgetEditorAutomationView(
                    widgetEditorSemanticID(widget.id, SEMANTIC_EDITOR_TITLE),
                    titleInput,
                )
                displayStyleButtons.forEach { (displayStyle, button) ->
                    registerWidgetEditorAutomationView(
                        widgetEditorDisplayStyleSemanticID(widget.id, displayStyle),
                        button,
                    )
                }
                registerWidgetEditorAutomationView(
                    widgetEditorSemanticID(widget.id, SEMANTIC_EDITOR_RETARGET),
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL),
                )
                registerWidgetEditorAutomationView(
                    widgetEditorSemanticID(widget.id, SEMANTIC_EDITOR_SAVE),
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                )
            },
            onDismiss = ::clearWidgetEditorAutomationViews,
        )
    }

    private fun showEditAppGroupDialog(widget: AppGroupHomeWidget) {
        showAppGroupDialog(null, widget)
    }

    private fun showAppGroupDialog(
        target: WidgetInsertionTarget?,
        existingWidget: AppGroupHomeWidget?,
    ) {
        val configuration = homeConfiguration ?: return
        val titleInput = EditText(this).apply {
            hint = getString(R.string.dikciz_dialog_title_hint)
            contentDescription = getString(R.string.dikciz_dialog_app_group_title_input)
            setText(existingWidget?.title ?: getString(R.string.dikciz_app_group_widget_title))
            setSelectAllOnFocus(true)
        }
        val selectableApplications = launchableApps().mapNotNull { application ->
            val component = application.component.flattenToShortString()
            if (component.length > configuration.limits.maxComponentCharacters) {
                null
            } else {
                application to component
            }
        }
        val selection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val checkboxes = selectableApplications.map { (application, component) ->
            CheckBox(this).apply {
                text = application.label
                contentDescription = getString(
                    R.string.dikciz_dialog_app_group_member_input,
                    application.label,
                )
                isChecked = component in existingWidget?.components.orEmpty()
                selection.addView(this)
            } to component
        }
        val visibleComponents = checkboxes.map { (_, component) -> component }.toSet()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), NO_PADDING)
            addView(titleInput)
            addView(TextView(this@DikcizHomeActivity).apply {
                text = getString(R.string.dikciz_dialog_app_group_members)
            })
            addView(ScrollView(this@DikcizHomeActivity).apply { addView(selection) })
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(
                if (existingWidget == null) {
                    R.string.dikciz_dialog_add_app_group
                } else {
                    R.string.dikciz_dialog_edit_app_group
                },
            )
            .setView(form)
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .setPositiveButton(R.string.dikciz_dialog_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val components = buildList {
                    existingWidget?.components
                        ?.filterNot(visibleComponents::contains)
                        ?.let(::addAll)
                    checkboxes.forEach { (checkbox, component) ->
                        if (checkbox.isChecked) {
                            add(component)
                        }
                    }
                }
                if (components.isEmpty() || components.size > AppGroupLimits.MAXIMUM_COMPONENTS) {
                    Toast.makeText(this, R.string.dikciz_app_group_rejected, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (existingWidget == null) {
                    val insertionTarget = target ?: return@setOnClickListener
                    val widgetID = newWidgetIDOrShowError(titleInput.text.toString())
                        ?: return@setOnClickListener
                    val appGroup = AppGroupHomeWidget(
                        id = widgetID,
                        title = titleInput.text.toString(),
                        components = components,
                        enabled = true,
                        cell = requestedCell(DikcizWidgetSpans.APP_GROUP),
                    )
                    if (insertWidget(insertionTarget, appGroup)) {
                        dikcizLogger.info(
                            EVENT_APP_GROUP_CREATED,
                            mapOf(FIELD_COMPONENT_COUNT to components.size),
                        )
                        dialog.dismiss()
                    }
                    return@setOnClickListener
                }
                if (updateSelectedPage { page ->
                        page.replaceWidget(existingWidget.id) { currentWidget ->
                            (currentWidget as AppGroupHomeWidget).copy(
                                title = titleInput.text.toString(),
                                components = components,
                            )
                        }
                    }
                ) {
                    dikcizLogger.info(
                        EVENT_APP_GROUP_EDITED,
                        mapOf(
                            FIELD_COMPONENT_COUNT to components.size,
                            FIELD_WIDGET_ID to existingWidget.id,
                        ),
                    )
                    dialog.dismiss()
                }
            }
        }
        showActiveDialog(
            dialog,
            onShown = {
                clearWidgetEditorAutomationViews()
                existingWidget?.let { widget ->
                    registerWidgetEditorAutomationView(
                        widgetEditorSemanticID(widget.id, SEMANTIC_EDITOR_TITLE),
                        titleInput,
                    )
                    registerWidgetEditorAutomationView(
                        widgetEditorSemanticID(widget.id, SEMANTIC_EDITOR_SAVE),
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                    )
                }
            },
            onDismiss = ::clearWidgetEditorAutomationViews,
        )
    }

    private fun showAppShortcutRetargetPicker(widget: AppHomeWidget) {
        val pickerID = appRetargetPickerSemanticID(widget.id)
        val queryInput = EditText(this).apply {
            hint = getString(R.string.dikciz_widget_picker_search)
            contentDescription = getString(R.string.dikciz_widget_picker_search)
        }
        val categoryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), NO_PADDING)
            addView(queryInput)
            addView(ScrollView(this@DikcizHomeActivity).apply {
                addView(categoryContainer)
            })
        }
        lateinit var dialog: AlertDialog
        fun retarget(application: DikcizLaunchableApp) {
            val component = appShortcutComponent(application) ?: return
            if (!updateSelectedPage { page ->
                    page.replaceWidget(widget.id) { currentWidget ->
                        (currentWidget as AppHomeWidget).copy(component = component)
                    }
                }
            ) {
                return
            }
            dikcizLogger.info(
                EVENT_APP_SHORTCUT_RETARGETED,
                mapOf(
                    FIELD_COMPONENT_PACKAGE to application.component.packageName,
                    FIELD_WIDGET_ID to widget.id,
                ),
            )
            dialog.dismiss()
        }
        fun renderCategories(query: String) {
            clearPrimaryWidgetPickerAutomationViews()
            registerWidgetPickerAutomationView(pickerSearchSemanticID(pickerID), queryInput)
            categoryContainer.removeAllViews()
            widgetPickerCatalogue.appShortcutPickerCategories().forEach { category ->
                val matchingEntries = category.entries.filter { entry ->
                    widgetPickerCatalogue.categoryMatchesQuery(category, entry, query)
                }
                if (matchingEntries.isEmpty()) {
                    return@forEach
                }
                addWidgetPickerCategory(
                    categoryContainer,
                    pickerID,
                    category,
                    matchingEntries,
                    query.isNotBlank(),
                    R.string.dikciz_dialog_retarget_app_entry,
                ) { entry ->
                    val application = (entry as? AppWidgetPickerEntry)?.application ?: return@addWidgetPickerCategory
                    retarget(application)
                }
            }
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_dialog_retarget_app)
            .setView(form)
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .create()
        queryInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                renderCategories(value?.toString().orEmpty())
            }

            override fun afterTextChanged(value: Editable?) = Unit
        })
        showActiveDialog(
            dialog,
            onShown = { renderCategories(EMPTY_QUERY) },
            onDismiss = ::clearPrimaryWidgetPickerAutomationViews,
        )
    }

    private fun showEditProviderWidgetDialog(widget: ProviderHomeWidget) {
        val titleInput = EditText(this).apply {
            hint = getString(R.string.dikciz_dialog_title_hint)
            contentDescription = getString(R.string.dikciz_dialog_provider_widget_title_input)
            setText(widget.title)
            setSelectAllOnFocus(true)
        }
        val providerInfo = providerInfoFor(widget)
        val dialogBuilder = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_dialog_edit_provider)
            .setView(titleInput)
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .setPositiveButton(R.string.dikciz_dialog_save, null)
        if (providerInfo?.configure != null) {
            dialogBuilder.setNeutralButton(R.string.dikciz_dialog_reconfigure_provider, null)
        }
        val dialog = dialogBuilder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                dialog.dismiss()
                requestProviderReconfiguration(widget, providerInfo)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!updateSelectedPage { page ->
                        page.replaceWidget(widget.id) { currentWidget ->
                            (currentWidget as ProviderHomeWidget).copy(
                                title = titleInput.text.toString(),
                            )
                        }
                    }
                ) {
                    return@setOnClickListener
                }
                dikcizLogger.info(EVENT_PROVIDER_WIDGET_EDITED, mapOf(FIELD_WIDGET_ID to widget.id))
                dialog.dismiss()
            }
        }
        showActiveDialog(
            dialog,
            onShown = {
                clearWidgetEditorAutomationViews()
                registerWidgetEditorAutomationView(
                    widgetEditorSemanticID(widget.id, SEMANTIC_EDITOR_TITLE),
                    titleInput,
                )
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.let { reconfigureButton ->
                    registerWidgetEditorAutomationView(
                        widgetEditorSemanticID(widget.id, SEMANTIC_EDITOR_RECONFIGURE),
                        reconfigureButton,
                    )
                }
                registerWidgetEditorAutomationView(
                    widgetEditorSemanticID(widget.id, SEMANTIC_EDITOR_SAVE),
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                )
            },
            onDismiss = ::clearWidgetEditorAutomationViews,
        )
    }

    private fun providerInfoFor(widget: ProviderHomeWidget): AppWidgetProviderInfo? {
        return appWidgetManager.getAppWidgetInfo(widget.appWidgetID)?.takeIf { providerInfo ->
            providerInfo.provider.flattenToString() == widget.provider
        }
    }

    private fun requestProviderReconfiguration(
        widget: ProviderHomeWidget,
        providerInfo: AppWidgetProviderInfo?,
    ) {
        if (providerInfo?.configure == null) {
            dikcizLogger.warn(
                EVENT_PROVIDER_WIDGET_RECONFIGURATION_REJECTED,
                mapOf(
                    FIELD_REASON to REASON_PROVIDER_CONFIGURATION_UNAVAILABLE,
                    FIELD_WIDGET_ID to widget.id,
                ),
            )
            Toast.makeText(this, R.string.dikciz_unavailable_widget, Toast.LENGTH_SHORT).show()
            return
        }
        pendingProviderReconfiguration = widget
        try {
            appWidgetHost.startAppWidgetConfigureActivityForResult(
                this,
                widget.appWidgetID,
                NO_APP_WIDGET_INTENT_FLAGS,
                APP_WIDGET_RECONFIGURATION_REQUEST_CODE,
                null,
            )
            dikcizLogger.info(
                EVENT_PROVIDER_WIDGET_RECONFIGURATION_REQUESTED,
                mapOf(FIELD_WIDGET_ID to widget.id),
            )
        } catch (exception: ActivityNotFoundException) {
            providerReconfigurationFailed(widget, exception)
        } catch (exception: SecurityException) {
            providerReconfigurationFailed(widget, exception)
        }
    }

    private fun handleAppWidgetReconfigurationResult(resultCode: Int) {
        val widget = pendingProviderReconfiguration ?: return
        pendingProviderReconfiguration = null
        if (resultCode != Activity.RESULT_OK) {
            dikcizLogger.warn(
                EVENT_PROVIDER_WIDGET_RECONFIGURATION_REJECTED,
                mapOf(FIELD_WIDGET_ID to widget.id),
            )
            return
        }
        renderConfiguration()
        dikcizLogger.info(
            EVENT_PROVIDER_WIDGET_RECONFIGURATION_COMPLETED,
            mapOf(FIELD_WIDGET_ID to widget.id),
        )
    }

    private fun providerReconfigurationFailed(
        widget: ProviderHomeWidget,
        exception: Exception,
    ) {
        pendingProviderReconfiguration = null
        dikcizLogger.error(
            EVENT_PROVIDER_WIDGET_RECONFIGURATION_FAILED,
            mapOf(
                FIELD_ERROR_CLASS to exception::class.java.simpleName,
                FIELD_ERROR_SUMMARY to exception.message.orEmpty(),
                FIELD_WIDGET_ID to widget.id,
            ),
        )
        Toast.makeText(this, R.string.dikciz_unavailable_widget, Toast.LENGTH_SHORT).show()
    }

    private fun showEditWidgetTitleDialog(
        widget: HomeWidget,
        dialogTitle: Int,
        titleInputDescription: Int,
        event: String,
        transform: (HomeWidget, String) -> HomeWidget,
    ) {
        val titleInput = EditText(this).apply {
            hint = getString(R.string.dikciz_dialog_title_hint)
            contentDescription = getString(titleInputDescription)
            setText(widget.title)
            setSelectAllOnFocus(true)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(titleInput)
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .setPositiveButton(R.string.dikciz_dialog_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!updateSelectedPage { page ->
                        page.replaceWidget(widget.id) { currentWidget ->
                            transform(currentWidget, titleInput.text.toString())
                        }
                    }
                ) {
                    return@setOnClickListener
                }
                dikcizLogger.info(event, mapOf(FIELD_WIDGET_ID to widget.id))
                dialog.dismiss()
            }
        }
        showActiveDialog(dialog)
    }

    private fun showWidgetPicker(page: HomePage, insertionIndex: Int) {
        showWidgetPicker(
            target = WidgetInsertionTarget(page.id, insertionIndex),
            deletablePage = page,
        )
    }

    private fun showWidgetPicker(
        target: WidgetInsertionTarget,
        deletablePage: HomePage? = null,
    ) {
        val targetPage = homeConfiguration?.pages?.firstOrNull { it.id == target.pageID } ?: return
        if (targetPage.locked) {
            return
        }
        val pickerID = widgetPickerSemanticID(target.pageID)
        val isTargetPageFull = isPageFull(targetPage)
        val pageFullNotice = TextView(this).apply {
            contentDescription = text
            setTextColor(getColor(R.color.dikciz_muted_foreground))
        }
        val queryInput = EditText(this).apply {
            hint = getString(R.string.dikciz_widget_picker_search)
            contentDescription = getString(R.string.dikciz_widget_picker_search)
            visibility = if (isTargetPageFull) View.GONE else View.VISIBLE
        }
        val categoryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), NO_PADDING)
            addView(pageFullNotice)
            addView(queryInput)
            addView(ScrollView(this@DikcizHomeActivity).apply {
                addView(categoryContainer)
            })
        }
        lateinit var dialog: AlertDialog
        fun selectEntry(entry: WidgetPickerEntry) {
            dialog.dismiss()
            when (entry) {
                is NativeWidgetPickerEntry -> when (entry.type) {
                    NativeWidgetType.Html -> showHtmlWidgetPackagePicker(target)
                    NativeWidgetType.ScriptDashboard -> addScriptDashboardWidget(target)
                }
                is AppWidgetPickerEntry -> addAppShortcut(target, entry.application)
                is ProviderWidgetPickerEntry -> beginProviderWidgetAddition(target, entry.providerInfo)
            }
        }
        fun renderCategories(query: String) {
            clearPrimaryWidgetPickerAutomationViews()
            registerWidgetPickerAutomationView(pickerSearchSemanticID(pickerID), queryInput)
            categoryContainer.removeAllViews()
            val currentPage = homeConfiguration?.pages?.firstOrNull { it.id == target.pageID }
            if (currentPage == null || isPageFull(currentPage)) {
                // Naming the reason here is the point: the user must not walk a package
                // chain only to be refused at the end.
                pageFullNotice.text = getString(R.string.dikciz_grid_page_full)
                pageFullNotice.contentDescription = pageFullNotice.text
                pageFullNotice.visibility = View.VISIBLE
                registerWidgetPickerAutomationView(
                    pickerPageFullSemanticID(pickerID),
                    pageFullNotice,
                )
                dikcizLogger.info(
                    EVENT_WIDGET_PICKER_PAGE_FULL,
                    mapOf(FIELD_PAGE_ID to target.pageID),
                )
                return
            }
            pageFullNotice.visibility = View.GONE
            widgetPickerCatalogue.widgetPickerCategories().forEach { category ->
                val matchingEntries = category.entries.filter { entry ->
                    widgetPickerCatalogue.categoryMatchesQuery(category, entry, query)
                }
                val matchingApplications = category.applications.filter { application ->
                    widgetPickerCatalogue.applicationMatchesQuery(category, application, query)
                }
                if (matchingEntries.isEmpty() && matchingApplications.isEmpty()) {
                    return@forEach
                }
                if (matchingApplications.isNotEmpty()) {
                    addWidgetPickerApplicationCategory(
                        categoryContainer,
                        pickerID,
                        category,
                        matchingApplications,
                        query.isNotBlank(),
                        onEntrySelected = ::selectEntry,
                    )
                    return@forEach
                }
                addWidgetPickerCategory(
                    categoryContainer,
                    pickerID,
                    category,
                    matchingEntries,
                    query.isNotBlank(),
                    onEntrySelected = ::selectEntry,
                )
            }
            if (deletablePage?.widgets?.isEmpty() == true) {
                categoryContainer.addView(Button(this).apply {
                    text = getString(R.string.dikciz_delete_empty_page)
                    setOnClickListener {
                        dialog.dismiss()
                        deletePage(deletablePage)
                    }
                })
            }
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_widget_picker_title)
            .setView(form)
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .create()
        queryInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                renderCategories(value?.toString().orEmpty())
            }

            override fun afterTextChanged(value: Editable?) = Unit
        })
        dikcizLogger.info(
            EVENT_WIDGET_PICKER_OPENED,
            mapOf(FIELD_INSERTION_TARGET to target::class.simpleName.orEmpty()),
        )
        showActiveDialog(
            dialog,
            onShown = { renderCategories(EMPTY_QUERY) },
            onDismiss = ::clearPrimaryWidgetPickerAutomationViews,
        )
    }

    private fun showActiveDialog(
        dialog: AlertDialog,
        onShown: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        activeDialog?.dismiss()
        activeDialog = dialog
        dialog.setOnDismissListener {
            if (activeDialog === dialog) {
                automationViews.remove(SEMANTIC_DIALOG_CLOSE)
            }
            onDismiss()
            if (activeDialog === dialog) {
                activeDialog = null
            }
            publishAutomationUIRendered()
        }
        dialog.show()
        registerDialogCloseAutomationView(dialog)
        onShown()
        publishAutomationUIRendered()
    }

    private fun registerDialogCloseAutomationView(dialog: AlertDialog) {
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { closeButton ->
            registerAutomationView(SEMANTIC_DIALOG_CLOSE, closeButton)
        }
    }

    private fun addWidgetPickerCategory(
        container: LinearLayout,
        pickerID: String,
        category: WidgetPickerCategory,
        entries: List<WidgetPickerEntry>,
        shouldExpand: Boolean,
        entryDescriptionResource: Int = R.string.dikciz_widget_picker_entry_description,
        onEntrySelected: (WidgetPickerEntry) -> Unit,
    ) {
        val entriesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (shouldExpand) View.VISIBLE else View.GONE
        }
        addWidgetPickerCategoryHeader(container, pickerID, category, entriesContainer)
        entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }).forEach { entry ->
            entriesContainer.addView(
                createWidgetPickerEntryButton(
                    pickerID,
                    entry,
                    onEntrySelected,
                    entryDescriptionResource,
                ),
            )
        }
        container.addView(entriesContainer)
    }

    private fun addWidgetPickerApplicationCategory(
        container: LinearLayout,
        pickerID: String,
        category: WidgetPickerCategory,
        applications: List<WidgetPickerApplication>,
        shouldExpand: Boolean,
        onEntrySelected: (WidgetPickerEntry) -> Unit,
    ) {
        val applicationsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (shouldExpand) View.VISIBLE else View.GONE
        }
        addWidgetPickerCategoryHeader(container, pickerID, category, applicationsContainer)
        applications.forEach { application ->
            applicationsContainer.addView(
                createWidgetPickerApplicationRow(
                    pickerID,
                    application,
                    shouldExpand,
                    onEntrySelected,
                ),
            )
        }
        container.addView(applicationsContainer)
    }

    private fun addWidgetPickerCategoryHeader(
        container: LinearLayout,
        pickerID: String,
        category: WidgetPickerCategory,
        entriesContainer: LinearLayout,
    ) {
        container.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(NO_PADDING, standardPadding(), NO_PADDING, NO_PADDING)
            addView(ImageView(this@DikcizHomeActivity).apply {
                contentDescription = category.label
                setImageDrawable(category.icon)
                layoutParams = LinearLayout.LayoutParams(
                    densityPixels(WIDGET_PICKER_ICON_SIZE_DP),
                    densityPixels(WIDGET_PICKER_ICON_SIZE_DP),
                )
            })
            val categoryButton = Button(this@DikcizHomeActivity).apply {
                text = category.label
                isAllCaps = false
                setOnClickListener {
                    entriesContainer.visibility = if (entriesContainer.visibility == View.VISIBLE) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }
                }
            }
            addView(categoryButton)
            registerWidgetPickerAutomationView(
                pickerCategorySemanticID(pickerID, category.automationID),
                categoryButton,
            )
        })
    }

    private fun createWidgetPickerApplicationRow(
        pickerID: String,
        application: WidgetPickerApplication,
        shouldExpand: Boolean,
        onEntrySelected: (WidgetPickerEntry) -> Unit,
    ): LinearLayout {
        val entriesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (shouldExpand) View.VISIBLE else View.GONE
        }
        application.providers.forEach { provider ->
            entriesContainer.addView(
                createProviderWidgetPickerEntryRow(pickerID, application, provider, onEntrySelected),
            )
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(this@DikcizHomeActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                setPadding(
                    densityPixels(WIDGET_PICKER_CHILD_INDENT_DP),
                    standardPadding(),
                    NO_PADDING,
                    NO_PADDING,
                )
                addView(ImageView(this@DikcizHomeActivity).apply {
                    contentDescription = application.label
                    setImageDrawable(application.icon)
                    layoutParams = LinearLayout.LayoutParams(
                        densityPixels(WIDGET_PICKER_ICON_SIZE_DP),
                        densityPixels(WIDGET_PICKER_ICON_SIZE_DP),
                    )
                })
                val applicationButton = Button(this@DikcizHomeActivity).apply {
                    text = application.label
                    contentDescription = getString(
                        R.string.dikciz_widget_picker_application_description,
                        application.label,
                    )
                    isAllCaps = false
                    setOnClickListener {
                        entriesContainer.visibility = if (entriesContainer.visibility == View.VISIBLE) {
                            View.GONE
                        } else {
                            View.VISIBLE
                        }
                    }
                }
                addView(applicationButton)
                registerWidgetPickerAutomationView(
                    pickerApplicationSemanticID(pickerID, application.packageName),
                    applicationButton,
                )
            })
            addView(entriesContainer)
        }
    }

    private fun createWidgetPickerEntryButton(
        pickerID: String,
        entry: WidgetPickerEntry,
        onEntrySelected: (WidgetPickerEntry) -> Unit,
        entryDescriptionResource: Int = R.string.dikciz_widget_picker_entry_description,
    ): Button {
        return Button(this).apply {
            text = entry.label
            contentDescription = getString(
                entryDescriptionResource,
                entry.label,
            )
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            isAllCaps = false
            setPadding(
                densityPixels(WIDGET_PICKER_CHILD_INDENT_DP),
                paddingTop,
                paddingRight,
                paddingBottom,
            )
            setOnClickListener { onEntrySelected(entry) }
        }.also { button ->
            registerWidgetPickerAutomationView(pickerEntrySemanticID(pickerID, entry), button)
        }
    }

    private fun createProviderWidgetPickerEntryRow(
        pickerID: String,
        application: WidgetPickerApplication,
        provider: ProviderWidgetPickerEntry,
        onEntrySelected: (WidgetPickerEntry) -> Unit,
    ): LinearLayout {
        return LinearLayout(this).apply {
            contentDescription = getString(
                R.string.dikciz_widget_picker_provider_entry_description,
                provider.label,
                application.label,
            )
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                densityPixels(WIDGET_PICKER_CHILD_INDENT_DP),
                standardPadding(),
                NO_PADDING,
                standardPadding(),
            )
            addView(ImageView(this@DikcizHomeActivity).apply {
                contentDescription = getString(
                    R.string.dikciz_widget_picker_provider_preview_description,
                    provider.label,
                )
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(provider.preview ?: application.icon)
                layoutParams = LinearLayout.LayoutParams(
                    densityPixels(WIDGET_PICKER_PREVIEW_WIDTH_DP),
                    densityPixels(WIDGET_PICKER_PREVIEW_HEIGHT_DP),
                )
            })
            addView(TextView(this@DikcizHomeActivity).apply {
                text = provider.label
                layoutParams = LinearLayout.LayoutParams(
                    NO_LAYOUT_SIZE,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    FULL_LAYOUT_WEIGHT,
                )
            })
            setOnClickListener { onEntrySelected(provider) }
        }.also { row ->
            registerWidgetPickerAutomationView(pickerEntrySemanticID(pickerID, provider), row)
        }
    }

    /**
     * The widget-local block picker.
     *
     * This changes the source of one existing HTML widget. It creates no native rectangle,
     * which is what separates it from the page-level package flow that creates a sibling
     * widget. The distinction is stated in the title and the confirmation.
     */
    private fun showHtmlBlockPicker(widget: HtmlHomeWidget) {
        val blocks = try {
            homeConfigStore.htmlBlocks()
        } catch (exception: HomeConfigException) {
            dikcizLogger.warn(
                EVENT_HTML_BLOCK_LIBRARY_UNAVAILABLE,
                mapOf(FIELD_REASON to exception.message.orEmpty()),
            )
            Toast.makeText(this, R.string.dikciz_html_block_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        if (blocks.isEmpty()) {
            Toast.makeText(this, R.string.dikciz_html_block_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        lateinit var dialog: AlertDialog
        val list = createPanelColumn().apply {
            addView(
                createPanelSubtitle(
                    getString(R.string.dikciz_html_block_picker_subtitle, widget.title),
                ),
            )
            blocks.forEach { block ->
                val entry = Button(this@DikcizHomeActivity).apply {
                    isAllCaps = false
                    text = block.title
                    contentDescription = getString(
                        R.string.dikciz_html_block_entry_description,
                        block.title,
                        widget.title,
                    )
                    setOnClickListener {
                        dialog.dismiss()
                        showHtmlBlockPreview(widget, block)
                    }
                    registerAutomationView(htmlBlockEntrySemanticID(widget.id, block.id), this)
                }
                addView(
                    entry,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dikciz_html_block_picker_title, widget.title))
            .setView(ScrollView(this).apply { addView(list) })
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .create()
        showActiveDialog(dialog, onDismiss = { clearHtmlBlockAutomationViews(widget.id, blocks) })
    }

    /**
     * Previews one block inside the target widget's own theme and cell bounds, then offers
     * one Add action that appends to that widget's document only.
     */
    private fun showHtmlBlockPreview(widget: HtmlHomeWidget, block: DikcizHtmlBlock) {
        val previewWidget = widget.copy(html = block.fragment)
        val preview = WebView(this).apply {
            settings.javaScriptEnabled = false
            settings.blockNetworkLoads = true
            setBackgroundColor(getColor(R.color.dikciz_widget_background))
            val bounds = widgetContainer.cellBounds(widget.cell)
            minimumHeight = bounds.height.coerceAtMost(densityPixels(HTML_BLOCK_PREVIEW_MAXIMUM_DP))
            loadDataWithBaseURL(
                null,
                DikcizHtmlWidgetDocument.create(
                    previewWidget,
                    DikcizWebWidgetContext.selfWidget(
                        homeConfiguration ?: return,
                        homeConfiguration?.selectedPage() ?: return,
                        previewWidget,
                    ),
                ),
                HTML_WIDGET_PREVIEW_MIME,
                HTML_WIDGET_PREVIEW_CHARSET,
                null,
            )
        }
        val body = createPanelColumn().apply {
            addView(createPanelSubtitle(block.description))
            addView(
                preview,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dikciz_html_block_preview_title, block.title))
            .setView(ScrollView(this).apply { addView(body) })
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .setPositiveButton(R.string.dikciz_html_block_add_confirm) { _, _ ->
                appendHtmlBlock(widget, block)
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { confirm ->
                confirm.contentDescription = getString(
                    R.string.dikciz_html_block_add_confirm_description,
                    block.title,
                    widget.title,
                )
                registerAutomationView(SEMANTIC_HTML_BLOCK_CONFIRM, confirm)
            }
            publishAutomationUIRendered()
        }
        showActiveDialog(dialog) {
            automationViews.remove(SEMANTIC_HTML_BLOCK_CONFIRM)
            publishAutomationUIRendered()
        }
    }

    /**
     * Appends the marked block source to one widget's document. No other widget, and no
     * grid rectangle, changes.
     */
    private fun appendHtmlBlock(widget: HtmlHomeWidget, block: DikcizHtmlBlock) {
        val currentWidget = currentSelectedHtmlWidget(widget.id) ?: return
        val instanceID = newHtmlBlockInstanceID()
        val fragment = homeConfigStore.markedHtmlBlockFragment(block, instanceID)
        val appended = buildString {
            append(currentWidget.html.trimEnd())
            append(HTML_BLOCK_SEPARATOR)
            append(fragment)
        }
        if (!updateSelectedPage(shouldRender = false) { page ->
                page.replaceWidget(currentWidget.id) { current ->
                    if (current is HtmlHomeWidget) {
                        current.copy(html = appended)
                    } else {
                        current
                    }
                }
            }
        ) {
            dikcizLogger.warn(
                EVENT_HTML_BLOCK_APPEND_REJECTED,
                mapOf(FIELD_WIDGET_ID to currentWidget.id),
            )
            return
        }
        val updatedWidget = homeConfiguration
            ?.selectedPage()
            ?.widgets
            ?.filterIsInstance<HtmlHomeWidget>()
            ?.firstOrNull { candidate -> candidate.id == currentWidget.id }
        val widgetView = widgetContainer.collectHtmlWidgetViews()
            .firstOrNull { candidate -> candidate.widgetID() == currentWidget.id }
        if (updatedWidget == null || widgetView == null) {
            dikcizLogger.warn(
                EVENT_HTML_BLOCK_APPEND_DEFERRED,
                mapOf(
                    FIELD_BLOCK_ID to block.id,
                    FIELD_BLOCK_INSTANCE_ID to instanceID,
                    FIELD_REASON to REASON_HTML_BLOCK_TARGET_NOT_RENDERED,
                    FIELD_WIDGET_ID to currentWidget.id,
                ),
            )
            return
        }
        widgetView.appendBlock(updatedWidget, instanceID, fragment) { outcome ->
            if (outcome != DikcizHtmlDomPatchOutcome.Executed) {
                dikcizLogger.warn(
                    EVENT_HTML_BLOCK_APPEND_DEFERRED,
                    mapOf(
                        FIELD_BLOCK_ID to block.id,
                        FIELD_BLOCK_INSTANCE_ID to instanceID,
                        FIELD_REASON to outcome,
                        FIELD_WIDGET_ID to currentWidget.id,
                    ),
                )
                return@appendBlock
            }
            dikcizLogger.info(
                EVENT_HTML_BLOCK_APPENDED,
                mapOf(
                    FIELD_BLOCK_ID to block.id,
                    FIELD_BLOCK_INSTANCE_ID to instanceID,
                    FIELD_WIDGET_ID to currentWidget.id,
                ),
            )
            Toast.makeText(
                this,
                getString(R.string.dikciz_html_block_added, block.title, currentWidget.title),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun currentSelectedHtmlWidget(widgetID: String): HtmlHomeWidget? {
        return homeConfiguration?.selectedPage()?.findWidget(widgetID) as? HtmlHomeWidget
    }

    private fun newHtmlBlockInstanceID(): String {
        return "$HTML_BLOCK_INSTANCE_PREFIX${UUID.randomUUID()}"
    }

    private fun clearHtmlBlockAutomationViews(widgetID: String, blocks: List<DikcizHtmlBlock>) {
        blocks.forEach { block ->
            automationViews.remove(htmlBlockEntrySemanticID(widgetID, block.id))
        }
        publishAutomationUIRendered()
    }

    private fun showHtmlWidgetPackagePicker(target: WidgetInsertionTarget) {
        val packages = try {
            homeConfigStore.htmlWidgetPackages()
        } catch (exception: HomeConfigException) {
            dikcizLogger.warn(
                EVENT_HTML_WIDGET_LIBRARY_REJECTED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            Toast.makeText(this, R.string.dikciz_html_widget_library_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        val pickerID = htmlWidgetPackagePickerSemanticID(target.pageID)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), NO_PADDING)
        }
        lateinit var dialog: AlertDialog
        packages.forEach { packageValue ->
            content.addView(Button(this).apply {
                text = getString(
                    R.string.dikciz_html_widget_package_label,
                    packageValue.title,
                    packageValue.description,
                )
                contentDescription = text
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                isAllCaps = false
                setOnClickListener {
                    dialog.dismiss()
                    showHtmlWidgetPackagePreview(target, packageValue)
                }
                registerWidgetPickerAutomationView(
                    htmlWidgetPackageEntrySemanticID(pickerID, packageValue.id),
                    this,
                )
            })
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_html_widget_library_title)
            .setView(content)
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .create()
        dikcizLogger.info(EVENT_HTML_WIDGET_LIBRARY_OPENED, mapOf(FIELD_PAGE_ID to target.pageID))
        showActiveDialog(dialog, onDismiss = ::clearHtmlWidgetPackageEntryAutomationViews)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showHtmlWidgetPackagePreview(
        target: WidgetInsertionTarget,
        packageValue: DikcizHtmlWidgetPackage,
    ) {
        val pickerID = htmlWidgetPackagePickerSemanticID(target.pageID)
        val preview = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return true
                }
            }
            loadDataWithBaseURL(
                "$HTML_WIDGET_PREVIEW_ORIGIN/${packageValue.id}/$HTML_WIDGET_PREVIEW_DOCUMENT",
                htmlWidgetPreviewDocument(packageValue),
                HTML_WIDGET_PREVIEW_MIME,
                HTML_WIDGET_PREVIEW_CHARSET,
                null,
            )
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dikciz_html_widget_preview_title, packageValue.title))
            .setView(preview)
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .setPositiveButton(R.string.dikciz_html_widget_load, null)
            .create()
        dialog.setOnShowListener {
            val loadButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            loadButton.setOnClickListener {
                if (addHtmlWidget(target, packageValue)) {
                    dialog.dismiss()
                }
            }
            registerWidgetPickerAutomationView(
                htmlWidgetPackageLoadSemanticID(pickerID, packageValue.id),
                loadButton,
            )
        }
        dikcizLogger.info(
            EVENT_HTML_WIDGET_PREVIEW_OPENED,
            mapOf(FIELD_PAGE_ID to target.pageID, FIELD_WIDGET_PACKAGE_ID to packageValue.id),
        )
        showActiveDialog(
            dialog,
            onDismiss = {
                preview.destroy()
                clearHtmlWidgetPackagePreviewAutomationViews()
            },
        )
    }

    private fun htmlWidgetPreviewDocument(packageValue: DikcizHtmlWidgetPackage): String {
        return """
            <!doctype html>
            <html>
              <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>${packageValue.css}</style>
              </head>
              <body>
                ${packageValue.html}
                <script>${packageValue.javascript}</script>
              </body>
            </html>
        """.trimIndent()
    }

    private fun addHtmlWidget(
        target: WidgetInsertionTarget,
        packageValue: DikcizHtmlWidgetPackage,
    ): Boolean {
        val widgetID = newWidgetIDOrShowError(packageValue.id) ?: return false
        val widget = HtmlHomeWidget(
            id = widgetID,
            title = packageValue.title,
            html = packageValue.html,
            css = packageValue.css,
            javascript = packageValue.javascript,
            heightMode = packageValue.defaultHeightMode,
            enabled = true,
            cell = requestedCell(packageValue.defaultSpan),
        )
        if (!insertWidget(target, widget)) {
            return false
        }
        dikcizLogger.info(
            EVENT_HTML_WIDGET_CREATED,
            mapOf(
                FIELD_WIDGET_ID to widget.id,
                FIELD_WIDGET_PACKAGE_ID to packageValue.id,
            ),
        )
        return true
    }

    private fun showHtmlWidgetManager(page: HomePage) {
        val widgets = page.widgets.filterIsInstance<HtmlHomeWidget>()
        val widgetButtons = mutableMapOf<HtmlHomeWidget, Button>()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), NO_PADDING)
        }
        if (widgets.isEmpty()) {
            content.addView(TextView(this).apply {
                text = getString(R.string.dikciz_html_widget_manager_empty)
                contentDescription = text
            })
        } else {
            widgets.forEach { widget ->
                content.addView(Button(this).apply {
                    text = getString(
                        R.string.dikciz_widget_reference_label,
                        widget.title,
                        page.scriptWidgetAddress(widget).persistedValue,
                        widget.id,
                    )
                    contentDescription = text
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    isAllCaps = false
                    setOnClickListener { showEditHtmlWidgetDialog(page.id, widget) }
                    widgetButtons[widget] = this
                })
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_html_widget_manager_title)
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(R.string.dikciz_dialog_close, null)
            .create()
        showActiveDialog(
            dialog,
            onShown = {
                clearWidgetEditorAutomationViews()
                widgetButtons.forEach { (widget, button) ->
                    registerWidgetEditorAutomationView(
                        widgetEditorSemanticID(widget.id, SEMANTIC_EDITOR_OPEN),
                        button,
                    )
                }
            },
            onDismiss = ::clearWidgetEditorAutomationViews,
        )
    }

    private fun showEditHtmlWidgetDialog(pageID: String, widget: HtmlHomeWidget) {
        val currentWidget = homeConfiguration
            ?.pages
            ?.firstOrNull { page -> page.id == pageID }
            ?.findWidget(widget.id) as? HtmlHomeWidget ?: return
        val titleInput = EditText(this).apply {
            hint = getString(R.string.dikciz_dialog_title_hint)
            contentDescription = getString(R.string.dikciz_dialog_title_hint)
            setText(currentWidget.title)
            setSelectAllOnFocus(true)
            styleHtmlWidgetEditorInput(this, isSourceCode = false)
        }
        val htmlInput = EditText(this).apply {
            hint = getString(R.string.dikciz_html_widget_html_input)
            contentDescription = getString(R.string.dikciz_html_widget_html_input)
            minLines = HTML_WIDGET_SOURCE_MINIMUM_LINES
            setText(currentWidget.html)
            setSelectAllOnFocus(true)
            styleHtmlWidgetEditorInput(this, isSourceCode = true)
        }
        val cssInput = EditText(this).apply {
            hint = getString(R.string.dikciz_html_widget_css_input)
            contentDescription = getString(R.string.dikciz_html_widget_css_input)
            minLines = HTML_WIDGET_SOURCE_MINIMUM_LINES
            setText(currentWidget.css)
            setSelectAllOnFocus(true)
            styleHtmlWidgetEditorInput(this, isSourceCode = true)
        }
        val javascriptInput = EditText(this).apply {
            hint = getString(R.string.dikciz_html_widget_javascript_input)
            contentDescription = getString(R.string.dikciz_html_widget_javascript_input)
            minLines = HTML_WIDGET_SOURCE_MINIMUM_LINES
            setText(currentWidget.javascript)
            setSelectAllOnFocus(true)
            styleHtmlWidgetEditorInput(this, isSourceCode = true)
        }
        val stateInput = EditText(this).apply {
            hint = getString(R.string.dikciz_html_widget_state_input)
            contentDescription = getString(R.string.dikciz_html_widget_state_input)
            minLines = HTML_WIDGET_STATE_MINIMUM_LINES
            setText(currentWidget.serializedState)
            setSelectAllOnFocus(true)
            styleHtmlWidgetEditorInput(this, isSourceCode = true)
        }
        val autoFitHeightInput = CheckBox(this).apply {
            text = getString(R.string.dikciz_html_widget_auto_fit_height)
            contentDescription = text
            isChecked = currentWidget.heightMode == HtmlWidgetHeightMode.Content
        }
        val packageIDInput = EditText(this).apply {
            hint = getString(R.string.dikciz_html_widget_package_id_input)
            contentDescription = getString(R.string.dikciz_html_widget_package_id_input)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(currentWidget.id)
            setSelectAllOnFocus(true)
            styleHtmlWidgetEditorInput(this, isSourceCode = false)
        }
        val saveToLibraryButton = Button(this).apply {
            text = getString(R.string.dikciz_html_widget_save_to_library)
            contentDescription = text
            isAllCaps = false
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(standardPadding(), NO_PADDING, standardPadding(), NO_PADDING)
            addView(createWidgetTitle(getString(R.string.dikciz_dialog_title_hint)))
            addView(titleInput)
            addView(createWidgetTitle(getString(R.string.dikciz_html_widget_html_input)))
            addView(htmlInput)
            addView(createWidgetTitle(getString(R.string.dikciz_html_widget_css_input)))
            addView(cssInput)
            addView(createWidgetTitle(getString(R.string.dikciz_html_widget_javascript_input)))
            addView(javascriptInput)
            addView(createWidgetTitle(getString(R.string.dikciz_html_widget_state_input)))
            addView(stateInput)
            addView(autoFitHeightInput)
            addView(createWidgetTitle(getString(R.string.dikciz_html_widget_package_id_input)))
            addView(packageIDInput)
            addView(saveToLibraryButton)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dikciz_html_widget_edit_title)
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .setPositiveButton(R.string.dikciz_dialog_save, null)
            .create()
        fun editedWidget(): HtmlHomeWidget? {
            val state = try {
                JSONObject(stateInput.text.toString())
            } catch (_: JSONException) {
                Toast.makeText(this, R.string.dikciz_html_widget_state_invalid, Toast.LENGTH_LONG).show()
                return null
            }
            return currentWidget.copy(
                title = titleInput.text.toString(),
                html = htmlInput.text.toString(),
                css = cssInput.text.toString(),
                javascript = javascriptInput.text.toString(),
                state = state,
                heightMode = if (autoFitHeightInput.isChecked) {
                    HtmlWidgetHeightMode.Content
                } else {
                    HtmlWidgetHeightMode.Fixed
                },
            )
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val edited = editedWidget() ?: return@setOnClickListener
                if (!updateConfiguration { configuration ->
                        configuration.replacePage(pageID) { page ->
                            page.replaceWidget(currentWidget.id) { current ->
                                if (current is HtmlHomeWidget) edited else current
                            }
                        }
                    }
                ) {
                    return@setOnClickListener
                }
                dikcizLogger.info(EVENT_HTML_WIDGET_EDITED, mapOf(FIELD_WIDGET_ID to currentWidget.id))
                dialog.dismiss()
            }
            saveToLibraryButton.setOnClickListener {
                val edited = editedWidget() ?: return@setOnClickListener
                saveHtmlWidgetToLibrary(edited, packageIDInput.text.toString())
            }
            clearWidgetEditorAutomationViews()
            registerWidgetEditorAutomationView(
                widgetEditorSemanticID(currentWidget.id, SEMANTIC_EDITOR_TITLE),
                titleInput,
            )
            registerWidgetEditorAutomationView(
                widgetEditorSemanticID(currentWidget.id, SEMANTIC_EDITOR_HTML),
                htmlInput,
            )
            registerWidgetEditorAutomationView(
                widgetEditorSemanticID(currentWidget.id, SEMANTIC_EDITOR_CSS),
                cssInput,
            )
            registerWidgetEditorAutomationView(
                widgetEditorSemanticID(currentWidget.id, SEMANTIC_EDITOR_JAVASCRIPT),
                javascriptInput,
            )
            registerWidgetEditorAutomationView(
                widgetEditorSemanticID(currentWidget.id, SEMANTIC_EDITOR_STATE),
                stateInput,
            )
            registerWidgetEditorAutomationView(
                widgetEditorSemanticID(currentWidget.id, SEMANTIC_EDITOR_AUTO_FIT_HEIGHT),
                autoFitHeightInput,
            )
            registerWidgetEditorAutomationView(
                widgetEditorSemanticID(currentWidget.id, SEMANTIC_EDITOR_PACKAGE_ID),
                packageIDInput,
            )
            registerWidgetEditorAutomationView(
                widgetEditorSemanticID(currentWidget.id, SEMANTIC_EDITOR_SAVE_TO_LIBRARY),
                saveToLibraryButton,
            )
            registerWidgetEditorAutomationView(
                widgetEditorSemanticID(currentWidget.id, SEMANTIC_EDITOR_SAVE),
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            )
        }
        showActiveDialog(dialog, onDismiss = ::clearWidgetEditorAutomationViews)
    }

    private fun saveHtmlWidgetToLibrary(widget: HtmlHomeWidget, packageID: String) {
        try {
            homeConfigStore.saveHtmlWidgetPackage(widget, packageID)
        } catch (exception: HomeConfigException) {
            dikcizLogger.warn(
                EVENT_HTML_WIDGET_LIBRARY_SAVE_REJECTED,
                mapOf(
                    FIELD_ERROR_CLASS to exception::class.java.simpleName,
                    FIELD_WIDGET_ID to widget.id,
                    FIELD_WIDGET_PACKAGE_ID to packageID,
                ),
            )
            Toast.makeText(this, R.string.dikciz_html_widget_export_failed, Toast.LENGTH_LONG).show()
            return
        }
        dikcizLogger.info(
            EVENT_HTML_WIDGET_LIBRARY_SAVED,
            mapOf(FIELD_WIDGET_ID to widget.id, FIELD_WIDGET_PACKAGE_ID to packageID),
        )
        Toast.makeText(this, R.string.dikciz_html_widget_exported, Toast.LENGTH_SHORT).show()
    }

    private fun addScriptDashboardWidget(target: WidgetInsertionTarget) {
        val title = getString(R.string.dikciz_script_dashboard_widget_title)
        val widgetID = newWidgetIDOrShowError(title) ?: return
        val widget = ScriptDashboardHomeWidget(
            id = widgetID,
            title = title,
            enabled = true,
            cell = requestedCell(DikcizWidgetSpans.SCRIPT_DASHBOARD),
        )
        if (insertWidget(target, widget)) {
            dikcizLogger.info(EVENT_SCRIPT_DASHBOARD_WIDGET_CREATED, mapOf(FIELD_WIDGET_ID to widget.id))
        }
    }

    private fun styleHtmlWidgetEditorInput(input: EditText, isSourceCode: Boolean) {
        input.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = densityPixels(HTML_WIDGET_EDITOR_CORNER_RADIUS_DP).toFloat()
            setColor(Color.TRANSPARENT)
            setStroke(
                densityPixels(HTML_WIDGET_EDITOR_BORDER_WIDTH_DP),
                getColor(R.color.dikciz_control_border),
            )
        }
        val horizontalPadding = densityPixels(HTML_WIDGET_EDITOR_HORIZONTAL_PADDING_DP)
        val verticalPadding = densityPixels(HTML_WIDGET_EDITOR_VERTICAL_PADDING_DP)
        input.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        if (!isSourceCode) {
            return
        }
        input.gravity = Gravity.TOP or Gravity.START
        input.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        input.setTypeface(Typeface.MONOSPACE)
    }

    private fun beginProviderWidgetAddition(
        target: WidgetInsertionTarget,
        providerInfo: AppWidgetProviderInfo,
    ) {
        val title = widgetPickerCatalogue.providerWidgetTitle(providerInfo)
        val widgetID = newWidgetIDOrShowError(title) ?: return
        val appWidgetID = appWidgetHost.allocateAppWidgetId()
        val widget = ProviderHomeWidget(
            id = widgetID,
            title = title,
            provider = providerInfo.provider.flattenToString(),
            appWidgetID = appWidgetID,
            enabled = true,
            cell = requestedCell(DikcizWidgetSpans.PROVIDER),
        )
        pendingProviderWidget = PendingProviderWidget(target, providerInfo, widget)
        if (appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetID, providerInfo.provider)) {
            continueProviderWidgetAddition()
            return
        }
        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetID)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, providerInfo.profile)
        }
        try {
            startActivityForResult(bindIntent, APP_WIDGET_BIND_REQUEST_CODE)
            dikcizLogger.info(EVENT_WIDGET_BIND_APPROVAL_REQUESTED)
        } catch (exception: ActivityNotFoundException) {
            discardPendingProviderWidget(EVENT_WIDGET_BIND_FAILED, exception)
        } catch (exception: SecurityException) {
            discardPendingProviderWidget(EVENT_WIDGET_BIND_FAILED, exception)
        }
    }

    private fun handleAppWidgetBindResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            continueProviderWidgetAddition()
            return
        }
        discardPendingProviderWidget(EVENT_WIDGET_BIND_REJECTED, null)
    }

    private fun continueProviderWidgetAddition() {
        val pendingWidget = pendingProviderWidget ?: return
        val configureComponent = pendingWidget.providerInfo.configure
        if (configureComponent == null) {
            persistPendingProviderWidget()
            return
        }
        try {
            appWidgetHost.startAppWidgetConfigureActivityForResult(
                this,
                pendingWidget.widget.appWidgetID,
                NO_APP_WIDGET_INTENT_FLAGS,
                APP_WIDGET_CONFIGURATION_REQUEST_CODE,
                null,
            )
            dikcizLogger.info(EVENT_WIDGET_CONFIGURATION_REQUESTED)
        } catch (exception: ActivityNotFoundException) {
            discardPendingProviderWidget(EVENT_WIDGET_CONFIGURATION_FAILED, exception)
        } catch (exception: SecurityException) {
            discardPendingProviderWidget(EVENT_WIDGET_CONFIGURATION_FAILED, exception)
        }
    }

    private fun handleAppWidgetConfigurationResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            persistPendingProviderWidget()
            return
        }
        discardPendingProviderWidget(EVENT_WIDGET_CONFIGURATION_REJECTED, null)
    }

    private fun persistPendingProviderWidget() {
        val pendingWidget = pendingProviderWidget ?: return
        if (insertWidget(pendingWidget.target, pendingWidget.widget)) {
            pendingProviderWidget = null
            dikcizLogger.info(EVENT_PROVIDER_WIDGET_CREATED)
            return
        }
        discardPendingProviderWidget(EVENT_PROVIDER_WIDGET_CREATION_FAILED, null)
    }

    private fun discardPendingProviderWidget(event: String, exception: Exception?) {
        val pendingWidget = pendingProviderWidget ?: return
        appWidgetHost.deleteAppWidgetId(pendingWidget.widget.appWidgetID)
        pendingProviderWidget = null
        val fields = mutableMapOf<String, Any>()
        if (exception != null) {
            fields[FIELD_ERROR_CLASS] = exception::class.java.simpleName
        }
        dikcizLogger.warn(event, fields)
    }

    private fun deletePage(page: HomePage) {
        if (page.locked) {
            return
        }
        val configuration = homeConfiguration ?: return
        if (configuration.pages.size == MINIMUM_PAGE_COUNT) {
            dikcizLogger.warn(
                EVENT_PAGE_DELETION_REJECTED,
                mapOf(FIELD_REASON to REASON_LAST_PAGE),
            )
            Toast.makeText(this, R.string.dikciz_last_page, Toast.LENGTH_SHORT).show()
            return
        }
        if (page.widgets.isNotEmpty()) {
            dikcizLogger.warn(
                EVENT_PAGE_DELETION_REJECTED,
                mapOf(FIELD_REASON to REASON_PAGE_NOT_EMPTY),
            )
            Toast.makeText(this, R.string.dikciz_page_not_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val deletionAxis = configuration.pages.pageDeletionAxis(page)
        if (updateConfiguration { currentConfiguration ->
            val remainingPages = currentConfiguration.pages
                .filterNot { candidate -> candidate.id == page.id }
                .compactPageCoordinatesAfterDeletion(page, deletionAxis)
            val selectedPageID = if (currentConfiguration.selectedPageID == page.id) {
                remainingPages.first().id
            } else {
                currentConfiguration.selectedPageID
            }
            val homePageID = if (currentConfiguration.homePageID == page.id) {
                remainingPages.first().id
            } else {
                currentConfiguration.homePageID
            }
            currentConfiguration.copy(
                homePageID = homePageID,
                selectedPageID = selectedPageID,
                pages = remainingPages,
            )
        }) {
            dikcizLogger.info(
                EVENT_PAGE_DELETED,
                mapOf(FIELD_PAGE_AXIS to deletionAxis.persistedValue),
            )
        }
    }

    private fun deleteTopLevelWidget(widgetID: String) {
        val selectedPage = homeConfiguration?.selectedPage() ?: return
        if (selectedPage.locked || selectedPage.findWidget(widgetID)?.locked == true) {
            return
        }
        if (selectedPage.widgets.none { it.id == widgetID }) {
            dikcizLogger.warn(
                EVENT_WIDGET_DELETION_REJECTED,
                mapOf(
                    FIELD_REASON to REASON_WIDGET_NOT_TOP_LEVEL,
                    FIELD_WIDGET_ID to widgetID,
                ),
            )
            return
        }
        if (updateSelectedPage { page ->
                page.copy(widgets = page.widgets.filterNot { it.id == widgetID })
            }
        ) {
            dikcizLogger.info(EVENT_WIDGET_DELETED, mapOf(FIELD_WIDGET_ID to widgetID))
        }
    }

    private fun setWidgetLocked(widgetID: String, isLocked: Boolean) {
        if (homeConfiguration?.selectedPage()?.locked == true) {
            return
        }
        if (!updateSelectedPage { page ->
                page.replaceWidget(widgetID) { currentWidget -> currentWidget.withLocked(isLocked) }
            }
        ) {
            return
        }
        dikcizLogger.info(
            EVENT_WIDGET_LOCK_CHANGED,
            mapOf(
                FIELD_IS_LOCKED to isLocked,
                FIELD_WIDGET_ID to widgetID,
            ),
        )
    }

    private fun saveWidgetLocks(pageID: String, requestedLocks: Map<String, Boolean>) {
        val page = homeConfiguration?.pages?.firstOrNull { candidate -> candidate.id == pageID } ?: return
        val changedWidgetCount = page.widgets.count { widget ->
            requestedLocks[widget.id] != null && requestedLocks[widget.id] != widget.locked
        }
        if (changedWidgetCount == NO_WIDGET_LOCK_CHANGES) {
            return
        }
        if (!updateConfiguration { configuration ->
                configuration.replacePage(pageID) { currentPage ->
                    currentPage.copy(
                        widgets = currentPage.widgets.map { widget ->
                            widget.withRequestedLocks(requestedLocks)
                        },
                    )
                }
            }
        ) {
            return
        }
        dikcizLogger.info(
            EVENT_WIDGET_LOCKS_SAVED,
            mapOf(
                FIELD_CHANGED_WIDGET_COUNT to changedWidgetCount,
                FIELD_SELECTED_PAGE_ID to pageID,
            ),
        )
    }

    private fun setPageLocked(pageID: String, isLocked: Boolean) {
        if (!updateConfiguration { configuration ->
                configuration.replacePage(pageID) { page -> page.copy(locked = isLocked) }
            }
        ) {
            return
        }
        dikcizLogger.info(
            EVENT_PAGE_LOCK_CHANGED,
            mapOf(
                FIELD_IS_LOCKED to isLocked,
                FIELD_SELECTED_PAGE_ID to pageID,
            ),
        )
    }

    private fun selectedPageOrWidgetIsLocked(widgetID: String): Boolean {
        val page = homeConfiguration?.selectedPage() ?: return true
        return page.locked || page.widgetLockState(widgetID) != false
    }

    /**
     * Creates one durable empty page at the far end of a rail.
     *
     * The page is saved and selected in the same operation, so there is no pending
     * state to confirm or cancel and nothing to lose by navigating away. The insertion
     * targets the global extremity of the rail rather than the selected page's own
     * neighbourhood: holding the horizontal rail before its dots extends the home to the
     * left of every existing column and shifts the rest right.
     */
    private fun createPageAtRailExtremity(
        axis: PageNavigationAxis,
        side: PageInsertionSide,
    ) {
        val configuration = homeConfiguration ?: return
        if (configuration.pages.size >= configuration.limits.maxPages) {
            dikcizLogger.warn(
                EVENT_PAGE_CREATION_REJECTED,
                mapOf(FIELD_REASON to REASON_MAXIMUM_PAGE_COUNT),
            )
            return
        }
        val selectedPage = configuration.selectedPage()
        if (selectedPage == null) {
            dikcizLogger.warn(
                EVENT_PAGE_CREATION_REJECTED,
                mapOf(FIELD_REASON to REASON_PAGE_NOT_FOUND),
            )
            return
        }
        if (selectedPage.locked) {
            dikcizLogger.warn(
                EVENT_PAGE_CREATION_REJECTED,
                mapOf(FIELD_REASON to REASON_PAGE_LOCKED),
            )
            return
        }
        val insertion = configuration.pageRailInsertion(selectedPage, axis, side)
        val insertedPages = insertion.pages
        if (insertion.position.column >= configuration.limits.maxPages ||
            insertion.position.row >= configuration.limits.maxPages ||
            insertedPages.any { page ->
                page.position.column >= configuration.limits.maxPages ||
                    page.position.row >= configuration.limits.maxPages
            }
        ) {
            dikcizLogger.warn(
                EVENT_PAGE_CREATION_REJECTED,
                mapOf(FIELD_REASON to REASON_MAXIMUM_PAGE_COUNT),
            )
            return
        }
        val page = HomePage(
            id = newPageID(),
            title = getString(R.string.dikciz_new_page),
            position = insertion.position,
            widgets = emptyList(),
        )
        if (!updateConfiguration { currentConfiguration ->
                val shifted = currentConfiguration
                    .pageRailInsertion(selectedPage, axis, side)
                    .pages
                currentConfiguration.copy(selectedPageID = page.id, pages = shifted + page)
            }
        ) {
            return
        }
        dikcizLogger.info(
            EVENT_PAGE_CREATED,
            mapOf(
                FIELD_PAGE_AXIS to axis.persistedValue,
                FIELD_PAGE_INSERTION_SIDE to side.semanticID,
                FIELD_PAGE_COLUMN to page.position.column,
                FIELD_PAGE_ROW to page.position.row,
            ),
        )
    }

    /**
     * Exchanges this page's coordinates with the neighbouring page in one direction.
     *
     * Nothing else about either page changes, and a direction with no existing neighbour
     * writes nothing at all.
     */
    private fun movePage(page: HomePage, direction: PageMoveDirection) {
        val configuration = homeConfiguration ?: return
        if (page.locked) {
            dikcizLogger.warn(
                EVENT_PAGE_MOVE_REJECTED,
                mapOf(
                    FIELD_REASON to REASON_PAGE_LOCKED,
                    FIELD_PAGE_DIRECTION to direction.persistedValue,
                ),
            )
            return
        }
        val neighbour = configuration.adjacentPage(page, direction)
        if (neighbour == null) {
            dikcizLogger.warn(
                EVENT_PAGE_MOVE_REJECTED,
                mapOf(
                    FIELD_REASON to REASON_PAGE_NOT_FOUND,
                    FIELD_PAGE_DIRECTION to direction.persistedValue,
                ),
            )
            return
        }
        if (!updateConfiguration { currentConfiguration ->
                currentConfiguration.withExchangedPagePositions(page, neighbour)
            }
        ) {
            return
        }
        dikcizLogger.info(
            EVENT_PAGE_MOVED,
            mapOf(
                FIELD_PAGE_DIRECTION to direction.persistedValue,
                FIELD_PAGE_COLUMN to neighbour.position.column,
                FIELD_PAGE_ROW to neighbour.position.row,
            ),
        )
    }

    /**
     * Places one new top-level item on the page grid.
     *
     * The requested span is placed at the first free row-major rectangle. A page with no
     * free cell left rejects the insertion with [DikcizGridFailure.PageFull] and writes
     * nothing. The placed item is left selected so its move and resize controls work
     * straight away.
     */
    private fun insertWidget(pageID: String, insertionIndex: Int, widget: HomeWidget): Boolean {
        val configuration = homeConfiguration ?: return false
        val page = configuration.pages.firstOrNull { candidate -> candidate.id == pageID }
        if (page == null) {
            dikcizLogger.warn(
                EVENT_WIDGET_INSERTION_REJECTED,
                mapOf(FIELD_REASON to REASON_PAGE_NOT_FOUND),
            )
            return false
        }
        val placement = DikcizGridLayoutEngine.placePreferredSpan(
            grid = configuration.nativeGrid,
            occupied = page.occupiedCells(),
            preferred = DikcizGridSpan(widget.cell.columnSpan, widget.cell.rowSpan),
        )
        if (placement is DikcizGridPlacement.Rejected) {
            reportGridRejection(
                event = EVENT_WIDGET_INSERTION_REJECTED,
                widgetID = widget.id,
                pageID = pageID,
                failure = placement.failure,
            )
            return false
        }
        val placedWidget = widget.withCell((placement as DikcizGridPlacement.Placed).rectangle)
        if (!updateConfiguration { currentConfiguration ->
            currentConfiguration
                .replacePage(pageID) { currentPage ->
                    val widgets = currentPage.widgets.toMutableList()
                    widgets.add(
                        insertionIndex.coerceIn(EMPTY_PAGE_INSERTION_INDEX, widgets.size),
                        placedWidget,
                    )
                    currentPage.copy(widgets = widgets)
                }
                .copy(selectedPageID = pageID)
        }) {
            return false
        }
        dikcizLogger.info(
            EVENT_WIDGET_INSERTED,
            mapOf(
                FIELD_COLUMN to placedWidget.cell.column,
                FIELD_COLUMN_SPAN to placedWidget.cell.columnSpan,
                FIELD_ROW to placedWidget.cell.row,
                FIELD_ROW_SPAN to placedWidget.cell.rowSpan,
                FIELD_WIDGET_ID to placedWidget.id,
            ),
        )
        selectInsertedWidget(pageID, placedWidget.id)
        return true
    }

    private fun insertWidget(target: WidgetInsertionTarget, widget: HomeWidget): Boolean {
        return insertWidget(target.pageID, target.insertionIndex, widget)
    }

    /** Whether the page has no free cell left for any further top-level item. */
    private fun isPageFull(page: HomePage): Boolean {
        return DikcizGridLayoutEngine.isPageFull(nativeGrid(), page.occupiedCells())
    }

    private fun reportGridRejection(
        event: String,
        widgetID: String,
        pageID: String,
        failure: DikcizGridFailure,
    ) {
        dikcizLogger.warn(
            event,
            mapOf(
                FIELD_PAGE_ID to pageID,
                FIELD_REASON to failure.persistedValue,
                FIELD_WIDGET_ID to widgetID,
            ),
        )
        showGridFailureMessage(failure)
    }

    private fun showGridFailureMessage(failure: DikcizGridFailure) {
        Toast.makeText(this, gridFailureMessageResourceID(failure), Toast.LENGTH_LONG).show()
    }

    private fun gridFailureMessageResourceID(failure: DikcizGridFailure): Int {
        return when (failure) {
            DikcizGridFailure.PageFull -> R.string.dikciz_grid_page_full
            DikcizGridFailure.GridCollision -> R.string.dikciz_grid_collision
            DikcizGridFailure.GridBounds -> R.string.dikciz_grid_bounds
        }
    }

    /**
     * Leaves a freshly placed item selected. A fixed page never scrolls, so the item is
     * already on screen; selecting it is what makes its controls reachable immediately.
     */
    private fun selectInsertedWidget(pageID: String, widgetID: String) {
        if (homeConfiguration?.selectedPageID != pageID) {
            return
        }
        widgetContainer.post {
            if (homeConfiguration?.selectedPageID != pageID) {
                return@post
            }
            val widget = homeConfiguration?.selectedPage()?.findWidget(widgetID) ?: return@post
            val widgetView = automationViews[widgetSemanticID(widgetID)] as? DikcizWidgetFrameLayout
                ?: return@post
            if (selectedPageOrWidgetIsLocked(widgetID)) {
                return@post
            }
            // Outline only. Entering resize mode here would disable the move gesture zone,
            // and the new item must accept both move and resize straight away.
            widgetView.resizeOverlay = createWidgetResizeOverlay(
                widget = widget,
                resizeHandles = emptySet(),
            )
        }
    }

    /**
     * The scroll offset that brings a descendant into view. A fixed native page normally
     * has nothing to scroll, so this resolves to the origin unless a container below the
     * page owns its own scrolling.
     */
    private fun pageScrollTargetY(view: View): Int? {
        var scrollY = SCROLL_Y_ORIGIN
        var descendant = view
        while (descendant !== widgetContainer) {
            scrollY += descendant.top
            descendant = descendant.parent as? View ?: return null
        }
        return scrollY.coerceAtLeast(SCROLL_Y_ORIGIN)
    }

    private fun startWidgetMove(
        widget: HomeWidget,
        view: View,
        initialRawX: Float,
        initialRawY: Float,
    ): Boolean {
        val page = homeConfiguration?.selectedPage() ?: return false
        if (page.widgets.none { it.id == widget.id }) {
            dikcizLogger.warn(
                EVENT_WIDGET_MOVE_REJECTED,
                mapOf(
                    FIELD_REASON to REASON_WIDGET_NOT_TOP_LEVEL,
                    FIELD_WIDGET_ID to widget.id,
                ),
            )
            return false
        }
        if (view.width <= NO_PADDING || view.height <= NO_PADDING) {
            dikcizLogger.warn(
                EVENT_WIDGET_MOVE_REJECTED,
                mapOf(
                    FIELD_REASON to REASON_MOVE_TARGET_UNAVAILABLE,
                    FIELD_WIDGET_ID to widget.id,
                ),
            )
            return false
        }
        val bounds = widgetContainer.cellBounds(widget.cell)
        activeWidgetMove = ActiveWidgetMove(
            widgetID = widget.id,
            widgetView = view,
            initialCell = widget.cell,
            initialBounds = GridWidgetBounds(
                x = bounds.left,
                y = bounds.top,
                width = bounds.width,
                height = bounds.height,
            ),
            initialRawX = initialRawX,
            initialRawY = initialRawY,
            initialTranslationX = view.translationX,
            initialTranslationY = view.translationY,
            lastRawX = initialRawX,
            lastRawY = initialRawY,
            targetCell = widget.cell,
        )
        view.bringToFront()
        view.elevation = densityPixels(WIDGET_MOVE_ELEVATION_DP).toFloat()
        widgetContainer.isGridGuideVisible = true
        dikcizLogger.info(
            EVENT_WIDGET_MOVE_STARTED,
            mapOf(FIELD_WIDGET_ID to widget.id),
        )
        return true
    }

    private fun updateWidgetMove(activeMove: ActiveWidgetMove, event: MotionEvent) {
        updateWidgetMove(activeMove, event.rawX, event.rawY)
    }

    private fun updateWidgetMove(
        activeMove: ActiveWidgetMove,
        rawX: Float,
        rawY: Float,
    ) {
        activeMove.lastRawX = rawX
        activeMove.lastRawY = rawY
        updateWidgetMove(activeMove)
    }

    /**
     * Snaps the drag to whole cells. A candidate that leaves the page or collides keeps the
     * last legal target, so the preview never shows an illegal placement.
     */
    private fun updateWidgetMove(activeMove: ActiveWidgetMove) {
        val page = homeConfiguration?.selectedPage() ?: return
        // A merge or a trade is aimed with the finger, so the tile under the
        // pointer decides them. Plain placement still follows the widget's own
        // top left, which is what positions a large widget predictably.
        val pointer = pointerCell(activeMove)
        if (showWidgetCombinePreview(activeMove, page, pointer)) {
            return
        }
        clearWidgetCombinePreview(activeMove)
        if (showWidgetSwapPreview(activeMove, page, pointer)) {
            return
        }
        clearWidgetSwapPreview(activeMove)
        val candidate = moveCandidateCell(activeMove)
        if (candidate == activeMove.targetCell) {
            return
        }
        val placement = DikcizGridLayoutEngine.moveCandidate(
            grid = nativeGrid(),
            occupied = page.occupiedCells(),
            current = activeMove.initialCell,
            target = candidate,
        )
        if (placement is DikcizGridPlacement.Rejected) {
            activeMove.targetFailure = placement.failure
            setWidgetMoveRejected(activeMove, true)
            return
        }
        activeMove.targetFailure = null
        setWidgetMoveRejected(activeMove, false)
        activeMove.targetCell = candidate
        applyWidgetMovePreview(activeMove)
        dikcizLogger.debug(
            EVENT_WIDGET_MOVE_TARGET_CHANGED,
            mapOf(
                FIELD_COLUMN to candidate.column,
                FIELD_ROW to candidate.row,
                FIELD_WIDGET_ID to activeMove.widgetID,
            ),
        )
    }

    /**
     * Shows the target swelling while the pointer sits on a tile it would join.
     *
     * A merge lands on an occupied cell, which the grid otherwise refuses, so
     * without this the tile would wear the refusal styling right up until the
     * drop quietly made a group instead.
     */
    private fun showWidgetCombinePreview(
        activeMove: ActiveWidgetMove,
        page: HomePage,
        pointer: Pair<Int, Int>,
    ): Boolean {
        val source = page.findWidget(activeMove.widgetID) ?: return false
        val target = page.widgetAtCell(pointer.first, pointer.second, activeMove.widgetID)
            ?: return false
        if (page.locked || source.locked || target.locked) {
            return false
        }
        if (groupedComponents(source, target) == null) {
            return false
        }
        if (activeMove.combineTargetID != target.id) {
            clearWidgetCombinePreview(activeMove)
            activeMove.combineTargetID = target.id
            automationViews[widgetSemanticID(target.id)]?.apply {
                scaleX = WIDGET_COMBINE_TARGET_SCALE
                scaleY = WIDGET_COMBINE_TARGET_SCALE
            }
        }
        activeMove.targetFailure = null
        setWidgetMoveRejected(activeMove, false)
        return true
    }

    /** Returns a merge target to its own size once the pointer leaves it. */
    private fun clearWidgetCombinePreview(activeMove: ActiveWidgetMove) {
        restoreWidgetCombineScale(activeMove)
        activeMove.combineTargetID = null
    }

    /**
     * Shrinks the merge target back without forgetting it.
     *
     * The drop reads the target the drag was previewing, so ending the gesture
     * has to undo the swelling while the offer itself survives long enough to
     * be saved.
     */
    private fun restoreWidgetCombineScale(activeMove: ActiveWidgetMove) {
        val targetID = activeMove.combineTargetID ?: return
        automationViews[widgetSemanticID(targetID)]?.apply {
            scaleX = NO_SCALE_CHANGE
            scaleY = NO_SCALE_CHANGE
        }
    }

    /**
     * Offers a cell trade while the pointer sits on a widget this one can swap with.
     *
     * Both tiles move immediately so the trade is visible before the finger
     * lifts. Returns true while the offer stands, which keeps the ordinary
     * collision refusal from firing on a cell that is deliberately occupied.
     */
    private fun showWidgetSwapPreview(
        activeMove: ActiveWidgetMove,
        page: HomePage,
        pointer: Pair<Int, Int>,
    ): Boolean {
        val source = page.findWidget(activeMove.widgetID) ?: return false
        val target = page.widgetAtCell(pointer.first, pointer.second, activeMove.widgetID)
            ?: return false
        if (page.locked || source.locked || target.locked) {
            return false
        }
        if (!canSwapWidgetCells(source, target)) {
            return false
        }
        if (activeMove.swapPartnerID != target.id) {
            clearWidgetSwapPreview(activeMove)
            activeMove.swapPartnerID = target.id
        }
        activeMove.targetFailure = null
        setWidgetMoveRejected(activeMove, false)
        activeMove.targetCell = target.cell
        applyWidgetMovePreview(activeMove)
        translateWidgetToCell(target.id, target.cell, activeMove.initialCell)
        return true
    }

    /** Sends a swap partner back to its own cell once the pointer leaves it. */
    private fun clearWidgetSwapPreview(activeMove: ActiveWidgetMove) {
        val partnerID = activeMove.swapPartnerID ?: return
        activeMove.swapPartnerID = null
        automationViews[widgetSemanticID(partnerID)]?.apply {
            translationX = NO_TRANSLATION_X
            translationY = NO_TRANSLATION_Y
        }
    }

    private fun translateWidgetToCell(
        widgetID: String,
        fromCell: DikcizGridRectangle,
        toCell: DikcizGridRectangle,
    ) {
        val view = automationViews[widgetSemanticID(widgetID)] ?: return
        val from = widgetContainer.cellBounds(fromCell)
        val to = widgetContainer.cellBounds(toCell)
        view.translationX = (to.left - from.left).toFloat()
        view.translationY = (to.top - from.top).toFloat()
    }

    /** The grid cell the dragging finger is currently over. */
    private fun pointerCell(activeMove: ActiveWidgetMove): Pair<Int, Int> {
        val location = IntArray(SCREEN_LOCATION_VALUES)
        widgetContainer.getLocationOnScreen(location)
        return widgetContainer.cellAt(
            activeMove.lastRawX.roundToInt() - location[SCREEN_LOCATION_X],
            activeMove.lastRawY.roundToInt() - location[SCREEN_LOCATION_Y],
        )
    }

    private fun moveCandidateCell(activeMove: ActiveWidgetMove): DikcizGridRectangle {
        val grid = nativeGrid()
        val draggedX = activeMove.initialBounds.x +
            (activeMove.lastRawX - activeMove.initialRawX).roundToInt()
        val draggedY = activeMove.initialBounds.y +
            (activeMove.lastRawY - activeMove.initialRawY).roundToInt()
        val (column, row) = widgetContainer.cellAt(
            draggedX + widgetContainer.paddingLeft,
            draggedY + widgetContainer.paddingTop,
        )
        val cell = activeMove.initialCell
        return cell.copy(
            column = column.coerceIn(FIRST_CELL_INDEX, grid.columns - cell.columnSpan),
            row = row.coerceIn(FIRST_CELL_INDEX, grid.rows - cell.rowSpan),
        )
    }

    private fun setWidgetMoveRejected(activeMove: ActiveWidgetMove, isRejected: Boolean) {
        val overlay = (activeMove.widgetView as? DikcizWidgetFrameLayout)?.resizeOverlay
        (overlay as? WidgetResizeSelectionDrawable)?.isRejected = isRejected
    }

    private fun applyWidgetMovePreview(activeMove: ActiveWidgetMove) {
        val targetBounds = widgetContainer.cellBounds(activeMove.targetCell)
        activeMove.widgetView.translationX =
            (targetBounds.left - activeMove.initialBounds.x).toFloat()
        activeMove.widgetView.translationY =
            (targetBounds.top - activeMove.initialBounds.y).toFloat()
    }

    private fun finishWidgetMove(activeMove: ActiveWidgetMove) {
        if (activeWidgetMove !== activeMove) {
            return
        }
        resetWidgetMovePreview(activeMove)
        activeWidgetMove = null
        if (persistWidgetSwap(activeMove)) {
            return
        }
        if (combineDroppedAppWidget(activeMove)) {
            return
        }
        persistWidgetMove(activeMove)
    }

    /** Commits the cell trade the drag was previewing, if it was offering one. */
    private fun persistWidgetSwap(activeMove: ActiveWidgetMove): Boolean {
        val partnerID = activeMove.swapPartnerID ?: return false
        activeMove.swapPartnerID = null
        val didSwap = updateSelectedPage { page ->
            page.withSwappedWidgetCells(activeMove.widgetID, partnerID)
        }
        if (!didSwap) {
            return false
        }
        dikcizLogger.info(
            EVENT_WIDGET_CELLS_SWAPPED,
            mapOf(
                FIELD_WIDGET_ID to activeMove.widgetID,
                FIELD_TARGET_WIDGET_ID to partnerID,
            ),
        )
        return true
    }

    /**
     * Turns a drop onto another app tile into one app group.
     *
     * A collision normally refuses the move. Dropping one app tile onto another,
     * or onto a group, is the launcher gesture for grouping instead, so this runs
     * before the ordinary move is saved and consumes the drop when it applies.
     */
    private fun combineDroppedAppWidget(activeMove: ActiveWidgetMove): Boolean {
        val page = homeConfiguration?.selectedPage() ?: return false
        if (page.locked) {
            return false
        }
        val source = page.findWidget(activeMove.widgetID) ?: return false
        val targetID = activeMove.combineTargetID ?: return false
        val target = page.findWidget(targetID) ?: return false
        if (source.locked || target.locked) {
            return false
        }
        val components = groupedComponents(source, target) ?: return false
        activeMove.combineTargetID = null
        val wasGroup = target is AppGroupHomeWidget
        val groupTitle = getString(R.string.dikciz_app_group_default_title)
        val groupID = if (wasGroup) target.id else newWidgetID(groupTitle) ?: return false
        val didCombine = updateSelectedPage { currentPage ->
            currentPage.withCombinedAppGroup(
                sourceID = activeMove.widgetID,
                targetID = target.id,
                groupID = groupID,
                groupTitle = groupTitle,
                components = components,
            )
        }
        if (!didCombine) {
            return false
        }
        dikcizLogger.info(
            if (wasGroup) EVENT_APP_GROUP_MEMBER_ADDED else EVENT_APP_GROUP_CREATED,
            mapOf(
                FIELD_WIDGET_ID to groupID,
                FIELD_APP_GROUP_MEMBER_COUNT to components.size,
            ),
        )
        return true
    }

    private fun cancelWidgetMove(activeMove: ActiveWidgetMove) {
        if (activeWidgetMove !== activeMove) {
            return
        }
        resetWidgetMovePreview(activeMove)
        activeWidgetMove = null
        dikcizLogger.debug(
            EVENT_WIDGET_MOVE_CANCELLED,
            mapOf(FIELD_WIDGET_ID to activeMove.widgetID),
        )
    }

    private fun resetWidgetMovePreview(activeMove: ActiveWidgetMove) {
        widgetContainer.isGridGuideVisible = false
        restoreWidgetCombineScale(activeMove)
        activeMove.widgetView.elevation = NO_ELEVATION
        setWidgetMoveRejected(activeMove, false)
        homeConfiguration?.selectedPage()?.widgets?.forEach { widget ->
            automationViews[widgetSemanticID(widget.id)]?.apply {
                translationX = if (widget.id == activeMove.widgetID) {
                    activeMove.initialTranslationX
                } else {
                    NO_TRANSLATION_X
                }
                translationY = if (widget.id == activeMove.widgetID) {
                    activeMove.initialTranslationY
                } else {
                    NO_TRANSLATION_Y
                }
            }
        }
    }

    private fun persistWidgetMove(activeMove: ActiveWidgetMove): Boolean {
        activeMove.targetFailure?.let { failure ->
            showGridFailureMessage(failure)
        }
        if (activeMove.targetCell == activeMove.initialCell) {
            return true
        }
        return updateSelectedPage { page ->
            page.moveWidget(activeMove.widgetID, activeMove.targetCell)
        }.also { didMove ->
            if (didMove) {
                dikcizLogger.info(
                    EVENT_WIDGET_MOVED,
                    mapOf(
                        FIELD_COLUMN to activeMove.targetCell.column,
                        FIELD_ROW to activeMove.targetCell.row,
                        FIELD_WIDGET_ID to activeMove.widgetID,
                    ),
                )
            }
        }
    }

    private fun updateSelectedPage(
        shouldRender: Boolean = true,
        transform: (HomePage) -> HomePage,
    ): Boolean {
        return updateConfiguration(shouldRender) { configuration ->
            val selectedPage = configuration.selectedPage() ?: return@updateConfiguration configuration
            configuration.replacePage(selectedPage.id, transform)
        }
    }

    private fun updateConfiguration(
        shouldRender: Boolean = true,
        transform: (HomeConfiguration) -> HomeConfiguration,
    ): Boolean {
        if (isSafeMode) {
            dikcizLogger.warn(EVENT_SAFE_MODE_MUTATION_REJECTED)
            Toast.makeText(this, R.string.dikciz_safe_mode_message, Toast.LENGTH_LONG).show()
            return false
        }
        val currentConfiguration = homeConfiguration ?: return false
        val updatedConfiguration = transform(currentConfiguration)
        dikcizLogger.debug(
            EVENT_CONFIGURATION_SAVE_STARTED,
            mapOf(FIELD_PAGE_COUNT to updatedConfiguration.pages.size),
        )
        try {
            homeConfigStore.save(updatedConfiguration)
        } catch (exception: HomeConfigException) {
            dikcizLogger.error(
                EVENT_CONFIGURATION_SAVE_FAILED,
                mapOf(
                    FIELD_ERROR_CLASS to exception::class.java.simpleName,
                    FIELD_ERROR_SUMMARY to exception.message.orEmpty(),
                ),
            )
            Toast.makeText(this, exception.message, Toast.LENGTH_LONG).show()
            return false
        }
        cleanupRemovedProviderWidgets(currentConfiguration, updatedConfiguration)
        homeConfiguration = updatedConfiguration
        syncAutomationService(updatedConfiguration)
        if (homeConfigFileObservers.isNotEmpty()) {
            replaceConfigurationObservers(updatedConfiguration)
        }
        dikcizLogger.debug(EVENT_CONFIGURATION_SAVE_COMPLETED)
        if (shouldRender) {
            renderConfiguration()
        }
        return true
    }

    private fun syncAutomationService(configuration: HomeConfiguration) {
        val serviceIntent = Intent(this, DikcizAutomationService::class.java)
        if (isSafeMode) {
            DikcizClipboardMonitor.stop()
            stopService(serviceIntent)
            return
        }
        DikcizClipboardMonitor.sync(this, configuration)
        if (!configuration.requiresBackgroundAutomationService()) {
            stopService(serviceIntent)
            return
        }
        requestAutomationServiceStart()
    }

    private fun requestAutomationServiceStart(): Boolean {
        val serviceIntent = Intent(this, DikcizAutomationService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
                return true
            }
            startService(serviceIntent)
            return true
        } catch (exception: IllegalStateException) {
            dikcizLogger.warn(
                EVENT_AUTOMATION_SERVICE_START_REJECTED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            return false
        } catch (exception: SecurityException) {
            dikcizLogger.warn(
                EVENT_AUTOMATION_SERVICE_START_REJECTED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            return false
        }
    }

    private fun syncAutomationServiceThroughAutomation(): JSONObject {
        if (homeConfiguration == null) {
            throw automationFailure(MESSAGE_CONFIGURATION_UNAVAILABLE)
        }
        val startRequested = requestAutomationServiceStart()
        dikcizLogger.info(EVENT_AUTOMATION_SERVICE_SYNC_REQUESTED)
        return JSONObject().put(KEY_AUTOMATION_SERVICE_SYNCED, startRequested)
    }

    private fun cleanupRemovedProviderWidgets(
        currentConfiguration: HomeConfiguration,
        updatedConfiguration: HomeConfiguration,
    ) {
        val retainedAppWidgetIDs = updatedConfiguration.providerAppWidgetIDs()
        val removedAppWidgetIDs = currentConfiguration.providerAppWidgetIDs()
            .filterNot(retainedAppWidgetIDs::contains)
        removedAppWidgetIDs.forEach(appWidgetHost::deleteAppWidgetId)
        if (removedAppWidgetIDs.isNotEmpty()) {
            dikcizLogger.info(
                EVENT_PROVIDER_WIDGET_IDS_REMOVED,
                mapOf(FIELD_REMOVED_WIDGET_COUNT to removedAppWidgetIDs.size),
            )
        }
    }

    private fun launchExplicitComponent(component: ComponentName): Boolean {
        if (!resolvesActivity(component)) {
            dikcizLogger.warn(
                EVENT_EXPLICIT_APP_LAUNCH_REJECTED,
                mapOf(FIELD_REASON to REASON_ACTIVITY_UNAVAILABLE),
            )
            Toast.makeText(this, R.string.dikciz_unavailable_app, Toast.LENGTH_SHORT).show()
            return false
        }
        try {
            startActivity(Intent().setComponent(component))
            dikcizLogger.info(EVENT_EXPLICIT_APP_LAUNCH_STARTED)
            return true
        } catch (exception: ActivityNotFoundException) {
            dikcizLogger.warn(
                EVENT_EXPLICIT_APP_LAUNCH_FAILED,
                mapOf(
                    FIELD_ERROR_CLASS to exception::class.java.simpleName,
                    FIELD_ERROR_SUMMARY to exception.message.orEmpty(),
                ),
            )
            Toast.makeText(this, R.string.dikciz_unavailable_app, Toast.LENGTH_SHORT).show()
            return false
        }
    }

    private fun resolvesActivity(component: ComponentName): Boolean {
        return packageManager.resolveActivity(Intent().setComponent(component), RESOLVE_ACTIVITY_FLAGS) != null
    }

    private fun showStorageAccessRequirement() {
        stopConfigurationObserver()
        setEditingControlsEnabled(false)
        isConfigurationErrorVisible = false
        automationViews.clear()
        providerAutomationViews.clear()
        val bundledTheme = try {
            dikcizThemeStore.loadDefaultBundledTheme()
        } catch (exception: HomeConfigException) {
            dikcizLogger.warn(
                EVENT_THEME_CATALOGUE_LOAD_FAILED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            null
        }
        val messageStyle = bundledTheme?.widgetStyle ?: DikcizStyle()
        applyLauncherBackground(bundledTheme?.launcherBackground ?: DikcizLauncherBackground())
        widgetContainer.removeAllViews()
        widgetContainer.addView(createThemedMessagePanel(messageStyle).apply {
            addView(createWidgetTitle(getString(R.string.dikciz_storage_access_title), messageStyle))
            addView(TextView(this@DikcizHomeActivity).apply {
                text = getString(R.string.dikciz_storage_access_body)
                setTextColor(getColor(R.color.dikciz_foreground))
                dikcizStyleRenderer.applyTextStyle(this, messageStyle)
            })
            val grantButton = Button(this@DikcizHomeActivity).apply {
                text = getString(R.string.dikciz_storage_access_grant)
                dikcizStyleRenderer.applyTextStyle(this, messageStyle)
                setOnClickListener { openStorageAccessSettings() }
            }
            addView(grantButton)
            registerAutomationView(SEMANTIC_STORAGE_ACCESS_GRANT, grantButton)
        })
    }

    private fun openStorageAccessSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                LEGACY_STORAGE_PERMISSION_REQUEST_CODE,
            )
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        try {
            startActivity(intent)
            dikcizLogger.info(EVENT_STORAGE_SETTINGS_OPENED)
        } catch (exception: ActivityNotFoundException) {
            dikcizLogger.error(
                EVENT_STORAGE_SETTINGS_LAUNCH_FAILED,
                mapOf(
                    FIELD_ERROR_CLASS to exception::class.java.simpleName,
                    FIELD_ERROR_SUMMARY to exception.message.orEmpty(),
                ),
            )
            Toast.makeText(this, exception.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showConfigurationError(exception: HomeConfigException) {
        setEditingControlsEnabled(false)
        isConfigurationErrorVisible = true
        automationViews.clear()
        providerAutomationViews.clear()
        startConfigurationErrorObserver()
        widgetContainer.removeAllViews()
        widgetContainer.addView(createMessagePanel().apply {
            addView(createWidgetTitle(getString(R.string.dikciz_error_title)))
            addView(TextView(this@DikcizHomeActivity).apply {
                text = getString(
                    R.string.dikciz_error_body,
                    "${homeConfigStore.configurationFile.absolutePath}\n${exception.message}",
                )
                setTextColor(getColor(R.color.dikciz_foreground))
            })
        })
    }

    private fun createMessagePanel(): LinearLayout {
        val padding = standardPadding()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
    }

    private fun createThemedMessagePanel(style: DikcizStyle): LinearLayout {
        val frameStyle = dikcizStyleRenderer.resolveWidgetFrameStyle(
            style = style,
            isEnabled = true,
            defaultCornerRadiusPixels = densityPixels(WIDGET_CORNER_RADIUS_DP).toFloat(),
        )
        return createMessagePanel().apply {
            background = dikcizStyleRenderer.createBackground(frameStyle)
            setPadding(
                frameStyle.padding.left,
                frameStyle.padding.top,
                frameStyle.padding.right,
                frameStyle.padding.bottom,
            )
        }
    }

    private fun setEditingControlsEnabled(isEnabled: Boolean) {
        widgetContainer.isEnabled = isEnabled
    }

    private fun standardPadding(): Int {
        return densityPixels(STANDARD_PADDING_DP)
    }

    private fun densityPixels(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun pixelsToDP(pixels: Int): Int {
        return (pixels / resources.displayMetrics.density).roundToInt()
    }

    private fun newPageID(): String {
        return "$PAGE_ID_PREFIX-${UUID.randomUUID()}"
    }

    private fun newWidgetIDOrShowError(title: String): String? {
        val widgetID = newWidgetID(title)
        if (widgetID != null) {
            return widgetID
        }
        dikcizLogger.warn(
            EVENT_WIDGET_ID_GENERATION_REJECTED,
            mapOf(FIELD_REASON to REASON_WIDGET_ID_CAPACITY),
        )
        Toast.makeText(this, R.string.dikciz_widget_id_unavailable, Toast.LENGTH_LONG).show()
        return null
    }

    private fun newScriptIDOrShowError(title: String): String? {
        val scriptID = newScriptID(title)
        if (scriptID != null) {
            return scriptID
        }
        dikcizLogger.warn(
            EVENT_LUA_SCRIPT_ID_GENERATION_REJECTED,
            mapOf(FIELD_REASON to REASON_SCRIPT_ID_CAPACITY),
        )
        Toast.makeText(this, R.string.dikciz_script_id_unavailable, Toast.LENGTH_LONG).show()
        return null
    }

    private fun newWidgetID(title: String): String? {
        val configuration = homeConfiguration ?: return null
        val usedIDs = configuration.pages
            .flatMap(HomePage::widgets)
            .mapTo(mutableSetOf(), HomeWidget::id)
        val slug = slugifyWidgetTitle(title)
        for (suffix in FIRST_WIDGET_ID_SUFFIX..MAXIMUM_WIDGET_ID_SUFFIX) {
            val candidate = suffixedWidgetID(
                slug = slug,
                suffix = suffix,
                maximumLength = configuration.limits.maxIdentifierCharacters,
            ) ?: return null
            if (candidate !in usedIDs) {
                return candidate
            }
        }
        return null
    }

    private fun newScriptID(title: String): String? {
        val configuration = homeConfiguration ?: return null
        val usedIDs = configuration.scripts
            .mapTo(mutableSetOf()) { script -> script.id.lowercase(Locale.ROOT) }
        val slug = slugifyWidgetTitle(title)
        for (suffix in FIRST_WIDGET_ID_SUFFIX..MAXIMUM_WIDGET_ID_SUFFIX) {
            val candidate = suffixedWidgetID(
                slug = slug,
                suffix = suffix,
                maximumLength = configuration.limits.maxIdentifierCharacters,
            ) ?: return null
            if (candidate !in usedIDs) {
                return candidate
            }
        }
        return null
    }

    private fun slugifyWidgetTitle(title: String): String {
        val normalizedTitle = Normalizer.normalize(title, Normalizer.Form.NFKD)
            .replace(COMBINING_MARKS_PATTERN, EMPTY_QUERY)
            .lowercase(Locale.ROOT)
        return normalizedTitle
            .replace(NON_WIDGET_ID_CHARACTER_PATTERN, WIDGET_ID_SEPARATOR)
            .trim(WIDGET_ID_SEPARATOR_CHARACTER)
            .ifEmpty { WIDGET_ID_FALLBACK_SLUG }
    }

    private fun suffixedWidgetID(
        slug: String,
        suffix: Int,
        maximumLength: Int,
    ): String? {
        val suffixText = "$WIDGET_ID_SEPARATOR$suffix"
        val maximumSlugLength = maximumLength - suffixText.length
        if (maximumSlugLength < MINIMUM_WIDGET_ID_SLUG_LENGTH) {
            return null
        }
        val prefix = slug.take(maximumSlugLength)
            .trimEnd(WIDGET_ID_SEPARATOR_CHARACTER)
            .ifEmpty { WIDGET_ID_FALLBACK_SLUG.take(maximumSlugLength) }
        return "$prefix$suffixText"
    }

    private fun widgetReferenceLabel(page: HomePage, widget: HomeWidget): String {
        return getString(
            R.string.dikciz_widget_reference_label,
            widget.title,
            widget.id,
            page.referenceLocation(),
        )
    }

    @Suppress("DEPRECATION")
    private class HomeConfigFileObserver(
        directory: File,
        private val onConfigurationChange: () -> Unit,
        private val ignoredPaths: Set<String> = emptySet(),
    ) : FileObserver(directory.absolutePath, CONFIGURATION_FILE_EVENTS) {
        override fun onEvent(event: Int, path: String?) {
            if (path == null || path in ignoredPaths) {
                return
            }
            onConfigurationChange()
        }
    }

    private data class AppearanceEditorTarget(
        val semanticID: String,
        val scope: String,
        val pageID: String? = null,
        val widgetID: String? = null,
    ) {
        fun logFields(): Map<String, Any> = buildMap {
            put(FIELD_APPEARANCE_SCOPE, scope)
            pageID?.let { put(FIELD_SELECTED_PAGE_ID, it) }
            widgetID?.let { put(FIELD_WIDGET_ID, it) }
        }
    }

    private enum class WidgetResizeFillAxis(
        val persistedValue: String,
        val semanticAction: String,
        val viewTag: String,
    ) {
        Width(
            persistedValue = "width",
            semanticAction = SEMANTIC_ACTION_RESIZE_FILL_WIDTH,
            viewTag = WIDGET_RESIZE_WIDTH_FILL_TAG,
        ),
        Height(
            persistedValue = "height",
            semanticAction = SEMANTIC_ACTION_RESIZE_FILL_HEIGHT,
            viewTag = WIDGET_RESIZE_HEIGHT_FILL_TAG,
        ),
        ;
    }

    private sealed interface CommandSheetEntry {
        val icon: Drawable?
            get() = null

        fun label(activity: Activity): String

        fun matches(query: String): Boolean
    }

    private data class ManagePageCommand(
        val page: HomePage,
    ) : CommandSheetEntry {
        override fun label(activity: Activity): String {
            return activity.getString(R.string.dikciz_command_manage_page, page.title)
        }

        override fun matches(query: String): Boolean {
            return DikcizAppCatalogue.matchesQuery(
                query,
                page.title,
                COMMAND_MANAGE_PAGE_KEYWORD,
            )
        }
    }

    private data object SettingsCommand : CommandSheetEntry {
        override fun label(activity: Activity): String {
            return activity.getString(R.string.dikciz_command_settings)
        }

        override fun matches(query: String): Boolean {
            return DikcizAppCatalogue.matchesQuery(query, COMMAND_SETTINGS_KEYWORD)
        }
    }

    private data class SelectPageCommand(
        val page: HomePage,
    ) : CommandSheetEntry {
        override fun label(activity: Activity): String {
            return activity.getString(R.string.dikciz_command_select_page, page.title)
        }

        override fun matches(query: String): Boolean {
            return DikcizAppCatalogue.matchesQuery(
                query,
                page.title,
                COMMAND_SELECT_PAGE_KEYWORD,
            )
        }
    }

    private data class LaunchableAppCommand(
        val application: DikcizLaunchableApp,
    ) : CommandSheetEntry {
        override val icon: Drawable
            get() = application.icon

        override fun label(activity: Activity): String = application.label

        override fun matches(query: String): Boolean {
            return DikcizAppCatalogue.matchesQuery(
                query,
                application.label,
                application.component.flattenToString(),
            )
        }
    }

    private companion object {
        private var hasShownStartupBrandInCurrentProcess = false

        const val APP_WIDGET_BIND_REQUEST_CODE = 2
        const val APP_WIDGET_CONFIGURATION_REQUEST_CODE = 3
        const val APP_WIDGET_RECONFIGURATION_REQUEST_CODE = 4
        const val AUTOMATION_LOCATION_PERMISSION_REQUEST_CODE = 5
        const val AUTOMATION_NOTIFICATION_PERMISSION_REQUEST_CODE = 6
        const val AUTOMATION_SENSOR_PERMISSION_REQUEST_CODE = 7
        const val AUTOMATION_BLUETOOTH_PERMISSION_REQUEST_CODE = 8
        const val AUTOMATION_CALENDAR_PERMISSION_REQUEST_CODE = 9
        const val AUTOMATION_CONTACTS_PERMISSION_REQUEST_CODE = 14
        const val AUTOMATION_PHONE_STATE_PERMISSION_REQUEST_CODE = 10
        const val AUTOMATION_SMS_PERMISSION_REQUEST_CODE = 11
        const val AUTOMATION_HEALTH_CONNECT_PERMISSION_REQUEST_CODE = 12
        const val AUTOMATION_DEVICE_ADMINISTRATION_REQUEST_CODE = 13
        const val FULL_DEVICE_ACCESS_PERMISSION_REQUEST_CODE = 15
        const val FULL_DEVICE_ACCESS_SMS_ROLE_REQUEST_CODE = 16
        const val APP_WIDGET_HOST_ID = 13_737
        const val APPEARANCE_SCOPE_WIDGET = "widget"
        const val CONFIGURATION_FILE_EVENTS = FileObserver.CLOSE_WRITE or
            FileObserver.CREATE or
            FileObserver.DELETE or
            FileObserver.MOVED_TO
        const val CONFIGURATION_RELOAD_DEBOUNCE_MS = 150L
        const val CONFIGURATION_POLL_INTERVAL_MS = 2000L
        const val FINGERPRINT_ENTRY_SEPARATOR = "|"
        const val EVENT_ACTIVITY_CREATED = "activity_created"
        const val EVENT_ACTIVITY_STARTED = "activity_started"
        const val EVENT_ACTIVITY_STOPPED = "activity_stopped"
        const val EVENT_DEVICE_ACCESS_REQUEST_STARTED = "device_access_request_started"
        const val EVENT_DEVICE_ACCESS_REQUEST_UNAVAILABLE = "device_access_request_unavailable"
        const val EVENT_DEVICE_ACCESS_SETUP_OPENED = "device_access_setup_opened"
        const val EVENT_AUTOMATION_APP_LAUNCHED = "automation_app_launched"
        const val EVENT_ACCESSIBILITY_ACTION_COMPLETED = "accessibility_action_completed"
        const val EVENT_ACCESSIBILITY_ACTION_REJECTED = "accessibility_action_rejected"
        const val EVENT_ACCESSIBILITY_SNAPSHOT_REJECTED = "accessibility_snapshot_rejected"
        const val EVENT_AUTOMATION_CONFIGURATION_READ_REJECTED = "automation_configuration_read_rejected"
        const val EVENT_AUTOMATION_CONFIGURATION_REPLACED = "automation_configuration_replaced"
        const val EVENT_AUTOMATION_CONFIGURATION_REPLACE_REJECTED = "automation_configuration_replace_rejected"
        const val EVENT_AUTOMATION_INTENT_DISPATCHED = "automation_intent_dispatched"
        const val EVENT_AUTOMATION_INTENT_REJECTED = "automation_intent_rejected"
        const val EVENT_AUTOMATION_HEALTH_CONNECT_PERMISSION_REJECTED =
            "automation_health_connect_permission_rejected"
        const val EVENT_AUTOMATION_DEVICE_ADMINISTRATION_REQUEST_REJECTED =
            "automation_device_administration_request_rejected"
        const val EVENT_AUTOMATION_LONG_PRESSED = "automation_long_pressed"
        const val EVENT_AUTOMATION_SERVICE_START_REJECTED = "automation_service_start_rejected"
        const val EVENT_AUTOMATION_SERVICE_SYNC_REQUESTED = "automation_service_sync_requested"
        const val EVENT_AUTOMATION_SCROLLED = "automation_scrolled"
        const val EVENT_AUTOMATION_SCROLLED_TO = "automation_scrolled_to"
        const val EVENT_AUTOMATION_TAPPED = "automation_tapped"
        const val EVENT_AUTOMATION_TEXT_SET = "automation_text_set"
        const val EVENT_AUTOMATION_UI_RENDERED = DikcizAutomationControlPlane.EVENT_UI_RENDERED
        const val EVENT_AUTOMATION_SHELL_COMPLETED = "automation_shell_completed"
        const val EVENT_AUTOMATION_SHELL_FAILED = "automation_shell_failed"
        const val EVENT_AUTOMATION_SHELL_REJECTED = "automation_shell_rejected"
        const val EVENT_AUTOMATION_SHELL_STARTED = "automation_shell_started"
        const val EVENT_APP_CATALOGUE_LOADED = "app_catalogue_loaded"
        const val EVENT_APP_CATALOGUE_LOAD_REJECTED = "app_catalogue_load_rejected"
        const val EVENT_APP_GROUP_CREATED = "app_group_created"
        const val EVENT_APP_GROUP_EDITED = "app_group_edited"
        const val EVENT_APP_SHORTCUT_CREATED = "app_shortcut_created"
        const val EVENT_APP_SHORTCUT_ICON_UNAVAILABLE = "app_shortcut_icon_unavailable"
        const val EVENT_APP_SHORTCUT_REJECTED = "app_shortcut_rejected"
        const val EVENT_APP_SHORTCUT_RETARGETED = "app_shortcut_retargeted"
        const val EVENT_APP_WIDGET_EDITED = "app_widget_edited"
        const val EVENT_APPEARANCE_EDITOR_OPENED = "appearance_editor_opened"
        const val EVENT_APPEARANCE_RESET = "appearance_reset"
        const val EVENT_APPEARANCE_SAVE_REJECTED = "appearance_save_rejected"
        const val EVENT_APPEARANCE_SAVED = "appearance_saved"
        const val EVENT_COMMAND_SHEET_FILTERED = "command_sheet_filtered"
        const val EVENT_COMMAND_SHEET_OPENED = "command_sheet_opened"
        const val EVENT_CONFIGURATION_FILE_CHANGED = "configuration_file_changed"
        const val EVENT_CONFIGURATION_ASSETS_RELOADED = "configuration_assets_reloaded"
        const val EVENT_CONFIGURATION_OBSERVER_ALREADY_RUNNING = "configuration_observer_already_running"
        const val EVENT_CONFIGURATION_OBSERVER_STARTED = "configuration_observer_started"
        const val EVENT_CONFIGURATION_OBSERVER_STOPPED = "configuration_observer_stopped"
        const val EVENT_CONFIGURATION_RELOAD_COMPLETED = "configuration_reload_completed"
        const val EVENT_CONFIGURATION_RELOAD_REJECTED = "configuration_reload_rejected"
        const val EVENT_CONFIGURATION_RELOAD_STARTED = "configuration_reload_started"
        const val EVENT_CONFIGURATION_RUNTIME_STATE_APPLIED = "configuration_runtime_state_applied"
        const val EVENT_CONFIGURATION_RELOAD_UNCHANGED = "configuration_reload_unchanged"
        const val EVENT_CONFIGURATION_SAVE_COMPLETED = "configuration_save_completed"
        const val EVENT_CONFIGURATION_SAVE_FAILED = "configuration_save_failed"
        const val EVENT_CONFIGURATION_SAVE_STARTED = "configuration_save_started"
        const val EVENT_CONFIGURATION_RESET_COMPLETED = "configuration_reset_completed"
        const val EVENT_CONFIGURATION_RESET_FAILED = "configuration_reset_failed"
        const val EVENT_CONFIGURATION_RESET_SOURCE_UNAVAILABLE = "configuration_reset_source_unavailable"
        const val EVENT_CONFIGURATION_RESET_STARTED = "configuration_reset_started"
        const val EVENT_EXPLICIT_APP_LAUNCH_REJECTED = "explicit_app_launch_rejected"
        const val EVENT_EXPLICIT_APP_LAUNCH_STARTED = "explicit_app_launch_started"
        const val EVENT_EXPLICIT_APP_LAUNCH_FAILED = "explicit_app_launch_failed"
        const val EVENT_FONT_FALLBACK = "font_fallback"
        const val EVENT_HOME_RENDERED = "home_rendered"
        const val EVENT_HOME_PAGE_CHANGED = "home_page_changed"
        const val EVENT_HOME_PAGE_SET_SKIPPED = "home_page_set_skipped"
        const val EVENT_HTML_WIDGET_CREATED = "html_widget_created"
        const val EVENT_HTML_WIDGET_CONTENT_FIT_APPLIED = "html_widget_content_fit_applied"
        const val EVENT_HTML_WIDGET_CONTENT_FIT_REQUESTED = "html_widget_content_fit_requested"
        const val EVENT_HTML_WIDGET_EDITED = "html_widget_edited"
        const val EVENT_HTML_WIDGET_LIBRARY_OPENED = "html_widget_library_opened"
        const val EVENT_HTML_WIDGET_LIBRARY_REJECTED = "html_widget_library_rejected"
        const val EVENT_HTML_WIDGET_LIBRARY_SAVED = "html_widget_library_saved"
        const val EVENT_HTML_WIDGET_LIBRARY_SAVE_REJECTED = "html_widget_library_save_rejected"
        const val EVENT_HTML_WIDGET_PREVIEW_OPENED = "html_widget_preview_opened"
        const val EVENT_HTML_WIDGET_RENDERER_RECOVERED = "html_widget_renderer_recovered"
        const val EVENT_LAUNCHER_BACKGROUND_RENDERED = "launcher_background_rendered"
        const val EVENT_LAUNCHER_WALLPAPER_FALLBACK = "launcher_wallpaper_fallback"
        const val EVENT_SCRIPT_DASHBOARD_WIDGET_CREATED = "script_dashboard_widget_created"
        const val EVENT_SCRIPT_LOG_VIEWER_OPENED = "script_log_viewer_opened"
        const val EVENT_LUA_SCRIPT_CREATED = "lua_script_created"
        const val EVENT_LUA_SCRIPT_DELETED = "lua_script_deleted"
        const val EVENT_LUA_SCRIPT_EDITED = "lua_script_edited"
        const val EVENT_LUA_SCRIPT_EXPORTED = "lua_script_exported"
        const val EVENT_LUA_SCRIPT_EXPORT_FAILED = "lua_script_export_failed"
        const val EVENT_LUA_SCRIPT_ID_GENERATION_REJECTED = "lua_script_id_generation_rejected"
        const val EVENT_LUA_SCRIPT_IMPORTED = "lua_script_imported"
        const val EVENT_LUA_SCRIPT_IMPORT_REJECTED = "lua_script_import_rejected"
        const val EVENT_LUA_SCRIPT_IMPORT_SKIPPED = "lua_script_import_skipped"
        const val EVENT_LUA_SCRIPT_MANAGER_OPENED = "lua_script_manager_opened"
        const val EVENT_PAGE_INDICATOR_INTERACTION = "page_indicator_interaction"
        const val EVENT_PAGE_CREATED = "page_created"
        const val EVENT_PAGE_CREATION_REJECTED = "page_creation_rejected"
        const val EVENT_PAGE_DELETED = "page_deleted"
        const val EVENT_PAGE_DELETION_REJECTED = "page_deletion_rejected"
        const val EVENT_PAGE_LOCK_CHANGED = "page_lock_changed"
        const val EVENT_PAGE_MENU_OPENED = "page_menu_opened"
        const val EVENT_PAGE_MOVED = "page_moved"
        const val EVENT_PAGE_MOVE_REJECTED = "page_move_rejected"
        const val EVENT_PAGE_RENAMED = "page_renamed"
        const val EVENT_PAGE_SELECTED = "page_selected"
        const val EVENT_PAGE_SELECTION_STARTED = "page_selection_started"
        const val EVENT_PAGE_SELECTION_REJECTED = "page_selection_rejected"
        const val EVENT_PROVIDER_WIDGET_CREATED = "provider_widget_created"
        const val EVENT_PROVIDER_WIDGET_EDITED = "provider_widget_edited"
        const val EVENT_PROVIDER_WIDGET_CREATION_FAILED = "provider_widget_creation_failed"
        const val EVENT_PROVIDER_WIDGET_HOST_CREATED = "provider_widget_host_created"
        const val EVENT_PROVIDER_WIDGET_IDS_REMOVED = "provider_widget_ids_removed"
        const val EVENT_PROVIDER_WIDGET_SIZE_REPORTED = "provider_widget_size_reported"
        const val EVENT_PROVIDER_WIDGET_UNAVAILABLE = "provider_widget_unavailable"
        const val EVENT_PROVIDER_WIDGET_RECONFIGURATION_COMPLETED = "provider_widget_reconfiguration_completed"
        const val EVENT_PROVIDER_WIDGET_RECONFIGURATION_FAILED = "provider_widget_reconfiguration_failed"
        const val EVENT_PROVIDER_WIDGET_RECONFIGURATION_REJECTED = "provider_widget_reconfiguration_rejected"
        const val EVENT_PROVIDER_WIDGET_RECONFIGURATION_REQUESTED = "provider_widget_reconfiguration_requested"
        const val EVENT_STORAGE_ACCESS_UNAVAILABLE = "storage_access_unavailable"
        const val EVENT_STORAGE_SETTINGS_OPENED = "storage_settings_opened"
        const val EVENT_STORAGE_SETTINGS_LAUNCH_FAILED = "storage_settings_launch_failed"
        const val EVENT_SAFE_MODE_LAUNCH_REQUESTED = "safe_mode_launch_requested"
        const val EVENT_SAFE_MODE_MUTATION_REJECTED = "safe_mode_mutation_rejected"
        const val EVENT_SAFE_MODE_RENDERED = "safe_mode_rendered"
        const val EVENT_SETTINGS_OPENED = "settings_opened"
        const val EVENT_SETTINGS_PAGES_SAVED = "settings_pages_saved"
        const val EVENT_THEME_APPLIED = "theme_applied"
        const val EVENT_THEME_APPLY_REJECTED = "theme_apply_rejected"
        const val EVENT_THEME_CATALOGUE_LOADED = "theme_catalogue_loaded"
        const val EVENT_THEME_CATALOGUE_LOAD_FAILED = "theme_catalogue_load_failed"
        const val EVENT_THEME_CATALOGUE_REJECTED = "theme_catalogue_rejected"
        const val EVENT_WIDGET_BIND_APPROVAL_REQUESTED = "widget_bind_approval_requested"
        const val EVENT_WIDGET_BIND_FAILED = "widget_bind_failed"
        const val EVENT_WIDGET_BIND_REJECTED = "widget_bind_rejected"
        const val EVENT_WIDGET_CONFIGURATION_FAILED = "widget_configuration_failed"
        const val EVENT_WIDGET_CONFIGURATION_REJECTED = "widget_configuration_rejected"
        const val EVENT_WIDGET_CONFIGURATION_REQUESTED = "widget_configuration_requested"
        const val EVENT_WIDGET_DELETED = "widget_deleted"
        const val EVENT_WIDGET_DELETION_REJECTED = "widget_deletion_rejected"
        const val EVENT_WIDGET_ID_GENERATION_REJECTED = "widget_id_generation_rejected"
        const val EVENT_HTML_BLOCK_APPENDED = "html_block_appended"
        const val EVENT_HTML_BLOCK_APPEND_DEFERRED = "html_block_append_deferred"
        const val EVENT_HTML_BLOCK_APPEND_REJECTED = "html_block_append_rejected"
        const val EVENT_HTML_BLOCK_LIBRARY_UNAVAILABLE = "html_block_library_unavailable"
        const val EVENT_PAGE_GRID_REJECTED = "page_grid_rejected"
        const val EVENT_PAGE_GRID_SAVED = "page_grid_saved"
        const val EVENT_WIDGET_INSERTED = "widget_inserted"
        const val EVENT_WIDGET_INSERTION_REJECTED = "widget_insertion_rejected"
        const val EVENT_WIDGET_MOVE_REJECTED = "widget_move_rejected"
        const val EVENT_WIDGET_MOVE_CANCELLED = "widget_move_cancelled"
        const val EVENT_WIDGET_MOVE_POINTER_UPDATED = "widget_move_pointer_updated"
        const val EVENT_WIDGET_MOVE_STARTED = "widget_move_started"
        const val EVENT_WIDGET_MOVE_TARGET_CHANGED = "widget_move_target_changed"
        const val EVENT_WIDGET_MOVED = "widget_moved"
        const val EVENT_WIDGET_CELLS_SWAPPED = "widget_cells_swapped"
        const val EVENT_APP_GROUP_MEMBER_ADDED = "app_group_member_added"
        const val EVENT_APP_GROUP_MEMBER_REMOVED = "app_group_member_removed"
        const val EVENT_APP_GROUP_COLLAPSED = "app_group_collapsed"
        const val EVENT_WIDGET_EDIT_ACTIONS_SHOWN = "widget_edit_actions_shown"
        const val EVENT_WIDGET_LOCK_CHANGED = "widget_lock_changed"
        const val EVENT_WIDGET_LOCKS_SAVED = "widget_locks_saved"
        const val EVENT_WIDGET_PICKER_OPENED = "widget_picker_opened"
        const val EVENT_WIDGET_PICKER_PAGE_FULL = "widget_picker_page_full"
        const val EVENT_WIDGET_RESIZE_FILLED = "widget_resize_filled"
        const val EVENT_WIDGET_RESIZE_CANCELLED = "widget_resize_cancelled"
        const val EVENT_WIDGET_RESIZE_HANDLE_SELECTED = "widget_resize_handle_selected"
        const val EVENT_WIDGET_RESIZE_POINTER_UPDATED = "widget_resize_pointer_updated"
        const val EVENT_WIDGET_RESIZE_REJECTED = "widget_resize_rejected"
        const val EVENT_WIDGET_RESIZE_SAVE_REJECTED = "widget_resize_save_rejected"
        const val EVENT_WIDGET_RESIZE_STARTED = "widget_resize_started"
        const val EVENT_WIDGET_RESIZED = "widget_resized"
        const val FIELD_ACTION = "action"
        const val FIELD_APPEARANCE_SCOPE = "appearance_scope"
        const val FIELD_ARCHIVE_NAME = "archive_name"
        const val FIELD_CHECKED = "checked"
        const val FIELD_CHANGED_WIDGET_COUNT = "changed_widget_count"
        const val FIELD_COMMAND_TYPE = "command_type"
        const val FIELD_OUTCOME = "outcome"
        const val FIELD_COMMAND_LENGTH = "command_length"
        const val FIELD_COMPONENT_COUNT = "component_count"
        const val FIELD_BLOCK_ID = "block_id"
        const val FIELD_BLOCK_INSTANCE_ID = "block_instance_id"
        const val FIELD_COLUMN = "column"
        const val FIELD_COLUMN_SPAN = "column_span"
        const val FIELD_COMPONENT_PACKAGE = "component_package"
        const val FIELD_DURATION_MILLISECONDS = "duration_milliseconds"
        const val FIELD_DEVICE_ACCESS_GRANTED_COUNT = "device_access_granted_count"
        const val FIELD_DEVICE_ACCESS_REQUIREMENT = "device_access_requirement"
        const val FIELD_DEVICE_ACCESS_REQUIREMENT_COUNT = "device_access_requirement_count"
        const val FIELD_DISPLAY_STYLE = "display_style"
        const val FIELD_ENTRY_COUNT = "entry_count"
        const val FIELD_ERROR_CLASS = "error_class"
        const val FIELD_ERROR_SUMMARY = "error_summary"
        const val FIELD_DELTA_Y = "delta_y"
        const val FIELD_FATAL_ON_FAILURE = "fatal_on_failure"
        const val FIELD_FONT_ID = "font_id"
        const val FIELD_FONT_SOURCE = "font_source"
        const val FIELD_FROM_PAGE_INDEX = "from_page_index"
        const val FIELD_FROM_WIDGET_INDEX = "from_widget_index"
        const val FIELD_HEIGHT_DP = "height_dp"
        const val FIELD_HOME_PAGE_ID = "home_page_id"
        const val FIELD_EXIT_CODE = "exit_code"
        const val FIELD_INTENT_TYPE = "intent_type"
        const val FIELD_INSERTION_INDEX = "insertion_index"
        const val FIELD_INSERTION_TARGET = "insertion_target"
        const val FIELD_IS_LOCKED = "is_locked"
        const val FIELD_OUTPUT_TRUNCATED = "output_truncated"
        const val FIELD_APP_GROUP_MEMBER_COUNT = "app_group_member_count"
        const val FIELD_TARGET_WIDGET_ID = "target_widget_id"
        const val FIELD_PAGE_COUNT = "page_count"
        const val FIELD_PAGE_AXIS = "page_axis"
        const val FIELD_PAGE_COLUMN = "page_column"
        const val FIELD_PAGE_DIRECTION = "page_direction"
        const val FIELD_PAGE_INSERTION_SIDE = "page_insertion_side"
        const val FIELD_PAGE_ROW = "page_row"
        const val FIELD_PAGE_ID = "page_id"
        const val FIELD_POPUP_PLACEMENT = "popup_placement"
        const val FIELD_SETTING = "setting"
        const val FIELD_REASON = "reason"
        const val FIELD_REPLACED = "replaced"
        const val FIELD_RESULT_COUNT = "result_count"
        const val FIELD_RAW_X = "raw_x"
        const val FIELD_ROOT_REQUESTED = "root_requested"
        const val FIELD_ROW = "row"
        const val FIELD_ROW_SPAN = "row_span"
        const val FIELD_RAW_Y = "raw_y"
        const val FIELD_REMOVED_WIDGET_COUNT = "removed_widget_count"
        const val FIELD_RESIZE_HANDLE = "resize_handle"
        const val FIELD_RESIZE_FILL_AXIS = "resize_fill_axis"
        const val FIELD_SCROLL_X = "scroll_x"
        const val FIELD_SCRIPT_COUNT = "script_count"
        const val FIELD_SCRIPT_ID = "script_id"
        const val FIELD_SCRIPT_KIND = "script_kind"
        const val FIELD_TEXT = "text"
        const val FIELD_SCRIPT_LOG_RECORD_COUNT = "script_log_record_count"
        const val FIELD_SAFE_MODE = "safe_mode"
        const val FIELD_SELECTED_PAGE_INDEX = "selected_page_index"
        const val FIELD_SELECTED_PAGE_ID = "selected_page_id"
        const val FIELD_SEMANTIC_ID = "semantic_id"
        const val FIELD_REJECTED_THEME_COUNT = "rejected_theme_count"
        const val FIELD_THEME_COUNT = "theme_count"
        const val FIELD_THEME_ID = "theme_id"
        const val FIELD_THEME_STATE = "theme_state"
        const val FIELD_TASK_COUNT = "task_count"
        const val FIELD_TO_PAGE_INDEX = "to_page_index"
        const val FIELD_TO_WIDGET_INDEX = "to_widget_index"
        const val FIELD_WIDGET_COUNT = "widget_count"
        const val FIELD_WIDGET_ID = "widget_id"
        const val FIELD_WIDGET_PACKAGE_ID = "widget_package_id"
        const val FIELD_WIDTH_DP = "width_dp"
        const val FIELD_X_DP = "x_dp"
        const val FIELD_Y_DP = "y_dp"
        const val DISABLED_WIDGET_ALPHA = 0.55F
        const val DISABLED_WIDGET_TEXT_SIZE_SP = 12F
        const val EMPTY_HTML_WIDGET_RESOURCE_VALUE = 0
        const val ENABLED_WIDGET_ALPHA = 1F
        const val FULL_LAYOUT_WEIGHT = 1F
        const val EXTRA_SAFE_MODE = "org.fossify.home.dikciz.extra.SAFE_MODE"
        const val FIRST_WIDGET_INDEX = 0
        const val FIRST_WIDGET_ID_SUFFIX = 1
        const val DIMENSION_DIVISOR = 2
        const val LOCATION_COMPONENT_COUNT = 2
        const val LOCATION_X = 0
        const val LOCATION_Y = 1
        const val MINIMUM_PAGE_COUNT = 1
        const val INITIAL_WALLPAPER_SAMPLE_SIZE = 1
        const val MAXIMUM_WALLPAPER_DECODE_DIMENSION_PIXELS = 2_048
        const val MAXIMUM_WALLPAPER_DIMENSION_PIXELS = 8_192
        const val NO_REJECTED_THEMES = 0
        const val MAXIMUM_AUTOMATION_NODE_TEXT_CHARACTERS = 4_096
        const val MAXIMUM_AUTOMATION_SCREENSHOT_BYTES = 4 * 1024 * 1024
        const val MAXIMUM_AUTOMATION_SCREENSHOT_EDGE = 1_024
        const val MAXIMUM_PROVIDER_AUTOMATION_DEPTH = 12
        const val MAXIMUM_PROVIDER_AUTOMATION_NODE_COUNT = 128
        const val MAXIMUM_PROVIDER_AUTOMATION_RENDER_TOKEN = Long.MAX_VALUE
        const val MAXIMUM_WIDGET_ID_SUFFIX = 1_000_000
        const val NO_LAYOUT_SIZE = 0
        const val NO_PADDING = 0
        const val NO_DRAWABLE = 0
        const val POPUP_PLACEMENT_ABOVE = "above"
        const val POPUP_PLACEMENT_BELOW = "below"
        const val NO_DIMENSION = 0
        const val NO_APP_WIDGET_INTENT_FLAGS = 0
        const val NO_ELEVATION = 0F
        const val NO_SCALE_CHANGE = 1F
        const val SCREEN_LOCATION_VALUES = 2
        const val SCREEN_LOCATION_X = 0
        const val SCREEN_LOCATION_Y = 1
        const val WIDGET_COMBINE_TARGET_SCALE = 1.12F
        const val NO_WIDGET_LOCK_CHANGES = 0
        const val HTML_WIDGET_PREVIEW_ORIGIN = "https://appassets.androidplatform.net"
        const val HTML_WIDGET_PREVIEW_DOCUMENT = "preview.html"
        const val HTML_WIDGET_PREVIEW_MIME = "text/html"
        const val HTML_WIDGET_PREVIEW_CHARSET = "UTF-8"
        const val HTML_DOM_PATCH_TIMEOUT_MILLISECONDS = 2_000L
        const val NO_TRANSLATION_X = 0F
        const val NO_TRANSLATION_Y = 0F
        const val EMPTY_PAGE_INSERTION_INDEX = 0
        const val FIRST_CELL_ADDRESS = 1
        const val HTML_BLOCK_INSTANCE_PREFIX = "block-"
        const val HTML_BLOCK_PREVIEW_MAXIMUM_DP = 240
        const val HTML_BLOCK_SEPARATOR = "\n"
        const val HTML_WIDGET_EDITOR_BORDER_WIDTH_DP = 1
        const val HTML_WIDGET_EDITOR_CORNER_RADIUS_DP = 8
        const val HTML_WIDGET_EDITOR_HORIZONTAL_PADDING_DP = 12
        const val HTML_WIDGET_EDITOR_VERTICAL_PADDING_DP = 8
        const val FIRST_CELL_INDEX = 0
        const val NEXT_CELL_EDGE = 1
        const val EMPTY_PAGE_VIEWPORT_SIZE = 0
        const val EMPTY_PAGE_MINIMUM_HEIGHT_DP = 240
        const val EMPTY_PROVIDER_AUTOMATION_PATH = ""
        const val INITIAL_PROVIDER_AUTOMATION_RENDER_TOKEN = 0L
        const val PROVIDER_AUTOMATION_RENDER_TOKEN_INCREMENT = 1L
        const val EMPTY_QUERY = ""
        const val DRAWER_SOURCE_CONTROL = "launcher_control"
        const val DRAWER_SOURCE_GESTURE = "home_gesture"
        const val STARTUP_DISMISS_SOURCE_TOUCH = "touch"
        const val MAXIMUM_APP_CATALOGUE_ENTRIES = 512
        const val MAXIMUM_APP_LABEL_CHARACTERS = 128
        const val KEY_APPS = "apps"
        const val KEY_COMPONENT = "component"
        const val KEY_DESTRUCTIVE = "destructive"
        const val KEY_ENTRY_COUNT = "entryCount"
        const val KEY_ACTION_ID = "id"
        const val KEY_LABEL = "label"
        const val KEY_PACKAGE_NAME = "packageName"
        const val KEY_ROOT_REQUIRED = "rootRequired"
        const val KEY_TRUNCATED = "truncated"
        const val FIELD_SOURCE = "source"
        const val EVENT_APP_CATALOGUE_PROJECTED = "app_catalogue_projected"
        const val EVENT_AUTOMATION_SETUP_OPENED = "automation_setup_opened"
        const val KEY_OPENED = "opened"
        const val EVENT_APP_DRAWER_CLOSED = "app_drawer_closed"
        const val EVENT_APP_DRAWER_FILTERED = "app_drawer_filtered"
        const val EVENT_APP_DRAWER_OPENED = "app_drawer_opened"
        const val EVENT_APP_ACTION_ROOT_REQUIRED = "app_action_root_required"
        const val EVENT_LAUNCHER_CONTROL_ACTION = "launcher_control_action"
        const val EVENT_LAUNCHER_CONTROL_OPENED = "launcher_control_opened"
        const val EVENT_PAGE_SEARCH_OPENED = "page_search_opened"
        const val EVENT_STARTUP_BRAND_DISMISSED = "startup_brand_dismissed"
        const val EVENT_WIDGET_MOVE_ARMED = "widget_move_armed"
        const val MESSAGE_UNSUPPORTED_APP_ACTION = "app action is unsupported"
        const val SEMANTIC_ACTION_MOVE = "move"
        const val SEMANTIC_APP_ACTION_PREFIX = "app-action:"
        const val SEMANTIC_APP_ACTION_CONFIRM = "app-action:confirm"
        const val SEMANTIC_HTML_BLOCK_CONFIRM = "html-block:confirm"
        const val SEMANTIC_APP_ACTION_ROOT_APP_INFO = "app-action:root-app-info"
        const val SEMANTIC_DRAWER = "drawer"
        const val SEMANTIC_DRAWER_APP_PREFIX = "drawer:app:"
        const val SEMANTIC_DRAWER_APP_ACTIONS_PREFIX = "drawer:app-actions:"
        const val SEMANTIC_DRAWER_CLOSE = "drawer:close"
        const val SEMANTIC_DRAWER_EMPTY = "drawer:empty"
        const val SEMANTIC_DRAWER_SEARCH = "drawer:search"
        const val SEMANTIC_APP_GROUP_MEMBER_SEPARATOR = ":member:"
        const val SEMANTIC_LAUNCHER_CONTROL = "launcher:controls"
        const val SEMANTIC_LAUNCHER_CONTROL_PREFIX = "launcher:controls:"
        const val SEMANTIC_PAGE_SEARCH = "launcher:page-search"
        const val SEMANTIC_PAGE_SEARCH_PREFIX = "launcher:page-search:"
        const val SEMANTIC_PAGE_SEARCH_QUERY = "launcher:page-search:query"
        const val SEMANTIC_PAGE_SEARCH_EMPTY = "launcher:page-search:empty"
        const val SEMANTIC_PAGE_SEARCH_PAGE_PREFIX = "launcher:page-search:page:"
        const val SEMANTIC_STARTUP_DISMISS = "startup:dismiss"
        const val APP_SHORTCUT_BUTTON_ICON_GAP_DP = 8
        const val APP_SHORTCUT_ICON_LABEL_GAP_DP = 4
        const val APP_GROUP_ICON_SIZE_DP = 24
        const val APP_GROUP_MEMBER_COLUMNS = 3
        const val APP_GROUP_MEMBER_SIZE_DP = 84
        const val APP_GROUP_MEMBER_GAP_DP = 6
        const val APP_GROUP_MEMBER_LABEL_LINES = 1
        const val SINGLE_APP_GROUP_MEMBER = 1
        const val APP_GROUP_PREVIEW_COLUMNS = 2
        const val COMMAND_SHEET_APP_ICON_GAP_DP = 8
        const val COMMAND_SHEET_APP_ICON_SIZE_DP = 32
        const val PAGE_ID_PREFIX = "page"
        const val STARTUP_BRAND_ENTER_DURATION_MILLISECONDS = 360L
        const val STARTUP_BRAND_EXIT_DURATION_MILLISECONDS = 280L
        const val STARTUP_BRAND_VISIBLE_DURATION_MILLISECONDS = 10_000L
        const val STARTUP_BRAND_INITIAL_SCALE = 0.82F
        const val TRANSPARENT_OPACITY = 0F
        const val REASON_ACTIVITY_UNAVAILABLE = "activity_unavailable"
        const val REASON_COMPONENT_TOO_LONG = "component_too_long"
        const val REASON_EMPTY_APP_LABEL = "empty_app_label"
        const val REASON_HTML_BLOCK_TARGET_NOT_RENDERED = "target_not_rendered"
        const val REASON_LAST_PAGE = "last_page"
        const val REASON_SCRIPT_ID_CONFLICT = "script_id_conflict"
        const val REASON_SCRIPT_ID_CAPACITY = "script_id_capacity"
        const val REASON_MAXIMUM_PAGE_COUNT = "maximum_page_count"
        const val REASON_PAGE_LOCKED = "page_locked"
        const val REASON_PAGE_NOT_EMPTY = "page_not_empty"
        const val REASON_GRID_VALUE_OUT_OF_RANGE = "grid_value_out_of_range"
        const val REASON_PAGE_NOT_FOUND = "page_not_found"
        const val REASON_DRAG_START_FAILED = "drag_start_failed"
        const val REASON_PROVIDER_CONFIGURATION_UNAVAILABLE = "provider_configuration_unavailable"
        const val REASON_MOVE_TARGET_UNAVAILABLE = "move_target_unavailable"
        const val REASON_WIDGET_NOT_TOP_LEVEL = "widget_not_top_level"
        const val REASON_WIDGET_ID_CAPACITY = "widget_id_capacity"
        const val REASON_WALLPAPER_UNAVAILABLE = "wallpaper_unavailable"
        const val OUTCOME_HTML_RENDERER_CRASH_REQUESTED = "crash_requested"
        const val RESOLVE_ACTIVITY_FLAGS = 0
        const val SCROLL_X_ORIGIN = 0
        const val SCROLL_Y_ORIGIN = 0
        const val SETTINGS_TITLE_WEIGHT = 1F
        const val WIDGET_EDIT_ACTION_WEIGHT = 1F
        const val SCALE_SCREENSHOT_FILTER = true
        const val PAGE_CONTENT_EDGE_INSET_DP = 1
        const val PANEL_ROUTE_GAP_DP = 8
        const val PANEL_ROUTE_HEIGHT_DP = 56
        const val PANEL_ROUTE_HORIZONTAL_PADDING_DP = 16
        const val PANEL_ROUTE_ICON_GAP_DP = 16
        const val PANEL_ROUTE_TEXT_SIZE_SP = 16F
        const val PANEL_SUBTITLE_GAP_DP = 12
        const val PAGE_CACHE_MAXIMUM_COUNT = 5
        const val PAGE_CACHE_NEIGHBOR_OFFSET = 1
        const val STANDARD_PADDING_DP = 12
        const val SCRIPT_EDITOR_MINIMUM_LINES = 8
        const val SCRIPT_STATE_EDITOR_MINIMUM_LINES = 4
        const val SCRIPT_STATE_JSON_INDENTATION_SPACES = 2
        const val HTML_WIDGET_SOURCE_MINIMUM_LINES = 6
        const val HTML_WIDGET_STATE_MINIMUM_LINES = 3
        const val SCRIPT_DASHBOARD_TEXT_SIZE_SP = 16F
        const val WIDGET_CORNER_RADIUS_DP = 12
        const val WIDGET_BORDER_WIDTH_DP = 1
        const val MINIMUM_WIDGET_ID_SLUG_LENGTH = 1
        const val WIDGET_ID_FALLBACK_SLUG = "widget"
        const val WIDGET_ID_SEPARATOR = "-"
        const val WIDGET_ID_SEPARATOR_CHARACTER = '-'
        const val WIDGET_MOVE_ELEVATION_DP = 12
        const val WIDGET_TOP_EDGE_MOVE_GESTURE_HEIGHT_DP = 24
        const val CONTROL_PLANE_START_ATTEMPTS = 3
        const val NO_CONTROL_PLANE_START_ATTEMPTS = 0
        const val ONE_CONTROL_PLANE_START_ATTEMPT = 1
        const val CONTROL_PLANE_START_RETRY_DELAY_MILLISECONDS = 500L
        const val WIDGET_PICKER_CHILD_INDENT_DP = 12
        const val WIDGET_PICKER_ICON_SIZE_DP = 40
        const val WIDGET_PICKER_PREVIEW_HEIGHT_DP = 72
        const val WIDGET_PICKER_PREVIEW_WIDTH_DP = 112
        const val WIDGET_RESIZE_OUTLINE_WIDTH_DP = 2
        const val WIDGET_RESIZE_ELEVATION_DP = 12
        const val WIDGET_RESIZE_FILL_CONTROL_MARGIN_DP = 8
        const val WIDGET_RESIZE_FILL_CONTROL_SIZE_DP = 48
        const val WIDGET_RESIZE_HEIGHT_FILL_TAG = "dikciz_resize_height_fill"
        const val WIDGET_RESIZE_POINT_RADIUS_DP = 6
        const val WIDGET_RESIZE_TOUCH_RADIUS_DP = 24
        const val WIDGET_RESIZE_HEIGHT_FILL_CONTROL_MARGIN_DP =
            WIDGET_RESIZE_FILL_CONTROL_MARGIN_DP + WIDGET_RESIZE_TOUCH_RADIUS_DP
        const val WIDGET_RESIZE_WIDTH_FILL_TAG = "dikciz_resize_width_fill"
        const val WIDGET_TITLE_TEXT_SIZE_SP = 20F
        const val WALLPAPER_SAMPLE_SIZE_MULTIPLIER = 2
        const val COMMAND_MANAGE_PAGE_KEYWORD = "manage"
        const val COMMAND_SELECT_PAGE_KEYWORD = "page"
        const val COMMAND_SETTINGS_KEYWORD = "settings"
        const val LOG_TAG = "DikcizHome"
        const val LEGACY_STORAGE_PERMISSION_REQUEST_CODE = 1
        const val KEY_BOUNDS = "bounds"
        const val KEY_BOTTOM = "bottom"
        const val KEY_CHECKED = "checked"
        const val KEY_CLASS_NAME = "className"
        const val KEY_CLICKABLE = "clickable"
        const val KEY_CONFIG_PATH = "configPath"
        const val KEY_HTML_WIDGET_ACTIVE_RENDERER_COUNT = "activeRendererCount"
        const val KEY_HTML_WIDGET_DOCUMENT_BYTES = "documentBytes"
        const val KEY_HTML_WIDGET_MAX_DOCUMENT_BYTES = "maxDocumentBytes"
        const val KEY_HTML_WIDGET_MAX_RENDERERS_PER_PAGE = "maxRenderersPerPage"
        const val KEY_HTML_WIDGET_RESOURCES = "htmlWidgetResources"
        const val KEY_AUTOMATION_SERVICE_SYNCED = "automationServiceSynced"
        const val KEY_ACTION_TYPE = "type"
        const val KEY_CONFIG = "config"
        const val KEY_CONTENT_DESCRIPTION = "contentDescription"
        const val KEY_CONTROL_PORT = "controlPort"
        const val KEY_DISPATCHED = "dispatched"
        const val KEY_ENABLED = "enabled"
        const val KEY_EXTENT = "extent"
        const val KEY_EXECUTION_SCOPE = "executionScope"
        const val KEY_EXIT_CODE = "exitCode"
        const val KEY_FOUND = "found"
        const val KEY_HEIGHT = "height"
        const val KEY_ID = "id"
        const val KEY_LAUNCHED = "launched"
        const val KEY_LEFT = "left"
        const val KEY_LOG_DIRECTORY = "logDirectory"
        const val KEY_MCP_CONTROL_PORT = "mcpControlPort"
        const val KEY_NODE = "node"
        const val KEY_NODES = "nodes"
        const val KEY_OUTPUT = "output"
        const val KEY_OUTCOME = "outcome"
        const val KEY_OUTPUT_TRUNCATED = "outputTruncated"
        const val KEY_PAGES = "pages"
        const val KEY_PAGE_ID = "pageId"
        const val KEY_PAGE_LOCATION = "pageLocation"
        const val KEY_PNG_BASE64 = "pngBase64"
        const val KEY_RANGE = "range"
        const val KEY_RESOURCE_ID = "resourceId"
        const val KEY_ROLE = "role"
        const val KEY_RIGHT = "right"
        const val KEY_SCREEN = "screen"
        const val KEY_SCROLL = "scroll"
        const val KEY_SCRIPT_LOG_DIAGNOSTIC = "diagnostic"
        const val KEY_SCRIPT_LOG_ACTION_TYPE = "actionType"
        const val KEY_SCRIPT_LOG_EVENT = "event"
        const val KEY_SCRIPT_LOG_EVENT_TYPE = "eventType"
        const val KEY_SCRIPT_LOG_LEVEL = "level"
        const val KEY_SCRIPT_LOG_OUTCOME = "outcome"
        const val KEY_SCRIPT_LOG_POLICY_ID = "policyId"
        const val KEY_SCRIPT_LOG_REASON = "reason"
        const val KEY_SCRIPT_LOG_RECORDS = "records"
        const val KEY_SCRIPT_LOG_SCRIPT_ID = "scriptId"
        const val KEY_SCRIPT_LOG_SCRIPT_KIND = "scriptKind"
        const val KEY_SCRIPT_LOG_TIMESTAMP = "timestamp"
        const val KEY_SCRIPT_LOGS_TRUNCATED = "truncated"
        const val KEY_SELECTED = "selected"
        const val KEY_SELECTED_PAGE_ID = "selectedPageId"
        const val KEY_SEMANTIC_ID = "semanticId"
        const val KEY_TEXT = "text"
        const val KEY_THEME_DIRECTORY = "themeDirectory"
        const val KEY_TITLE = "title"
        const val KEY_TOP = "top"
        const val KEY_TYPE = "type"
        const val KEY_VISIBLE = "visible"
        const val KEY_WALLPAPER_DIRECTORY = "wallpaperDirectory"
        const val KEY_WIDTH = "width"
        const val KEY_WIDGET_REFERENCES = "widgetReferences"
        const val KEY_Y = "y"
        const val MESSAGE_APP_UNAVAILABLE = "app widget target is unavailable"
        const val MESSAGE_APP_WIDGET_REQUIRED = "semantic ID must name an app widget"
        const val AUTOMATION_WIDGET_TYPE_APP_GROUP = "appGroup"
        const val AUTOMATION_WIDGET_TYPE_HTML = "html"
        const val AUTOMATION_WIDGET_TYPE_SCRIPT_DASHBOARD = "scriptDashboard"
        const val FIELD_WIDGET_TYPE = "widget_type"
        const val MESSAGE_CONFIGURATION_SAVE_FAILED = "configuration could not be saved"
        const val MESSAGE_GRID_CELL_REQUIRED = "cell is required for this command"
        const val MESSAGE_UNSUPPORTED_WIDGET_TYPE =
            "widgetType must be html, appGroup, or scriptDashboard"
        const val MESSAGE_WIDGET_ID_UNAVAILABLE = "a widget id could not be generated"
        const val MESSAGE_WIDGET_INSERTION_FAILED = "the widget could not be inserted"
        const val MESSAGE_CONFIGURATION_UNAVAILABLE = "launcher configuration is unavailable"
        const val MESSAGE_INVALID_AUTOMATION_ACTIONS = "automation actions are invalid"
        const val MESSAGE_INVALID_APP_COMPONENT = "app widget component is invalid"
        const val MESSAGE_INTENT_REJECTED = "Android rejected the requested intent"
        const val MESSAGE_INTENT_UNAVAILABLE = "Android activity intent is unavailable"
        const val MESSAGE_MANUAL_TRIGGER_UNAVAILABLE = "script is not enabled for manual triggering"
        const val MESSAGE_PAGE_NOT_FOUND = "page ID was not found"
        const val MESSAGE_SCREEN_UNAVAILABLE = "launcher screen is unavailable"
        const val MESSAGE_SCREENSHOT_TOO_LARGE = "screenshot exceeds its size limit"
        const val MESSAGE_SCREENSHOT_ENCODING_FAILED = "screenshot could not be encoded"
        const val MESSAGE_SELECTED_PAGE_UNAVAILABLE = "selected page is unavailable"
        const val MESSAGE_SEMANTIC_ID_NOT_FOUND = "semantic ID was not found on the selected page"
        const val MESSAGE_SAFE_MODE_READ_ONLY = "safe mode only allows recovery actions"
        const val MESSAGE_HTML_WIDGET_REQUIRED = "semantic ID must name an HTML widget"
        const val MESSAGE_SHELL_FAILED = "launcher shell command could not start"
        const val MESSAGE_UNSUPPORTED_INTENT_TYPE = "intentType is not supported"
        const val MESSAGE_UNSUPPORTED_AUTOMATION_COMMAND = "automation command is unsupported"
        const val MESSAGE_VIEW_NOT_ACTIONABLE = "semantic ID does not name an actionable view"
        const val MESSAGE_WIDGET_DISABLED = "widget is disabled"
        const val MESSAGE_WIDGET_NOT_FOUND = "the requested widget does not exist"
        const val AUTOMATION_ROLE_APP_WIDGET = "appWidget"
        const val AUTOMATION_ROLE_BUTTON = "button"
        const val AUTOMATION_ROLE_IMAGE = "image"
        const val AUTOMATION_ROLE_IMAGE_BUTTON = "imageButton"
        const val AUTOMATION_ROLE_PAGE_INDICATOR = "pageIndicator"
        const val AUTOMATION_ROLE_RADIO_BUTTON = "radioButton"
        const val AUTOMATION_ROLE_SCROLL_CONTAINER = "scrollContainer"
        const val AUTOMATION_ROLE_STATIC_TEXT = "staticText"
        const val AUTOMATION_ROLE_SWITCH = "switch"
        const val AUTOMATION_ROLE_TEXT_INPUT = "textInput"
        const val AUTOMATION_ROLE_VIEW = "view"
        const val AUTOMATION_ROLE_WIDGET = "widget"
        const val PNG_QUALITY = 100
        const val SEMANTIC_ACTION_APP = "app"
        const val SEMANTIC_ACTION_APP_GROUP = "app-group"
        const val SEMANTIC_ACTION_APPEARANCE = "appearance"
        const val SEMANTIC_ACTION_DELETE = "delete"
        const val SEMANTIC_ACTION_EDIT = "edit"
        const val SEMANTIC_ACTION_ADD_BLOCK = "add-block"
        const val SEMANTIC_ACTION_FIT_CONTENT = "fit-content"
        const val SEMANTIC_ACTION_LOCK = "lock"
        const val SEMANTIC_ACTION_MOVE_HANDLE = "move-handle"
        const val SEMANTIC_ACTION_PROVIDER = "provider"
        const val SEMANTIC_ACTION_RESIZE = "resize"
        const val SEMANTIC_ACTION_RESIZE_FILL_HEIGHT = "resize-fill-height"
        const val SEMANTIC_ACTION_RESIZE_FILL_WIDTH = "resize-fill-width"
        const val SEMANTIC_ACTION_SCRIPT = "script"
        const val SEMANTIC_COMMAND_ADD_SHORTCUT = "add-shortcut"
        const val SEMANTIC_COMMAND_APP = "app"
        const val SEMANTIC_COMMAND_ENTRY_PREFIX = "command:entry:"
        const val SEMANTIC_COMMAND_LAUNCH = "launch"
        const val SEMANTIC_COMMAND_MANAGE_PAGE = "manage-page"
        const val SEMANTIC_COMMAND_PREFIX = "command:"
        const val SEMANTIC_DIALOG_CLOSE = "dialog:close"
        const val SEMANTIC_PAGE_MENU_PREFIX = "page:menu:"
        const val SEMANTIC_PAGE_RENAME_PREFIX = "page:rename:"
        const val SEMANTIC_PAGE_RENAME_SAVE = "save"
        const val SEMANTIC_PAGE_RENAME_TITLE = "title"
        const val SEMANTIC_WIDGET_LOCKS_PREFIX = "page:widget-locks:"
        const val SEMANTIC_WIDGET_LOCKS_SAVE = "save"
        const val SEMANTIC_WIDGET_LOCKS_WIDGET = "widget"
        const val SEMANTIC_COMMAND_SEARCH = "search"
        const val SEMANTIC_COMMAND_SELECT_PAGE = "select-page"
        const val SEMANTIC_COMMAND_SETTINGS = "settings"
        const val SEMANTIC_ACTION_EDITOR = "editor"
        const val SEMANTIC_PICKER_PREFIX = "picker:"
        const val SEMANTIC_PICKER_WIDGET_PREFIX = "picker:widget:"
        const val SEMANTIC_PICKER_RETARGET_PREFIX = "picker:retarget:"
        const val SEMANTIC_PICKER_PAGE_FULL = "page-full"
        const val SEMANTIC_PICKER_SEARCH = "search"
        const val SEMANTIC_PICKER_CATEGORY = "category"
        const val SEMANTIC_PICKER_APPLICATION = "application"
        const val SEMANTIC_PICKER_ENTRY = "entry"
        const val SEMANTIC_PICKER_HTML_PACKAGE = "html-package"
        const val SEMANTIC_PICKER_HTML_PACKAGE_ENTRY_SEGMENT = ":html-package:entry:"
        const val SEMANTIC_PICKER_HTML_PACKAGE_LOAD_SEGMENT = ":html-package:load:"
        const val SEMANTIC_PICKER_HTML_PACKAGE_SEGMENT = ":html-package:"
        const val SEMANTIC_PICKER_LOAD = "load"
        const val SEMANTIC_APPEARANCE_PREFIX = "appearance:"
        const val SEMANTIC_APPEARANCE_RESET = "reset"
        const val SEMANTIC_APPEARANCE_SAVE = "save"
        const val SEMANTIC_EDITOR_CSS = "css"
        const val SEMANTIC_EDITOR_DISPLAY_STYLE = "display-style"
        const val SEMANTIC_EDITOR_HTML = "html"
        const val SEMANTIC_EDITOR_AUTO_FIT_HEIGHT = "auto-fit-height"
        const val SEMANTIC_EDITOR_JAVASCRIPT = "javascript"
        const val SEMANTIC_EDITOR_OPEN = "open"
        const val SEMANTIC_EDITOR_PACKAGE_ID = "package-id"
        const val SEMANTIC_EDITOR_RECONFIGURE = "reconfigure"
        const val SEMANTIC_EDITOR_RETARGET = "retarget"
        const val SEMANTIC_EDITOR_SAVE = "save"
        const val SEMANTIC_EDITOR_SAVE_TO_LIBRARY = "save-to-library"
        const val SEMANTIC_EDITOR_STATE = "state"
        const val SEMANTIC_EDITOR_TITLE = "title"
        const val SEMANTIC_ID_HORIZONTAL_PAGE_INDICATOR = "page:navigation:horizontal"
        const val SEMANTIC_ID_PAGE_CANVAS = "page:canvas"
        const val SEMANTIC_ID_PAGE_SCROLL = "pageScroll"
        const val SEMANTIC_ID_VERTICAL_PAGE_INDICATOR = "page:navigation:vertical"
        const val SEMANTIC_PAGE_INDICATOR_EDGE_PREFIX = ":edge:"
        const val SEMANTIC_PROVIDER_RENDER_TOKEN_PREFIX = "r"
        const val SEMANTIC_WIDGET_ACTION_SEPARATOR = ":"
        const val SEMANTIC_PROVIDER_PATH_SEPARATOR = "."
        const val SEMANTIC_WIDGET_PREFIX = "widget:"
        const val SCRIPT_KIND_HTML = "html"
        const val SEMANTIC_SAFE_MODE_EXIT = "safeMode:exit"

        private val COMBINING_MARKS_PATTERN = Regex("\\p{M}+")
        private val NON_WIDGET_ID_CHARACTER_PATTERN = Regex("[^a-z0-9]+")
        const val SEMANTIC_SAFE_MODE_RESET = "safeMode:reset"
        const val SEMANTIC_SETTINGS_PAGE_PREFIX = "settings:page:"
        const val SEMANTIC_SETTINGS_APPEARANCE_PREFIX = "settings:appearance:"
        const val SEMANTIC_SETTINGS_PAGE_TITLE = "title"
        const val SEMANTIC_SETTINGS_GRID_PREFIX = "settings:grid:"
        const val SEMANTIC_SETTINGS_PREFIX = "settings:"
        const val SEMANTIC_SETTINGS_RESET = "settings:reset"
        const val SEMANTIC_SETTINGS_RESET_CONFIRM = "settings:reset-confirm"
        const val SEMANTIC_SETTINGS_SAFE_MODE = "settings:safe-mode"
        const val SEMANTIC_SETTINGS_SAVE_PAGE_GRID = "settings:save-page-grid"
        const val SEMANTIC_SETTINGS_SAVE_PAGES = "settings:save-pages"
        const val SEMANTIC_STORAGE_ACCESS_GRANT = "storage:grant"
        const val SEMANTIC_SETTINGS_AUTOMATION = "settings:automation"
        const val SEMANTIC_SETTINGS_AUTOMATION_FULL_ACCESS = "settings:automation:full-access"
        const val SEMANTIC_SETTINGS_AUTOMATION_REMOTE_AUTH = "settings:automation:remote-auth"
        const val SEMANTIC_SETTINGS_AUTOMATION_ACCESS_PREFIX = "$SEMANTIC_SETTINGS_AUTOMATION:"
        const val SEMANTIC_SETTINGS_AUTOMATION_CLOSE =
            "${SEMANTIC_SETTINGS_AUTOMATION_ACCESS_PREFIX}close"
        const val SEMANTIC_SETTINGS_AUTOMATION_GRANT_BLUETOOTH =
            "${SEMANTIC_SETTINGS_AUTOMATION_ACCESS_PREFIX}grant-bluetooth"
        const val SEMANTIC_SETTINGS_AUTOMATION_GRANT_CALENDAR =
            "${SEMANTIC_SETTINGS_AUTOMATION_ACCESS_PREFIX}grant-calendar"
        const val SEMANTIC_SETTINGS_AUTOMATION_GRANT_CONTACTS =
            "${SEMANTIC_SETTINGS_AUTOMATION_ACCESS_PREFIX}grant-contacts"
        const val SEMANTIC_SETTINGS_AUTOMATION_GRANT_HEALTH_BACKGROUND =
            "${SEMANTIC_SETTINGS_AUTOMATION_ACCESS_PREFIX}grant-health-background"
        const val SEMANTIC_SETTINGS_AUTOMATION_GRANT_HEALTH_STEPS =
            "${SEMANTIC_SETTINGS_AUTOMATION_ACCESS_PREFIX}grant-health-steps"
        const val SEMANTIC_SETTINGS_AUTOMATION_GRANT_HEART_RATE_BACKGROUND =
            "${SEMANTIC_SETTINGS_AUTOMATION_ACCESS_PREFIX}grant-heart-rate-background"
        const val SEMANTIC_SETTINGS_AUTOMATION_GRANT_HEART_RATE_SENSOR =
            "${SEMANTIC_SETTINGS_AUTOMATION_ACCESS_PREFIX}grant-heart-rate-sensor"
        const val SEMANTIC_SETTINGS_AUTOMATION_GRANT_LOCATION =
            "${SEMANTIC_SETTINGS_AUTOMATION_ACCESS_PREFIX}grant-location"
        const val SEMANTIC_SETTINGS_AUTOMATION_GRANT_NOTIFICATIONS =
            "${SEMANTIC_SETTINGS_AUTOMATION_ACCESS_PREFIX}grant-notifications"
        const val SEMANTIC_SETTINGS_AUTOMATION_GRANT_PHONE_STATE =
            "${SEMANTIC_SETTINGS_AUTOMATION_ACCESS_PREFIX}grant-phone-state"
        const val SEMANTIC_SETTINGS_AUTOMATION_GRANT_SMS =
            "${SEMANTIC_SETTINGS_AUTOMATION_ACCESS_PREFIX}grant-sms"
        const val SEMANTIC_SETTINGS_AUTOMATION_GRANT_STEP_SENSOR =
            "${SEMANTIC_SETTINGS_AUTOMATION_ACCESS_PREFIX}grant-step-sensor"
        const val SEMANTIC_SETTINGS_AUTOMATION_OPEN_APP_SETTINGS =
            "${SEMANTIC_SETTINGS_AUTOMATION_ACCESS_PREFIX}open-app-settings"
        const val SEMANTIC_SETTINGS_AUTOMATION_OPEN_DEVICE_ADMINISTRATION =
            "${SEMANTIC_SETTINGS_AUTOMATION_ACCESS_PREFIX}open-device-administration"
        const val SEMANTIC_SETTINGS_AUTOMATION_OPEN_NOTIFICATION_LISTENER =
            "${SEMANTIC_SETTINGS_AUTOMATION_ACCESS_PREFIX}open-notification-listener"
        const val SEMANTIC_SETTINGS_AUTOMATION_OPEN_NOTIFICATION_POLICY =
            "${SEMANTIC_SETTINGS_AUTOMATION_ACCESS_PREFIX}open-notification-policy"
        const val SEMANTIC_REMOTE_AUTH_PREFIX = "remote-auth:"
        const val SEMANTIC_REMOTE_AUTH_CLEAR_PASSWORD = "${SEMANTIC_REMOTE_AUTH_PREFIX}clear-password"
        const val SEMANTIC_REMOTE_AUTH_CLOSE = "${SEMANTIC_REMOTE_AUTH_PREFIX}close"
        const val SEMANTIC_REMOTE_AUTH_ENABLED = "${SEMANTIC_REMOTE_AUTH_PREFIX}enabled"
        const val SEMANTIC_REMOTE_AUTH_PASSWORD = "${SEMANTIC_REMOTE_AUTH_PREFIX}password"
        const val SEMANTIC_REMOTE_AUTH_PASSWORD_CONFIRMATION =
            "${SEMANTIC_REMOTE_AUTH_PREFIX}password-confirmation"
        const val SEMANTIC_REMOTE_AUTH_SET_PASSWORD = "${SEMANTIC_REMOTE_AUTH_PREFIX}set-password"
        const val REMOTE_AUTH_PASSWORD_CLEAR_BYTE: Byte = 0
        const val REMOTE_AUTH_PASSWORD_CLEAR_CHARACTER = '\u0000'
        const val SEMANTIC_SETTINGS_DEVICE_ACCESS_PREFIX = "settings:device-access:"
        const val SEMANTIC_SETTINGS_DEVICE_ACCESS_CLOSE =
            "${SEMANTIC_SETTINGS_DEVICE_ACCESS_PREFIX}close"
        const val SEMANTIC_SETTINGS_DEVICE_ACCESS_NEXT =
            "${SEMANTIC_SETTINGS_DEVICE_ACCESS_PREFIX}next"
        const val SEMANTIC_SETTINGS_SCRIPT_LOGS = "settings:script-logs"
        const val SEMANTIC_SETTINGS_SCRIPT_LOGS_CLOSE = "settings:script-logs:close"
        const val SEMANTIC_SCRIPT_ACTION_EDIT = "edit"
        const val SEMANTIC_SCRIPT_ACTION_LOGS = "logs"
        const val SEMANTIC_SCRIPT_ACTION_TOGGLE_ENABLED = "toggle-enabled"
        const val SEMANTIC_SETTINGS_SCRIPT_LOGS_REFRESH = "settings:script-logs:refresh"
        const val SEMANTIC_SETTINGS_SCRIPTS = "settings:scripts"
        const val SEMANTIC_SETTINGS_THEMES = "settings:themes"
        val AUTOMATION_PERMISSION_REQUEST_CODES = setOf(
            AUTOMATION_BLUETOOTH_PERMISSION_REQUEST_CODE,
            AUTOMATION_CALENDAR_PERMISSION_REQUEST_CODE,
            AUTOMATION_CONTACTS_PERMISSION_REQUEST_CODE,
            AUTOMATION_LOCATION_PERMISSION_REQUEST_CODE,
            AUTOMATION_NOTIFICATION_PERMISSION_REQUEST_CODE,
            AUTOMATION_PHONE_STATE_PERMISSION_REQUEST_CODE,
            AUTOMATION_SMS_PERMISSION_REQUEST_CODE,
            AUTOMATION_SENSOR_PERMISSION_REQUEST_CODE,
            AUTOMATION_HEALTH_CONNECT_PERMISSION_REQUEST_CODE,
        )
        const val SEMANTIC_THEME_PREFIX = "theme:"
        const val SEMANTIC_LUA_SCRIPT_PREFIX = "lua-script:"
        const val SEMANTIC_LUA_SCRIPT_ARCHIVE_PREFIX = "lua-script:import-file:"
        const val SEMANTIC_LUA_SCRIPT_CLOSE = "lua-script:close"
        const val SEMANTIC_LUA_SCRIPT_CREATE = "lua-script:create"
        const val SEMANTIC_LUA_SCRIPT_DELETE = "lua-script:delete"
        const val SEMANTIC_LUA_SCRIPT_DELETE_CONFIRM = "lua-script:delete-confirm"
        const val SEMANTIC_LUA_SCRIPT_ENABLED = "lua-script:enabled"
        const val SEMANTIC_LUA_SCRIPT_EXPORT = "lua-script:export"
        const val SEMANTIC_LUA_SCRIPT_IMPORT = "lua-script:import"
        const val SEMANTIC_LUA_SCRIPT_IMPORT_CONFIRM = "lua-script:import-confirm"
        const val SEMANTIC_LUA_SCRIPT_IMPORT_KEEP = "lua-script:import-keep"
        const val SEMANTIC_LUA_SCRIPT_IMPORT_REPLACE = "lua-script:import-replace"
        const val SEMANTIC_LUA_SCRIPT_SAVE = "lua-script:save"
        const val SEMANTIC_LUA_SCRIPT_SOURCE = "lua-script:source"
        const val SEMANTIC_LUA_SCRIPT_STATE = "lua-script:state"
        const val SEMANTIC_LUA_SCRIPT_TITLE = "lua-script:title"
        const val VALUE_HOME_SCREEN = "home"
        const val VALUE_CONFIGURATION_ERROR_SCREEN = "configuration_error"
        const val VALUE_STORAGE_ACCESS_SCREEN = "storage_access"
        const val DEFAULT_LUA_SCRIPT_SOURCE = "function on_event(event)\n  return { status = \"Received \" .. event.type }\nend"
        const val DEFAULT_LUA_SCRIPT_STATE = "{}"
        const val SCRIPT_LOG_FIELD_ACTION_TYPE = "action_type"
        const val SCRIPT_LOG_FIELD_DIAGNOSTIC = "diagnostic"
        const val SCRIPT_LOG_FIELD_EVENT_TYPE = "event_type"
        const val SCRIPT_LOG_FIELD_OUTCOME = "outcome"
        const val SCRIPT_LOG_FIELD_POLICY_ID = "policy_id"
        const val SCRIPT_LOG_FIELD_REASON = "reason"
        const val SCRIPT_LOG_FIELD_SCRIPT_ID = "script_id"
        const val SCRIPT_LOG_FIELD_SCRIPT_KIND = "script_kind"
        const val SCRIPT_LOG_RECORD_LINE_SEPARATOR = "\n"
        val SAFE_MODE_ACTION_SEMANTIC_IDS = setOf(
            SEMANTIC_SAFE_MODE_EXIT,
            SEMANTIC_SAFE_MODE_RESET,
        )
        val AUTOMATION_BACKGROUND_COMMAND_TYPES = setOf(
            DikcizAutomationControlPlane.TYPE_APP_CATALOGUE,
            DikcizAutomationControlPlane.TYPE_AUTOMATION_DISPATCH,
            DikcizAutomationControlPlane.TYPE_SHELL,
            DikcizAutomationControlPlane.TYPE_SCRIPT_LOGS,
        )
        val STORAGE_ACCESS_COMMAND_TYPES = setOf(
            DikcizAutomationControlPlane.TYPE_ACCESSIBILITY_ACTION,
            DikcizAutomationControlPlane.TYPE_ACCESSIBILITY_SNAPSHOT,
            DikcizAutomationControlPlane.TYPE_AUTOMATION_STATUS,
            DikcizAutomationControlPlane.TYPE_CONTROL_STATUS,
            DikcizAutomationControlPlane.TYPE_FIND,
            DikcizAutomationControlPlane.TYPE_SNAPSHOT,
            DikcizAutomationControlPlane.TYPE_TAP,
            DikcizAutomationControlPlane.TYPE_UI_DUMP,
        )
        val SAFE_MODE_READ_ONLY_COMMAND_TYPES = setOf(
            DikcizAutomationControlPlane.TYPE_ACCESSIBILITY_SNAPSHOT,
            DikcizAutomationControlPlane.TYPE_APP_CATALOGUE,
            DikcizAutomationControlPlane.TYPE_CONFIG_GET,
            DikcizAutomationControlPlane.TYPE_AUTOMATION_STATUS,
            DikcizAutomationControlPlane.TYPE_CONTROL_STATUS,
            DikcizAutomationControlPlane.TYPE_DIAGNOSTICS,
            DikcizAutomationControlPlane.TYPE_HOME_GET,
            DikcizAutomationControlPlane.TYPE_SCREENSHOT,
            DikcizAutomationControlPlane.TYPE_SNAPSHOT,
            DikcizAutomationControlPlane.TYPE_SCRIPT_LOGS,
            DikcizAutomationControlPlane.TYPE_UI_DUMP,
            DikcizAutomationControlPlane.TYPE_WIDGET_GET,
            DikcizAutomationControlPlane.TYPE_RESET,
        )
    }
}
