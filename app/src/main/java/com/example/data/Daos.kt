package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Insert
    suspend fun insertRoute(route: RouteEntity): Long

    @Update
    suspend fun updateRoute(route: RouteEntity)

    @Delete
    suspend fun deleteRoute(route: RouteEntity)

    @Query("DELETE FROM routes WHERE id = :routeId")
    suspend fun deleteRouteById(routeId: Long)

    @Query("DELETE FROM path_points WHERE routeId = :routeId")
    suspend fun deletePointsForRoute(routeId: Long)

    @Query("SELECT * FROM routes ORDER BY startTime DESC")
    fun getAllRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :routeId")
    suspend fun getRouteById(routeId: Long): RouteEntity?

    @Insert
    suspend fun insertPoints(points: List<PathPointEntity>)

    @Query("SELECT * FROM path_points WHERE routeId = :routeId ORDER BY timestamp ASC")
    suspend fun getPointsForRoute(routeId: Long): List<PathPointEntity>

    @Query("SELECT * FROM path_points WHERE routeId = :routeId ORDER BY timestamp ASC")
    fun getPointsForRouteFlow(routeId: Long): Flow<List<PathPointEntity>>
}

@Dao
interface CalibrationDao {
    @Query("SELECT * FROM calibrations WHERE mode = :mode")
    suspend fun getCalibration(mode: String): CalibrationEntity?

    @Query("SELECT * FROM calibrations")
    fun getAllCalibrations(): Flow<List<CalibrationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(calibration: CalibrationEntity)
}
