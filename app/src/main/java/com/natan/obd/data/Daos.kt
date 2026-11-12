package com.natan.obd.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DriverDao {
    @Query("SELECT * FROM drivers ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Driver>>

    @Query(
        """
        SELECT d.id AS id, d.name AS name, COUNT(t.id) AS tripCount,
               AVG(t.score) AS avgScore, SUM(t.distanceKm) AS totalKm
        FROM drivers d LEFT JOIN trips t ON t.driverId = d.id
        GROUP BY d.id
        ORDER BY avgScore DESC, d.name COLLATE NOCASE
        """
    )
    fun observeSummaries(): Flow<List<DriverSummary>>

    /** Throws SQLiteConstraintException if the name is taken. */
    @Insert
    suspend fun insert(driver: Driver): Long

    @Delete
    suspend fun delete(driver: Driver)
}

@Dao
abstract class TripDao {
    @Insert
    protected abstract suspend fun insertTrip(trip: Trip): Long

    @Insert
    protected abstract suspend fun insertSamples(samples: List<TripSample>)

    @Insert
    protected abstract suspend fun insertRoute(points: List<RoutePoint>)

    /** Trip, samples and route are written atomically, so history never shows a half-saved trip. */
    @Transaction
    open suspend fun insertWithSamples(trip: Trip, samples: List<TripSample>, route: List<RoutePoint>): Long {
        val id = insertTrip(trip)
        insertSamples(samples.map { it.copy(tripId = id) })
        insertRoute(route.map { it.copy(tripId = id) })
        return id
    }

    @Query("SELECT * FROM route_points WHERE tripId = :tripId ORDER BY tSec")
    abstract suspend fun route(tripId: Long): List<RoutePoint>

    @Query("SELECT * FROM trips ORDER BY startedAt DESC")
    abstract fun observeAll(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE id = :id")
    abstract fun observe(id: Long): Flow<Trip?>

    @Query("SELECT * FROM trip_samples WHERE tripId = :tripId ORDER BY tSec")
    abstract suspend fun samples(tripId: Long): List<TripSample>

    @Query("DELETE FROM trips WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    @Query("DELETE FROM trips")
    abstract suspend fun deleteAll()
}
