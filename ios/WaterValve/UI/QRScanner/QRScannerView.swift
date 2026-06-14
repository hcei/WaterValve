import AVFoundation
import AudioToolbox
import SwiftUI
import UIKit
import Vision

struct QRScannerView: View {
    let onScan: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @StateObject private var viewModel = QRScannerViewModel()

    var body: some View {
        ScannerContainerView(viewModel: viewModel) { value in
            onScan(value)
            dismiss()
        }
        .ignoresSafeArea()
        .overlay(alignment: .topTrailing) {
            if viewModel.canToggleTorch, viewModel.scannerError == nil {
                Button {
                    viewModel.toggleTorch()
                } label: {
                    Image(systemName: viewModel.isTorchOn ? "flashlight.on.fill" : "flashlight.off.fill")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(14)
                        .background(.black.opacity(0.55), in: Circle())
                }
                .padding(.top, 20)
                .padding(.trailing, 20)
            }
        }
        .overlay {
            if let scannerError = viewModel.scannerError {
                VStack(spacing: 16) {
                    Image(systemName: "camera.fill")
                        .font(.system(size: 44))
                        .foregroundStyle(.white)
                    Text(scannerError)
                        .font(.headline)
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                    Button("打开设置") {
                        guard let settingsURL = URL(string: UIApplication.openSettingsURLString) else { return }
                        openURL(settingsURL)
                    }
                    .buttonStyle(.borderedProminent)
                }
                .padding(24)
                .background(.black.opacity(0.72))
            }
        }
        .overlay(alignment: .bottom) {
            if viewModel.scannerError == nil {
                VStack(spacing: 10) {
                    Text("请将二维码放入取景框内")
                        .font(.headline)
                        .foregroundStyle(.white)

                    if let scannedValue = viewModel.lastDetectedValue {
                        Text(scannedValue)
                            .font(.caption)
                            .foregroundStyle(.white.opacity(0.85))
                            .lineLimit(2)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 12)
                    } else {
                        Text("支持扫描饮水机设备二维码")
                            .font(.caption)
                            .foregroundStyle(.white.opacity(0.85))
                    }
                }
                .padding(.vertical, 20)
                .frame(maxWidth: .infinity)
                .background(.black.opacity(0.45))
            }
        }
        .navigationTitle("扫描设备")
        .navigationBarTitleDisplayMode(.inline)
    }
}

@MainActor
final class QRScannerViewModel: ObservableObject {
    @Published var isTorchOn = false
    @Published var canToggleTorch = false
    @Published var scannerError: String?
    @Published var lastDetectedValue: String?

    func toggleTorch() {
        guard canToggleTorch else { return }
        isTorchOn.toggle()
    }

    func updateTorchAvailability(_ available: Bool) {
        canToggleTorch = available
        if !available {
            isTorchOn = false
        }
    }

    func handleScannerError(_ message: String?) {
        scannerError = message
        if message != nil {
            isTorchOn = false
        }
    }

    func clearScannerError() {
        scannerError = nil
    }

    func handleDetected(_ value: String) {
        lastDetectedValue = value
    }
}

private struct ScannerContainerView: UIViewControllerRepresentable {
    @ObservedObject var viewModel: QRScannerViewModel
    let onDetected: (String) -> Void

    func makeUIViewController(context: Context) -> ScannerViewController {
        let controller = ScannerViewController()
        controller.onCodeScanned = { value in
            Task { @MainActor in
                viewModel.handleDetected(value)
                onDetected(value)
            }
        }
        controller.onFailure = { message in
            Task { @MainActor in
                viewModel.handleScannerError(message)
            }
        }
        controller.onTorchAvailabilityChanged = { available in
            Task { @MainActor in
                viewModel.updateTorchAvailability(available)
            }
        }
        return controller
    }

    func updateUIViewController(_ uiViewController: ScannerViewController, context: Context) {
        uiViewController.onCodeScanned = { value in
            Task { @MainActor in
                viewModel.handleDetected(value)
                onDetected(value)
            }
        }
        uiViewController.onFailure = { message in
            Task { @MainActor in
                viewModel.handleScannerError(message)
            }
        }
        uiViewController.onTorchAvailabilityChanged = { available in
            Task { @MainActor in
                viewModel.updateTorchAvailability(available)
            }
        }
        uiViewController.setTorch(enabled: viewModel.isTorchOn)
    }
}

private final class ScannerViewController: UIViewController, AVCaptureVideoDataOutputSampleBufferDelegate {
    var onCodeScanned: ((String) -> Void)?
    var onFailure: ((String?) -> Void)?
    var onTorchAvailabilityChanged: ((Bool) -> Void)?

    private let captureSession = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "com.hgu.watervalve.ios.scanner.session")
    private let visionQueue = DispatchQueue(label: "com.hgu.watervalve.ios.scanner.vision")
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var videoOutput: AVCaptureVideoDataOutput?
    private var captureDevice: AVCaptureDevice?
    private var hasScannedCode = false
    private var isSessionConfigured = false
    private var isProcessingFrame = false
    private var pendingTorchEnabled = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        configureSessionIfAuthorized()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        sessionQueue.async { [weak self] in
            guard let self, self.isSessionConfigured, !self.captureSession.isRunning else { return }
            self.hasScannedCode = false
            self.captureSession.startRunning()
            self.applyTorchState()
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        sessionQueue.async { [weak self] in
            guard let self, self.captureSession.isRunning else { return }
            self.captureSession.stopRunning()
            self.setTorchMode(enabled: false)
        }
    }

    func setTorch(enabled: Bool) {
        pendingTorchEnabled = enabled
        sessionQueue.async { [weak self] in
            self?.applyTorchState()
        }
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard !hasScannedCode, !isProcessingFrame else { return }
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }

        isProcessingFrame = true

        let request = VNDetectBarcodesRequest { [weak self] request, error in
            guard let self else { return }
            defer { self.isProcessingFrame = false }

            if error != nil {
                return
            }

            guard !self.hasScannedCode,
                  let observation = (request.results as? [VNBarcodeObservation])?.first(where: { $0.symbology == .qr }),
                  let payload = observation.payloadStringValue,
                  !payload.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                return
            }

            self.hasScannedCode = true
            AudioServicesPlaySystemSound(SystemSoundID(kSystemSoundID_Vibrate))
            self.sessionQueue.async {
                if self.captureSession.isRunning {
                    self.captureSession.stopRunning()
                }
            }
            DispatchQueue.main.async {
                self.onCodeScanned?(payload)
            }
        }
        request.symbologies = [.qr]

        let orientation = exifOrientation(for: UIDevice.current.orientation)

        visionQueue.async {
            let handler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, orientation: orientation, options: [:])
            do {
                try handler.perform([request])
            } catch {
                self.isProcessingFrame = false
            }
        }
    }

    private func configureSessionIfAuthorized() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configureSession()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    guard let self else { return }
                    if granted {
                        self.configureSession()
                    } else {
                        self.onFailure?("未获得相机权限，请前往“设置”中允许相机访问后再扫描设备二维码。")
                    }
                }
            }
        case .denied, .restricted:
            onFailure?("当前设备无法使用相机。请前往“设置”中允许相机访问后再扫描设备二维码。")
        @unknown default:
            onFailure?("当前设备暂时无法使用相机。")
        }
    }

    private func configureSession() {
        sessionQueue.async { [weak self] in
            guard let self else { return }
            guard let videoCaptureDevice = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
                DispatchQueue.main.async {
                    self.onFailure?("未找到可用的后置摄像头。")
                }
                return
            }

            self.captureDevice = videoCaptureDevice
            self.captureSession.beginConfiguration()
            self.captureSession.sessionPreset = .high

            guard let videoInput = try? AVCaptureDeviceInput(device: videoCaptureDevice),
                  self.captureSession.canAddInput(videoInput) else {
                self.captureSession.commitConfiguration()
                DispatchQueue.main.async {
                    self.onFailure?("无法初始化相机输入。")
                }
                return
            }

            self.captureSession.addInput(videoInput)

            let videoOutput = AVCaptureVideoDataOutput()
            videoOutput.alwaysDiscardsLateVideoFrames = true
            videoOutput.videoSettings = [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA
            ]
            videoOutput.setSampleBufferDelegate(self, queue: self.visionQueue)

            guard self.captureSession.canAddOutput(videoOutput) else {
                self.captureSession.commitConfiguration()
                DispatchQueue.main.async {
                    self.onFailure?("无法初始化二维码识别输出。")
                }
                return
            }

            self.captureSession.addOutput(videoOutput)
            if let connection = videoOutput.connection(with: .video), connection.isVideoOrientationSupported {
                connection.videoOrientation = .portrait
            }
            self.videoOutput = videoOutput
            self.captureSession.commitConfiguration()

            DispatchQueue.main.async {
                let previewLayer = AVCaptureVideoPreviewLayer(session: self.captureSession)
                previewLayer.videoGravity = .resizeAspectFill
                previewLayer.frame = self.view.layer.bounds
                self.view.layer.insertSublayer(previewLayer, at: 0)
                self.previewLayer = previewLayer
                self.isSessionConfigured = true
                self.onTorchAvailabilityChanged?(videoCaptureDevice.hasTorch)
                self.onFailure?(nil)
            }

            self.captureSession.startRunning()
            self.applyTorchState()
        }
    }

    private func applyTorchState() {
        setTorchMode(enabled: pendingTorchEnabled)
    }

    private func setTorchMode(enabled: Bool) {
        guard let device = captureDevice, device.hasTorch else { return }

        do {
            try device.lockForConfiguration()
            if enabled, device.isTorchModeSupported(.on) {
                try device.setTorchModeOn(level: AVCaptureDevice.maxAvailableTorchLevel)
            } else if device.isTorchModeSupported(.off) {
                device.torchMode = .off
            }
            device.unlockForConfiguration()
        } catch {
            DispatchQueue.main.async {
                self.onFailure?("无法切换手电筒，请重试。")
            }
        }
    }

    private func exifOrientation(for orientation: UIDeviceOrientation) -> CGImagePropertyOrientation {
        switch orientation {
        case .landscapeLeft:
            return .up
        case .landscapeRight:
            return .down
        case .portraitUpsideDown:
            return .left
        default:
            return .right
        }
    }
}
