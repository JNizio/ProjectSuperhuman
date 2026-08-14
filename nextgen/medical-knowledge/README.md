# Structured medical condition and symptom corpus (v2)

`conditions.v1.json` is the canonical offline reference corpus. It contains deliberately concise,
derived factual summaries and source metadata; it does not contain treatment recommendations.
`symptom-language.v1.json` owns curated consumer phrasing and explicit non-equivalence notes.
`symptom-index.v1.json` and `medical-lexical-index.v1.json` are generated deterministically; neither is
an independent source of medical truth.

## Safety semantics

- `common`, `possible`, and `uncommon` describe feature placement within a condition record. They do
  not estimate the probability that a user with the symptom has the condition.
- A symptom-index result is a set of candidate educational records, not a differential diagnosis.
- Red flags are condition/context specific and proportionately classified as `emergency`,
  `urgent_same_day`, or `prompt_clinical_review`.
- Empty red-flag lists are valid. The corpus must not inflate routine symptoms into emergencies.
- Investigations describe common diagnostic approaches; they are not instructions to order tests.
- Personal observations and confirmed/user-reported conditions belong in the Data Vault, never here.

## Source strategy

Each record carries one or more direct authoritative references, an evidence scope, and an access
date. Sources are primarily NHS condition pages, with WHO/CDC/NHLBI/NIDDK and major professional
societies used where they provide stronger or more specific coverage. Summaries are original and
compact; source text is not copied in bulk. A clinician/content review remains required before a
production medical release, and the review status is explicit per record.

## Maintenance

Run `python3 nextgen/scripts/validate_medical_corpus.py` after editing. The validator checks schema
shape, stable IDs, referential integrity, provenance, duplicate aliases/features/red flags, forbidden
diagnostic-certainty language, coverage floors, and regenerates/verifies the reverse symptom index.


## v2 coverage

- 188 condition records (64 preserved from v1 and 124 added)
- 597 canonical symptom/feature IDs
- 448 condition aliases and 268 symptom aliases
- 18 body-system domains, including explicit `sleep` and `autonomic` coverage
- 186 authoritative source records and 294 proportionate, contextual red flags

Condition aliases now carry a type: lay term, clinical term, abbreviation, British spelling,
American spelling, common misspelling or alternative name. The original string `aliases` array
remains for v1 consumers.

Symptom aliases are lexical equivalents only. Similar phrases that should not be collapsed (for
example dizziness, lightheadedness and vertigo; or palpitations and measured tachycardia) live in
`related_terms` with `related_not_equivalent` semantics and are not promoted into synonym postings.

## Indexed retrieval

The validator produces a static inverted lexical index:

- normalized condition phrases -> stable condition IDs
- normalized symptom phrases -> stable symptom IDs
- token postings for conditions and symptoms
- symptom IDs -> linked educational condition records

The Android adapter generates query n-grams, consults those postings and caps the pre-score set at
128 condition records. Only that bounded set is scored. Normalization, JSON parsing and fallback
index construction are lazy and happen once; a message does not scan every condition.

The fallback path exists for older asset bundles but also builds indexes only once. Retrieval scores
express lexical relevance, never disease probability.

## Validation

Run:

```bash
python3 nextgen/scripts/validate_medical_corpus.py --check
python3 -m unittest nextgen/scripts/test_medical_retrieval.py
```

The validator checks stable IDs, provenance, review metadata, typed aliases, referential integrity,
non-diagnostic wording, body-system coverage, symptom-language distinctions, red-flag shape, and
byte-for-byte freshness of both generated indexes.
