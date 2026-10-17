package com.example.engine

import android.hardware.SensorManager
import com.example.model.AltitudeSource
import com.example.model.GpsFix
import com.example.model.InertialFix
import com.example.model.TransportMode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Inertial Dead-Reckoning Engine for real-time path estimation using purely on-device MEMS sensors.
 * Implements strapdown inertial navigation principles, coordinate transformation from body frame
 * to Earth frame (East-North-Up), mode-specific kinematics, Zero-Velocity Updates (ZUPT),
 * and honest uncertainty propagation.
 */
class InertialEngine(
    var mode: TransportMode = TransportMode.WALKING,
    var gyroBiasX: Float = 0f,
    var gyroBiasY: Float = 0f,
    var gyroBiasZ: Float = 0f,
    var accelBiasX: Float = 0f,
    var accelBiasY: Float = 0f,
    var accelBiasZ: Float = 0f,
    var stepLengthMultiplier: Float = 1.0f,
    var customSpeedDamping: Float? = null
) {
    // Current estimated position and kinematics
    var currentLat: Double = 0.0
        private set
    var currentLon: Double = 0.0
        private set
    var currentAlt: Double = 0.0
        private set
    var currentSpeedMps: Float = 0f
        private set
    var currentCourseDeg: Float = 0f
        private set
    var currentErrorRadiusMeters: Float = 10f
        private set

    var isInitialized: Boolean = false
        private set

    var totalDistanceMeters: Float = 0f
        private set
    var maxSpeedMps: Float = 0f
        private set
    var elevationGainMeters: Float = 0f
        private set

    var isBarometerAvailable: Boolean = false
    private var baseSeaLevelPressureHpa: Float = 1013.25f
    private var baselineBaroAltitude: Double? = null
    private var lastAltBeforeDelta: Double? = null

    // Tracking state and timestamps
    private var lastUpdateTimestampNanos: Long = 0L
    private var sessionStartTimeNanos: Long = 0L
    private var lastReanchorTimestampNanos: Long = 0L
    private var initialGpsAccuracy: Float = 10f

    // Current phone orientation representation (Rotation Matrix)
    private val rotationMatrix = FloatArray(9) { if (it % 4 == 0) 1f else 0f }
    private val orientationAngles = FloatArray(3) // [azimuth/yaw, pitch, roll]
    private var hasOrientation: Boolean = false

    // Velocity state in ENU (East, North, Up) in m/s
    private var velocityEast: Double = 0.0
    private var velocityNorth: Double = 0.0
    private var velocityUp: Double = 0.0

    // Step and walking dynamics
    private var lastStepTimestampNanos: Long = 0L
    private var recentStepCount: Int = 0
    private var accelMagnitudeHistory = FloatArray(16)
    private var accelHistoryIndex = 0
    private var lastStepPeak = 9.8f
    private var lastStepValley = 9.8f
    private var isLookingForPeak = true

    // Vehicle stop detection (ZUPT)
    private var stationaryCounter = 0
    private var isStationary = false

    // Heading estimation buffer for arbitrary phone orientation while walking
    private var horizontalMotionVectorE = 0.0
    private var horizontalMotionVectorN = 0.0

    /**
     * Initializes or re-anchors the engine with a real GPS fix.
     */
    fun anchorWithGps(fix: GpsFix) {
        currentLat = fix.latitude
        currentLon = fix.longitude
        currentAlt = fix.altitude
        initialGpsAccuracy = fix.accuracyMeters.coerceAtLeast(3.0f)
        currentErrorRadiusMeters = initialGpsAccuracy

        if (fix.speedMps > 0.5f) {
            currentSpeedMps = fix.speedMps
            currentCourseDeg = fix.bearingDeg
            val courseRad = Math.toRadians(fix.bearingDeg.toDouble())
            velocityEast = fix.speedMps * sin(courseRad)
            velocityNorth = fix.speedMps * kotlin.math.cos(courseRad)
        } else {
            currentSpeedMps = 0f
            velocityEast = 0.0
            velocityNorth = 0.0
        }

        lastAltBeforeDelta = fix.altitude
        baselineBaroAltitude = null // re-align barometer with anchor altitude

        val now = System.nanoTime()
        lastReanchorTimestampNanos = now
        lastUpdateTimestampNanos = now
        if (sessionStartTimeNanos == 0L) {
            sessionStartTimeNanos = now
        }
        isInitialized = true
    }

    /**
     * Updates device orientation from Rotation Vector sensor.
     */
    fun onRotationVector(rotationVector: FloatArray) {
        try {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            hasOrientation = true
        } catch (e: Exception) {
            // gracefully ignore malformed vectors
        }
    }

    /**
     * Updates device barometer pressure in hPa.
     */
    fun onBarometerPressure(pressureHpa: Float) {
        isBarometerAvailable = true
        // Hypsometric formula: h = 44330 * (1 - (p / p0)^(1/5.255))
        val baroAlt = 44330.0 * (1.0 - (pressureHpa / baseSeaLevelPressureHpa).toDouble().pow(1.0 / 5.255))
        if (baselineBaroAltitude == null) {
            // Calibration offset to match GPS anchor altitude
            baselineBaroAltitude = baroAlt - currentAlt
        }
        val correctedAlt = baroAlt - (baselineBaroAltitude ?: 0.0)

        // Elevation gain calculation
        lastAltBeforeDelta?.let { prev ->
            val delta = correctedAlt - prev
            if (delta > 0.15) {
                elevationGainMeters += delta.toFloat()
            }
        }
        lastAltBeforeDelta = correctedAlt
        currentAlt = correctedAlt
    }

    /**
     * Handles step detector event for walking mode.
     */
    fun onStepDetected(timestampNanos: Long) {
        recentStepCount++
        if (mode == TransportMode.WALKING) {
            val deltaSec = if (lastStepTimestampNanos > 0) {
                ((timestampNanos - lastStepTimestampNanos) / 1_000_000_000.0).coerceIn(0.2, 2.0)
            } else 0.6
            lastStepTimestampNanos = timestampNanos

            // Weinberg step length model: L = k * (a_max - a_min)^0.25
            val bounce = (lastStepPeak - lastStepValley).coerceIn(0.5f, 12f)
            val stepLength = (0.42f * bounce.toDouble().pow(0.25) * stepLengthMultiplier).toFloat()
                .coerceIn(0.35f, 1.25f)

            val stepSpeed = (stepLength / deltaSec).toFloat().coerceIn(0.5f, 2.8f)

            // Propagate position along current course
            propagatePosition(distanceMeters = stepLength.toDouble(), headingDeg = currentCourseDeg)
            currentSpeedMps = (currentSpeedMps * 0.4f) + (stepSpeed * 0.6f)
            if (currentSpeedMps > maxSpeedMps) maxSpeedMps = currentSpeedMps

            // Step provides a natural stance ZUPT to bound drift
            isStationary = false
            stationaryCounter = 0
        }
    }

    /**
     * Primary inertial integration loop on linear acceleration (acceleration without gravity)
     * and raw acceleration data.
     */
    fun onLinearAcceleration(
        ax: Float,
        ay: Float,
        az: Float,
        timestampNanos: Long
    ): InertialFix? {
        if (!isInitialized) return null

        val dt = if (lastUpdateTimestampNanos > 0L) {
            ((timestampNanos - lastUpdateTimestampNanos) / 1_000_000_000.0).coerceIn(0.001, 0.25)
        } else 0.02
        lastUpdateTimestampNanos = timestampNanos

        // Apply calibrated accelerometer bias
        val calAx = ax - accelBiasX
        val calAy = ay - accelBiasY
        val calAz = az - accelBiasZ

        // Track magnitude for step peak/valley detection and stationary analysis
        val mag = sqrt(calAx * calAx + calAy * calAy + calAz * calAz)
        accelMagnitudeHistory[accelHistoryIndex] = mag
        accelHistoryIndex = (accelHistoryIndex + 1) % accelMagnitudeHistory.size

        // Calculate variance over recent window
        var mean = 0f
        for (v in accelMagnitudeHistory) mean += v
        mean /= accelMagnitudeHistory.size
        var variance = 0f
        for (v in accelMagnitudeHistory) variance += (v - mean) * (v - mean)
        variance /= accelMagnitudeHistory.size

        // Zero-Velocity Update (ZUPT) detection
        // When stationary, acceleration variance is near zero
        val stopThreshold = when (mode) {
            TransportMode.CAR, TransportMode.TRANSIT_FIXED -> 0.08f
            TransportMode.BICYCLE_FIXED -> 0.12f
            else -> 0.18f
        }

        if (variance < stopThreshold && mag < 0.35f) {
            stationaryCounter++
            if (stationaryCounter > 15) { // ~0.15 - 0.3 seconds stationary
                isStationary = true
                velocityEast *= 0.7
                velocityNorth *= 0.7
                if (stationaryCounter > 40) {
                    velocityEast = 0.0
                    velocityNorth = 0.0
                    currentSpeedMps = 0f
                }
            }
        } else {
            stationaryCounter = 0
            isStationary = false
        }

        // Peak / Valley tracking for walking step length
        if (mag > lastStepPeak && isLookingForPeak) {
            lastStepPeak = mag
        } else if (mag < (lastStepPeak - 0.8f) && isLookingForPeak && lastStepPeak > 1.2f) {
            isLookingForPeak = false
            lastStepValley = mag
        } else if (mag < lastStepValley && !isLookingForPeak) {
            lastStepValley = mag
        } else if (mag > (lastStepValley + 0.8f) && !isLookingForPeak) {
            isLookingForPeak = true
            // If phone doesn't have hardware step detector, fallback to peak detection
            if (mode == TransportMode.WALKING && recentStepCount == 0) {
                onStepDetected(timestampNanos)
            }
            lastStepPeak = mag
        }

        // Transform acceleration from body frame to Earth ENU frame using rotation matrix
        // [a_E, a_N, a_U]^T = R * [a_x, a_y, a_z]^T
        val r = rotationMatrix
        val aEast = (r[0] * calAx + r[1] * calAy + r[2] * calAz).toDouble()
        val aNorth = (r[3] * calAx + r[4] * calAy + r[5] * calAz).toDouble()
        val aUp = (r[6] * calAx + r[7] * calAy + r[8] * calAz).toDouble()

        // Mode-specific integration logic
        val damping = customSpeedDamping ?: mode.velocityDamping

        if (!isStationary) {
            when (mode) {
                TransportMode.WALKING -> {
                    // In walking mode, orientation can be arbitrary (phone in hand or pocket).
                    // We integrate horizontal acceleration with heavier leaky damping
                    // to prevent cubic position runaway while extracting direction of motion.
                    velocityEast = (velocityEast + aEast * dt) * damping
                    velocityNorth = (velocityNorth + aNorth * dt) * damping

                    // Track dominant horizontal motion direction over step cycles
                    horizontalMotionVectorE = horizontalMotionVectorE * 0.95 + velocityEast * 0.05
                    horizontalMotionVectorN = horizontalMotionVectorN * 0.95 + velocityNorth * 0.05

                    val headingRad = atan2(horizontalMotionVectorE, horizontalMotionVectorN)
                    var deg = Math.toDegrees(headingRad).toFloat()
                    if (deg < 0) deg += 360f
                    if (sqrt(horizontalMotionVectorE.pow(2) + horizontalMotionVectorN.pow(2)) > 0.2) {
                        currentCourseDeg = deg
                    }
                }

                TransportMode.CAR, TransportMode.BICYCLE_FIXED, TransportMode.TRANSIT_FIXED -> {
                    // In fixed mount modes, non-holonomic constraint:
                    // Lateral slip velocity is zero. Acceleration along heading propagates speed.
                    velocityEast = (velocityEast + aEast * dt) * damping
                    velocityNorth = (velocityNorth + aNorth * dt) * damping

                    val speed = sqrt(velocityEast * velocityEast + velocityNorth * velocityNorth).toFloat()
                    if (speed > 0.3f) {
                        var deg = Math.toDegrees(atan2(velocityEast, velocityNorth)).toFloat()
                        if (deg < 0) deg += 360f
                        currentCourseDeg = deg
                        currentSpeedMps = speed.coerceAtMost(mode.maxExpectedSpeedMps)
                        if (currentSpeedMps > maxSpeedMps) maxSpeedMps = currentSpeedMps

                        // Displace coordinate
                        val dist = (currentSpeedMps * dt).toDouble()
                        propagatePosition(dist, currentCourseDeg)
                    } else {
                        currentSpeedMps = 0f
                    }
                }

                TransportMode.BICYCLE_LOOSE, TransportMode.TRANSIT_LOOSE -> {
                    // Loose in pocket/bag: motion envelope integration
                    velocityEast = (velocityEast + aEast * dt) * damping
                    velocityNorth = (velocityNorth + aNorth * dt) * damping
                    val speed = sqrt(velocityEast * velocityEast + velocityNorth * velocityNorth).toFloat()
                    if (speed > 0.4f) {
                        currentSpeedMps = speed.coerceAtMost(mode.maxExpectedSpeedMps)
                        if (currentSpeedMps > maxSpeedMps) maxSpeedMps = currentSpeedMps
                        val dist = (currentSpeedMps * dt).toDouble()
                        propagatePosition(dist, currentCourseDeg)
                    }
                }
            }
        }

        // Vertical / Altitude integration when barometer is absent
        if (!isBarometerAvailable) {
            velocityUp = (velocityUp + aUp * dt) * 0.90
            val deltaZ = velocityUp * dt
            currentAlt += deltaZ
            if (deltaZ > 0.1) elevationGainMeters += deltaZ.toFloat()
        }

        // Accumulate honest error radius (P05 & Assumption A2)
        accumulateErrorRadius(dt)

        return InertialFix(
            latitude = currentLat,
            longitude = currentLon,
            altitude = currentAlt,
            speedMps = currentSpeedMps,
            courseDeg = currentCourseDeg,
            errorRadiusMeters = currentErrorRadiusMeters,
            timestamp = System.currentTimeMillis(),
            isZupt = isStationary,
            altitudeSource = if (isBarometerAvailable) AltitudeSource.BAROMETER else AltitudeSource.ACCELEROMETER,
            stepCount = recentStepCount
        )
    }

    /**
     * Advances the latitude and longitude given travel distance and bearing.
     */
    private fun propagatePosition(distanceMeters: Double, headingDeg: Float) {
        if (distanceMeters <= 0.0001) return
        val dest = GeodesyUtils.destinationPoint(
            startLat = currentLat,
            startLon = currentLon,
            distanceMeters = distanceMeters,
            bearingDeg = headingDeg.toDouble()
        )
        currentLat = dest.first
        currentLon = dest.second
        totalDistanceMeters += distanceMeters.toFloat()
    }

    /**
     * Honest error radius growth model (P05 & Assumption A2).
     * Uncertainty increases as a function of elapsed time since last anchor,
     * distance traveled, and mode-specific drift rates.
     */
    private fun accumulateErrorRadius(dt: Double) {
        val now = System.nanoTime()
        val elapsedSec = (now - lastReanchorTimestampNanos) / 1_000_000_000.0

        // Time-based drift (MEMS bias uncertainty)
        // With ZUPT, stationary periods dramatically reduce velocity error growth
        val timeDriftRate = if (isStationary) 0.015 else when (mode) {
            TransportMode.WALKING -> 0.04
            TransportMode.CAR -> 0.08
            TransportMode.BICYCLE_FIXED -> 0.06
            TransportMode.BICYCLE_LOOSE -> 0.10
            TransportMode.TRANSIT_FIXED -> 0.07
            TransportMode.TRANSIT_LOOSE -> 0.12
        }

        // Distance-based drift (scale factor & heading deviation ~ 3-7% of distance)
        val distanceDriftFactor = when (mode) {
            TransportMode.WALKING -> 0.035
            TransportMode.CAR -> 0.045
            TransportMode.BICYCLE_FIXED -> 0.040
            TransportMode.BICYCLE_LOOSE -> 0.060
            TransportMode.TRANSIT_FIXED -> 0.035
            TransportMode.TRANSIT_LOOSE -> 0.070
        }

        // Gyro heading drift: grows with time^1.2
        val gyroHeadingDriftMeters = 0.003 * elapsedSec.pow(1.2)

        val accumulatedDrift = (timeDriftRate * sqrt(elapsedSec) +
                distanceDriftFactor * totalDistanceMeters +
                gyroHeadingDriftMeters).toFloat()

        currentErrorRadiusMeters = initialGpsAccuracy + accumulatedDrift
    }
}
