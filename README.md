# Project Superhuman v9.9.6 — reconstructed Android source

Reconstructed from the stable v9.9.3 packaged project so native Android work can be done without editing DEX bytecode.

## Current native structure
- `MainActivity`: WebView host + existing BLE scale bridge.
- `MainActivitx`: image/file chooser bridge used by Clinical/BP imports.
- `HealthBridge`: launcher activity + Android Health Connect sleep bridge.
- `BarcodeBridge`: native EAN/UPC image decoder using ML Kit with ZXing fallback.
- `nextgen/`: native Android sleep data, reconstruction and interpretation layers.

## Current app features
### Nutrition / Food Tracker
- Native live barcode scanner for EAN-13, EAN-8, UPC-A and UPC-E.
- Screenshot/photo barcode scanning using ML Kit with ZXing fallback.
- Camera capture and gallery/screenshot decoding use the original high-resolution image data.
- Successful barcode scans feed the existing Open Food Facts lookup and Project Superhuman UI.
- Manual barcode entry remains available.

### Hydration
- Water-focused hydration tracking.
- Fast-adjust controls with clear/reset behaviour.
- Goal percentage orb with polished central percentage + Today presentation.
- Historical water logs are persisted and shown through the calendar/history system.
- Hydration data is kept separate from nutrition/electrolyte tracking.

### Sleep / Health Connect
- Android Health Connect integration for sleep data.
- Explicit Health Connect permission flow and sleep-access handling.
- Manual `Sync now` flow with useful sync/error/no-data feedback.
- Sleep records and individual stage segments are persisted without collapsing multiple metrics into one source record.
- Sleep architecture timeline for Awake, Light, Deep and REM stages.
- Fragmented sleep reconstruction: multiple compatible Health Connect sleep blocks can form one canonical overnight episode.
- Naps are classified separately from overnight sleep where appropriate.
- Inter-session gaps are preserved as interruptions/unknown gaps rather than automatically being invented as Awake sleep.
- Raw recorded sleep data is kept separate from reconstructed and interpreted sleep.
- Canonical sleep episodes retain source record IDs, stage timelines, interruptions and confidence.
- Sleep analysis engine calculates duration, opportunity, efficiency, continuity, stage balance and recovery interpretation.
- Interpretation confidence is designed to reflect incomplete or uncertain source data.
- Personal sleep baseline foundation tracks typical duration, timing, stage mix and fragmentation for future adaptive analysis.
- Cross-module context foundation exists for future Sleep ↔ Nutrition ↔ Hydration ↔ Training ↔ Recovery analysis.

## Sleep backend architecture
The intended data pipeline is:

`Health Connect → Raw recorded data → Sleep Reconstruction → Intelligence / confidence → Personal model → Cross-module insights`

The system deliberately does **not** overwrite source data. Future improvements to reconstruction or interpretation can reprocess the original records.

Two user-facing concepts are planned and kept distinct:

1. **Recorded sleep** — what the connected source actually reported.
2. **Interpreted sleep** — Project Superhuman's calculated/reconstructed view, with confidence and explanations where appropriate.

The interpretation layer must never present an estimate as measured fact.

## Change logs
Development notes and architectural changes are kept in `docs/changelog/`. Each entry should record what changed, why it changed, important implementation decisions, and any known limitations so future development can continue without losing context.

## Build
Open this folder in Android Studio, let Gradle sync, then build the `release` APK. The existing Project Superhuman signing key is included under `signing/` so updates keep the same application identity.

## v9.9.6 live native barcode scanner
- Nutrition > Food Tracker > Camera opens a native continuous camera barcode scanner.
- Supports EAN-13, EAN-8, UPC-A and UPC-E food retail barcodes.
- Successful scans feed the existing Open Food Facts lookup automatically.
- Existing screenshot/photo ML Kit + ZXing fallback remains available as a separate option.
- Added Android CAMERA permission; scanner UI is provided by the embedded native ZXing Android activity.
