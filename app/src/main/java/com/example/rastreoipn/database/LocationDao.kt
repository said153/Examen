package com.ipn.rastreo.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LocationDao {
    @Insert
    suspend fun insert(location: LocationEntity)

    @Query("SELECT * FROM locations ORDER BY timestamp DESC")
    suspend fun getAll(): List<LocationEntity>

    @Query("DELETE FROM locations")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM locations")
    suspend fun getCount(): Int

    @Query("SELECT * FROM locations WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getByDateRange(startTime: Long, endTime: Long): List<LocationEntity>
}