# Cardio Core Integration

## Scope

This branch owns Cardio session identity, live-session lifecycle, timing, persistence safety,
state management, foreground/background continuity and core regression tests. It deliberately
does not implement BLE/heart-rate sensors, GPS, Health Connect Cardio import, charting, PR
algorithms or advanced progress analytics.

## Architecture

The original Cardio screen remains the UI shell so parallel UI/analytics work can be merged with
minimal churn. Core behavior has moved out of the Composable into small testable components under:

`androidApp/src/main/java/com/projectsuperhuman/next/cardio/`

- `CardioModels.kt` - versioned completed-session and live-draft domain models.
- `CardioLiveSessionController.kt` - pure lifecycle/timing state machine.
- `CardioSessionStore.kt` - single structured DataStore for incomplete/live Cardio drafts.
- `CardioPersistenceMapper.kt` - Cardio <-> Data Vault mapping.
- `CardioValidation.kt` - validation at the Cardio persistence boundary.
- `CardioRepository.kt` - completed-session persistence/query abstraction.
- `CardioSessionCoordinator.kt` - serialized save/pause/resume/discard/undo transactions.
- `CardioState.kt` - immutable UI state.
- `CardioViewModel.kt` - lifecycle-aware StateFlow owner for UI state.
- `CardioSessionService.kt` - foreground service and notification actions for active workouts.

`NativeCardio.kt` now consumes these components. Timer correctness and persistence are no longer
driven by recomposition.

## Session model

A permanent UUID is generated when a live workout starts. The same `sessionId` survives pause,
resume, process recreation, finish, edit and Undo.

Completed sessions preserve separate fields for:

- wall-clock start: `startedAt`
- actual wall-clock completion: `endedAt`
- active elapsed duration: `durationSeconds`
- paused duration: `pausedDurationSeconds`
- schema version: `schemaVersion`
- extension metadata: `extensions`

The model intentionally leaves room for future typed HR summaries/timelines, distance, pace,
cadence, elevation, route, laps/splits, sensor provenance, workout segments and Health Connect
source IDs. High-frequency streams should not be placed in `extensions`; add an appropriate
typed/store-backed stream keyed by `sessionId`.

## Timer semantics

`CardioLiveSessionController` uses two clocks:

- `SystemClock.elapsedRealtime()` for active and paused elapsed duration.
- epoch time for historical start/completion timestamps.

Changing the device wall clock therefore does not change active duration. The completion
timestamp is captured from the wall clock at the moment Finish/Stop & Save is requested; it is
never derived as start + active duration.

A guarded epoch fallback exists only for legacy drafts/device reboot cases where the previous
monotonic checkpoint cannot be reused.

## Live draft persistence

The active Cardio workout has one canonical live store:
`cardio_live_session` DataStore.

Current draft schema version: **2**.

The former `superhuman_cardio` SharedPreferences draft is migrated on first load. Existing v1
drafts are mapped to v2 with a deterministic legacy session ID. A draft from a newer unsupported
schema is not destructively cleared; the UI reports that it was left untouched.

The draft is checkpointed before a finish write is attempted. It is cleared only after a verified
successful completed-session write or an explicit confirmed discard.

## Repository API

`CardioRepository` is the stable completed-session boundary:

- `save(session)`
- `update(session)`
- `delete(sessionId)`
- `sessionById(sessionId)`
- `recentSessions(limit)`
- `sessionsBetween(from, to)`
- `pageHistory(limit, offset)`

UI code should not call `NativeDataHub.saveValues()` directly.

Writes use `NativeDataHub.ingestValues()` and check accepted/rejected/issues before a draft is
destroyed.

### Atomic/idempotent updates

Each Cardio row maps:

`sourceRecordId = cardio:<stable-session-id>`

The Data Vault already enforces a unique `(source, source_record_id)` index and uses
`INSERT OR REPLACE` within its transaction. Editing therefore performs an atomic logical upsert
with the same source record ID instead of deleting the old row first.

Rapid duplicate save attempts are additionally serialized by `CardioSessionCoordinator` and
guarded by ViewModel save state.

## Live-session state API

UI and integrations should observe `CardioViewModel.state` / `CardioUiState`.

Important fields:

- `liveDraft`
- `liveElapsedSeconds`
- `pausedElapsedSeconds`
- `isRecording`
- `saveInProgress`
- `saveState`
- `feedback`
- `undo`

The live phases are `RECORDING`, `PAUSED` and `FINISHING`.

Do not derive an independent timer from Compose state.

## Foreground/background behavior

Starting/resuming a workout starts `CardioSessionService` as a foreground service. It exposes
notification actions for Pause, Resume and Stop & Save and uses the same DataStore/coordinator as
the UI. Because both surfaces observe/write the same durable draft, reopening the app reconnects
to the current workout rather than creating another session.

The service uses a low-importance notification channel and the Android special-use foreground
service declaration appropriate to a user-initiated workout timer. No sensor/location foreground
service type is claimed on this branch.

## Undo and discard

Quick Stop & Save keeps the one-tap path. After success the ViewModel exposes a short-lived Undo
token. Undo first recreates a paused live draft with the original stable ID, then attempts to
remove the completed copy. If that delete fails, the restored draft still survives and the next
save atomically replaces the same `sourceRecordId`.

Discard is explicit and requires confirmation in the Cardio UI. Navigation away from a live
workout does not discard it.

## History/query performance

The screen no longer preloads a five-year Cardio window. The repository initially returns 250
recent sessions and exposes paging and bounded/date-range query APIs for future UI/analytics work.

Analytics engineers should query through `CardioRepository` (or add summary methods there) rather
than loading the entire Exercise vault into Compose.

## Sensor integration seam

Sensor/Health Connect engineers should:

1. Treat `CardioLiveDraft.sessionId` as the durable foreign key from the moment recording starts.
2. Keep sensor transport/stream buffering outside `CardioViewModel` and `SavedStateHandle`.
3. Attach completed summary/provenance fields through a typed extension to `CardioSession` and
   `CardioPersistenceMapper`.
4. Store high-frequency timelines in a dedicated store keyed by stable session ID; do not encode
   thousands of samples into Data Vault metadata.
5. Do not create a second live-session identity or clear the Cardio draft after sensor failures.
6. Let the core coordinator own final save/discard semantics.

No H19C/BLE internals were changed here.

## Analytics integration seam

Analytics/history/progress engineers should:

1. Consume completed `CardioSession` values from `CardioRepository`.
2. Add bounded/date/activity/summary queries to the repository rather than direct full-vault reads.
3. Preserve `sessionId`, `endedAt`, `durationSeconds` and `pausedDurationSeconds` semantics.
4. Keep PR/efficiency/load algorithms outside `CardioLiveSessionController`.
5. Prefer new UI files/components over moving persistence logic back into `NativeCardio.kt`.

The existing load, efficiency, records and progress algorithms were intentionally left intact.

## Metric registry

`cardio_session` is registered in `CoreMetricRegistry` as:

- domain: Exercise
- canonical unit: minutes
- aggregation: SUM
- accepted range: 0..1440 minutes

No speculative future Cardio metrics were registered.

## Migration notes

- Existing completed Cardio rows continue to decode; missing schema/paused fields use legacy
  defaults.
- Existing active SharedPreferences drafts migrate into the versioned DataStore.
- New completed writes use the same `cardio_session` metric and `native-cardio` source, but now
  carry explicit schema/paused metadata and a stable source record ID.
- Existing edit UI now uses repository upsert instead of delete-then-save.

## Tests

`CardioCoreReliabilityTest.kt` covers:

1. start -> pause -> resume -> finish
2. wall-clock changes vs monotonic elapsed duration
3. paused time excluded from active duration
4. real completion timestamp after pauses
5. exactly-one quick save
6. repeated save duplicate protection
7. failed write preserves draft
8. rejected write preserves draft
9. running process recreation
10. paused process recreation
11. running elapsed continuation
12. explicit discard
13. failed edit preserves original
14. future-date validation
15. zero/negative duration validation
16. zone duration validation
17. stable ID across lifecycle/restoration
18. navigation/recreation does not discard a live draft
19. Undo restores the original stable workout as paused
20. old/future draft schema behavior

There is also a stable `sourceRecordId` mapping regression test.

CI now runs shared and Android JVM unit tests before APK assembly.

## Files moved/renamed

No sensor, Strength, analytics-engine or Exercise navigation files were renamed. Cardio domain and
persistence definitions that previously lived inside `NativeCardio.kt` were extracted into the
new `cardio/` files listed above; package name remains `com.projectsuperhuman.next` to minimize
merge friction with parallel branches.
