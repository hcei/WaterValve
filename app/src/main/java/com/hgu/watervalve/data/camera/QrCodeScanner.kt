package com.hgu.watervalve.data.camera

import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * QR 码 / 条形码扫描器。
 *
 * 基于 CameraX + ML Kit Barcode Scanning。
 * 扫码结果通过 [onScanned] 回调返回。
 *
 * ## 用法
 * ```kotlin
 * val scanner = QrCodeScanner(previewView, lifecycleOwner) { result ->
 *     // 处理扫描结果
 * }
 * scanner.start()
 * // ...
 * scanner.stop()
 * ```
 *
 * @param previewView CameraX PreviewView
 * @param lifecycleOwner 生命周期所有者（通常是 Fragment 或 Activity）
 * @param onScanned 扫码成功回调
 */
class QrCodeScanner(
    private val previewView: PreviewView,
    private val lifecycleOwner: LifecycleOwner,
    private val onScanned: (QrScanResult) -> Unit,
) {
    companion object {
        private const val TAG = "QrCodeScanner"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /** 启动相机和扫码分析 */
    fun start() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "CameraX 初始化失败: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(previewView.context))
    }

    /** 停止并释放资源 */
    fun stop() {
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {}
        cameraProvider = null
        cameraExecutor.shutdown()
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return

        // 预览
        val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        // 图像分析（扫码）
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    try {
                        @androidx.camera.core.ExperimentalGetImage
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees,
                            )
                            val scanner = BarcodeScanning.getClient()
                            scanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        val rawValue = barcode.rawValue
                                        if (!rawValue.isNullOrBlank()) {
                                            val result = QrScanResult(
                                                content = rawValue,
                                                format = barcode.format,
                                                formatName = formatName(barcode.format),
                                            )
                                            Log.d(TAG, "扫码成功: $rawValue")
                                            onScanned(result)
                                            // 扫码成功后停止分析，避免重复回调
                                            analysis.clearAnalyzer()
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.w(TAG, "条码识别失败: ${e.message}")
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "ImageAnalysis 异常: ${e.message}")
                        try { imageProxy.close() } catch (_: Exception) {}
                    }
                }
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis,
            )
        } catch (e: Exception) {
            Log.e(TAG, "CameraX bindToLifecycle 失败: ${e.message}", e)
        }
    }

    private fun formatName(format: Int): String = when (format) {
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE -> "QR_CODE"
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_AZTEC -> "AZTEC"
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128 -> "CODE_128"
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_39 -> "CODE_39"
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_93 -> "CODE_93"
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODABAR -> "CODABAR"
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13 -> "EAN_13"
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8 -> "EAN_8"
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ITF -> "ITF"
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_PDF417 -> "PDF417"
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_A -> "UPC_A"
        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_E -> "UPC_E"
        else -> "UNKNOWN"
    }
}

/**
 * 扫码结果。
 *
 * @param content 条码内容（URL、设备 ID 等）
 * @param format ML Kit 条码格式常量
 * @param formatName 条码格式可读名称
 */
data class QrScanResult(
    val content: String,
    val format: Int,
    val formatName: String,
)
