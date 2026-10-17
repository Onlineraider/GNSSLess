package com.example.data

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException

/**
 * Logs high-rate raw motion and environmental sensor data in documented JSON-Lines (.jsonl) format
 * sufficient to recompute the exact path offline (P52).
 */
class RawDataLogger(private val context: Context) {
    private var writer: BufferedWriter? = null
    private var currentFile: File? = null
    private val tag = "RawDataLogger"

    /**
     * Starts logging for a specific route session.
     * Returns the absolute path of the created raw log file.
     */
    fun startLogging(routeId: Long): String? {
        stopLogging()
        return try {
            val dir = File(context.filesDir, "raw_recordings").apply { mkdirs() }
            val file = File(dir, "route_${routeId}_raw.jsonl")
            currentFile = file
            writer = BufferedWriter(FileWriter(file, true), 32768)

            // Header metadata line
            writeLine("{\"version\":1,\"format\":\"inertial_raw_jsonl\",\"routeId\":$routeId,\"created\":${System.currentTimeMillis()}}")
            file.absolutePath
        } catch (e: IOException) {
            Log.e(tag, "Failed to initialize raw data log file", e)
            null
        }
    }

    fun logAnchor(lat: Double, lon: Double, alt: Double, accuracy: Float) {
        writeLine("{\"t\":${System.currentTimeMillis()},\"type\":\"GPS_ANCHOR\",\"lat\":$lat,\"lon\":$lon,\"alt\":$alt,\"acc\":$accuracy}")
    }

    fun logLinearAccel(x: Float, y: Float, z: Float, timestampNanos: Long) {
        writeLine("{\"t\":$timestampNanos,\"type\":\"LIN_ACC\",\"x\":$x,\"y\":$y,\"z\":$z}")
    }

    fun logGyro(x: Float, y: Float, z: Float, timestampNanos: Long) {
        writeLine("{\"t\":$timestampNanos,\"type\":\"GYRO\",\"x\":$x,\"y\":$y,\"z\":$z}")
    }

    fun logRotationVector(values: FloatArray, timestampNanos: Long) {
        val x = values.getOrNull(0) ?: 0f
        val y = values.getOrNull(1) ?: 0f
        val z = values.getOrNull(2) ?: 0f
        val w = values.getOrNull(3) ?: 1f
        writeLine("{\"t\":$timestampNanos,\"type\":\"ROT_VEC\",\"x\":$x,\"y\":$y,\"z\":$z,\"w\":$w}")
    }

    fun logPressure(pressureHpa: Float, timestampNanos: Long) {
        writeLine("{\"t\":$timestampNanos,\"type\":\"BARO\",\"p\":$pressureHpa}")
    }

    fun logStep(timestampNanos: Long) {
        writeLine("{\"t\":$timestampNanos,\"type\":\"STEP\"}")
    }

    fun logGpsComparison(lat: Double, lon: Double, alt: Double, accuracy: Float, timestamp: Long) {
        writeLine("{\"t\":$timestamp,\"type\":\"GPS_TEST_GROUND_TRUTH\",\"lat\":$lat,\"lon\":$lon,\"alt\":$alt,\"acc\":$accuracy}")
    }

    private fun writeLine(json: String) {
        try {
            writer?.write(json)
            writer?.newLine()
        } catch (e: Exception) {
            // non-fatal write error
        }
    }

    fun stopLogging(): String? {
        try {
            writer?.flush()
            writer?.close()
        } catch (e: Exception) {
            Log.e(tag, "Error closing raw data logger", e)
        }
        writer = null
        val path = currentFile?.absolutePath
        currentFile = null
        return path
    }
}
