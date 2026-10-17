package com.example.model

/**
 * Transport modes supported by the inertial path tracker.
 * Each mode has distinct sensor integration characteristics,
 * zero-velocity update (ZUPT) rules, and noise damping parameters.
 */
enum class TransportMode(
    val titleResId: Int,
    val isFixedMount: Boolean,
    val defaultStepLengthMeters: Float = 0.72f,
    val maxExpectedSpeedMps: Float = 50f,
    val velocityDamping: Float = 0.985f
) {
    WALKING(
        titleResId = com.example.R.string.mode_walking,
        isFixedMount = false,
        defaultStepLengthMeters = 0.72f,
        maxExpectedSpeedMps = 3.5f,
        velocityDamping = 0.92f
    ),
    CAR(
        titleResId = com.example.R.string.mode_car,
        isFixedMount = true,
        maxExpectedSpeedMps = 65f, // ~230 km/h
        velocityDamping = 0.992f
    ),
    BICYCLE_FIXED(
        titleResId = com.example.R.string.mode_bicycle_fixed,
        isFixedMount = true,
        maxExpectedSpeedMps = 20f, // ~72 km/h
        velocityDamping = 0.988f
    ),
    BICYCLE_LOOSE(
        titleResId = com.example.R.string.mode_bicycle_loose,
        isFixedMount = false,
        maxExpectedSpeedMps = 20f,
        velocityDamping = 0.975f
    ),
    TRANSIT_FIXED(
        titleResId = com.example.R.string.mode_transit_fixed,
        isFixedMount = true,
        maxExpectedSpeedMps = 85f, // ~300 km/h high-speed rail
        velocityDamping = 0.995f
    ),
    TRANSIT_LOOSE(
        titleResId = com.example.R.string.mode_transit_loose,
        isFixedMount = false,
        maxExpectedSpeedMps = 85f,
        velocityDamping = 0.985f
    );

    val isTransit: Boolean
        get() = this == TRANSIT_FIXED || this == TRANSIT_LOOSE

    val isBicycle: Boolean
        get() = this == BICYCLE_FIXED || this == BICYCLE_LOOSE
}

enum class PowerProfile(val samplingDelayUs: Int, val mockRateHz: Int) {
    MAX_ACCURACY(
        samplingDelayUs = 10000, // ~100Hz (SENSOR_DELAY_FASTEST / GAME)
        mockRateHz = 5
    ),
    POWER_SAVING(
        samplingDelayUs = 30000, // ~33Hz (SENSOR_DELAY_UI)
        mockRateHz = 1
    )
}

enum class TrackingState {
    IDLE,
    ACQUIRING_START_FIX,
    TRACKING,
    PAUSED,
    STOPPED
}

enum class AltitudeSource {
    BAROMETER,
    ACCELEROMETER,
    GPS_ANCHOR
}

data class InertialFix(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speedMps: Float,
    val courseDeg: Float,
    val errorRadiusMeters: Float,
    val timestamp: Long,
    val isZupt: Boolean = false,
    val altitudeSource: AltitudeSource = AltitudeSource.BAROMETER,
    val stepCount: Int = 0
)

data class GpsFix(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float,
    val bearingDeg: Float,
    val timestamp: Long,
    val satellites: Int = 0
)

data class TrackingMetrics(
    val currentFix: InertialFix? = null,
    val gpsAnchor: GpsFix? = null,
    val distanceTraveledMeters: Float = 0f,
    val durationSeconds: Long = 0L,
    val maxSpeedMps: Float = 0f,
    val elevationGainMeters: Float = 0f,
    val trackingState: TrackingState = TrackingState.IDLE,
    val activeMode: TransportMode = TransportMode.WALKING,
    val isMockActive: Boolean = false,
    val isTestModeActive: Boolean = false,
    val latestGpsComparison: GpsFix? = null,
    val pointsCount: Int = 0
)
