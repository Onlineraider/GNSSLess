package com.example.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Manages 100% offline map tiles stored locally in standard Slippy Map format ({z}/{x}/{y}.png).
 * Allows the user to import an offline tile archive (ZIP file) without network calls (P54).
 */
class OfflineTileManager(private val context: Context) {
    private val tilesDir = File(context.filesDir, "map_tiles").apply { mkdirs() }

    // In-memory cache for fast canvas rendering
    private val memoryCache = object : LruCache<String, Bitmap>(64) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    fun hasOfflineTiles(): Boolean {
        return (tilesDir.listFiles()?.size ?: 0) > 0
    }

    fun getInstalledTilesCount(): Int {
        var count = 0
        tilesDir.walkTopDown().forEach {
            if (it.isFile && (it.name.endsWith(".png") || it.name.endsWith(".jpg"))) count++
        }
        return count
    }

    /**
     * Retrieves an offline tile bitmap if present.
     */
    fun getTileBitmap(z: Int, x: Int, y: Int): Bitmap? {
        val key = "$z/$x/$y"
        memoryCache.get(key)?.let { return it }

        val tileFile = File(tilesDir, "$z/$x/$y.png")
        if (tileFile.exists()) {
            val bmp = BitmapFactory.decodeFile(tileFile.absolutePath)
            if (bmp != null) {
                memoryCache.put(key, bmp)
                return bmp
            }
        }
        return null
    }

    /**
     * Unpacks a ZIP file containing offline OSM tiles into {z}/{x}/{y}.png.
     */
    suspend fun importTilesFromZip(zipUri: Uri): Int = withContext(Dispatchers.IO) {
        var importedCount = 0
        try {
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipStream ->
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && (entry.name.endsWith(".png") || entry.name.endsWith(".jpg"))) {
                            // Extract relative path like 14/8520/5621.png
                            val cleanPath = entry.name.substringAfterLast("map_tiles/").trimStart('/')
                            val destFile = File(tilesDir, cleanPath)
                            destFile.parentFile?.mkdirs()
                            FileOutputStream(destFile).use { out ->
                                zipStream.copyTo(out)
                            }
                            importedCount++
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        importedCount
    }

    fun clearAllTiles(): Boolean {
        memoryCache.evictAll()
        return tilesDir.deleteRecursively().also { tilesDir.mkdirs() }
    }
}
