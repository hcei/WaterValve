# ios-qr-scanner

> Dependencies: `ios-core`
> Related requirement: `F2`
> Current status: `implemented, not runtime-verified`

## Tasks

- [x] Request and handle camera permission through AVFoundation.
- [x] Show a live camera preview with `AVCaptureSession`.
- [x] Detect QR codes with `AVCaptureMetadataOutput`.
- [x] Stop scanning after the first detected QR code.
- [x] Vibrate on successful detection.
- [x] Return the scanned payload back to the caller.
- [x] Show a permission or camera-availability error state with an `Open Settings` action.
- [ ] Verify scanning reliability and camera-permission flows on a real iPhone.

## Done Criteria

- [x] The project has a concrete native QR-scanning path instead of a placeholder.
- [x] Permission failure is handled in-app.
- [ ] Real-device testing confirms reliable scanning behavior and camera lifecycle handling.
