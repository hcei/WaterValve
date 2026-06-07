package com.hgu.watervalve.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android BLE 管理器：扫描、连接、读写特征。
 *
 * 所有 BLE 操作回调通过 [events] SharedFlow 向外发射，
 * 连接状态通过 [connectionState] StateFlow 暴露。
 *
 * ## 使用流程
 * ```
 * bleManager.startScan().collect { result -> ... }   // 扫描设备
 * bleManager.stopScan()
 * bleManager.connect(macAddress)                     // 连接设备
 * bleManager.discoverServices()                      // 发现服务
 * bleManager.writeCharacteristic(svc, char, data)    // 写指令
 * bleManager.disconnect()
 * ```
 */
@Singleton
class BleManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "BleManager"
        /** BLE 扫描超时默认 10 秒 */
        const val DEFAULT_SCAN_TIMEOUT_MS = 10_000L
        /** Client Characteristic Configuration Descriptor UUID */
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    // --- 基础设施 ---
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner
    private var bluetoothGatt: BluetoothGatt? = null

    // --- 状态 ---
    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<BleEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<BleEvent> = _events.asSharedFlow()

    // 发现的服务缓存
    private var discoveredServices: List<BleServiceInfo> = emptyList()

    // 当前连接的设备地址
    private var connectedDeviceAddress: String? = null

    /** 是否支持 BLE */
    val isBleSupported: Boolean get() = bluetoothAdapter != null

    /** 蓝牙是否已开启 */
    val isBluetoothEnabled: Boolean get() = bluetoothAdapter?.isEnabled == true

    // ═══════════════════════════════════════════════════════════
    // BLE 扫描
    // ═══════════════════════════════════════════════════════════

    /**
     * 开始 BLE 扫描，返回 [BleScanResult] 的 Flow。
     *
     * 可多次收集（热流）。超时后自动停止扫描并发射 [BleEvent.ScanFinished]。
     *
     * @param timeoutMs 扫描超时（毫秒），默认 10 秒
     */
    fun scanResults(timeoutMs: Long = DEFAULT_SCAN_TIMEOUT_MS): Flow<BleScanResult> = callbackFlow {
        val leScanner = scanner
        if (leScanner == null) {
            _events.emit(BleEvent.Error("BLE 扫描器不可用（蓝牙未开启或不支持）"))
            close()
            return@callbackFlow
        }

        Log.d(TAG, "开始 BLE 扫描，超时 ${timeoutMs}ms")

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name ?: ""
                val address = device.address ?: ""
                if (address.isBlank()) return

                val scanRecord = result.scanRecord?.bytes?.joinToString("") { "%02x".format(it) } ?: ""

                val scanResult = BleScanResult(
                    macAddress = address,
                    name = name,
                    rssi = result.rssi,
                    scanRecord = scanRecord,
                )
                trySend(scanResult)
                _events.tryEmit(BleEvent.DeviceFound(scanResult))
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE 扫描失败: errorCode=$errorCode")
                _events.tryEmit(BleEvent.Error("BLE 扫描失败 (code=$errorCode)"))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { onScanResult(0, it) }
            }
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        leScanner.startScan(emptyList<ScanFilter>(), scanSettings, scanCallback)

        // 超时后自动停止
        if (timeoutMs > 0) {
            kotlinx.coroutines.delay(timeoutMs)
            try {
                leScanner.stopScan(scanCallback)
            } catch (_: Exception) {}
            _events.tryEmit(BleEvent.ScanFinished)
            Log.d(TAG, "BLE 扫描结束（超时）")
        }

        awaitClose {
            try {
                leScanner.stopScan(scanCallback)
            } catch (_: Exception) {}
            Log.d(TAG, "BLE 扫描 Flow 关闭")
        }
    }

    /** 检查蓝牙是否可用 */
    fun requireBluetooth(): Result<Unit> {
        return when {
            bluetoothAdapter == null -> Result.failure(IllegalStateException("设备不支持蓝牙"))
            !bluetoothAdapter.isEnabled -> Result.failure(IllegalStateException("蓝牙未开启"))
            else -> Result.success(Unit)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // BLE 连接
    // ═══════════════════════════════════════════════════════════

    /**
     * 连接到指定 MAC 地址的 BLE 设备。
     *
     * 连接状态变化通过 [connectionState] 和 [events] 发射。
     */
    @SuppressLint("MissingPermission")
    fun connect(macAddress: String) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _events.tryEmit(BleEvent.Error("蓝牙不可用"))
            return
        }

        // 先断开已有连接
        disconnect()

        _connectionState.value = BleConnectionState.CONNECTING
        connectedDeviceAddress = macAddress

        val device = bluetoothAdapter.getRemoteDevice(macAddress)
        if (device == null) {
            _connectionState.value = BleConnectionState.DISCONNECTED
            _events.tryEmit(BleEvent.Error("找不到设备: $macAddress"))
            return
        }

        Log.d(TAG, "正在连接 BLE 设备: $macAddress")

        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(context, false, gattCallback)
        }
    }

    /** 断开当前 BLE 连接 */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.let { gatt ->
            Log.d(TAG, "断开 BLE 连接: ${connectedDeviceAddress}")
            gatt.close()
        }
        bluetoothGatt = null
        connectedDeviceAddress = null
        discoveredServices = emptyList()
        _connectionState.value = BleConnectionState.DISCONNECTED
    }

    /** 发现服务（通常在连接成功后自动调用） */
    @SuppressLint("MissingPermission")
    fun discoverServices() {
        val gatt = bluetoothGatt ?: return
        _connectionState.value = BleConnectionState.DISCOVERING_SERVICES
        val success = gatt.discoverServices()
        if (!success) {
            _events.tryEmit(BleEvent.Error("服务发现请求失败"))
            _connectionState.value = BleConnectionState.CONNECTED
        }
        // 结果在 onServicesDiscovered 回调中
    }

    // ═══════════════════════════════════════════════════════════
    // GATT 读写
    // ═══════════════════════════════════════════════════════════

    /**
     * 向指定特征写入数据。
     *
     * @param serviceUuid 服务 UUID
     * @param charUuid 特征 UUID
     * @param data 要写入的数据
     */
    @SuppressLint("MissingPermission")
    fun writeCharacteristic(serviceUuid: String, charUuid: String, data: ByteArray) {
        val char = findCharacteristic(serviceUuid, charUuid)
        if (char == null) {
            _events.tryEmit(BleEvent.WriteError(serviceUuid, charUuid, "特征未找到"))
            return
        }
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        char.value = data
        val success = bluetoothGatt?.writeCharacteristic(char) ?: false
        if (!success) {
            _events.tryEmit(BleEvent.WriteError(serviceUuid, charUuid, "写入请求失败"))
        }
        // 结果在 onCharacteristicWrite 回调中
    }

    /**
     * 读取指定特征。
     */
    @SuppressLint("MissingPermission")
    fun readCharacteristic(serviceUuid: String, charUuid: String) {
        val char = findCharacteristic(serviceUuid, charUuid)
        if (char == null) {
            _events.tryEmit(BleEvent.Error("特征未找到: $serviceUuid / $charUuid"))
            return
        }
        val success = bluetoothGatt?.readCharacteristic(char) ?: false
        if (!success) {
            _events.tryEmit(BleEvent.Error("读取特征请求失败"))
        }
    }

    /**
     * 启用/禁用特征通知。
     */
    @SuppressLint("MissingPermission")
    fun setCharacteristicNotification(serviceUuid: String, charUuid: String, enable: Boolean) {
        val char = findCharacteristic(serviceUuid, charUuid) ?: return
        val gatt = bluetoothGatt ?: return

        val success = gatt.setCharacteristicNotification(char, enable)
        if (!success) {
            _events.tryEmit(BleEvent.Error("设置通知失败: $charUuid"))
            return
        }

        // 写 CCCD descriptor
        val descriptor = char.getDescriptor(CCCD_UUID)
        if (descriptor != null) {
            descriptor.value = if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    /** 获取已发现的服务列表 */
    fun getDiscoveredServices(): List<BleServiceInfo> = discoveredServices

    // ═══════════════════════════════════════════════════════════
    // GATT 回调
    // ═══════════════════════════════════════════════════════════

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "连接状态变化: status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = BleConnectionState.CONNECTED
                    _events.tryEmit(BleEvent.ConnectionStateChanged(BleConnectionState.CONNECTED))
                    // 连接成功后自动发现服务
                    discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = BleConnectionState.DISCONNECTED
                    _events.tryEmit(BleEvent.ConnectionStateChanged(BleConnectionState.DISCONNECTED))
                }
                else -> {
                    val state = BleConnectionState.DISCONNECTED
                    _connectionState.value = state
                    _events.tryEmit(BleEvent.ConnectionStateChanged(state))
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "服务发现完成: status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val services = gatt.services.map { svc ->
                    BleServiceInfo(
                        uuid = svc.uuid.toString(),
                        characteristics = svc.characteristics.map { char ->
                            BleCharacteristicInfo(
                                uuid = char.uuid.toString(),
                                properties = char.properties,
                                canRead = (char.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0,
                                canWrite = (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                                        (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0,
                                canNotify = (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0,
                            )
                        }
                    )
                }
                discoveredServices = services
                _connectionState.value = BleConnectionState.SERVICES_DISCOVERED
                _events.tryEmit(BleEvent.ServicesDiscovered(services))
            } else {
                _events.tryEmit(BleEvent.Error("服务发现失败 (status=$status)"))
                _connectionState.value = BleConnectionState.CONNECTED
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "特征写入成功: ${characteristic.uuid}")
                _events.tryEmit(BleEvent.WriteSuccess(
                    serviceUuid = characteristic.service.uuid.toString(),
                    charUuid = characteristic.uuid.toString(),
                ))
            } else {
                Log.e(TAG, "特征写入失败: ${characteristic.uuid} status=$status")
                _events.tryEmit(BleEvent.WriteError(
                    serviceUuid = characteristic.service.uuid.toString(),
                    charUuid = characteristic.uuid.toString(),
                    message = "写入失败 (status=$status)",
                ))
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val data = characteristic.value ?: ByteArray(0)
                Log.d(TAG, "特征读取成功: ${characteristic.uuid}, len=${data.size}")
                _events.tryEmit(BleEvent.NotificationReceived(
                    serviceUuid = characteristic.service.uuid.toString(),
                    charUuid = characteristic.uuid.toString(),
                    data = data,
                ))
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val data = characteristic.value ?: ByteArray(0)
            Log.d(TAG, "特征通知: ${characteristic.uuid}, len=${data.size}")
            _events.tryEmit(BleEvent.NotificationReceived(
                serviceUuid = characteristic.service.uuid.toString(),
                charUuid = characteristic.uuid.toString(),
                data = data,
            ))
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            Log.d(TAG, "Descriptor 写入: ${descriptor.uuid}, status=$status")
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 内部工具
    // ═══════════════════════════════════════════════════════════

    private fun findCharacteristic(
        serviceUuid: String,
        charUuid: String,
    ): BluetoothGattCharacteristic? {
        val gatt = bluetoothGatt ?: return null
        val service = gatt.getService(UUID.fromString(serviceUuid)) ?: return null
        return service.getCharacteristic(UUID.fromString(charUuid))
    }

    /** 释放所有资源（在 Activity onDestroy 中调用） */
    fun release() {
        disconnect()
    }
}
