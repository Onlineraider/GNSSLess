package com.example.map

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.PathPointEntity
import com.example.engine.GeodesyUtils
import com.example.ui.theme.AeroAmberGps
import com.example.ui.theme.AeroCyanLight
import com.example.ui.theme.AeroCyanPrimary
import com.example.ui.theme.AeroEmeraldZupt
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * 100% Offline Path Canvas Renderer (P54).
 * Supports:
 * - Own Drawing: Vector route on aerospace grid with dynamic scale bar and North arrow.
 * - Offline Map Tiles: Slippy map tiles rendered underneath the route when imported.
 * - Test Mode: Parallel GPS ground truth track overlay in amber.
 * - Pan and pinch-to-zoom interactive gestures.
 */
@Composable
fun PathCanvasView(
    points: List<PathPointEntity>,
    modifier: Modifier = Modifier,
    isOfflineTilesMode: Boolean = false,
    tileManager: OfflineTileManager? = null,
    onRecenterRequested: Boolean = false,
    onRecenterHandled: () -> Unit = {}
) {
    var scale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    if (onRecenterRequested) {
        panOffsetX = 0f
        panOffsetY = 0f
        scale = 1.0f
        onRecenterHandled()
    }

    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val gridColor = if (isDark) Color(0x2294A3B8) else Color(0x22475569)
    val pathColor = if (isDark) AeroCyanLight else AeroCyanPrimary
    val gpsGroundTruthColor = AeroAmberGps

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.2f, 15.0f)
                    panOffsetX += pan.x
                    panOffsetY += pan.y
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            if (points.isEmpty()) {
                // Empty state grid
                drawEmptyGrid(canvasWidth, canvasHeight, gridColor)
                drawNorthArrow(canvasWidth, canvasHeight, isDark)
                return@Canvas
            }

            // Determine geographic bounds of the path
            val centerLat: Double
            val centerLon: Double
            val minLat: Double
            val maxLat: Double
            val minLon: Double
            val maxLon: Double

            val lastPt = points.last()
            if (points.size == 1) {
                centerLat = lastPt.latitude
                centerLon = lastPt.longitude
                minLat = centerLat - 0.001
                maxLat = centerLat + 0.001
                minLon = centerLon - 0.001
                maxLon = centerLon + 0.001
            } else {
                var lMin = points.first().latitude
                var lMax = points.first().latitude
                var oMin = points.first().longitude
                var oMax = points.first().longitude
                for (p in points) {
                    if (p.latitude < lMin) lMin = p.latitude
                    if (p.latitude > lMax) lMax = p.latitude
                    if (p.longitude < oMin) oMin = p.longitude
                    if (p.longitude > oMax) oMax = p.longitude
                }
                centerLat = (lMin + lMax) / 2.0
                centerLon = (oMin + oMax) / 2.0
                minLat = lMin
                maxLat = lMax
                minLon = oMin
                maxLon = oMax
            }

            // Calculate auto-fit pixels per meter
            val latSpanMeters = GeodesyUtils.distanceMeters(minLat, centerLon, maxLat, centerLon).coerceAtLeast(40.0)
            val lonSpanMeters = GeodesyUtils.distanceMeters(centerLat, minLon, centerLat, maxLon).coerceAtLeast(40.0)
            val fitPixelsPerMeter = min(
                (canvasWidth * 0.75f) / lonSpanMeters.toFloat(),
                (canvasHeight * 0.75f) / latSpanMeters.toFloat()
            ).coerceIn(0.05f, 50.0f)

            val effectivePpm = fitPixelsPerMeter * scale
            val screenCenterX = canvasWidth / 2f + panOffsetX
            val screenCenterY = canvasHeight / 2f + panOffsetY

            fun toScreen(lat: Double, lon: Double): Offset {
                val (eastMeters, northMeters) = GeodesyUtils.latLonToEnu(
                    refLat = centerLat,
                    refLon = centerLon,
                    targetLat = lat,
                    targetLon = lon
                )
                val x = screenCenterX + (eastMeters * effectivePpm).toFloat()
                val y = screenCenterY - (northMeters * effectivePpm).toFloat() // Y is inverted on screen
                return Offset(x, y)
            }

            // Draw offline map tiles if enabled
            if (isOfflineTilesMode && tileManager != null) {
                drawOfflineTiles(
                    tileManager = tileManager,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    effectivePpm = effectivePpm,
                    screenCenterX = screenCenterX,
                    screenCenterY = screenCenterY,
                    width = canvasWidth,
                    height = canvasHeight
                )
            } else {
                // Vector background coordinate grid
                drawEmptyGrid(canvasWidth, canvasHeight, gridColor)
            }

            // Draw parallel GPS ground truth polyline if test mode points exist
            val hasGpsPoints = points.any { it.isGpsComparison && it.gpsLatitude != null }
            if (hasGpsPoints) {
                val gpsPath = Path()
                var gpsStarted = false
                for (p in points) {
                    if (p.isGpsComparison && p.gpsLatitude != null && p.gpsLongitude != null) {
                        val pos = toScreen(p.gpsLatitude, p.gpsLongitude)
                        if (!gpsStarted) {
                            gpsPath.moveTo(pos.x, pos.y)
                            gpsStarted = true
                        } else {
                            gpsPath.lineTo(pos.x, pos.y)
                        }
                    }
                }
                drawPath(
                    path = gpsPath,
                    color = gpsGroundTruthColor.copy(alpha = 0.75f),
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // Draw main inertial trajectory polyline
            val inertialPath = Path()
            val startScreen = toScreen(points.first().latitude, points.first().longitude)
            inertialPath.moveTo(startScreen.x, startScreen.y)
            for (i in 1 until points.size) {
                val p = toScreen(points[i].latitude, points[i].longitude)
                inertialPath.lineTo(p.x, p.y)
            }
            drawPath(
                path = inertialPath,
                color = pathColor,
                style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Start marker (green dot)
            drawCircle(color = Color(0xFF10B981), radius = 6.5f, center = startScreen)
            drawCircle(color = Color.White, radius = 3.0f, center = startScreen)

            // Current position marker + honest error radius bubble (P05)
            val currentScreen = toScreen(lastPt.latitude, lastPt.longitude)
            val errorRadiusPixels = (lastPt.errorRadiusMeters * effectivePpm).coerceAtLeast(8f)

            // Translucent error radius uncertainty disk
            drawCircle(
                color = pathColor.copy(alpha = 0.16f),
                radius = errorRadiusPixels,
                center = currentScreen
            )
            drawCircle(
                color = pathColor.copy(alpha = 0.45f),
                radius = errorRadiusPixels,
                center = currentScreen,
                style = Stroke(width = 1.5f)
            )

            // Current position core pin with pulse ring
            drawCircle(color = pathColor, radius = 7.0f, center = currentScreen)
            drawCircle(color = Color.White, radius = 3.5f, center = currentScreen)

            // Draw Dynamic Scale Bar (P54)
            drawScaleBar(canvasWidth, canvasHeight, effectivePpm, isDark)

            // Draw North Arrow
            drawNorthArrow(canvasWidth, canvasHeight, isDark)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEmptyGrid(
    width: Float,
    height: Float,
    gridColor: Color
) {
    val step = 60f
    var x = 0f
    while (x <= width) {
        drawLine(color = gridColor, start = Offset(x, 0f), end = Offset(x, height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y <= height) {
        drawLine(color = gridColor, start = Offset(0f, y), end = Offset(width, y), strokeWidth = 1f)
        y += step
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNorthArrow(
    canvasWidth: Float,
    canvasHeight: Float,
    isDark: Boolean
) {
    val cx = canvasWidth - 40f
    val cy = 45f
    val textColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY

    // Compass circle
    drawCircle(color = if (isDark) Color(0x33000000) else Color(0x22000000), radius = 18f, center = Offset(cx, cy))

    // Arrow pointer pointing North (Up)
    val arrowPath = Path().apply {
        moveTo(cx, cy - 14f)
        lineTo(cx - 6f, cy + 8f)
        lineTo(cx, cy + 3f)
        close()
    }
    drawPath(arrowPath, color = Color(0xFFEF4444))

    val southPath = Path().apply {
        moveTo(cx, cy + 14f)
        lineTo(cx + 6f, cy - 8f)
        lineTo(cx, cy - 3f)
        close()
    }
    drawPath(southPath, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))

    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            color = textColor
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.nativeCanvas.drawText("N", cx, cy - 18f, paint)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScaleBar(
    canvasWidth: Float,
    canvasHeight: Float,
    pixelsPerMeter: Float,
    isDark: Boolean
) {
    val targetBarPixels = 160f
    val approxMeters = targetBarPixels / pixelsPerMeter

    // Snap to standard round distance increments (10m, 25m, 50m, 100m, 250m, 500m, 1km, 2.5km, 5km)
    val distanceMeters = when {
        approxMeters < 20 -> 10.0
        approxMeters < 40 -> 25.0
        approxMeters < 80 -> 50.0
        approxMeters < 150 -> 100.0
        approxMeters < 350 -> 250.0
        approxMeters < 750 -> 500.0
        approxMeters < 1500 -> 1000.0
        approxMeters < 3500 -> 2500.0
        else -> 5000.0
    }

    val barWidthPixels = (distanceMeters * pixelsPerMeter).toFloat()
    val startX = 24f
    val bottomY = canvasHeight - 24f
    val barColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
    val textColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK

    // Draw horizontal bar and end caps
    drawLine(color = barColor, start = Offset(startX, bottomY), end = Offset(startX + barWidthPixels, bottomY), strokeWidth = 3f)
    drawLine(color = barColor, start = Offset(startX, bottomY - 6f), end = Offset(startX, bottomY + 6f), strokeWidth = 3f)
    drawLine(color = barColor, start = Offset(startX + barWidthPixels, bottomY - 6f), end = Offset(startX + barWidthPixels, bottomY + 6f), strokeWidth = 3f)

    val label = if (distanceMeters >= 1000) "${(distanceMeters / 1000).roundToInt()} km" else "${distanceMeters.roundToInt()} m"
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            color = textColor
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }
        canvas.nativeCanvas.drawText(label, startX + 6f, bottomY - 10f, paint)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOfflineTiles(
    tileManager: OfflineTileManager,
    centerLat: Double,
    centerLon: Double,
    effectivePpm: Float,
    screenCenterX: Float,
    screenCenterY: Float,
    width: Float,
    height: Float
) {
    // Determine appropriate zoom level based on pixelsPerMeter
    // At zoom z, Earth circumference (40,075,016m) is 256 * 2^z pixels
    val initialZoom = (ln(effectivePpm * 40075016.0 / (256.0 * cos(Math.toRadians(centerLat)))) / ln(2.0)).toInt()
    val z = initialZoom.coerceIn(10, 18)

    val n = 2.0.pow(z.toDouble())
    val centerTileX = ((centerLon + 180.0) / 360.0 * n).toInt()
    val latRad = Math.toRadians(centerLat)
    val centerTileY = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n).toInt()

    // Render 3x3 surrounding tiles
    for (dx in -1..1) {
        for (dy in -1..1) {
            val tx = centerTileX + dx
            val ty = centerTileY + dy
            val bmp = tileManager.getTileBitmap(z, tx, ty)
            if (bmp != null) {
                // Calculate geographic origin of this tile
                val tileLon = tx / n * 360.0 - 180.0
                val tileLatRad = Math.atan(kotlin.math.sinh(Math.PI * (1.0 - 2.0 * ty / n)))
                val tileLat = Math.toDegrees(tileLatRad)

                val (eastM, northM) = GeodesyUtils.latLonToEnu(centerLat, centerLon, tileLat, tileLon)
                val sx = screenCenterX + (eastM * effectivePpm).toFloat()
                val sy = screenCenterY - (northM * effectivePpm).toFloat()
                drawImage(bmp.asImageBitmap(), topLeft = Offset(sx, sy))
            }
        }
    }
}
