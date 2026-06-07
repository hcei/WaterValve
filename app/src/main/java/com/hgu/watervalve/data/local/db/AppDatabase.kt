package com.hgu.watervalve.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hgu.watervalve.domain.model.Device
import com.hgu.watervalve.domain.model.WaterRecord

@Database(
    entities = [Device::class, WaterRecord::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun waterRecordDao(): WaterRecordDao
}
