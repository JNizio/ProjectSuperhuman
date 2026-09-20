# Cardio Live Sensors / GPS Integration

Branch: `cardio-nof1-live-sensors`

This document describes the live Cardio execution layer added on top of the separated Cardio session architecture. It intentionally does not own Smart Devices pairing/settings, longitudinal analytics, or the Cardio Overview.

## Ownership and lifecycle

The canonical workout lifecycle remains:

- `CardioSessionCoordinator`
- `CardioLiveSessionController`
- `CardioSessionStore`
- `DataVaultCardioRepository`

Live sensor/GPS code never creates an alternate workout save path. A completion is frozen through the coordinator, the completed workout is saved first, and the live draft is cleared only after the core save succeeds. Raw telemetry is then published to the Data Vault. A telemetry failure does not delete an already-saved workout.

`CardioSessionService` owns screen-off/background auto-pause reactions and foreground execution. `CardioViewModel` owns screen-facing actions and delegates lifecycle mutations to the same coordinator.

## Public integration seams

### `CardioSensorRuntime`

Provides:

- selected live sensor state;
- fresh current HR (stale values become null);
- average/min/max HR;
- current zone;
- HR time coverage;
- accepted HR percentage and suspect count;
- RR valid-beat percentage;
- RR coverage;
- session RMSSD when enough true RR evidence exists;
- raw HR and true RR samples for final persistence.

BLE RR values come from the Bluetooth SIG Heart Rate Measurement characteristic. RR is never reconstructed from BPM.

### `CardioGpsRuntime`

Provides:

- `metrics: StateFlow<CardioLiveMovementMetrics>`;
- outdoor GPS collection for running, walking, hiking and cycling;
- raw fixes plus a separately cleaned route;
- distance;
- moving time;
- current speed / pace;
- moving pace;
- elevation gain when altitude evidence exists;
- GPS quality and fix age;
- manual laps;
- distance auto-laps;
- optional auto-pause;
- structured-workout progress;
- final `CardioLiveTelemetrySnapshot`.

Important callable APIs:

- `startSession(...)`
- `restoreSession(...)`
- `pause(...)`
- `resume(...)`
- `tick(...)`
- `manualLap(...)`
- `setAutoPauseEnabled(...)`
- `setStructuredWorkout(...)`
- `snapshot(...)`
- `stop(...)`
- `enrichSession(...)`

Auto-pause is disabled by default.

### Structured workout domain

`CardioStructuredWorkout` contains steps and repeat blocks. A step supports:

- warm-up;
- work;
- recovery;
- cooldown;
- fixed time;
- fixed distance;
- HR-zone target;
- pace target;
- open target.

The execution engine exposes the current step, upcoming step, remaining time/distance and target adherence. Time-based steps preserve elapsed evidence across long background gaps and can advance through multiple completed steps when execution resumes.

## Quality semantics

### HR

Raw HR observations are retained. Quality classification is separate from live display smoothing and currently marks:

- invalid physiology range;
- stale delivery;
- observations adjacent to a data gap;
- transient implausible spikes;
- likely cadence lock when real cadence evidence is supplied.

No classified observation is silently rewritten into a different HR value.

### RR

Raw true RR observations are retained with:

- physiological timestamp;
- received timestamp;
- sensor provenance;
- quality;
- correction state.

RR outside 250–2500 ms is invalid. Abrupt interval changes are classified as suspect artifacts. RMSSD is derived only from accepted true RR observations and is explicitly stored as a derived, non-diagnostic training-context metric.

### GPS

Raw GPS fixes are retained separately from the cleaned route. A fix can be rejected/classified for:

- invalid coordinates;
- poor horizontal accuracy (>60 m);
- stale delivery (>15 s);
- non-increasing timestamp;
- implausible sport-specific speed/jump.

A GPS gap longer than 30 seconds is represented as `GAP_ADJACENT`. The recovered point is retained as the start of a new clean segment, but the app does not draw distance across the missing interval.

Current quality labels are intentionally coarse:

- `GPS good`
- `GPS weak`
- `Location unavailable`

## Time concepts

The system keeps these concepts distinct:

- elapsed wall time: core session lifecycle;
- active time: core session lifecycle;
- paused time: core session lifecycle;
- moving time: accepted GPS segments above the sport-specific movement threshold;
- manual pause interval: telemetry event with `MANUAL` origin;
- auto-pause interval: telemetry event with `AUTO` origin.

Stopped time is never silently removed from the underlying workout timing.

## Laps

`CardioLiveLapTracker` records:

- index;
- start/end timestamps;
- duration;
- distance;
- average/max HR when available;
- provenance;
- exact-distance flag.

Distance auto-laps are exact route-distance boundaries. Manual laps are not promoted to exact-distance PR evidence merely because they have an observed distance.

Defaults:

- running/walking/hiking: 1 km;
- cycling: 5 km.

## Data Vault metrics

The live layer uses the canonical Data Vault path.

Raw / event metrics:

- `cardio_hr_sample_bpm`
- `cardio_rr_interval_ms`
- `cardio_gps_fix`
- `cardio_lap_distance_m`
- `cardio_pause_duration_s`

Derived session metric:

- `cardio_session_hrv_rmssd_ms`

GPS coordinates, accuracy, altitude/speed when available, source kind and quality are attached as provenance metadata to the raw GPS-fix event row.

No database/session schema migration is required by this branch. The only registry migration is the addition of the four new Cardio telemetry metric definitions; existing session schema versions remain unchanged.

## Android permissions / foreground service

Existing fine/coarse location permissions are reused. This branch adds:

- `android.permission.FOREGROUND_SERVICE_LOCATION`;
- `location|specialUse` foreground service declaration.

At runtime the service selects only the foreground types it currently needs. Outdoor Cardio requests fine location at workout start. Indoor/timer-only Cardio remains usable without location permission.

## Failure behavior

Core-save failure:

- live draft remains recoverable;
- sensor/GPS runtime is paused rather than discarded;
- retry remains possible.

Core-save success + telemetry failure:

- completed workout remains saved;
- failure is surfaced as a partial telemetry write problem;
- raw telemetry is never treated as a reason to delete the successful workout.

## Deliberately not owned here

This branch does not add:

- Cardio-specific device pairing/forgetting/settings;
- a replacement source-arbitration system;
- longitudinal fitness/trend dashboards;
- experiments/correlation;
- Strength changes;
- fabricated rowing/swimming distance;
- reconstructed RR from BPM;
- diagnostic HRV interpretation.

Smart Devices remains the device-management authority.

## Hardware/device validation still needed

Physical-device validation remains necessary for:

- H19C disconnect/reconnect behavior over long workouts;
- generic BLE straps with multiple RR values per packet;
- Android background GPS on OEM battery-management variants;
- permission revocation/restoration while the service is active;
- GPS quality thresholds in dense urban / tree-cover conditions;
- future cycling cadence/power sources;
- swimming/rowing distance sensors;
- optional haptic/audio structured-workout cues.
