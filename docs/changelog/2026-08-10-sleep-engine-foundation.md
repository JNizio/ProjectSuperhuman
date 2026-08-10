# 2026-08-10 — Sleep Engine Foundation

## Purpose
Establish a durable sleep backend that keeps source data separate from reconstruction and interpretation.

## Implemented
- Added raw sleep record/stage data contracts.
- Added canonical sleep episode reconstruction.
- Supports fragmented overnight sleep blocks and morning continuation.
- Separates likely naps from overnight episodes.
- Preserves gaps between source sessions as explicit interruptions instead of automatically labelling them Awake.
- Preserves Health Connect source record IDs and original stage timelines.
- Added episode/source confidence values.
- Added sleep analysis for duration, opportunity, efficiency, continuity, stage balance and recovery interpretation.
- Added confidence-aware interpretation foundations.
- Added personal sleep baseline foundations for duration, timing, stage mix and fragmentation.
- Added cross-module context foundations for future Sleep, Nutrition, Hydration, Training and Recovery relationships.

## Architecture decisions
1. Raw Health Connect data must remain immutable.
2. Reconstruction must not invent sleep or awake minutes.
3. Unknown gaps remain distinguishable from recorded Awake stages.
4. Recorded and interpreted sleep are separate concepts.
5. Interpretation must expose uncertainty rather than imply 100% accuracy.
6. Personal baselines should become more useful as more nights accumulate.
7. Future engine improvements should be able to reprocess historical raw records.

## Known limitations
- Health Connect data is already downstream of wearable/device sleep detection, so the app cannot recover information the source never recorded.
- Current personal baseline is a foundation; robust personalized correlations require substantially more longitudinal data.
- Cross-module context is currently a contract/foundation and should not yet claim causal relationships.
- Scientific reference ranges are guides, not diagnostic thresholds.

## Next direction
Build the Sleep UI around two explicit layers: Recorded and Interpreted. Show confidence and the reason for meaningful differences. Then build longitudinal pattern detection and evidence-based cross-module experiments without corrupting source data.
