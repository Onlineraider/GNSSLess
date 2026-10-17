package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.map.PathCanvasView
import com.example.model.AltitudeSource
import com.example.model.TrackingState
import com.example.model.TransportMode
import com.example.ui.theme.AeroAmberGps
import com.example.ui.theme.AeroCyanLight
import com.example.ui.theme.AeroCyanPrimary
import com.example.ui.theme.AeroEmeraldZupt
import com.example.ui.theme.AeroRoseError
import com.example.viewmodel.TrackerViewModel
import java.util.Locale

@Composable
fun TrackerScreen(
    viewModel: TrackerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val metrics by viewModel.metricsState.collectAsStateWithLifecycle()
    val points by viewModel.recentPathPoints.collectAsStateWithLifecycle()
    val activeMode by viewModel.activeMode.collectAsStateWithLifecycle()
    val isMockAppSet by viewModel.isMockAppSet.collectAsStateWithLifecycle()
    val isOfflineTilesMode by viewModel.isOfflineTilesMode.collectAsStateWithLifecycle()

    var recenterTrigger by remember { mutableStateOf(false) }
    var isMockBannerDismissed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Mode Selector Chips Row (P10 - P15)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransportMode.entries.forEach { mode ->
                val isSelected = (mode == activeMode)
                val icon = when (mode) {
                    TransportMode.WALKING -> Icons.Default.DirectionsWalk
                    TransportMode.CAR -> Icons.Default.DirectionsCar
                    TransportMode.BICYCLE_FIXED, TransportMode.BICYCLE_LOOSE -> Icons.Default.DirectionsBike
                    TransportMode.TRANSIT_FIXED, TransportMode.TRANSIT_LOOSE -> Icons.Default.DirectionsTransit
                }
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (metrics.trackingState == TrackingState.IDLE) {
                            viewModel.setMode(mode)
                        }
                    },
                    label = { Text(stringResource(mode.titleResId), fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    enabled = (metrics.trackingState == TrackingState.IDLE),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AeroCyanPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = AeroCyanLight
                    ),
                    modifier = Modifier.testTag("mode_chip_${mode.name}")
                )
            }
        }

        // Mock Location Informational Banner (Optional)
        AnimatedVisibility(visible = !isMockAppSet && !isMockBannerDismissed) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AeroCyanPrimary.copy(alpha = 0.12f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Sensors, contentDescription = null, tint = AeroCyanLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.mock_provider_warning),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TextButton(
                        onClick = { isMockBannerDismissed = true },
                        modifier = Modifier.testTag("dismiss_mock_banner_btn")
                    ) {
                        Text("OK", fontSize = 11.sp)
                    }
                }
            }
        }

        // Live Status & GPS Bar (U03)
        StatusHeader(metrics = metrics)

        // Live Metrics HUD Card
        LiveMetricsHud(metrics = metrics)

        // Interactive Map / Path Canvas View (P54)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            PathCanvasView(
                points = points,
                isOfflineTilesMode = isOfflineTilesMode,
                tileManager = viewModel.tileManager,
                onRecenterRequested = recenterTrigger,
                onRecenterHandled = { recenterTrigger = false }
            )

            // Recenter & Map Mode Overlay Buttons
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { viewModel.toggleOfflineTilesMode() },
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                        .testTag("toggle_map_mode_btn")
                ) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = "Toggle Offline Tiles",
                        tint = if (isOfflineTilesMode) AeroCyanLight else MaterialTheme.colorScheme.onSurface
                    )
                }

                FloatingActionButton(
                    onClick = { recenterTrigger = true },
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("recenter_map_btn"),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = AeroCyanLight
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Recenter", modifier = Modifier.size(20.dp))
                }
            }
        }

        // Action Control Dock
        ActionDock(
            metrics = metrics,
            onStart = { viewModel.startTracking() },
            onAcceptFixNow = { viewModel.acceptCurrentFixNow() },
            onReanchor = { viewModel.reanchorWithGps() },
            onPause = { viewModel.pauseTracking() },
            onResume = { viewModel.resumeTracking() },
            onStop = { viewModel.stopTracking() }
        )
    }
}

@Composable
private fun StatusHeader(metrics: com.example.model.TrackingMetrics) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val stateText = when (metrics.trackingState) {
            TrackingState.IDLE -> stringResource(R.string.status_idle)
            TrackingState.ACQUIRING_START_FIX -> stringResource(R.string.status_acquiring_fix)
            TrackingState.TRACKING -> stringResource(R.string.status_tracking)
            TrackingState.PAUSED -> stringResource(R.string.status_paused)
            TrackingState.STOPPED -> stringResource(R.string.status_stopped)
        }
        val stateColor = when (metrics.trackingState) {
            TrackingState.TRACKING -> AeroEmeraldZupt
            TrackingState.ACQUIRING_START_FIX -> AeroAmberGps
            TrackingState.PAUSED -> Color(0xFFF97316)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(stateColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stateText, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = stateColor)
        }

        // GPS State Tag (Strictly shows Off during tracking unless fix/test mode)
        val gpsLabel = when {
            metrics.isTestModeActive -> stringResource(R.string.gps_status_test_mode)
            metrics.trackingState == TrackingState.ACQUIRING_START_FIX -> stringResource(R.string.gps_status_searching)
            metrics.trackingState == TrackingState.TRACKING -> stringResource(R.string.gps_status_off)
            else -> "GPS: Standby"
        }
        Text(gpsLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LiveMetricsHud(metrics: com.example.model.TrackingMetrics) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Speed & Course Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    val speedKmh = (metrics.currentFix?.speedMps ?: 0f) * 3.6f
                    Text(
                        text = String.format(Locale.US, "%.1f", speedKmh),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "km/h • ${(metrics.currentFix?.speedMps ?: 0f).let { String.format(Locale.US, "%.1f m/s", it) }}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Course & Cardinal Bearing
                Column(horizontalAlignment = Alignment.End) {
                    val course = metrics.currentFix?.courseDeg ?: 0f
                    val cardinal = getCardinalDirection(course)
                    Text(
                        text = "${course.toInt()}° $cardinal",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AeroCyanLight
                    )
                    Text(
                        text = stringResource(R.string.metric_course),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-metrics Grid (Distance, Duration, Altitude, Error Radius)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Distance Traveled
                Column {
                    val distText = if (metrics.distanceTraveledMeters >= 1000) {
                        String.format(Locale.US, "%.2f km", metrics.distanceTraveledMeters / 1000f)
                    } else {
                        "${metrics.distanceTraveledMeters.toInt()} m"
                    }
                    Text(distText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.metric_distance), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Duration
                Column {
                    val mins = metrics.durationSeconds / 60
                    val secs = metrics.durationSeconds % 60
                    val durText = String.format(Locale.US, "%02d:%02d", mins, secs)
                    Text(durText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.metric_duration), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Altitude
                Column {
                    val altText = "${(metrics.currentFix?.altitude ?: 0.0).toInt()} m"
                    Text(altText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    val isBaro = metrics.currentFix?.altitudeSource == AltitudeSource.BAROMETER
                    Text(
                        if (isBaro) "Baro Alt" else "Accel Alt",
                        fontSize = 11.sp,
                        color = if (isBaro) AeroEmeraldZupt else AeroAmberGps
                    )
                }

                // Honest Error Radius (P05)
                Column(horizontalAlignment = Alignment.End) {
                    val errRadius = metrics.currentFix?.errorRadiusMeters ?: 10f
                    val errText = "±${errRadius.toInt()} m"
                    val errColor = when {
                        errRadius < 25f -> AeroEmeraldZupt
                        errRadius < 75f -> AeroAmberGps
                        else -> AeroRoseError
                    }
                    Text(errText, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = errColor)
                    Text(stringResource(R.string.metric_error_radius), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ActionDock(
    metrics: com.example.model.TrackingMetrics,
    onStart: () -> Unit,
    onAcceptFixNow: () -> Unit,
    onReanchor: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (metrics.trackingState) {
                TrackingState.IDLE, TrackingState.STOPPED -> {
                    Button(
                        onClick = onStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_tracking_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AeroCyanPrimary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_start), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                TrackingState.ACQUIRING_START_FIX -> {
                    val anchor = metrics.gpsAnchor
                    Column(modifier = Modifier.weight(1f)) {
                        if (anchor != null) {
                            Text(
                                text = "GPS: ±${anchor.accuracyMeters.toInt()}m",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AeroCyanLight
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.status_searching_signal),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Button(
                        onClick = onAcceptFixNow,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (anchor != null) AeroCyanPrimary else AeroAmberGps
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("accept_fix_now_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        val btnText = if (anchor != null) stringResource(R.string.btn_accept_fix_now) else stringResource(R.string.btn_skip_gps)
                        Text(btnText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                TrackingState.TRACKING -> {
                    OutlinedButton(
                        onClick = onReanchor,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("reanchor_button")
                    ) {
                        Icon(Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_reanchor), fontSize = 11.sp)
                    }

                    Button(
                        onClick = onPause,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pause_button")
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_pause), fontSize = 13.sp)
                    }

                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = AeroRoseError),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("stop_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_stop), fontSize = 13.sp)
                    }
                }

                TrackingState.PAUSED -> {
                    Button(
                        onClick = onResume,
                        colors = ButtonDefaults.buttonColors(containerColor = AeroCyanPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("resume_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_resume), fontSize = 13.sp)
                    }

                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = AeroRoseError),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("stop_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_stop), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private fun getCardinalDirection(deg: Float): String {
    val normalized = (deg % 360f + 360f) % 360f
    return when {
        normalized < 22.5f -> "N"
        normalized < 67.5f -> "NE"
        normalized < 112.5f -> "E"
        normalized < 157.5f -> "SE"
        normalized < 202.5f -> "S"
        normalized < 247.5f -> "SW"
        normalized < 292.5f -> "W"
        normalized < 337.5f -> "NW"
        else -> "N"
    }
}
