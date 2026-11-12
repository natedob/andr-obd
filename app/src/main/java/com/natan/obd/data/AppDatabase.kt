package com.natan.obd.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Driver::class, Trip::class, TripSample::class, RoutePoint::class],
    version = 2,
    // v2 added route_points; the exported schemas let Room generate the migration.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun driverDao(): DriverDao
    abstract fun tripDao(): TripDao
}
