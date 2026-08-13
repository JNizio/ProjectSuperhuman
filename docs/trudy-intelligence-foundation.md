# Trudy Intelligence Foundation

This layer performs bounded, deterministic personal-evidence calculations above Module Parity. It does not access SQL, Health Connect, OCR, Compose state, provider APIs, or web research. Runtime wiring should supply a `TrudyPersonalEvidenceSource`, normally through `HealthContextPersonalEvidenceSource`.

## Evidence classes

Personal observations, trends, associations, repeated associations, and personal experiment results are represented separately. Scientific context uses the separate `TrudyScientificContextProvider` seam and must never be folded into the personal confidence label.

Association and lagged association are descriptive only. They must never be called causal evidence. Lagged analysis tests only the lag explicitly requested; Trudy does not scan many lags and choose the strongest result.

## Confidence rules

`TrudyConfidence` is a transparent heuristic quality label, not a probability or proof value.

- Fewer than 5 usable/aligned samples, or under 45% matched observations: `INSUFFICIENT`.
- Sample size contributes progressively at 7, 14, and 30 observations.
- Fresh data, at least 80% matching, consistent direction, meaningful signal magnitude, and repeated observation can each strengthen the label.
- Labels are `INSUFFICIENT`, `LOW`, `MODERATE`, or `STRONG`.
- Stale, sparse, poorly aligned, or low-quality data reduces the usable interpretation and remains explicit in caveats/warnings.

These rules intentionally do not represent statistical certainty.

## Baseline comparisons

The library supports explicit-window comparisons and the default recent-trend comparison of latest 7 days against the preceding 28 days. Results include means, absolute and relative change where valid, standardized effect where baseline variability permits it, sample counts, quality status, and evidence references.

## Experiments

The experiment foundation is limited to safe lifestyle presets: earlier caffeine cutoff, hydration consistency, sleep-schedule consistency, exercise timing, and mindfulness routine. It does not autonomously recommend prescription-medication changes, insulin changes, dangerous fasting, substance withdrawal, or clinically risky treatment changes.

Experiment evaluation compares baseline and intervention distributions, adherence, variability, absolute/relative change, and confidence. A single uncontrolled experiment can only be described as `consistent with`, `supports the hypothesis`, `did not support`, or `inconclusive`; it cannot establish causation or universal truth.

`TrudyExpectedGain` defaults to qualitative direction/uncertainty. Numeric plausible ranges should remain absent unless a future bounded evidence source genuinely supports them.

## Tool boundary

Typed tools expose personal trend, baseline comparison, association, explicit lagged association, safe experiment hypothesis generation, and experiment evaluation. Metrics and domains remain explicit in every operation. The model should request these calculations rather than perform important health math in free text.
