package com.natan.obd.di

import android.content.Context
import androidx.room.Room
import com.natan.obd.data.AppDatabase
import com.natan.obd.data.DriverDao
import com.natan.obd.data.TripDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // One database instance for the whole process (the old code built a new one per screen).
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "androbd.db").build()

    @Provides fun driverDao(db: AppDatabase): DriverDao = db.driverDao()
    @Provides fun tripDao(db: AppDatabase): TripDao = db.tripDao()
}
