package com.natan.obd

import android.app.Application
import com.natan.obd.trip.TripService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ObdApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TripService.createChannel(this)
    }
}
