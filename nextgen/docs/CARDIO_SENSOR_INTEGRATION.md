# Cardio Sensor & Health Connect Integration

Branch: `cardio-sensors-healthconnect`  
Base: `11.3`

## Scope

This layer makes Cardio sensor-aware without taking ownership of Cardio ViewModel, foreground-service, history, analytics, or persistence architecture. Timer-only Cardio remains valid when no sensor is selected or when a sensor fails.

Supported sources:

- Existing H19C / Da Fit-family runtime via `H19cWearableRuntime.state`
- Bluetooth SIG Heart Rate Service (0x180D / 0x2A37)
- Health Connect `ExerciseSessionRecord` workout imports
- Health Connect workout-scoped heart rate, distance, speed, elevation, cadence, and active calories when permission/data are available

GPS/routes are intentionally not implemented on this branch.

## Architecture

### Core models

`CardioSensorModels.kt` owns platform-neutral sensor contracts and HR processing:

- `CardioSensorProvider`
- `CardioSensorState`
- `CardioSensorProvenance`
- `CardioHeartRateSample`
- `CardioSessionHeartRateCollector`
- `CardioHeartRateProcessor`
- `CardioHeartRateTimeline`
- configurable `CardioHrZoneScheme`

A provider publishes connection state through `StateFlow` and timestamped HR through `SharedFlow`. Cardio UI/ViewModels never need to depend directly on H19C or Android GATT.

### Runtime integration seam

`CardioSensorRuntime` is the current bridge for the monolithic Cardio screen and the intended integration seam for the Cardio ViewModel refactor.

It owns only sensor selection and session telemetry. It does **not** save workouts.

Important state:

- `CardioSensorRuntime.state`
- `CardioSensorRuntime.liveMetrics`
- `CardioSensorRuntime.bleDevices`

Sensor failure changes telemetry state only. It does not stop the workout.

## H19C adapter

`H19cCardioSensorProvider` observes the existing `H19cWearableRuntime.state`.

No second H19C GATT implementation was created. The adapter:

1. Reuses existing scan/reconnect/disconnect behavior.
2. Enables H19C live HR only for an active Cardio session when needed.
3. Emits one `CardioHeartRateSample` per new HR timestamp.
4. Preserves device name/manufacturer/model provenance.
5. Hashes the BLE address before it can enter Cardio session metadata.
6. Stops live-HR mode on session stop only if Cardio enabled it.

`H19cWearableState` now exposes `lastHeartRateEpochMs`. This is intentionally distinct from `lastSyncEpochMs`; a steps or SpO2 refresh must not make an old HR reading look fresh.

## Standard BLE Heart Rate

`AndroidBleHeartRateClient` implements the Bluetooth SIG Heart Rate Service:

- Service: `0x180D`
- Heart Rate Measurement: `0x2A37`
- CCCD notifications: `0x2902`
- 8-bit and 16-bit HR payloads
- scan, connect, disconnect, saved-device reconnect
- bounded automatic reconnect attempts
- explicit scanning/connecting/reconnecting/disconnected/error events

`GenericBleHeartRateProvider` adapts the transport to `CardioSensorProvider`.

Raw BLE addresses are only retained in app-private preferences because Android needs them for reconnect. Cardio provenance stores a SHA-256-derived anonymous sensor ID instead.

### Permission API

The existing app manifest already had Bluetooth permissions. UI/ViewModel code can query:

```kotlin
CardioSensorRuntime.requiredPermissions()
CardioSensorRuntime.hasRequiredPermissions(context)
```

The caller remains responsible for launching the Android runtime permission request.

## Live HR validity and staleness

Stored/accepted HR range is `20..260 bpm`, matching the pre-existing H19C validation boundary.

A current HR value is considered stale after **10 seconds** without a new sample.

States are explicit:

- `NO_SENSOR`
- `SCANNING`
- `CONNECTING`
- `CONNECTED`
- `STALE`
- `RECONNECTING`
- `DISCONNECTED`
- `ERROR`

A stale HR value remains available as historical evidence inside the session sample list, but `CardioLiveSensorMetrics.currentHeartRateBpm` becomes null so UI cannot present it as live.

## Session HR coverage

Coverage is measured against **active session windows**, not simply start-to-end wall time. The runtime exposes pause/resume calls so paused workout time is excluded.

Each measured sample represents signal only until the earlier of:

- the next measured sample,
- 10 seconds after the sample,
- the end of the active window.

A gap longer than 10 seconds therefore creates unclassified time rather than extending the last HR indefinitely.

`heartRateCoveragePct = measured signal time / active session time * 100`.

Partial coverage remains partial. Dropout time is never assigned to a zone.

## HR statistics

`CardioHeartRateProcessor` calculates from accepted measured samples only:

- average BPM
- minimum BPM
- maximum BPM
- latest BPM
- sample count
- last-sample timestamp
- coverage
- zone seconds
- unclassified seconds
- current zone
- downsampled HR timeline

Exact duplicate samples are removed.

## HR zones

Zones are driven by `CardioHrZoneScheme`.

The current default is `neutral-five-band-v1`, a neutral storage/display bucket scheme:

- Band 1: 20–99
- Band 2: 100–119
- Band 3: 120–139
- Band 4: 140–159
- Band 5: 160–260

These are **not** presented as medical or prescriptive training zones. Engineer-owned settings can replace the scheme at runtime:

```kotlin
CardioSensorRuntime.setZoneScheme(customScheme)
```

Zone time is reconstructed only from covered measured segments. Uncovered/dropout time remains `heartRateUnclassifiedSeconds`.

## HR timeline retention

The persisted session seam stores a compact timeline in `heartRateTimeline`.

Rules:

1. Invalid and exact duplicate samples are removed.
2. Up to 720 points are retained directly.
3. Longer sessions are bucketed.
4. Each bucket preserves temporal low/high points.
5. First and last points are preserved.
6. Every stored point includes timestamp, BPM, provider type, and source package where applicable.

This preserves useful interval/spike/drift shape without permanently storing every high-frequency BLE notification.

## Sensor provenance

`CardioSensorProvenance` standardises:

- provider type
- source name
- transport: live BLE vs Health Connect import
- device name
- manufacturer/model where available
- source package
- anonymous sensor ID
- external record ID where applicable

The persistence adapter writes these fields into the existing `cardio_session` metadata rather than introducing a competing workout store.

## Current NativeCardio integration

The existing screen receives sensor state through `CardioSensorRuntime`. The old hard-coded `NO SENSOR` panel now displays:

- source and connection state
- fresh current HR
- average HR
- max HR
- current zone
- HR coverage
- last-reading age
- reconnect/stale/error feedback

`CardioSensorPersistence.kt` merges measured evidence into the existing `HealthValue` row. Explicit user-entered avg/max/zone fields win; sensor quality/provenance/timeline fields are retained alongside them.

## Exact calls for Engineer 1

After the ViewModel/lifecycle branch is merged, replace direct calls in `NativeCardio.kt` with the ViewModel at these lifecycle points.

### App/screen setup

```kotlin
CardioSensorRuntime.initialize(context)
val sensorState: StateFlow<CardioSensorState> = CardioSensorRuntime.state
val liveMetrics: StateFlow<CardioLiveSensorMetrics> = CardioSensorRuntime.liveMetrics
val bleDevices: StateFlow<List<BleHeartRateDevice>> = CardioSensorRuntime.bleDevices
```

### Restore preferred source

From a coroutine:

```kotlin
CardioSensorRuntime.reconnectPreferred()
```

### Select source

From a coroutine:

```kotlin
CardioSensorRuntime.selectNone()
CardioSensorRuntime.selectH19c(connect = true)
CardioSensorRuntime.selectBle(connectPreferred = true)
CardioSensorRuntime.scanBle()
CardioSensorRuntime.connectBle(sensorId)
CardioSensorRuntime.disconnectSelected()
```

### Start workout

```kotlin
CardioSensorRuntime.startSession(sessionId, startedAtEpochMs)
```

### Pause / resume active workout

```kotlin
CardioSensorRuntime.pauseSession(pausedAtEpochMs)
CardioSensorRuntime.resumeSession(resumedAtEpochMs)
```

### Live snapshot

```kotlin
val summary = CardioSensorRuntime.snapshot(nowEpochMs)
```

### Final stop

```kotlin
val summary: CardioHeartRateSummary =
    CardioSensorRuntime.stopSession(endedAtEpochMs)
```

Persist `summary.toMetadata()`, or if still using the current `HealthValue` Cardio row:

```kotlin
val value = cardioHealthValue.withCardioHeartRateSummary(summary)
```

Do not stop or delete the active workout merely because `CardioSensorState.connection` becomes stale/disconnected/error.

## Sensor picker backend

The picker can render:

- No sensor
- H19C
- discovered generic BLE HR devices

The preferred provider is stored in `superhuman_cardio_sensors`. Generic BLE reconnect information is stored in app-private `superhuman_cardio_ble_hr`. H19C continues to use its existing saved-device preferences.

The picker should request `CardioSensorRuntime.requiredPermissions()` before initiating scan/connect.

## Health Connect Cardio import

`CardioHealthConnect.sync(context)` reads the last 30 days of `ExerciseSessionRecord` records and imports appropriate workouts into the existing `cardio_session` metric.

The importer is vendor-neutral. It does not require Samsung Health. Every imported workout preserves its Health Connect data origin package.

Explicit Strength, weightlifting, stretching, yoga, Pilates, and guided-breathing sessions are rejected from Cardio.

Supported Cardio mappings include:

- walking
- running
- treadmill running
- biking
- stationary biking
- rowing / rowing machine
- elliptical
- stair climbing / stair machine
- pool/open-water swimming
- hiking
- HIIT
- generic/unknown workouts safely mapped to General Cardio while preserving the original exercise type

## Workout-scoped Health Connect metrics

Associated data is queried only inside each workout's start/end range and filtered to the workout's Health Connect origin where supported.

Imported when permission/data exist:

- raw heart-rate samples and derived avg/min/max/coverage/zones/timeline
- distance
- average and max speed
- elevation gained
- cycling RPM cadence
- walking/running step cadence
- active calories

No whole-day summary is attached to a workout.

## Health Connect deduplication

The stable key is:

`hc-cardio:<source package>:<Health Connect record id>`

Each imported row also stores `healthConnectLastModifiedEpochMs`.

Sync behavior:

- unseen stable key -> imported
- same stable key + same last-modified timestamp -> deduplicated/no write
- same stable key + changed last-modified timestamp -> existing row replaced/updated

Timestamp + duration is not used as the primary identity.

## Cardio sync result

Observe:

```kotlin
CardioHealthConnect.syncState
```

or use the return value from `sync()`.

`CardioHealthConnectSyncResult` exposes:

- success
- imported
- updated
- deduplicated
- rejected
- providers
- lastSyncEpochMs
- message

`GlobalHealthConnect` now includes Cardio permissions and Cardio workout sync.

## Added Health Connect permissions

The manifest now declares:

- `android.permission.health.READ_EXERCISE`
- `android.permission.health.READ_DISTANCE`
- `android.permission.health.READ_SPEED`
- `android.permission.health.READ_ELEVATION_GAINED`

Existing permissions already cover:

- `READ_HEART_RATE`
- `READ_STEPS`
- `READ_ACTIVE_CALORIES_BURNED`

Cycling cadence is covered by Health Connect's exercise permission.

## Optional write-back

Write-back is intentionally **not implemented** on this branch. Read/import correctness has priority and product permission decisions have not been established.

A future writer should:

1. Request the corresponding write permissions for exercise plus every metric being written.
2. Write an `ExerciseSessionRecord` using Project Superhuman start/end/activity.
3. Write HR as bounded series records rather than one huge series.
4. Use a stable `Metadata.clientRecordId` such as `project-superhuman-cardio:<sessionId>`.
5. Increment `clientRecordVersion` when replacing a previously exported session.
6. Store the resulting Health Connect record IDs in Cardio provenance.
7. Never re-import the app's own exported record as a duplicate local workout.

## Known limitations

- This branch does not implement GPS, routes, maps, or location foreground services.
- Generic BLE discovery intentionally targets devices advertising the standard Heart Rate service.
- The current Cardio screen exposes the sensor readout but not a finished visual sensor picker; the complete picker should be added by the UI-owning branch using the API above.
- Runtime Android permission prompts remain owned by the screen/ViewModel; this branch exposes exact required permissions and actionable states.
- Session HR samples are buffered in memory until the Cardio persistence layer saves the final summary. Crash-safe live workout persistence belongs to the Cardio lifecycle/persistence refactor; the integration seam is `snapshot()` + `toMetadata()`.
- Health Connect access is bounded by the permissions/history Health Connect grants to the app.
- Health Connect metrics absent from a provider stay null; no value is fabricated.
- Health Connect workout active/pause intervals are not reconstructed from exercise segments on this branch, so imported coverage uses the session start/end interval.
- Unknown Health Connect exercise types are retained as the original integer in metadata and represented as General Cardio rather than discarded.

## Tests

`CardioSensorProcessingTest.kt` is hardware-free and covers:

1. BLE 8-bit HR packet
2. BLE 16-bit HR packet
3. invalid HR rejection
4. avg/min/max
5. zone allocation
6. sensor gaps
7. coverage
8. stale sample state
9. reconnect state
10. H19C mapping
11. provenance hashing
12. Health Connect activity mapping
13. Health Connect stable dedupe
14. HR window boundaries
15. missing metrics/no fabrication
16. unknown activity
17. timeline downsampling
18. duplicate samples
19. session stop closes collection
20. sensor disconnect during active workout
21. paused-time coverage
22. stopped collector rejects late samples
