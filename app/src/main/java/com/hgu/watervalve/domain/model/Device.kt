package com.hgu.watervalve.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device")
data class Device(
    @PrimaryKey val id: String,
    val bleId: String,
    val customName: String,
    val displayOrder: Int = 0,
    val isFavorite: Boolean = false,
    val lastUsedAt: Long = 0L,
)
