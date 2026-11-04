# Exercise overhaul — Project Superhuman 11.4

Source: user-provided Complete Exercise Module Overhaul PDF, 25-section implementation specification.

This record documents implemented work and remaining verification. It is not a claim that all 25 acceptance sections are complete.

## Delivered code

- Action-first Exercise Hub with Strength and Cardio quick entry, retained in-progress resume.
- Continuous per-exercise Strength logging, with prior real results, cached RepDB images, per-exercise load/reps, completed sets, edit/delete and explicit completion.
- Optional warm-up/working type, RIR and RPE; zero-load sets continue to work.
- Rest timers with 0/60/90/120/180-second presets, +/-15-second adjustments and timestamp-derived restoration.
- JSON draft recovery extended with paused state and paused duration; older draft versions have backward-compatible defaults.
- Guarded workout finishing; finish checks pending set writes and preserves recovery data until session save and readback.
- Guarded discard with confirmed deletion of the active workout's previously saved sets.
- Repeat the previous session into a new workout without modifying history.
- Strength history calendar retained with added workout/exercise search and 7-day/30-day/90-day/year/all-time filters.
- Strength aggregate calculation memoization.
- Cardio quick starts prioritize real recently recorded activities unless user-defined slots exist.
- Heart-rate sensor states distinguish connected/no-reading from connected/reporting, never synthesizing HR.
- Activity-dependent Cardio live metrics only show sourced movement readings; swimming timer-only fallback.
- Flat Exercise, Strength and Cardio treatments replacing gradients; removed network-backed Exercise Hub photographs.
- Tests added for distinct rapid-set timestamps, cardio sensor status and recent-activity ordering.

## Data contracts and preservation

Retained existing HealthValue workout-session and exercise-set metric names, routine records, historical sessions, sensor models, Cardio persistence and Health Connect adapters. No destructive data migration introduced. Preserve all pre-existing persisted measurements.

## Test and build protocol

The repository's verify-11-4-native-performance GitHub Actions workflow compiles an Android debug APK, uploads artifact ProjectSuperhuman-11.4-APK, then runs all unit tests. Verify each target commit separately. The original baseline commit 46996b76ab03ea01853a571134fa3320567e81ee already compiled successfully but failed three TrudyRuntimeHardeningAndroidTest assertions out of 322 tests; this baseline issue is unrelated to the Exercise implementation.

No actual device-level performance benchmark, full screen-reader audit, sensor reconnection/foreground endurance test, or signed release build has yet been verified.

## Still pending for full PDF acceptance

- Insert/reorder logged sets, complete supersets/routine-editor parity and advanced favorites/frequently-used library.
- Restore uncompleted numeric text edits after process death (completed sets and primary workout draft already persist).
- Complete Cardio/Strength range-aware deep analytics and tests of all historical comparison paths.
- Android instrumentation runs for text scale, keyboard overlaps, one-handed targets, screen lock and rotation.
- Stress checks for activity lifecycle, background tracking, Bluetooth permissions/reconnection, GPS availability and Health Connect.
- Measured cold-entry/search latency, scroll-jank and recomposition counts.
- Complete 25-section feature-inventory parity audit and full acceptance test grid.

## Touched files

- NativeExerciseHub.kt
- NativeExerciseParity.kt
- StrengthInlineExerciseLogger.kt
- StrengthTrainingEngine.kt
- ActiveWorkoutStore.kt
- NativeCardio.kt
- CardioHubPreferences.kt
- CardioHubUi.kt
- CardioProductUi.kt
- StrengthLoggingPolicyTest.kt
- CardioProductUiTest.kt
- CardioHubPreferencesTest.kt
