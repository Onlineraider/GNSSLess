package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppDatabase
import com.example.data.PathPointEntity
import com.example.data.RouteEntity
import com.example.engine.GeodesyUtils
import com.example.map.PathCanvasView
import com.example.ui.theme.AeroAmberGps
import com.example.ui.theme.AeroCyanLight
import com.example.ui.theme.AeroEmeraldZupt
import com.example.ui.theme.AeroRoseError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun EvaluationScreen(
    routeId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var route by remember { mutableStateOf<RouteEntity?>(null) }
    var points by remember { mutableStateOf<List<PathPointEntity>>(emptyList()) }

    LaunchedEffect(routeId) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            route = db.routeDao().getRouteById(routeId)
            points = db.routeDao().getPointsForRoute(routeId)
        }
    }

    val comparisonPoints = points.filter { it.isGpsComparison && it.gpsLatitude != null && it.gpsLongitude != null }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top Bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("eval_back_btn")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    stringResource(R.string.eval_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    route?.title ?: "Loading...",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Test Mode Verification Badge
        Surface(
            color = AeroAmberGps.copy(alpha = 0.15f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AeroAmberGps)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    stringResource(R.string.settings_test_mode_desc),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (comparisonPoints.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "No parallel GPS comparison points recorded for this route session.",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 13.sp
                )
            }
        } else {
            // Calculate Evaluation Metrics
            var maxError = 0f
            var sumError = 0f
            var pointsInsideRadius = 0

            val errorSeries = mutableListOf<Pair<Long, Float>>() // (elapsedSec, errorMeters)
            val startTime = comparisonPoints.first().timestamp

            for (p in comparisonPoints) {
                val err = p.errorDistanceMeters ?: GeodesyUtils.distanceMeters(
                    p.latitude, p.longitude,
                    p.gpsLatitude!!, p.gpsLongitude!!
                ).toFloat()

                if (err > maxError) maxError = err
                sumError += err
                if (err <= p.errorRadiusMeters) pointsInsideRadius++

                val elapsedSec = (p.timestamp - startTime) / 1000L
                errorSeries.add(Pair(elapsedSec, err))
            }

            val meanError = sumError / comparisonPoints.size
            val radiusCoveragePct = (pointsInsideRadius.toFloat() / comparisonPoints.size.toFloat()) * 100f
            val driftRatio = if ((route?.totalDistanceMeters ?: 0f) > 10f) {
                (errorSeries.last().second / route!!.totalDistanceMeters) * 100f
            } else 0f

            // Key Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Mean Drift",
                    value = String.format(Locale.US, "%.1f m", meanError),
                    subtitle = "Average error",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Max Drift",
                    value = String.format(Locale.US, "%.1f m", maxError),
                    subtitle = "Peak deviation",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Drift / Distance",
                    value = String.format(Locale.US, "%.2f%%", driftRatio),
                    subtitle = "${(driftRatio * 10).toInt()}m per km",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Radius Coverage",
                    value = String.format(Locale.US, "%.1f%%", radiusCoveragePct),
                    subtitle = if (radiusCoveragePct >= 80f) "Honest uncertainty" else "Underestimated",
                    valueColor = if (radiusCoveragePct >= 80f) AeroEmeraldZupt else AeroAmberGps,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Drift Over Time Chart (P61)
            Text(
                stringResource(R.string.eval_drift_over_time),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            DriftOverTimeChart(
                errorSeries = errorSeries,
                maxError = maxError,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Visual Path Comparison
            Text(
                "Ground Truth Overlay (Cyan: Inertial, Amber: GPS)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                PathCanvasView(points = points)
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    ElevatedCard(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = valueColor)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DriftOverTimeChart(
    errorSeries: List<Pair<Long, Float>>,
    maxError: Float,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            if (errorSeries.size < 2) return@Canvas

            val w = size.width
            val h = size.height
            val effectiveMax = maxError.coerceAtLeast(10f) * 1.15f
            val maxSec = errorSeries.last().first.coerceAtLeast(10L).toFloat()

            // Draw grid lines
            drawLine(Color(0x3394A3B8), Offset(0f, h), Offset(w, h), 1.5f)
            drawLine(Color(0x2294A3B8), Offset(0f, h / 2f), Offset(w, h / 2f), 1f)
            drawLine(Color(0x2294A3B8), Offset(0f, 0f), Offset(w, 0f), 1f)

            // Draw drift curve
            val chartPath = Path()
            val firstX = (errorSeries.first().first / maxSec) * w
            val firstY = h - (errorSeries.first().second / effectiveMax) * h
            chartPath.moveTo(firstX, firstY)

            for (i in 1 until errorSeries.size) {
                val pt = errorSeries[i]
                val x = (pt.first / maxSec) * w
                val y = h - (pt.second / effectiveMax) * h
                chartPath.lineTo(x, y)
            }

            drawPath(
                path = chartPath,
                color = AeroAmberGps,
                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
            )
        }
    }
}
