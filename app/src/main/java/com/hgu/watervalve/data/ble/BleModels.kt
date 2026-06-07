package com.hgu.watervalve.data.ble

/**
 * BLE 扫描结果。
 *
 * @param macAddress 设备 MAC 地址
 * @param name 广播名称（可能为空）
 * @param rssi 信号强度 (dBm)
 * @param scanRecord 原始广播数据（十六进制字符串，调试用）
 */
data class BleScanResult(
    val macAddress: String,
    val name: String,
    val rssi: Int,
    val scanRecord: String = "",
)

/**
 * BLE 连接状态。
 */
enum class BleConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCOVERING_SERVICES,
    SERVICES_DISCOVERED,
    DISCONNECTING,
}

/**
 * BLE GATT 服务信息。
 *
 * @param uuid 服务 UUID 字符串
 * @param characteristics 该服务下的特征列表
 */
data class BleServiceInfo(
    val uuid: String,
    val characteristics: List<BleCharacteristicInfo>,
)

/**
 * BLE GATT 特征信息。
 *
 * @param uuid 特征 UUID 字符串
 * @param properties 属性位掩码（读/写/通知等）
 * @param canRead 是否可读
 * @param canWrite 是否可写
 * @param canNotify 是否支持通知
 */
data class BleCharacteristicInfo(
    val uuid: String,
    val properties: Int,
    val canRead: Boolean,
    val canWrite: Boolean,
    val canNotify: Boolean,
)

/**
 * BLE 写入结果。
 */
sealed class BleWriteResult {
    data object Success : BleWriteResult()
    data class Error(val message: String) : BleWriteResult()
}

/**
 * BLE 事件 —— 通过 Flow 向外发送的各类事件。
 */
sealed class BleEvent {
    /** 扫描到设备 */
    data class DeviceFound(val result: BleScanResult) : BleEvent()

    /** 扫描完成（超时或手动停止） */
    data object ScanFinished : BleEvent()

    /** 连接状态变化 */
    data class ConnectionStateChanged(val state: BleConnectionState) : BleEvent()

    /** 服务已发现 */
    data class ServicesDiscovered(val services: List<BleServiceInfo>) : BleEvent()

    /** 收到特征通知数据 */
    data class NotificationReceived(val serviceUuid: String, val charUuid: String, val data: ByteArray) : BleEvent()

    /** 写入成功 */
    data class WriteSuccess(val serviceUuid: String, val charUuid: String) : BleEvent()

    /** 写入失败 */
    data class WriteError(val serviceUuid: String, val charUuid: String, val message: String) : BleEvent()

    /** 出错 */
    data class Error(val message: String, val cause: Throwable? = null) : BleEvent()
}
