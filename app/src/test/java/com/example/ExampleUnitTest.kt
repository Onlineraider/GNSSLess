package com.example

import com.example.data.PathPointEntity
import com.example.data.RouteEntity
import com.example.engine.GeodesyUtils
import com.example.engine.InertialEngine
import com.example.export.RouteExporter
import com.example.model.GpsFix
import com.example.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testGeodesyDistanceAndBearing() {
        // Berlin Brandenburg Gate to Alexanderplatz (~3.2 km)
        val lat1 = 52.516275
        val lon1 = 13.377704
        val lat2 = 52.521918
        val lon2 = 13.413215

        val dist = GeodesyUtils.distanceMeters(lat1, lon1, lat2, lon2)
        assertTrue("Distance should be around 2500m", dist in 2300.0..2700.0)

        val bearing = GeodesyUtils.bearingDegrees(lat1, lon1, lat2, lon2)
        assertTrue("Bearing towards Alexanderplatz is roughly East-North-East (~75-85 deg)", bearing in 70f..90f)

        // Test destination point calculation
        val (destLat, destLon) = GeodesyUtils.destinationPoint(lat1, lon1, dist, bearing.toDouble())
        val recoveredDist = GeodesyUtils.distanceMeters(destLat, destLon, lat2, lon2)
        assertTrue("Recovered point should match destination within 1 meter", recoveredDist < 1.0)
    }

    @Test
    fun testInertialEngineAnchorAndErrorRadius() {
        val engine = InertialEngine(mode = TransportMode.WALKING)

        val anchorFix = GpsFix(
            latitude = 52.5200,
            longitude = 13.4050,
            altitude = 45.0,
            accuracyMeters = 8.5f,
            speedMps = 0.0f,
            bearingDeg = 0.0f,
            timestamp = System.currentTimeMillis()
        )

        engine.anchorWithGps(anchorFix)
        assertTrue(engine.isInitialized)
        assertEquals(52.5200, engine.currentLat, 0.00001)
        assertEquals(13.4050, engine.currentLon, 0.00001)
        assertEquals(8.5f, engine.currentErrorRadiusMeters, 0.01f)

        // Simulate steps
        val nowNanos = System.nanoTime()
        engine.onStepDetected(nowNanos)
        engine.onStepDetected(nowNanos + 600_000_000L)

        assertTrue("Distance traveled should increase after steps", engine.totalDistanceMeters > 0f)
        assertTrue("Error radius should grow honestly as steps accumulate", engine.currentErrorRadiusMeters >= 8.5f)
    }

    @Test
    fun testRouteExportGpxAndKml() {
        val route = RouteEntity(
            id = 1L,
            title = "Test Route",
            startTime = 1729170000000L,
            endTime = 1729170600000L,
            mode = "WALKING",
            isPhoneFixed = false,
            powerProfile = "MAX_ACCURACY",
            isTestMode = true,
            durationSeconds = 600L,
            totalDistanceMeters = 500f,
            avgSpeedMps = 1.2f,
            maxSpeedMps = 1.8f,
            elevationGainMeters = 5f,
            finalErrorRadiusMeters = 15f,
            pointsCount = 2
        )

        val points = listOf(
            PathPointEntity(
                id = 1L,
                routeId = 1L,
                timestamp = 1729170000000L,
                latitude = 52.5200,
                longitude = 13.4050,
                altitude = 45.0,
                speedMps = 1.2f,
                courseDeg = 90.0f,
                errorRadiusMeters = 8.0f,
                isZupt = false,
                altitudeSource = "BAROMETER"
            ),
            PathPointEntity(
                id = 2L,
                routeId = 1L,
                timestamp = 1729170010000L,
                latitude = 52.5201,
                longitude = 13.4052,
                altitude = 45.2,
                speedMps = 1.3f,
                courseDeg = 88.0f,
                errorRadiusMeters = 8.5f,
                isZupt = false,
                altitudeSource = "BAROMETER"
            )
        )

        val gpx = RouteExporter.toGpx(route, points)
        assertTrue(gpx.contains("<gpx version=\"1.1\""))
        assertTrue(gpx.contains("lat=\"52.52\""))
        assertTrue(gpx.contains("<name>Test Route</name>"))

        val kml = RouteExporter.toKml(route, points)
        assertTrue(kml.contains("<kml xmlns=\"http://www.opengis.net/kml/2.2\""))
        assertTrue(kml.contains("13.405,52.52,45.0"))
    }
}
