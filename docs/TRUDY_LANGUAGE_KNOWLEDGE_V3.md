# Trudy Language and Knowledge Retrieval v3

Base audited: `11.3` at `9c78ef8d1cc8f73fd7d5bf1bc709dba43c767ff4`.

## Scope

This change is a retrieval/routing layer over the existing medical, nutrition/body,
sleep/performance, environment, emotional-wellbeing and experiment-methodology corpora. It does
not add a second encyclopedia, a second Data Vault, diagnostic inference, analytics or hardware.

## Phrase coverage

The regression matrix contains 74 representative phrases across sleep, fatigue, UK/US food
language, GORD/GERD, palpitations, breathlessness, abdominal symptoms, exercise slang, overnight
weight, hydration, caffeine, stress/anxiety and experiment language.

- Static audit of the pre-change unified path: 38/74 reached a relevant existing corpus lane.
- New shared router: 74/74 map to their expected semantic group.
- Product/unrelated negative set: dashboard/tile/navigation/build requests and unrelated coding or
  writing prompts produce no health-knowledge route.

The before figure is a deterministic source audit of the previous coordinator gate plus the
existing performance and reviewed-medical aliases. It is not a clinical-quality score. The after
figure is backed by the checked-in regression expectations; the full Gradle suite still needs to
run in an Android/Kotlin build environment.

## Architecture

`TrudyLanguageRouter` constructs one immutable, bounded `TrudyLanguageRouting` per coordinator
query. It provides:

- consistent apostrophe, punctuation, compound and UK/US spelling normalization;
- 41 semantic alias groups and 363 unique normalized alias rows;
- a token-to-alias lexical index rather than a question-sized exact-string switch;
- phrase, token-sequence and unordered multi-token scoring;
- one-edit fuzzy matching only for explicitly safe single words of six or more characters;
- a maximum of 12 topic matches, with eight by default;
- ranked multi-intent output rather than a forced single intent;
- bounded canonical search-term expansion for the existing domain repositories.

The coordinator stores the plan on `TrudyKnowledgeQuery.routing`, applies source/domain/metric
relevance gates, ranks by route and corpus lexical relevance, preserves one best result per routed
lane, then fills the remaining top-K positions. Lexical relevance is explicitly not diagnostic
probability.

The existing performance source keeps ownership of its larger internal lexicon (including naps,
shift work, readiness and training modalities). The shared gate allows that indexed source to run
after product-only suppression, so v3 adds cross-corpus language without narrowing mature coverage.

## Ambiguity boundaries

Separate semantic IDs are retained for:

- dizziness, lightheadedness and vertigo;
- palpitations and tachycardia;
- reflux, indigestion and abdominal pain.

These groups share an audit-friendly `ambiguityBoundary` label but do not share canonical search
terms. Fuzzy matching is disabled for these clinical neighbours; reviewed explicit typo aliases
can still resolve to the correct single concept.

## Medical corpus integration

`AndroidMedicalCorpusCandidateProvider` expands lay symptom language into at most 12 canonical
terms before its existing n-gram/posting lookup. It still:

- loads/parses the corpus lazily;
- retrieves a bounded candidate-ID set from the generated lexical postings;
- scores only those records;
- keeps structured red-flag evaluation separate;
- treats relevance as educational candidate retrieval, never likelihood or diagnosis.

There is no restoration of full-corpus scanning.

## Relevance fixes

- Product-only requests such as renaming or moving a dashboard tile are rejected before any corpus
  adapter executes, even if a health word appears in the tile name.
- Nutrition is no longer selected by one standalone hard-coded term gate. Food aliases such as
  porridge/oatmeal, crisps/chips, chips/fries, latte and protein shake can reach the existing
  nutrition resolver.
- Caffeine plus sleep selects both the sleep/performance and nutrition lanes. Experiment
  methodology is included only when test/experiment language is present.
- Performance, environment and emotional results are ranked by their semantic match rather than
  being appended after earlier source order.
- Duplicate knowledge remains deduplicated by `(kind, stableId)` after ranking.

## Integration contract

1. The orchestrator continues to call `TrudyKnowledgeCoordinator.retrieve()` once per turn.
2. The coordinator attaches `TrudyLanguageRouting` to the existing `TrudyKnowledgeQuery`.
3. Corpus adapters call `query.searchTextFor(kind)` to receive bounded canonical expansion while
   retaining the original text.
4. Personal evidence expansion remains owned by Agent 2's bounded metric/tool plan. Language
   matches do not query storage themselves.
5. Medical candidate retrieval continues through `TrudyMedicalContextPlanner` and the Android
   posting-index provider; medical safety remains separate from general knowledge ranking.
6. Experiment methodology remains reference knowledge. It is not canonical experiment state.
7. Future corpora should add an alias group pointing at an existing stable semantic/topic ID,
   declare its knowledge kind/domain, and add positive plus negative regression phrases. New facts
   still require the corpus's existing provenance and safety review.

## Validation

Added `TrudyLanguageKnowledgeRoutingTest` covers:

- the 74-phrase positive matrix;
- multi-intent routing and top-K bounds;
- clinical ambiguity separation;
- medical canonical expansion;
- safe fuzzy matching;
- caffeine/sleep/methodology gating;
- product and unrelated false-positive queries;
- coordinator source gating, ranking and bounded lane coverage.

Run from a configured Java 17 / Gradle environment:

```text
gradle --no-daemon --build-cache -p nextgen :shared:testDebugUnitTest :androidApp:testDebugUnitTest
python3 nextgen/scripts/validate_medical_corpus.py
python3 nextgen/scripts/test_medical_retrieval.py
```
