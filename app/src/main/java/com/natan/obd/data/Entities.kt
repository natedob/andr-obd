package com.natan.obd.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "drivers", indices = [Index(value = ["name"], unique = true)])
data class Driver(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // NOCASE makes "Ann" and "ann" collide on the unique index instead of silently coexisting.
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "trips",
    foreignKeys = [ForeignKey(Driver::class, ["id"], ["driverId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("driverId")],
)
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val driverId: Long,
    val startedAt: Long,
    val durationSec: Int,
    val distanceKm: Double,
    val avgRpm: Int,
    val avgSpeed: Int,
    val maxSpeed: Int,
    val avgLoad: Double,
    /** Null when the car exposes no usable fuel data. */
    val fuelLitres: Double?,
    val consumptionL100: Double?,
    val score: Int,
    val speedScore: Int,
    val rpmScore: Int,
    val loadScore: Int,
    val efficiencyScore: Int?,
)

/** One down-sampled data point of a trip, used for the charts on the detail screen. */
@Entity(
    tableName = "trip_samples",
    foreignKeys = [ForeignKey(Trip::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class TripSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val tSec: Int,
    val rpm: Int,
    val speed: Int,
    val load: Double,
)

data class DriverSummary(
    val id: Long,
    val name: String,
    val tripCount: Int,
    val avgScore: Double?,
    val totalKm: Double?,
)
