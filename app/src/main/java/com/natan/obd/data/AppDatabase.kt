package com.natan.obd.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Driver::class, Trip::class, TripSample::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun driverDao(): DriverDao
    abstract fun tripDao(): TripDao
}
