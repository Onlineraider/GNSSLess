package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.PowerProfile
import com.example.ui.theme.AeroAmberGps
import com.example.ui.theme.AeroCyanLight
import com.example.ui.theme.AeroCyanPrimary
import com.example.ui.theme.AeroRoseError
import com.example.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val powerProfile by viewModel.powerProfile.collectAsStateWithLifecycle()
    val isTestMode by viewModel.isTestMode.collectAsStateWithLifecycle()
    val mockRateHz by viewModel.mockRateHz.collectAsStateWithLifecycle()
    val tileCount by viewModel.tileCount.collectAsStateWithLifecycle()
    val isImportingTiles by viewModel.isImportingTiles.collectAsStateWithLifecycle()
    val isMockAppSet by viewModel.isMockLocationAppSet.collectAsStateWithLifecycle()

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importTilesZip(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Power & Battery Profile (P55)
        ElevatedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = AeroCyanLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_power_profile), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = (powerProfile == PowerProfile.MAX_ACCURACY),
                        onClick = { viewModel.setPowerProfile(PowerProfile.MAX_ACCURACY) },
                        label = { Text("Max Accuracy (100Hz+)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = (powerProfile == PowerProfile.POWER_SAVING),
                        onClick = { viewModel.setPowerProfile(PowerProfile.POWER_SAVING) },
                        label = { Text("Power Saving (33Hz)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (powerProfile == PowerProfile.MAX_ACCURACY)
                        "Samples motion sensors at ~100Hz with 5Hz mock location dispatch. Ideal for sports, driving, and highest precision."
                    else
                        "Samples motion sensors at ~33Hz with 1Hz mock location dispatch. Minimizes battery draw during long train or walk sessions.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Test Mode Toggle (P60, P61)
        ElevatedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Science, contentDescription = null, tint = AeroAmberGps)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.settings_test_mode), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Parallel GNSS ground truth logging",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isTestMode,
                        onCheckedChange = { viewModel.setTestMode(it) },
                        modifier = Modifier.testTag("test_mode_switch")
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.settings_test_mode_desc),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Mock Location Framework (P40, P42)
        ElevatedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeveloperMode, contentDescription = null, tint = AeroCyanLight)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mock Location Provider (AOSP)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text(
                        if (isMockAppSet) "Configured" else "Action Needed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMockAppSet) AeroCyanLight else AeroAmberGps
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Enables other navigation and fitness apps to read this app's dead-reckoning position through Android's test provider mechanism.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_dev_settings_btn")
                ) {
                    Text(stringResource(R.string.settings_developer_options), fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Offline Map Tiles (P54)
        ElevatedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderZip, contentDescription = null, tint = AeroCyanLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_tile_import), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.settings_tile_import_desc),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Currently installed: $tileCount tiles",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AeroCyanLight
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { zipPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                        enabled = !isImportingTiles,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_tile_zip_btn")
                    ) {
                        if (isImportingTiles) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import ZIP", fontSize = 12.sp)
                        }
                    }

                    if (tileCount > 0) {
                        OutlinedButton(
                            onClick = { viewModel.clearTiles() },
                            modifier = Modifier.testTag("clear_tiles_btn")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = AeroRoseError, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", fontSize = 12.sp, color = AeroRoseError)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Privacy & Offline Guarantee Card (C01, C02, C03)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = AeroCyanLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("100% Offline & Sensor Independence", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "This application contains NO INTERNET permission and makes zero network calls. All sensor measurements, position estimations, and logs stay strictly on your device.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
