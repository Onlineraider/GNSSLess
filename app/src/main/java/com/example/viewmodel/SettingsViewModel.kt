package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PreferencesManager
import com.example.map.OfflineTileManager
import com.example.mock.MockLocationPublisher
import com.example.model.PowerProfile
import com.example.model.TransportMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefsManager = PreferencesManager(application.applicationContext)
    val tileManager = OfflineTileManager(application.applicationContext)
    private val mockPublisher = MockLocationPublisher(application.applicationContext)

    val powerProfile: StateFlow<PowerProfile> = prefsManager.powerProfileFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, PowerProfile.MAX_ACCURACY)

    val isTestMode: StateFlow<Boolean> = prefsManager.testModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val mockRateHz: StateFlow<Int> = prefsManager.mockRateHzFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 5)

    val gpsThresholdMeters: StateFlow<Float> = prefsManager.gpsAccuracyThresholdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 15.0f)

    private val _tileCount = MutableStateFlow(tileManager.getInstalledTilesCount())
    val tileCount: StateFlow<Int> = _tileCount.asStateFlow()

    private val _isImportingTiles = MutableStateFlow(false)
    val isImportingTiles: StateFlow<Boolean> = _isImportingTiles.asStateFlow()

    private val _isMockLocationAppSet = MutableStateFlow(true)
    val isMockLocationAppSet: StateFlow<Boolean> = _isMockLocationAppSet.asStateFlow()

    init {
        refreshMockStatus()
    }

    fun refreshMockStatus() {
        _isMockLocationAppSet.value = mockPublisher.isMockLocationAppSet()
    }

    fun setPowerProfile(profile: PowerProfile) {
        viewModelScope.launch { prefsManager.setPowerProfile(profile) }
    }

    fun setTestMode(enabled: Boolean) {
        viewModelScope.launch { prefsManager.setTestMode(enabled) }
    }

    fun setMockRateHz(rate: Int) {
        viewModelScope.launch { prefsManager.setMockRateHz(rate) }
    }

    fun setGpsThreshold(threshold: Float) {
        viewModelScope.launch { prefsManager.setGpsAccuracyThreshold(threshold) }
    }

    fun importTilesZip(uri: Uri) {
        viewModelScope.launch {
            _isImportingTiles.value = true
            val count = tileManager.importTilesFromZip(uri)
            _tileCount.value = tileManager.getInstalledTilesCount()
            _isImportingTiles.value = false
        }
    }

    fun clearTiles() {
        viewModelScope.launch {
            tileManager.clearAllTiles()
            _tileCount.value = 0
        }
    }
}
