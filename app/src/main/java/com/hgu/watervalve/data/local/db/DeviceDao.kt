package com.hgu.watervalve.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hgu.watervalve.domain.model.Device
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM device ORDER BY displayOrder ASC")
    fun observeAll(): Flow<List<Device>>

    @Query("SELECT * FROM device WHERE isFavorite = 1 ORDER BY displayOrder ASC")
    fun observeFavorites(): Flow<List<Device>>

    @Query("SELECT * FROM device WHERE id = :id")
    suspend fun getById(id: String): Device?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: Device)

    @Update
    suspend fun update(device: Device)

    @Delete
    suspend fun delete(device: Device)

    @Query("SELECT MAX(displayOrder) FROM device")
    suspend fun getMaxDisplayOrder(): Int?

    @Query("DELETE FROM device")
    suspend fun deleteAll()
}
