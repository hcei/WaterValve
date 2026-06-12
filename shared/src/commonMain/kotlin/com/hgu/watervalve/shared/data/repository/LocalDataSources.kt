package com.hgu.watervalve.shared.data.repository

import com.hgu.watervalve.shared.domain.model.Device
import com.hgu.watervalve.shared.domain.model.WaterRecord

interface LocalDeviceDataSource {
    suspend fun getAll(): List<Device>
    suspend fun getById(id: String): Device?
    suspend fun upsert(device: Device)
    suspend fun replaceAll(devices: List<Device>)
    suspend fun deleteById(id: String)
    suspend fun deleteAll()
}

interface LocalWaterRecordDataSource {
    suspend fun getAll(): List<WaterRecord>
    suspend fun insert(record: WaterRecord)
    suspend fun deleteAll()
}
