# Trudy Investigation v2

## Purpose

Investigation v2 turns personal-data questions into a bounded evidence plan and a structured result for the Answer Engine. It does not generate answer prose and it does not diagnose or claim causation.

## Flow

1. Resolve the question intent and recent/baseline windows.
2. Select one target family and a small, explicit candidate-metric graph.
3. Verify the user's premise across target outcomes before considering explanations.
4. Align only planned exposure/outcome pairs with metric-specific semantics.
5. score changes and relationships with sample, coverage, freshness, variance, provenance and alignment quality.
6. Keep missing or stale signals in `missingEvidence`, never in priority findings.
7. Return `TrudyInvestigationResult` to the Answer Engine.

The legacy `InvestigateChange` operation and `TrudyChangeInvestigation` comparison/association fields remain available. The new `structuredResult` field is the preferred consumer contract.

## Premise verification

`premiseStatus` is one of:

- `PREMISE_SUPPORTED`
- `PREMISE_NOT_SUPPORTED`
- `PREMISE_MIXED`
- `PREMISE_UNVERIFIABLE`
- `NOT_APPLICABLE`

Sleep verification respects metric direction. Higher sleep score, continuity, deep sleep and total sleep are favourable; lower awake minutes are favourable. A subjective claim is retained separately so the Answer Engine can explain disagreement between reported experience and wearable measurements.

## Relevance graph

The graph is deliberately small and target-specific:

- Sleep: sleep outcomes plus calmness/stress, caffeine, environment, exercise, recovery heart rate, hydration and mindfulness.
- Heart rate: heart-rate outcomes plus exercise/load, previous-night sleep, calmness, hydration, environment and body temperature.
- Today: a bounded cross-domain set of recent state metrics. Active canonical experiments are retrieved as a separate typed result.

No plan requests every domain. Generic investigations use only explicitly resolved metrics.

## Temporal semantics

Candidate edges carry a `TrudyTemporalAlignment`:

- `SAME_DAY`
- `PREVIOUS_EVENING_TO_FOLLOWING_SLEEP`
- `PREVIOUS_NIGHT_TO_NEXT_MORNING`
- `ROLLING_AVERAGE`
- `BASELINE_VS_RECENT`

Lags and tolerances are explicit. The engine never searches multiple lags and then reports the best one. Caffeine is aligned to following sleep rather than the same calendar date.

## Relationship and quality rules

Relationships are classified only as:

- `OBSERVED_CHANGE`
- `POSSIBLE_ASSOCIATION`
- `NO_CLEAR_ASSOCIATION`
- `INSUFFICIENT_EVIDENCE`
- `CONTRADICTORY_EVIDENCE`

There is intentionally no causal classification. Quality records sample count, expected coverage, missingness, matched fraction, variance, measurement frequency, freshness, source set, and wearable/manual/derived/device proportions. Sparse, stale, low-variance or poorly aligned relationships cannot become strong findings.

## Performance bounds

Default limits are five targets, eight related signals, 256 rows per metric, 56 lookback days and five important findings. Today can use eight target metrics but no quadratic pair search. Every relationship is one declared graph edge against the primary target, so work is linear in the bounded candidate count.

