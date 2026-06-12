import AVFoundation
import AudioToolbox
import SwiftUI
import UIKit

struct QRScannerView: View {
    let onScan: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var scannedValue: String?
    @State private var scannerError: String?

    var body: some View {
        ScannerContainerView(scannedValue: $scannedValue, scannerError: $scannerError)
            .ignoresSafeArea()
            .overlay {
                if let scannerError {
                    VStack(spacing: 16) {
                        Image(systemName: "camera.fill")
                            .font(.system(size: 44))
                            .foregroundStyle(.white)
                        Text(scannerError)
                            .font(.headline)
                            .foregroundStyle(.white)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)
                        Button("Open Settings") {
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
                if scannerError == nil {
                    VStack(spacing: 10) {
                        Text("Align the QR code inside the frame.")
                            .font(.headline)
                            .foregroundStyle(.white)

                        if let scannedValue {
                            Text(scannedValue)
                                .font(.caption)
                                .foregroundStyle(.white.opacity(0.85))
                                .lineLimit(2)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 12)
                        }
                    }
                    .padding(.vertical, 20)
                    .frame(maxWidth: .infinity)
                    .background(.black.opacity(0.45))
                }
            }
            .navigationTitle("Scan Device")
            .navigationBarTitleDisplayMode(.inline)
            .onChange(of: scannedValue) { newValue in
                guard let newValue, !newValue.isEmpty else { return }
                onScan(newValue)
                dismiss()
            }
    }
}

private struct ScannerContainerView: UIViewControllerRepresentable {
    @Binding var scannedValue: String?
    @Binding var scannerError: String?

    func makeUIViewController(context: Context) -> ScannerViewController {
        let controller = ScannerViewController()
        controller.onCodeScanned = { value in
            scannedValue = value
        }
        controller.onFailure = { message in
            scannerError = message
        }
        return controller
    }

    func updateUIViewController(_ uiViewController: ScannerViewController, context: Context) {
        uiViewController.onCodeScanned = { value in
            scannedValue = value
        }
        uiViewController.onFailure = { message in
            scannerError = message
        }
    }
}

private final class ScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onCodeScanned: ((String) -> Void)?
    var onFailure: ((String) -> Void)?

    private let captureSession = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var hasScannedCode = false
    private var isSessionConfigured = false

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
        if isSessionConfigured, !captureSession.isRunning {
            hasScannedCode = false
            captureSession.startRunning()
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if captureSession.isRunning {
            captureSession.stopRunning()
        }
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard !hasScannedCode,
              let metadataObject = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let value = metadataObject.stringValue else {
            return
        }

        hasScannedCode = true
        AudioServicesPlaySystemSound(SystemSoundID(kSystemSoundID_Vibrate))
        captureSession.stopRunning()
        onCodeScanned?(value)
    }

    private func configureSession() {
        guard let videoCaptureDevice = AVCaptureDevice.default(for: .video) else { return }
        guard let videoInput = try? AVCaptureDeviceInput(device: videoCaptureDevice) else { return }

        if captureSession.canAddInput(videoInput) {
            captureSession.addInput(videoInput)
        } else {
            return
        }

        let metadataOutput = AVCaptureMetadataOutput()
        if captureSession.canAddOutput(metadataOutput) {
            captureSession.addOutput(metadataOutput)
            metadataOutput.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
            metadataOutput.metadataObjectTypes = [.qr]
        } else {
            return
        }

        let previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
        previewLayer.videoGravity = .resizeAspectFill
        previewLayer.frame = view.layer.bounds
        view.layer.addSublayer(previewLayer)
        self.previewLayer = previewLayer
        isSessionConfigured = true
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
                        self.captureSession.startRunning()
                    } else {
                        self.onFailure?("Camera permission was denied. Enable camera access in Settings to scan device QR codes.")
                    }
                }
            }
        case .denied, .restricted:
            onFailure?("Camera access is unavailable. Enable camera access in Settings to scan device QR codes.")
        @unknown default:
            onFailure?("Camera access is unavailable on this device.")
        }
    }
}
