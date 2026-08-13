# Environmental Data Vault integration

Environmental data is persisted as longitudinal context, not as a weather-screen cache. `EnvironmentalHistoryStore` writes generic `HealthValue` evidence through `DataIngestionPipeline` and `DataVaultGateway.module(ENVIRONMENT)`.

## Schema and history

No SQL schema change is required. The existing `health_value` table already stores domain, metric, numeric value, unit, observation timestamp, source, source record ID and metadata. Environmental rows therefore use `HealthDomain.ENVIRONMENT`, and the existing daily aggregate path automatically provides bounded historical summaries. Existing user data needs no migration.

The stored timestamp is the provider observation time, never the UI refresh time. Namespaced metadata preserves fetch time, freshness expiry, observation age, fresh/stale state at fetch, evidence kind, optional provider observation ID, coarse location context/granularity, and the sampling policy.

## Sampling and deduplication

Default policy is one durable sample per provider + coarse location + metric + one-hour observation-time bucket. This caps normal history at 24 samples per provider/location/metric/day while retaining useful resolution for sleep, exercise, recovery, emotional-state and future cross-domain analysis. The minimum configurable bucket is 15 minutes.

Multiple readings for the same bucket in one `persist()` call are collapsed before storage and the newest observation wins. If that bucket is already present, later refreshes are sampled out without a write. Different providers and different coarse locations remain separate evidence. A deterministic `sourceRecordId` also reuses the Data Vault's existing `(source, source_record_id)` uniqueness as a second durable deduplication layer.

Future forecasts are not written as historical observations. Agent 5 should only send current observation, current estimate, or derived current-context values to this boundary.

## Privacy

The persistence DTO has no latitude/longitude fields. Retained granularities are `country`, `region`, `city`, `coarse_grid`, `weather_zone`, or `unknown`. Unsupported precise granularities are stored as `redacted` with no location context. Unknown granularity also stores no context.

Additional provider metadata is filtered so coordinate, GPS, address, street and postcode-style fields do not enter historical storage. Precise coordinates may be used ephemerally upstream for a weather lookup but should not be persisted here.

## Agent 5 canonical-model integration

Agent 5 owns the canonical Environmental models. `EnvironmentalEvidenceInput` is intentionally only a narrow persistence adapter and should not become the canonical model.

For each numeric canonical reading, map:

| Canonical concept | `EnvironmentalEvidenceInput` |
| --- | --- |
| canonical metric ID | `metric` |
| numeric reading | `value` |
| canonical/source unit | `unit` |
| time the value describes | `observedAtEpochMs` |
| stable provider/source slug | `provider` |
| retrieval time | `fetchedAtEpochMs` |
| freshness/cache expiry | `freshUntilEpochMs` |
| upstream observation ID if available | `providerObservationId` |
| observation/current estimate/derived | `evidenceKind` |
| coarse city/region/weather-zone label only | `locationContext` |
| matching coarse granularity | `locationGranularity` |
| non-sensitive provenance | `metadata` |

A canonical snapshot containing temperature, humidity, pressure, AQI, pollen, UV or other numeric context should be mapped to one adapter input per metric and passed as one list to `EnvironmentalHistoryStore.persist(...)`.

Agent 5 should register its final canonical metric IDs and units in `CoreMetricRegistry`. Until then, unregistered Environmental metrics are intentionally preserved unchanged rather than guessed or relabelled.

If Agent 5 independently adds `HealthDomain.ENVIRONMENT`, keep a single enum entry during merge conflict resolution. No additional storage migration is needed.

Final flow:

`Agent 5 canonical Environmental snapshot -> narrow mapping -> EnvironmentalHistoryStore -> DataIngestionPipeline -> DataVaultGateway.module(ENVIRONMENT) -> health_value + daily_aggregate`

Networking, UI, Trudy and Emotional-module behavior remain outside this change.
