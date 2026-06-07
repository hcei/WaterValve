package com.hgu.watervalve.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "water_record")
data class WaterRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val deviceName: String,
    val action: String,          // "open" / "close"
    val result: String,          // "success" / "fail"
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)
