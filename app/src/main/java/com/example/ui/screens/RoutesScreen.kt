package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.data.RouteEntity
import com.example.map.PathCanvasView
import com.example.model.TransportMode
import com.example.ui.theme.AeroAmberGps
import com.example.ui.theme.AeroCyanLight
import com.example.ui.theme.AeroCyanPrimary
import com.example.ui.theme.AeroRoseError
import com.example.viewmodel.RoutesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesScreen(
    viewModel: RoutesViewModel,
    onNavigateToEvaluation: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val routes by viewModel.routes.collectAsStateWithLifecycle()
    val selectedRoute by viewModel.selectedRoute.collectAsStateWithLifecycle()
    val routePoints by viewModel.selectedRoutePoints.collectAsStateWithLifecycle()

    var showRenameDialog by remember { mutableStateOf<RouteEntity?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<RouteEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.nav_routes),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (routes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Route,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No recorded routes yet",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Start a tracking session on the Tracker tab to record an inertial path.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(routes, key = { it.id }) { route ->
                    RouteCard(
                        route = route,
                        dateFormat = dateFormat,
                        onClick = { viewModel.selectRoute(route) },
                        onRename = {
                            showRenameDialog = route
                            renameText = route.title
                        },
                        onDelete = { showDeleteConfirmDialog = route },
                        onEvaluate = { onNavigateToEvaluation(route.id) }
                    )
                }
            }
        }
    }

    // Route Detail Bottom Sheet
    if (selectedRoute != null) {
        val route = selectedRoute!!
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectRoute(null) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(route.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            dateFormat.format(Date(route.startTime)) + " • Mode: ${route.mode}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (route.isTestMode) {
                        Surface(
                            color = AeroAmberGps.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "TEST MODE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AeroAmberGps,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Route Canvas Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    PathCanvasView(
                        points = routePoints,
                        isOfflineTilesMode = false,
                        tileManager = viewModel.tileManager
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem("Distance", "${route.totalDistanceMeters.toInt()} m")
                    StatItem("Duration", "${route.durationSeconds / 60}m ${route.durationSeconds % 60}s")
                    StatItem("Avg Speed", String.format(Locale.US, "%.1f km/h", route.avgSpeedMps * 3.6f))
                    StatItem("Max Speed", String.format(Locale.US, "%.1f km/h", route.maxSpeedMps * 3.6f))
                    StatItem("Est. Error", "±${route.finalErrorRadiusMeters.toInt()}m")
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Evaluation button if test mode
                if (route.isTestMode) {
                    Button(
                        onClick = {
                            viewModel.selectRoute(null)
                            onNavigateToEvaluation(route.id)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AeroAmberGps),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("open_evaluation_btn")
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Drift & Error Evaluation (GPS vs Inertial)")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Export Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val gpx = viewModel.generateGpxExport(route)
                            shareOrCopy(context, gpx, "${route.title}.gpx", "application/gpx+xml")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_gpx_btn")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("GPX", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val kml = viewModel.generateKmlExport(route)
                            shareOrCopy(context, kml, "${route.title}.kml", "application/vnd.google-earth.kml+xml")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_kml_btn")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("KML", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            route.rawLogFilePath?.let { path ->
                                val file = java.io.File(path)
                                if (file.exists()) {
                                    val content = file.readText()
                                    shareOrCopy(context, content, "route_${route.id}_raw.jsonl", "application/json")
                                } else {
                                    Toast.makeText(context, "Raw file not found", Toast.LENGTH_SHORT).show()
                                }
                            } ?: Toast.makeText(context, "No raw data recorded", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_raw_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Raw Data", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog != null) {
        val target = showRenameDialog!!
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text(stringResource(R.string.btn_rename)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Route Title") },
                    singleLine = true,
                    modifier = Modifier.testTag("rename_text_field")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameRoute(target, renameText)
                        showRenameDialog = null
                    },
                    modifier = Modifier.testTag("confirm_rename_button")
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) { Text("Cancel") }
            }
        )
    }

    // Delete Confirm Dialog
    if (showDeleteConfirmDialog != null) {
        val target = showDeleteConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text(stringResource(R.string.btn_delete)) },
            text = { Text("Are you sure you want to delete \"${target.title}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRoute(target)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AeroRoseError),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RouteCard(
    route: RouteEntity,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onEvaluate: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("route_card_${route.id}"),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val modeEnum = try { TransportMode.valueOf(route.mode) } catch (e: Exception) { TransportMode.WALKING }
                    val icon = when (modeEnum) {
                        TransportMode.WALKING -> Icons.Default.DirectionsWalk
                        TransportMode.CAR -> Icons.Default.DirectionsCar
                        TransportMode.BICYCLE_FIXED, TransportMode.BICYCLE_LOOSE -> Icons.Default.DirectionsBike
                        TransportMode.TRANSIT_FIXED, TransportMode.TRANSIT_LOOSE -> Icons.Default.DirectionsTransit
                    }
                    Icon(icon, contentDescription = null, tint = AeroCyanLight, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(route.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Row {
                    IconButton(onClick = onRename, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AeroRoseError, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                dateFormat.format(Date(route.startTime)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${route.totalDistanceMeters.toInt()} m",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${route.durationSeconds / 60}m ${route.durationSeconds % 60}s",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "±${route.finalErrorRadiusMeters.toInt()}m drift",
                    fontSize = 13.sp,
                    color = AeroCyanLight,
                    fontWeight = FontWeight.Medium
                )
            }

            if (route.isTestMode) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Test Recording (Ground Truth logged)",
                        fontSize = 11.sp,
                        color = AeroAmberGps,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(onClick = onEvaluate) {
                        Text("View Evaluation", fontSize = 11.sp, color = AeroAmberGps)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun shareOrCopy(context: Context, content: String, fileName: String, mimeType: String) {
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, content)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export $fileName")
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(fileName, content))
        Toast.makeText(context, "$fileName copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
