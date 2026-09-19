# Cardio Consolidation

## Source branches

This branch consolidates the three independent Cardio workstreams created from `11.3`
(`8bde7df85fa4412b2b0ac3e40f8ac0f223a195fe`):

- Core reliability: `cardio-core-reliability` @ `3d499c811846e5713c69ae67baac02dd3b878928`
- Sensors / Health Connect: `cardio-sensors-healthconnect` @ `5d01794ad240024d44807e88f9550092843bebc1`
- Analytics / UX: `cardio-analytics-ux` @ `fd04020b313eb792a715e6118acd1454660099eb`

All three source branches were still at those tips when consolidation was performed.

## Integration order

1. Core reliability is authoritative for session identity, timing, persistence, foreground service,
   ViewModel state and Data Vault writes.
2. Sensor/Health Connect code is layered onto that lifecycle through `CardioSensorRuntime`.
3. Analytics/history/accessibility code is layered onto the resulting saved-session surface.

The two feature branches were not force-merged because both modified `NativeCardio.kt` from the
old monolithic architecture. Their independent files were retained and the conflicting integration
points were reconciled manually.

## Core + sensor reconciliation

The consolidated lifecycle uses the core branch's stable UUID from workout start.

Sensor collection is attached to the same lifecycle:

- ViewModel start -> `CardioSensorRuntime.startSession(sessionId, startedAt)`
- pause -> sensor active window pauses
- resume -> sensor active window resumes
- detailed finish -> sensor collection is paused and snapshotted
- quick save -> the frozen core session is decorated with the measured sensor summary before the
  verified repository write
- successful save -> sensor collector stops
- failed save -> the durable live draft remains intact
- discard -> sensor collector stops without creating a completed session

`CardioSessionCoordinator.quickSave` now exposes a defaulted session-transform seam so sensor
evidence can be attached before the atomic/idempotent core write without moving sensor ownership
into the coordinator.

`CardioSensorPersistence.kt` now contains both the original HealthValue adapter and a
`CardioSession.withCardioHeartRateSummary` adapter for the refactored architecture.

Sensor quality/provenance/timeline fields are carried through `CardioSession.extensions`.
`CardioPersistenceMapper` also preserves legacy/direct Health Connect sensor metadata when
loading imported sessions.

## Health Connect reconciliation

The consolidated Android manifest preserves the core foreground-service declarations and adds the
sensor branch's cardio read permissions.

`GlobalHealthConnect` includes `CardioHealthConnect.permissions` and Cardio workout sync.

The Health Connect importer remains vendor-neutral and preserves stable external record identity.

## Core + analytics reconciliation

The core ViewModel/repository remains the source of saved sessions and archive paging.

The analytics layer adds:

- common analysis ranges
- daily/weekly/monthly trend aggregation
- activity-specific trend metrics
- verified PR logic
- exact-distance PR locking until real lap/split timing exists
- comparable-session analysis
- load quality / baseline quality
- measured intensity distribution
- consistency metrics
- optional goal models
- conditional aerobic decoupling
- metric/imperial display conversion
- locale-aware decimal parsing
- native date/time picker
- accessible Compose charts
- filtered/paged history UI

History retains the core repository's `loadMoreHistory()` seam outside the analytics panel so the
UI can extend beyond the initial bounded Data Vault page without loading the entire archive.

## NativeCardio integration

`NativeCardio.kt` remains a UI shell over the core ViewModel.

Consolidated additions include:

- live sensor current/average/max HR and freshness/coverage state
- measured HR values prefilled into the detailed finish form
- analytics history panel
- previous comparable-session panel
- longitudinal progress panel
- verified records panel
- locale-aware numeric input
- native date/time picker
- TalkBack semantics for live state and hero controls
- retained Stop & Save / Undo / discard / save-in-progress reliability UX

## Known limitations

- Generic sensor selection backend exists, but a complete final sensor-picker UI is still not
  exposed from Cardio; timer-only mode remains valid.
- Live HR samples are memory-backed during a session. Core workout state survives process death,
  but high-frequency sensor samples collected before process death are not yet crash-persisted.
- Exact-distance PRs remain locked until a future live/import path supplies real `CardioLap`
  records.
- Analytics operate on the bounded session list currently loaded by the core ViewModel; older
  history can be loaded incrementally.
- App-wide unit preference persistence is not yet wired; analytics UI can switch units locally.
- Health Connect write-back is intentionally not implemented.

## Validation

The consolidated branch runs the existing Data Vault boundary check, all JVM tests matching
`com.projectsuperhuman.next.Cardio*`, and a full debug APK assembly through
`.github/workflows/build-nextgen.yml`.

Draft integration PR: #28.
