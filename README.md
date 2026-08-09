# Project Superhuman v9.9.6 — reconstructed Android source

Reconstructed from the stable v9.9.3 packaged project so native Android work can be done without editing DEX bytecode.

## Native structure
- `MainActivity`: WebView host + existing BLE scale bridge.
- `MainActivitx`: image/file chooser bridge used by Clinical/BP imports.
- `HealthBridge`: launcher activity + Android Health Connect sleep bridge.
- `BarcodeBridge`: new native EAN/UPC image decoder using ZXing Core.

## Barcode change
The Food Tracker's **Screenshot / photo** button now calls native Android code. The selected image is decoded outside the WebView for EAN-13, EAN-8, UPC-A and UPC-E, then only the barcode number is passed back to the existing Open Food Facts lookup and rendered in Project Superhuman's UI.

Camera uses the system camera intent and native decoding. Manual EAN/UPC remains available. The old browser BarcodeDetector stays only as a fallback when the app is run outside the reconstructed native host.

## Build
Open this folder in Android Studio, let Gradle sync, then build the `release` APK. The existing Project Superhuman signing key is included under `signing/` so updates keep the same application identity.

## v9.9.5 native barcode scanner
- Bundled ML Kit Barcode Scanning 17.3.0 (no first-run model download required).
- EAN-13, EAN-8, UPC-A and UPC-E only, matching retail food barcodes.
- Gallery/screenshots are decoded directly from the original full-resolution content URI.
- Camera capture writes a full-resolution JPEG through FileProvider; the old low-resolution Intent thumbnail path is no longer used.
- ZXing 3.5.4 remains as a secondary fallback with crop/upscale variants.
- Successful decoding is passed back to the existing Open Food Facts lookup/UI as only the barcode number.


## v9.9.6 live native barcode scanner
- Nutrition > Food Tracker > Camera now opens a native continuous camera barcode scanner rather than taking a photo first.
- Supports EAN-13, EAN-8, UPC-A and UPC-E food retail barcodes.
- Successful scans feed the existing Open Food Facts lookup automatically.
- Existing screenshot/photo ML Kit + ZXing fallback remains available as a separate option.
- Added Android CAMERA permission; scanner UI is provided by the embedded native ZXing Android activity.
