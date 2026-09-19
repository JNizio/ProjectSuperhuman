# Cardio Analytics Integration

This branch adds a merge-friendly analytics and presentation layer around the existing Cardio session model. It does not own live-session persistence, foreground services, BLE acquisition, H19C, Health Connect import, or the Cardio ViewModel lifecycle.

## Architecture

The analytics layer is intentionally split from Compose:

- `CardioAnalyticsModels.kt` — analysis ranges, trend points, lap/split model, record/load/consistency/goal/comparison models and data-quality labels.
- `CardioTrendEngine.kt` — reusable range filtering plus daily/weekly/monthly aggregation.
- `CardioAnalyticsEngine.kt` — load quality, baseline quality, intensity distribution, consistency, goals, post-workout summaries and conditional aerobic decoupling.
- `CardioComparisonEngine.kt` — like-for-like previous-session selection and descriptive metric deltas.
- `CardioRecordsEngine.kt` — general records plus exact-distance PRs that require real timing data.
- `CardioHistoryEngine.kt` — filter/search/pagination.
- `CardioUnits.kt` — canonical-SI presentation conversion and locale-aware decimal parsing.
- `CardioCharts.kt` — lightweight Compose Canvas trend chart with null-gap handling and accessibility description.
- `CardioAnalyticsUi.kt` — reusable range, unit, progress, history, records, activity-detail, load-quality and session-comparison UI.

All engines are pure functions over immutable inputs and are suitable for ViewModel precomputation.

## Required Cardio session fields

The existing `CardioSession` fields required for baseline analytics are:

- `id`
- `activity`
- `startedAt`
- `endedAt`
- `durationSeconds`
- `workoutType`
- `source`

Optional fields are consumed only when present:

- `distanceKm`
- `avgHeartRate`, `maxHeartRate`, `minHeartRate`
- `avgPaceSecPerKm`, `avgSpeedKmh`
- `elevationGainM`
- `cadence`
- `rpe`
- `zoneSeconds`
- `avgSplit500mSeconds`
- `avgPace100mSeconds`
- `notes`

No analytics function invents an absent metric.

## Optional sensor / imported fields

Future sensor and Health Connect work can add richer inputs without changing the engines:

- continuous HR samples
- speed/pace samples
- power samples
- GPS or device route splits
- imported provider/source identifiers
- sensor coverage metadata

Sensor provenance should remain explicit. Imported values should preserve provider/source in the persistence model and map real laps/splits into `CardioLap`.

## Range model

Use one `CardioAnalysisRange` for a Progress surface:

- 7 days
- 4 weeks
- 3 months
- 6 months
- 1 year
- all time

Call `CardioTrendEngine.filterRange(...)` once for range-scoped summary cards, and pass the same range to trend functions. Default chart bucket size is centralized in `CardioAnalysisRange.defaultGranularity()`:

- 7 days -> daily
- 4 weeks / 3 months -> weekly
- 6 months / 1 year / all time -> monthly

Do not duplicate date-window logic in individual charts.

## Trend APIs

Volume:

```kotlin
CardioTrendEngine.volumeTrend(
    sessions,
    range,
    CardioTrendMetric.MINUTES
)
```

Supported additive volume metrics are minutes, distance, session count, Cardio load and measured Zone 2 minutes.

Activity-specific:

```kotlin
CardioTrendEngine.availableActivityMetrics(activity, sessions)
CardioTrendEngine.activityTrend(sessions, activity, range, metric)
```

Only metrics actually present for that activity are returned. Pace/speed/HR/cadence/elevation/RPE/duration are therefore not shown as fake zero trends.

A null trend point means the metric was unavailable for that bucket. Empty additive buckets can be zero. Charts break lines across null values rather than interpolating them.

## PR APIs

General and activity records:

```kotlin
CardioRecordsEngine.recordsForActivity(activity, sessions, laps)
CardioRecordsEngine.allRecords(sessions, laps)
```

Exact-distance:

```kotlin
CardioRecordsEngine.exactDistanceRecord(
    activity,
    sessions,
    laps,
    targetDistanceMeters
)
```

Exact-distance PRs are locked unless real `CardioLap` timing can produce the requested distance. Session-average pace is never multiplied across a longer session to manufacture an exact PR.

Current target sets include running/walking/treadmill/hiking 1 km, mile, 5 km and 10 km; rowing 500 m and 2 km; common swimming distances; and supported cycling exact segments.

## Lap / split persistence mapping

`CardioLap` is the analytics contract for later live/import work:

- `sessionId`
- `index`
- `startedAt`
- `endedAt`
- `durationSeconds`
- `distanceMeters`
- `avgHeartRate`
- `maxHeartRate`
- `source`
- `exactDistance`

`exactDistance=true` means the distance and time came from a real lap, split, route segment or external exact-distance record. It does **not** mean “derived from average session pace.”

Suggested persistence metadata keys if the core branch chooses HealthValue-style storage later:

- `cardioLapSessionId`
- `cardioLapIndex`
- `cardioLapStartedAt`
- `cardioLapEndedAt`
- `cardioLapDurationSeconds`
- `cardioLapDistanceMeters`
- `cardioLapAvgHeartRate`
- `cardioLapMaxHeartRate`
- `cardioLapSource`
- `cardioLapExactDistance`

The core persistence engineer should choose the final table/row representation.

## Comparison API

```kotlin
CardioComparisonEngine.previousComparableSession(current, history)
CardioComparisonEngine.compareWithPrevious(current, history)
```

Comparison requires the same activity and rejects substantially different duration/distance/elevation. Non-free workout classifications must match. The output is descriptive:

- faster at similar or lower average HR
- lower average HR at similar pace/speed/split
- comparable result

It does not infer cause or declare that fitness improved.

## Cardio load

```kotlin
CardioAnalyticsEngine.loadDetail(session)
CardioAnalyticsEngine.loadAnalytics(sessions)
```

Hierarchy remains:

1. measured zone duration × transparent zone weights
2. otherwise RPE × minutes
3. otherwise unavailable

`CardioLoadDetail` exposes measured-zone coverage percent and unclassified duration. A 60-minute session with 10 measured HR-zone minutes therefore reports about 17% measured coverage rather than presenting the score as fully measured.

`CardioLoadAnalytics` exposes:

- scored sessions / total sessions
- duration-level measured-zone coverage
- unclassified seconds
- previous 21-day weekly average
- baseline quality
- recent/baseline ratio only when the baseline is usable

Baseline states are `INSUFFICIENT`, `BUILDING`, and `USABLE`. The UI avoids injury-risk predictions.

## Intensity distribution

```kotlin
CardioAnalyticsEngine.intensityDistribution(filteredSessions)
```

The result has Z1-Z5 plus unclassified duration. Sessions without measured zones remain unclassified. Workout type and RPE are not silently converted to HR zones.

## Consistency and optional goals

```kotlin
CardioAnalyticsEngine.consistency(sessions, range)
CardioAnalyticsEngine.goalProgress(goals, sessions)
```

Consistency is descriptive: sessions/week, active weeks, average weekly minutes, average weekly distance when present, and rolling 28-day frequency.

Goals are optional models. Persist target configuration separately from observed session data. Changing or disabling a goal must never rewrite Cardio history.

## Aerobic decoupling

`CardioAnalyticsEngine.aerobicDecoupling(samples)` is intentionally conditional.

It returns null unless the session has:

- at least 20 minutes of samples
- at least 20 usable samples
- at least 80% HR plus speed or power coverage

The calculation uses the middle 80% of the sampled timeline and compares first-half versus second-half output/HR efficiency. The result is labeled `ESTIMATED`; do not show it as a medical or diagnostic value.

## Chart components

`CardioTrendChart` uses Compose Canvas only. It:

- supports dark-mode theme tokens
- provides a TalkBack content description
- uses readable text sizes
- avoids connecting across missing/null buckets
- scales from recorded min/max
- shows an explicit empty state

Keep chart count low on any one screen. Prefer a summary plus one or two relevant trends and progressive disclosure for activity-specific pages.

## Unit conversion

Canonical persisted values remain SI. `CardioUnits` converts presentation only:

- km <-> miles
- km/h <-> mph
- sec/km <-> sec/mile
- metres <-> feet

No record is duplicated when display units change.

`parseLocalizedDecimal` and `sanitizeDecimalInput` support locale decimal separators while accepting a period as a copy/paste fallback. The integration patch updates existing manual Cardio decimal fields to use these helpers.

A future app-wide unit preference should be injected into the analytics UI rather than stored in Cardio sessions.

## History and performance

`CardioHistoryEngine.filterAndPage` applies range/activity/workout/source/query filters before pagination.

`CardioAnalyticsHistoryPanel` uses a bounded `LazyColumn` and loads 30 rows at a time. This replaces the previous eager `.take(100)` path at the integration point.

For the refactored Cardio ViewModel, recommended state is:

```kotlin
data class CardioAnalyticsState(
    val range: CardioAnalysisRange,
    val progress: PrecomputedProgress,
    val historyFilter: CardioHistoryFilter,
    val historyPage: CardioHistoryPage
)
```

Compute analytics when sessions/range/filter change, not on every recomposition. Date labels and chart series should be memoized in ViewModel/state where practical.

## Current NativeCardio integration points

This branch intentionally makes only small integration edits in `NativeCardio.kt`:

1. History delegates to `CardioAnalyticsHistoryPanel`.
2. Session detail adds `CardioSessionComparisonPanel`.
3. Progress adds `CardioAnalyticsProgressPanel` while retaining the existing progress cards.
4. Records adds `CardioAnalyticsRecordsPanel` while retaining existing record UI.
5. Existing decimal entry/save paths use locale-aware `CardioUnits` helpers.

When the core Cardio ViewModel refactor is merged, keep the new analytics files and rewire these calls to ViewModel-provided immutable session/lap state.

## Engineer 1 merge expectations

The core branch should provide:

- canonical saved `CardioSession` list
- selected/session-detail ID
- paged history source if persistence paging moves below the UI
- lap/split list keyed by session ID when available
- app-wide unit preference if one is introduced
- optional saved goal configuration

It should **not** move analytics formulas back into the ViewModel. Call the pure engines and expose their results as UI state.

If the core branch renames `CardioSession`, either retain a compatibility typealias/mapper or create one mapper into an analytics DTO. Avoid making the engines depend on persistence entities.

## Accessibility

New reusable analytics UI uses:

- >= 12sp supporting text
- >= 48dp interactive targets
- selected-state semantics on range/unit/filter controls
- button roles
- meaningful chart descriptions
- non-colour labels for locked/available records
- explicit text for measured coverage and unclassified data
- layouts that rely on wrapping/vertical growth rather than fixed text heights

The live Recording/Paused announcement remains owned by the session/lifecycle branch because that engineer controls live state.

## Assumptions and limitations

- Existing sessions do not yet persist lap/split arrays, so exact-distance PRs remain locked until another branch supplies real timing data.
- Existing session-level pace/speed can still be shown as an average record, but never as an exact-distance PR.
- Cardio load is a transparent training-management score, not a physiological measurement.
- Baseline thresholds are conservative presentation gates, not injury-risk thresholds.
- Distance trends are unavailable rather than zero when sessions exist but no distance was recorded.
- Zone 2 trends are unavailable when a bucket has sessions but no measured zone data.
- Aerobic decoupling is not shown from session-average HR plus average pace.
- Goal persistence is deliberately not implemented here because persistence ownership belongs to the core branch.
- App-wide unit preference persistence and native date/time picker migration should be completed in the shared settings/core integration layer rather than duplicated inside Cardio.
