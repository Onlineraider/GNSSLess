package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.PathPointEntity
import com.example.data.RouteEntity
import com.example.export.RouteExporter
import com.example.map.OfflineTileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RoutesViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application.applicationContext)
    val tileManager = OfflineTileManager(application.applicationContext)

    val routes: StateFlow<List<RouteEntity>> = database.routeDao().getAllRoutes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedRoute = MutableStateFlow<RouteEntity?>(null)
    val selectedRoute: StateFlow<RouteEntity?> = _selectedRoute.asStateFlow()

    private val _selectedRoutePoints = MutableStateFlow<List<PathPointEntity>>(emptyList())
    val selectedRoutePoints: StateFlow<List<PathPointEntity>> = _selectedRoutePoints.asStateFlow()

    private val _exportContent = MutableStateFlow<String?>(null)
    val exportContent: StateFlow<String?> = _exportContent.asStateFlow()

    fun selectRoute(route: RouteEntity?) {
        _selectedRoute.value = route
        if (route != null) {
            viewModelScope.launch {
                val pts = withContext(Dispatchers.IO) {
                    database.routeDao().getPointsForRoute(route.id)
                }
                _selectedRoutePoints.value = pts
            }
        } else {
            _selectedRoutePoints.value = emptyList()
        }
    }

    fun deleteRoute(route: RouteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            database.routeDao().deleteRouteById(route.id)
            database.routeDao().deletePointsForRoute(route.id)
            route.rawLogFilePath?.let { path ->
                try { File(path).delete() } catch (e: Exception) {}
            }
            if (_selectedRoute.value?.id == route.id) {
                _selectedRoute.value = null
                _selectedRoutePoints.value = emptyList()
            }
        }
    }

    fun renameRoute(route: RouteEntity, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = route.copy(title = newTitle.trim())
            database.routeDao().updateRoute(updated)
            if (_selectedRoute.value?.id == route.id) {
                _selectedRoute.value = updated
            }
        }
    }

    fun generateGpxExport(route: RouteEntity): String {
        return RouteExporter.toGpx(route, _selectedRoutePoints.value)
    }

    fun generateKmlExport(route: RouteEntity): String {
        return RouteExporter.toKml(route, _selectedRoutePoints.value)
    }

    fun clearExport() {
        _exportContent.value = null
    }
}
