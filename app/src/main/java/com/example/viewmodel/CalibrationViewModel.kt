package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calibration.CalibrationManager
import com.example.calibration.CalibrationProgress
import com.example.data.AppDatabase
import com.example.data.CalibrationEntity
import com.example.model.TransportMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CalibrationViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application.applicationContext)
    val calibrationManager = CalibrationManager(application.applicationContext)
    val progress: StateFlow<CalibrationProgress> = calibrationManager.progressFlow

    val calibrations: StateFlow<List<CalibrationEntity>> = database.calibrationDao().getAllCalibrations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var countdownJob: Job? = null

    fun startCalibration(mode: TransportMode) {
        countdownJob?.cancel()
        calibrationManager.startCalibration(mode)
        countdownJob = viewModelScope.launch {
            var remaining = 10
            while (isActive && remaining > 0) {
                delay(1000L)
                remaining--
                calibrationManager.onSecondTick(remaining)
            }
        }
    }

    fun saveCalibration(entity: CalibrationEntity) {
        viewModelScope.launch {
            calibrationManager.saveCalibration(entity)
        }
    }

    fun cancelCalibration() {
        countdownJob?.cancel()
        calibrationManager.cancelCalibration()
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        calibrationManager.cancelCalibration()
    }
}
