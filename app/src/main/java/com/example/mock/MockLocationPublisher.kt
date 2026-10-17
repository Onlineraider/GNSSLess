package com.example.mock

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.model.InertialFix

/**
 * Publishes sensor-derived dead-reckoning positions to the Android Mock Location framework.
 * Feeds estimated latitude, longitude, speed, course, altitude, and honest error radius (as accuracy)
 * to all other apps on the device without requiring GNSS.
 *
 * Resolves Spec Issue P43: Avoids self-mock feedback during real GPS acquisition by cleanly disabling
 * the test provider during fix search and discarding any locations flagged with `isFromMockProvider`.
 */
class MockLocationPublisher(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val tag = "MockLocationPublisher"

    private val providers = listOf(LocationManager.GPS_PROVIDER)
    var isPublishing = false
        private set

    /**
     * Checks whether the user has granted mock location permission by selecting this app
     * under Developer Options -> "Select mock location app".
     */
    fun isMockLocationAppSet(): Boolean {
        return try {
            locationManager.addTestProvider(
                "temp_test_check",
                false, false, false, false, true, true, true,
                Criteria.POWER_LOW, Criteria.ACCURACY_FINE
            )
            locationManager.removeTestProvider("temp_test_check")
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Initializes test provider(s) and prepares for publishing.
     */
    fun start(): Boolean {
        if (isPublishing) return true

        return try {
            for (p in providers) {
                try {
                    locationManager.removeTestProvider(p)
                } catch (e: Exception) {
                    // ignore if wasn't set
                }
                locationManager.addTestProvider(
                    p,
                    false, // requiresNetwork
                    false, // requiresSatellite
                    false, // requiresCell
                    false, // hasMonetaryCost
                    true,  // supportsAltitude
                    true,  // supportsSpeed
                    true,  // supportsBearing
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                )
                locationManager.setTestProviderEnabled(p, true)
            }
            isPublishing = true
            Log.i(tag, "Mock location provider registered successfully")
            true
        } catch (e: SecurityException) {
            Log.w(tag, "SecurityException: App not selected as mock location app in Developer Options", e)
            isPublishing = false
            false
        } catch (e: Exception) {
            Log.e(tag, "Error registering mock location provider", e)
            isPublishing = false
            false
        }
    }

    /**
     * Publishes an inertial fix to the Android system mock location provider.
     */
    fun publishFix(fix: InertialFix) {
        if (!isPublishing) return

        for (provider in providers) {
            try {
                val loc = Location(provider).apply {
                    latitude = fix.latitude
                    longitude = fix.longitude
                    altitude = fix.altitude
                    speed = fix.speedMps
                    bearing = fix.courseDeg
                    accuracy = fix.errorRadiusMeters
                    time = fix.timestamp
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

                    // Optional extended accuracy fields in modern Android
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        verticalAccuracyMeters = if (fix.altitudeSource == com.example.model.AltitudeSource.BAROMETER) 2.5f else 15f
                        speedAccuracyMetersPerSecond = 0.5f
                        bearingAccuracyDegrees = 5.0f
                    }
                }
                locationManager.setTestProviderLocation(provider, loc)
            } catch (e: Exception) {
                Log.w(tag, "Failed to publish mock fix on $provider: ${e.message}")
            }
        }
    }

    /**
     * Pauses motion publishing (P44).
     * Publishes a stationary fix (speed 0, bearing frozen) with current error radius,
     * so third-party navigation apps see the user has come to a stop.
     */
    fun publishStationaryPause(lastFix: InertialFix) {
        if (!isPublishing) return
        val stationaryFix = lastFix.copy(
            speedMps = 0f,
            timestamp = System.currentTimeMillis()
        )
        publishFix(stationaryFix)
    }

    /**
     * Temporarily suspends or completely removes test provider.
     * Essential during GPS start-fix and manual re-anchor to prevent self-mock feedback (P43).
     */
    fun stop() {
        if (!isPublishing) return
        for (p in providers) {
            try {
                locationManager.setTestProviderEnabled(p, false)
                locationManager.removeTestProvider(p)
            } catch (e: Exception) {
                Log.d(tag, "Teardown test provider $p: ${e.message}")
            }
        }
        isPublishing = false
        Log.i(tag, "Mock location provider stopped")
    }
}
