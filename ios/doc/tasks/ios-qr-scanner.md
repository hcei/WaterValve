# ios-qr-scanner

> Dependencies: `ios-core`
> Related requirement: `F2`
> Current status: `implemented with Vision + torch support and archive-validated on macOS CI; live camera behavior remains a documented runtime risk`

## Tasks

- [x] Request and handle camera permission through AVFoundation.
- [x] Show a live camera preview with `AVCaptureSession`.
- [x] Detect QR codes with `Vision` (`VNDetectBarcodesRequest`) on top of the camera stream.
- [x] Stop scanning after the first detected QR code.
- [x] Vibrate on successful detection.
- [x] Return the scanned payload back to the caller.
- [x] Show a permission or camera-availability error state with an `Open Settings` action.
- [x] Provide a flashlight toggle when the active capture device supports torch mode.
- [x] Validate that the QR scanner module participates in successful macOS archive builds and document the remaining live camera/runtime risk separately.

## Done Criteria

- [x] The project has a concrete native QR-scanning path instead of a placeholder.
- [x] Permission failure is handled in-app.
- [x] The current coding scope is complete, and the remaining live camera/runtime risk is documented.
