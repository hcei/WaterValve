package com.hgu.watervalve.shared.data.repository

import com.hgu.watervalve.shared.domain.model.Device
import com.hgu.watervalve.shared.domain.model.WaterRecord
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryLocalDeviceDataSource : LocalDeviceDataSource {
    private val mutex = Mutex()
    private val devices = linkedMapOf<String, Device>()

    override suspend fun getAll(): List<Device> = mutex.withLock {
        devices.values.toList()
    }

    override suspend fun getById(id: String): Device? = mutex.withLock {
        devices[id]
    }

    override suspend fun upsert(device: Device) {
        mutex.withLock {
            devices[device.id] = device
        }
    }

    override suspend fun replaceAll(devices: List<Device>) {
        mutex.withLock {
            this.devices.clear()
            devices.forEach { this.devices[it.id] = it }
        }
    }

    override suspend fun deleteById(id: String) {
        mutex.withLock {
            devices.remove(id)
        }
    }

    override suspend fun deleteAll() {
        mutex.withLock {
            devices.clear()
        }
    }
}

class InMemoryLocalWaterRecordDataSource : LocalWaterRecordDataSource {
    private val mutex = Mutex()
    private val records = mutableListOf<WaterRecord>()

    override suspend fun getAll(): List<WaterRecord> = mutex.withLock {
        records.toList()
    }

    override suspend fun insert(record: WaterRecord) {
        mutex.withLock {
            records += record
        }
    }

    override suspend fun deleteAll() {
        mutex.withLock {
            records.clear()
        }
    }
}
