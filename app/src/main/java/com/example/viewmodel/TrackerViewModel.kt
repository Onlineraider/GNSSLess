package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.PathPointEntity
import com.example.data.PreferencesManager
import com.example.map.OfflineTileManager
import com.example.mock.MockLocationPublisher
import com.example.model.PowerProfile
import com.example.model.TrackingMetrics
import com.example.model.TrackingState
import com.example.model.TransportMode
import com.example.service.TrackingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefsManager = PreferencesManager(context)
    val tileManager = OfflineTileManager(context)
    private val mockPublisher = MockLocationPublisher(context)

    val metricsState: StateFlow<TrackingMetrics> = TrackingService.metricsState
    val recentPathPoints: StateFlow<List<PathPointEntity>> = TrackingService.recentPathPoints

    val activeMode: StateFlow<TransportMode> = prefsManager.transportModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, TransportMode.WALKING)

    val powerProfile: StateFlow<PowerProfile> = prefsManager.powerProfileFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, PowerProfile.MAX_ACCURACY)

    val isTestMode: StateFlow<Boolean> = prefsManager.testModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val mockRateHz: StateFlow<Int> = prefsManager.mockRateHzFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 5)

    val gpsThresholdMeters: StateFlow<Float> = prefsManager.gpsAccuracyThresholdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 15.0f)

    private val _isMockAppSet = MutableStateFlow(true)
    val isMockAppSet: StateFlow<Boolean> = _isMockAppSet.asStateFlow()

    private val _isOfflineTilesMode = MutableStateFlow(false)
    val isOfflineTilesMode: StateFlow<Boolean> = _isOfflineTilesMode.asStateFlow()

    init {
        checkMockSetting()
    }

    fun checkMockSetting() {
        _isMockAppSet.value = mockPublisher.isMockLocationAppSet()
    }

    fun setMode(mode: TransportMode) {
        viewModelScope.launch {
            prefsManager.setTransportMode(mode)
        }
    }

    fun toggleOfflineTilesMode() {
        _isOfflineTilesMode.value = !_isOfflineTilesMode.value
    }

    fun startTracking() {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
            putExtra(TrackingService.EXTRA_MODE, activeMode.value.name)
            putExtra(TrackingService.EXTRA_POWER_PROFILE, powerProfile.value.name)
            putExtra(TrackingService.EXTRA_TEST_MODE, isTestMode.value)
            putExtra(TrackingService.EXTRA_MOCK_RATE, mockRateHz.value)
            putExtra(TrackingService.EXTRA_GPS_THRESHOLD, gpsThresholdMeters.value)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        checkMockSetting()
    }

    fun acceptCurrentFixNow() {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_ACCEPT_CURRENT_FIX
        }
        context.startService(intent)
    }

    fun reanchorWithGps() {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_REANCHOR
        }
        context.startService(intent)
    }

    fun pauseTracking() {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeTracking() {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun stopTracking() {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP
        }
        context.startService(intent)
    }
}
