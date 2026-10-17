package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.CalibrationScreen
import com.example.ui.screens.EvaluationScreen
import com.example.ui.screens.RoutesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TrackerScreen
import com.example.ui.theme.AeroCyanLight
import com.example.ui.theme.AeroCyanPrimary
import com.example.ui.theme.InertialTrackerTheme
import com.example.viewmodel.CalibrationViewModel
import com.example.viewmodel.RoutesViewModel
import com.example.viewmodel.SettingsViewModel
import com.example.viewmodel.TrackerViewModel

enum class AppTab(val titleRes: Int) {
    TRACKER(R.string.nav_track),
    ROUTES(R.string.nav_routes),
    CALIBRATE(R.string.nav_calibrate),
    SETTINGS(R.string.nav_settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            InertialTrackerTheme {
                MainAppContent()
            }
        }
    }
}

@Composable
fun MainAppContent() {
    var selectedTab by remember { mutableStateOf(AppTab.TRACKER) }
    var evaluationRouteId by remember { mutableStateOf<Long?>(null) }

    val trackerViewModel: TrackerViewModel = viewModel()
    val routesViewModel: RoutesViewModel = viewModel()
    val calibrationViewModel: CalibrationViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    // Request permissions on app launch
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        trackerViewModel.checkMockSetting()
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (evaluationRouteId == null) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    AppTab.entries.forEach { tab ->
                        val isSelected = (selectedTab == tab)
                        val icon = when (tab) {
                            AppTab.TRACKER -> Icons.Default.Explore
                            AppTab.ROUTES -> Icons.Default.Route
                            AppTab.CALIBRATE -> Icons.Default.Tune
                            AppTab.SETTINGS -> Icons.Default.Settings
                        }
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            icon = { Icon(icon, contentDescription = stringResource(tab.titleRes)) },
                            label = { Text(stringResource(tab.titleRes), fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AeroCyanLight,
                                selectedTextColor = AeroCyanLight,
                                indicatorColor = AeroCyanPrimary.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (evaluationRouteId != null) {
            BackHandler { evaluationRouteId = null }
            EvaluationScreen(
                routeId = evaluationRouteId!!,
                onBack = { evaluationRouteId = null },
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            when (selectedTab) {
                AppTab.TRACKER -> TrackerScreen(
                    viewModel = trackerViewModel,
                    modifier = Modifier.padding(innerPadding)
                )
                AppTab.ROUTES -> RoutesScreen(
                    viewModel = routesViewModel,
                    onNavigateToEvaluation = { routeId -> evaluationRouteId = routeId },
                    modifier = Modifier.padding(innerPadding)
                )
                AppTab.CALIBRATE -> CalibrationScreen(
                    viewModel = calibrationViewModel,
                    modifier = Modifier.padding(innerPadding)
                )
                AppTab.SETTINGS -> SettingsScreen(
                    viewModel = settingsViewModel,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
