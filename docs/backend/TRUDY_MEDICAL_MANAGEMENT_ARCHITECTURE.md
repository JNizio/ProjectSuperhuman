# Trudy Medical Management Architecture

Status: Agent 3 implementation on `agent/trudy-medical-management`  
Base: `11.2` at `0f66fc9621b1fb767093ec2385fa3ea5dcd85e56`  
Reviewed: 2026-08-14

## Scope

This layer gives Trudy structured, source-linked management knowledge and a deterministic plan for combining it with bounded Data Vault context. It is not a diagnosis engine, prescription engine, condition catalogue or symptom corpus.

The initial reviewed seed pack covers:

- asthma
- irritable bowel syndrome
- gastro-oesophageal reflux disease
- hiatus hernia
- low back pain and sciatica
- panic disorder
- hypertension

Each `MedicalManagementEntry` uses a stable lowercase snake_case condition ID and can represent lifestyle, behaviour, diet, rehabilitation, psychological therapy, monitoring, first-line categories, specialist approaches, procedures, prevention, professional evaluation and limits of self-treatment.

## Architecture

```text
question
  -> TrudyMedicalContextPlanner
       -> MedicalKnowledgeProvider (management repository)
       -> MedicalConditionCandidateProvider (Agent 2 seam)
       -> MedicalSafetySignalProvider (high-specificity red flags)
       -> bounded TrudyContextRequest
  -> existing typed get_context tool
  -> TrudyMedicalContextRenderer
  -> existing TrudyModelClient
```

`MedicalContextAwareTrudyModelClient` is a decorator. It preserves the general language model and existing Trudy orchestration. For a relevant non-urgent question it first requests explicit Data Vault domains through the existing typed tool. On the next model iteration it supplies:

1. management entries and provenance;
2. condition candidates as retrieval possibilities, never probabilities or diagnoses;
3. Data Vault observations and deterministic summaries as context, never diagnostic proof;
4. a proportionate safety assessment;
5. a natural answer plan: answer first, explain the relevant pattern, qualify uncertainty only where material, then give useful next steps.

Urgent or emergency patterns skip Data Vault retrieval so escalation is not delayed.

## Evidence and provenance strategy

- Guidance is paraphrased into short category-level statements; source prose is not bulk-copied.
- Every management option must resolve to at least one `MedicalSource` within its entry.
- Sources include organisation, title, HTTPS URL, evidence type, source update date where known, and the date Project Superhuman reviewed it.
- The initial pack prioritises NHS, NICE, WHO and CDC material.
- Medicine content stays at treatment-category level and has a structural `personalizationProhibited` flag for clinician-led or specialist options.
- Model validation rejects dose-like text in curated management summaries.
- Entries carry a review-due date. Updating the pack requires checking the live source, revising the paraphrase if needed and moving both review dates.

This is curated management knowledge, not the broad MedlinePlus/Mondo condition catalogue described in `CONDITION_KNOWLEDGE_ARCHITECTURE.md`.

## Safety behaviour

The safety evaluator uses high-specificity conjunctions for selected time-critical patterns rather than attaching generic warnings to every answer. Current patterns cover severe breathing difficulty, an acute coronary-style symptom combination, cauda equina warning combinations, gastrointestinal bleeding, reflux alarm features and explicit immediate self-harm risk.

Important boundaries:

- a non-match does not certify safety;
- an emergency match leads the response and suppresses slower Vault analysis;
- a candidate condition is not a diagnosis;
- stored symptoms, trends, vitals or clinical markers can change relevance but cannot prove a condition;
- Trudy does not declare an individual medicine safe, choose a dose, or advise stopping prescribed medicine;
- missing or incomplete Vault data cannot support reassurance.

## Exact Agent 2 integration contract

Agent 2 should implement:

```kotlin
interface MedicalConditionCandidateProvider {
    suspend fun candidates(
        request: MedicalConditionCandidateRequest
    ): List<MedicalConditionCandidate>
}
```

Input:

```kotlin
MedicalConditionCandidateRequest(
    question: String,
    explicitlyRecordedConditionIds: Set<String>,
    maxCandidates: Int
)
```

Output:

```kotlin
MedicalConditionCandidate(
    conditionId: String,                 // lowercase snake_case
    displayName: String,
    relevance: MedicalCandidateRelevance, // retrieval relevance, not probability
    reasonsForFit: List<String>,
    reasonsAgainstFit: List<String>,
    informationThatWouldMatter: List<String>,
    sourceIds: List<String>
)
```

Contract rules:

1. `conditionId` is the only join key and must use lowercase snake_case.
2. Results are bounded by `maxCandidates` and ordered by retrieval relevance.
3. `relevance` must not be presented as disease probability.
4. `reasonsForFit` describes supplied pattern compatibility, not a diagnostic conclusion.
5. Include meaningful mismatches and missing discriminating information where the corpus supports them.
6. `sourceIds` refer to Agent 2's own corpus provenance. Agent 3 does not require those source objects to compile.
7. Returning an unknown but valid condition ID is allowed; Trudy can communicate the candidate while management retrieval remains empty.
8. Return an empty list when the corpus cannot support a candidate. Do not manufacture a generic diagnosis.

`EmptyMedicalConditionCandidateProvider` keeps this branch independently compilable until integration.

## Composition follow-up

At the app composition root, wrap the selected hosted/local model client:

```kotlin
val medicalModel = MedicalContextAwareTrudyModelClient(
    delegate = baseModel,
    planner = TrudyMedicalContextPlanner(
        knowledge = CuratedMedicalManagementRepository(),
        conditionCandidates = agent2CorpusAdapter
    ),
    explicitlyRecordedConditionIds = userConditionIdProvider
)
```

Then pass `medicalModel` to the existing `TrudyOrchestrator`. This branch intentionally does not modify the composition root because five swarm branches are changing Trudy independently and the integration agent should assemble the final composite once all contracts are present.

## Remaining follow-up

- Connect Agent 2's corpus adapter and reconcile any ID aliases in one integration-only mapping.
- Add a proper user-condition profile provider; do not infer recorded diagnoses from searches.
- Compose the medical decorator with other swarm decorators/tool routers in the app composition root.
- Expand the seed pack only through reviewed, source-linked additions and add region metadata if non-UK guidance diverges.
- Add localisation and user-facing source links.
- Run full shared, Android compile and APK CI on the integrated branch.
