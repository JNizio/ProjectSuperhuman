# Project Superhuman Next

This workspace is the migration target for the native/cross-platform Project Superhuman architecture.

## Migration rule

The current WebView Android app remains the working product while modules are migrated incrementally. The frozen `legacy-webview-final` branch is the recovery checkpoint.

## Layers

1. **Experience** — Compose UI and navigation. Platform-specific presentation where needed.
2. **Scientific** — deterministic interpretation, scoring, trends and provenance behind `ScientificEngine`.
3. **Data** — structured local storage behind `HealthRepository`; SQLite implementation arrives in Step 2.
4. **Platform adapters** — Android Health Connect/camera/Bluetooth and future iOS HealthKit/camera integrations.

## Source-set intent

- `commonMain`: health models, repository contracts, scientific contracts, use-cases and shared state.
- `androidMain`: Health Connect, Android camera/barcode/BP, Bluetooth and secure-storage adapters.
- `iosMain`: HealthKit, iOS camera and secure-storage adapters when the iOS app is introduced.

## Migration sequence

1. Architecture foundation (this step)
2. Structured database
3. Native app shell/navigation
4. Native home dashboard
5. Clinical/Body/Sleep/BP
6. Nutrition/barcodes
7. Exercise/Mindfulness
8. iOS + sync + scale

No legacy app source is removed by this workspace.
