package com.example.engine

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geodetic calculations for inertial dead-reckoning on the WGS84 ellipsoid / spherical Earth model.
 */
object GeodesyUtils {
    const val EARTH_RADIUS_METERS = 6371000.0 // Mean radius

    /**
     * Great-circle distance between two coordinates in meters.
     */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = sin(deltaPhi / 2.0) * sin(deltaPhi / 2.0) +
                cos(phi1) * cos(phi2) * sin(deltaLambda / 2.0) * sin(deltaLambda / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Initial bearing from point 1 to point 2 in degrees (0..360).
     */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val initialBearing = Math.toDegrees(atan2(y, x))
        return ((initialBearing + 360.0) % 360.0).toFloat()
    }

    /**
     * Calculates the destination coordinate given a starting point, distance in meters, and bearing in degrees.
     */
    fun destinationPoint(
        startLat: Double,
        startLon: Double,
        distanceMeters: Double,
        bearingDeg: Double
    ): Pair<Double, Double> {
        val delta = distanceMeters / EARTH_RADIUS_METERS
        val theta = Math.toRadians(bearingDeg)

        val phi1 = Math.toRadians(startLat)
        val lambda1 = Math.toRadians(startLon)

        val phi2 = kotlin.math.asin(
            sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta)
        )
        val lambda2 = lambda1 + atan2(
            sin(theta) * sin(delta) * cos(phi1),
            cos(delta) - sin(phi1) * sin(phi2)
        )

        return Pair(Math.toDegrees(phi2), Math.toDegrees(lambda2))
    }

    /**
     * Translates local ENU (East, North) displacement in meters to latitude and longitude delta from an anchor.
     */
    fun enuToLatLon(
        anchorLat: Double,
        anchorLon: Double,
        eastMeters: Double,
        northMeters: Double
    ): Pair<Double, Double> {
        val latRad = Math.toRadians(anchorLat)
        val metersPerLat = 111132.954 - 559.822 * cos(2 * latRad) + 1.175 * cos(4 * latRad)
        val metersPerLon = 111412.84 * cos(latRad) - 93.5 * cos(3 * latRad)

        val deltaLat = northMeters / metersPerLat
        val deltaLon = if (metersPerLon > 0.0001) eastMeters / metersPerLon else 0.0

        return Pair(anchorLat + deltaLat, anchorLon + deltaLon)
    }

    /**
     * Converts coordinate to local ENU (East, North) in meters relative to reference origin.
     */
    fun latLonToEnu(
        refLat: Double,
        refLon: Double,
        targetLat: Double,
        targetLon: Double
    ): Pair<Double, Double> {
        val latRad = Math.toRadians(refLat)
        val metersPerLat = 111132.954 - 559.822 * cos(2 * latRad) + 1.175 * cos(4 * latRad)
        val metersPerLon = 111412.84 * cos(latRad) - 93.5 * cos(3 * latRad)

        val northMeters = (targetLat - refLat) * metersPerLat
        val eastMeters = (targetLon - refLon) * metersPerLon

        return Pair(eastMeters, northMeters)
    }
}
