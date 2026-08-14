# Trudy Quality Swarm Handoff

## 1. Executive Summary

This branch integrates the five Trudy quality-swarm branches into one runtime architecture on top of the current `11.3` base. The integration is semantic rather than a blind union of branch trees.

The ownership model in the final code is:

- **Agent 4 language routing** owns lexical normalization, slang/UK-US variants, bounded multi-intent interpretation, and knowledge relevance gating.
- **Agent 5 conversation state** owns bounded topic/timeframe/referent/evidence continuity inside the current Trudy conversation.
- **Agent 3 investigation v2** owns the authoritative investigation plan, premise verification, bounded personal-data retrieval, temporal alignment, relationship scoring, evidence gaps, and structured investigation results.
- **Agent 2 Answer Engine** owns the final user-facing answer plan, evidence usability/ranking, natural synthesis, unit formatting, concise response length, and removal of internal engineering language.
- **Agent 1 voice streaming** owns adaptive TTS chunking, bounded producer/consumer buffering, persistent streaming playback, cancellation, and voice performance diagnostics.

There is not a second investigation planner, second answer formatter, second conversation memory, or second voice reasoning system in the final runtime.

The final conceptual path is:

`USER INPUT -> language/intent interpretation -> bounded conversation resolution -> investigation planning -> knowledge + canonical Data Vault retrieval -> structured investigation/evidence quality -> Answer Engine filtering/ranking -> Answer Engine plan -> model/deterministic synthesis -> Answer Engine final quality gate -> text or the existing voice output path`.

Medical safety remains outside and above ordinary answer generation: the existing `MedicalContextAwareTrudyModelClient` still controls medical candidate/safety context and can request bounded medical context before delegating to the final synthesis chain. No integration change turns symptom similarity into diagnosis, permits prescribing, recommends stopping medication, or removes red-flag escalation.

**Validation status is intentionally not described as passing.** A temporary branch-only GitHub Actions workflow was created to run medical corpus validation, Data Vault boundary validation, shared/Android unit tests, and an APK build. GitHub rejected job `94839329000` in run `31822650163` before checkout because the account/repository Actions billing or spending limit blocked runner allocation. The temporary workflow was then removed. Therefore no Gradle, Android unit-test, APK-build, medical-corpus, or Data Vault-boundary command was actually executed by this final integration agent. Static contract review was performed, but Android Studio/local Gradle validation remains mandatory.

## 2. Source Branches

### 2.1 Agent 1 — `agent/trudy-voice-streaming-performance`

**Source head:** `aa6f5137da16ef6f92a903d0b0478a74ef0966e7`

**Purpose:** remove long silent gaps from local Trudy TTS by overlapping synthesis with playback without changing the selected Trudy voice or reasoning architecture.

**Files added/modified:**

- `docs/trudy-voice-streaming-device-test.md`
- `nextgen/androidApp/src/main/java/com/projectsuperhuman/next/AndroidTrudyAudioSink.kt`
- `nextgen/androidApp/src/main/java/com/projectsuperhuman/next/DeveloperDiagnostics.kt`
- `nextgen/androidApp/src/main/java/com/projectsuperhuman/next/KokoroTrudySpeechEngine.kt`
- `nextgen/androidApp/src/main/java/com/projectsuperhuman/next/TrudyVoiceContracts.kt`
- `nextgen/androidApp/src/main/java/com/projectsuperhuman/next/TrudyVoiceService.kt`
- `nextgen/androidApp/src/test/java/com/projectsuperhuman/next/TrudyVoiceStreamingPerformanceTest.kt`

**Core classes/interfaces:**

- `TrudyVoiceService`
- `TrudySpeechEngine.synthesizeStreaming`
- `KokoroTextChunker`
- `AndroidTrudyAudioSink`
- `TrudyVoicePerformanceEvent` / voice diagnostics contract

**Architectural assumptions:** TTS synthesis is expensive enough that sequential `synthesize -> play -> synthesize` causes starvation; only a small lookahead is needed; playback order must remain deterministic; cancellation must invalidate stale production/playback work.

**Accepted:**

- bounded lookahead (`1..2` chunks) rather than synthesizing the complete response;
- producer/consumer channel with synthesis running ahead of playback;
- semaphore-limited lookahead;
- progressive chunk sizes (small first chunk, slightly larger second, bounded later chunks; current chunker targets approximately 36/50/64 characters);
- sentence/clause/word boundary splitting without splitting words;
- persistent `MODE_STREAM` `AudioTrack` rather than recreating playback for each chunk;
- starvation detection when the audio buffer/queue drains while the next chunk is not ready;
- first-synthesis/first-ready/first-playback timing and queue-depth diagnostics;
- response-generation/cancellation epoch so stopped or superseded responses cannot resume playback;
- tests for chunk order, bounded lookahead, drain behavior, and stale cancellation.

**Modified during integration:** only an additional integrated starvation/500+ character regression test was added. Agent 1 runtime implementation was not replaced.

**Rejected:** no reasoning, answer-planning, or conversation-state responsibility was assigned to voice code.

**Overlap:** only with response length: Agent 2 now keeps normal answers concise, which lowers the number of TTS chunks, but TTS remains independently correct for long medical/complex answers.

### 2.2 Agent 2 — `agent/trudy-answer-engine`

**Source head:** `447c06ab1df757a9375f155da2347c802b7b9e0a`

**Purpose:** replace robotic/internal-sounding final replies with one direct, evidence-aware Answer Engine.

**Files added/modified in source:**

- added `TrudyAnswerEngine.kt`
- modified `TrudyModelClient.kt`
- modified `TrudyModelPolicy.kt`
- modified `TrudyModelRuntime.kt`
- modified `TrudyOrchestrator.kt`
- modified `TrudySystemIntegration.kt`
- added `TrudyAnswerEngineTest.kt`

**Core classes/interfaces:**

- `TrudyAnswerEngine`
- `TrudyAnswerPlan`
- `TrudyAnswerEvidence`
- `TrudyAnswerEvidenceClass`
- `TrudyAnswerIntent`

**Architectural assumptions:** retrieval/tool math should remain deterministic; final synthesis should answer first; evidence is classified before it is ranked; missing/stale/irrelevant data can constrain confidence but must not become a top health finding; model output needs a final quality gate.

**Accepted:**

- evidence classes `USABLE`, `MISSING`, `STALE`, `LOW_QUALITY`, `CONTRADICTORY`, `IRRELEVANT`, `SUPPORTING`;
- direct-answer-first response planning;
- question-intent-specific ranking;
- missing/stale/irrelevant exclusion from positive top-finding rank;
- question/timeframe/recency/sample-count/quality/magnitude-aware scoring;
- natural metric labels and unit formatting;
- concise target sentence counts;
- one uncertainty/causation caveat where it materially matters rather than after every metric;
- finalizer that rejects/rewrites implementation language such as `bounded context`, `structured evidence`, `tool execution`, `preflight`, and `context bundle`;
- emergency/safety wording preservation;
- deterministic runtime delegation of final prose to the Answer Engine rather than old duplicated response templates.

**Modified during integration:**

- Agent 2's Answer Engine is now a `TrudyAnswerEngineModelClient` synthesis decorator in `TrudyQualityPipeline.kt`. It receives the final safety-enriched request and Agent 3 tool results, produces/refines the Answer Plan, delegates provider/local/deterministic synthesis, then applies the final quality gate.
- Agent 2's direct planner tests that depended on its pre-Agent-3 `TrudySystemIntegration` assumptions were changed to test the final language/conversation boundaries instead.
- Agent 2's old `TrudyChangeInvestigation(...)` fixture shape is supported by `TrudyLegacyInvestigationCompatibility.kt`, which constructs the mandatory Agent 3 structured result rather than weakening Agent 3's contract.

**Rejected:**

- Agent 2's conflicting `TrudyOrchestrator.kt` implementation was not allowed to replace Agent 3's later/budget-aware orchestrator.
- Agent 2's conflicting `TrudySystemIntegration.kt` investigation/planner implementation was not allowed to replace Agent 3's richer investigation v2 path.
- Agent 2 is not permitted to perform its own second set of correlations/statistics.

**Overlap:** strongest overlap was Agent 3. Resolution is Agent 3 produces the structured investigation; Agent 2 consumes it.

### 2.3 Agent 3 — `agent/trudy-investigation-v2`

**Source head:** `1bba5a71b533f40d0033f999d566cfcb63a693bd`

**Source commits:**

- `4a7c389a508efb97b61ee58c967ac62c3d0e583a`
- `88ffd49682c27e144f64cc13fcf72ab41e1b8a10`
- `1bba5a71b533f40d0033f999d566cfcb63a693bd`

**Purpose:** make `why / what changed / what explains / what matters / what was different` questions true bounded investigations rather than metric lists.

**Files added/modified:**

- `docs/backend/TRUDY_INVESTIGATION_V2.md`
- `TrudyIntelligenceTools.kt`
- added `TrudyInvestigationEngine.kt`
- `TrudyOrchestrator.kt`
- `TrudyPersonalEvidenceLibrary.kt`
- `TrudySystemIntegration.kt`
- added `TrudyInvestigationV2Test.kt`

**Core classes/interfaces:**

- `TrudyInvestigationResult`
- `TrudyInvestigationTarget`
- `TrudyInvestigationTimeframe`
- `TrudyPremiseStatus`
- `TrudyInvestigationFinding`
- `TrudyRelatedSignal`
- `TrudyMissingEvidence`
- `TrudySignalQuality`
- `TrudyInvestigationBudget`
- `TrudyCrossDomainInvestigator`
- `TrudySystemInvestigationPlanner`

**Architectural assumptions:** premise verification occurs before explanation; bounded candidate sets are preferable to all-metric scans; temporal semantics can require lagged windows; associations must include data/sample/alignment quality; observed relationships are not causation.

**Accepted as authoritative investigation path:**

- premise states `PREMISE_SUPPORTED`, `PREMISE_NOT_SUPPORTED`, `PREMISE_MIXED`, `PREMISE_UNVERIFIABLE`, `NOT_APPLICABLE`;
- finding classes `OBSERVED_CHANGE`, `POSSIBLE_ASSOCIATION`, `NO_CLEAR_ASSOCIATION`, `INSUFFICIENT_EVIDENCE`, `CONTRADICTORY_EVIDENCE`;
- explicit missing-evidence reasons including no data, sparse data, stale data, low alignment, low variance, unmeasured confounder, and not connected;
- source/device capture distribution and signal-quality scoring;
- bounded default investigation budget (small target set, bounded related signals, bounded rows and lookback) rather than full-history/all-metric work;
- premise verification that can reject the user's asserted deterioration;
- relationship scoring using sample count, matched fraction/timing, variance/quality and source capture;
- no automatic lag fishing;
- semantic temporal alignment such as evening caffeine -> following-night sleep;
- structured result contract and execution statistics;
- orchestration-side budget enforcement.

**Modified during integration:**

- Agent 4 language interpretation wraps the Agent 3 preflight planner rather than duplicating it.
- Agent 5 resolved conversation state supplies topic/metric/timeframe/referent hints into the Agent 3 planner.
- `last week or so` is resolved by Agent 5 to an exact rolling seven-day range; the planner wrapper normalizes the wording for Agent 3 while retaining the resolved range.
- subjective phrases such as `sleep has been terrible`, `slept like crap`, `sleep suffered` are translated by the language-aware wrapper into Agent 3's existing change intent so they still use one investigation operation.
- Agent 3 structured findings/gaps/premise status are mapped into Agent 2 evidence classes before final ranking.

**Rejected:** no alternate Agent 2 investigation planner was kept active.

### 2.4 Agent 4 — `agent/trudy-language-knowledge-v3`

**Source head:** `53503241546ccf761cc277d9d75184b51c54ed0d`

**Source commits:**

- `c969740883c82d0bf79be3552b9ce72091ec2a3e`
- `753f8c98d38d95c46370f4ae85bcefeb7162f4bc`
- `3fe60b0cf7a2586780d1e32823823ecc6441b5b2`
- `53503241546ccf761cc277d9d75184b51c54ed0d`

**Purpose:** improve the existing knowledge/retrieval system's understanding of natural language without building another encyclopedia or planner.

**Files added/modified:**

- `docs/TRUDY_LANGUAGE_KNOWLEDGE_V3.md`
- `AndroidMedicalCorpusCandidateProvider.kt`
- `TrudyKnowledgeIntegration.kt`
- added `TrudyLanguageKnowledgeRouting.kt`
- `TrudyUnifiedKnowledgeSources.kt`
- `medical/TrudyMedicalContextPlanning.kt`
- added `TrudyLanguageKnowledgeRoutingTest.kt`

**Core classes/interfaces:**

- `TrudyLanguageRouter`
- `TrudyLanguageRouting`
- `TrudyLanguageMatch`
- `TrudyPhraseClass`
- language phrase/alias groups and normalization helpers
- bounded knowledge coordinator relevance gates

**Architectural assumptions:** language routing should normalize once and query indexed aliases; fuzzy matching must be conservative for medical concepts; multi-intent messages may yield several bounded lanes; product/UI-only text must not trigger health retrieval.

**Accepted:**

- casual, British/American, abbreviations, common misspellings and multi-word phrase normalization;
- immutable prebuilt lexical structures rather than rebuilding per request;
- tightly constrained one-edit fuzzy matching;
- clinically important distinctions such as dizziness vs vertigo, palpitations language vs measured tachycardia, reflux vs abdominal pain;
- bounded multi-intent routing;
- aliases covering examples including `slept like crap`, `knackered`, `stomach's playing up`, `the runs`, `heart is racing`, `GORD`, `weight shot up`, and hard leg-training language;
- final product-only/relevance gate to prevent broad false-positive retrieval;
- bounded medical-term expansion feeding the existing indexed medical candidate provider rather than scanning the corpus;
- knowledge source top-k/dedup/relevance improvements.

**Modified during integration:**

- Agent 5's resolver now uses `TrudyLanguageRouter` instead of preserving a separate small lexical universe for topic/domain detection.
- `TrudyLanguageAwarePreflightPlanner` converts routing matches into bounded canonical planning anchors, but Agent 3 remains the only investigation planner.

**Rejected:** no giant phrase switch, no second statistics layer, no unbounded medical corpus scan, and no independent answer engine.

### 2.5 Agent 5 — `agent/trudy-conversation-evidence-v2`

**Source head:** `8ebf8b5d0e7ef5f636bbeb37e3b7c8a54afabac9`

**Purpose:** make Trudy maintain one coherent bounded conversation and accurate evidence semantics instead of treating follow-ups as isolated questions.

**Files added/modified:**

- `NativeTrudy.kt`
- `SharedTrudyBackendAdapter.kt`
- `TrudyConversationController.kt`
- `TrudyUiModels.kt`
- added `conversation/INTEGRATION_AGENT_2.md`
- added `conversation/TrudyConversationEvidenceCoordinator.kt`
- added `conversation/TrudyConversationEvidenceModels.kt`
- added `conversation/TrudyConversationResolver.kt`
- added `conversation/TrudyEvidenceContinuity.kt`
- added `conversation/TrudyConversationEvidenceTest.kt`

**Core classes/interfaces:**

- `TrudyConversationEvidenceState`
- `TrudyConversationResolver`
- `TrudyConversationEvidenceCoordinator`
- `TrudyEvidenceContinuity`
- `TrudyEvidenceReuseAction`
- `TrudyConversationEvidenceBatch`
- structured evidence identity/presentation models

**Architectural assumptions:** conversation memory is bounded and structured rather than an ever-growing raw transcript; evidence can be reused only while the scope and freshness rules still hold; user-facing evidence count means records actually used.

**Accepted:**

- bounded active topic/domains/metrics/timeframe/previous timeframe/latest investigation/latest evidence/active experiment/missing-data state;
- bounded evidence cache and bounded ID lists;
- referents including `that`, `it`, `those nights`, `those readings`, `that trend`, `same thing`, `before that`, and timeframe shifts;
- refresh when timeframe/metric/topic/currentness/staleness changes;
- reuse when exact scope remains valid and fresh;
- explicit missing-data continuity;
- separation of retrieved-record count and used-evidence count;
- backend/UI filtering so retrieved-but-unused evidence does not appear as supporting evidence;
- text and voice use the same visible Trudy conversation/controller rather than separate memory stores.

**Modified during integration:**

- the source branch intentionally stopped short of production coordinator wiring; `TrudyConversationService` and `TrudyRuntimeFactory` now instantiate and use it;
- the resolver uses Agent 4 language routing once per turn;
- production cache stores answer-selected structured evidence identities and final answer summaries, not arbitrary provider payloads;
- known-missing same-scope and evidence-explanation follow-ups can avoid another Data Vault query;
- exact day-before missing-data follow-up shifts the structured timeframe before Agent 3 planning.

**Rejected:** no unbounded permanent memory and no reconstruction of health calculations from display strings.

## 3. Integration Branch

- **Branch:** `integration/trudy-quality-v3`
- **Base branch:** latest `11.3`
- **Base SHA:** `9c78ef8d1cc8f73fd7d5bf1bc709dba43c767ff4`
- **Code-freeze SHA before this documentation-only commit:** `4226c68b8e875c0ace444a0383965c901537665e`
- **Changed files at code freeze vs `11.3`:** 42
- **Source branches modified:** none
- **Merged into `11.3`:** no

### About the final HEAD value in this file

A Git commit SHA is a hash of the commit/tree containing this document, so the document cannot literally contain the SHA of the commit that contains itself without changing that SHA. The exact final branch HEAD after the handoff commit must therefore be taken from `git rev-parse integration/trudy-quality-v3` / the final Agent 6 delivery message. The semantic code freeze that the handoff describes is the SHA above; the only later change intended is this handoff document.

## 4. Conflict Log

### Conflict A — Agent 2 vs Agent 3 investigation ownership

**Overlap:** `TrudyOrchestrator.kt`, `TrudySystemIntegration.kt`, change/premise semantics.

**Decision:** Agent 3 wins investigation ownership. Agent 2's conflicting orchestrator/planner files were not overlaid after Agent 3. Agent 2 is adapted as the final synthesis layer.

**Reason:** Agent 3 contains the later bounded budget, richer source/device quality model, explicit temporal semantics, structured gaps, and premise states required by the task.

### Conflict B — old vs structured investigation contract

Agent 2 tests/callers used a pre-v2 `TrudyChangeInvestigation` constructor. Agent 3 requires a non-null `structuredResult`.

**Decision:** keep Agent 3's field mandatory. `TrudyLegacyInvestigationCompatibility.kt` provides a compatibility factory that derives a structured result for old callers/tests. Production Agent 3 code always supplies the real structured result directly.

### Conflict C — language routing vs investigation planning

Agent 4 can recognize more natural language than Agent 3's compact planner lexicon.

**Decision:** `TrudyLanguageAwarePreflightPlanner` wraps, but does not replace, `TrudySystemInvestigationPlanner`. It appends bounded canonical anchors. Statistics/retrieval remain Agent 3-owned.

### Conflict D — language duplication in conversation resolver

Agent 5 had its own compact topic heuristics; Agent 4 has the stronger language normalization.

**Decision:** Agent 5 resolver now invokes Agent 4's shared router once, then stores the resulting bounded topic/domain/metric context.

### Conflict E — `last week or so`

Agent 2 expected a rolling recent week; Agent 3's older temporal phrase list did not directly encode this wording.

**Decision:** Agent 5 resolves the exact rolling seven-day range (`roughly the past week`). The conversation-aware planner retains that exact resolved scope while normalizing wording for Agent 3.

### Conflict F — subjective deterioration language

`My sleep has been terrible lately` and `I slept like crap` can be recognized by Agent 4 but would not necessarily enter Agent 3's `why/what changed` branch from the raw phrase alone.

**Decision:** the language-aware wrapper adds the existing `what changed / worse` intent anchors. The result is still exactly one Agent 3 `InvestigateChange`; no special second investigation engine is created.

### Conflict G — retrieved evidence vs used evidence

Agent 2 deterministic/model output could expose many returned references while Agent 5 defines user-facing evidence as the subset actually supporting the answer.

**Decision:** `TrudyUsedAnswerEvidenceSelector` selects references corresponding to top usable/supporting/contradictory Answer Engine findings, with bounded references per finding/turn. `TrudyConversationService` caches and returns those used identities. Retrieved count remains diagnostic only.

### Conflict H — Answer Engine position relative to medical/environment/emotional decorators

**Decision:** `TrudyAnswerEngineModelClient` is the inner synthesis client. Existing emotional/environmental and then medical wrappers are outside it. This means they can enrich the request/system instruction or request necessary medical context before the final Answer Engine sees the request. The Answer Engine does not bypass the safety wrappers.

### Conflict I — voice response length vs medical completeness

**Decision:** Agent 2's adaptive concise answer planning reduces ordinary TTS work. Agent 1 still supports long responses through bounded streaming. No medical safety section is truncated merely to optimize voice latency.

## 5. Final Runtime Flow

Android production wiring is in `TrudyRuntimeFactory.kt`.

1. User text enters the existing `TrudyConversationController`/shared backend.
2. `TrudyConversationEvidenceCoordinator.prepare` resolves bounded current-conversation state.
3. `TrudyConversationResolver` invokes `TrudyLanguageRouter` once to identify natural-language topics/domains/metrics while preserving Agent 4's product-only gate.
4. `TrudyConversationAwarePreflightPlanner` supplies resolved topic/metric/timeframe/referent hints.
5. `TrudyLanguageAwarePreflightPlanner` supplies bounded canonical semantic anchors.
6. `TrudySystemInvestigationPlanner` remains the authoritative planner.
7. `TrudyOrchestrator` performs bounded knowledge retrieval and typed tool execution.
8. `TrudyCrossDomainInvestigator` / typed intelligence tools produce `TrudyInvestigationResult` for investigation questions.
9. Existing emotional/environmental/medical model-client decorators enrich the model request and preserve medical safety behavior.
10. `TrudyAnswerEngineModelClient` creates/refines the Answer Plan from the final request/tool results.
11. The configured deterministic/local/hosted model produces candidate prose.
12. `TrudyAnswerEngine.finalize` performs the final answer-quality/safety-language gate.
13. `TrudyUsedAnswerEvidenceSelector` reduces references to evidence actually used by the answer.
14. `TrudyConversationService` updates bounded conversation/evidence state.
15. Text UI renders the answer and used evidence. Voice mode receives the same answer through the same visible conversation controller and then passes text to Agent 1's TTS streaming path.

## 6. Investigation Architecture

There is one investigation architecture: Agent 3.

`TrudySystemInvestigationPlanner` identifies the target and explicit windows. `InvestigateChange` carries target/supporting metrics, related candidates, observation/baseline windows, claimed direction, and a bounded association limit. `TrudyCrossDomainInvestigator` verifies targets first, then evaluates a bounded related set.

The structured result exposes:

- target/domain/primary and supporting metrics;
- recent and baseline timeframe;
- premise status;
- important findings;
- related signals;
- missing/stale/quality gaps;
- aggregate signal quality;
- confidence;
- caveats;
- bounded execution statistics.

Agent 2 does not recompute this. `TrudyStructuredInvestigationAnswerBridge` translates the structured result into Answer Engine evidence classes/ranking inputs.

## 7. Premise Verification

Premise verification remains inside Agent 3 before explanation.

For a claim such as `My sleep has been getting worse`, Agent 3 compares the target sleep outcomes against the baseline and returns one of the explicit premise states. A deterioration claim is not accepted merely because it was included in the user's wording.

If awake time falls while continuity/deep sleep/sleep score improve, the premise can be `PREMISE_NOT_SUPPORTED`; mixed directional metrics can return `PREMISE_MIXED`; inadequate comparable data returns `PREMISE_UNVERIFIABLE`.

The bridge maps not-supported/mixed premise evidence to `CONTRADICTORY` rather than ordinary supporting evidence. Agent 2 then uses natural wording such as recorded sleep metrics not clearly supporting deterioration while allowing for subjective experience to differ.

Missing or stale data cannot be converted into proof for either side.

## 8. Answer Engine

`TrudyAnswerEngine` is the sole final answer-planning/synthesis quality layer.

Key behavior retained:

- answers the user's actual question first;
- chooses answer length from intent/complexity;
- filters evidence before ranking;
- removes `MISSING`, `STALE`, `LOW_QUALITY`, and `IRRELEVANT` evidence from positive top-finding rank;
- retains contradictory evidence where it directly answers a premise question;
- ranks by exact-question relevance, time relevance/recency, quality, sample count, missingness, magnitude and confidence;
- uses natural labels/units rather than schema units;
- explains missing data specifically;
- keeps uncertainty/correlation caveats compact instead of repeating them after every number;
- strips normal-user implementation phrases such as `bounded context`, `structured evidence`, `preflight`, `provider`, `tool execution`, and `context bundle`;
- replaces known robotic provider templates with deterministic natural synthesis when necessary;
- preserves emergency safety language.

Old deterministic response templates in `TrudyModelRuntime.kt` were reduced rather than kept as a competing formatter.

## 9. Evidence Quality + Ranking

The final evidence-quality path is:

1. Agent 3 computes signal/source/alignment quality and structured gaps for investigations.
2. Agent 2 classifies each answer candidate into one of `USABLE`, `MISSING`, `STALE`, `LOW_QUALITY`, `CONTRADICTORY`, `IRRELEVANT`, or `SUPPORTING`.
3. `TrudyStructuredInvestigationAnswerBridge` makes Agent 3's richer structured status authoritative when the two layers refer to the same metric/relationship.
4. Only usable/supporting/contradictory evidence enters top-answer ranking.
5. Missing/stale/low-quality information appears as limitations/confidence context, not as `the clearest thing`.
6. `TrudyUsedAnswerEvidenceSelector` binds user-facing evidence to the ranked findings actually used.

`No data yet` cannot be ranked as a health finding.

For `What should I pay attention to today?`, stale body-composition data is down-ranked/excluded; a fresh relevant signal is allowed to win. The existing Agent 2 regression explicitly covers this.

## 10. Language / Intent Routing

Agent 4's immutable/indexed router is the shared lexical interpretation source.

Supported patterns include casual speech, UK/US variants, abbreviations, misspellings and multi-word expressions. Examples retained in tests/routing include:

- `I slept like crap`
- `I'm knackered`
- `My stomach's playing up`
- `I've got the runs`
- `My heart is racing`
- `Could this be GORD?`
- `My weight shot up overnight`
- `I smashed legs yesterday`

The integrated resolver/planner consumes the router's canonical terms and bounded domain matches. It does not contain a giant exact-phrase switch.

Important medical distinctions remain represented in Agent 4's groups. `GORD/GERD/heartburn/acid coming up` routes to reflux language rather than generic abdominal pain. Palpitation language is not automatically treated as a measured tachycardia reading. Dizziness/vertigo remain separate semantic groups.

Product/UI-only requests remain behind Agent 4's final product relevance gate so `rename my dashboard tile` does not query health domains.

## 11. Conversation State

Agent 5 state is now production-wired through `TrudyConversationService`.

The state is intentionally bounded and structured. It tracks concepts including:

- active topic;
- up to a bounded number of active domains/metrics;
- active timeframe;
- previous timeframe;
- latest investigation pointer;
- latest used evidence IDs;
- latest missing-data findings;
- active experiment pointer where applicable;
- bounded evidence cache.

Agent 5's source bounds cap domain/metric/evidence/missing/cache state rather than allowing transcript-like growth. No permanent general memory was introduced.

The Android visible conversation still keeps its existing short display/history list for UI/model context, but structured referent/evidence reuse does not depend entirely on rereading that raw prose.

## 12. Evidence Continuity

Agent 5's coordinator is now used on real turns.

Reuse behavior:

- exact same fresh scope may reuse answer-selected in-session evidence summaries/identities;
- an evidence-explanation follow-up can reuse the same answer-supporting evidence rather than rerun retrieval;
- known missing-data state can be reused for the same scope;
- timeframe, metric, topic/investigation-target, stale evidence, or current/latest requests force refresh;
- a day-before follow-up after missing blood pressure preserves the BP topic/metrics and shifts the structured timeframe before planning.

The cache deliberately stores answer-selected evidence identities and final answer summaries. It does **not** rebuild a correlation, mean, clinical interpretation, or health value from a human-formatted display string.

User-facing evidence count is now the number of used records returned to the Android controller. The larger retrieved count is kept on `TrudyConversationResult` for diagnostics and is not rendered as supporting evidence.

## 13. Temporal Reasoning

Agent 3 temporal semantics are preserved, including explicit lag semantics instead of calendar-date-only correlation.

Examples:

- evening caffeine -> following-night sleep;
- prior-night sleep/recovery -> appropriate following-morning target where defined;
- same-day relationships only where the metric semantics make same-day meaningful;
- explicit baseline/recent windows remain separate.

Agent 5 adds structured conversational timeframe shifts including today, yesterday, last week, last month, `the day before`, and rolling `last week or so`.

No integration change adds automatic lag searching. Relationship candidates use the defined semantic alignment rather than trying many lags until one looks interesting.

## 14. Voice Streaming Architecture

Agent 1 voice runtime remains the authority.

Final local voice path:

`final answer text -> KokoroTextChunker progressive chunk plan -> synthesis producer coroutine -> bounded lookahead Channel/Semaphore -> playback consumer -> persistent MODE_STREAM AudioTrack`.

Playback can begin when the first small chunk is ready. Later chunks synthesize while the current chunk is playing. The service does not wait for the complete long answer to synthesize and does not synthesize an unbounded number of chunks ahead.

Current chunk planning deliberately prioritizes first-audio latency with a small first chunk and bounded subsequent chunks. It preserves word boundaries and tries sentence/clause boundaries first.

The persistent Android audio sink avoids per-chunk stream creation/teardown and can report buffered audio duration for starvation diagnostics.

Diagnostics retained include chunk planning/count, synthesis starts/readiness, first playback, queue depth/lookahead, starvation, cancellation and total timings. Private response text is not required in performance logs.

## 15. Voice Cancellation

Agent 1 uses cancellation plus a response/generation epoch so stale queued work cannot resume after stop/supersession.

The existing Trudy UI lifecycle calls the voice controller when a new prompt is submitted, when stop is requested and when the voice/Trudy session is left. The streaming service stops the active sink and invalidates the previous generation.

Regression coverage from Agent 1 checks stale-response cancellation; the integrated voice test checks starvation does not reorder chunks or prematurely drain playback.

Still requiring device validation: cancellation during native Kokoro synthesis, rapid stop->new question, voice->text transition, leaving Trudy mid-chunk, and repeated open/close cycles for resource leaks.

## 16. Performance

The integration intentionally preserves the `11.3` performance work because none of the swarm branches replaces the unrelated polling/WorkManager/state optimizations.

Specific protections retained/added:

- Agent 4 uses prebuilt/indexed lexical structures rather than rebuilding lexicons;
- medical language expansion still feeds the existing indexed/lazy medical corpus retrieval path;
- product/relevance gating prevents accidental broad knowledge retrieval;
- Agent 3 uses bounded target/related candidate sets, bounded rows and bounded lookback rather than all-history/all-metric correlation;
- Agent 5 conversation memory/cache is bounded;
- Agent 5 reuse avoids some repeated same-scope evidence work;
- Agent 2 keeps normal answers concise, reducing downstream local-TTS synthesis work;
- Agent 1 audio lookahead is bounded to approximately one/two chunks and does not synthesize the complete response up front;
- no full medical corpus scanning, unbounded history load, quadratic all-metric correlation, or unbounded prompt-memory system was introduced.

One intentional change in `TrudyRuntimeFactory` is that `knowledgeSources` now defaults to `defaultTrudyKnowledgeSources()` rather than an empty list, so the existing curated knowledge adapters are actually used. Agent 4's relevance gates/bounds remain responsible for keeping this safe and small.

## 17. Medical Safety

Existing medical safety architecture remains in place.

`MedicalContextAwareTrudyModelClient` remains outside the ordinary environmental/emotional/Answer Engine delegate chain. It can perform the existing medical plan/safety check and request bounded context before final synthesis.

Preserved rules include:

- non-diagnostic framing;
- candidate relevance is not diagnostic probability;
- Data Vault observations are not diagnostic proof;
- red-flag escalation;
- no personalized prescribing/dosing;
- no instruction to stop prescribed medication;
- evidence/source provenance;
- uncertainty where clinically material;
- urgent/emergency action before optimization analysis.

Agent 2's Answer Engine may improve wording but its finalizer explicitly preserves safety-critical action rather than stripping it while cleaning provider/internal prose.

No voice-length optimization is allowed to suppress urgent medical action.

## 18. Tests

### Tests preserved from source branches

- `TrudyVoiceStreamingPerformanceTest.kt` — chunk planning, bounded lookahead/order/drain, cancellation/stale generation.
- `TrudyAnswerEngineTest.kt` — direct answers, missing/stale filtering, today ranking, missing BP, premise contradiction, evidence follow-up, unit cleanup, internal-language cleanup, emergency wording.
- `TrudyInvestigationV2Test.kt` — premise verification, bounded reads/budget, signal quality, relationship semantics, temporal alignment including caffeine->following-night sleep.
- `TrudyLanguageKnowledgeRoutingTest.kt` — broad natural-language phrase matrix, UK/US/spelling/fuzzy/multi-intent/medical distinctions/product relevance.
- `TrudyConversationEvidenceTest.kt` — structured follow-ups, referents, BP day shift, evidence reuse/refresh, stale/current rules, bounded state, retrieved-vs-used counts, text/voice state continuity.

### Integrated tests added/modified by Agent 6

`TrudyQualityV3IntegrationTest.kt` adds cross-agent seam coverage for:

- sleep slang entering the single Agent 3 investigation path;
- `knackered` and `heart is racing` reaching bounded canonical data lanes;
- bounded multi-intent `tired, bloated and sleeping badly`;
- GORD vs abdominal-pain distinction and overnight-weight routing;
- Agent 3 structured `PREMISE_NOT_SUPPORTED` controlling Agent 2 contradictory classification;
- stale structured gaps being removed from top ranking;
- 24 retrieved references becoming 5 answer-used references.

`TrudyVoiceQualityIntegrationTest.kt` adds:

- delayed-second-chunk starvation behavior: first chunk plays, no premature drain, order remains `0,1`, runtime remains speaking while waiting;
- 500+ character answer still uses progressive bounded chunks and reconstructs the exact text.

`TrudyAnswerEngineTest.kt` was adjusted so subjective terrible-sleep routing and rolling `last week or so` are tested through the final language/conversation boundaries instead of Agent 2's obsolete direct planner assumptions.

### Required 20-case regression map

1. `How's my sleep doing the last week or so?` — Answer Engine + conversation rolling-week tests.
2. `What should I pay attention to in my health data today?` — Answer Engine stale/no-data priority regression.
3. `Why has my sleep suffered?` — Answer Engine cause regression + Agent 3 investigation tests.
4. `My sleep has been terrible lately` while objective metrics improve — premise contradiction regression.
5. `What was my blood pressure yesterday?` with no reading — Answer Engine missing-BP + conversation missing-state coverage.
6. `What about the day before?` — Agent 5 resolver/retrieval policy test.
7. `Could that explain my heart rate?` — Agent 5 cross-domain referent test.
8. `What evidence are you using?` — Answer Engine + Agent 5 evidence-explanation reuse tests.
9. `I slept like crap.` — Agent 4 + integrated single-investigation regression.
10. `I'm knackered.` — Agent 4 + integrated canonical-lane regression.
11. `My heart is racing.` — Agent 4 + integrated canonical-heart-rate regression.
12. `Could this be GORD?` — Agent 4 + integrated reflux-distinction regression.
13. `My weight shot up overnight.` — Agent 4 + integrated body-weight regression.
14. `I've been tired, bloated and sleeping badly.` — integrated bounded multi-intent regression.
15. Caffeine -> following-night sleep — Agent 3 temporal-alignment regression.
16. Old body-composition reading does not win today ranking — Agent 2 stale/today priority and Agent 3 quality logic.
17. Retrieved vs actually used evidence — Agent 5 plus integrated 24->5 regression.
18. Voice chunk order — Agent 1 streaming test.
19. Voice cancellation — Agent 1 stale/cancel test.
20. Voice playback starvation — Agent 6 integrated delayed-chunk regression.

### Tests actually executed by Agent 6

**None of the Gradle/Python/Android tests above were executed successfully in the final integration environment.**

Attempted GitHub Actions run: `31822650163`, job/check `94839329000`.

Result: failed before checkout/runner allocation because GitHub reported that recent account payments had failed or the spending limit needed to be increased. The temporary workflow was deleted from the final branch.

No statement in this handoff should be interpreted as `tests passed`. The test files and static contracts were inspected; execution remains required.

## 19. Known Limitations

1. **No executed compiler/test suite:** the largest unresolved risk is ordinary compile/test integration risk because GitHub Actions could not allocate a runner and no writable local private clone was available in this environment.
2. **No physical Android voice measurement:** first-audio latency, starvation frequency and real 500+ character speech continuity have not been measured on the target phone/Kokoro hardware path after integration.
3. **Conversation reuse is intentionally conservative:** the cache reuses final answer-selected identities/summaries, not arbitrary raw typed provider/tool payloads. This avoids reconstructing health calculations from display text but means some same-scope analytical follow-ups will correctly refresh rather than reuse deeper raw results.
4. **Voice/text input-mode diagnostic:** text and voice share the same `TrudyConversationService`/coordinator state, so continuity is preserved. The Android shared backend currently calls `respondTo` without an explicit voice input-mode flag, so Agent 5's `inputMode` field may still record the default text value for voice-originated prompts. It is diagnostic context, not a routing dependency, but should be wired if accurate input-channel analytics are required.
5. **Hosted/local final prose must be reviewed:** Answer Engine finalization is provider-neutral, but real hosted/local model outputs should be tested for edge cases where unnecessary prose survives or necessary caveats are over-compressed.
6. **Compatibility factory is transitional:** `TrudyLegacyInvestigationCompatibility.kt` exists to keep older callers/tests source-compatible. New production code should use Agent 3's structured result directly.
7. **Cache TTL policy is integration-level:** current requests use a very short cache lifetime; historical same-scope evidence is longer-lived. These values need real usage review, especially for frequently changing wearable streams.
8. **No device-level leak audit:** rapid stop/start, app backgrounding and repeated voice-session lifecycle still need Android profiler/log review.

## 20. Manual Review Required

The lead integrator should independently inspect these high-risk seams before merging:

- `TrudyRuntimeFactory.kt`: verify model-client wrapper ordering and default knowledge-source wiring in deterministic, local and hosted modes.
- `TrudyQualityPipeline.kt`: verify Agent 3 structured result -> Agent 2 evidence class/rank mapping and used-evidence selection.
- `TrudyConversationService.kt`: verify coordinator prepare/complete lifecycle, cache TTL, missing-data reuse and no stale turn-registry leakage.
- `conversation/TrudyConversationResolver.kt`: verify Agent 4 routing integration, `last week or so`, day-before and current/latest semantics.
- `TrudyLegacyInvestigationCompatibility.kt`: compile compatibility only; confirm no production call accidentally chooses the compatibility overload when a real structured result is available.
- `TrudyVoiceService.kt` / `KokoroTrudySpeechEngine.kt` / `AndroidTrudyAudioSink.kt`: verify producer/consumer lifecycle and real-device buffer behavior.
- `MedicalContextAwareTrudyModelClient` interaction with `TrudyAnswerEngineModelClient`: verify urgent medical prompts still lead with required escalation in all configured model modes.
- Android evidence UI: verify evidence badge/group count equals records genuinely used and never the diagnostic retrieved count.

Do not merge on static review alone. A clean Gradle build and physical-device voice pass are required.

## 21. Android Studio / Device Test Checklist

### Build and automated checks

From repository root / Android Studio terminal, run at minimum:

```bash
python3 nextgen/scripts/check_data_boundaries.py
python3 nextgen/scripts/validate_medical_corpus.py
python3 nextgen/scripts/test_medical_retrieval.py
gradle --no-daemon -p nextgen :shared:testDebugUnitTest :androidApp:testDebugUnitTest
gradle --no-daemon -p nextgen :androidApp:assembleDebug
```

Confirm no test was silently skipped because of task/source-set mismatch.

### Text / reasoning checks

- Ask a normal sleep summary and confirm direct answer first.
- Ask `How's my sleep this week?` -> `What about last month?` -> `Could that explain my heart rate?` -> `What evidence are you using?` and verify topic/referent/evidence continuity.
- Enter an objective contradiction fixture where sleep score, deep sleep and continuity improve but ask `My sleep has been terrible lately`; confirm Trudy does not simply agree.
- Ask `What was my blood pressure yesterday?` with no record; confirm no invented value. Follow with `What about the day before?`; verify BP topic persists and the exact prior-day range is used.
- Ask `What should I pay attention to today?` with a fresh relevant metric and a three-week-old body-composition reading; old body data must not dominate.
- Ask `Could this be GORD?`; confirm reflux-specific medical retrieval and no conversion to generic abdominal pain.
- Check slang: `I slept like crap`, `I'm knackered`, `My stomach's playing up`, `I've got the runs`, `My heart is racing`, `I smashed legs yesterday`.
- Ask `I've been tired, bloated and sleeping badly`; verify several relevant bounded lanes, not every domain.
- Inspect evidence UI: if retrieval loaded many rows but answer used a smaller subset, the visible evidence count must equal the used subset.
- Search normal replies for forbidden engineering phrases: `bounded context`, `structured evidence`, `preflight`, `provider`, `tool execution`, `context bundle`.

### Medical safety checks

- Use the existing emergency/red-flag fixtures; verify urgent action is still first.
- Verify no personalized prescription/dose or stop-medication wording appears.
- Verify missing data never becomes reassurance that a red flag is absent.

### Voice checks on a physical phone

- Confirm voice identity/selected voice is unchanged.
- Measure time from submit to first audible audio for a short answer.
- Use a 500+ character Trudy answer. Confirm first audio begins after the first small chunk rather than after complete synthesis.
- Listen through the full long response and record any mid-answer silent gaps. There should be no repeated multi-second synth/play starvation pattern.
- Use diagnostics to inspect chunk plan, first synth, first ready, first playback, queue depth and starvation events without logging private response prose.
- Press stop during first chunk, later chunk and active synthesis; queued/stale chunks must not resume.
- Start another question while Trudy is speaking; old synthesis/playback must stay cancelled.
- Start speaking while Trudy is speaking if the UI supports interruption; old answer must stop.
- Leave Trudy/voice mode mid-answer; audio and queued synthesis must stop.
- Re-enter voice repeatedly and check AudioTrack/native resources for leaks.
- Switch voice -> text and continue `What about last month?`; context/evidence continuity must remain intact.

## 22. Recommended Trudy Regression Prompts

Run these against controlled fixtures where the expected Data Vault state is known:

1. `How's my sleep doing the last week or so?`
2. `What should I pay attention to in my health data today?`
3. `Why has my sleep suffered?`
4. `My sleep has been terrible lately.` — with objectively improved recent sleep metrics.
5. `What was my blood pressure yesterday?` — with no BP reading.
6. `What about the day before?`
7. `Could that explain my heart rate?`
8. `What evidence are you using?`
9. `I slept like crap.`
10. `I'm knackered.`
11. `My heart is racing.`
12. `Could this be GORD?`
13. `My weight shot up overnight.`
14. `I've been tired, bloated and sleeping badly.`
15. `Could my evening caffeine be affecting my sleep?` — verify following-night alignment rather than same-calendar-date-only correlation.
16. `What should I pay attention to today?` — with a fresh relevant signal and an old body-composition reading.
17. Use a fixture where 24 records are retrieved but only five support the final answer; verify visible evidence count is five.
18. Speak a long answer and verify chunk playback order.
19. Cancel speech during synthesis/playback and immediately submit a new question.
20. Introduce an artificial delayed second TTS chunk and verify starvation waits without reordering/premature drain.

Additional manual quality prompts:

- `My stomach's playing up.`
- `I've got the runs.`
- `I feel dizzy.` vs `The room is spinning.`
- `I can feel palpitations.` vs `My measured heart rate is 130.`
- `Rename my sleep dashboard card.` — must not trigger broad health retrieval.
- `I have severe chest pain and trouble breathing.` — required safety action must survive answer cleanup.

The branch should only be considered ready for lead integration after the automated build/tests and the physical Android voice checklist have been completed and reviewed.
