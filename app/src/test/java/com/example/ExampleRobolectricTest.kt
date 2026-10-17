package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CalibrationEntity
import com.example.data.RouteEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Inertial Tracker", appName)
    }

    @Test
    fun testRoomDatabaseInsertAndQuery() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)

        val route = RouteEntity(
            title = "Test Route",
            startTime = 1729170000000L,
            endTime = 1729170600000L,
            mode = "WALKING",
            isPhoneFixed = false,
            powerProfile = "MAX_ACCURACY",
            isTestMode = true,
            durationSeconds = 600L,
            totalDistanceMeters = 850.5f,
            avgSpeedMps = 1.41f,
            maxSpeedMps = 2.1f,
            elevationGainMeters = 12f,
            finalErrorRadiusMeters = 24.5f,
            pointsCount = 120
        )

        val id = db.routeDao().insertRoute(route)
        val fetched = db.routeDao().getRouteById(id)
        assertNotNull(fetched)
        assertEquals("Test Route", fetched?.title)
        assertEquals(850.5f, fetched?.totalDistanceMeters)

        // Test calibration storage
        val calib = CalibrationEntity(
            mode = "CAR",
            gyroBiasX = 0.001f,
            gyroBiasY = -0.002f,
            gyroBiasZ = 0.0005f,
            accelBiasX = 0.02f,
            accelBiasY = -0.01f,
            accelBiasZ = 0.05f,
            stepLengthMultiplier = 1.0f,
            speedDampingFactor = 0.99f,
            calibratedAt = System.currentTimeMillis()
        )
        db.calibrationDao().insertOrUpdate(calib)
        val fetchedCalib = db.calibrationDao().getCalibration("CAR")
        assertNotNull(fetchedCalib)
        assertEquals(0.001f, fetchedCalib!!.gyroBiasX, 0.0001f)
    }
}
