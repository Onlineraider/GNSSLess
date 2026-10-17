package com.example

import android.app.Application
import com.example.data.AppDatabase

class InertialTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDatabase.getInstance(this)
    }
}
