package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val mode: String,
    val isPhoneFixed: Boolean,
    val powerProfile: String,
    val isTestMode: Boolean,
    val durationSeconds: Long,
    val totalDistanceMeters: Float,
    val avgSpeedMps: Float,
    val maxSpeedMps: Float,
    val elevationGainMeters: Float,
    val finalErrorRadiusMeters: Float,
    val pointsCount: Int,
    val rawLogFilePath: String? = null
)

@Entity(
    tableName = "path_points",
    indices = [Index(value = ["routeId"])]
)
data class PathPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routeId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speedMps: Float,
    val courseDeg: Float,
    val errorRadiusMeters: Float,
    val isZupt: Boolean,
    val altitudeSource: String,
    // Parallel test mode GPS comparison fields (P60, P61)
    val isGpsComparison: Boolean = false,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val gpsAltitude: Double? = null,
    val gpsAccuracy: Float? = null,
    val errorDistanceMeters: Float? = null
)

@Entity(tableName = "calibrations")
data class CalibrationEntity(
    @PrimaryKey
    val mode: String,
    val gyroBiasX: Float,
    val gyroBiasY: Float,
    val gyroBiasZ: Float,
    val accelBiasX: Float,
    val accelBiasY: Float,
    val accelBiasZ: Float,
    val stepLengthMultiplier: Float,
    val speedDampingFactor: Float,
    val calibratedAt: Long
)
