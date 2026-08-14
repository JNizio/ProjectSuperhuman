# Trudy medical knowledge v2 delivery

Date: 2026-08-14  
Base: `11.3` at `d5f244b2733ba7a370597253f1bf631661295336`  
Branch: `agent/trudy-medical-knowledge-v2`

## Measured result

| Measure | Before | v2 |
|---|---:|---:|
| Conditions | 64 | 188 |
| Canonical symptom/feature IDs | 212 | 597 |
| Condition aliases | 106 | 448 |
| Symptom aliases | 27 | 268 |
| Total aliases | 133 | 716 |
| Source records | 62 | 186 |
| Contextual red flags | 96 | 294 |
| Body-system domains | 16 | 18 |

All 64 prior condition IDs are preserved. v2 adds 124 records spanning gastrointestinal,
cardiovascular, respiratory, neurological, musculoskeletal, endocrine/metabolic, infectious,
dermatological, urinary/renal, haematological/nutritional, mental health, ENT, eye,
allergy/immunology, reproductive, pain, sleep and autonomic medicine.

## Schema changes

The major v1 file family remains in place, while `schema_version` advances from `1.0.0` to
`1.1.0` and `corpus_version` advances to `2.0.0`.

- `alias_records` is required on each condition and types every alias as a lay term, clinical
  term, abbreviation, British spelling, American spelling, common misspelling or alternative name.
- The original string `aliases` array remains and is mirrored exactly for existing consumers.
- `sleep` and `autonomic` are first-class body-system values.
- A root `terminology_policy` states that lexical matching retrieves candidates rather than
  diagnoses.
- `symptom-language.v1.json` separates lexical equivalents from
  `related_not_equivalent` terms.
- Dedicated JSON schemas now cover the condition corpus, generated symptom index and generated
  lexical index.
- Shared Kotlin models add the two body systems and typed alias/related-term models with defaults,
  preserving existing positional constructors.

## Symptom-language policy

Consumer phrasing includes British and American spellings, abbreviations, and expressions such as:

- tummy ache / stomach pain / abdominal pain
- short of breath / breathless / cannot catch my breath / dyspnoea / dyspnea
- heart racing / pounding heartbeat / palpitations
- diarrhoea / diarrhea / loose stools
- pins and needles / paraesthesia / paresthesia

Terms are not collapsed when the distinction can matter. The language file explicitly records:

- dizziness vs lightheadedness vs vertigo
- palpitations vs measured fast heartbeat
- fatigue vs sleepiness
- fainting vs seizure
- breathlessness vs low oxygen
- abdominal pain vs indigestion

Related-but-non-equivalent terms are documented but are not emitted as synonym postings.

## Source strategy

Existing provenance is retained. Each added record has a direct source entry with organisation,
title, HTTPS URL, evidence scope and access date. NHS patient information is the UK-first default;
MedlinePlus/NLM fills suitable coverage gaps; CDC material is used selectively for infection and
metabolic topics. Existing WHO sources remain. Summaries are compact original syntheses rather than
copied source passages.

Every new record remains `needs_clinical_review`. Provenance, uncertainty, proportionate red
flags and non-diagnostic boundaries are validation requirements, not optional prose.

## Indexed retrieval

`validate_medical_corpus.py` now deterministically generates two artifacts:

1. `symptom-index.v1.json`: symptom-to-condition links plus curated lexical terminology.
2. `medical-lexical-index.v1.json`: condition phrase, symptom phrase, condition token, symptom
   token and symptom-to-condition postings.

The generated index contains 641 normalized condition phrases and 1,279 normalized symptom phrases.
Each posting is capped at 64 IDs.

At runtime the Android provider:

1. lazily parses the corpus and static index once;
2. normalizes the question and creates at most six-word n-grams;
3. gathers IDs through phrase/token postings;
4. adds explicitly recorded condition IDs;
5. caps the pre-score set at 128 records;
6. scores only that bounded set and returns the requested top candidates.

The compatibility fallback builds the same index shape once if an older asset bundle lacks the
generated file. Neither path scans all condition objects for each message. Scores are lexical
relevance only and are never exposed as disease probabilities.

## Compatibility implications

- Stable condition IDs: fully preserved for the original 64 records.
- Existing readers of the string `aliases` field continue to work.
- Readers that reject any schema version other than exactly `1.0.0` must be updated to accept
  `1.1.0`.
- Exhaustive switches over `MedicalBodySystem` must handle `SLEEP` and `AUTONOMIC`.
- Asset packaging already includes the entire `medical-knowledge` directory, so the new indexes
  require no Gradle source-set change.
- The filenames retain `.v1` because this is a backward-compatible major-schema evolution; the
  corpus content version is `2.0.0`.

## Verification

```text
validated 188 conditions, 597 symptoms, 716 aliases, 186 sources;
indexed 641 condition and 1279 symptom phrases

Ran 5 retrieval contract tests: OK
```

The tests preserve the complete original ID set, exercise representative lay-language queries,
assert non-equivalent dizziness terminology, enforce posting/runtime bounds, and reject diagnostic
probability language.
