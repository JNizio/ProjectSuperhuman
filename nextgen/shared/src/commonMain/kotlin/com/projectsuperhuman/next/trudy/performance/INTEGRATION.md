# Trudy performance-knowledge integration contract

`TrudyPerformanceKnowledge` is a deterministic, read-only knowledge and routing boundary. It does
not read the Data Vault, run correlations, mutate experiments, or produce medical conclusions.

## Planner flow

1. Call `resolve(userMessage)` once per turn.
2. Treat `matches` as topic hints. A phrase can intentionally match several topics.
3. Deduplicate `metricBindings` and request only the bounded canonical evidence needed for the
   selected response. `PerformanceMetricRole` distinguishes primary outcomes, exposures,
   confounders, context and data-quality metrics.
4. Compute trends and associations through existing deterministic Trudy tools. Never infer a
   correlation from the knowledge text.
5. Use `claims` to explain concepts and limitations. Claims are external reference knowledge, not
   personal observations.
6. Preserve `sources` and `globalSafetyBoundaries` in model context or deterministic synthesis.
7. Use `experimentBlueprints` only as methodology/template input. Existing templates expose their
   current `TrudyExperimentKind`; blueprints without one need an explicit future engine mapping.

## Canonical metric ownership

- `CORE_METRIC_REGISTRY`: IDs already registered in `CoreMetricRegistry`.
- `ENVIRONMENTAL_DOMAIN`: IDs owned by `EnvironmentalMetricIds` / `EnvironmentalMetricCatalog`.
- `EMOTIONAL_DOMAIN`: IDs owned by `EmotionalMetricIds` / `EmotionalMetricRegistry`.

Do not copy these values into a new persistence layer. Query them through the canonical Data Vault.
Where the topic records a `dataAvailabilityNote`, do not invent a metric. For example, caffeine
dose/time, sitting time, soreness, indoor bedroom temperature and detailed breathwork parameters
are not currently complete canonical measurements.

## Model-context guidance

Include only claims and sources attached to the top matched topics. Do not inject the entire catalog
into every prompt. A concise context block should contain topic ID, relevant claim summary,
limitations, requested metric IDs and source IDs. Personal evidence should remain separately
labelled with its timestamp, source and data quality.

## Safety invariants

- Consumer sleep stages are estimates and are not equivalent to polysomnography.
- Heart-rate zones and readiness scores are contextual estimates, not diagnoses or commands.
- Emotional and breathing guidance remains wellbeing information, not mental-health diagnosis.
- Environmental and cross-domain patterns are personal associations unless stronger evidence is
  explicitly available.
- A small personal experiment may support a personal decision under similar conditions; it does
  not establish universal causality or treatment efficacy.
- Trudy must not override concerning symptoms, medical advice, medication plans or exercise
  restrictions.
