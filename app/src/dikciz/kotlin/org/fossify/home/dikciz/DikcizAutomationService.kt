package org.fossify.home.dikciz

import android.Manifest
import android.annotation.TargetApi
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.ContentObserver
import android.database.Cursor
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Base64
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.fossify.home.BuildConfig
import org.fossify.home.R

/**
 * Identity of the launcher's own foreground-service notification.
 *
 * The notification listener has to recognise it, so it lives outside the
 * service's private constants.
 */
internal object DikcizAutomationForegroundNotification {
    const val CHANNEL_ID = "dikciz-automation-service"
    const val ID = 7302
}

internal class DikcizAutomationService : Service(), SensorEventListener, LocationListener {
    private lateinit var homeConfigStore: HomeConfigStore
    private lateinit var logger: DikcizLogger
    private var broadcastReceiverRegistered = false
    private var packageLifecycleReceiverRegistered = false
    private var calendarEventObserverRegistered = false
    private var contactsObserverRegistered = false
    private var calendarEventReconciliationLimitReached = false
    private var connectivityRegistered = false
    private var configuration: HomeConfiguration? = null
    private var scheduledAlarmInterval: Long? = null
    private var scheduledAlarmSchedule: AlarmSchedule? = null
    private var nextAlarmElapsedRealtime = 0L
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var debugPhoneState: Int? = null
    private var legacyPhoneStateListenerRegistered = false
    private var lastPhoneState: String? = null
    private var phoneStateCallback: PhoneStateCallback? = null
    private var powerManager: PowerManager? = null
    private var audioManager: AudioManager? = null
    private var sensorManager: SensorManager? = null
    private var thermalStatusListener: PowerManager.OnThermalStatusChangedListener? = null
    private var locationManager: LocationManager? = null
    private var telephonyManager: TelephonyManager? = null
    private lateinit var healthConnectDailyStepsSource: DikcizHealthConnectDailyStepsSource
    private val registeredSensorTypes = mutableSetOf<Int>()
    private var registeredLocationProvider: String? = null
    private val calendarEventSignatures = mutableMapOf<Long, String>()
    private val configurationHandler = Handler(Looper.getMainLooper())
    private val configurationObservers = mutableListOf<ConfigurationFileObserver>()
    private val configurationReload = Runnable { reconfigure() }
    private val foregroundAlarm = Runnable { deliverAlarm(SOURCE_FOREGROUND_TIMER) }

    private val calendarEventObserver = object : ContentObserver(configurationHandler) {
        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            logger.debug(
                EVENT_CALENDAR_CHANGE_RECEIVED,
                mapOf(FIELD_REASON to calendarChangeReason(uri)),
            )
            if (calendarEventID(uri) != null) {
                dispatchCalendarEvent(uri)
                return
            }
            reconcileCalendarEvents()
        }
    }

    private val contactsObserver = object : ContentObserver(configurationHandler) {
        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            dispatchContactsChanged()
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            systemEvents(intent).forEach { event -> DikcizAutomationEventBus.enqueue(context, event) }
        }
    }

    private val packageLifecycleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            packageLifecycleEvent(intent)?.let { event ->
                DikcizAutomationEventBus.enqueue(context, event)
            }
        }
    }

    @Suppress("DEPRECATION")
    private val legacyPhoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            dispatchPhoneState(state)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val connectivityManager = getSystemService(ConnectivityManager::class.java)
            val capabilities = connectivityManager?.getNetworkCapabilities(network)
            if (capabilities == null) {
                dispatchConnectivity(
                    state = STATE_CONNECTED,
                    validated = false,
                    metered = false,
                    transports = emptyList(),
                )
                return
            }
            dispatchConnectivity(capabilities)
        }

        override fun onLost(network: Network) {
            dispatchDisconnectedConnectivity()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            dispatchConnectivity(capabilities)
        }
    }


    override fun onCreate() {
        super.onCreate()
        homeConfigStore = HomeConfigStore(applicationContext)
        logger = DikcizLogger(LOG_TAG, homeConfigStore.logDirectory)
        bluetoothAdapter = getSystemService(BluetoothManager::class.java)?.adapter
        sensorManager = getSystemService(SensorManager::class.java)
        locationManager = getSystemService(LocationManager::class.java)
        telephonyManager = getSystemService(TelephonyManager::class.java)
        powerManager = getSystemService(PowerManager::class.java)
        audioManager = getSystemService(AudioManager::class.java)
        healthConnectDailyStepsSource = DikcizHealthConnectDailyStepsSource(applicationContext, logger)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startForegroundForConfiguration(configuration)) {
            // Android refused the foreground start, which it does for a service
            // launched from the background. The launch used
            // startForegroundService, so the only way out is to stop now.
            // Staying alive without a foreground notification makes the
            // platform kill the whole launcher process at the deadline.
            stopSelf()
            return START_NOT_STICKY
        }
        val recoveringAlarm = intent?.action == ACTION_ALARM && scheduledAlarmInterval == null
        if (
            BuildConfig.DEBUG &&
            intent?.action == DikcizAutomationDebugContract.ACTION_SERVICE_PHONE_STATE
        ) {
            val callState = intent.getIntExtra(
                DikcizAutomationDebugContract.EXTRA_CALL_STATE,
                INVALID_PHONE_STATE,
            )
            debugPhoneState = callState
            dispatchPhoneState(callState)
            return START_STICKY
        }
        reconfigure()
        if (intent?.action == ACTION_ALARM) {
            if (recoveringAlarm) {
                nextAlarmElapsedRealtime = SystemClock.elapsedRealtime()
            }
            deliverAlarm(SOURCE_WAKEUP_ALARM, recoveringAlarm)
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        dispatchNightMode(isNightMode(newConfig))
        dispatchDeviceConfiguration(newConfig)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // Process death skips this callback, retaining its wakeup recovery alarm.
        cancelAlarm()
        unregisterConfigurationObservers()
        unregisterSources()
        unregisterLocation()
        healthConnectDailyStepsSource.close()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val values = JSONArray()
        event.values.take(MAXIMUM_SENSOR_VALUES).forEach { value -> values.put(value.toDouble()) }
        DikcizAutomationEventBus.enqueue(
            applicationContext,
            automationEvent(
                type = DikcizAutomationEventType.Sensor,
                source = "$SOURCE_SENSOR_PREFIX${event.sensor.type}",
                coalescingKey = "$COALESCING_SENSOR_PREFIX${event.sensor.type}",
                payload = JSONObject()
                    .put(PAYLOAD_ACCURACY, event.accuracy)
                    .put(PAYLOAD_SENSOR_NAME, event.sensor.name.take(MAXIMUM_SENSOR_NAME_CHARACTERS))
                    .put(PAYLOAD_SENSOR_TYPE, event.sensor.type)
                    .put(PAYLOAD_VALUES, values),
            ),
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onLocationChanged(location: Location) {
        val locationPrecisions = configuration
            ?.let { activeConfiguration ->
                registeredSubscriptions(activeConfiguration, DikcizAutomationEventType.Location)
                    .mapNotNull(DikcizAutomationSubscription::locationPrecision)
                    .toSet()
            }
            .orEmpty()
        locationPrecisions.forEach { locationPrecision ->
            logger.debug(
                EVENT_LOCATION_RECEIVED,
                mapOf(
                    FIELD_LOCATION_PRECISION to locationPrecision.persistedValue,
                    FIELD_PROVIDER to location.provider.orEmpty(),
                ),
            )
            DikcizAutomationEventBus.enqueue(
                applicationContext,
                automationEvent(
                    type = DikcizAutomationEventType.Location,
                    source = "$SOURCE_LOCATION_PREFIX${location.provider}:${locationPrecision.persistedValue}",
                    coalescingKey = "$COALESCING_LOCATION_PREFIX${location.provider}:${locationPrecision.persistedValue}",
                    payload = locationPayload(location, locationPrecision),
                    locationPrecision = locationPrecision,
                ),
            )
        }
    }

    private fun locationPayload(
        location: Location,
        locationPrecision: DikcizLocationPrecision,
    ): JSONObject {
        val payload = JSONObject()
            .put(
                DikcizAutomationEventPayload.LATITUDE,
                location.latitude.roundedToLocationPrecision(locationPrecision),
            )
            .put(
                DikcizAutomationEventPayload.LONGITUDE,
                location.longitude.roundedToLocationPrecision(locationPrecision),
            )
            .put(DikcizAutomationEventPayload.PROVIDER, location.provider)
        if (locationPrecision == DikcizLocationPrecision.Precise) {
            payload
                .put(DikcizAutomationEventPayload.ACCURACY, location.accuracy.toDouble())
                .put(DikcizAutomationEventPayload.ALTITUDE, location.altitude)
                .put(DikcizAutomationEventPayload.BEARING, location.bearing.toDouble())
                .put(DikcizAutomationEventPayload.SPEED, location.speed.toDouble())
        }
        return payload
    }

    private fun Double.roundedToLocationPrecision(
        locationPrecision: DikcizLocationPrecision,
    ): Double {
        if (locationPrecision == DikcizLocationPrecision.Precise) {
            return this
        }
        return (this * APPROXIMATE_LOCATION_COORDINATE_SCALE).roundToInt() /
            APPROXIMATE_LOCATION_COORDINATE_SCALE
    }

    private fun reconfigure() {
        val loadedConfiguration = try {
            homeConfigStore.loadOrCreate()
        } catch (exception: HomeConfigException) {
            logger.warn(
                EVENT_CONFIGURATION_REJECTED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            if (configuration == null) {
                stopSelf()
            }
            return
        }
        configuration = loadedConfiguration
        logger.configure(loadedConfiguration.logging)
        DikcizAutomationEventBus.acceptConfiguration(applicationContext, loadedConfiguration)
        if (!loadedConfiguration.requiresBackgroundAutomationService()) {
            cancelAlarm()
            unregisterSources()
            unregisterLocation()
            unregisterConfigurationObservers()
            stopSelf()
            return
        }
        unregisterSources()
        replaceConfigurationObservers(loadedConfiguration)
        if (!startForegroundForConfiguration(loadedConfiguration)) {
            unregisterSources()
            unregisterLocation()
            stopSelf()
            return
        }
        registerSystemReceiver(loadedConfiguration)
        registerPackageLifecycleReceiver(loadedConfiguration)
        emitConfiguredNightMode(loadedConfiguration)
        emitConfiguredDeviceConfiguration(loadedConfiguration)
        registerCalendarEvents(loadedConfiguration)
        registerContactsChanges(loadedConfiguration)
        registerPhoneState(loadedConfiguration)
        registerConnectivity(loadedConfiguration)
        registerThermalStatus(loadedConfiguration)
        registerSensors(loadedConfiguration)
        registerLocation(loadedConfiguration)
        healthConnectDailyStepsSource.configure(loadedConfiguration)
        scheduleAlarm(loadedConfiguration)
    }

    private fun startForegroundForConfiguration(configuration: HomeConfiguration?): Boolean {
        val usesLocation = configuration?.let(::hasLocationSubscription) == true &&
            hasForegroundLocationPermission()
        // Every start command reaches startForeground. Android gives five
        // seconds from startForegroundService to that call and kills the whole
        // process otherwise, and only the platform knows whether an earlier
        // start already satisfied the one in flight, so the service never
        // decides to skip it.
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return false
        notificationManager.createNotificationChannel(
            NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                FOREGROUND_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val notification = android.app.Notification.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_launcher_name))
            .setContentText(FOREGROUND_NOTIFICATION_TEXT)
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = if (usesLocation) {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                startForeground(FOREGROUND_NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            }
            return true
        } catch (exception: SecurityException) {
            return reportForegroundStartRejected(exception, notificationManager)
        } catch (exception: IllegalStateException) {
            // Android refuses a foreground start from the background, and
            // reports it as ForegroundServiceStartNotAllowedException, which is
            // an IllegalStateException. Letting it escape kills the launcher
            // process.
            return reportForegroundStartRejected(exception, notificationManager)
        }
    }

    /**
     * Reports a refused foreground start and answers whether the service can
     * carry on.
     *
     * A refusal is only fatal while this service is not already in the
     * foreground. When it is, the refused start had nothing to add and the
     * service keeps working. That question is answered from the platform's own
     * record of the posted foreground notification, because a field tracking
     * the same thing falls out of step whenever Android ends the foreground
     * state on its own, and then a start command skips startForeground and the
     * launcher process is killed at the deadline.
     */
    private fun reportForegroundStartRejected(
        exception: Exception,
        notificationManager: NotificationManager,
    ): Boolean {
        logger.warn(
            EVENT_FOREGROUND_START_REJECTED,
            mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
        )
        return notificationManager.activeNotifications.any { notification ->
            notification.id == FOREGROUND_NOTIFICATION_ID
        }
    }

    private fun registerSystemReceiver(configuration: HomeConfiguration) {
        val events = configuredEventTypes(configuration)
        if (broadcastReceiverRegistered) {
            unregisterReceiver(systemReceiver)
            broadcastReceiverRegistered = false
        }
        val filter = IntentFilter().apply {
            if (DikcizAutomationEventType.Battery in events ||
                DikcizAutomationEventType.Charging in events
            ) {
                addAction(Intent.ACTION_BATTERY_CHANGED)
            }
            if (DikcizAutomationEventType.Time in events) {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            if (DikcizAutomationEventType.PowerSaveMode in events) {
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            if (DikcizAutomationEventType.DeviceIdleMode in events) {
                addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            }
            if (DikcizAutomationEventType.RingerMode in events) {
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            }
            if (DikcizAutomationEventType.InterruptionFilter in events) {
                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                addAction(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED)
            }
            if (DikcizAutomationEventType.BluetoothState in events) {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            if (DikcizAutomationEventType.Screen in events) {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            if (DikcizAutomationEventType.UserPresent in events) addAction(Intent.ACTION_USER_PRESENT)
        }
        if (filter.countActions() == NO_ACTIONS) {
            return
        }
        registerReceiver(systemReceiver, filter)
        broadcastReceiverRegistered = true
        emitConfiguredBluetoothState(configuration)
        emitConfiguredPowerSaveMode(configuration)
        emitConfiguredDeviceIdleMode(configuration)
        emitConfiguredRingerMode(configuration)
        emitConfiguredInterruptionFilter(configuration)
    }

    private fun registerPackageLifecycleReceiver(configuration: HomeConfiguration) {
        if (packageLifecycleReceiverRegistered) {
            unregisterReceiver(packageLifecycleReceiver)
            packageLifecycleReceiverRegistered = false
        }
        if (DikcizAutomationEventType.PackageChanged !in configuredEventTypes(configuration)) {
            return
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme(PACKAGE_URI_SCHEME)
        }
        registerReceiver(packageLifecycleReceiver, filter)
        packageLifecycleReceiverRegistered = true
    }

    private fun registerConnectivity(configuration: HomeConfiguration) {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return
        val shouldRegister = DikcizAutomationEventType.Connectivity in configuredEventTypes(configuration)
        if (connectivityRegistered) {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            connectivityRegistered = false
        }
        if (!shouldRegister || checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        connectivityRegistered = true
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun registerThermalStatus(configuration: HomeConfiguration) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }
        unregisterThermalStatus()
        if (!hasThermalStatusSubscription(configuration)) {
            return
        }
        val manager = powerManager ?: return
        val listener = PowerManager.OnThermalStatusChangedListener(::dispatchThermalStatus)
        try {
            manager.addThermalStatusListener(listener)
            thermalStatusListener = listener
            dispatchThermalStatus(manager.currentThermalStatus)
            logger.info(EVENT_THERMAL_STATUS_REGISTERED, emptyMap())
        } catch (exception: IllegalStateException) {
            logger.warn(
                EVENT_THERMAL_STATUS_REGISTRATION_REJECTED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
        } catch (exception: SecurityException) {
            logger.warn(
                EVENT_THERMAL_STATUS_REGISTRATION_REJECTED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
        }
    }

    private fun registerCalendarEvents(configuration: HomeConfiguration) {
        if (calendarEventObserverRegistered) {
            contentResolver.unregisterContentObserver(calendarEventObserver)
            calendarEventObserverRegistered = false
        }
        calendarEventSignatures.clear()
        calendarEventReconciliationLimitReached = false
        if (!hasCalendarEventSubscription(configuration)) {
            return
        }
        calendarEventObservations()?.let(::replaceCalendarEventSignatures)
        try {
            contentResolver.registerContentObserver(
                CalendarContract.CONTENT_URI,
                true,
                calendarEventObserver,
            )
            calendarEventObserverRegistered = true
            logger.info(EVENT_CALENDAR_REGISTERED, emptyMap())
            reconcileCalendarEvents()
        } catch (exception: SecurityException) {
            logger.warn(
                EVENT_CALENDAR_REGISTRATION_REJECTED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
        }
    }

    private fun registerContactsChanges(configuration: HomeConfiguration) {
        if (contactsObserverRegistered) {
            contentResolver.unregisterContentObserver(contactsObserver)
            contactsObserverRegistered = false
        }
        if (!hasContactsChangedSubscription(configuration)) {
            return
        }
        try {
            contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                contactsObserver,
            )
            contactsObserverRegistered = true
            logger.info(EVENT_CONTACTS_REGISTERED, emptyMap())
        } catch (exception: SecurityException) {
            logger.warn(
                EVENT_CONTACTS_REGISTRATION_REJECTED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun registerPhoneState(configuration: HomeConfiguration) {
        if (!hasPhoneStateSubscription(configuration)) {
            return
        }
        val manager = telephonyManager ?: run {
            logger.warn(
                EVENT_PHONE_STATE_REGISTRATION_SKIPPED,
                mapOf(FIELD_REASON to REASON_TELEPHONY_UNAVAILABLE),
            )
            return
        }
        try {
            lastPhoneState = phoneStateName(debugPhoneState ?: manager.callState)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PhoneStateCallback().also { callback ->
                    manager.registerTelephonyCallback(mainExecutor, callback)
                    phoneStateCallback = callback
                }
            } else {
                manager.listen(legacyPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
                legacyPhoneStateListenerRegistered = true
            }
            logger.info(EVENT_PHONE_STATE_REGISTERED, emptyMap())
        } catch (exception: SecurityException) {
            logger.warn(
                EVENT_PHONE_STATE_REGISTRATION_REJECTED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
        } catch (exception: IllegalStateException) {
            logger.warn(
                EVENT_PHONE_STATE_REGISTRATION_REJECTED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
        }
    }

    private fun registerSensors(configuration: HomeConfiguration) {
        val manager = sensorManager ?: return
        manager.unregisterListener(this)
        registeredSensorTypes.clear()
        val subscriptions = registeredSubscriptions(configuration, DikcizAutomationEventType.Sensor)
        subscriptions.forEach { subscription ->
            val samplingPeriod = subscription.samplingPeriodMicroseconds ?: return@forEach
            subscription.sensorTypes.forEach { sensorType ->
                if (!DikcizAutomationSensorPermission.hasPermission(this, sensorType)) {
                    logger.warn(
                        EVENT_SENSOR_REGISTRATION_REJECTED,
                        mapOf(
                            FIELD_REASON to REASON_PERMISSION_MISSING,
                            FIELD_SENSOR_TYPE to sensorType,
                        ),
                    )
                    return@forEach
                }
                if (!registeredSensorTypes.add(sensorType)) {
                    return@forEach
                }
                manager.getDefaultSensor(sensorType)?.let { sensor ->
                    try {
                        manager.registerListener(this, sensor, samplingPeriod)
                    } catch (exception: SecurityException) {
                        logger.warn(
                            EVENT_SENSOR_REGISTRATION_REJECTED,
                            mapOf(
                                FIELD_ERROR_CLASS to exception::class.java.simpleName,
                                FIELD_SENSOR_TYPE to sensorType,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun registerLocation(configuration: HomeConfiguration) {
        val manager = locationManager ?: return
        val hasSubscription = hasLocationSubscription(configuration)
        if (!hasSubscription) {
            if (registeredLocationProvider != null) {
                manager.removeUpdates(this)
                registeredLocationProvider = null
            }
            logger.debug(
                EVENT_LOCATION_REGISTRATION_SKIPPED,
                mapOf(
                    FIELD_REASON to if (hasAuthorizedLocationSubscription(configuration)) {
                        REASON_PERMISSION_MISSING
                    } else {
                        REASON_SUBSCRIPTION_MISSING
                    },
                ),
            )
            return
        }
        val provider = preferredLocationProvider(manager)
        // A satellite fix takes time to arrive. Re-requesting an unchanged
        // registration restarts that wait, so a configuration reload while a
        // faster source keeps reloading the tree would cancel every fix
        // before it lands.
        if (registeredLocationProvider == provider) {
            return
        }
        if (!manager.isProviderEnabled(provider)) {
            logger.warn(
                EVENT_LOCATION_REGISTRATION_SKIPPED,
                mapOf(
                    FIELD_PROVIDER to provider,
                    FIELD_REASON to REASON_PROVIDER_DISABLED,
                ),
            )
            return
        }
        try {
            manager.removeUpdates(this)
            manager.requestLocationUpdates(provider, LOCATION_MINIMUM_INTERVAL_MILLISECONDS, LOCATION_MINIMUM_DISTANCE_METERS, this)
            registeredLocationProvider = provider
            logger.info(
                EVENT_LOCATION_REGISTERED,
                mapOf(FIELD_PROVIDER to provider),
            )
        } catch (exception: SecurityException) {
            logger.warn(
                EVENT_LOCATION_REGISTRATION_REJECTED,
                mapOf(
                    FIELD_ERROR_CLASS to exception::class.java.simpleName,
                    FIELD_PROVIDER to provider,
                ),
            )
        }
    }

    private fun preferredLocationProvider(manager: LocationManager): String {
        val hasFineLocation =
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFineLocation && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER
        }
        if (manager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
            return LocationManager.FUSED_PROVIDER
        }
        return LocationManager.NETWORK_PROVIDER
    }

    private fun scheduleAlarm(configuration: HomeConfiguration) {
        val alarmSchedule = alarmSchedule(configuration)
        if (alarmSchedule == scheduledAlarmSchedule) {
            return
        }
        cancelAlarm()
        val interval = alarmSchedule.interval ?: return
        scheduledAlarmInterval = interval
        scheduledAlarmSchedule = alarmSchedule
        nextAlarmElapsedRealtime = SystemClock.elapsedRealtime() + interval
        armAlarm()
    }

    private fun alarmSchedule(configuration: HomeConfiguration): AlarmSchedule {
        val scriptSubscriptions = configuration.automation.scripts.asSequence()
            .flatMap { automationScript ->
                val policy = configuration.automation.policy(automationScript.policyID)
                val script = configuration.scripts.firstOrNull { candidate ->
                    candidate.id == automationScript.scriptID
                }
                if (
                    !automationScript.enabled ||
                    policy?.enabled != true ||
                    script?.enabled != true
                ) {
                    return@flatMap emptySequence()
                }
                automationScript.subscriptions.asSequence()
                    .filter { subscription ->
                        subscription.event == DikcizAutomationEventType.Alarm &&
                            policy.allows(subscription) &&
                            hasAndroidSubscriptionPermission(subscription)
                    }
                    .map { subscription ->
                        AlarmScheduleSubscription(automationScript.scriptID, subscription)
                    }
            }
            .toSet()
        val htmlSubscriptions = htmlEventSubscriptions(configuration, DikcizAutomationEventType.Alarm)
            .filter(::hasAndroidSubscriptionPermission)
            .toSet()
        val interval = (scriptSubscriptions.asSequence().map { item -> item.subscription } +
            htmlSubscriptions.asSequence())
            .mapNotNull(DikcizAutomationSubscription::alarmIntervalMilliseconds)
            .minOrNull()
        return AlarmSchedule(interval, scriptSubscriptions, htmlSubscriptions)
    }

    private fun alarmPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        this,
        ALARM_REQUEST_CODE,
        Intent(this, DikcizAutomationAlarmReceiver::class.java).setAction(ACTION_ALARM),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cancelAlarm() {
        configurationHandler.removeCallbacks(foregroundAlarm)
        getSystemService(AlarmManager::class.java)?.cancel(alarmPendingIntent())
        scheduledAlarmInterval = null
        scheduledAlarmSchedule = null
        logger.debug(EVENT_ALARM_CANCELLED)
    }

    private fun armAlarm() {
        configurationHandler.removeCallbacks(foregroundAlarm)
        configurationHandler.postDelayed(
            foregroundAlarm,
            (nextAlarmElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L),
        )
        getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            nextAlarmElapsedRealtime,
            alarmPendingIntent(),
        )
    }

    private fun deliverAlarm(
        source: String,
        recoveredFromProcessLoss: Boolean = false,
    ) {
        val interval = scheduledAlarmInterval ?: return
        val now = SystemClock.elapsedRealtime()
        if (now < nextAlarmElapsedRealtime) {
            armAlarm()
            return
        }
        val lateness = now - nextAlarmElapsedRealtime
        // One shared deadline prevents duplicate callbacks and catch-up bursts.
        nextAlarmElapsedRealtime = now + interval
        armAlarm()
        logger.info(
            EVENT_ALARM_DELIVERED,
            mapOf(
                FIELD_SOURCE to source,
                FIELD_LATENESS_MILLISECONDS to lateness,
                FIELD_RECOVERY to recoveredFromProcessLoss,
            ),
        )
        DikcizAutomationEventBus.enqueue(
            applicationContext,
            automationEvent(
                type = DikcizAutomationEventType.Alarm,
                source = SOURCE_ALARM,
                coalescingKey = SOURCE_ALARM,
                payload = JSONObject()
                    .put(PAYLOAD_ALARM_ELAPSED_REALTIME, now)
                    .put(PAYLOAD_ALARM_RECOVERY, recoveredFromProcessLoss),
            ),
        )
    }

    private fun unregisterSources() {
        healthConnectDailyStepsSource.stop()
        if (broadcastReceiverRegistered) {
            unregisterReceiver(systemReceiver)
            broadcastReceiverRegistered = false
        }
        if (packageLifecycleReceiverRegistered) {
            unregisterReceiver(packageLifecycleReceiver)
            packageLifecycleReceiverRegistered = false
        }
        if (connectivityRegistered) {
            getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(networkCallback)
            connectivityRegistered = false
        }
        unregisterThermalStatus()
        if (calendarEventObserverRegistered) {
            contentResolver.unregisterContentObserver(calendarEventObserver)
            calendarEventObserverRegistered = false
        }
        if (contactsObserverRegistered) {
            contentResolver.unregisterContentObserver(contactsObserver)
            contactsObserverRegistered = false
        }
        unregisterPhoneState()
        calendarEventSignatures.clear()
        calendarEventReconciliationLimitReached = false
        sensorManager?.unregisterListener(this)
        registeredSensorTypes.clear()
    }

    /**
     * Stops location updates.
     *
     * This is deliberately not part of unregisterSources. Every configuration
     * reload calls that to rebuild the Android sources, and a satellite fix
     * takes long enough that tearing the listener down and requesting it again
     * on each reload means no fix ever arrives. registerLocation keeps,
     * replaces, or drops the registration instead, so only a service that is
     * going away stops it here.
     */
    private fun unregisterLocation() {
        locationManager?.removeUpdates(this)
        registeredLocationProvider = null
    }

    private fun replaceConfigurationObservers(configuration: HomeConfiguration) {
        unregisterConfigurationObservers()
        homeConfigStore.configurationWatchDirectories(configuration)
            .distinctBy { file -> file.absolutePath }
            .forEach { directory ->
                ConfigurationFileObserver(directory).also { observer ->
                    observer.startWatching()
                    configurationObservers += observer
                }
            }
    }

    private fun unregisterConfigurationObservers() {
        configurationHandler.removeCallbacks(configurationReload)
        configurationObservers.forEach(FileObserver::stopWatching)
        configurationObservers.clear()
    }

    private fun configuredEventTypes(configuration: HomeConfiguration): Set<DikcizAutomationEventType> {
        val scriptEvents = DikcizAutomationEventType.entries.filterTo(mutableSetOf()) { event ->
            activeSubscriptions(configuration, event).isNotEmpty()
        }
        return scriptEvents + htmlEventSubscriptions(configuration).map(DikcizAutomationSubscription::event)
    }

    private fun activeSubscriptions(
        configuration: HomeConfiguration,
        event: DikcizAutomationEventType,
    ): List<DikcizAutomationSubscription> {
        return configuration.automation.scripts.flatMap { script ->
            val policy = configuration.automation.policy(script.policyID)
            val luaScript = configuration.scripts.firstOrNull { candidate -> candidate.id == script.scriptID }
            if (!script.enabled || policy?.enabled != true || luaScript?.enabled != true) {
                return@flatMap emptyList()
            }
            script.subscriptions.filter { subscription ->
                subscription.event == event &&
                    policy.allows(subscription) &&
                    hasAndroidSubscriptionPermission(subscription)
            }
        }
    }

    private fun htmlEventSubscriptions(
        configuration: HomeConfiguration,
        event: DikcizAutomationEventType? = null,
    ): List<DikcizAutomationSubscription> {
        return configuration.pages.asSequence()
            .flatMap { page -> page.widgets.asSequence() }
            .filterIsInstance<HtmlHomeWidget>()
            .filter(HtmlHomeWidget::enabled)
            .flatMap { widget -> widget.eventSubscriptions.asSequence() }
            .filter { subscription -> event == null || subscription.event == event }
            .toList()
    }

    private fun registeredSubscriptions(
        configuration: HomeConfiguration,
        event: DikcizAutomationEventType,
    ): List<DikcizAutomationSubscription> {
        return (activeSubscriptions(configuration, event) + htmlEventSubscriptions(configuration, event))
            .filter(::hasAndroidSubscriptionPermission)
    }

    private fun DikcizAutomationPolicy.allows(subscription: DikcizAutomationSubscription): Boolean {
        if (subscription.event == DikcizAutomationEventType.Location) {
            return subscription.locationPrecision?.capability in capabilities
        }
        return subscription.event.capability in capabilities
    }

    private fun hasLocationSubscription(configuration: HomeConfiguration): Boolean {
        return registeredSubscriptions(configuration, DikcizAutomationEventType.Location).isNotEmpty()
    }

    private fun hasForegroundLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAuthorizedLocationSubscription(configuration: HomeConfiguration): Boolean {
        if (htmlEventSubscriptions(configuration, DikcizAutomationEventType.Location).isNotEmpty()) {
            return true
        }
        return configuration.automation.scripts.any { script ->
            val policy = configuration.automation.policy(script.policyID)
            script.enabled &&
                policy?.enabled == true &&
                script.subscriptions.any { subscription ->
                    subscription.event == DikcizAutomationEventType.Location && policy.allows(subscription)
                }
        }
    }

    private fun hasBluetoothSubscription(configuration: HomeConfiguration): Boolean {
        return registeredSubscriptions(configuration, DikcizAutomationEventType.BluetoothState).isNotEmpty()
    }

    private fun hasThermalStatusSubscription(configuration: HomeConfiguration): Boolean {
        return registeredSubscriptions(configuration, DikcizAutomationEventType.ThermalStatus).isNotEmpty()
    }

    private fun hasPowerSaveModeSubscription(configuration: HomeConfiguration): Boolean {
        return registeredSubscriptions(configuration, DikcizAutomationEventType.PowerSaveMode).isNotEmpty()
    }

    private fun hasDeviceIdleModeSubscription(configuration: HomeConfiguration): Boolean {
        return registeredSubscriptions(configuration, DikcizAutomationEventType.DeviceIdleMode).isNotEmpty()
    }

    private fun hasNightModeSubscription(configuration: HomeConfiguration): Boolean {
        return registeredSubscriptions(configuration, DikcizAutomationEventType.NightMode).isNotEmpty()
    }

    private fun hasDeviceConfigurationSubscription(configuration: HomeConfiguration): Boolean {
        return registeredSubscriptions(configuration, DikcizAutomationEventType.DeviceConfiguration).isNotEmpty()
    }

    private fun hasRingerModeSubscription(configuration: HomeConfiguration): Boolean {
        return registeredSubscriptions(configuration, DikcizAutomationEventType.RingerMode).isNotEmpty()
    }

    private fun hasInterruptionFilterSubscription(configuration: HomeConfiguration): Boolean {
        return registeredSubscriptions(configuration, DikcizAutomationEventType.InterruptionFilter).isNotEmpty()
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun unregisterThermalStatus() {
        val listener = thermalStatusListener
        thermalStatusListener = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || listener == null) {
            return
        }
        try {
            powerManager?.removeThermalStatusListener(listener)
        } catch (exception: IllegalArgumentException) {
            logger.warn(
                EVENT_THERMAL_STATUS_UNREGISTRATION_REJECTED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
        }
    }

    private fun hasCalendarEventSubscription(configuration: HomeConfiguration): Boolean {
        return registeredSubscriptions(configuration, DikcizAutomationEventType.CalendarEvent).isNotEmpty()
    }

    private fun hasContactsChangedSubscription(configuration: HomeConfiguration): Boolean {
        return registeredSubscriptions(configuration, DikcizAutomationEventType.ContactsChanged).isNotEmpty()
    }

    private fun hasPhoneStateSubscription(configuration: HomeConfiguration): Boolean {
        return registeredSubscriptions(configuration, DikcizAutomationEventType.PhoneState).isNotEmpty()
    }

    @Suppress("DEPRECATION")
    private fun unregisterPhoneState() {
        val callback = phoneStateCallback
        phoneStateCallback = null
        val wasLegacyListenerRegistered = legacyPhoneStateListenerRegistered
        legacyPhoneStateListenerRegistered = false
        lastPhoneState = null
        val manager = telephonyManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            callback?.let(manager::unregisterTelephonyCallback)
            return
        }
        if (wasLegacyListenerRegistered) {
            manager.listen(legacyPhoneStateListener, PhoneStateListener.LISTEN_NONE)
        }
    }

    private fun emitConfiguredBluetoothState(configuration: HomeConfiguration) {
        if (!hasBluetoothSubscription(configuration)) {
            return
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val adapter = bluetoothAdapter ?: return
        dispatchBluetoothState(adapter.isEnabled)
    }

    private fun emitConfiguredPowerSaveMode(configuration: HomeConfiguration) {
        if (!hasPowerSaveModeSubscription(configuration)) {
            return
        }
        val manager = powerManager ?: return
        dispatchPowerSaveMode(manager.isPowerSaveMode)
    }

    private fun emitConfiguredDeviceIdleMode(configuration: HomeConfiguration) {
        if (!hasDeviceIdleModeSubscription(configuration)) {
            return
        }
        val manager = powerManager ?: return
        dispatchDeviceIdleMode(manager.isDeviceIdleMode)
    }

    private fun emitConfiguredNightMode(configuration: HomeConfiguration) {
        if (!hasNightModeSubscription(configuration)) {
            return
        }
        dispatchNightMode(isNightMode(resources.configuration))
    }

    private fun emitConfiguredDeviceConfiguration(configuration: HomeConfiguration) {
        if (!hasDeviceConfigurationSubscription(configuration)) {
            return
        }
        dispatchDeviceConfiguration(resources.configuration)
    }

    private fun emitConfiguredRingerMode(configuration: HomeConfiguration) {
        if (!hasRingerModeSubscription(configuration)) {
            return
        }
        val manager = audioManager ?: return
        dispatchRingerMode(manager.ringerMode)
    }

    private fun emitConfiguredInterruptionFilter(configuration: HomeConfiguration) {
        if (!hasInterruptionFilterSubscription(configuration)) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java) ?: return
        dispatchInterruptionFilter(manager.currentInterruptionFilter)
    }

    private fun systemEvents(intent: Intent): List<DikcizAutomationEvent> {
        if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
            return buildList {
                if (hasConfiguredEvent(DikcizAutomationEventType.Battery)) {
                    add(batteryEvent(intent))
                }
                if (hasConfiguredEvent(DikcizAutomationEventType.Charging)) {
                    add(chargingEvent(intent))
                }
            }
        }
        return systemEvent(intent)?.let(::listOf).orEmpty()
    }

    private fun hasConfiguredEvent(event: DikcizAutomationEventType): Boolean {
        return configuration?.let(::configuredEventTypes)?.contains(event) == true
    }

    private fun batteryEvent(intent: Intent): DikcizAutomationEvent {
        return automationEvent(
            DikcizAutomationEventType.Battery,
            SOURCE_BATTERY,
            COALESCING_BATTERY,
            JSONObject()
                .put(PAYLOAD_LEVEL, intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, UNKNOWN_LEVEL))
                .put(PAYLOAD_STATUS, intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, UNKNOWN_STATUS)),
        )
    }

    private fun chargingEvent(intent: Intent): DikcizAutomationEvent {
        return automationEvent(
            DikcizAutomationEventType.Charging,
            SOURCE_POWER,
            COALESCING_CHARGING,
            JSONObject().put(
                PAYLOAD_CHARGING,
                intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, NO_POWER_SOURCE) != NO_POWER_SOURCE,
            ),
        )
    }

    private fun systemEvent(intent: Intent): DikcizAutomationEvent? {
        return when (intent.action) {
            Intent.ACTION_TIME_TICK,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> automationEvent(
                DikcizAutomationEventType.Time,
                SOURCE_TIME,
                COALESCING_TIME,
                JSONObject().put(PAYLOAD_ACTION, intent.action),
            )

            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_SCREEN_OFF,
            -> automationEvent(
                DikcizAutomationEventType.Screen,
                SOURCE_SCREEN,
                COALESCING_SCREEN,
                JSONObject().put(PAYLOAD_SCREEN_ON, intent.action == Intent.ACTION_SCREEN_ON),
            )

            Intent.ACTION_USER_PRESENT -> automationEvent(
                DikcizAutomationEventType.UserPresent,
                SOURCE_USER,
                COALESCING_USER,
                JSONObject(),
            )

            BluetoothAdapter.ACTION_STATE_CHANGED -> bluetoothStateEvent(intent)

            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> powerSaveModeEvent()

            PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> deviceIdleModeEvent()

            AudioManager.RINGER_MODE_CHANGED_ACTION -> ringerModeEvent(intent)

            NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED,
            NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED,
            -> interruptionFilterEvent()

            else -> null
        }
    }

    private fun packageLifecycleEvent(intent: Intent): DikcizAutomationEvent? {
        val packageName = intent.data
            ?.takeIf { uri -> uri.scheme == PACKAGE_URI_SCHEME }
            ?.schemeSpecificPart
        if (!isValidAndroidPackageName(packageName)) {
            logger.warn(
                EVENT_PACKAGE_LIFECYCLE_IGNORED,
                mapOf(FIELD_REASON to REASON_PACKAGE_NAME_INVALID),
            )
            return null
        }
        val change = when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    return null
                }
                PACKAGE_CHANGE_INSTALLED
            }

            Intent.ACTION_PACKAGE_CHANGED -> PACKAGE_CHANGE_CHANGED

            Intent.ACTION_PACKAGE_REMOVED -> {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    return null
                }
                PACKAGE_CHANGE_REMOVED
            }

            Intent.ACTION_PACKAGE_REPLACED -> PACKAGE_CHANGE_UPDATED

            else -> return null
        }
        return automationEvent(
            DikcizAutomationEventType.PackageChanged,
            "$SOURCE_PACKAGE_PREFIX$packageName",
            "$COALESCING_PACKAGE_PREFIX$packageName",
            JSONObject()
                .put(PAYLOAD_CHANGE, change)
                .put(PAYLOAD_PACKAGE_NAME, packageName),
        )
    }

    private fun isValidAndroidPackageName(value: String?): Boolean {
        return value != null &&
            value.length <= MAXIMUM_ANDROID_PACKAGE_NAME_CHARACTERS &&
            ANDROID_PACKAGE_NAME_PATTERN.matches(value)
    }

    private fun bluetoothStateEvent(intent: Intent): DikcizAutomationEvent? {
        val enabled = when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
            BluetoothAdapter.STATE_OFF -> false
            BluetoothAdapter.STATE_ON -> true
            else -> return null
        }
        return bluetoothStateEvent(enabled)
    }

    private fun dispatchBluetoothState(enabled: Boolean) {
        DikcizAutomationEventBus.enqueue(
            applicationContext,
            bluetoothStateEvent(enabled),
        )
    }

    private fun bluetoothStateEvent(enabled: Boolean): DikcizAutomationEvent {
        return automationEvent(
            DikcizAutomationEventType.BluetoothState,
            SOURCE_BLUETOOTH,
            COALESCING_BLUETOOTH_STATE,
            JSONObject().put(PAYLOAD_ENABLED, enabled),
        )
    }

    private fun dispatchPowerSaveMode(enabled: Boolean) {
        if (!hasPowerSaveModeSubscription(configuration ?: return)) {
            return
        }
        DikcizAutomationEventBus.enqueue(
            applicationContext,
            powerSaveModeEvent(enabled),
        )
    }

    private fun powerSaveModeEvent(): DikcizAutomationEvent? {
        val manager = powerManager ?: return null
        return powerSaveModeEvent(manager.isPowerSaveMode)
    }

    private fun powerSaveModeEvent(enabled: Boolean): DikcizAutomationEvent {
        return automationEvent(
            DikcizAutomationEventType.PowerSaveMode,
            SOURCE_POWER_SAVE,
            COALESCING_POWER_SAVE,
            JSONObject().put(PAYLOAD_ENABLED, enabled),
        )
    }

    private fun dispatchDeviceIdleMode(idle: Boolean) {
        if (!hasDeviceIdleModeSubscription(configuration ?: return)) {
            return
        }
        DikcizAutomationEventBus.enqueue(
            applicationContext,
            deviceIdleModeEvent(idle),
        )
    }

    private fun deviceIdleModeEvent(): DikcizAutomationEvent? {
        val loadedConfiguration = configuration ?: return null
        if (!hasDeviceIdleModeSubscription(loadedConfiguration)) {
            return null
        }
        val manager = powerManager ?: return null
        return deviceIdleModeEvent(manager.isDeviceIdleMode)
    }

    private fun deviceIdleModeEvent(idle: Boolean): DikcizAutomationEvent {
        return automationEvent(
            DikcizAutomationEventType.DeviceIdleMode,
            SOURCE_DEVICE_IDLE,
            COALESCING_DEVICE_IDLE,
            JSONObject().put(PAYLOAD_IDLE, idle),
        )
    }

    private fun dispatchNightMode(night: Boolean) {
        if (!hasNightModeSubscription(configuration ?: return)) {
            return
        }
        DikcizAutomationEventBus.enqueue(
            applicationContext,
            nightModeEvent(night),
        )
    }

    private fun nightModeEvent(night: Boolean): DikcizAutomationEvent {
        return automationEvent(
            DikcizAutomationEventType.NightMode,
            SOURCE_NIGHT_MODE,
            COALESCING_NIGHT_MODE,
            JSONObject().put(PAYLOAD_NIGHT, night),
        )
    }

    private fun isNightMode(configuration: Configuration): Boolean {
        val nightMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun dispatchDeviceConfiguration(configuration: Configuration) {
        if (!hasDeviceConfigurationSubscription(this.configuration ?: return)) {
            return
        }
        DikcizAutomationEventBus.enqueue(
            applicationContext,
            deviceConfigurationEvent(configuration),
        )
    }

    private fun deviceConfigurationEvent(configuration: Configuration): DikcizAutomationEvent {
        return automationEvent(
            DikcizAutomationEventType.DeviceConfiguration,
            SOURCE_DEVICE_CONFIGURATION,
            COALESCING_DEVICE_CONFIGURATION,
            JSONObject()
                .put(PAYLOAD_ORIENTATION, orientationName(configuration.orientation))
                .put(PAYLOAD_FONT_SCALE_PERCENT, fontScalePercent(configuration.fontScale)),
        )
    }

    private fun fontScalePercent(fontScale: Float): Int {
        val rawPercent = fontScale * FONT_SCALE_PERCENT_MULTIPLIER
        if (!rawPercent.isFinite()) {
            return DEFAULT_FONT_SCALE_PERCENT
        }
        return rawPercent.roundToInt().coerceIn(
            MINIMUM_FONT_SCALE_PERCENT,
            MAXIMUM_FONT_SCALE_PERCENT,
        )
    }

    private fun orientationName(orientation: Int): String {
        return when (orientation) {
            Configuration.ORIENTATION_PORTRAIT -> ORIENTATION_PORTRAIT
            Configuration.ORIENTATION_LANDSCAPE -> ORIENTATION_LANDSCAPE
            else -> ORIENTATION_UNDEFINED
        }
    }

    private fun dispatchRingerMode(mode: Int) {
        if (!hasRingerModeSubscription(configuration ?: return)) {
            return
        }
        val name = ringerModeName(mode) ?: return
        DikcizAutomationEventBus.enqueue(
            applicationContext,
            ringerModeEvent(name),
        )
    }

    private fun ringerModeEvent(intent: Intent): DikcizAutomationEvent? {
        val mode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, INVALID_RINGER_MODE)
        val name = ringerModeName(mode) ?: return null
        return ringerModeEvent(name)
    }

    private fun ringerModeEvent(name: String): DikcizAutomationEvent {
        return automationEvent(
            DikcizAutomationEventType.RingerMode,
            SOURCE_RINGER,
            COALESCING_RINGER_MODE,
            JSONObject().put(PAYLOAD_MODE, name),
        )
    }

    private fun ringerModeName(mode: Int): String? {
        return when (mode) {
            AudioManager.RINGER_MODE_NORMAL -> RINGER_MODE_NORMAL
            AudioManager.RINGER_MODE_SILENT -> RINGER_MODE_SILENT
            AudioManager.RINGER_MODE_VIBRATE -> RINGER_MODE_VIBRATE
            else -> null
        }
    }

    private fun dispatchInterruptionFilter(filter: Int) {
        if (!hasInterruptionFilterSubscription(configuration ?: return)) {
            return
        }
        val mode = interruptionFilterName(filter) ?: return
        DikcizAutomationEventBus.enqueue(
            applicationContext,
            interruptionFilterEvent(mode),
        )
    }

    private fun interruptionFilterEvent(): DikcizAutomationEvent? {
        val loadedConfiguration = configuration ?: return null
        if (!hasInterruptionFilterSubscription(loadedConfiguration)) {
            return null
        }
        val manager = getSystemService(NotificationManager::class.java) ?: return null
        val mode = interruptionFilterName(manager.currentInterruptionFilter) ?: return null
        return interruptionFilterEvent(mode)
    }

    private fun interruptionFilterEvent(mode: String): DikcizAutomationEvent {
        return automationEvent(
            DikcizAutomationEventType.InterruptionFilter,
            SOURCE_INTERRUPTION_FILTER,
            COALESCING_INTERRUPTION_FILTER,
            JSONObject().put(PAYLOAD_MODE, mode),
        )
    }

    private fun interruptionFilterName(filter: Int): String? {
        return when (filter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> INTERRUPTION_FILTER_ALL
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> INTERRUPTION_FILTER_PRIORITY
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> INTERRUPTION_FILTER_ALARMS
            NotificationManager.INTERRUPTION_FILTER_NONE -> INTERRUPTION_FILTER_NONE
            NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> INTERRUPTION_FILTER_UNKNOWN
            else -> null
        }
    }

    private fun dispatchPhoneState(callState: Int) {
        if (!hasPhoneStateSubscription(configuration ?: return)) {
            return
        }
        val state = phoneStateName(callState) ?: run {
            logger.warn(
                EVENT_PHONE_STATE_IGNORED,
                mapOf(FIELD_REASON to REASON_PHONE_STATE_UNSUPPORTED),
            )
            return
        }
        if (state == lastPhoneState) {
            return
        }
        lastPhoneState = state
        DikcizAutomationEventBus.enqueue(
            applicationContext,
            automationEvent(
                DikcizAutomationEventType.PhoneState,
                SOURCE_PHONE,
                COALESCING_PHONE_STATE,
                JSONObject().put(PAYLOAD_STATE, state),
            ),
        )
    }

    private fun phoneStateName(callState: Int): String? {
        return when (callState) {
            TelephonyManager.CALL_STATE_IDLE -> PHONE_STATE_IDLE
            TelephonyManager.CALL_STATE_RINGING -> PHONE_STATE_RINGING
            TelephonyManager.CALL_STATE_OFFHOOK -> PHONE_STATE_OFFHOOK
            else -> null
        }
    }

    private fun dispatchConnectivity(capabilities: NetworkCapabilities) {
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            dispatchDisconnectedConnectivity()
            return
        }
        dispatchConnectivity(
            state = STATE_CONNECTED,
            validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            transports = CONNECTIVITY_TRANSPORTS.mapNotNull { transport ->
                transport.name.takeIf { capabilities.hasTransport(transport.value) }
            },
        )
    }

    private fun dispatchDisconnectedConnectivity() {
        dispatchConnectivity(
            state = STATE_DISCONNECTED,
            validated = false,
            metered = false,
            transports = emptyList(),
        )
    }

    private fun dispatchConnectivity(
        state: String,
        validated: Boolean,
        metered: Boolean,
        transports: List<String>,
    ) {
        val payloadTransports = JSONArray()
        transports.forEach { transport -> payloadTransports.put(transport) }
        DikcizAutomationEventBus.enqueue(
            applicationContext,
            automationEvent(
                DikcizAutomationEventType.Connectivity,
                SOURCE_CONNECTIVITY,
                COALESCING_CONNECTIVITY,
                JSONObject()
                    .put(PAYLOAD_STATE, state)
                    .put(PAYLOAD_VALIDATED, validated)
                    .put(PAYLOAD_METERED, metered)
                    .put(PAYLOAD_TRANSPORTS, payloadTransports),
            ),
        )
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun dispatchThermalStatus(level: Int) {
        if (!hasThermalStatusSubscription(configuration ?: return)) {
            return
        }
        val status = thermalStatusName(level) ?: run {
            logger.warn(
                EVENT_THERMAL_STATUS_IGNORED,
                mapOf(FIELD_REASON to REASON_THERMAL_STATUS_UNSUPPORTED),
            )
            return
        }
        DikcizAutomationEventBus.enqueue(
            applicationContext,
            automationEvent(
                DikcizAutomationEventType.ThermalStatus,
                SOURCE_THERMAL,
                COALESCING_THERMAL_STATUS,
                JSONObject()
                    .put(PAYLOAD_LEVEL, level)
                    .put(PAYLOAD_STATUS, status),
            ),
        )
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun thermalStatusName(level: Int): String? {
        return when (level) {
            PowerManager.THERMAL_STATUS_NONE -> THERMAL_STATUS_NONE
            PowerManager.THERMAL_STATUS_LIGHT -> THERMAL_STATUS_LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> THERMAL_STATUS_MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> THERMAL_STATUS_SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL -> THERMAL_STATUS_CRITICAL
            PowerManager.THERMAL_STATUS_EMERGENCY -> THERMAL_STATUS_EMERGENCY
            PowerManager.THERMAL_STATUS_SHUTDOWN -> THERMAL_STATUS_SHUTDOWN
            else -> null
        }
    }

    private fun dispatchCalendarEvent(uri: android.net.Uri?) {
        if (!calendarEventObserverRegistered) {
            return
        }
        val eventID = calendarEventID(uri) ?: return
        if (!hasCalendarEventSubscription(configuration ?: return)) {
            return
        }
        val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventID)
        try {
            contentResolver.query(eventUri, CALENDAR_EVENT_PROJECTION, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return
                }
                val event = calendarEvent(cursor)
                if (eventID in calendarEventSignatures) {
                    calendarEventSignatures[eventID] = calendarEventSignature(event)
                }
                DikcizAutomationEventBus.enqueue(applicationContext, event)
            }
        } catch (exception: SecurityException) {
            logCalendarQueryRejected(exception)
        } catch (exception: IllegalArgumentException) {
            logCalendarQueryRejected(exception)
        }
    }

    private fun dispatchContactsChanged() {
        if (!contactsObserverRegistered || !hasContactsChangedSubscription(configuration ?: return)) {
            return
        }
        DikcizAutomationEventBus.enqueue(
            applicationContext,
            automationEvent(
                DikcizAutomationEventType.ContactsChanged,
                SOURCE_CONTACTS,
                COALESCING_CONTACTS_CHANGED,
                JSONObject().put(PAYLOAD_CHANGE, CONTACTS_CHANGE_CHANGED),
            ),
        )
    }

    private fun reconcileCalendarEvents() {
        if (!calendarEventObserverRegistered || !hasCalendarEventSubscription(configuration ?: return)) {
            return
        }
        val observations = calendarEventObservations() ?: return
        val previousSignatures = calendarEventSignatures.toMap()
        replaceCalendarEventSignatures(observations)
        observations.events.forEach { observation ->
            if (previousSignatures[observation.eventID] == observation.signature) {
                return@forEach
            }
            DikcizAutomationEventBus.enqueue(applicationContext, observation.event)
        }
    }

    private fun calendarEventObservations(): CalendarEventObservations? {
        return try {
            val events = mutableListOf<CalendarEventObservation>()
            contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                CALENDAR_EVENT_PROJECTION,
                null,
                null,
                CALENDAR_EVENT_SORT_ORDER,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val event = calendarEvent(cursor)
                    events += CalendarEventObservation(
                        eventID = event.payload.getLong(PAYLOAD_CALENDAR_EVENT_ID),
                        event = event,
                        signature = calendarEventSignature(event),
                    )
                }
            }
            CalendarEventObservations(
                events = events,
                isLimitReached = events.size == MAXIMUM_CALENDAR_EVENT_RECONCILIATION_RECORDS,
            )
        } catch (exception: SecurityException) {
            logCalendarQueryRejected(exception)
            null
        } catch (exception: IllegalArgumentException) {
            logCalendarQueryRejected(exception)
            null
        }
    }

    private fun replaceCalendarEventSignatures(observations: CalendarEventObservations) {
        calendarEventSignatures.clear()
        observations.events.forEach { observation ->
            calendarEventSignatures[observation.eventID] = observation.signature
        }
        if (
            observations.isLimitReached &&
            !calendarEventReconciliationLimitReached
        ) {
            logger.warn(EVENT_CALENDAR_RECONCILIATION_LIMIT_REACHED, emptyMap())
        }
        calendarEventReconciliationLimitReached = observations.isLimitReached
    }

    private fun calendarEventSignature(event: DikcizAutomationEvent): String {
        return Base64.encodeToString(
            MessageDigest.getInstance(CALENDAR_EVENT_DIGEST_ALGORITHM).digest(
                event.payload.toString().toByteArray(StandardCharsets.UTF_8),
            ),
            Base64.NO_WRAP,
        )
    }

    private fun calendarEventID(uri: android.net.Uri?): Long? {
        if (uri?.authority != CalendarContract.AUTHORITY) {
            return null
        }
        val pathSegments = uri.pathSegments
        if (
            pathSegments.size != CALENDAR_EVENT_URI_PATH_SEGMENT_COUNT ||
            pathSegments[CALENDAR_EVENT_COLLECTION_PATH_SEGMENT_INDEX] != CALENDAR_EVENTS_PATH_SEGMENT
        ) {
            return null
        }
        return pathSegments[CALENDAR_EVENT_ID_PATH_SEGMENT_INDEX].toLongOrNull()
    }

    private fun calendarChangeReason(uri: android.net.Uri?): String {
        if (uri == null) {
            return REASON_CALENDAR_URI_MISSING
        }
        if (uri.authority != CalendarContract.AUTHORITY) {
            return REASON_CALENDAR_AUTHORITY_UNSUPPORTED
        }
        val pathSegments = uri.pathSegments
        if (pathSegments.isEmpty()) {
            return REASON_CALENDAR_AUTHORITY_ROOT
        }
        if (pathSegments.size == CALENDAR_EVENT_COLLECTION_PATH_SEGMENT_COUNT &&
            pathSegments[CALENDAR_EVENT_COLLECTION_PATH_SEGMENT_INDEX] == CALENDAR_EVENTS_PATH_SEGMENT
        ) {
            return REASON_CALENDAR_EVENTS_COLLECTION
        }
        if (pathSegments.size == CALENDAR_EVENT_URI_PATH_SEGMENT_COUNT &&
            pathSegments[CALENDAR_EVENT_COLLECTION_PATH_SEGMENT_INDEX] == CALENDAR_EVENTS_PATH_SEGMENT
        ) {
            return REASON_CALENDAR_EVENT_ITEM
        }
        return REASON_CALENDAR_PATH_UNSUPPORTED
    }

    private fun logCalendarQueryRejected(exception: Exception) {
        logger.warn(
            EVENT_CALENDAR_QUERY_REJECTED,
            mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
        )
    }

    private fun calendarEvent(cursor: Cursor): DikcizAutomationEvent {
        val eventID = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events._ID))
        val payload = JSONObject()
            .put(PAYLOAD_CALENDAR_EVENT_ID, eventID)
            .put(
                PAYLOAD_CALENDAR_ID,
                cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)),
            )
            .put(
                PAYLOAD_CALENDAR_START_MILLISECONDS,
                cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)),
            )
            .put(
                PAYLOAD_CALENDAR_END_MILLISECONDS,
                cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)),
            )
            .put(
                PAYLOAD_CALENDAR_ALL_DAY,
                cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)) != NO_CALENDAR_BOOLEAN_VALUE,
            )
            .put(
                PAYLOAD_CALENDAR_AVAILABILITY,
                cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Events.AVAILABILITY)),
            )
            .put(
                PAYLOAD_CALENDAR_STATUS,
                cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Events.STATUS)),
            )
            .put(
                PAYLOAD_CALENDAR_RECURRING,
                !cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.RRULE)).isNullOrBlank(),
            )
            .put(
                PAYLOAD_CALENDAR_DESCRIPTION,
                calendarEventText(cursor, CalendarContract.Events.DESCRIPTION),
            )
            .put(
                PAYLOAD_CALENDAR_LOCATION,
                calendarEventText(cursor, CalendarContract.Events.EVENT_LOCATION),
            )
            .put(
                PAYLOAD_CALENDAR_TITLE,
                calendarEventText(cursor, CalendarContract.Events.TITLE),
            )
        return automationEvent(
            DikcizAutomationEventType.CalendarEvent,
            SOURCE_CALENDAR,
            "$COALESCING_CALENDAR_EVENT_PREFIX$eventID",
            payload,
        )
    }

    private fun calendarEventText(cursor: Cursor, column: String): String {
        return cursor.getString(cursor.getColumnIndexOrThrow(column))
            .orEmpty()
            .take(MAXIMUM_CALENDAR_EVENT_TEXT_CHARACTERS)
    }

    private data class CalendarEventObservation(
        val eventID: Long,
        val event: DikcizAutomationEvent,
        val signature: String,
    )

    private data class CalendarEventObservations(
        val events: List<CalendarEventObservation>,
        val isLimitReached: Boolean,
    )

    private data class AlarmSchedule(
        val interval: Long?,
        val scriptSubscriptions: Set<AlarmScheduleSubscription>,
        val htmlSubscriptions: Set<DikcizAutomationSubscription>,
    )

    private data class AlarmScheduleSubscription(
        val scriptID: String,
        val subscription: DikcizAutomationSubscription,
    )

    @TargetApi(Build.VERSION_CODES.S)
    private inner class PhoneStateCallback : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            dispatchPhoneState(state)
        }
    }

    private fun automationEvent(
        type: DikcizAutomationEventType,
        source: String,
        coalescingKey: String,
        payload: JSONObject,
        locationPrecision: DikcizLocationPrecision? = null,
    ): DikcizAutomationEvent {
        return DikcizAutomationEvent(
            type = type,
            source = source,
            timestampMilliseconds = System.currentTimeMillis(),
            coalescingKey = coalescingKey,
            payload = payload,
            locationPrecision = locationPrecision,
        )
    }

    @Suppress("DEPRECATION")
    private inner class ConfigurationFileObserver(
        directory: File,
    ) : FileObserver(directory.absolutePath, CONFIGURATION_FILE_OBSERVER_EVENTS) {
        override fun onEvent(event: Int, path: String?) {
            if (event and CONFIGURATION_FILE_OBSERVER_EVENTS == NO_FILE_EVENT) {
                return
            }
            configurationHandler.removeCallbacks(configurationReload)
            configurationHandler.postDelayed(configurationReload, CONFIGURATION_RELOAD_DEBOUNCE_MILLISECONDS)
        }
    }

    private companion object {
        val CONNECTIVITY_TRANSPORTS = listOf(
            ConnectivityTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH, TRANSPORT_BLUETOOTH),
            ConnectivityTransport(NetworkCapabilities.TRANSPORT_CELLULAR, TRANSPORT_CELLULAR),
            ConnectivityTransport(NetworkCapabilities.TRANSPORT_ETHERNET, TRANSPORT_ETHERNET),
            ConnectivityTransport(NetworkCapabilities.TRANSPORT_VPN, TRANSPORT_VPN),
            ConnectivityTransport(NetworkCapabilities.TRANSPORT_WIFI, TRANSPORT_WIFI),
            ConnectivityTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE, TRANSPORT_WIFI_AWARE),
        )
        const val ACTION_ALARM = "org.fossify.home.dikciz.AUTOMATION_ALARM"
        const val ALARM_REQUEST_CODE = 7301
        const val COALESCING_BATTERY = "battery"
        const val COALESCING_BLUETOOTH_STATE = "bluetooth"
        const val COALESCING_CALENDAR_EVENT_PREFIX = "calendar:"
        const val COALESCING_CHARGING = "charging"
        const val COALESCING_CONNECTIVITY = "connectivity"
        const val COALESCING_CONTACTS_CHANGED = "contacts"
        const val COALESCING_DEVICE_CONFIGURATION = "device-configuration"
        const val COALESCING_DEVICE_IDLE = "device-idle"
        const val COALESCING_INTERRUPTION_FILTER = "interruption-filter"
        const val COALESCING_NIGHT_MODE = "night-mode"
        const val COALESCING_PACKAGE_PREFIX = "package:"
        const val COALESCING_POWER_SAVE = "power-save"
        const val COALESCING_RINGER_MODE = "ringer-mode"
        const val COALESCING_THERMAL_STATUS = "thermal"
        const val COALESCING_LOCATION_PREFIX = "location:"
        const val COALESCING_PHONE_STATE = "phone-state"
        const val COALESCING_SCREEN = "screen"
        const val COALESCING_SENSOR_PREFIX = "sensor:"
        const val COALESCING_TIME = "time"
        const val COALESCING_USER = "user"
        const val CONFIGURATION_FILE_OBSERVER_EVENTS =
            FileObserver.CLOSE_WRITE or FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_FROM or
                FileObserver.MOVED_TO
        const val CONFIGURATION_RELOAD_DEBOUNCE_MILLISECONDS = 150L
        const val EVENT_CONFIGURATION_REJECTED = "automation_service_configuration_rejected"
        const val EVENT_FOREGROUND_START_REJECTED = "automation_foreground_start_rejected"
        const val EVENT_ALARM_CANCELLED = "automation_alarm_cancelled"
        const val EVENT_ALARM_DELIVERED = "automation_alarm_delivered"
        const val FIELD_SOURCE = "source"
        const val FIELD_LATENESS_MILLISECONDS = "lateness_ms"
        const val FIELD_RECOVERY = "recovery"
        const val SOURCE_ALARM = "alarm"
        const val SOURCE_FOREGROUND_TIMER = "foreground_timer"
        const val SOURCE_WAKEUP_ALARM = "wakeup_alarm"
        const val EVENT_CALENDAR_CHANGE_RECEIVED = "automation_calendar_change_received"
        const val EVENT_CALENDAR_QUERY_REJECTED = "automation_calendar_event_query_rejected"
        const val EVENT_CALENDAR_RECONCILIATION_LIMIT_REACHED = "automation_calendar_reconciliation_limit_reached"
        const val EVENT_CALENDAR_REGISTERED = "automation_calendar_event_registered"
        const val EVENT_CALENDAR_REGISTRATION_REJECTED = "automation_calendar_event_registration_rejected"
        const val EVENT_CONTACTS_REGISTERED = "automation_contacts_registered"
        const val EVENT_CONTACTS_REGISTRATION_REJECTED = "automation_contacts_registration_rejected"
        const val EVENT_LOCATION_REGISTERED = "automation_location_registered"
        const val EVENT_LOCATION_RECEIVED = "automation_location_received"
        const val EVENT_LOCATION_REGISTRATION_REJECTED = "automation_location_registration_rejected"
        const val EVENT_LOCATION_REGISTRATION_SKIPPED = "automation_location_registration_skipped"
        const val EVENT_PHONE_STATE_IGNORED = "automation_phone_state_ignored"
        const val EVENT_PHONE_STATE_REGISTERED = "automation_phone_state_registered"
        const val EVENT_PHONE_STATE_REGISTRATION_REJECTED = "automation_phone_state_registration_rejected"
        const val EVENT_PHONE_STATE_REGISTRATION_SKIPPED = "automation_phone_state_registration_skipped"
        const val EVENT_PACKAGE_LIFECYCLE_IGNORED = "automation_package_lifecycle_ignored"
        const val EVENT_SENSOR_REGISTRATION_REJECTED = "automation_sensor_registration_rejected"
        const val EVENT_THERMAL_STATUS_IGNORED = "automation_thermal_status_ignored"
        const val EVENT_THERMAL_STATUS_REGISTERED = "automation_thermal_status_registered"
        const val EVENT_THERMAL_STATUS_REGISTRATION_REJECTED = "automation_thermal_status_registration_rejected"
        const val EVENT_THERMAL_STATUS_UNREGISTRATION_REJECTED = "automation_thermal_status_unregistration_rejected"
        const val FIELD_ERROR_CLASS = "error_class"
        const val FIELD_LOCATION_PRECISION = "location_precision"
        const val FIELD_PROVIDER = "provider"
        const val FIELD_REASON = "reason"
        const val FIELD_SENSOR_TYPE = "sensor_type"
        const val FOREGROUND_CHANNEL_ID = DikcizAutomationForegroundNotification.CHANNEL_ID
        const val FOREGROUND_CHANNEL_NAME = "Dikciz automation service"
        const val FOREGROUND_NOTIFICATION_ID = DikcizAutomationForegroundNotification.ID
        const val FOREGROUND_NOTIFICATION_TEXT = "Automation sources are active"
        const val INVALID_PHONE_STATE = -1
        const val INVALID_RINGER_MODE = -1
        const val INVALID_SENSOR_TYPE = -1
        const val CALENDAR_EVENT_COLLECTION_PATH_SEGMENT_INDEX = 0
        const val CALENDAR_EVENT_COLLECTION_PATH_SEGMENT_COUNT = 1
        const val CALENDAR_EVENT_ID_PATH_SEGMENT_INDEX = 1
        const val CALENDAR_EVENT_URI_PATH_SEGMENT_COUNT = 2
        const val CALENDAR_EVENTS_PATH_SEGMENT = "events"
        val CALENDAR_EVENT_PROJECTION = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.AVAILABILITY,
            CalendarContract.Events.STATUS,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.TITLE,
        )
        val CALENDAR_EVENT_SORT_ORDER =
            "${CalendarContract.Events._ID} DESC LIMIT $MAXIMUM_CALENDAR_EVENT_RECONCILIATION_RECORDS"
        const val CALENDAR_EVENT_DIGEST_ALGORITHM = "SHA-256"
        const val DEFAULT_FONT_SCALE_PERCENT = 100
        const val FONT_SCALE_PERCENT_MULTIPLIER = 100
        const val LOCATION_MINIMUM_DISTANCE_METERS = 0f
        const val APPROXIMATE_LOCATION_COORDINATE_SCALE = 100.0
        const val LOCATION_MINIMUM_INTERVAL_MILLISECONDS = 1_000L
        const val LOG_TAG = "DikcizAutomationService"
        const val MAXIMUM_SENSOR_NAME_CHARACTERS = 120
        const val MAXIMUM_SENSOR_VALUES = 16
        const val MAXIMUM_ANDROID_PACKAGE_NAME_CHARACTERS = 255
        const val MAXIMUM_CALENDAR_EVENT_TEXT_CHARACTERS = 512
        const val MAXIMUM_CALENDAR_EVENT_RECONCILIATION_RECORDS = 256
        const val MAXIMUM_FONT_SCALE_PERCENT = 300
        const val MINIMUM_FONT_SCALE_PERCENT = 50
        const val NO_ACTIONS = 0
        const val NO_CALENDAR_BOOLEAN_VALUE = 0
        const val NO_FILE_EVENT = 0
        const val NO_POWER_SOURCE = 0
        const val PAYLOAD_ACCURACY = "accuracy"
        const val PAYLOAD_ACTION = "action"
        const val PAYLOAD_ALARM_ELAPSED_REALTIME = "alarmElapsedRealtime"
        const val PAYLOAD_ALARM_RECOVERY = "alarmRecovery"
        const val PAYLOAD_CALENDAR_ALL_DAY = "allDay"
        const val PAYLOAD_CALENDAR_AVAILABILITY = "availability"
        const val PAYLOAD_CALENDAR_DESCRIPTION = "description"
        const val PAYLOAD_CALENDAR_END_MILLISECONDS = "endMilliseconds"
        const val PAYLOAD_CALENDAR_EVENT_ID = "eventId"
        const val PAYLOAD_CALENDAR_ID = "calendarId"
        const val PAYLOAD_CALENDAR_LOCATION = "location"
        const val PAYLOAD_CALENDAR_RECURRING = "recurring"
        const val PAYLOAD_CALENDAR_START_MILLISECONDS = "startMilliseconds"
        const val PAYLOAD_CALENDAR_STATUS = "status"
        const val PAYLOAD_CALENDAR_TITLE = "title"
        const val PAYLOAD_CHARGING = "charging"
        const val PAYLOAD_CHANGE = "change"
        const val PAYLOAD_ENABLED = "enabled"
        const val PAYLOAD_FONT_SCALE_PERCENT = "fontScalePercent"
        const val PAYLOAD_IDLE = "idle"
        const val PAYLOAD_LEVEL = "level"
        const val PAYLOAD_METERED = "metered"
        const val PAYLOAD_MODE = "mode"
        const val PAYLOAD_NIGHT = "night"
        const val PAYLOAD_ORIENTATION = "orientation"
        const val PAYLOAD_PACKAGE_NAME = "packageName"
        const val PAYLOAD_SCREEN_ON = "screenOn"
        const val PAYLOAD_SENSOR_NAME = "sensorName"
        const val PAYLOAD_SENSOR_TYPE = "sensorType"
        const val PAYLOAD_STATE = "state"
        const val PAYLOAD_STATUS = "status"
        const val PAYLOAD_TRANSPORTS = "transports"
        const val PAYLOAD_VALIDATED = "validated"
        const val PAYLOAD_VALUES = "values"
        const val PACKAGE_URI_SCHEME = "package"
        const val REASON_PERMISSION_MISSING = "permission_missing"
        const val REASON_CALENDAR_AUTHORITY_UNSUPPORTED = "calendar_authority_unsupported"
        const val REASON_CALENDAR_AUTHORITY_ROOT = "calendar_authority_root"
        const val REASON_CALENDAR_EVENT_ITEM = "calendar_event_item"
        const val REASON_CALENDAR_EVENTS_COLLECTION = "calendar_events_collection"
        const val REASON_CALENDAR_PATH_UNSUPPORTED = "calendar_path_unsupported"
        const val REASON_CALENDAR_URI_MISSING = "calendar_uri_missing"
        const val REASON_PROVIDER_DISABLED = "provider_disabled"
        const val REASON_PHONE_STATE_UNSUPPORTED = "phone_state_unsupported"
        const val REASON_PACKAGE_NAME_INVALID = "package_name_invalid"
        const val REASON_SUBSCRIPTION_MISSING = "subscription_missing"
        const val REASON_TELEPHONY_UNAVAILABLE = "telephony_unavailable"
        const val REASON_THERMAL_STATUS_UNSUPPORTED = "thermal_status_unsupported"
        const val SOURCE_BATTERY = "battery"
        const val SOURCE_BLUETOOTH = "bluetooth"
        const val SOURCE_CALENDAR = "calendar"
        const val SOURCE_CONNECTIVITY = "connectivity"
        const val SOURCE_CONTACTS = "contacts"
        const val SOURCE_DEVICE_CONFIGURATION = "device-configuration"
        const val SOURCE_DEVICE_IDLE = "device-idle"
        const val SOURCE_INTERRUPTION_FILTER = "interruption-filter"
        const val SOURCE_NIGHT_MODE = "night-mode"
        const val SOURCE_PACKAGE_PREFIX = "package:"
        const val SOURCE_LOCATION_PREFIX = "location:"
        const val SOURCE_PHONE = "phone"
        const val SOURCE_POWER = "power"
        const val SOURCE_POWER_SAVE = "power-save"
        const val SOURCE_RINGER = "ringer"
        const val SOURCE_SCREEN = "screen"
        const val SOURCE_SENSOR_PREFIX = "sensor:"
        const val SOURCE_TIME = "time"
        const val SOURCE_THERMAL = "thermal"
        const val SOURCE_USER = "user"
        const val STATE_CONNECTED = "connected"
        const val STATE_DISCONNECTED = "disconnected"
        const val TRANSPORT_BLUETOOTH = "bluetooth"
        const val TRANSPORT_CELLULAR = "cellular"
        const val TRANSPORT_ETHERNET = "ethernet"
        const val TRANSPORT_VPN = "vpn"
        const val TRANSPORT_WIFI = "wifi"
        const val TRANSPORT_WIFI_AWARE = "wifiAware"
        const val THERMAL_STATUS_CRITICAL = "critical"
        const val THERMAL_STATUS_EMERGENCY = "emergency"
        const val THERMAL_STATUS_LIGHT = "light"
        const val THERMAL_STATUS_MODERATE = "moderate"
        const val THERMAL_STATUS_NONE = "none"
        const val THERMAL_STATUS_SEVERE = "severe"
        const val THERMAL_STATUS_SHUTDOWN = "shutdown"
        const val PHONE_STATE_IDLE = "idle"
        const val PHONE_STATE_OFFHOOK = "offhook"
        const val PHONE_STATE_RINGING = "ringing"
        const val RINGER_MODE_NORMAL = "normal"
        const val RINGER_MODE_SILENT = "silent"
        const val RINGER_MODE_VIBRATE = "vibrate"
        const val ORIENTATION_LANDSCAPE = "landscape"
        const val ORIENTATION_PORTRAIT = "portrait"
        const val ORIENTATION_UNDEFINED = "undefined"
        const val PACKAGE_CHANGE_CHANGED = "changed"
        const val PACKAGE_CHANGE_INSTALLED = "installed"
        const val PACKAGE_CHANGE_REMOVED = "removed"
        const val PACKAGE_CHANGE_UPDATED = "updated"
        const val CONTACTS_CHANGE_CHANGED = "changed"
        const val INTERRUPTION_FILTER_ALL = "all"
        const val INTERRUPTION_FILTER_PRIORITY = "priority"
        const val INTERRUPTION_FILTER_ALARMS = "alarms"
        const val INTERRUPTION_FILTER_NONE = "none"
        const val INTERRUPTION_FILTER_UNKNOWN = "unknown"
        const val UNKNOWN_LEVEL = -1
        const val UNKNOWN_STATUS = -1
        private val ANDROID_PACKAGE_NAME_PATTERN = Regex(
            "^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$",
        )
    }

    private data class ConnectivityTransport(
        val value: Int,
        val name: String,
    )
}

internal class DikcizAutomationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) {
            return
        }
        val store = HomeConfigStore(context.applicationContext)
        DikcizLogger(LOG_TAG, store.logDirectory).info(EVENT_ALARM_RECEIVED)
        val serviceIntent = Intent(context, DikcizAutomationService::class.java).setAction(ACTION_ALARM)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (exception: IllegalStateException) {
            logServiceStartRejected(context, exception)
        } catch (exception: SecurityException) {
            logServiceStartRejected(context, exception)
        }
    }

    private fun logServiceStartRejected(context: Context, exception: Exception) {
        val store = HomeConfigStore(context.applicationContext)
        DikcizLogger(LOG_TAG, store.logDirectory).warn(
            EVENT_ALARM_SERVICE_START_REJECTED,
            mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
        )
    }

    private companion object {
        const val ACTION_ALARM = "org.fossify.home.dikciz.AUTOMATION_ALARM"
        const val EVENT_ALARM_RECEIVED = "automation_alarm_receiver_received"
        const val EVENT_ALARM_SERVICE_START_REJECTED = "automation_alarm_service_start_rejected"
        const val FIELD_ERROR_CLASS = "error_class"
        const val LOG_TAG = "DikcizAutomationAlarm"
    }
}
