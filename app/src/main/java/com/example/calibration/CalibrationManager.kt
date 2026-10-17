package com.example.calibration

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.data.AppDatabase
import com.example.data.CalibrationEntity
import com.example.model.TransportMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

data class CalibrationProgress(
    val isRunning: Boolean = false,
    val secondsRemaining: Int = 10,
    val isDeviceStill: Boolean = true,
    val gyroSamplesCount: Int = 0,
    val accelSamplesCount: Int = 0,
    val completedSuccessfully: Boolean = false,
    val resultEntity: CalibrationEntity? = null
)

/**
 * Handles sensor calibration procedure (P20) to measure zero-rate gyro drift,
 * accelerometer bias, and mode-specific parameters.
 */
class CalibrationManager(private val context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val database = AppDatabase.getInstance(context)

    private val _progressFlow = MutableStateFlow(CalibrationProgress())
    val progressFlow: StateFlow<CalibrationProgress> = _progressFlow.asStateFlow()

    private var activeMode = TransportMode.WALKING
    private var gyroSumX = 0.0
    private var gyroSumY = 0.0
    private var gyroSumZ = 0.0
    private var gyroCount = 0

    private var accelSumX = 0.0
    private var accelSumY = 0.0
    private var accelSumZ = 0.0
    private var accelCount = 0

    private var lastAccelMag = 9.8f

    fun startCalibration(mode: TransportMode) {
        activeMode = mode
        gyroSumX = 0.0
        gyroSumY = 0.0
        gyroSumZ = 0.0
        gyroCount = 0

        accelSumX = 0.0
        accelSumY = 0.0
        accelSumZ = 0.0
        accelCount = 0

        _progressFlow.value = CalibrationProgress(
            isRunning = true,
            secondsRemaining = 10,
            isDeviceStill = true
        )

        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    fun onSecondTick(secondsRemaining: Int) {
        if (!_progressFlow.value.isRunning) return

        if (secondsRemaining <= 0) {
            completeCalibration()
        } else {
            _progressFlow.value = _progressFlow.value.copy(
                secondsRemaining = secondsRemaining,
                gyroSamplesCount = gyroCount,
                accelSamplesCount = accelCount
            )
        }
    }

    private fun completeCalibration() {
        sensorManager.unregisterListener(this)

        val biasGx = if (gyroCount > 0) (gyroSumX / gyroCount).toFloat() else 0f
        val biasGy = if (gyroCount > 0) (gyroSumY / gyroCount).toFloat() else 0f
        val biasGz = if (gyroCount > 0) (gyroSumZ / gyroCount).toFloat() else 0f

        val biasAx = if (accelCount > 0) (accelSumX / accelCount).toFloat() else 0f
        val biasAy = if (accelCount > 0) (accelSumY / accelCount).toFloat() else 0f
        // Vertical accel bias relative to standard gravity
        val rawAzMean = if (accelCount > 0) (accelSumZ / accelCount).toFloat() else 9.8f
        val biasAz = rawAzMean - 9.80665f

        val entity = CalibrationEntity(
            mode = activeMode.name,
            gyroBiasX = biasGx,
            gyroBiasY = biasGy,
            gyroBiasZ = biasGz,
            accelBiasX = biasAx,
            accelBiasY = biasAy,
            accelBiasZ = biasAz,
            stepLengthMultiplier = 1.0f,
            speedDampingFactor = activeMode.velocityDamping,
            calibratedAt = System.currentTimeMillis()
        )

        _progressFlow.value = _progressFlow.value.copy(
            isRunning = false,
            secondsRemaining = 0,
            completedSuccessfully = true,
            resultEntity = entity
        )
    }

    suspend fun saveCalibration(entity: CalibrationEntity) {
        database.calibrationDao().insertOrUpdate(entity)
    }

    fun cancelCalibration() {
        sensorManager.unregisterListener(this)
        _progressFlow.value = CalibrationProgress(isRunning = false)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                gyroSumX += event.values[0]
                gyroSumY += event.values[1]
                gyroSumZ += event.values[2]
                gyroCount++
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                val mag = sqrt(ax * ax + ay * ay + az * az)

                // Detect if device moved during static calibration
                val delta = kotlin.math.abs(mag - lastAccelMag)
                lastAccelMag = mag
                val isStill = delta < 0.25f

                accelSumX += ax
                accelSumY += ay
                accelSumZ += az
                accelCount++

                _progressFlow.value = _progressFlow.value.copy(isDeviceStill = isStill)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
