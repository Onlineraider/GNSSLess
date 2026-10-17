package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.PathPointEntity
import com.example.data.RawDataLogger
import com.example.data.RouteEntity
import com.example.engine.GeodesyUtils
import com.example.engine.InertialEngine
import com.example.mock.MockLocationPublisher
import com.example.model.GpsFix
import com.example.model.InertialFix
import com.example.model.PowerProfile
import com.example.model.TrackingMetrics
import com.example.model.TrackingState
import com.example.model.TransportMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground Service responsible for:
 * - Real GPS start-fix acquisition and manual re-anchoring (P30, P31)
 * - Pure inertial dead-reckoning during movement (P01-P06)
 * - Mock location publishing to system (P40-P44)
 * - Raw sensor data logging (P52)
 * - Parallel test-mode GPS ground-truth recording (P60, P61)
 * - Reliable background tracking with WakeLock when screen is off (P41)
 */
class TrackingService : Service(), SensorEventListener, LocationListener {

    private val tag = "TrackingService"
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var mockPublisher: MockLocationPublisher
    private lateinit var rawDataLogger: RawDataLogger
    private lateinit var database: AppDatabase

    private var engine = InertialEngine()

    // Config options passed upon starting
    private var activeMode = TransportMode.WALKING
    private var activePowerProfile = PowerProfile.MAX_ACCURACY
    private var isTestModeActive = false
    private var mockRateHz = 5
    private var gpsQualityThresholdMeters = 15.0f

    // Database route tracking
    private var currentRouteId: Long = 0L
    private var sessionStartTimeMs: Long = 0L
    private val pendingPoints = mutableListOf<PathPointEntity>()
    private var dbFlushJob: Job? = null
    private var mockPublishJob: Job? = null
    private var timerJob: Job? = null

    // GPS state
    private var isWaitingForStartFix = true
    private var isReanchoring = false
    private var latestRealGpsFix: GpsFix? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "inertial_tracker_channel"

        const val ACTION_START = "com.example.action.START"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_PAUSE = "com.example.action.PAUSE"
        const val ACTION_RESUME = "com.example.action.RESUME"
        const val ACTION_ACCEPT_CURRENT_FIX = "com.example.action.ACCEPT_CURRENT_FIX"
        const val ACTION_REANCHOR = "com.example.action.REANCHOR"

        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_POWER_PROFILE = "extra_power_profile"
        const val EXTRA_TEST_MODE = "extra_test_mode"
        const val EXTRA_MOCK_RATE = "extra_mock_rate"
        const val EXTRA_GPS_THRESHOLD = "extra_gps_threshold"

        private val _metricsState = MutableStateFlow(TrackingMetrics())
        val metricsState: StateFlow<TrackingMetrics> = _metricsState.asStateFlow()

        private val _recentPathPoints = MutableStateFlow<List<PathPointEntity>>(emptyList())
        val recentPathPoints: StateFlow<List<PathPointEntity>> = _recentPathPoints.asStateFlow()

        var isRunning: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "InertialTracker::TrackingWakeLock")

        mockPublisher = MockLocationPublisher(this)
        rawDataLogger = RawDataLogger(this)
        database = AppDatabase.getInstance(this)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val modeStr = intent.getStringExtra(EXTRA_MODE) ?: TransportMode.WALKING.name
                val profileStr = intent.getStringExtra(EXTRA_POWER_PROFILE) ?: PowerProfile.MAX_ACCURACY.name
                activeMode = try { TransportMode.valueOf(modeStr) } catch (e: Exception) { TransportMode.WALKING }
                activePowerProfile = try { PowerProfile.valueOf(profileStr) } catch (e: Exception) { PowerProfile.MAX_ACCURACY }
                isTestModeActive = intent.getBooleanExtra(EXTRA_TEST_MODE, false)
                mockRateHz = intent.getIntExtra(EXTRA_MOCK_RATE, activePowerProfile.mockRateHz)
                gpsQualityThresholdMeters = intent.getFloatExtra(EXTRA_GPS_THRESHOLD, 15.0f)

                startTrackingSession()
            }
            ACTION_ACCEPT_CURRENT_FIX -> {
                latestRealGpsFix?.let { fix ->
                    acceptGpsAnchor(fix)
                }
            }
            ACTION_REANCHOR -> {
                triggerReanchor()
            }
            ACTION_PAUSE -> {
                pauseTracking()
            }
            ACTION_RESUME -> {
                resumeTracking()
            }
            ACTION_STOP -> {
                stopTrackingSession()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTrackingSession() {
        isRunning = true
        sessionStartTimeMs = System.currentTimeMillis()
        wakeLock?.acquire(8 * 3600 * 1000L) // 8h max safety timeout

        val initialNotif = buildNotification(
            title = getString(R.string.status_acquiring_fix),
            text = getString(R.string.gps_status_searching)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initialNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, initialNotif)
        }

        serviceScope.launch {
            // Load calibrated parameters for this mode
            val calib = database.calibrationDao().getCalibration(activeMode.name)
            engine = InertialEngine(
                mode = activeMode,
                gyroBiasX = calib?.gyroBiasX ?: 0f,
                gyroBiasY = calib?.gyroBiasY ?: 0f,
                gyroBiasZ = calib?.gyroBiasZ ?: 0f,
                accelBiasX = calib?.accelBiasX ?: 0f,
                accelBiasY = calib?.accelBiasY ?: 0f,
                accelBiasZ = calib?.accelBiasZ ?: 0f,
                stepLengthMultiplier = calib?.stepLengthMultiplier ?: 1.0f,
                customSpeedDamping = calib?.speedDampingFactor
            )

            // Create initial route record in Room
            val initialRoute = RouteEntity(
                title = "Route " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                startTime = sessionStartTimeMs,
                endTime = 0L,
                mode = activeMode.name,
                isPhoneFixed = activeMode.isFixedMount,
                powerProfile = activePowerProfile.name,
                isTestMode = isTestModeActive,
                durationSeconds = 0L,
                totalDistanceMeters = 0f,
                avgSpeedMps = 0f,
                maxSpeedMps = 0f,
                elevationGainMeters = 0f,
                finalErrorRadiusMeters = 10f,
                pointsCount = 0
            )
            currentRouteId = database.routeDao().insertRoute(initialRoute)
            val rawPath = rawDataLogger.startLogging(currentRouteId)
            database.routeDao().updateRoute(initialRoute.copy(id = currentRouteId, rawLogFilePath = rawPath))

            isWaitingForStartFix = true
            _metricsState.value = TrackingMetrics(
                trackingState = TrackingState.ACQUIRING_START_FIX,
                activeMode = activeMode,
                isTestModeActive = isTestModeActive
            )
            _recentPathPoints.value = emptyList()

            // Step P43: Avoid self-mock feedback during fix acquisition
            mockPublisher.stop()

            // Start hardware GNSS listener for initial fix
            withContext(Dispatchers.Main) {
                try {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L,
                        0f,
                        this@TrackingService
                    )
                } catch (e: SecurityException) {
                    Log.e(tag, "Location permission not granted for start fix", e)
                }
            }
        }
    }

    private fun acceptGpsAnchor(fix: GpsFix) {
        isWaitingForStartFix = false
        isReanchoring = false

        engine.anchorWithGps(fix)
        rawDataLogger.logAnchor(fix.latitude, fix.longitude, fix.altitude, fix.accuracyMeters)

        // If test mode is not enabled, stop GPS completely (P32)!
        if (!isTestModeActive) {
            try {
                locationManager.removeUpdates(this)
            } catch (e: Exception) {
                Log.e(tag, "Failed to remove GPS updates", e)
            }
        }

        // Initialize and start Mock Location Publisher (P40)
        val mockSuccess = mockPublisher.start()

        // Register sensors based on power profile
        registerSensors()

        // Start background mock dispatch and DB flush jobs
        startPeriodicJobs(mockSuccess)

        updateNotification()
    }

    private fun triggerReanchor() {
        if (_metricsState.value.trackingState != TrackingState.TRACKING) return
        isReanchoring = true
        // Step P43: stop mock publisher so test provider doesn't intercept GNSS
        mockPublisher.stop()

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                this
            )
        } catch (e: SecurityException) {
            Log.e(tag, "Location permission missing for re-anchor", e)
        }
    }

    private fun registerSensors() {
        val delay = activePowerProfile.samplingDelayUs

        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(this, it, delay)
        } ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(this, it, delay)
        }

        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let {
            sensorManager.registerListener(this, it, delay)
        } ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, delay)
        }

        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager.registerListener(this, it, delay)
        }

        sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
            sensorManager.registerListener(this, it, delay)
        }

        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
            sensorManager.registerListener(this, it, delay)
        }
    }

    private fun startPeriodicJobs(mockStarted: Boolean) {
        mockPublishJob?.cancel()
        dbFlushJob?.cancel()
        timerJob?.cancel()

        // Mock publisher loop
        val intervalMs = (1000L / mockRateHz).coerceIn(100L, 2000L)
        mockPublishJob = serviceScope.launch {
            while (isActive) {
                delay(intervalMs)
                if (_metricsState.value.trackingState == TrackingState.TRACKING) {
                    _metricsState.value.currentFix?.let { fix ->
                        mockPublisher.publishFix(fix)
                    }
                }
            }
        }

        // Database batching loop (flushes points to Room DB every 2 seconds)
        dbFlushJob = serviceScope.launch {
            while (isActive) {
                delay(2000L)
                flushPendingPointsToDb()
            }
        }

        // Timer job (updates elapsed seconds & notification every second)
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                if (_metricsState.value.trackingState == TrackingState.TRACKING) {
                    val durationSec = (System.currentTimeMillis() - sessionStartTimeMs) / 1000L
                    _metricsState.value = _metricsState.value.copy(
                        durationSeconds = durationSec,
                        distanceTraveledMeters = engine.totalDistanceMeters,
                        maxSpeedMps = engine.maxSpeedMps,
                        elevationGainMeters = engine.elevationGainMeters,
                        isMockActive = mockPublisher.isPublishing
                    )
                    updateNotification()
                }
            }
        }
    }

    private fun pauseTracking() {
        _metricsState.value = _metricsState.value.copy(trackingState = TrackingState.PAUSED)
        _metricsState.value.currentFix?.let { fix ->
            mockPublisher.publishStationaryPause(fix)
        }
        updateNotification()
    }

    private fun resumeTracking() {
        _metricsState.value = _metricsState.value.copy(trackingState = TrackingState.TRACKING)
        updateNotification()
    }

    private fun stopTrackingSession() {
        isRunning = false
        _metricsState.value = _metricsState.value.copy(trackingState = TrackingState.STOPPED)

        mockPublishJob?.cancel()
        timerJob?.cancel()
        dbFlushJob?.cancel()

        sensorManager.unregisterListener(this)
        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) {
            // ignore
        }
        mockPublisher.stop()
        val rawPath = rawDataLogger.stopLogging()

        serviceScope.launch {
            flushPendingPointsToDb()
            // Finalize Route Entity with all stats
            val existing = database.routeDao().getRouteById(currentRouteId)
            if (existing != null) {
                val durationSec = (System.currentTimeMillis() - sessionStartTimeMs) / 1000L
                val avgSpeed = if (durationSec > 0) engine.totalDistanceMeters / durationSec else 0f
                val updated = existing.copy(
                    endTime = System.currentTimeMillis(),
                    durationSeconds = durationSec,
                    totalDistanceMeters = engine.totalDistanceMeters,
                    avgSpeedMps = avgSpeed,
                    maxSpeedMps = engine.maxSpeedMps,
                    elevationGainMeters = engine.elevationGainMeters,
                    finalErrorRadiusMeters = engine.currentErrorRadiusMeters,
                    pointsCount = _recentPathPoints.value.size,
                    rawLogFilePath = rawPath ?: existing.rawLogFilePath
                )
                database.routeDao().updateRoute(updated)
            }

            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun flushPendingPointsToDb() {
        val pointsToInsert = synchronized(pendingPoints) {
            if (pendingPoints.isEmpty()) return
            val copy = ArrayList(pendingPoints)
            pendingPoints.clear()
            copy
        }
        withContext(Dispatchers.IO) {
            try {
                database.routeDao().insertPoints(pointsToInsert)
            } catch (e: Exception) {
                Log.e(tag, "Failed to flush points to Room DB", e)
            }
        }
    }

    // --- SensorEventListener Implementations ---
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (_metricsState.value.trackingState != TrackingState.TRACKING &&
            _metricsState.value.trackingState != TrackingState.PAUSED) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                engine.onRotationVector(event.values)
                rawDataLogger.logRotationVector(event.values, event.timestamp)
            }
            Sensor.TYPE_PRESSURE -> {
                val p = event.values[0]
                engine.onBarometerPressure(p)
                rawDataLogger.logPressure(p, event.timestamp)
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                engine.onStepDetected(event.timestamp)
                rawDataLogger.logStep(event.timestamp)
            }
            Sensor.TYPE_GYROSCOPE -> {
                rawDataLogger.logGyro(event.values[0], event.values[1], event.values[2], event.timestamp)
            }
            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> {
                if (_metricsState.value.trackingState == TrackingState.PAUSED) return

                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                rawDataLogger.logLinearAccel(ax, ay, az, event.timestamp)

                val fix = engine.onLinearAcceleration(ax, ay, az, event.timestamp)
                if (fix != null) {
                    onInertialFixGenerated(fix)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun onInertialFixGenerated(fix: InertialFix) {
        val currentGpsComp = latestRealGpsFix
        val errorDist = if (isTestModeActive && currentGpsComp != null) {
            GeodesyUtils.distanceMeters(
                fix.latitude, fix.longitude,
                currentGpsComp.latitude, currentGpsComp.longitude
            ).toFloat()
        } else null

        val pointEntity = PathPointEntity(
            routeId = currentRouteId,
            timestamp = fix.timestamp,
            latitude = fix.latitude,
            longitude = fix.longitude,
            altitude = fix.altitude,
            speedMps = fix.speedMps,
            courseDeg = fix.courseDeg,
            errorRadiusMeters = fix.errorRadiusMeters,
            isZupt = fix.isZupt,
            altitudeSource = fix.altitudeSource.name,
            isGpsComparison = isTestModeActive && currentGpsComp != null,
            gpsLatitude = currentGpsComp?.latitude,
            gpsLongitude = currentGpsComp?.longitude,
            gpsAltitude = currentGpsComp?.altitude,
            gpsAccuracy = currentGpsComp?.accuracyMeters,
            errorDistanceMeters = errorDist
        )

        synchronized(pendingPoints) {
            pendingPoints.add(pointEntity)
        }

        val updatedPoints = _recentPathPoints.value + pointEntity
        // Keep in-memory list bounded to recent 2000 points for UI rendering smoothness
        _recentPathPoints.value = if (updatedPoints.size > 2000) updatedPoints.takeLast(2000) else updatedPoints

        _metricsState.value = _metricsState.value.copy(
            currentFix = fix,
            trackingState = TrackingState.TRACKING,
            distanceTraveledMeters = engine.totalDistanceMeters,
            maxSpeedMps = engine.maxSpeedMps,
            elevationGainMeters = engine.elevationGainMeters,
            isMockActive = mockPublisher.isPublishing,
            latestGpsComparison = currentGpsComp,
            pointsCount = updatedPoints.size
        )
    }

    // --- LocationListener Implementations ---
    override fun onLocationChanged(location: Location) {
        // Step P43: Filter out any mock locations!
        val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
        if (isMock) {
            Log.d(tag, "Discarding self-mock location from GNSS receiver")
            return
        }

        val gpsFix = GpsFix(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            accuracyMeters = location.accuracy,
            speedMps = location.speed,
            bearingDeg = location.bearing,
            timestamp = location.time
        )
        latestRealGpsFix = gpsFix

        if (isWaitingForStartFix) {
            _metricsState.value = _metricsState.value.copy(
                gpsAnchor = gpsFix,
                trackingState = TrackingState.ACQUIRING_START_FIX
            )
            // Quality check (P30): automatic anchor if accuracy is good
            if (gpsFix.accuracyMeters <= gpsQualityThresholdMeters) {
                acceptGpsAnchor(gpsFix)
            }
        } else if (isReanchoring) {
            acceptGpsAnchor(gpsFix)
        } else if (isTestModeActive) {
            // Parallel test mode recording (P60, P61)
            rawDataLogger.logGpsComparison(
                gpsFix.latitude,
                gpsFix.longitude,
                gpsFix.altitude,
                gpsFix.accuracyMeters,
                gpsFix.timestamp
            )
            _metricsState.value = _metricsState.value.copy(latestGpsComparison = gpsFix)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    // --- Notifications & Lifecycle ---
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val state = _metricsState.value
        val title = when (state.trackingState) {
            TrackingState.ACQUIRING_START_FIX -> getString(R.string.status_acquiring_fix)
            TrackingState.TRACKING -> getString(R.string.status_tracking)
            TrackingState.PAUSED -> getString(R.string.status_paused)
            else -> getString(R.string.app_name)
        }
        val text = if (state.trackingState == TrackingState.TRACKING) {
            getString(
                R.string.notif_running_text,
                getString(activeMode.titleResId),
                engine.currentErrorRadiusMeters
            )
        } else {
            getString(R.string.gps_status_searching)
        }

        val notif = buildNotification(title, text)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notif)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
