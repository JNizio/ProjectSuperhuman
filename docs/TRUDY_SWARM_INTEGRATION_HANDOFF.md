# Trudy Swarm Integration Handoff

## 1. Executive Summary

This branch integrates the five Trudy swarm branches onto the exact `11.3` base while keeping Agent 2's system-integration work as the orchestration spine. The result is one Trudy runtime, one typed evidence/tool path, one canonical Data Vault boundary, one medical safety/candidate path, and a unified bounded knowledge-provider boundary that adapts the medical-management, nutrition/body, and performance knowledge repositories without flattening their useful domain-specific schemas.

The key architectural addition made during final integration is the `TrudyKnowledgeProvider` / `TrudyKnowledgeCoordinator` boundary in `nextgen/shared/src/commonMain/kotlin/com/projectsuperhuman/next/trudy/TrudyKnowledgeIntegration.kt`, backed by adapters in `TrudyUnifiedKnowledgeSources.kt`. Agent 4 and Agent 5 metric mappings are converted to `TrudyKnowledgeMetricHint` values and may enrich Agent 2's bounded preflight plan in `TrudyOrchestrator`; they do not introduce a second statistics engine or query every domain. Medical knowledge and experiment-methodology knowledge are deliberately excluded from automatic personal-metric expansion.

The final runtime flow remains centered on `TrudyRuntimeFactory`, `TrudyOrchestrator`, `TrudySystemInvestigationPlanner`, typed `TrudyToolExecutor` services, and `MedicalContextAwareTrudyModelClient`. Voice mode is a presentation/input-output layer over the same `TrudyConversationController` used by text chat, so it does not create a second Trudy brain.

Canonical personal observations remain in the Project Superhuman Data Vault. Curated knowledge remains read-only reference knowledge. Personal associations remain deterministic calculations over canonical observations and are explicitly not causation. Medical retrieval remains candidate/context retrieval rather than diagnostic probability.

**Validation status:** source tests and new integration tests are present in the branch, but the complete Gradle/Python validation suite was **NOT EXECUTED in GitHub Actions** because GitHub refused to allocate a runner due to the repository/account Actions billing or spending-limit state. Run `31813899359`, job `94810831280`, failed before checkout with the annotation: `The job was not started because recent account payments have failed or your spending limit needs to be increased.` This is an infrastructure blocker, not evidence that the code passed or failed. Local Android Studio/Gradle validation is therefore mandatory before merging.

## 2. Source Branches

### 2.1 `agent/trudy-voice-experience`

- Source commit: `09f1d4cedf084608326b5f57b282b32ef7d81b4d`
- Commit message: `Add dedicated Trudy voice experience`
- Purpose: dedicated full-screen Trudy voice UX while preserving the existing conversation/runtime.
- Important files:
  - `nextgen/androidApp/src/main/AndroidManifest.xml`
  - `nextgen/androidApp/src/main/java/com/projectsuperhuman/next/AndroidTrudySpeechRecognizer.kt`
  - `nextgen/androidApp/src/main/java/com/projectsuperhuman/next/NativeTrudy.kt`
  - `nextgen/androidApp/src/main/java/com/projectsuperhuman/next/TrudyVoiceExperience.kt`
  - `nextgen/androidApp/src/test/java/com/projectsuperhuman/next/TrudyVoiceExperienceTest.kt`
- Accepted:
  - `RECORD_AUDIO` permission and optional microphone feature declaration.
  - Android speech-recognition controller with partial results, amplitude, permission/error/no-input states, free-form recognition, locale support and on-device/offline preference where available.
  - Full-screen voice experience with real states `IDLE`, `LISTENING`, `THINKING`, `SPEAKING`, `NO_INPUT`, `ERROR`.
  - Turquoise/cyan/aqua/light-blue animated organic orb.
  - TTS integration through the existing voice output controller.
  - Keyboard/text-chat escape path and conversation continuity.
  - Runtime-derived context labels only. `TrudyVoiceContextMapper` maps `TrudyReply.activity` and structured evidence labels/IDs; it does not parse the prose answer to invent consulted modules.
  - Accessibility semantics such as `Return to Trudy text chat`, voice-settings state, and the orb state/guidance content description.
- Modified during final integration: no semantic rewrite was needed. Voice was integrated last, after the runtime/data/knowledge spine was stable.
- Rejected: no second conversation service/runtime; no answer-text-derived fake context labels.

### 2.2 `agent/trudy-system-integration`

- Source commit: `0060448c266bbcd0691f8a3b7668f9404e04e96a`
- Commit message: `Integrate Trudy across Project Superhuman`
- Purpose: make Trudy aware of canonical Project Superhuman modules, add bounded temporal/cross-domain investigation, canonical experiment contracts, evidence continuity, and efficient Data Vault retrieval.
- Important files:
  - `nextgen/androidApp/src/main/java/com/projectsuperhuman/next/AndroidTrudyTemporalBoundaryProvider.kt`
  - `nextgen/androidApp/src/main/java/com/projectsuperhuman/next/TrudyRuntimeFactory.kt`
  - `nextgen/shared/src/commonMain/kotlin/com/projectsuperhuman/next/trudy/TrudySystemIntegration.kt`
  - `TrudyCanonicalExperiments.kt`
  - `TrudyKnowledgeIntegration.kt`
  - `TrudyOrchestrator.kt`
  - `TrudyPersonalEvidenceLibrary.kt`
  - `TrudyHealthContextService.kt`
  - `TrudyToolContract.kt`
  - `TrudyIntelligenceTools.kt`
  - `nextgen/shared/src/commonMain/kotlin/com/projectsuperhuman/next/core/DataVaultContracts.kt`
  - `nextgen/shared/src/commonMain/kotlin/com/projectsuperhuman/next/core/ModuleParity.kt`
  - `nextgen/shared/src/commonMain/kotlin/com/projectsuperhuman/next/data/SqlDataVaultGateway.kt`
  - `SqlHealthRepository.kt`
  - `nextgen/shared/src/commonTest/kotlin/com/projectsuperhuman/next/trudy/TrudySystemIntegrationTest.kt`
- Accepted as the central architecture:
  - `TrudySystemCatalog` canonical module/metric map.
  - `TrudyTemporalResolver` and Android device-local calendar boundaries.
  - `TrudySystemInvestigationPlanner` as the main query/investigation planner.
  - `InvestigateChange` / `TrudyCrossDomainInvestigator` with premise verification, baseline comparison, bounded related associations, missing-data reporting and association-not-causation caveat.
  - Exact evidence binding and follow-up evidence keys.
  - Metric-specific Data Vault history/window access rather than full-history scans.
  - Canonical experiment repository contracts and `NOT_CONNECTED` behavior when persistence is absent.
  - Runtime wiring in `TrudyRuntimeFactory` and the provider-neutral model/tool loop.
- Modified during final integration:
  - `TrudyKnowledgeIntegration.kt` was expanded from a small source enum/coordinator into the unified provider contract described below, while retaining backward compatibility with Agent 2 tests/custom sources.
  - `TrudyOrchestrator.kt` now retrieves bounded curated knowledge before executing preflight and can enrich Agent 2's plan with exact metric hints from relevant non-medical/non-methodology knowledge.
- Rejected: none of Agent 2's core investigation/statistics architecture was replaced.

### 2.3 `agent/trudy-medical-knowledge-v2`

- Source commit: `d1578e8cfe5e5e8128c21f320e8527355d2f2902`
- Commit message: `Expand Trudy medical knowledge and indexed retrieval`
- Purpose: expand the existing condition/symptom corpus and make retrieval indexed, bounded, provenance-aware and linguistically broad.
- Important files:
  - `docs/backend/TRUDY_MEDICAL_KNOWLEDGE_V2.md`
  - `nextgen/medical-knowledge/conditions.v1.json`
  - `condition.schema.json`
  - `medical-lexical-index.v1.json` and schema
  - `symptom-index.v1.json` and schema
  - `symptom-language.v1.json` and schema
  - `nextgen/androidApp/src/main/java/com/projectsuperhuman/next/AndroidMedicalCorpusCandidateProvider.kt`
  - `nextgen/scripts/validate_medical_corpus.py`
  - `nextgen/scripts/test_medical_retrieval.py`
  - `nextgen/shared/src/commonMain/kotlin/com/projectsuperhuman/next/medical/MedicalKnowledgeModels.kt`
- Source-branch measured expansion:
  - conditions: 64 -> 188
  - canonical symptom/feature IDs: 212 -> 597
  - condition aliases: 106 -> 448
  - symptom aliases: 27 -> 268
  - total aliases: 133 -> 716
  - source records: 62 -> 186
  - contextual red flags: 96 -> 294
  - body-system domains: 16 -> 18
  - all 64 pre-existing condition IDs preserved.
- Accepted:
  - expanded corpus/schema/index data.
  - British/American spelling, clinical/lay language, abbreviations and common misspellings.
  - related-but-not-equivalent symptom handling such as dizziness/lightheadedness/vertigo and palpitations/measured tachycardia.
  - lazy Android parsing/index construction and bounded candidate sets rather than full-corpus scans per turn.
  - candidate relevance explicitly treated as retrieval relevance, not disease probability.
- Modified during final integration:
  - the Agent 3 corpus remains behind the existing `MedicalConditionCandidateProvider` / `TrudyMedicalContextPlanner` path rather than becoming a generic diagnosis engine.
  - the unified knowledge provider's MEDICAL adapter wraps the existing reviewed medical-management repository only; symptom/condition candidate retrieval still goes through the dedicated medical safety wrapper.
- Rejected:
  - no `symptom = diagnosis` mapping.
  - no diagnostic probability layer.
  - no second independent medical reasoning engine.

### 2.4 `agent/trudy-nutrition-body-knowledge`

- Source commits:
  - `7e2cf5968d73a0bc29a1eafce1211bb04eee06d0` — `Add Trudy nutrition and body knowledge`
  - `0777212bae848a6e16d6522fbf97e798ff03e5fc` — `Handle natural food and energy phrasing`
- Purpose: curated nutrition, hydration, food, nutrient, body composition and metabolism reference knowledge with canonical metric mappings.
- Important files:
  - `nextgen/docs/TRUDY_NUTRITION_BODY_KNOWLEDGE.md`
  - `nextgen/shared/src/commonMain/kotlin/com/projectsuperhuman/next/trudy/nutrition/CuratedNutritionBodyKnowledgeRepository.kt`
  - `TrudyNutritionKnowledgeModels.kt`
  - `TrudyNutritionMetricCatalog.kt`
  - `nextgen/shared/src/commonTest/kotlin/com/projectsuperhuman/next/trudy/nutrition/TrudyNutritionBodyKnowledgeTest.kt`
- Accepted:
  - structured nutrient/topic/source models and 27 common nutrient references.
  - protein, hydration, overnight-weight, body-composition, energy-balance, food/energy, nutrient-gap and exercise-nutrition intents.
  - UK/US food terminology and ambiguity handling.
  - exact canonical metric mappings for logged energy/macros/fibre, dynamic food micronutrients, hydration, body metrics and selected exercise/environment context.
  - safety policy: missing food-log micronutrients are unknown rather than zero; food logs cannot diagnose deficiency/toxicity/blood status; no supplement-dose prescribing; short-term weight is not automatically fat change; no forced hydration or punitive restriction.
- Modified during final integration:
  - `TrudyNutritionBodyKnowledgeSource` adapts the domain repository to the unified provider and emits typed metric roles/hints.
  - a source-level relevance gate was added because Agent 4 correctly defaults unknown text to `GENERAL_NUTRITION` inside its own domain, but that fallback was too broad when used in a global coordinator. Unrelated text such as `Please rename my dashboard tile` now does not retrieve nutrition knowledge.
- Rejected:
  - `TrudyNutritionToolPlanner` is not used as a second competing global planner. Its useful metric mappings are adapted into Agent 2's bounded orchestration instead.

### 2.5 `agent/trudy-performance-knowledge`

- Source commit: `066f37866c417fecccb6053dfa0b33bfc0bb8f14`
- Commit message: `Add Trudy performance knowledge`
- Purpose: deterministic reference/terminology knowledge for sleep, exercise, recovery, activity, HR context, environment, mindfulness, breathing, emotional wellbeing and personal experiment methodology.
- Important files:
  - `nextgen/shared/src/commonMain/kotlin/com/projectsuperhuman/next/trudy/performance/INTEGRATION.md`
  - `PerformanceEvidenceCatalog.kt`
  - `PerformanceExperimentMethodology.kt`
  - `PerformanceIntentLexicon.kt`
  - `PerformanceKnowledgeCatalog.kt`
  - `PerformanceKnowledgeModels.kt`
  - `TrudyPerformanceKnowledge.kt`
  - `nextgen/shared/src/commonTest/kotlin/com/projectsuperhuman/next/trudy/performance/TrudyPerformanceKnowledgeTest.kt`
- Accepted:
  - normalized indexed phrase groups instead of a giant exact-string switch.
  - performance domains and structured evidence-source catalogue.
  - metric roles: primary outcome, secondary outcome, exposure, confounder, context, data quality.
  - safety distinctions: wearable estimate vs clinical measurement, personal association vs causation, no single-score readiness proof, no mental-health diagnosis, no autonomous exercise prescription, respect clinician restrictions.
  - experiment blueprints and methodology knowledge.
  - slang/abbreviation/misspelling coverage, including sleep-quality slang, fatigue/recovery language, HR/pulse phrasing, environment and emotional wording.
- Modified during final integration:
  - `TrudyPerformanceKnowledgeSource` adapts the facade into the unified provider and can emit `SLEEP_AND_PERFORMANCE`, `ENVIRONMENT`, `EMOTIONAL_WELLBEING` and `EXPERIMENT_METHODOLOGY` lanes while retaining Agent 5's internal structured models.
  - exact performance metric bindings can enrich Agent 2's plan through `TrudyKnowledgeMetricHint`; they do not compute personal correlations themselves.
- Rejected:
  - no parallel Data Vault access, statistics engine or persisted experiment state was introduced from this branch.

## 3. Integration Branch

- Branch: `integration/trudy-knowledge-voice-v2`
- Base relationship: created from `11.3` at `d5f244b2733ba7a370597253f1bf631661295336`; the integration branch is ahead of that base and was not merged into `11.3`.
- Code-freeze HEAD immediately before this handoff document was committed: `f655501c940de5255d7e8ce0ef1b20b3f5d47883`.
- Final HEAD SHA: **the commit containing this handoff document**. A Git commit cannot reliably embed its own final SHA in its tracked contents because changing the document changes the commit object hash. Obtain the authoritative final SHA with `git rev-parse HEAD`; the exact SHA is also supplied in the external final-delivery message accompanying this handoff.
- Source branches were not modified.

Integration trace:

1. Agent 2 source commit became the orchestration spine.
2. Agent 3 audited paths were integrated in merge commit `5486ee5ee01c6c3377d8a8080484559950694cea`.
3. Agent 4 head/ancestry was integrated in merge commit `3bdb10418c8a5b29744aee3976ae73f6c62b7730`.
4. Agent 5 was integrated in merge commit `15ba130dc293958f97b0d85188364ffbf05a419b`.
5. Agent 1 voice was integrated last in merge commit `e9a4fc6b3a9bb59f183571379a673b720a3955ec`.
6. Final semantic reconciliation commits added unified knowledge retrieval, relevance gating, the knowledge-to-evidence bridge, safety adjustment and integration tests.

## 4. Conflict Log

### Conflict A — Three knowledge systems vs one Trudy

Agents 3-5 use deliberately different domain models. Flattening them into one generic data class would discard medical red flags/provenance, nutrition nutrient structure, and performance experiment/metric roles.

Resolution: domain repositories remain structured behind adapters. `TrudyKnowledgeProvider`, `TrudyKnowledgeSource`, `TrudyKnowledgeItem` and `TrudyKnowledgeMetricHint` unify only the orchestration boundary: relevance, provenance, safety notes, metric coordinates and bounded rendering.

### Conflict B — Agent 2 planner vs Agent 4 nutrition planner

Agent 4 includes `TrudyNutritionToolPlanner`. Running it beside `TrudySystemInvestigationPlanner` would create parallel query planning and duplicated Data Vault access.

Resolution: Agent 2 remains authoritative. Agent 4's exact metric mappings are converted into typed hints; `TrudyOrchestrator.enrichPreflightWithKnowledge()` can add at most four exact metric-history operations for clearly personal non-medical questions, or enrich an existing `InvestigateChange` without replacing it.

### Conflict C — Agent 4 general-intent fallback in a global coordinator

Agent 4 intentionally returns `GENERAL_NUTRITION` for unrecognized text. In a global knowledge coordinator this caused unrelated text to retrieve generic nutrition knowledge.

Resolution: `TrudyKnowledgeRelevanceGate` performs a small source-level gate using canonical nutrition/body domains or normalized high-level nutrition/body terms. Rich nutrition terminology remains inside the Agent 4 repository.

### Conflict D — Agent 3 corpus models vs existing Trudy medical safety/management architecture

Agent 3 expands condition/symptom data in `com.projectsuperhuman.next.medical`; 11.3 already contains `com.projectsuperhuman.next.trudy.medical` safety, management and model-wrapper contracts.

Resolution: do not merge them into one diagnostic model. `AndroidMedicalCorpusCandidateProvider` continues to satisfy `MedicalConditionCandidateProvider`. `TrudyMedicalContextPlanner` and `MedicalContextAwareTrudyModelClient` remain the only medical context/safety path. The generic knowledge coordinator only exposes reviewed management reference context and does not replace candidate/safety retrieval.

### Conflict E — Experiment state vs experiment methodology

Agent 2 models canonical experiment state/evaluation; Agent 5 provides experiment-design methodology and blueprints.

Resolution: they remain separate. Canonical state is accessed through `TrudyCanonicalExperimentRepository` and typed experiment tools. Methodology is `EXPERIMENT_METHODOLOGY` knowledge. The orchestrator excludes methodology knowledge from automatic personal-metric expansion. When persistence is not connected, Trudy explicitly says so rather than treating preview/mock experiments as user history.

### Conflict F — Voice mode vs text runtime

A separate voice conversation runtime would fragment context/evidence.

Resolution: Agent 1's implementation already routes recognized speech through the same conversation controller as text. That design was retained.

### Conflict G — Context labels in voice UI

Labels such as `Sleep` or `Vitals` must not be cosmetic guesses.

Resolution: `TrudyVoiceContextMapper.modulesFor()` derives labels only from `TrudyReply.activity` and structured reply evidence IDs/labels/details. No answer-prose keyword parsing was added.

### Conflict H — Required severe chest-pain + breathing test vs safety phrase coverage

The existing high-specificity acute-coronary rule included severe shortness of breath but not the exact user wording `difficulty breathing`.

Resolution: the chest-discomfort + associated-feature rule now also recognizes `difficulty breathing` and `struggling to breathe`. It still requires a chest-discomfort phrase plus the associated feature, so ordinary reflux text does not become an emergency warning. This small medical-safety rule change requires senior manual review before merge.

### Conflict I — Per-item safety metadata vs bounded model formatter

`TrudyKnowledgeItem` now carries `safetyNotes`, but `TrudyPromptFormatter` primarily renders title/summary/provenance/uncertainty and truncates summaries.

Resolution: `TrudyOrchestrator.withSafetyInSummary()` appends up to three safety statements to the knowledge summary. **Known limitation:** the formatter later truncates each summary to 500 characters, so long source summaries can truncate appended safety notes. Existing global `TrudyModelPolicy` and the dedicated medical wrapper remain the primary safety controls; nevertheless, the lead integrator should consider rendering `safetyNotes` as a dedicated bounded field in the formatter rather than appending them.

## 5. Final Architecture

Primary flow:

`NativeTrudy` text or voice input
-> `TrudyConversationController`
-> `TrudyConversationService`
-> `TrudyOrchestrator.ask()`
-> `TrudySystemInvestigationPlanner.plan()`
-> `TrudyKnowledgeCoordinator.retrieve()`
-> `TrudyOrchestrator.enrichPreflightWithKnowledge()`
-> typed `CompositeTrudyToolExecutor`
-> canonical Data Vault / deterministic intelligence tools / canonical experiment boundary
-> `MedicalContextAwareTrudyModelClient` around Emotional/Environmental/model decorators
-> local/hosted/offline model synthesis
-> exact evidence-reference binding
-> `TrudyReply`
-> text UI and, when enabled, the same reply through TTS/voice-state UI.

Key files/classes:

- `nextgen/androidApp/src/main/java/com/projectsuperhuman/next/TrudyRuntimeFactory.kt`
  - creates `ModuleParityService`, `TrudyHealthContextService`, health/intelligence tool services, `TrudySystemInvestigationPlanner`, default `TrudyKnowledgeCoordinator`, medical wrapper and one conversation controller.
- `nextgen/shared/src/commonMain/kotlin/com/projectsuperhuman/next/trudy/TrudySystemIntegration.kt`
  - canonical module/metric catalog, temporal resolver, query planning, change investigation and premise assessment.
- `TrudyOrchestrator.kt`
  - bounded provider-neutral model/tool loop, knowledge retrieval, knowledge-to-metric planning bridge, warnings and exact evidence binding.
- `TrudyKnowledgeIntegration.kt`
  - common knowledge provider/source/item/metric-hint contracts and bounded coordinator.
- `TrudyUnifiedKnowledgeSources.kt`
  - adapters for reviewed medical management, nutrition/body and performance knowledge.
- `TrudyToolContract.kt` / `TrudyIntelligenceTools.kt`
  - typed tool operations/results; deterministic trends, associations, experiments and investigations.
- `TrudyPersonalEvidenceLibrary.kt`
  - deterministic personal comparisons/associations over canonical evidence.
- `nextgen/shared/src/commonMain/kotlin/com/projectsuperhuman/next/trudy/medical/*`
  - dedicated medical safety, candidate and management context layer.
- `AndroidMedicalCorpusCandidateProvider.kt`
  - lazy indexed Android adapter for the expanded medical corpus.
- `TrudyVoiceExperience.kt` / `AndroidTrudySpeechRecognizer.kt`
  - voice presentation and speech input over the same runtime.

## 6. Knowledge Architecture

The integration intentionally unifies boundaries rather than flattening domain schemas.

### Medical

- Generic lane: `TrudyKnowledgeKind.MEDICAL` through `TrudyMedicalManagementKnowledgeSource` for reviewed management/reference context and provenance.
- Safety-critical candidate lane remains separate: `AndroidMedicalCorpusCandidateProvider` -> `TrudyMedicalContextPlanner` -> `MedicalContextAwareTrudyModelClient`.
- This prevents a generic knowledge adapter from becoming a diagnosis engine.

### Nutrition/body

- `TrudyNutritionBodyKnowledgeSource` adapts `CuratedNutritionBodyKnowledgeRepository`.
- It retains topic/nutrient/source models and converts exact canonical metric bindings to `TrudyKnowledgeMetricHint` roles.
- Safety/uncertainty explicitly covers incomplete food logs, nutrient deficiency inference, short-term weight interpretation and hydration/restriction boundaries.

### Sleep/performance

- `TrudyPerformanceKnowledgeSource` adapts `TrudyPerformanceKnowledge`.
- It keeps Agent 5's claims, evidence sources, lexicon, metric roles and experiment blueprints.
- It emits `SLEEP_AND_PERFORMANCE` for sleep/exercise/recovery/activity/HR/mindfulness/breathing topics.

### Environment

- Environment remains a canonical Data Vault domain for user observations.
- Performance knowledge may emit `ENVIRONMENT` reference items and exact environment metric hints where relevant.
- Existing `withEnvironmentalReasoning()` remains in the model chain.

### Emotional/wellbeing

- Emotional data remains canonical user evidence.
- Performance knowledge may emit `EMOTIONAL_WELLBEING` reference items.
- Existing `withEmotionalReasoning()` remains in the model chain.

### Experiment methodology

- Agent 5 blueprints become `EXPERIMENT_METHODOLOGY` reference items.
- They are not persisted experiment state and are excluded from automatic personal-data expansion.

The coordinator is bounded by default to four items per source and eight total. Source ownership and source references are checked before items enter model context.

## 7. Data Vault Integration

No second health database was created. User observations remain in the canonical Data Vault/health repository path.

Important interfaces/files:

- `DataVaultGateway` additions in `nextgen/shared/src/commonMain/kotlin/com/projectsuperhuman/next/core/DataVaultContracts.kt`.
- `SqlDataVaultGateway.kt`, `SqlHealthRepository.kt` and `HealthStore.sq` implement bounded metric/domain access used by Trudy.
- `ModuleParityService` presents canonical module evidence.
- `TrudyHealthContextService` exposes current state, bounded metric history, metric windows, derived features, insights and data quality.
- `HealthContextPersonalEvidenceSource` / `TrudyPersonalEvidenceSource` provide bounded `metricHistory`, `metricWindow` and `dataQuality` access for deterministic intelligence tools.
- `TrudyHealthToolService` and `TrudyIntelligenceToolService` expose this through typed operations rather than raw SQL/model-created queries.

Canonical module coverage represented by `TrudySystemCatalog` includes:

- Sleep
- Vitals components
- Heart rate
- Blood pressure
- Body temperature
- Exercise
- Steps/activity
- Nutrition
- Hydration
- Emotional
- Environment
- Body/body composition
- Clinical markers
- Mindfulness

Current explicit limitations in the catalog:

- Breathwork: no canonical persisted Data Vault metric is connected yet.
- Symptoms: no canonical personal symptom-history port is connected yet.
- Experiments: canonical interface exists, but the default repository is `EmptyTrudyCanonicalExperimentRepository` until real persistence is connected.

The integration preserves the rule that absence of a measurement is absence of data. It does not substitute population values, another period, another user or invented readings.

## 8. Medical Architecture

The final medical path has four distinct responsibilities:

1. **Corpus/index:** Agent 3's JSON condition/symptom/lexical assets and schemas.
2. **Indexed candidate retrieval:** `AndroidMedicalCorpusCandidateProvider`; lazy-loaded/indexed, bounded candidate retrieval.
3. **Reviewed management/reference knowledge:** `CuratedMedicalManagementRepository` and its source-provenanced management entries.
4. **Safety and model integration:** `CuratedMedicalSafetySignalProvider`, `TrudyMedicalContextPlanner`, `MedicalContextAwareTrudyModelClient`.

`MedicalContextAwareTrudyModelClient` remains the outer medical context layer in `TrudyRuntimeFactory`. It can request bounded relevant Data Vault evidence and then delegate to the existing Emotional/Environmental/model chain. Candidate retrieval is context for reasoning, not a probability distribution.

Preserved medical boundaries:

- non-diagnostic framing.
- no `symptom = diagnosis` rule.
- no personalized medication doses.
- no medication stopping advice.
- no false reassurance.
- red flags are checked separately from condition relevance.
- source/provenance is retained.
- uncertainty is retained.
- Data Vault evidence may support context, but does not prove a diagnosis.

The integration added one narrow phrase coverage change to the acute chest-pain red-flag rule (`difficulty breathing` / `struggling to breathe` when paired with chest discomfort). This must be manually reviewed.

## 9. Language Understanding

Language handling is layered rather than centralized into a huge exact-string switch:

- `TrudySystemCatalog` owns stable module/metric coordinates and a small canonical alias set for planning.
- Agent 3's generated medical lexical index owns medical/lay/symptom language, British/American variants, abbreviations and common misspellings.
- Agent 4 owns normalized nutrition/nutrient/food aliases and explicit UK/US food terminology.
- Agent 5 owns normalized `PerformanceIntentLexicon` phrase groups; `TrudyPerformanceKnowledge` prebuilds normalized alias rows once for indexed deterministic matching.
- `TrudyKnowledgeRelevanceGate` is intentionally small. It prevents one domain's fallback intent from creating global noise; it does not replace domain lexicons.

Examples now route naturally:

- `I've been sleeping like crap lately` -> performance sleep aliases/claims/metrics, then bounded canonical evidence when personal.
- `I'm knackered` / `I'm shattered` -> performance recovery/emotional terminology.
- `My heart is racing` -> performance HR context; exact HR metric hints can enter the Agent 2 evidence plan without adding that entire phrase to the central system switch.
- `My tummy hurts` -> medical symptom language/candidate retrieval.
- `I've got acid reflux` / `Could this be GORD?` -> reviewed GORD reference plus dedicated medical candidate/safety reasoning.
- `My weight shot up overnight` -> Agent 4 short-term-weight intent and body/nutrition context.
- `I feel wired` / `I keep waking up` -> Agent 5 emotional/sleep phrase groups.
- British/American forms such as `fibre/fiber`, `glycaemic/glycemic`, `diarrhoea/diarrhea`, and `dyspnoea/dyspnea` remain in their domain lexicons.

## 10. Cross-Domain Reasoning

`TrudySystemInvestigationPlanner` remains central for `why`/change questions. It does not query every domain.

For a request such as `Why has my sleep suffered?`:

1. identify the sleep targets and requested change premise.
2. resolve an observation window and baseline window.
3. issue one bounded `InvestigateChange` operation.
4. `TrudyCrossDomainInvestigator` compares target metrics between windows.
5. assess the premise as `SUPPORTED`, `NOT_SUPPORTED`, `MIXED` or `INSUFFICIENT`.
6. only then compute bounded associations with selected related metrics.
7. retain missing metrics and data-quality limitations.
8. synthesize with retrieved reference knowledge.

Knowledge metric hints can enrich the targets/related set but are capped (`MAX_INVESTIGATION_TARGETS = 8`, `MAX_INVESTIGATION_RELATED = 8`). Agent 2's `maxAssociations` remains bounded at eight or fewer.

Contradiction handling is preserved. The existing test constructs a user claim of worse sleep while `sleep_score`, `sleep_deep_minutes` and `sleep_continuity_score` all improve; the deterministic investigator returns `TrudyPremiseAssessment.NOT_SUPPORTED`. Synthesis can therefore acknowledge subjective poor sleep while saying recorded metrics do not clearly support deterioration.

## 11. Temporal Reasoning

Temporal reasoning is dynamic.

- Shared contract: `TrudyTemporalBoundaryProvider`.
- Android implementation: `AndroidTrudyTemporalBoundaryProvider` using device-local `ZoneId` and calendar boundaries.
- Shared fallback: `UtcTrudyTemporalBoundaryProvider`.
- Resolver: `TrudyTemporalResolver` in `TrudySystemIntegration.kt`.

Supported planning includes today, yesterday, this week, last week, recently/recent windows, this month, last month and follow-up inheritance. Calendar month/week boundaries are calculated at runtime; no absolute date is hard-coded.

`lately` is primarily understood through Agent 5's language layer; Agent 2's explicit temporal phrase table does not have a distinct `lately` token, so it falls back to the planner's recent/default window rather than a separate temporal primitive. This is acceptable behavior but is a candidate for future explicit temporal alias expansion.

## 12. Conversational Continuity

`TrudyConversationTurn` now retains `evidenceKeys` for assistant turns. Agent 2 planning can inherit domain/time referents from recent conversation turns and can answer evidence-follow-up requests from prior structured evidence keys.

Preserved test sequences include:

- `How has my sleep been?` -> `What about last month?`
- prior sleep discussion -> `Could that affect my heart rate?`
- prior evidence-backed answer -> `What data are you basing that on?`

Voice mode uses the same controller/history, so switching between speech and keyboard does not intentionally reset the Trudy investigation context.

## 13. Experiments

Two separate concepts are retained deliberately.

### A. Canonical experiment state/data

- `TrudyCanonicalExperimentRepository`
- `EmptyTrudyCanonicalExperimentRepository`
- typed operations/results in `TrudyCanonicalExperiments.kt` / intelligence tools.

The default empty repository reports `NOT_CONNECTED`. The deterministic model explicitly refuses to treat preview/mock experiments as saved user history.

### B. Experiment methodology knowledge

Agent 5's `PerformanceExperimentMethodology` provides concepts/blueprints covering hypothesis, baseline, intervention, outcome, duration, confounders, measurement noise/limitations and interpretation. These are reference methods, not records of an experiment the user is actually running.

`How could I test whether caffeine affects my sleep?` can retrieve methodology guidance while the orchestrator intentionally avoids automatically querying personal metrics just because a blueprint contains metric mappings.

Known limitation: real canonical Experiments persistence still needs to be connected before Trudy can truthfully answer `What experiment am I running?` from user history.

## 14. Voice Mode

Files:

- `AndroidTrudySpeechRecognizer.kt`
- `TrudyVoiceExperience.kt`
- `NativeTrudy.kt`
- `AndroidManifest.xml`
- `TrudyVoiceExperienceTest.kt`

Speech recognition:

- Android `RECORD_AUDIO` runtime permission.
- microphone declared optional.
- free-form recognizer with locale, partial results, amplitude and no-input/error handling.
- on-device/offline preference where supported by the device recognizer.

TTS:

- uses the existing Trudy voice output controller/state rather than a second answer pipeline.
- voice state is mapped from real input/conversation/output state.

State machine:

- `IDLE`
- `LISTENING`
- `THINKING`
- `SPEAKING`
- `NO_INPUT`
- `ERROR`

Animated orb:

- original Project Superhuman visual identity using `VoiceCyan`, `VoiceAqua`, `VoiceBlue` and translucent light backgrounds.
- input amplitude affects the orb only while listening.
- state changes affect energy/tension/motion.

Runtime integration:

- speech transcript enters the same `TrudyConversationController` as text.
- TTS speaks the same reply produced by that conversation/runtime.
- context labels are derived from structured runtime activity/evidence only.

Accessibility:

- semantic labels exist for returning to text chat and voice settings.
- orb semantics announce current Trudy voice state and guidance.
- text mode remains directly available if voice input/output is unavailable.

Manual review should include TalkBack, permission denied/permanently denied flows, TTS unavailable/model-not-installed states and whether continuous re-listening after TTS feels appropriate on the target device.

## 15. Performance

The integration was designed not to regress 11.3 performance work.

Preserved safeguards:

- branch starts from exact latest `11.3`, so unrelated WorkManager, Home Vitals polling, cached Insights state and allocation improvements are retained.
- Agent 3 medical corpus remains lazy/indexed; no full medical scan per question.
- Agent 5 normalizes/indexes alias rows once per `TrudyPerformanceKnowledge` instance.
- `TrudyKnowledgeCoordinator` is bounded to four items/source and eight total by default.
- model formatter remains bounded (`MAX_KNOWLEDGE_ITEMS = 8`).
- knowledge-to-evidence expansion is capped at four extra exact metric-history operations with a history limit of 60 rows.
- `InvestigateChange` target/related enrichment is capped and its actual associations remain bounded.
- Agent 2 metric-window/history methods are used instead of full Data Vault history retrieval.
- no domain repository rebuild or second health database was introduced.

Remaining performance concerns:

- the default coordinator constructs the three curated source adapters when the runtime is created. Confirm `TrudyRuntimeFactory.create()` is not being recreated unnecessarily by Compose lifecycle changes.
- the enlarged medical assets materially increase app/package data size even though runtime retrieval is indexed.
- knowledge items append safety text before formatter truncation; a dedicated compact rendering field would be both clearer and potentially more allocation-efficient.

## 16. Safety

Medical safety is preserved as a separate layer, not mixed into general performance/nutrition retrieval.

Key protections:

- non-diagnostic language and candidate relevance, not probability.
- high-specificity red-flag rules checked separately.
- no personalized prescribing/doses.
- no medication stopping advice.
- no false reassurance.
- provenance retained for medical, nutrition and performance reference knowledge.
- missing user data is reported as missing.
- food logs cannot diagnose deficiency.
- unknown food micronutrient values are not treated as zero.
- short-term weight changes are not automatically called fat gain.
- no forced/excessive hydration guidance.
- no punitive restriction/compensatory-exercise framing.
- wearable sleep/body/HR estimates are not treated as clinical measurements.
- personal association is not causation.
- experiment result is not universal proof.

Manual safety review is required for the small acute-coronary phrase addition and for the model-context rendering of per-item `safetyNotes` described in the conflict log.

## 17. Tests

### Preserved/inherited

Agent 1:

- `TrudyVoiceExperienceTest.kt`
  - state transitions, listening/speaking/no-input/error-related mapping and runtime context-label behavior.

Agent 2:

- `TrudySystemIntegrationTest.kt`
  - bounded BP-yesterday windows.
  - sleep follow-up to last month.
  - one bounded why/sleep investigation.
  - evidence-key follow-up.
  - bounded direct cross-domain association.
  - `Could that affect my heart rate?` referent continuity.
  - contradiction: objectively improved sleep does not confirm a worse premise.
  - empty canonical experiment repository never returns preview history.
  - coordinator bounding/source ownership.
- `TrudyEvidenceContinuityTest.kt`
  - structured evidence continuity through Android/backend UI mapping.

Agent 3:

- `nextgen/scripts/validate_medical_corpus.py`
- `nextgen/scripts/test_medical_retrieval.py`

Agent 4:

- `TrudyNutritionBodyKnowledgeTest.kt`

Agent 5:

- `TrudyPerformanceKnowledgeTest.kt`
  - sleep/recovery slang, workout language, racing-heart phrasing, environment/emotional terminology, spelling variants and caffeine experiment methodology.

### Added during final integration

`nextgen/shared/src/commonTest/kotlin/com/projectsuperhuman/next/trudy/TrudySwarmKnowledgeIntegrationTest.kt` verifies:

1. `I've been sleeping like crap lately` resolves through unified performance knowledge with canonical sleep hints.
2. `Could this be GORD?` and `Could this be acid reflux?` resolve to the same reviewed GORD medical reference boundary without diagnostic-probability semantics.
3. overnight weight jump retrieves body-weight primary context and fluid/fat uncertainty.
4. caffeine testing retrieves experiment methodology separately from canonical experiment state.
5. environment and emotional knowledge remain separate lanes.
6. a knowledge-only alias can drive exactly one bounded canonical personal metric (`My heart is racing` -> `heart_rate_avg_bpm`) through the real orchestrator without requiring the small central system alias table to contain that phrase.
7. empty BP metric window produces the explicit no-measurement answer instead of an invented value.
8. severe chest pain + difficulty breathing triggers the high-specificity emergency rule while ordinary reflux does not.
9. unrelated UI text does not trigger broad nutrition/general knowledge retrieval.

### Execution status

**NOT EXECUTED as a complete integration suite in this session.** A temporary integration workflow was created to run medical validation, data-boundary checks, shared/Android unit tests and APK assembly. GitHub Actions run `31813899359` / job `94810831280` was rejected before checkout because of the account Actions billing/spending limit. The temporary workflow file was then removed from the final branch.

The source tests and new tests are therefore **present/preserved, not claimed as passing**. The lead integrator must run the commands in section 20 before merge.

## 18. Known Limitations

1. **Automated CI blocked:** no GitHub-hosted runner was allocated due account billing/spending state; integration Gradle/Python tests and APK assembly remain unverified in this handoff.
2. **Canonical Experiments persistence is not connected by default:** Trudy correctly reports this rather than using preview data.
3. **Canonical symptom-history persistence is not connected:** medical corpus understands symptom language, but there is no canonical persisted personal symptom-history port in `TrudySystemCatalog` yet.
4. **Canonical breathwork persistence is not connected:** methodology/performance knowledge exists, but no canonical personal breathwork metric is exposed in the system catalog.
5. **Per-item safety rendering:** `safetyNotes` are appended to a summary and may be truncated by `TrudyPromptFormatter`'s 500-character per-item summary rendering. Global/model medical policies still apply.
6. **`lately` temporal wording:** performance language understands it, but Agent 2 uses its normal/default recent window rather than a dedicated `lately` temporal primitive.
7. **Voice recognition quality is device/Android-recognizer dependent:** on-device availability, amplitude and partial-result behavior vary by phone/locale.
8. **Medical asset size:** the expanded corpus/indexes increase packaged data size; retrieval is indexed, but APK/storage impact should be measured on the beta target device.
9. **No instrumentation/device test was run here:** Compose permission, recognizer and TTS lifecycle need physical-device review.
10. **Knowledge hint planning is intentionally conservative:** non-personal general questions do not automatically query personal Data Vault metrics, and medical/methodology items are excluded from automatic hint expansion. This may require future intent refinement for edge cases, but avoids unnecessary data retrieval.

## 19. Manual Review Required

The lead integrator should personally inspect before merging:

1. `TrudyOrchestrator.kt`
   - confirm knowledge enrichment preserves desired planning semantics and query caps.
   - confirm no unexpected extra metric-history requests for broad personal wording.
2. `TrudyKnowledgeIntegration.kt`
   - review the nutrition relevance gate for false positives/false negatives.
3. `TrudyUnifiedKnowledgeSources.kt`
   - review metric-role mappings and bounded summaries/provenance.
4. `MedicalSafetyRules.kt`
   - specifically the addition of `difficulty breathing` / `struggling to breathe` to the chest-discomfort emergency pattern.
5. `TrudyModelRuntime.kt` / `TrudyPromptFormatter`
   - decide whether `safetyNotes` should get a dedicated bounded field rather than being appended to summary text.
6. `TrudyRuntimeFactory.kt`
   - confirm runtime is retained appropriately across Compose lifecycle so indexes/repos are not recreated more often than expected.
7. `AndroidMedicalCorpusCandidateProvider.kt`
   - profile cold initialization and verify the expanded assets on a low/mid-range Android phone.
8. `TrudyVoiceExperience.kt` / `AndroidTrudySpeechRecognizer.kt`
   - microphone permission flows, background/foreground behavior, automatic re-listening, TTS unavailable path, TalkBack semantics.
9. Full Gradle/Python suite and APK build because CI was blocked.
10. Inspect `git diff 11.3...integration/trudy-knowledge-voice-v2` before merge and confirm no later `11.3` commits appeared after base `d5f244b2733ba7a370597253f1bf631661295336`; if `11.3` advanced, rebase/reconcile rather than blindly merging.

## 20. Android Studio Build Checklist

- [ ] Check out `integration/trudy-knowledge-voice-v2` and confirm `git status` is clean.
- [ ] Confirm `git merge-base HEAD 11.3` is the intended base or deliberately reconcile any newer `11.3` work.
- [ ] Run `python3 nextgen/scripts/validate_medical_corpus.py`.
- [ ] Run `python3 nextgen/scripts/test_medical_retrieval.py`.
- [ ] Run `python3 nextgen/scripts/check_data_boundaries.py`.
- [ ] With Java 17 / Gradle 8.11.1-compatible tooling, run `gradle --no-daemon --build-cache -p nextgen :shared:testDebugUnitTest :androidApp:testDebugUnitTest` (verify task naming in the current Android Gradle plugin if necessary).
- [ ] Run `gradle --no-daemon --build-cache -p nextgen :androidApp:assembleDebug`.
- [ ] Install the debug APK on a physical Android phone without wiping existing Project Superhuman data unless intentionally testing a clean install.
- [ ] Open Trudy text chat and run the prompts in section 21 against both populated and deliberately missing-data cases.
- [ ] Verify a missing BP day says no measurement exists and does not substitute another day/average.
- [ ] Verify a worse-sleep claim can be contradicted when test/real metrics improve.
- [ ] Verify experiment mock/preview UI is never described as canonical user history when persistence is not connected.
- [ ] Grant microphone permission and test `IDLE -> LISTENING -> THINKING -> SPEAKING -> LISTENING` behavior.
- [ ] Deny microphone permission and verify a safe text-chat path remains.
- [ ] Trigger no-input and recognizer error states.
- [ ] Disable/unavailable TTS if possible and verify text answer remains usable.
- [ ] Turn on TalkBack and verify header, settings, orb-state and control semantics are understandable.
- [ ] Confirm voice and keyboard modes share prior conversation/referents/evidence.
- [ ] Inspect displayed voice context labels and verify each corresponds to actual `TrudyReply.activity`/evidence.
- [ ] Profile Trudy first medical query and subsequent queries for corpus initialization latency/memory.
- [ ] Measure APK/install size impact of the expanded medical assets.

## 21. Recommended Trudy Test Prompts

Use both text and voice for a representative subset.

1. `How has my sleep been recently?`
2. Follow with: `What about last month?`
3. Follow with: `Could that affect my heart rate?`
4. Follow with: `What data are you basing that on?`
5. `I've been sleeping like crap lately.`
6. `Why has my sleep suffered?`
7. With a test dataset where sleep score/continuity/deep sleep improved: `My sleep has been getting worse.`
8. On a day with no BP record: `What was my blood pressure yesterday?`
9. `My heart is racing.`
10. `My tummy hurts. What information would matter?`
11. `Could this be GORD?`
12. `Could this be acid reflux?`
13. `Why did my weight jump overnight?`
14. `I'm shattered after the gym. What should we look at in my data?`
15. `Could my hot bedroom or the weather be affecting my sleep?`
16. `How could I test whether caffeine affects my sleep?`
17. `What experiment am I running?` — expected to report that canonical persistence is not connected if still using the empty repository, not preview data.
18. `I have severe chest pain and difficulty breathing.` — verify the high-specificity emergency safety path.
19. In voice mode ask `How has my sleep been?`, then switch to keyboard and ask `What about last month?` to verify one conversation runtime.

---

### Integration status legend

- **INTEGRATED:** present in the final branch and wired into the described architecture.
- **PARTIAL:** the architectural boundary exists, but an external/canonical dependency is not yet connected.
- **NOT INTEGRATED:** deliberately excluded from the final runtime.

Current status:

- Agent 1 voice experience: **INTEGRATED**.
- Agent 2 system integration/orchestration: **INTEGRATED**, central spine.
- Agent 3 medical corpus/indexed candidate retrieval: **INTEGRATED** into the existing medical safety architecture.
- Agent 4 nutrition/body knowledge: **INTEGRATED** through the unified provider; separate global nutrition planner **NOT INTEGRATED** by design.
- Agent 5 performance knowledge: **INTEGRATED** through the unified provider and bounded metric-hint bridge.
- Canonical experiment interface: **INTEGRATED**; real default persistence **PARTIAL / NOT CONNECTED**.
- Personal symptom-history Data Vault port: **PARTIAL / NOT CONNECTED**.
- Personal breathwork Data Vault port: **PARTIAL / NOT CONNECTED**.
- GitHub-hosted integration validation: **NOT EXECUTED** because the Actions runner was blocked by account billing/spending state.
