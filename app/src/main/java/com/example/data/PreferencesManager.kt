package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.PowerProfile
import com.example.model.TransportMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_settings")

class PreferencesManager(private val context: Context) {
    companion object {
        val KEY_TRANSPORT_MODE = stringPreferencesKey("transport_mode")
        val KEY_POWER_PROFILE = stringPreferencesKey("power_profile")
        val KEY_TEST_MODE = booleanPreferencesKey("test_mode")
        val KEY_MOCK_RATE_HZ = intPreferencesKey("mock_rate_hz")
        val KEY_GPS_ACCURACY_THRESHOLD = floatPreferencesKey("gps_accuracy_threshold")
        val KEY_MAP_RENDER_MODE = stringPreferencesKey("map_render_mode")
    }

    val transportModeFlow: Flow<TransportMode> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_TRANSPORT_MODE] ?: TransportMode.WALKING.name
        try {
            TransportMode.valueOf(name)
        } catch (e: Exception) {
            TransportMode.WALKING
        }
    }

    val powerProfileFlow: Flow<PowerProfile> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_POWER_PROFILE] ?: PowerProfile.MAX_ACCURACY.name
        try {
            PowerProfile.valueOf(name)
        } catch (e: Exception) {
            PowerProfile.MAX_ACCURACY
        }
    }

    val testModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_TEST_MODE] ?: false
    }

    val mockRateHzFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_MOCK_RATE_HZ] ?: 5
    }

    val gpsAccuracyThresholdFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_GPS_ACCURACY_THRESHOLD] ?: 15.0f
    }

    suspend fun setTransportMode(mode: TransportMode) {
        context.dataStore.edit { it[KEY_TRANSPORT_MODE] = mode.name }
    }

    suspend fun setPowerProfile(profile: PowerProfile) {
        context.dataStore.edit { it[KEY_POWER_PROFILE] = profile.name }
    }

    suspend fun setTestMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TEST_MODE] = enabled }
    }

    suspend fun setMockRateHz(rate: Int) {
        context.dataStore.edit { it[KEY_MOCK_RATE_HZ] = rate }
    }

    suspend fun setGpsAccuracyThreshold(thresholdMeters: Float) {
        context.dataStore.edit { it[KEY_GPS_ACCURACY_THRESHOLD] = thresholdMeters }
    }
}
