package com.hgu.watervalve.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hgu.watervalve.domain.model.WaterRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterRecordDao {
    @Query("SELECT * FROM water_record ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<WaterRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: WaterRecord)

    @Query("DELETE FROM water_record")
    suspend fun deleteAll()
}
