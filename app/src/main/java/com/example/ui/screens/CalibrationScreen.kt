package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.TransportMode
import com.example.ui.theme.AeroAmberGps
import com.example.ui.theme.AeroCyanLight
import com.example.ui.theme.AeroCyanPrimary
import com.example.ui.theme.AeroEmeraldZupt
import com.example.ui.theme.AeroRoseError
import com.example.viewmodel.CalibrationViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CalibrationScreen(
    viewModel: CalibrationViewModel,
    modifier: Modifier = Modifier
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val savedCalibrations by viewModel.calibrations.collectAsStateWithLifecycle()

    var selectedMode by remember { mutableStateOf(TransportMode.WALKING) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.calib_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Instructions Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = AeroCyanLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sensor Zero-Bias Procedure (P20)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.calib_instructions),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Target Mode Selection Chips
        Text("Select Mode to Calibrate:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(TransportMode.WALKING, TransportMode.CAR, TransportMode.BICYCLE_FIXED, TransportMode.TRANSIT_FIXED).forEach { mode ->
                FilterChip(
                    selected = (mode == selectedMode),
                    onClick = {
                        if (!progress.isRunning) selectedMode = mode
                    },
                    label = { Text(stringResource(mode.titleResId), fontSize = 11.sp) },
                    enabled = !progress.isRunning,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AeroCyanPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = AeroCyanLight
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Interactive Calibration Execution Card
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (progress.isRunning) {
                    Text(
                        stringResource(R.string.calib_keep_still, progress.secondsRemaining),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AeroCyanLight
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { (10 - progress.secondsRemaining) / 10f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = AeroCyanPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stability indicator
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(if (progress.isDeviceStill) AeroEmeraldZupt else AeroRoseError, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (progress.isDeviceStill) "Device Stationary (Good)" else "Movement Detected! Keep device flat & still",
                            fontSize = 12.sp,
                            color = if (progress.isDeviceStill) AeroEmeraldZupt else AeroRoseError
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Samples: ${progress.gyroSamplesCount} gyro • ${progress.accelSamplesCount} accel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = { viewModel.cancelCalibration() },
                        modifier = Modifier.testTag("cancel_calib_btn")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel")
                    }
                } else if (progress.completedSuccessfully && progress.resultEntity != null) {
                    val result = progress.resultEntity!!
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AeroEmeraldZupt, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(stringResource(R.string.calib_step_done), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AeroEmeraldZupt)

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        String.format(Locale.US, "Gyro Bias: [%.4f, %.4f, %.4f] rad/s", result.gyroBiasX, result.gyroBiasY, result.gyroBiasZ),
                        fontSize = 11.sp
                    )
                    Text(
                        String.format(Locale.US, "Accel Bias: [%.3f, %.3f, %.3f] m/s²", result.accelBiasX, result.accelBiasY, result.accelBiasZ),
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            viewModel.saveCalibration(result)
                            viewModel.cancelCalibration()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AeroEmeraldZupt),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_calib_btn")
                    ) {
                        Text("Save Profile for ${selectedMode.name}")
                    }
                } else {
                    Text(
                        "Ready to calibrate ${selectedMode.name}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Place phone on table/mount and tap start. Takes 10 seconds.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { viewModel.startCalibration(selectedMode) },
                        colors = ButtonDefaults.buttonColors(containerColor = AeroCyanPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("start_calib_btn")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_calibrate_now), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Stored Calibrations List
        Text("Saved Calibration Profiles", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))

        if (savedCalibrations.isEmpty()) {
            Text(
                "No custom calibrations saved yet. Default factory profiles in use.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(savedCalibrations, key = { it.mode }) { calib ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(calib.mode, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    dateFormat.format(Date(calib.calibratedAt)),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                String.format(Locale.US, "Gyro bias: [%.4f, %.4f, %.4f] rad/s", calib.gyroBiasX, calib.gyroBiasY, calib.gyroBiasZ),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
