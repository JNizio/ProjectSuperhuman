# Cardio n-of-1: Trudy and cross-app integration

## Ownership

This integration layer connects Cardio evidence to Project Superhuman's existing canonical systems. It does not own BLE/GPS, live workout UI, deep Cardio analytics equations, Strength, or Smart Devices.

## Canonical data flow

```text
Cardio measurements / sessions / derived analytics
  -> Data Vault (HealthValue + canonical metric IDs + provenance)
  -> ModuleParity / TrudyHealthContextService
  -> typed Trudy evidence
  -> deterministic trend / association / experiment tools
  -> Answer Engine

Sleep / body / mindfulness stress / environment
  -> Data Vault
  -> CardioRecoveryContext
```

No direct module-to-module stores are introduced.

## Evidence semantics

Trudy promotes Cardio metadata into typed evidence fields:

- measured / derived / estimated / inferred classification;
- session ID;
- source/device identity where available;
- algorithm version;
- sample count;
- coverage;
- confidence label;
- caveat.

The original metadata map remains intact for drill-down and forward compatibility. Derived algorithm outputs remain distinguishable by algorithm version rather than overwriting their lineage in model-facing evidence.

## Period summaries

`TrudyCardioEvidenceAssembler` creates factual current-week and current-month assemblies from canonical Cardio rows. It only groups recorded sessions and already-published derived metrics; it does not recalculate physiology.

Missing distance, Zone 2 duration, heart-rate coverage, or a fitness-efficiency baseline is emitted as an explicit data gap.

## Cross-domain context

`CardioRecoveryContext` consumes canonical values for:

- resting heart rate and personal baseline;
- HRV and personal baseline;
- sleep score and sleep duration;
- training-stress balance;
- body weight with observation timestamp;
- recorded stress score with observation timestamp;
- environmental temperature and humidity with observation timestamp.

Readiness remains a training/recovery signal only. It is not medical clearance.

## Experiments

Cardio-focused experiment protocols use `cardio_experiment_protocol` in the Data Vault through `DataVaultCardioExperimentRepository`. Stored protocol lineage includes:

- hypothesis and intervention;
- comparator;
- baseline/intervention windows;
- primary and secondary metrics;
- inclusion rules;
- confounders;
- observations;
- analysis method;
- confidence/result;
- caveats;
- structured evidence references.

The existing preview-only Experiments UI remains separate and is not treated as canonical persisted evidence. Trudy evaluates persisted protocols through the existing `TrudyExperimentEngine`, which retains non-causal wording and safety boundaries.

## Trudy semantic routing

The shared semantic catalog now understands Cardio terms such as fitness, running efficiency, readiness, acute/chronic load and training-stress balance. Explicit association routes cover:

- sleep vs Cardio fitness efficiency;
- environmental temperature vs running heart rate;
- humidity vs running heart rate.

These routes calculate associations only through the existing bounded personal-evidence tools. They do not claim causation.

## Future planning seam

`CardioPlanningContextSource` exposes measurement/context inputs for a future planner without producing a workout prescription or medical recommendation.

## Verification

The branch-specific workflow `.github/workflows/cardio-trudy-integration-ci.yml` runs:

1. Data Vault boundary checks;
2. shared unit tests;
3. Android unit tests;
4. Android debug Kotlin compilation.

It intentionally does not run an APK assemble/package task.
