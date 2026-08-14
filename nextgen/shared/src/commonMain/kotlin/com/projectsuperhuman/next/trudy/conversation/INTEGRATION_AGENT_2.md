# Trudy conversation and evidence continuity — Agent 2 contract

This package is a bounded, local conversation context layer. It resolves follow-ups and controls
evidence reuse, but it never generates final prose and never queries a second health-data store.

## Turn lifecycle

1. Keep one `TrudyConversationEvidenceCoordinator` for the visible Trudy conversation. Voice and
   keyboard input must call the same instance.
2. Call `prepare(userText, inputMode)` exactly once before planning the turn.
3. Consume `TrudyAnswerEngineConversationContext` instead of reparsing the transcript:
   - `resolvedRequest` contains topic, domains, canonical metric coordinates, timeframe, prior
     timeframe and the structured referent.
   - `conversationState` contains the latest investigation pointer, evidence IDs, active experiment,
     unresolved question and bounded missing-data findings.
   - `retrieval` is `FETCH`, `REFRESH`, `REUSE` or `REUSE_MISSING_DATA`, with a reason.
4. For `FETCH` or `REFRESH`, run the existing typed Trudy/Data Vault tools. Convert the bounded
   result to a `TrudyConversationEvidenceBatch`. The data layer owns `staleAtEpochMs`; historical
   closed windows can remain reusable longer than current/live queries.
5. For `REUSE`, supply `reusableEvidence` to the answer engine and do not rerun equivalent tools.
   For `REUSE_MISSING_DATA`, use the exact remembered domain/metrics/window and do not pretend that
   an absent record is a measurement.
6. Require the answer engine to return the exact evidence record IDs it actually used. Pass those
   IDs, the structured investigation result, missing-data findings and any retrieved batch to
   `complete(context, outcome)`.
7. Render the returned `TrudyUsedEvidencePresentation`. Its count is based on used, relevant,
   de-duplicated records; `retrievedRecordCount` is diagnostic only and must not be labelled as
   answer evidence.

## Reference behavior

- “What about last month?” inherits the active topic/metrics and changes only the timeframe.
- “Could that explain my heart rate?” targets heart-rate metrics while retaining the prior result as
  the source referent and keeps both domains in the resolved request.
- “that trend”, “those readings”, “those nights”, “it”, “then”, “before that” and “the same thing”
  resolve to structured state, not a transcript search.
- “What evidence are you using?” requests the prior used evidence IDs. A valid in-session cache is
  reused; stale evidence is refreshed.
- After a missing BP result for yesterday, “the day before” keeps the BP metrics and shifts the exact
  prior window backward.

## Evidence mapping

Create one `TrudyConversationEvidenceRecord` per bindable `TrudyEvidenceReference`. Use the same
exact domain, metric/insight ID, evidence kind and timestamp/range identity already enforced by the
orchestrator. Suggested roles:

- `SUPPORTING`: directly supports a statement in the answer.
- `CONTEXT`: is explicitly used to qualify the answer.
- `DATA_GAP`: supports a missing/insufficient-data explanation and is shown as data availability,
  not added to the supporting-record count.

Do not place arbitrary provider payloads, entire tool responses or model prompts in `displayValue`.

## UI semantics already isolated on this branch

The Android adapter now drops `usedInAnswer = false`, removes duplicate IDs, propagates a domain
group label, and renders “N records used” grouped by domain instead of “N evidence items”. Current
orchestrator evidence references are already model-bound; Agent 2 should set `usedInAnswer` from the
selector when wiring the new coordinator.

## Bounds and privacy

- No transcript is stored in structured state.
- At most 6 evidence batches are retained.
- At most 96 de-duplicated records are retained per batch.
- Latest used evidence IDs are capped at 48; missing-data findings at 12.
- `reset()` clears the entire in-memory conversation state/cache.
- There is no external logging, network write, permanent memory, or transcript index.

The integration point should live beside the existing `TrudyConversationService` / Android backend
adapter. It must not create a new endpoint or persistence layer.
